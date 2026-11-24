package dev.schooldiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
class PostgreSQLInvitationConcurrencyTests extends ApiTestSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;

  @Test
  void twoOverlappingActivationRequestsCreateExactlyOneAccount() throws Exception {
    String email = uniqueEmail();
    String schoolId = createSchool();
    var created =
        create("/api/invitations", Map.of("email", email, "role", "STUDENT", "schoolId", schoolId));
    UUID id = UUID.fromString(created.get("invitation").get("id").asText());
    String token = created.get("token").asText();

    var transaction = new TransactionTemplate(transactions);
    transaction.setTimeout(25);
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    String firstConnection = "invite-race-" + UUID.randomUUID();
    String secondConnection = "invite-race-" + UUID.randomUUID();

    try (var workers = Executors.newFixedThreadPool(3)) {
      var blocker =
          workers.submit(
              () ->
                  transaction.executeWithoutResult(
                      ignored -> {
                        jdbc.queryForObject(
                            "select id from account_invitation where id = ? for update",
                            UUID.class,
                            id);
                        locked.countDown();
                        try {
                          if (!release.await(15, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                "Timed out holding invitation row lock");
                          }
                        } catch (InterruptedException interrupted) {
                          Thread.currentThread().interrupt();
                          throw new IllegalStateException(
                              "Interrupted while holding row lock", interrupted);
                        }
                      }));

      Future<Integer> first;
      Future<Integer> second;
      try {
        assertThat(locked.await(10, TimeUnit.SECONDS)).as("invitation row is locked").isTrue();
        first = workers.submit(() -> activate(transaction, firstConnection, token, "Alicja"));
        second = workers.submit(() -> activate(transaction, secondConnection, token, "Jan"));

        assertThat(waitForTwoBlockedActivations(firstConnection, secondConnection))
            .as("both HTTP activation requests wait for the invitation row lock")
            .isTrue();
      } finally {
        release.countDown();
      }

      blocker.get(10, TimeUnit.SECONDS);
      int firstStatus = first.get(10, TimeUnit.SECONDS);
      int secondStatus = second.get(10, TimeUnit.SECONDS);
      assertThat(new int[] {firstStatus, secondStatus}).containsExactlyInAnyOrder(204, 404);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from app_user where lower(email) = ?", Long.class, email))
          .isEqualTo(1L);
      assertThat(
              jdbc.queryForObject(
                  "select accepted_at is not null and revoked_at is null from account_invitation"
                      + " where id = ?",
                  Boolean.class,
                  id))
          .isTrue();
      var account = credentials.findCredentials(email).orElseThrow();
      var user = read("/api/users/" + account.id());
      assertThat(user.get("firstName").asText()).isEqualTo(firstStatus == 204 ? "Alicja" : "Jan");
      assertThat(user.get("role").asText()).isEqualTo("STUDENT");
      assertThat(user.get("schoolId").asText()).isEqualTo(schoolId);
    }
  }

  private boolean waitForTwoBlockedActivations(String firstConnection, String secondConnection)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      Integer blocked =
          jdbc.queryForObject(
              "select count(*) from pg_stat_activity where application_name in (?, ?) and"
                  + " wait_event_type = 'Lock'",
              Integer.class,
              firstConnection,
              secondConnection);
      if (blocked == 2) return true;
      Thread.sleep(20);
    }
    return false;
  }

  private int activate(
      TransactionTemplate transaction, String connectionName, String token, String firstName) {
    // Naming the request's transaction lets the test distinguish both waiters in PostgreSQL.
    return transaction.execute(
        status -> {
          jdbc.queryForObject(
              "select set_config('application_name', ?, true)", String.class, connectionName);
          try {
            int code =
                mvc.perform(
                        json(
                            post("/api/invitations/accept").with(anonymous()),
                            Map.of(
                                "token", token,
                                "firstName", firstName,
                                "lastName", "Nowak",
                                "dateOfBirth", "2000-01-01",
                                "password", PASSWORD)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            if (code != 204) status.setRollbackOnly();
            return code;
          } catch (Exception failure) {
            throw new IllegalStateException("Activation request failed", failure);
          }
        });
  }
}
