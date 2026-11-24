package dev.schooldiary.diary.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import dev.schooldiary.diary.internal.domain.*;
import dev.schooldiary.diary.internal.persistence.*;
import dev.schooldiary.identity.AccountLookup;
import dev.schooldiary.identity.internal.domain.User;
import dev.schooldiary.organization.internal.domain.School;
import dev.schooldiary.shared.access.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class GradeHistoryAccessTests {
  private final CurrentActor actors = mock(CurrentActor.class);
  private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
  private final GradeRevisionRepository revisions = mock(GradeRevisionRepository.class);
  private final GradeService service =
      new GradeService(
          mock(GradeRepository.class),
          revisions,
          enrollments,
          mock(GradeMapper.class),
          new DiaryAccessPolicy(actors, enrollments),
          actors,
          mock(GradeScopes.class),
          mock(EntityManager.class),
          mock(AccountLookup.class));

  @Test
  void historyResolvesTheActorAndEnrollmentOnceForOneHundredRevisions() {
    var school = new School();
    school.setId(UUID.randomUUID());
    var subject = subject(school);
    var student = account();
    when(actors.current()).thenReturn(new Actor(student.getId(), UserRole.STUDENT, school.getId()));
    when(enrollments.findBySubjectIdAndStudentId(subject.getId(), student.getId()))
        .thenReturn(Optional.of(new Enrollment()));
    UUID gradeId = UUID.randomUUID();
    var history = new ArrayList<GradeRevision>();
    for (int index = 0; index < 100; index++) history.add(revision(gradeId, subject, student));
    when(revisions.findByGradeIdOrderByChangedAtAsc(gradeId)).thenReturn(history);

    var result = service.history(gradeId);

    assertThat(result).hasSize(100);
    assertThat(result).allSatisfy(view -> assertThat(view.studentId()).isEqualTo(student.getId()));
    verify(actors, times(1)).current();
    verify(enrollments, times(1)).findBySubjectIdAndStudentId(subject.getId(), student.getId());
    verifyNoMoreInteractions(actors, enrollments);
  }

  @Test
  void historyStillRejectsAnUnauthorizedStudentInALaterRevision() {
    var school = new School();
    school.setId(UUID.randomUUID());
    var subject = subject(school);
    var student = account();
    var otherStudent = account();
    when(actors.current()).thenReturn(new Actor(student.getId(), UserRole.STUDENT, school.getId()));
    when(enrollments.findBySubjectIdAndStudentId(subject.getId(), student.getId()))
        .thenReturn(Optional.of(new Enrollment()));
    UUID gradeId = UUID.randomUUID();
    when(revisions.findByGradeIdOrderByChangedAtAsc(gradeId))
        .thenReturn(
            java.util.List.of(
                revision(gradeId, subject, student), revision(gradeId, subject, otherStudent)));

    assertThatThrownBy(() -> service.history(gradeId)).isInstanceOf(AccessDeniedException.class);
    verify(actors, times(1)).current();
  }

  @Test
  void historyStillChecksTheSchoolOfEveryDistinctSubject() {
    var school = new School();
    school.setId(UUID.randomUUID());
    var anotherSchool = new School();
    anotherSchool.setId(UUID.randomUUID());
    var subject = subject(school);
    var otherSubject = subject(anotherSchool);
    var student = account();
    when(actors.current()).thenReturn(new Actor(UUID.randomUUID(), UserRole.ADMIN, school.getId()));
    UUID gradeId = UUID.randomUUID();
    when(revisions.findByGradeIdOrderByChangedAtAsc(gradeId))
        .thenReturn(
            java.util.List.of(
                revision(gradeId, subject, student), revision(gradeId, otherSubject, student)));

    assertThatThrownBy(() -> service.history(gradeId)).isInstanceOf(AccessDeniedException.class);
    verify(actors, times(1)).current();
    verifyNoInteractions(enrollments);
  }

  private Subject subject(School school) {
    var subject = new Subject();
    subject.setId(UUID.randomUUID());
    subject.setSchool(school);
    subject.setTeacher(account());
    return subject;
  }

  private User account() {
    var user = new User();
    user.setId(UUID.randomUUID());
    return user;
  }

  private GradeRevision revision(UUID gradeId, Subject subject, User student) {
    var revision = new GradeRevision();
    revision.setId(UUID.randomUUID());
    revision.setGradeId(gradeId);
    revision.setSubject(subject);
    revision.setStudent(student);
    revision.setChangedBy(subject.getTeacher());
    revision.setScore(Score.B);
    revision.setDateOfScore(LocalDate.of(2026, 1, 1));
    revision.setChangedAt(Instant.parse("2026-01-01T10:00:00Z"));
    revision.setAction(GradeRevisionAction.UPDATED);
    return revision;
  }
}
