package com.example.demo.modules.subject.repository;

import com.example.demo.modules.subject.domain.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {
}