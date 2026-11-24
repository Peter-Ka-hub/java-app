# School Diary API

A Java backend for a school diary with school accounts, invitations, academic structure, enrollment, grades and lesson attendance. The project demonstrates a complete request path from an OpenAPI contract through authorization and transactional business rules to a migrated relational database.

It is a modular monolith with three business modules: `organization` (schools, years, classes), `identity` (accounts and invitations), and `diary` (subjects, enrollment, grades and revisions). It runs as one Spring Boot application with one database. A simple Polish web frontend visualizes all areas; Swagger UI remains available as an API explorer.

## Stack

| Area | Technology |
| --- | --- |
| Runtime | Java 25, Spring Boot 4.1 |
| HTTP and security | Spring MVC, Spring Security sessions, Bean Validation |
| Persistence | Spring Data JPA, PostgreSQL 18.6, Liquibase |
| Contract | OpenAPI, generated Java interfaces and DTOs, springdoc |
| Verification | JUnit, MockMvc, Testcontainers, ArchUnit, JaCoCo, Spotless |
| Delivery | Gradle Wrapper, Docker Compose, GitHub Actions, Dependabot, Trivy |

Exact library versions live in `build.gradle.kts`. The wrapper pins the Gradle distribution and its checksum.

## Run with Docker Compose

Prerequisite: Docker with Compose and a running Docker daemon. A host JDK is unnecessary for this route.

```sh
test -f .env || cp .env.example .env
```

This command preserves an existing `.env`. For the first start, set `DATABASE_PASSWORD`, leave `BOOTSTRAP_ADMIN_ENABLED=true`, and set `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`. Use independent, strong passwords; account passwords require 12–128 characters. An enabled bootstrap with missing or invalid credentials stops application startup with an explicit configuration error. Do not commit `.env`.

```sh
docker compose up --build -d --wait
docker compose ps
```

Open the [school diary frontend](http://localhost:8080/) and sign in with your existing account. No separate frontend server or Node installation is required. The API explorer remains at [Swagger UI](http://localhost:8080/swagger-ui/index.html), and the contract at [/openapi.json](http://localhost:8080/openapi.json). Readiness is available at [/actuator/health/readiness](http://localhost:8080/actuator/health/readiness).

Compose starts PostgreSQL first, applies Liquibase migrations, then starts the application. Database files persist in the `postgres-data` volume. The database has no published host port. The application listens on host loopback and runs as a non-root user with a read-only container filesystem.

```sh
docker compose logs --follow app
docker compose down
```

`docker compose down` preserves the database. Adding `--volumes` permanently removes local database contents. Changing `DATABASE_PASSWORD` after initialization also requires rotating the existing PostgreSQL role password; changing the environment alone does not change an initialized database.

After confirming that the administrator can log in, set `BOOTSTRAP_ADMIN_ENABLED=false`, remove `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` from `.env`, and apply the configuration with `docker compose up -d --wait`. Compose passes the flag into the application and accepts absent bootstrap credentials when disabled. The existing administrator remains in the database. Changing bootstrap variables does not reset an existing account's password; use its current password or an authorized account-management operation.

## Local development without Docker

Prerequisite: JDK 25. The explicit `dev` profile uses a persistent H2 file in `./data` and binds to loopback.

```sh
export BOOTSTRAP_ADMIN_EMAIL=admin@example.test
export BOOTSTRAP_ADMIN_PASSWORD='replace-with-your-own-long-password'
./gradlew bootRun --args='--spring.profiles.active=dev'
```

H2 console is available only in this profile at `/h2-console` after logging in as a global administrator. Its JDBC URL is `jdbc:h2:file:./data/school-diary;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`, username `sa`, empty password. The dev profile is for local experimentation. PostgreSQL integration tests verify behavior that H2 cannot reproduce.

The normal profile requires `DATABASE_PASSWORD` and uses PostgreSQL. Override `DATABASE_URL` and `DATABASE_USERNAME` for an existing database. Local HTTP with the normal profile additionally needs `SESSION_COOKIE_SECURE=false`; the default is `true` for HTTPS.

Optional sample data:

```sh
export DEMO_DATA_ENABLED=true
export DEMO_USER_PASSWORD='choose-a-separate-long-demo-password'
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Bootstrap credentials must also be set. Sample accounts are `teacher@demo.local` and `student@demo.local`; both use `DEMO_USER_PASSWORD`. Use an empty local database for a clean demonstration. Seeding is opt-in and uses fictional data.

## Demonstrate invitation registration

Start the application using Docker Compose or the local `dev` instructions above. Have a school and a subject with an assigned teacher in that school. The optional dev fixtures supply both. A global administrator can create a school administrator from **Użytkownicy → Dodaj konto** by choosing `ADMIN` and assigning a school.

1. Sign in as a school administrator and choose **Użytkownicy → Zaproś użytkownika**. Enter a new email, choose **Uczeń** or **Nauczyciel**, and create the invitation. A global administrator also selects a school.
2. Copy the link and open it in a private window. Enter the invited person's name, date of birth and a password of 12–128 characters. Submit the form and sign in with the invited email and the chosen password.
3. In the administrator's window, open **Użytkownicy → Zaproszenia** to see the status. A pending invitation can be revoked there. The link works once within 24 hours. Ask the school administrator for a new link if it expires or is revoked.
4. To continue the student demo, enroll the new student in the teacher's subject under **Zapisy**. Sign in as that teacher, add a grade under **Oceny**, and refresh the student's **Oceny** view.

## Demonstrate lesson attendance

Use the invitation flow above and a subject in the same school. The dev fixtures provide **Mathematics** and its assigned teacher.

1. As the teacher, open **Przedmioty → Frekwencja** for that subject. Choose **Zapisz ucznia po e-mailu** and enter the new student's account email.
2. Reopen **Frekwencja**, select a lesson number and register it. Mark the new student **Spóźnienie**. Save a draft, then assign every student a status and confirm attendance. Use a different lesson number for another lesson that day.
3. As the student, open **Frekwencja** and select a period including today. The report shows your finalized entries, separate counts for all three statuses, and the percentage including late arrivals.
4. As an administrator, open **Frekwencja** and filter by date, class and subject. Reopen a finalized lesson as the teacher to correct a mark and see the correction history.

**Historical roster.** Reopening an enrollment clears its withdrawal date, so the current enrollment row cannot reconstruct earlier gaps reliably. The server registers lessons for its current date and saves the active roster and class at that moment. Later enrollment changes affect subsequent lessons.

**Concurrent saves.** Every save submits the whole roster and its version. The server checks that version under a row lock and applies the update in one transaction. Changing only a student's mark still advances the lesson version. A stale save receives `409`. Choose **Wczytaj aktualną listę** to review the latest status before editing again.

## Authentication and API use

For everyday use, open the [web frontend](http://localhost:8080/). It includes a dashboard, school/account/year/class/subject/enrollment lists, forms, search, sorting, pagination and grade history. Controls follow your role: administrators manage school data, teachers manage their subjects' enrollments and grades, and students have a read-only view. Cookies and CSRF are handled automatically. See [frontend architecture and usage](docs/frontend.md).

Accounts are managed by administrators. Enabled bootstrap requires both credentials and creates the initial global administrator only when that email does not already exist; it does not replace an existing account or reset its password. A school administrator manages only their own school; a teacher manages grades for their assigned subjects; a student reads their own records. User passwords are never returned by the API. See [security and operations](docs/security.md) for the full model.

Authentication is cookie based. Keep the session cookie and fetch a CSRF token before a state-changing request, including login and logout:

1. `GET /api/auth/csrf` returns `token`, `headerName`, and `parameterName` and establishes the CSRF session.
2. `POST /api/auth/login` with JSON `{"email":"...","password":"..."}`, the session cookie, and the returned token in the returned header.
3. Fetch `/api/auth/csrf` again after login, because authentication rotates the token.
4. Use `GET /api/auth/me` to inspect the signed-in account. Send the current CSRF token for `POST`, `PUT`, `PATCH`, and `DELETE` requests.
5. `POST /api/auth/logout` ends the session.

[`test.http`](test.http) contains a runnable request sequence for the JetBrains HTTP Client. For curl, keep cookies with `--cookie-jar` and `--cookie`; do not expect a bearer token from login.

In Swagger UI, expand **Authentication → POST /api/auth/login**, choose **Try it out**, enter the administrator email/password, and execute. The UI automatically fetches a fresh CSRF token before every write, including login and logout; manual token copying into **Authorize** is unnecessary. The browser sends the same-origin session cookie automatically. Use `GET /api/auth/me` to confirm the account and role. Creating a school requires `role: ADMIN` with no `schoolId`. Keep the browser on one origin, such as `http://localhost:8080`; `127.0.0.1` has a separate cookie store. The API does not enable cross-origin browser access by default.

Other clients, including the JetBrains HTTP Client and curl, still follow the explicit CSRF sequence above. A missing/invalid token returns a specific `403` message; a revoked session requires signing in again. Password resets, account deactivation/reactivation and changes to role or school revoke older sessions. Changing an email keeps a session bound to the same account UUID, even if another account later uses the old email.

| Resource | Purpose |
| --- | --- |
| `/api/schools` | Schools and their details |
| `/api/users` | School-scoped accounts and administrator-managed password resets |
| `/api/invitations` | School-scoped invitation creation, listing and revocation, plus token-based activation |
| `/api/academic-years` | School years with start and end dates |
| `/api/classes` | Classes belonging to a school and academic year |
| `/api/subjects` | A subject offering with teacher, class, and participants |
| `/api/enrollments` | Enrollment dates and status |
| `/api/subjects/{id}/attendance-lessons` | Same-day lesson registration and roster snapshots |
| `/api/attendance/lessons/{id}` | Whole-roster saves, finalization and audited corrections |
| `/api/attendance/me`, `/api/attendance/report` | Finalized personal and school reports |
| `/api/grades` | Student grades with author, timestamps, and version |
| `/api/grades/{id}/history` | Grade revision history |

Swagger UI and clients reading `/v3/api-docs` use the same contract as the Java generator. It describes the fields required for each operation. For example, creating an account requires an initial password. Updating one requires its current version and accepts an omitted or null password to keep the existing credentials.

Invalid input returns `400`, missing records `404`, unauthenticated requests `401`, unauthorized operations `403`, and conflicting data or stale versions `409`. If one request deletes a grade while a correction or another deletion waits, the waiting request receives `404`. The grade history remains available.

## Verify changes

```sh
./gradlew test
./gradlew integrationTest
./gradlew check integrationTest bootJar --no-daemon
node --test src/test/javascript/*.test.cjs
```

`test` runs local automated tests with the test profile, including ArchUnit module boundary checks. `integrationTest` starts PostgreSQL in Testcontainers and requires Docker; it deliberately fails when Docker is unavailable. `check` runs tests and formatting checks and generates a JaCoCo report. CI also builds the runtime image and scans its operating-system packages and Java dependencies with Trivy. The vulnerability gate fails for HIGH or CRITICAL findings with an available fix; unfixed findings and lower severities are outside this gate. Scanner failures also fail CI. Reports are under `build/reports` and are uploaded by CI even after failures when available. See the [structural refactor and its verification](docs/refactor.md), the [earlier verification record](docs/verification.md), and [the scan policy and local command](docs/security.md#vulnerability-scan).

Frontend tests use Node.js 22 or newer. CI checks grade history navigation in Chromium with a simulated API, including Back during a pending request and Forward to reopen the history. Run the same browser test from the repository root with the following commands.

```sh
npm --prefix src/test/browser ci
npm --prefix src/test/browser run install:chromium
npm --prefix src/test/browser test
```

On a fresh Linux machine, append `-- --with-deps` to the browser installation command to install its system libraries. Playwright is a development dependency of the browser tests. The Java application serves its frontend directly.

Session identity and revocation regressions run against both H2 and PostgreSQL. The PostgreSQL suite also overlaps real transactions to test concurrent corrections and grading versus withdrawal.

```sh
./gradlew spotlessApply
```

Run formatting before committing handwritten Java changes. API interfaces and DTOs in `dev.schooldiary.generated` are generated under `build/generated/openapi`; edit `src/main/resources/openapi/openapi.json` rather than generated files. Handwritten code starts at `src/main/java/dev/schooldiary`; the entry point is `dev.schooldiary.SchoolDiaryApplication`. Spring discovers components, entities and repositories under that base package automatically.

## Architecture and learning path

Read [architecture and module dependencies](docs/architecture.md), [database and migration notes](docs/database.md), [security and operations](docs/security.md), and [the module boundary decision](docs/decisions/0005-business-module-boundaries.md). The [refactor guide](docs/refactor.md) includes the package tree, class move map, remaining JPA coupling and a walkthrough suitable for a technical interview. The [session identity decision](docs/decisions/0006-stable-session-identity.md) explains UUID-based identity and credential revocation.

The [security and API fixes record](docs/fixes.md) documents the subsequent fixes, database upgrade, browser check and final test results.

To learn the project, follow one grade request end to end: contract → controller → service → permission check → repository → migration constraints → response. Then read the tests for the rejected cases: another school's data, a teacher outside the subject, an inactive enrollment, or a stale update version.

For each extension, describe the use case, add a failing behavioral test, implement it, and record the design decision. Useful next exercises include OIDC sign-in, user-managed password changes, attendance, metrics export, and a tested backup restore. These features are not part of the current application.

Before exposing the application publicly, configure HTTPS, secure cookies, restricted administration access, and a database backup policy, then perform the deployment checks in [security and operations](docs/security.md). This repository provides local delivery and CI; it does not provision public hosting.

CV description after running and understanding the application:

> Developed a Java/Spring Boot school diary with a role-aware web frontend, school-scoped authorization, transactional enrollment and grading rules, optimistic locking, PostgreSQL/Liquibase migrations, OpenAPI generation, Testcontainers integration tests, and Docker/GitHub Actions delivery.
