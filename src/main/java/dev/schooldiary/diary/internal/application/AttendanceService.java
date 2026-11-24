package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.AttendanceCorrection;
import dev.schooldiary.diary.internal.domain.AttendanceEntry;
import dev.schooldiary.diary.internal.domain.AttendanceLesson;
import dev.schooldiary.diary.internal.domain.AttendanceLessonState;
import dev.schooldiary.diary.internal.domain.AttendanceStatus;
import dev.schooldiary.diary.internal.domain.Enrollment;
import dev.schooldiary.diary.internal.domain.EnrollmentStatus;
import dev.schooldiary.diary.internal.domain.Subject;
import dev.schooldiary.diary.internal.persistence.AttendanceCorrectionRepository;
import dev.schooldiary.diary.internal.persistence.AttendanceEntryRepository;
import dev.schooldiary.diary.internal.persistence.AttendanceLessonRepository;
import dev.schooldiary.diary.internal.persistence.EnrollmentRepository;
import dev.schooldiary.diary.internal.persistence.SubjectRepository;
import dev.schooldiary.identity.AccountLookup;
import dev.schooldiary.shared.application.PageResult;
import dev.schooldiary.shared.concurrency.Versions;
import dev.schooldiary.shared.error.ConflictException;
import dev.schooldiary.shared.error.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AttendanceService {
  private final SubjectRepository subjects;
  private final EnrollmentRepository enrollments;
  private final AttendanceLessonRepository lessons;
  private final AttendanceEntryRepository entries;
  private final AttendanceCorrectionRepository corrections;
  private final DiaryAccessPolicy access;
  private final AccountLookup accounts;
  private final EntityManager entityManager;

  public PageResult<AttendanceLessonSummaryView> list(UUID subjectId, int page, int size) {
    var subject =
        subjects
            .findById(subjectId)
            .orElseThrow(() -> new NotFoundException("Subject not found: " + subjectId));
    access.manageAttendance(subject);
    var paging =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lessonDate", "lessonNumber"));
    return PageResult.from(lessons.findAllBySubjectId(subjectId, paging), this::summary);
  }

  public AttendanceLessonView get(UUID lessonId) {
    var lesson = findLesson(lessonId);
    access.manageAttendance(lesson.getSubject());
    return details(lesson, entries.findRoster(lessonId));
  }

  @Transactional
  public AttendanceLessonView create(UUID subjectId, int lessonNumber) {
    if (lessonNumber < 1 || lessonNumber > 99) {
      throw new IllegalArgumentException("Lesson number must be between 1 and 99");
    }

    var subject = lockSubject(subjectId);
    access.manageAttendance(subject);
    LocalDate today = LocalDate.now();
    var year = subject.getAcademicYear();
    if (today.isBefore(year.getStartDate()) || today.isAfter(year.getEndDate())) {
      throw new IllegalArgumentException("Today's date must be within the academic year");
    }
    if (lessons.existsBySubjectIdAndLessonDateAndLessonNumber(subjectId, today, lessonNumber)) {
      throw new ConflictException("A lesson with this number already exists today for the subject");
    }

    var students =
        enrollments
            .findBySubjectIdAndStatusAndJoinedOnLessThanEqual(
                subjectId, EnrollmentStatus.ACTIVE, today)
            .stream()
            .map(Enrollment::getStudent)
            .toList();
    if (students.isEmpty()) {
      throw new ConflictException("A lesson requires at least one active student enrollment");
    }

    var lesson =
        lessons.saveAndFlush(
            new AttendanceLesson(subject, today, lessonNumber, students, Instant.now()));
    return details(lesson, entries.findRoster(lesson.getId()));
  }

  @Transactional
  public AttendanceLessonView save(
      UUID lessonId,
      Long version,
      AttendanceLessonState state,
      List<AttendanceEntryCommand> submitted) {
    var lesson = findLesson(lessonId);
    // Subject first so a simultaneous reassignment cannot authorize a former teacher's write.
    var subject = lockSubject(lesson.getSubject().getId());
    access.manageAttendance(subject);
    entityManager.refresh(lesson, LockModeType.PESSIMISTIC_WRITE);
    Versions.requireCurrent(version, lesson.getVersion());

    if (state == null) {
      throw new IllegalArgumentException("Lesson state is required");
    }
    boolean correcting = lesson.getState() == AttendanceLessonState.FINALIZED;
    if (correcting && state != AttendanceLessonState.FINALIZED) {
      throw new ConflictException("Finalized attendance cannot return to draft");
    }

    var roster = entries.findRoster(lessonId);
    var statuses = validateRoster(roster, submitted, state);
    Instant savedAt = lesson.markSaved(state, Instant.now());
    var changed =
        roster.stream()
            .filter(entry -> entry.getStatus() != statuses.get(entry.getStudent().getId()))
            .toList();

    if (correcting && !changed.isEmpty()) {
      var actor = accounts.currentManaged();
      corrections.saveAll(
          changed.stream()
              .map(
                  entry ->
                      new AttendanceCorrection(
                          entry, statuses.get(entry.getStudent().getId()), actor, savedAt))
              .toList());
    }
    for (var entry : changed) {
      entry.changeStatus(statuses.get(entry.getStudent().getId()));
    }
    lessons.flush();
    return details(lesson, roster);
  }

  private Map<UUID, AttendanceStatus> validateRoster(
      List<AttendanceEntry> roster,
      List<AttendanceEntryCommand> submitted,
      AttendanceLessonState state) {
    if (submitted == null || submitted.size() != roster.size()) {
      throw new IllegalArgumentException("Submit every student on the lesson roster once");
    }
    var remaining =
        roster.stream().map(entry -> entry.getStudent().getId()).collect(Collectors.toSet());
    Map<UUID, AttendanceStatus> statuses = new HashMap<>();
    for (var row : submitted) {
      if (row == null || !remaining.remove(row.studentId())) {
        throw new IllegalArgumentException("Submit every student on the lesson roster once");
      }
      if (state == AttendanceLessonState.FINALIZED && row.status() == null) {
        throw new IllegalArgumentException("Every student needs a status before finalizing");
      }
      statuses.put(row.studentId(), row.status());
    }
    return statuses;
  }

  private AttendanceLesson findLesson(UUID id) {
    return lessons
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Attendance lesson not found: " + id));
  }

  private Subject lockSubject(UUID id) {
    // Enrollment create/withdraw/reactivate and subject edits use the same lock.
    var subject = entityManager.find(Subject.class, id, LockModeType.PESSIMISTIC_WRITE);
    if (subject == null) {
      throw new NotFoundException("Subject not found: " + id);
    }
    entityManager.refresh(subject, LockModeType.PESSIMISTIC_WRITE);
    return subject;
  }

  private AttendanceLessonSummaryView summary(AttendanceLesson lesson) {
    var subject = lesson.getSubject();
    var schoolClass = lesson.getSchoolClass();
    return new AttendanceLessonSummaryView(
        lesson.getId(),
        subject.getId(),
        subject.getSchool().getId(),
        schoolClass == null ? null : schoolClass.getId(),
        lesson.getLessonDate(),
        lesson.getLessonNumber(),
        lesson.getState(),
        lesson.getVersion());
  }

  private AttendanceLessonView details(AttendanceLesson lesson, List<AttendanceEntry> roster) {
    Map<UUID, List<AttendanceCorrectionView>> history = new HashMap<>();
    for (var correction : corrections.findByEntryLessonIdOrderByChangedAtAscIdAsc(lesson.getId())) {
      var view =
          new AttendanceCorrectionView(
              correction.getPreviousStatus(),
              correction.getNewStatus(),
              correction.getChangedBy().getId(),
              correction.getChangedAt());
      history.computeIfAbsent(correction.getEntry().getId(), id -> new ArrayList<>()).add(view);
    }
    var rows =
        roster.stream()
            .map(
                entry -> {
                  var student = entry.getStudent();
                  return new AttendanceEntryView(
                      student.getId(),
                      student.getFirstName(),
                      student.getLastName(),
                      entry.getStatus(),
                      List.copyOf(history.getOrDefault(entry.getId(), List.of())));
                })
            .toList();
    return new AttendanceLessonView(summary(lesson), rows);
  }
}
