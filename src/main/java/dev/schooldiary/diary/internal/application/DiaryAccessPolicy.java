package dev.schooldiary.diary.internal.application;

import dev.schooldiary.diary.internal.domain.*;
import dev.schooldiary.diary.internal.persistence.EnrollmentRepository;
import dev.schooldiary.shared.access.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Permissions owned by the subject, membership and assessment aggregate cluster. */
@Component
@RequiredArgsConstructor
public class DiaryAccessPolicy {
  private final CurrentActor actors;
  private final EnrollmentRepository enrollments;

  public void readSubject(Subject subject) {
    readSubject(actors.current(), subject);
  }

  public void readSubject(Actor actor, Subject subject) {
    if (actor.isGlobalAdmin()) return;
    requireSchool(actor, subject.getSchool().getId());
    if (actor.role() == UserRole.ADMIN) return;
    if (actor.role() == UserRole.TEACHER && subject.getTeacher().getId().equals(actor.id())) return;
    if (actor.role() == UserRole.STUDENT
        && enrollments.findBySubjectIdAndStudentId(subject.getId(), actor.id()).isPresent()) return;
    CurrentActor.deny();
  }

  public void writeGrades(Subject subject) {
    requireSubjectStaff(subject);
  }

  public void manageAttendance(Subject subject) {
    requireSubjectStaff(subject);
  }

  private void requireSubjectStaff(Subject subject) {
    Actor actor = actors.current();
    requireSchool(actor, subject.getSchool().getId());
    if (actor.role() == UserRole.ADMIN
        || (actor.role() == UserRole.TEACHER && subject.getTeacher().getId().equals(actor.id())))
      return;
    CurrentActor.deny();
  }

  public void readGrade(Subject subject, UUID studentId) {
    readGrade(actors.current(), subject, studentId);
  }

  public void readGrade(Actor actor, Subject subject, UUID studentId) {
    if (actor.role() == UserRole.STUDENT && !actor.id().equals(studentId)) CurrentActor.deny();
    readSubject(actor, subject);
  }

  public void readEnrollment(Enrollment enrollment) {
    Actor actor = actors.current();
    readSubject(actor, enrollment.getSubject());
    if (actor.role() == UserRole.STUDENT && !actor.id().equals(enrollment.getStudent().getId()))
      CurrentActor.deny();
  }

  private void requireSchool(Actor actor, UUID schoolId) {
    if (!actor.isGlobalAdmin() && (schoolId == null || !schoolId.equals(actor.schoolId())))
      CurrentActor.deny();
  }
}
