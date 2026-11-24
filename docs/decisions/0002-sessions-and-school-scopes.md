# ADR 0002: Session authentication with object-level school access

Status: Accepted

## Context

The first client is a browser or API explorer using one application origin. Global, school administrator, teacher, and student privileges differ both by role and by the school or subject involved.

## Decision

Use Spring Security sessions and CSRF protection. Bootstrap a privileged administrator through environment configuration. Enforce authorization inside service operations for the actual objects being accessed, including list visibility. Store encoded passwords and allow administrators to provision accounts.

## Consequences

The browser has an HttpOnly session cookie and no access token storage requirements. Unsafe requests must carry a CSRF token. A distributed deployment requires shared session storage or another deliberate authentication design. OIDC and self-service account recovery remain future extensions.
