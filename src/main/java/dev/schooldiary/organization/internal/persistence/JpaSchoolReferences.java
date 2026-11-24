package dev.schooldiary.organization.internal.persistence;

import dev.schooldiary.organization.SchoolReferences;
import dev.schooldiary.organization.internal.domain.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class JpaSchoolReferences implements SchoolReferences {
  private final SchoolRepository schools;
  private final AcademicYearRepository years;
  private final SchoolClassRepository classes;
  private final EntityManager entityManager;

  public Optional<School> school(UUID id) {
    return schools.findById(id);
  }

  public Optional<AcademicYear> year(UUID id) {
    return years.findById(id);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<AcademicYear> lockedYear(UUID id) {
    var year = years.findById(id);
    year.ifPresent(value -> entityManager.refresh(value, LockModeType.PESSIMISTIC_WRITE));
    return year;
  }

  public Optional<SchoolClass> schoolClass(UUID id) {
    return classes.findById(id);
  }
}
