package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.Enrollment;
import dev.schooldiary.diary.internal.domain.EnrollmentStatus;
import dev.schooldiary.diary.internal.domain.Subject;
import dev.schooldiary.diary.internal.persistence.EnrollmentRepository;
import dev.schooldiary.diary.internal.persistence.GradeRepository;
import dev.schooldiary.diary.internal.persistence.GradeRevisionRepository;
import dev.schooldiary.identity.AccountLookup;
import dev.schooldiary.identity.internal.domain.User;
import dev.schooldiary.organization.internal.domain.AcademicYear;
import dev.schooldiary.shared.access.Actor;
import dev.schooldiary.shared.access.CurrentActor;
import dev.schooldiary.shared.access.UserRole;
import dev.schooldiary.shared.application.PageResult;
import dev.schooldiary.shared.concurrency.Versions;
import dev.schooldiary.shared.error.ConflictException;
import dev.schooldiary.shared.error.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {
  private final EnrollmentRepository enrollments;
  private final AccountLookup accounts;
  private final GradeRepository grades;
  private final GradeRevisionRepository revisions;
  private final EnrollmentMapper mapper;
  private final DiaryAccessPolicy access;
  private final CurrentActor actors;
  private final EntityManager entityManager;

  public PageResult<EnrollmentView> list(int page, int size) {
    Actor actor = actors.current();
    Specification<Enrollment> scope =
        (root, query, cb) -> {
          if (actor.isGlobalAdmin()) return cb.conjunction();
          var school = cb.equal(root.get("subject").get("school").get("id"), actor.schoolId());
          return switch (actor.role()) {
            case ADMIN -> school;
            case TEACHER ->
                cb.and(school, cb.equal(root.get("subject").get("teacher").get("id"), actor.id()));
            case STUDENT -> cb.and(school, cb.equal(root.get("student").get("id"), actor.id()));
          };
        };
    var result = enrollments.findAll(scope, PageRequest.of(page, size, Sort.by("id")));
    return PageResult.from(result, mapper::toView);
  }

  public EnrollmentView get(UUID id) {
    Enrollment enrollment = find(id);
    access.readEnrollment(enrollment);
    return mapper.toView(enrollment);
  }

  @Transactional
  public EnrollmentView create(EnrollmentCommand request) {
    Subject subject = subjectForEnrollment(request.subjectId());
    User student =
        accounts
            .managed(request.studentId())
            .orElseThrow(() -> new NotFoundException("Student not found: " + request.studentId()));
    accounts.lock(student);
    return enroll(subject, student, request.joinedOn());
  }

  @Transactional
  public EnrollmentView createByEmail(UUID subjectId, String email, LocalDate joinedOn) {
    Subject subject = subjectForEnrollment(subjectId);
    User student =
        accounts
            .lockedEnabledStudentByEmail(email, subject.getSchool().getId())
            .orElseThrow(
                () -> new NotFoundException("No enabled student with that email in this school"));
    return enroll(subject, student, joinedOn);
  }

  private Subject subjectForEnrollment(UUID id) {
    Subject subject = entityManager.find(Subject.class, id, LockModeType.PESSIMISTIC_WRITE);
    if (subject == null) throw new NotFoundException("Subject not found: " + id);
    access.writeGrades(subject);
    return subject;
  }

  private EnrollmentView enroll(Subject subject, User student, LocalDate joinedOn) {
    validStudent(subject, student);
    validJoinedOn(joinedOn, subject.getAcademicYear());
    if (enrollments.findBySubjectIdAndStudentId(subject.getId(), student.getId()).isPresent()) {
      throw new ConflictException(
          "The student already has an enrollment; update its status instead");
    }
    Enrollment enrollment = new Enrollment();
    enrollment.setSubject(subject);
    enrollment.setStudent(student);
    enrollment.setJoinedOn(joinedOn);
    enrollment.setStatus(EnrollmentStatus.ACTIVE);
    return mapper.toView(enrollments.saveAndFlush(enrollment));
  }

  @Transactional
  public EnrollmentView update(UUID id, EnrollmentUpdateCommand request) {
    Enrollment enrollment = find(id);
    Subject subject = enrollment.getSubject();
    entityManager.refresh(subject, LockModeType.PESSIMISTIC_WRITE);
    access.writeGrades(subject);
    entityManager.refresh(enrollment, LockModeType.PESSIMISTIC_WRITE);
    Versions.requireCurrent(request.version(), enrollment.getVersion());
    if (request.status() == EnrollmentStatus.ACTIVE) {
      if (request.leftOn() != null) {
        throw new IllegalArgumentException("An active enrollment cannot have leftOn");
      }
      accounts.lock(enrollment.getStudent());
      validStudent(subject, enrollment.getStudent());
      if (LocalDate.now().isAfter(subject.getAcademicYear().getEndDate())) {
        throw new IllegalArgumentException(
            "An enrollment cannot be reactivated after its academic year ends");
      }
      enrollment.setLeftOn(null);
      enrollment.setStatus(EnrollmentStatus.ACTIVE);
    } else {
      LocalDate today = LocalDate.now();
      LocalDate lastDay = subject.getAcademicYear().getEndDate();
      LocalDate leftOn =
          request.leftOn() == null ? (today.isAfter(lastDay) ? lastDay : today) : request.leftOn();
      if (leftOn.isBefore(enrollment.getJoinedOn())
          || leftOn.isAfter(today)
          || leftOn.isAfter(lastDay)) {
        throw new IllegalArgumentException(
            "leftOn must be between joinedOn and the earlier of today or the academic year end");
      }
      boolean invalidGrade =
          grades.exists(
              (root, query, cb) ->
                  cb.and(
                      cb.equal(root.get("subject").get("id"), subject.getId()),
                      cb.equal(root.get("student").get("id"), enrollment.getStudent().getId()),
                      cb.greaterThan(root.get("dateOfScore"), leftOn)));
      boolean invalidHistory =
          revisions.exists(
              (root, query, cb) ->
                  cb.and(
                      cb.equal(root.get("subject").get("id"), subject.getId()),
                      cb.equal(root.get("student").get("id"), enrollment.getStudent().getId()),
                      cb.greaterThan(root.get("dateOfScore"), leftOn)));
      if (invalidGrade || invalidHistory) {
        throw new ConflictException(
            "Withdrawal date cannot precede an existing grade or grade history");
      }
      enrollment.setLeftOn(leftOn);
      enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
    }
    return mapper.toView(enrollments.saveAndFlush(enrollment));
  }

  private void validStudent(Subject subject, User student) {
    actors.requireSchool(student.getSchool() == null ? null : student.getSchool().getId());
    if (student.getRole() != UserRole.STUDENT || !student.isEnabled()) {
      throw new IllegalArgumentException("Enrollment requires an enabled student account");
    }
    if (!subject.getTeacher().isEnabled()) {
      throw new IllegalArgumentException("Enrollment requires an enabled assigned teacher");
    }
    if (student.getSchool() == null
        || !subject.getSchool().getId().equals(student.getSchool().getId())) {
      throw new IllegalArgumentException("The student and subject must belong to the same school");
    }
  }

  private void validJoinedOn(LocalDate joinedOn, AcademicYear year) {
    if (joinedOn.isAfter(LocalDate.now())
        || joinedOn.isBefore(year.getStartDate())
        || joinedOn.isAfter(year.getEndDate())) {
      throw new IllegalArgumentException(
          "joinedOn must not be in the future and must fall within the academic year");
    }
  }

  private Enrollment find(UUID id) {
    return enrollments
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Enrollment not found: " + id));
  }
}
