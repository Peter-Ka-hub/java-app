package dev.schooldiary;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the complete workflow suite against PostgreSQL, including Liquibase and Hibernate
 * validation. Docker is deliberately required: an unavailable production database must fail this
 * task, not skip it.
 */
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
class PostgreSQLIntegrationTests extends SchoolDiaryWorkflowTests {
  @Autowired DataSource dataSource;

  @Autowired JdbcTemplate jdbc;

  @Autowired PostgreSQLContainer postgres;

  @Test
  void appliesMigrationsToAnActualPostgreSqlDatabase() throws Exception {
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
    }
    assertThat(jdbc.queryForObject("select count(*) from databasechangelog", Long.class))
        .isGreaterThan(5);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app_user where email = ?", Long.class, ADMIN_EMAIL))
        .isEqualTo(1);
  }

  @Test
  void upgradesAnExistingPostgreSqlDatabaseWithoutLosingLegacyRecords() throws Exception {
    String schema = "legacy_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      try (var statement = connection.createStatement()) {
        statement.execute("CREATE SCHEMA " + schema);
      }
      connection.setSchema(schema);
      LegacyMigrationTests.assertValidUpgrade(connection);
    }
  }
}
