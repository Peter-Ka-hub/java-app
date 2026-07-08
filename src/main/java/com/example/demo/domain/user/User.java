package com.example.demo.domain.user;

import com.example.demo.domain.school.School;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "app_user")
public class User {
    @Id
    UUID id;

    String firstName;
    String lastName;
    @Enumerated(EnumType.STRING)
    UserRole role;
    LocalDate dateOfBirth;
    String PESEL;

    @ManyToOne
    School school;
}
