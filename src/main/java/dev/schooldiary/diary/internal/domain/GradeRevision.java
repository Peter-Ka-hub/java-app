package dev.schooldiary.diary.internal.domain;

import dev.schooldiary.identity.internal.domain.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Immutable audit snapshot retained after the live grade is deleted. */
@Entity
@Table(name = "grade_revision")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GradeRevision {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  // Deliberately no foreign key to grade: deletion must keep the audit trail.
  @Column(nullable = false, updatable = false)
  UUID gradeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50, updatable = false)
  Score score;

  @Column(nullable = false, updatable = false)
  LocalDate dateOfScore;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_id", nullable = false, updatable = false)
  User student;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subject_id", nullable = false, updatable = false)
  Subject subject;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "changed_by_id", nullable = false, updatable = false)
  User changedBy;

  @Column(nullable = false, updatable = false)
  Instant changedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  GradeRevisionAction action;

  @PrePersist
  void onCreate() {
    if (changedAt == null) {
      changedAt = Instant.now();
    }
  }
}
