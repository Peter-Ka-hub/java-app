package dev.schooldiary.shared.access;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/** Common role/tenant checks; record-specific decisions belong to the owning module. */
public interface CurrentActor {
  Actor current();

  default Actor requireAdmin() {
    Actor actor = current();
    if (actor.role() != UserRole.ADMIN) deny();
    return actor;
  }

  default void requireSchool(UUID schoolId) {
    Actor actor = current();
    if (!actor.isGlobalAdmin() && (schoolId == null || !schoolId.equals(actor.schoolId()))) deny();
  }

  default void requireGlobalAdmin() {
    if (!current().isGlobalAdmin()) deny();
  }

  static void deny() {
    throw new AccessDeniedException("Access denied");
  }
}
