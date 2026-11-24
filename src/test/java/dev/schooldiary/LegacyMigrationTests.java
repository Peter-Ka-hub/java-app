package dev.schooldiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LegacyMigrationTests {
  private static final String SCHOOL = "00000000-0000-0000-0000-000000000001";
  private static final String TEACHER = "00000000-0000-0000-0000-000000000002";

  @Test
  void upgradesExistingSchoolsUsersMembershipsAndGradesWithoutLosingRecords() throws Exception {
    try (Connection connection = database()) {
      assertValidUpgrade(connection);
    }
  }

  static void assertValidUpgrade(Connection connection) throws Exception {
    try (Liquibase liquibase = liquibase(connection)) {
      liquibase.update(5, new Contexts(), new LabelExpression());
      seedLegacyRows(connection);
      liquibase.update(new Contexts(), new LabelExpression());

      try (Statement statement = connection.createStatement();
          var result = statement.executeQuery("SELECT credentials_version FROM app_user")) {
        int migratedAccounts = 0;
        while (result.next()) {
          assertThat(result.getLong("credentials_version")).isZero();
          assertThat(result.wasNull()).isFalse();
          migratedAccounts++;
        }
        assertThat(migratedAccounts).isGreaterThan(0);
      }

      try (Statement statement = connection.createStatement();
          var result =
              statement.executeQuery(
                  """
                         SELECT s.school_id, s.academic_year_id, e.status, u.email,
                                g.graded_by_id, g.score, g.version, g.date_of_score,
                                y.start_date, y.end_date, e.joined_on
                         FROM subject s
                         JOIN enrollment e ON e.subject_id = s.id
                         JOIN app_user u ON u.id = e.student_id
                         JOIN grade g ON g.subject_id = s.id
                         JOIN academic_year y ON y.id = s.academic_year_id
                         """)) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString("school_id")).isEqualTo(SCHOOL);
        assertThat(result.getString("academic_year_id")).isEqualTo(SCHOOL);
        assertThat(result.getString("status")).isEqualTo("ACTIVE");
        assertThat(result.getString("email")).isEqualTo("student@example.com");
        assertThat(result.getString("graded_by_id")).isEqualTo(TEACHER);
        assertThat(result.getString("score")).isEqualTo("A");
        assertThat(result.getLong("version")).isZero();
        LocalDate gradeDate = result.getDate("date_of_score").toLocalDate();
        assertThat(gradeDate)
            .isBetween(
                result.getDate("start_date").toLocalDate(),
                result.getDate("end_date").toLocalDate());
        assertThat(result.getDate("joined_on").toLocalDate()).isBeforeOrEqualTo(gradeDate);
        assertThat(result.next()).isFalse();
      }
      try (Statement statement = connection.createStatement();
          var result = statement.executeQuery("SELECT COUNT(*) FROM grade_revision")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getLong(1)).isZero();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "UPDATE app_user SET school_id = NULL WHERE role = 'STUDENT'",
        "UPDATE app_user SET role = 'STUDENT' WHERE role = 'TEACHER'",
        "UPDATE app_user SET email = 'TEACHER@example.com' WHERE role = 'STUDENT'",
        "UPDATE app_user SET date_of_birth = '2999-01-01' WHERE role = 'STUDENT'",
        "UPDATE school SET date_of_construction = '2999-01-01'",
        "UPDATE grade SET date_of_score = '2999-01-01'"
      })
  void haltsUnsafeLegacyUpgradesBeforeAddingTheNewSchema(String corruption) throws Exception {
    try (Connection connection = database();
        Liquibase liquibase = liquibase(connection)) {
      liquibase.update(5, new Contexts(), new LabelExpression());
      seedLegacyRows(connection);
      try (Statement statement = connection.createStatement()) {
        statement.execute(corruption);
      }

      assertThatThrownBy(() -> liquibase.update(new Contexts(), new LabelExpression()))
          .isInstanceOf(LiquibaseException.class)
          .hasStackTraceContaining("Legacy data violates the diary invariants");
      try (Statement statement = connection.createStatement();
          var result = statement.executeQuery("SELECT COUNT(*) FROM grade")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getLong(1)).isEqualTo(1);
      }
    }
  }

  private Connection database() throws Exception {
    return DriverManager.getConnection(
        "jdbc:h2:mem:migration-" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "");
  }

  private static Liquibase liquibase(Connection connection) throws LiquibaseException {
    return new Liquibase(
        "db/changelog/db.changelog-master.yaml",
        new ClassLoaderResourceAccessor(),
        new JdbcConnection(connection));
  }

  private static void seedLegacyRows(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
                    INSERT INTO school (id, name, address, type, date_of_construction)
                    VALUES ('00000000-0000-0000-0000-000000000001', 'School', 'School street 1', 'PRIMARY', '2000-01-01')
                    """);
      statement.execute(
          """
                    INSERT INTO app_user (id, first_name, last_name, role, date_of_birth, email, school_id)
                    VALUES ('00000000-0000-0000-0000-000000000002', 'Teacher', 'Example', 'TEACHER', '1980-01-01',
                            'Teacher@Example.com', '00000000-0000-0000-0000-000000000001'),
                           ('00000000-0000-0000-0000-000000000003', 'Student', 'Example', 'STUDENT', '2008-01-01',
                            'Student@Example.com', '00000000-0000-0000-0000-000000000001')
                    """);
      statement.execute(
          """
                    INSERT INTO subject (id, name, teacher_id)
                    VALUES ('00000000-0000-0000-0000-000000000004', 'Math', '00000000-0000-0000-0000-000000000002')
                    """);
      statement.execute(
          """
                    INSERT INTO subject_students (subject_id, students_id)
                    VALUES ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000003')
                    """);
      try (var grade =
          connection.prepareStatement(
              """
                    INSERT INTO grade (id, score, date_of_score, student_id, subject_id)
                    VALUES ('00000000-0000-0000-0000-000000000005', 'A', ?,
                            '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000004')
                    """)) {
        grade.setDate(1, Date.valueOf(LocalDate.now().minusYears(3)));
        grade.executeUpdate();
      }
    }
  }
}
