package dev.schooldiary.organization.internal.persistence;

import dev.schooldiary.organization.internal.domain.*;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SchoolClassRepository
    extends JpaRepository<SchoolClass, UUID>, JpaSpecificationExecutor<SchoolClass> {
  List<SchoolClass> findByAcademicYearIdOrderByName(UUID academicYearId);

  boolean existsByAcademicYearIdAndName(UUID academicYearId, String name);

  boolean existsByAcademicYearIdAndNameAndIdNot(UUID academicYearId, String name, UUID id);
}
