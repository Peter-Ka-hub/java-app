# ADR 0006: Stable session identity and credential revocation

Status: Accepted (2026-10-27).

## Problem

An authenticated session stored an email as its principal, and each use case looked the account up by that mutable email. After renaming/deleting account A and reusing its former email for account B, A's old session could be resolved as B, including B's administrative rights. Resetting a password also left existing sessions authenticated. Both behaviors were reproduced against an isolated database.

## Decision

Use an `identity.AccountPrincipal` implementing Spring Security `UserDetails` and `CredentialsContainer`. It carries the account UUID, a credential version, and the authentication snapshot. Email remains a login/display value; it never selects the account for an existing session. Spring's authentication manager erases the password hash before saving the principal in the session.

`SessionActor` requires this principal, reloads the account by UUID, checks enabled status and the current credential version, then returns fresh role/school data. Principals from the old format are rejected and require login. Deleted UUIDs cannot resolve to newly created accounts even if an email is reused.

Add a new Liquibase change set for `app_user.credentials_version`, defaulting existing rows to zero. Password resets, role/school changes and enabled-state transitions increment it within the account update transaction. A prior session is rejected at its next protected operation when its version differs. Ordinary profile/email changes preserve sessions. Keep the JPA optimistic `version` separate so a name/address edit does not force reauthentication.

## Consequences

No process-local session registry is required to revoke all sessions for a credential change. The current database account remains the source of permissions. This does not cancel an already-running transaction mid-request; revocation applies when the next authorization check reads the committed state.

Real-session regression tests cover email rename/reuse, deletion/reuse, two sessions invalidated by one password reset, disable/re-enable, and ordinary profile changes. Mocked-principal API tests now use the same UUID-bearing principal type; they cannot rely on the removed email-only fallback.

Swagger obtains fresh CSRF state before each same-origin mutation. CSRF remains enforced server-side for all clients and still rotates during authentication. Token handling in an API explorer does not replace authentication or authorization.
