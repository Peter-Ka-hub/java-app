package dev.schooldiary.diary.internal.domain;

import dev.schooldiary.identity.internal.domain.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(
    name = "enrollment",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_enrollment_subject_student",
            columnNames = {"subject_id", "student_id"}))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Enrollment {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subject_id", nullable = false)
  Subject subject;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_id", nullable = false)
  User student;

  @Column(nullable = false)
  LocalDate joinedOn;

  LocalDate leftOn;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  EnrollmentStatus status = EnrollmentStatus.ACTIVE;

  @Version
  @Column(nullable = false)
  Long version;
}
