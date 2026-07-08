package com.example.demo.domain.grade;

import com.example.demo.domain.subject.Subject;
import com.example.demo.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Accessors
@Entity
public class Grade {
    @Id
    UUID id;
    @Enumerated(EnumType.STRING)
    Score score;
    LocalDate dateOfScore;

    @ManyToOne
    User student;

    @ManyToOne
    Subject subject;
}
