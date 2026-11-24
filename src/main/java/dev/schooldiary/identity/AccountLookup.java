package dev.schooldiary.identity;

import dev.schooldiary.identity.internal.domain.User;
import java.util.Optional;
import java.util.UUID;

/** Deliberate JPA bridge. Managed references may only be used inside the caller's transaction. */
public interface AccountLookup {
  Optional<User> managed(UUID id);

  Optional<User> locked(UUID id);

  Optional<User> lockedEnabledStudentByEmail(String email, UUID schoolId);

  void lock(User account);

  User currentManaged();
}
