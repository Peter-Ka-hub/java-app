# ADR 0005: Business package modules with explicit JPA reference bridges

Status: Accepted (2026-10-27). Refines [ADR 0001](0001-modular-monolith.md).

## Context

The working API had global `api`, `app`, `domain`, `infrastructure` and `security` packages. A single use case was spread across several roots. Services imported HTTP mappers, exceptions and generated requests/responses, and directly used other areas' repositories. `AccessPolicy` and `ScopedQueries` mixed account identity, school isolation and enrollment-dependent decisions. Moving files alone would preserve these problems.

The starting worktree included uncommitted and untracked implementation, documentation and tests. It was preserved as the source of truth. The baseline passed 91 local and 22 PostgreSQL tests. This change must retain API behavior, existing migrations and database contents.

## Decision

Use `dev.schooldiary` with `SchoolDiaryApplication` and three package modules in one Gradle project:

1. `organization`: schools, academic years, classes. These describe the school's structure; a class belongs to a year and school.
2. `identity`: accounts, credentials, current actor and administrator bootstrap. Role, enabled status and tenant membership have an account lifecycle.
3. `diary`: subject offerings, enrollment, grades, grade revisions. Enrollment intervals constrain grading; withdrawal preserves grades/history; mutations coordinate locks. Separate entity modules would create needless cycles.

Put each module's implementation in `internal.web`, `internal.application`, `internal.domain`, and `internal.persistence`. Create only contracts with real consumers. Controllers convert generated HTTP DTOs to application commands. Services return materialized views, mapped from entities inside their transactions. They do not depend on HTTP. Generated Java is under `build/generated` and `dev.schooldiary.generated`, never handwritten source directories.

Source dependencies point `diary → identity → organization`, plus `diary → organization`. The small `shared` package has actor/role/tenant primitives, pagination, version checking and application exceptions. Framework security configuration and common HTTP adaptation live under `platform`.

For reverse questions, define the interface in the asking module: `identity.AccountUsage`, `identity.TeacherAccountScope`, `organization.AcademicUsage`. Diary-owned adapters implement these with their own repositories. This is dependency inversion: runtime callbacks do not require a reverse Java import. `TeacherAccountScope` deliberately exposes Criteria types to retain the correlated SQL predicate and database pagination; replacing it with an unbounded list of account IDs would change query behavior.

Retain existing cross-module JPA relationships. `identity.AccountLookup` and `organization.SchoolReferences` expose narrow managed-reference/locking operations, not repositories. Their implementations require an existing transaction. The allowed foreign entity targets are exactly School for identity, and User/School/AcademicYear/SchoolClass for diary. No new cross-module entity mutations are permitted. `AccountCredentials` exposes a materialized technical credential snapshot for the authentication adapter only.

Keep `DemoDataInitializer` as the single demo composition root. It may invoke an explicit list of application services, commands and views. It cannot use foreign repositories. A facade that merely forwards fixture calls is unnecessary.

ArchUnit 1.5.1 enforces module discovery, allowed directions, no module cycles, private internals, owner-only repositories, inward HTTP boundaries and entity-free application views. The reference exceptions are concrete target-class pairs, not wildcard packages. A negative fixture verifies that illegal repository and generated HTTP dependencies fail the rules. No global rule ignores are used.

## Consequences

One use case can be read within one business directory. Record-specific access rules and query scopes live with their data. Use cases can be instantiated without a web container; the account role-change test demonstrates this. Existing transactions, locks, SQL scoping, LAZY relations and wire responses remain in place.

This is package-level modularity with shared persistence, **not independently deployable services or completely isolated domain models**. Entity references, the Criteria query seam, Spring Data pagination and shared Spring access exceptions are intentional remaining coupling. Package visibility is enforced by tests rather than Java's module system. Any future service extraction must replace the JPA bridges and plan data ownership and consistency separately.

## Alternatives considered

- One module per entity: rejected because subject, enrollment, grade and history rules form a tightly connected transaction cluster.
- Put all school/account/diary code in one module: avoids cycles but hides meaningful ownership and makes boundaries untestable.
- Replace all foreign relationships with IDs now: would also change persistence, fetch and locking semantics, beyond a structure-only refactor.
- Add a facade over every service: adds interfaces without reducing real dependencies; use narrow contracts only where needed.

## Validation

See [the refactor verification and class move guide](../refactor.md). No change sets, table names, column names, enum values, endpoints or OpenAPI schema were changed. No existing Docker database or volume was reset.
