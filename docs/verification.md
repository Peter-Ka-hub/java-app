# Verification record

For the subsequent business-module refactor validated on 2026-10-27, see [the refactor verification record](refactor.md#weryfikacja). The checks below record the earlier implementation and scan, not a scan repeated after that refactor.

Validated locally on 2026-10-21–2026-10-22 (Europe/Warsaw). This records the completed working tree, not a remotely deployed release.

## Automated checks

Command: `./gradlew check integrationTest bootJar --no-daemon`.

| Check | Result |
| --- | --- |
| Local automated suite | 91 tests passed; no failures, errors, or skipped tests |
| PostgreSQL 18.6 Testcontainers suite | 22 tests passed; no failures, errors, or skipped tests |
| Spotless formatting | Passed |
| Executable Spring Boot JAR | Built successfully |
| Git whitespace checks | Passed |

The suites cover validation, authentication sessions, CSRF, role and school isolation, optimistic versions, enrollment intervals, grade corrections and history, password boundaries, bounded login throttling, and fresh/legacy database migrations. Historical grade dates are preserved during legacy import; unsafe legacy records stop migration with diagnostics.

## Runtime checks

Built the multi-stage Docker image and started an isolated Compose application with PostgreSQL and synthetic credentials. Real HTTP requests verified:

- readiness, anonymous rejection, and CSRF rejection;
- cookie-based login and logout;
- school, academic year, class, teacher, student, subject, and enrollment creation;
- teacher grading, correction, stale-version rejection, and revision history;
- student visibility and forbidden writes;
- PostgreSQL data and history surviving an application restart;
- withdrawal and audit history surviving live grade deletion;
- deletion protection for resources referenced by academic records.

The isolated test containers and their test volume were removed afterward. A separate executable-JAR check verified the dev profile and opt-in synthetic demo data using an in-memory H2 database.

## Vulnerability gate

The final image scan used official Trivy `0.76.1`, an exported image archive, and local vulnerability databases. Both Ubuntu packages and Java dependencies were analyzed. The configured gate is `--scanners vuln --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1`.

Result: **0 HIGH/CRITICAL findings with an available fix**, scanner exit code 0. This is the configured gate's scope and database snapshot, not a guarantee against every vulnerability. The local JSON report is generated at `build/reports/security/trivy.json`; CI uploads its own reports.

The first scan identified managed Jackson/Tomcat dependencies needing maintenance patches. The final build uses Jackson 2.21.8, Jackson 3.1.8, and embedded Tomcat 11.0.27; see [the dependency decision](decisions/0004-security-patch-overrides.md).

## Delivery scope

The repository includes a verified local Docker/Compose path, a dev profile, CI configuration, and production proxy/security guidance. The GitHub-hosted workflow and public HTTPS hosting were not executed or provisioned from this workspace.
