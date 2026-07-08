package com.example.demo.domain.subject;

import com.example.demo.domain.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.util.Set;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Accessors
@Entity
public class Subject {
    @Id
    UUID id;
    String name;

    @ManyToOne
    User teacher;

    @ManyToMany
    Set<User> students;
}
