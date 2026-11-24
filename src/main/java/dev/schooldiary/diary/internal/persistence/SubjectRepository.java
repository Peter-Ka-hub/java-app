package dev.schooldiary.diary.internal.persistence;

import dev.schooldiary.diary.internal.domain.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SubjectRepository
    extends JpaRepository<Subject, UUID>, JpaSpecificationExecutor<Subject> {
  boolean existsByTeacherId(UUID teacherId);

  boolean existsByAcademicYearId(UUID academicYearId);

  boolean existsBySchoolClassId(UUID schoolClassId);

  boolean existsBySchoolId(UUID schoolId);
}
