package dev.schooldiary.bootstrap;

import dev.schooldiary.diary.internal.application.*;
import dev.schooldiary.diary.internal.domain.Score;
import dev.schooldiary.identity.AccountCredentials;
import dev.schooldiary.identity.AccountPrincipal;
import dev.schooldiary.identity.internal.application.*;
import dev.schooldiary.organization.internal.application.*;
import dev.schooldiary.organization.internal.domain.SchoolType;
import dev.schooldiary.shared.access.UserRole;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Composition root for opt-in demo fixtures; never used as an inter-module business API. */
@Component
@Order(10)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {
  private final AccountCredentials users;
  private final SchoolService schools;
  private final UserService accounts;
  private final SubjectService subjects;
  private final AcademicService academics;
  private final EnrollmentService enrollments;
  private final GradeService grades;

  @Value("${app.bootstrap.admin-email:}")
  private String adminEmail;

  @Value("${app.demo.user-password:}")
  private String password;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (password.isBlank() || password.length() < 12 || password.length() > 128)
      throw new IllegalArgumentException(
          "Demo fixtures require DEMO_USER_PASSWORD with 12–128 characters");
    if (users.findCredentials("teacher@demo.local").isPresent()) return;
    var original = SecurityContextHolder.getContext();
    try {
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication(adminEmail.strip()));
      SecurityContextHolder.setContext(context);
      var school =
          schools.create(
              new SchoolCommand(
                  "Demo Secondary School",
                  "1 Example Street",
                  LocalDate.of(2000, 9, 1),
                  SchoolType.SECONDARY,
                  null));
      LocalDate today = LocalDate.now();
      int startYear = today.getMonthValue() >= 9 ? today.getYear() : today.getYear() - 1;
      var year =
          academics.createYear(
              new AcademicYearCommand(
                  startYear + "/" + (startYear + 1),
                  school.id(),
                  LocalDate.of(startYear, 9, 1),
                  LocalDate.of(startYear + 1, 8, 31),
                  null));
      var schoolClass = academics.createClass(new ClassCommand("1A", school.id(), year.id(), null));
      var teacher =
          accounts.create(
              new UserCommand(
                  "Alex",
                  "Teacher",
                  "teacher@demo.local",
                  UserRole.TEACHER,
                  LocalDate.of(1990, 1, 1),
                  school.id(),
                  password,
                  null,
                  null));
      var student =
          accounts.create(
              new UserCommand(
                  "Sam",
                  "Student",
                  "student@demo.local",
                  UserRole.STUDENT,
                  LocalDate.of(2010, 1, 1),
                  school.id(),
                  password,
                  null,
                  null));
      var subject =
          subjects.create(
              new SubjectCommand(
                  "Mathematics", teacher.id(), school.id(), year.id(), schoolClass.id(), null));
      enrollments.create(new EnrollmentCommand(subject.id(), student.id(), today));
      context.setAuthentication(authentication(teacher.email()));
      grades.create(new GradeCommand(Score.B, today, student.id(), subject.id(), null));
    } finally {
      SecurityContextHolder.setContext(original);
    }
  }

  private UsernamePasswordAuthenticationToken authentication(String email) {
    var principal =
        new AccountPrincipal(
            users
                .findCredentials(email)
                .orElseThrow(() -> new IllegalStateException("Demo account missing")));
    principal.eraseCredentials();
    return UsernamePasswordAuthenticationToken.authenticated(
        principal, null, principal.getAuthorities());
  }
}
