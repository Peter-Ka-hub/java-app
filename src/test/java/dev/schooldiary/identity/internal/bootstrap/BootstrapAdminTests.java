package dev.schooldiary.identity.internal.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import dev.schooldiary.identity.internal.domain.User;
import dev.schooldiary.identity.internal.persistence.UserRepository;
import dev.schooldiary.shared.access.UserRole;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class BootstrapAdminTests {
  private static final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();
  private final UserRepository users = mock(UserRepository.class);
  private final PasswordEncoder passwords = mock(PasswordEncoder.class);
  private BootstrapAdmin bootstrap;

  @AfterAll
  static void closeValidatorFactory() {
    validators.close();
  }

  @Test
  void disabledBootstrapDoesNotRequireCredentialsOrTouchAccounts() {
    configure(false, "", "");

    bootstrap.run(new DefaultApplicationArguments());

    verifyNoInteractions(users, passwords);
  }

  @Test
  void enabledBootstrapRejectsMissingCredentialsInsteadOfStartingWithoutAnAdministrator() {
    configure(true, "", "");

    assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BOOTSTRAP_ADMIN_EMAIL")
        .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD");
    verifyNoInteractions(users, passwords);
  }

  @Test
  void enabledBootstrapRejectsIncompleteOrInvalidCredentialsBeforeAccessingTheDatabase() {
    for (String[] credentials :
        new String[][] {
          {"admin@example.test", ""},
          {"", "Long-Password-123!"},
          {"invalid-email", "Long-Password-123!"},
          {"admin@example.test", "short"},
          {"admin@example.test", "x".repeat(129)},
          {"admin@example.test", " ".repeat(12)}
        }) {
      configure(true, credentials[0], credentials[1]);

      assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
          .isInstanceOf(IllegalArgumentException.class);
    }
    verifyNoInteractions(users, passwords);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"@", "a@@example.test", "an-address-that-is-exactly-51-characters@school.org"})
  void rejectsAnEmailThatLoginWouldRejectWithoutPersistingIt(String email) {
    var password = "Long-Password-123!";
    configure(true, email, password);

    assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BOOTSTRAP_ADMIN_EMAIL")
        .hasMessageNotContaining(password);
    verifyNoInteractions(users, passwords);
  }

  @Test
  void createsAnEnabledGlobalAdministratorWithNormalizedEmailAndEncodedPassword() {
    configure(true, "  Admin@Example.Test  ", "Long-Password-123!");
    when(passwords.encode("Long-Password-123!")).thenReturn("encoded-password");

    bootstrap.run(new DefaultApplicationArguments());

    var saved = ArgumentCaptor.forClass(User.class);
    verify(users).findByEmailIgnoreCase("admin@example.test");
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.test");
    assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(saved.getValue().getSchool()).isNull();
    assertThat(saved.getValue().isEnabled()).isTrue();
    assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded-password");
    verify(passwords).encode("Long-Password-123!");
  }

  @Test
  void restartingBootstrapDoesNotResetAnExistingAccountsPasswordOrPermissions() {
    configure(true, "admin@example.test", "Different-Password-123!");
    var existing = new User();
    existing.setPasswordHash("existing-hash");
    existing.setRole(UserRole.STUDENT);
    when(users.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.of(existing));

    bootstrap.run(new DefaultApplicationArguments());

    assertThat(existing.getPasswordHash()).isEqualTo("existing-hash");
    assertThat(existing.getRole()).isEqualTo(UserRole.STUDENT);
    verify(users, never()).save(any());
    verifyNoInteractions(passwords);
  }

  private void configure(boolean enabled, String email, String password) {
    bootstrap =
        new BootstrapAdmin(users, passwords, validators.getValidator(), enabled, email, password);
  }
}
