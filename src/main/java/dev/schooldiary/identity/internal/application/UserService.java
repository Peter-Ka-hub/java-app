package dev.schooldiary.identity.internal.application;

import dev.schooldiary.identity.AccountUsage;
import dev.schooldiary.identity.internal.domain.User;
import dev.schooldiary.identity.internal.persistence.UserRepository;
import dev.schooldiary.identity.internal.persistence.UserScopes;
import dev.schooldiary.organization.SchoolReferences;
import dev.schooldiary.organization.internal.domain.School;
import dev.schooldiary.shared.access.Actor;
import dev.schooldiary.shared.access.CurrentActor;
import dev.schooldiary.shared.access.UserRole;
import dev.schooldiary.shared.application.PageResult;
import dev.schooldiary.shared.concurrency.Versions;
import dev.schooldiary.shared.error.ConflictException;
import dev.schooldiary.shared.error.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
  private final UserRepository users;
  private final SchoolReferences schools;
  private final AccountUsage academicRecords;
  private final UserMapper mapper;
  private final PasswordEncoder passwords;
  private final CurrentActor access;
  private final UserScopes scopes;
  private final EntityManager entityManager;

  public PageResult<UserView> list(int page, int size) {
    var result =
        users.findAll(scopes.users(access.current()), PageRequest.of(page, size, Sort.by("id")));
    return PageResult.from(result, mapper::toView);
  }

  public UserView get(UUID id) {
    User user = find(id);
    if (access.current().id().equals(id)) return mapper.toView(user);
    if (!users.exists(scopes.users(access.current()).and((r, q, cb) -> cb.equal(r.get("id"), id))))
      CurrentActor.deny();
    return mapper.toView(user);
  }

  public UserView me() {
    return mapper.toView(find(access.current().id()));
  }

  @Transactional
  public UserView create(UserCommand request) {
    access.requireAdmin();
    School school = school(request.schoolId());
    UserRole role = UserRole.valueOf(request.role().name());
    validateScope(role, school);
    return mapper.toView(createAccount(request, school));
  }

  // The caller authorizes the account's role and school before entering this shared write path.
  User createAccount(UserCommand request, School school) {
    if (request.password() == null || request.password().isBlank())
      throw new IllegalArgumentException("password is required when creating a user");
    User user = mapper.toEntity(request);
    user.setSchool(school);
    user.setPasswordHash(passwords.encode(request.password()));
    user.setEnabled(request.enabled() == null || request.enabled());
    return users.saveAndFlush(user);
  }

  @Transactional
  public UserView update(UUID id, UserCommand request) {
    Actor actor = access.requireAdmin();
    User user = locked(id);
    access.requireSchool(user.schoolId());
    Versions.requireCurrent(request.version(), user.getVersion());
    School school = school(request.schoolId());
    UserRole role = UserRole.valueOf(request.role().name());
    validateScope(role, school);
    boolean contextChanged =
        user.getRole() != role
            || !Objects.equals(user.schoolId(), school == null ? null : school.getId());
    if (contextChanged && referenced(id))
      throw new ConflictException(
          "User role or school cannot change while academic records reference this account");
    if (actor.id().equals(id) && (contextChanged || Boolean.FALSE.equals(request.enabled())))
      throw new ConflictException("An administrator cannot remove their own administrative access");
    mapper.updateEntity(request, user);
    user.setSchool(school);
    boolean enabledChanged = request.enabled() != null && request.enabled() != user.isEnabled();
    if (request.enabled() != null) user.setEnabled(request.enabled());
    if (request.password() != null && request.password().isBlank())
      throw new IllegalArgumentException("password must not be blank");
    if (request.password() != null) user.setPasswordHash(passwords.encode(request.password()));
    if (request.password() != null || enabledChanged || contextChanged)
      user.setCredentialsVersion(user.getCredentialsVersion() + 1);
    return mapper.toView(users.saveAndFlush(user));
  }

  @Transactional
  public void delete(UUID id) {
    Actor actor = access.requireAdmin();
    User user = locked(id);
    access.requireSchool(user.schoolId());
    if (actor.id().equals(id))
      throw new ConflictException("An administrator cannot delete their own account");
    if (referenced(id))
      throw new ConflictException(
          "Account has academic records; disable it instead of deleting it");
    users.delete(user);
    users.flush();
  }

  private boolean referenced(UUID id) {
    return academicRecords.hasAcademicRecords(id);
  }

  private User find(UUID id) {
    return users.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
  }

  private User locked(UUID id) {
    User user = find(id);
    entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
    return user;
  }

  private School school(UUID id) {
    return id == null
        ? null
        : schools.school(id).orElseThrow(() -> new NotFoundException("School not found: " + id));
  }

  private void validateScope(UserRole role, School school) {
    if (school == null && role != UserRole.ADMIN)
      throw new IllegalArgumentException("schoolId is required for students and teachers");
    access.requireSchool(school == null ? null : school.getId());
  }
}
