package dev.schooldiary.organization.internal.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(
    name = "school_class",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_school_class_year_name",
            columnNames = {"academic_year_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SchoolClass {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "school_id", nullable = false)
  School school;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "academic_year_id", nullable = false)
  AcademicYear academicYear;

  @Column(nullable = false, length = 50)
  String name;

  @Version
  @Column(nullable = false)
  Long version;
}
