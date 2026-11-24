package dev.schooldiary.diary.internal.domain;

import dev.schooldiary.identity.internal.domain.User;
import dev.schooldiary.organization.internal.domain.SchoolClass;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "attendance_lesson",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_attendance_lesson_subject_date_number",
            columnNames = {"subject_id", "lesson_date", "lesson_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceLesson {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subject_id", nullable = false, updatable = false)
  private Subject subject;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "school_class_id", updatable = false)
  private SchoolClass schoolClass;

  @Column(nullable = false, updatable = false)
  private LocalDate lessonDate;

  @Column(nullable = false, updatable = false)
  private int lessonNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AttendanceLessonState state;

  @Column(nullable = false)
  private Instant lastSavedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  @OneToMany(mappedBy = "lesson", cascade = CascadeType.PERSIST)
  @Getter(AccessLevel.NONE)
  private List<AttendanceEntry> entries = new ArrayList<>();

  public AttendanceLesson(
      Subject subject, LocalDate lessonDate, int lessonNumber, List<User> students, Instant now) {
    this.subject = subject;
    this.schoolClass = subject.getSchoolClass();
    this.lessonDate = lessonDate;
    this.lessonNumber = lessonNumber;
    this.state = AttendanceLessonState.DRAFT;
    this.lastSavedAt = now.truncatedTo(ChronoUnit.MICROS);
    for (User student : students) {
      entries.add(new AttendanceEntry(this, student));
    }
  }

  public Instant markSaved(AttendanceLessonState state, Instant now) {
    this.state = state;
    Instant savedAt = now.truncatedTo(ChronoUnit.MICROS);
    // Dirty the versioned lesson even if only entries changed or the payload was identical.
    lastSavedAt = savedAt.isAfter(lastSavedAt) ? savedAt : lastSavedAt.plus(1, ChronoUnit.MICROS);
    return lastSavedAt;
  }
}
