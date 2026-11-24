package com.example.demo.modules.user.domain;

import com.example.demo.modules.school.domain.School;
import jakarta.persistence.*;
import lombok.*;
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
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    String firstName;
    String lastName;
    @Enumerated(EnumType.STRING)
    UserRole role;
    LocalDate dateOfBirth;
    String pesel;

    @Column(nullable = false, unique = true)
    String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    School school;
}