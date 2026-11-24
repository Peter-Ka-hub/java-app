package com.example.demo.modules.school.repository;

import com.example.demo.modules.school.domain.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SchoolRepository extends JpaRepository<School, UUID> {
}