package dev.schooldiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.schooldiary.diary.internal.application.*;
import dev.schooldiary.diary.internal.domain.EnrollmentStatus;
import dev.schooldiary.diary.internal.domain.GradeRevisionAction;
import dev.schooldiary.diary.internal.domain.Score;
import dev.schooldiary.identity.AccountCredentials;
import dev.schooldiary.identity.AccountPrincipal;
import dev.schooldiary.shared.error.ConflictException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses real overlapping database transactions and observes PostgreSQL's lock wait. */
@Tag("postgres")
@SpringBootTest(
    properties = {
      "spring.profiles.active=test",
      "spring.datasource.driver-class-name=org.postgresql.Driver",
      "app.bootstrap.enabled=true",
      "app.bootstrap.admin-email=admin@tests.local",
      "app.bootstrap.admin-password=Test-Admin-123!",
      "app.security.login.max-attempts=1000"
    })
@Import(TestcontainersConfiguration.class)
class PostgreSQLConcurrencyTests extends ApiTestSupport {
  @Autowired GradeService grades;
  @Autowired EnrollmentService enrollments;
  @Autowired AccountCredentials credentials;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcTemplate jdbc;

  @Test
  void concurrentCorrectionsCannotOverwriteTheCommittedGradeOrCreateAFalseRevision()
      throws Exception {
    Fixture fixture = fixture();
    var initial =
        grades.create(
            new GradeCommand(
                Score.B, LocalDate.now(), fixture.studentId(), fixture.subjectId(), null));

    var outcomes =
        overlap(
            () ->
                grades.update(
                    initial.id(),
                    new GradeCommand(
                        Score.A,
                        LocalDate.now(),
                        fixture.studentId(),
                        fixture.subjectId(),
                        initial.version())),
            () ->
                grades.update(
                    initial.id(),
                    new GradeCommand(
                        Score.C,
                        LocalDate.now(),
                        fixture.studentId(),
                        fixture.subjectId(),
                        initial.version())));

    assertThat(outcomes.first().error()).isNull();
    assertThat(outcomes.second().error()).isInstanceOf(ConflictException.class);
    var stored = grades.get(initial.id());
    assertThat(stored.score()).isEqualTo(Score.A);
    assertThat(stored.version()).isEqualTo(initial.version() + 1);
    assertThat(grades.history(initial.id()))
        .extracting(GradeRevisionView::score)
        .containsExactly(Score.B, Score.A);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PUT", "DELETE"})
  void mutationWaitingForADeletedGradeReturnsNotFoundAndPreservesHistory(String method)
      throws Exception {
    Fixture fixture = fixture();
    var initial =
        grades.create(
            new GradeCommand(
                Score.B, LocalDate.now(), fixture.studentId(), fixture.subjectId(), null));
    String path = "/api/grades/" + initial.id();
    var correction =
        gradeRequest(fixture.studentId().toString(), fixture.subjectId().toString(), "A");
    correction.put("version", initial.version());
    var first = delete(path).with(csrf()).with(actor(ADMIN_EMAIL, "ADMIN"));
    var second =
        ("PUT".equals(method) ? json(put(path), correction) : delete(path).with(csrf()))
            .with(actor(ADMIN_EMAIL, "ADMIN"));

    var outcomes =
        overlap(
            () -> mvc.perform(first).andReturn().getResponse().getStatus(),
            () -> mvc.perform(second).andReturn().getResponse().getStatus());

    assertThat(outcomes.first().error()).isNull();
    assertThat(outcomes.first().value()).isEqualTo(204);
    assertThat(outcomes.second().error()).isNull();
    assertThat(outcomes.second().value()).isEqualTo(404);
    assertThat(grades.history(initial.id()))
        .extracting(GradeRevisionView::action)
        .containsExactly(GradeRevisionAction.CREATED, GradeRevisionAction.DELETED);
    mvc.perform(get(path)).andExpect(status().isNotFound());
  }

  @ParameterizedTest(name = "grade transaction commits first: {0}")
  @ValueSource(booleans = {true, false})
  void gradingAndWithdrawalKeepMembershipDatesAndAuditConsistent(boolean gradeFirst)
      throws Exception {
    Fixture fixture = fixture();
    Callable<Object> createGrade =
        () ->
            grades.create(
                new GradeCommand(
                    Score.B, LocalDate.now(), fixture.studentId(), fixture.subjectId(), null));
    Callable<Object> withdraw =
        () ->
            enrollments.update(
                fixture.enrollmentId(),
                new EnrollmentUpdateCommand(
                    EnrollmentStatus.WITHDRAWN, LocalDate.now().minusDays(1), 0L));

    var outcomes = gradeFirst ? overlap(createGrade, withdraw) : overlap(withdraw, createGrade);

    assertThat(outcomes.first().error()).isNull();
    assertThat(outcomes.second().error())
        .isInstanceOf(gradeFirst ? ConflictException.class : IllegalArgumentException.class);
    assertThat(enrollments.get(fixture.enrollmentId()).status())
        .isEqualTo(gradeFirst ? EnrollmentStatus.ACTIVE : EnrollmentStatus.WITHDRAWN);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from grade where subject_id = ? and student_id = ?",
                Long.class,
                fixture.subjectId(),
                fixture.studentId()))
        .isEqualTo(gradeFirst ? 1L : 0L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from grade_revision where subject_id = ? and student_id = ?",
                Long.class,
                fixture.subjectId(),
                fixture.studentId()))
        .isEqualTo(gradeFirst ? 1L : 0L);
  }

  private Fixture fixture() throws Exception {
    String school = createSchool();
    String year = createYear(school);
    String teacher = createUser("TEACHER", school);
    String student = createUser("STUDENT", school);
    String subject = createSubject(teacher, school, year);
    String enrollment = enroll(student, subject);
    // MockMvc clears the thread-local context after each HTTP request. Direct service calls below
    // need their own authenticated context, just like each transaction worker does.
    authenticate(adminPrincipal());
    return new Fixture(
        UUID.fromString(student), UUID.fromString(subject), UUID.fromString(enrollment));
  }

  private Outcomes overlap(Callable<?> first, Callable<?> second) throws Exception {
    var principal = adminPrincipal();
    var applied = new CompletableFuture<Integer>();
    var commit = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      Future<Outcome> firstResult =
          workers.submit(
              () -> asActor(principal, () -> holdFirstTransaction(first, applied, commit)));
      try {
        int firstPid = applied.get(15, TimeUnit.SECONDS);
        Future<Outcome> secondResult = workers.submit(() -> asActor(principal, second));
        boolean blocked = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
          blocked =
              jdbc.queryForObject(
                  "select exists (select 1 from pg_stat_activity where wait_event_type = 'Lock'"
                      + " and ? = any(pg_blocking_pids(pid)))",
                  Boolean.class,
                  firstPid);
          if (blocked) break;
          Thread.sleep(20);
        }
        assertThat(blocked)
            .as("second real PostgreSQL transaction waits for the first transaction's row lock")
            .isTrue();
        commit.countDown();
        return new Outcomes(
            firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS));
      } finally {
        commit.countDown();
      }
    }
  }

  private Object holdFirstTransaction(
      Callable<?> first, CompletableFuture<Integer> applied, CountDownLatch commit) {
    var transaction = new TransactionTemplate(transactions);
    transaction.setTimeout(20);
    return transaction.execute(
        status -> {
          try {
            Object result = first.call();
            applied.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
            if (!commit.await(15, TimeUnit.SECONDS))
              throw new IllegalStateException("Timed out waiting to commit first mutation");
            return result;
          } catch (Exception exception) {
            applied.completeExceptionally(exception);
            throw new IllegalStateException(exception);
          }
        });
  }

  private Outcome asActor(AccountPrincipal principal, Callable<?> action) {
    authenticate(principal);
    try {
      return new Outcome(action.call(), null);
    } catch (Exception exception) {
      return new Outcome(null, exception);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private AccountPrincipal adminPrincipal() {
    var principal = new AccountPrincipal(credentials.findCredentials(ADMIN_EMAIL).orElseThrow());
    principal.eraseCredentials();
    return principal;
  }

  private void authenticate(AccountPrincipal principal) {
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities()));
    SecurityContextHolder.setContext(context);
  }

  private record Fixture(UUID studentId, UUID subjectId, UUID enrollmentId) {}

  private record Outcome(Object value, Throwable error) {}

  private record Outcomes(Outcome first, Outcome second) {}
}
