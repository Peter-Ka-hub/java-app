package dev.schooldiary.diary.internal.persistence;

import dev.schooldiary.diary.internal.domain.*;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GradeRevisionRepository
    extends JpaRepository<GradeRevision, UUID>, JpaSpecificationExecutor<GradeRevision> {
  List<GradeRevision> findByGradeIdOrderByChangedAtAsc(UUID gradeId);

  boolean existsByStudentId(UUID studentId);

  boolean existsBySubjectId(UUID subjectId);

  boolean existsByChangedById(UUID changedById);
}
