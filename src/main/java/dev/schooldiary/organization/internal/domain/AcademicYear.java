package dev.schooldiary.organization.internal.domain;

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
    name = "academic_year",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_academic_year_school_name",
            columnNames = {"school_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AcademicYear {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "school_id", nullable = false)
  School school;

  @Column(nullable = false, length = 50)
  String name;

  @Column(nullable = false)
  LocalDate startDate;

  @Column(nullable = false)
  LocalDate endDate;

  @Version
  @Column(nullable = false)
  Long version;
}
