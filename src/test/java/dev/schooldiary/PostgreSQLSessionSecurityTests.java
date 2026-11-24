package dev.schooldiary;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The real-session regressions must also pass against the deployment database. */
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
class PostgreSQLSessionSecurityTests extends SessionSecurityRegressionTests {}
