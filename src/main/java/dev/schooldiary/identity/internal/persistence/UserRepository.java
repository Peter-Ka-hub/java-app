package dev.schooldiary.identity.internal.persistence;

import dev.schooldiary.identity.internal.domain.User;
import dev.schooldiary.shared.access.UserRole;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<User> findByEmailIgnoreCaseAndSchool_IdAndRoleAndEnabledTrue(
      String email, UUID schoolId, UserRole role);
}
