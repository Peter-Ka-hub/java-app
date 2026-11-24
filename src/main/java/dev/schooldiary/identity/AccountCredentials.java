package dev.schooldiary.identity;

import dev.schooldiary.shared.access.UserRole;
import java.util.Optional;
import java.util.UUID;

/** Technical authentication snapshot; never a public HTTP response. */
public interface AccountCredentials {
  Optional<Credentials> findCredentials(String email);

  record Credentials(
      UUID id,
      String email,
      String passwordHash,
      UserRole role,
      boolean enabled,
      long credentialsVersion) {}
}
