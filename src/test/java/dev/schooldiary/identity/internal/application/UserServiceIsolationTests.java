package dev.schooldiary.identity.internal.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.schooldiary.identity.AccountUsage;
import dev.schooldiary.identity.internal.domain.User;
import dev.schooldiary.identity.internal.persistence.UserRepository;
import dev.schooldiary.identity.internal.persistence.UserScopes;
import dev.schooldiary.organization.SchoolReferences;
import dev.schooldiary.organization.internal.domain.School;
import dev.schooldiary.shared.access.*;
import dev.schooldiary.shared.error.ConflictException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceIsolationTests {
  @Test
  void protectsReferencedAccountsThroughAPortWithoutStartingHttpOrSpring() {
    var users = mock(UserRepository.class);
    var schools = mock(SchoolReferences.class);
    var usage = mock(AccountUsage.class);
    var mapper = mock(UserMapper.class);
    var passwords = mock(PasswordEncoder.class);
    var scopes = mock(UserScopes.class);
    var persistence = mock(EntityManager.class);
    CurrentActor actor = () -> new Actor(UUID.randomUUID(), UserRole.ADMIN, null);
    var service =
        new UserService(users, schools, usage, mapper, passwords, actor, scopes, persistence);
    var school = new School();
    school.setId(UUID.randomUUID());
    var user = new User();
    user.setId(UUID.randomUUID());
    user.setSchool(school);
    user.setRole(UserRole.TEACHER);
    user.setVersion(0L);
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    when(schools.school(school.getId())).thenReturn(Optional.of(school));
    when(usage.hasAcademicRecords(user.getId())).thenReturn(true);
    var change =
        new UserCommand(
            "Alex",
            "Example",
            "alex@example.test",
            UserRole.STUDENT,
            LocalDate.of(1990, 1, 1),
            school.getId(),
            null,
            null,
            0L);
    assertThatThrownBy(() -> service.update(user.getId(), change))
        .isInstanceOf(ConflictException.class)
        .hasMessage(
            "User role or school cannot change while academic records reference this account");
    verify(usage).hasAcademicRecords(user.getId());
    verify(users, never()).saveAndFlush(any(User.class));
    verifyNoInteractions(mapper, passwords, scopes);
  }
}
