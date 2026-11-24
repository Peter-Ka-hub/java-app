package dev.schooldiary.diary.internal.domain;

import dev.schooldiary.identity.internal.domain.User;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "attendance_entry",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_attendance_entry_lesson_student",
            columnNames = {"lesson_id", "student_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "lesson_id", nullable = false, updatable = false)
  private AttendanceLesson lesson;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_id", nullable = false, updatable = false)
  private User student;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private AttendanceStatus status;

  AttendanceEntry(AttendanceLesson lesson, User student) {
    this.lesson = lesson;
    this.student = student;
  }

  public void changeStatus(AttendanceStatus status) {
    this.status = status;
  }
}
