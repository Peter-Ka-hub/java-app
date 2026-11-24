package dev.schooldiary.identity;

import dev.schooldiary.shared.access.Actor;
import jakarta.persistence.criteria.*;
import java.util.UUID;

/** Query seam for a roster owner; keeps filtering and pagination in the database. */
public interface TeacherAccountScope {
  Predicate visibleTo(
      Actor teacher, Path<UUID> accountId, CriteriaQuery<?> query, CriteriaBuilder builder);
}
