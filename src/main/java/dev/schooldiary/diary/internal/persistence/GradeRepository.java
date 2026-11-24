package dev.schooldiary.diary.internal.persistence;

import dev.schooldiary.diary.internal.domain.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GradeRepository
    extends JpaRepository<Grade, UUID>, JpaSpecificationExecutor<Grade> {
  boolean existsByStudentId(UUID studentId);

  boolean existsBySubjectId(UUID subjectId);

  boolean existsByGradedById(UUID gradedById);
}
