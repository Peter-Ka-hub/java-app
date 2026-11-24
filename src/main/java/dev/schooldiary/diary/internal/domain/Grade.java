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

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "grade")
public class Grade {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  Score score;

  @Column(nullable = false)
  LocalDate dateOfScore;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_id", nullable = false)
  User student;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subject_id", nullable = false)
  Subject subject;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "graded_by_id", nullable = false)
  User gradedBy;

  @Column(nullable = false, updatable = false)
  Instant createdAt;

  @Column(nullable = false)
  Instant updatedAt;

  @Version
  @Column(nullable = false)
  Long version;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
