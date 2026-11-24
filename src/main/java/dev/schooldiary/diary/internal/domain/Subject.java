package dev.schooldiary.diary.internal.domain;

import dev.schooldiary.identity.internal.domain.User;
import dev.schooldiary.organization.internal.domain.AcademicYear;
import dev.schooldiary.organization.internal.domain.School;
import dev.schooldiary.organization.internal.domain.SchoolClass;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subject")
public class Subject {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  @Column(nullable = false, length = 50)
  String name;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "teacher_id", nullable = false)
  User teacher;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "school_id", nullable = false)
  School school;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "academic_year_id", nullable = false)
  AcademicYear academicYear;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "school_class_id")
  SchoolClass schoolClass;

  @Version
  @Column(nullable = false)
  Long version;
}
