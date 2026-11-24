package dev.schooldiary.organization.internal.domain;

import jakarta.persistence.*;
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
@Table(name = "school")
public class School {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  @Column(nullable = false, length = 50)
  String name;

  @Column(nullable = false)
  LocalDate dateOfConstruction;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  SchoolType type;

  @Column(nullable = false, length = 200)
  String address;

  @Version
  @Column(nullable = false)
  Long version;
}
