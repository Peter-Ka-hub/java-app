package com.example.demo.modules.grade.repository;

import com.example.demo.modules.grade.domain.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GradeRepository extends JpaRepository<Grade, UUID> {
}