package dev.schooldiary.organization;

import dev.schooldiary.organization.internal.domain.*;
import java.util.Optional;
import java.util.UUID;

/**
 * Deliberate read/lock JPA bridge for existing cross-module relationships, not a repository API.
 */
public interface SchoolReferences {
  Optional<School> school(UUID id);

  Optional<AcademicYear> year(UUID id);

  Optional<AcademicYear> lockedYear(UUID id);

  Optional<SchoolClass> schoolClass(UUID id);
}
