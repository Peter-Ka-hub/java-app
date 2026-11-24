package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.*;
import dev.schooldiary.diary.internal.domain.EnrollmentStatus;
import dev.schooldiary.diary.internal.domain.Subject;
import dev.schooldiary.diary.internal.persistence.EnrollmentRepository;
import dev.schooldiary.diary.internal.persistence.GradeRepository;
import dev.schooldiary.diary.internal.persistence.GradeRevisionRepository;
import dev.schooldiary.diary.internal.persistence.GradeScopes;
import dev.schooldiary.identity.AccountLookup;
import dev.schooldiary.shared.access.CurrentActor;
import dev.schooldiary.shared.access.UserRole;
import dev.schooldiary.shared.application.PageResult;
import dev.schooldiary.shared.concurrency.Versions;
import dev.schooldiary.shared.error.ConflictException;
import dev.schooldiary.shared.error.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GradeService {
  private final GradeRepository grades;
  private final GradeRevisionRepository revisions;
  private final EnrollmentRepository enrollments;
  private final GradeMapper mapper;
  private final DiaryAccessPolicy access;
  private final CurrentActor actors;
  private final GradeScopes scopes;
  private final EntityManager entityManager;
  private final AccountLookup accounts;

  public PageResult<GradeView> list(int page, int size) {
    var result =
        grades.findAll(scopes.grades(actors.current()), PageRequest.of(page, size, Sort.by("id")));
    return PageResult.from(result, mapper::toView);
  }

  public GradeView get(UUID id) {
    var grade = find(id);
    access.readGrade(grade.getSubject(), grade.getStudent().getId());
    return mapper.toView(grade);
  }

  @Transactional
  public GradeView create(GradeCommand request) {
    var subject = subject(request.subjectId());
    access.writeGrades(subject);
    var grade = new Grade();
    apply(request, grade, subject, true);
    grade.setGradedBy(accounts.currentManaged());
    grades.saveAndFlush(grade);
    audit(grade, GradeRevisionAction.CREATED);
    return mapper.toView(grade);
  }

  @Transactional
  public GradeView update(UUID id, GradeCommand request) {
    var grade = gradeForWriting(id);
    Versions.requireCurrent(request.version(), grade.getVersion());
    if (!grade.getSubject().getId().equals(request.subjectId())
        || !grade.getStudent().getId().equals(request.studentId()))
      throw new ConflictException(
          "A grade cannot be reassigned to another student or subject; correct or delete it"
              + " instead");
    apply(request, grade, grade.getSubject(), false);
    grades.saveAndFlush(grade);
    audit(grade, GradeRevisionAction.UPDATED);
    return mapper.toView(grade);
  }

  @Transactional
  public void delete(UUID id) {
    var grade = gradeForWriting(id);
    audit(grade, GradeRevisionAction.DELETED);
    grades.delete(grade);
    grades.flush();
  }

  public List<GradeRevisionView> history(UUID id) {
    var history = revisions.findByGradeIdOrderByChangedAtAsc(id);
    if (history.isEmpty()) throw new NotFoundException("Grade history not found: " + id);
    var actor = actors.current();
    var authorized = new HashSet<GradeContext>();
    for (var revision : history) {
      var context = new GradeContext(revision.getSubject().getId(), revision.getStudent().getId());
      if (authorized.add(context))
        access.readGrade(actor, revision.getSubject(), revision.getStudent().getId());
    }
    return history.stream()
        .map(
            revision ->
                new GradeRevisionView(
                    revision.getId(),
                    revision.getGradeId(),
                    revision.getScore(),
                    revision.getDateOfScore(),
                    revision.getStudent().getId(),
                    revision.getSubject().getId(),
                    revision.getChangedBy().getId(),
                    revision.getChangedAt(),
                    revision.getAction()))
        .toList();
  }

  private record GradeContext(UUID subjectId, UUID studentId) {}

  private void apply(GradeCommand request, Grade grade, Subject subject, boolean creating) {
    var student = accounts.locked(request.studentId()).orElse(null);
    if (student == null) throw new NotFoundException("Student not found");
    if (student.getRole() != UserRole.STUDENT || !student.isEnabled())
      throw new IllegalArgumentException("Student must be an enabled STUDENT");
    if (!subject.getSchool().getId().equals(student.schoolId()))
      throw new IllegalArgumentException("Student must belong to the subject's school");
    var enrollment =
        enrollments
            .findBySubjectIdAndStudentId(subject.getId(), student.getId())
            .orElseThrow(
                () -> new IllegalArgumentException("Student is not enrolled in this subject"));
    var date = request.dateOfScore();
    var year = subject.getAcademicYear();
    if (date.isBefore(year.getStartDate()) || date.isAfter(year.getEndDate()))
      throw new IllegalArgumentException("Grade date must be within the academic year");
    if (date.isBefore(enrollment.getJoinedOn())
        || (enrollment.getLeftOn() != null && date.isAfter(enrollment.getLeftOn()))
        || (creating && enrollment.getStatus() != EnrollmentStatus.ACTIVE))
      throw new IllegalArgumentException("Grade requires an active enrollment on the grading date");
    grade.setScore(Score.valueOf(request.score().name()));
    grade.setDateOfScore(date);
    grade.setSubject(subject);
    grade.setStudent(student);
  }

  private void audit(Grade grade, GradeRevisionAction action) {
    var revision = new GradeRevision();
    revision.setGradeId(grade.getId());
    revision.setScore(grade.getScore());
    revision.setDateOfScore(grade.getDateOfScore());
    revision.setStudent(grade.getStudent());
    revision.setSubject(grade.getSubject());
    revision.setChangedBy(accounts.currentManaged());
    revision.setAction(action);
    revisions.saveAndFlush(revision);
  }

  private Subject subject(UUID id) {
    var subject = entityManager.find(Subject.class, id, LockModeType.PESSIMISTIC_WRITE);
    if (subject == null) throw new NotFoundException("Subject not found: " + id);
    entityManager.refresh(subject, LockModeType.PESSIMISTIC_WRITE);
    return subject;
  }

  private Grade find(UUID id) {
    return grades.findById(id).orElseThrow(() -> new NotFoundException("Grade not found: " + id));
  }

  private Grade gradeForWriting(UUID id) {
    var grade = find(id);
    var subject = subject(grade.getSubject().getId());
    try {
      entityManager.refresh(grade, LockModeType.PESSIMISTIC_WRITE);
    } catch (EntityNotFoundException deleted) {
      // Another transaction deleted the grade while this request waited for the subject lock.
      throw new NotFoundException("Grade not found: " + id);
    }
    access.writeGrades(subject);
    return grade;
  }
}
