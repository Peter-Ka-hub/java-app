package dev.schooldiary.identity.internal.bootstrap;

import dev.schooldiary.identity.internal.domain.User;
import dev.schooldiary.identity.internal.persistence.UserRepository;
import dev.schooldiary.shared.access.UserRole;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(0)
public class BootstrapAdmin implements ApplicationRunner {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final Validator validator;
  private final boolean enabled;

  @NotBlank
  @Email
  @Size(max = 50)
  private final String email;

  @NotBlank
  @Size(min = 12, max = 128)
  private final String password;

  public BootstrapAdmin(
      UserRepository users,
      PasswordEncoder passwords,
      Validator validator,
      @Value("${app.bootstrap.enabled:true}") boolean enabled,
      @Value("${app.bootstrap.admin-email:}") String email,
      @Value("${app.bootstrap.admin-password:}") String password) {
    this.users = users;
    this.passwords = passwords;
    this.validator = validator;
    this.enabled = enabled;
    this.email = email.strip().toLowerCase(Locale.ROOT);
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!enabled) return;
    if (!validator.validate(this).isEmpty())
      throw new IllegalArgumentException(
          "When BOOTSTRAP_ADMIN_ENABLED=true, set BOOTSTRAP_ADMIN_EMAIL to a valid email"
              + " of at most 50 characters and BOOTSTRAP_ADMIN_PASSWORD to a nonblank password"
              + " of 12–128 characters");
    if (users.findByEmailIgnoreCase(email).isPresent()) return;
    var admin = new User();
    admin.setFirstName("System");
    admin.setLastName("Administrator");
    admin.setEmail(email);
    admin.setPasswordHash(passwords.encode(password));
    admin.setRole(UserRole.ADMIN);
    admin.setDateOfBirth(LocalDate.of(1990, 1, 1));
    admin.setEnabled(true);
    users.save(admin);
  }
}
