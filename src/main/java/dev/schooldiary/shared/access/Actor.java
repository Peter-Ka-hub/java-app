package dev.schooldiary.shared.access;

import java.util.UUID;

/** Immutable, transaction-independent identity of the current request. */
public record Actor(UUID id, UserRole role, UUID schoolId) {
  public boolean isGlobalAdmin() {
    return role == UserRole.ADMIN && schoolId == null;
  }
}
