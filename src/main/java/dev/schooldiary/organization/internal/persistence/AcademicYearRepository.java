package dev.schooldiary.organization.internal.persistence;

import dev.schooldiary.organization.internal.domain.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AcademicYearRepository
    extends JpaRepository<AcademicYear, UUID>, JpaSpecificationExecutor<AcademicYear> {
  List<AcademicYear> findBySchoolIdOrderByStartDateDesc(UUID schoolId);

  boolean existsBySchoolIdAndName(UUID schoolId, String name);

  boolean existsBySchoolIdAndNameAndIdNot(UUID schoolId, String name, UUID id);

  boolean existsBySchoolIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
      UUID schoolId, LocalDate endDate, LocalDate startDate);
}
