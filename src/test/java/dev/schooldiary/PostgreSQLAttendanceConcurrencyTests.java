package dev.schooldiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
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
class PostgreSQLAttendanceConcurrencyTests extends ApiTestSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;

  @Test
  void competingCorrectionsOfTheSameVersionProduceOneSuccessAndOneConflict() throws Exception {
    Fixture fixture = lesson();
    String path = "/api/attendance/lessons/" + fixture.lessonId();
    long version =
        response(
                mvc.perform(json(put(path), body(fixture, fixture.version(), "PRESENT")))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("lesson")
            .path("version")
            .asLong();
    assertThat(version).isEqualTo(fixture.version() + 1);

    var template = new TransactionTemplate(transactions);
    template.setTimeout(25);
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    String firstConnection = "attendance-a-" + UUID.randomUUID();
    String secondConnection = "attendance-b-" + UUID.randomUUID();
    try (var workers = Executors.newFixedThreadPool(3)) {
      var blocker =
          workers.submit(
              () ->
                  template.executeWithoutResult(
                      ignored -> {
                        jdbc.queryForObject(
                            "select id from attendance_lesson where id = ? for update",
                            UUID.class,
                            fixture.lessonId());
                        locked.countDown();
                        await(release);
                      }));
      Future<Integer> first;
      Future<Integer> second;
      try {
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        first =
            workers.submit(() -> save(template, firstConnection, path, fixture, version, "ABSENT"));
        second =
            workers.submit(() -> save(template, secondConnection, path, fixture, version, "LATE"));
        assertThat(bothWaiting(firstConnection, secondConnection))
            .as("both requests contend for PostgreSQL row locks before either commits")
            .isTrue();
      } finally {
        release.countDown();
      }
      blocker.get(10, TimeUnit.SECONDS);
      int firstStatus = first.get(10, TimeUnit.SECONDS);
      int secondStatus = second.get(10, TimeUnit.SECONDS);
      assertThat(new int[] {firstStatus, secondStatus}).containsExactlyInAnyOrder(200, 409);
      var stored = read(path);
      assertThat(stored.path("lesson").path("version").asLong()).isEqualTo(version + 1);
      var entry = stored.path("entries").get(0);
      String winner = firstStatus == 200 ? "ABSENT" : "LATE";
      assertThat(entry.path("status").asText()).isEqualTo(winner);
      assertThat(entry.path("corrections").size()).isEqualTo(1);
      assertThat(entry.path("corrections").get(0).path("previousStatus").asText())
          .isEqualTo("PRESENT");
      assertThat(entry.path("corrections").get(0).path("newStatus").asText()).isEqualTo(winner);
    }
  }

  @Test
  void postgresRejectsDuplicateStudentRowsForOneLesson() throws Exception {
    Fixture fixture = lesson();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into attendance_entry (id, lesson_id, student_id, status) values (?, ?, ?, ?)",
                    UUID.randomUUID(),
                    fixture.lessonId(),
                    fixture.studentId(),
                    "LATE"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private int save(
      TransactionTemplate template,
      String connectionName,
      String path,
      Fixture fixture,
      long version,
      String mark) {
    return template.execute(
        status -> {
          jdbc.queryForObject(
              "select set_config('application_name', ?, true)", String.class, connectionName);
          try {
            int code =
                mvc.perform(
                        json(
                            put(path).with(actor(fixture.teacherEmail(), "TEACHER")),
                            body(fixture, version, mark)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            if (code != 200) status.setRollbackOnly();
            return code;
          } catch (Exception failure) {
            throw new IllegalStateException("Attendance request failed", failure);
          }
        });
  }

  private boolean bothWaiting(String first, String second) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      Integer waiters =
          jdbc.queryForObject(
              "select count(*) from pg_stat_activity where application_name in (?, ?) and wait_event_type = 'Lock'",
              Integer.class,
              first,
              second);
      if (waiters == 2) return true;
      Thread.sleep(20);
    }
    return false;
  }

  private static void await(CountDownLatch release) {
    try {
      if (!release.await(15, TimeUnit.SECONDS))
        throw new IllegalStateException("Lock release timed out");
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while holding lock", failure);
    }
  }

  private Fixture lesson() throws Exception {
    String school = createSchool();
    String year = createYear(school);
    String teacherEmail = uniqueEmail();
    String teacher = createUser("TEACHER", teacherEmail, school);
    String student = createUser("STUDENT", school);
    String subject = createSubject(teacher, school, year);
    enroll(student, subject);
    var lesson =
        create("/api/subjects/" + subject + "/attendance-lessons", Map.of("lessonNumber", 1))
            .path("lesson");
    return new Fixture(
        UUID.fromString(lesson.path("id").asText()),
        UUID.fromString(student),
        teacherEmail,
        lesson.path("version").asLong());
  }

  private static Map<String, Object> body(Fixture fixture, long version, String mark) {
    return Map.of(
        "version",
        version,
        "state",
        "FINALIZED",
        "entries",
        List.of(Map.of("studentId", fixture.studentId(), "status", mark)));
  }

  private record Fixture(UUID lessonId, UUID studentId, String teacherEmail, long version) {}
}
