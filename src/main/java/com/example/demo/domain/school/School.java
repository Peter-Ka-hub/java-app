package com.example.demo.domain.school;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@FieldDefaults(level = AccessLevel.PRIVATE)
//@Getter
//@Setter
@Accessors
@Entity
public class School {

//    @ManyToOne
//    @JoinColumn(name = "voivodeships_uuid")
//    Voivodeships voivodeships;

    @Id
    java.util.UUID id;
    String name;
    LocalDate dateOfConstruction;
    @Enumerated(EnumType.STRING)
    SchoolType type;
//    String address;
}
