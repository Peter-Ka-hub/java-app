package dev.schooldiary.identity.internal.persistence;

import dev.schooldiary.identity.*;
import dev.schooldiary.identity.internal.domain.User;
import dev.schooldiary.shared.access.CurrentActor;
import dev.schooldiary.shared.access.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
public class JpaAccountLookup implements AccountLookup, AccountCredentials {
  private final UserRepository users;
  private final CurrentActor actors;
  private final EntityManager entityManager;

  @Override
  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public Optional<User> managed(UUID id) {
    return users.findById(id);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<User> locked(UUID id) {
    User user = entityManager.find(User.class, id, LockModeType.PESSIMISTIC_WRITE);
    if (user != null) entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
    return Optional.ofNullable(user);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<User> lockedEnabledStudentByEmail(String email, UUID schoolId) {
    return users.findByEmailIgnoreCaseAndSchool_IdAndRoleAndEnabledTrue(
        email.strip(), schoolId, UserRole.STUDENT);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lock(User user) {
    entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public User currentManaged() {
    return users
        .findById(actors.current().id())
        .orElseThrow(() -> new AccessDeniedException("Account no longer available"));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Credentials> findCredentials(String email) {
    return users
        .findByEmailIgnoreCase(email)
        .map(
            user ->
                new Credentials(
                    user.getId(),
                    user.getEmail(),
                    user.getPasswordHash(),
                    user.getRole(),
                    user.isEnabled(),
                    user.getCredentialsVersion()));
  }
}
