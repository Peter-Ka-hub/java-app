package dev.schooldiary.identity.internal.domain;

import dev.schooldiary.organization.internal.domain.School;
import dev.schooldiary.shared.access.UserRole;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "app_user")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;

  @Column(nullable = false, length = 50)
  String firstName;

  @Column(nullable = false, length = 50)
  String lastName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  UserRole role;

  @Column(nullable = false)
  LocalDate dateOfBirth;

  @Column(length = 11)
  String pesel;

  @Column(nullable = false, unique = true, length = 50)
  String email;

  @Column(length = 255)
  String passwordHash;

  @Column(nullable = false)
  boolean enabled = true;

  @Column(nullable = false)
  long credentialsVersion;

  @Version
  @Column(nullable = false)
  Long version;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "school_id")
  School school;

  public UUID schoolId() {
    return school == null ? null : school.getId();
  }
}
