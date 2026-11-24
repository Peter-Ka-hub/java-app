# Security and operations

## Account and authorization model

| Principal | Access |
| --- | --- |
| Global `ADMIN` without a school | School administration, account provisioning and invitations for selected schools |
| School `ADMIN` | Users, invitations, academic structure, enrollment, and grades in its own school |
| `TEACHER` | Assigned subject offerings, relevant students, and grades for those offerings |
| `STUDENT` | Own account, subject membership, and grades |

Authorization combines the role with ownership of the specific school, subject, student, or grade. An authenticated user cannot gain access by changing an ID in a URL or request body. List queries apply the same visibility rules as single-record requests.

Administrators can create accounts and set or reset passwords. Students and teachers can also register with an administrator-issued invitation. Password inputs require 12–128 characters. New passwords use Spring Security's PBKDF2 encoder with its versioned `pbkdf2@SpringSecurity_v5_8` parameters and random salts. The delegating encoder can verify legacy `{bcrypt}` hashes. Password hashes and login secrets are excluded from API responses and application request logs.

### Invitation registration

Only administrators issue, list and revoke invitations. A school administrator is scoped to their school and a global administrator chooses the target school. The invitation fixes the email, school and role. Activation accepts profile fields and a password, then sends the new user through ordinary login.

The application generates a 256-bit random token valid for 24 hours and stores its SHA-256 digest. The token is returned only at creation and the UI lets the administrator copy the link for private sharing. The token goes in the URL fragment, which the browser excludes from HTTP requests when opening the form. Submission sends the token and password in the JSON body with the existing CSRF header and cookie. Application request logs contain the HTTP method and path. The activation endpoint uses a fixed path without credentials.

Activation locks the invitation row while it checks availability, creates the account and records consumption in the same transaction. Revocation uses the same row lock. A competing activation waits and then observes the consumed invitation. The unique email constraint also protects against concurrent account creation through different invitations or manual provisioning. Expired, used and revoked links return the same unavailable response. The [OWASP token guidance for password reset](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html#general-security-practices) describes the same expiry and one-time-use principles.

The bootstrap initializer provides the initial global administrator without embedding a default production password in source. `BOOTSTRAP_ADMIN_ENABLED=true` requires a valid `BOOTSTRAP_ADMIN_EMAIL` and a `BOOTSTRAP_ADMIN_PASSWORD` of 12–128 characters; missing or invalid credentials stop startup with a configuration error. If the email already exists, bootstrap leaves that account and its password unchanged. After confirming administrator access, set `BOOTSTRAP_ADMIN_ENABLED=false`, remove both bootstrap credentials, and run `docker compose up -d --wait` to recreate the application with the updated configuration. Compose passes the enabled flag and allows absent credentials when it is disabled. Keep `DATABASE_PASSWORD` unchanged unless the database role password is rotated separately. Disabling bootstrap does not remove the administrator account or existing data.

## Sessions and CSRF

Login uses server-side sessions. Session IDs rotate on successful authentication. Cookies are HttpOnly and SameSite=Strict, with a 30-minute idle timeout. Secure cookies are enabled by default; local dev and loopback Compose explicitly disable the Secure flag because they use HTTP.

The session principal contains the immutable account UUID and a credential version, not an email-based identity. Email is only a login identifier. A rename cannot transfer an existing session to a later account that reuses the address. The password hash is erased from the authenticated principal before it is stored in the session. Every authorized use case still checks the current persisted account and its enabled status/permissions.

Password changes, changes to role or school, and transitions of enabled status advance `app_user.credentials_version`. Existing sessions with an earlier version are rejected with `401` on their next protected use. Disabled accounts are rejected with `403`; re-enabling an account does not revive its old sessions. Ordinary profile/email edits without a password change preserve sessions. Deleted accounts and pre-upgrade principals without a UUID cannot authenticate. See [ADR 0006](decisions/0006-stable-session-identity.md).

CSRF protection applies to unsafe HTTP methods, including login and logout. Obtain the current token from `/api/auth/csrf` and send it in the returned header together with the session cookie. Fetch a new token after login. A failed CSRF check returns `403`; a missing or expired authenticated session returns `401` for protected resources.

Swagger UI installs a same-origin request interceptor that fetches the current token immediately before each unsafe request. It replaces stale manually entered tokens, includes the browser session cookie, and does not forward the CSRF header to another origin. A token-fetch error aborts the write; the interceptor never retries mutations automatically. Other API clients must send CSRF explicitly. Error messages distinguish missing/session-expired CSRF state, an invalid token, and denied access without exposing token values.

Login is limited to 10 attempts per remote address per minute by default. The limiter returns `429` with a `Retry-After` header, uses a monotonic clock, and tracks at most 10,000 addresses. Expired entries free space; a full tracker temporarily rejects new addresses rather than growing memory without a bound. `LOGIN_MAX_ATTEMPTS`, `LOGIN_WINDOW` (for example `1m`), and `LOGIN_MAX_TRACKED_ADDRESSES` configure these limits. This is a per-process limit; it resets on restart and does not replace a shared gateway limit in a multi-instance deployment. It uses the actual remote address and does not trust `X-Forwarded-For`; configure gateway limits when clients share a proxy address.

The browser client should run on the same origin. The application does not install a permissive CORS policy. Do not disable CSRF merely to make a cross-origin frontend work; design the origin and cookie configuration together.

## Exposed operations

Only health and info Actuator endpoints are enabled for HTTP. Health probes omit database details and are intended for orchestration. Info requires administrative access checked against the current database account. SQL console access is confined to the explicit local dev profile and requires a current global administrator session because it permits access to the whole database. Only that console bypasses CSRF in dev and permits same-origin frames; the application API retains CSRF protection. Never run that profile on a public host.

Production logs use Spring Boot ECS JSON output. Request IDs allow correlating responses with server logs. Do not log request bodies containing credentials or real student information. Configure log retention and access permissions appropriate to the data.

## Running outside a developer machine

Before public deployment:

1. Terminate TLS at a trusted reverse proxy; set `SESSION_COOKIE_SECURE=true` and configure forwarded headers only for that trusted topology.
2. Keep PostgreSQL private. Supply secrets from the deployment platform's secret mechanism and rotate credentials independently.
3. Run under a non-root identity, set resource limits, and route readiness and liveness probes to the health endpoints.
4. Restrict administrative access, apply login rate limiting at the gateway, and configure an operational alert for repeated failures.
5. Define database backup and restore procedures and test a restore using synthetic data.
6. Review retention and access rules before storing real student information.
7. Review dependency alerts and update container images and Java patches; run the same CI checks for updates.

Compose is configured for local demonstration, with no published database port and application access bound to loopback. It does not provision TLS, a production identity provider, shared session storage, secret management, gateway rate limiting, or external monitoring. A single application instance is the supported topology; horizontal scaling needs an explicit shared-session design.

## Reverse proxy example

On a host where Nginx is the only public entry point and the application port remains bound to loopback, a server block can forward HTTPS traffic as follows. Replace the hostname and certificate paths with your deployment's actual values:

```nginx
server {
    listen 443 ssl;
    server_name diary.example.com;
    ssl_certificate /etc/nginx/tls/fullchain.pem;
    ssl_certificate_key /etc/nginx/tls/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Host $host;
    }
}
```

Set `SESSION_COOKIE_SECURE=true`. If enabling Spring's forwarded-header support (`SERVER_FORWARD_HEADERS_STRATEGY=framework`), ensure the application port is reachable only through the trusted proxy and the proxy overwrites incoming forwarded headers. Supply that environment variable through your deployment configuration; it is not included in the local Compose environment mapping. Configure a gateway login limit using the real client address. By default the application's own limiter sees the proxy connection address; forwarded-header processing changes that trust boundary. Provision certificate renewal, HTTP-to-HTTPS redirects, and proxy monitoring as deployment tasks. This example is documentation, not an executed deployment or a claim of proxy configuration validation.

## Dependency and CI practices

GitHub Actions run with read-only repository permissions and actions pinned to verified commit IDs. Dependabot proposes weekly Gradle, action, Dockerfile, and Compose updates; updates require review and passing checks. CI validates the Gradle Wrapper, runs local and PostgreSQL tests, builds a container image, and applies the vulnerability gate below without publishing the image.

The build explicitly overrides Jackson and Tomcat maintenance versions when fixes precede a Spring Boot dependency baseline. See [security patch overrides](decisions/0004-security-patch-overrides.md) for the versions, rationale, and removal policy.

Enable GitHub dependency vulnerability alerts for the repository. Weekly update PRs complement vulnerability review; they do not establish that a dependency set has no known vulnerabilities.

## Vulnerability scan

CI scans the completed runtime image with the official `aquasec/trivy:0.76.1` container. It exports the image with `docker save` and gives the scanner read-only access to that archive, without mounting the Docker socket. A separate writable host scratch directory is mounted at `/tmp`; the remaining container filesystem is read-only. Reserve several gigabytes of free disk for the archive, database cache, and temporary database extraction, since the Java database download alone can approach one gigabyte. Operating-system packages and packaged Java dependencies are included. Database downloads and the scan have a 10-minute timeout inside a 12-minute CI step. A dated GitHub Actions cache reuses Trivy databases; normal database freshness checks remain enabled.

The gate uses `--severity HIGH,CRITICAL --ignore-unfixed --exit-code 1`. It fails when Trivy reports a HIGH or CRITICAL vulnerability in its fixed status, meaning a remediation is available. Lower severities and vulnerabilities without an available fix are excluded from this gate and from its filtered report; they still require operational review. Download failures, scanner errors, and timeouts fail the job. There is no repository-specific CVE suppression list. Review scanner version pins with dependency maintenance.

The JSON result is saved as `build/reports/security/trivy.json` and uploaded with the other CI reports even if the gate fails, when a report was produced. A passing gate describes this policy and the database available at scan time; it is not a claim that the application has no vulnerabilities. [Trivy documents this filtering behavior](https://trivy.dev/docs/latest/configuration/filtering/).

To run the same gate locally against a built image:

```sh
docker build --tag school-diary:ci .
mkdir -p build/security-scan/cache build/security-scan/scratch build/reports/security
docker save --output build/security-scan/image.tar school-diary:ci
docker run --rm --read-only \
  --user "$(id -u):$(id -g)" \
  --cap-drop ALL --security-opt no-new-privileges:true \
  --mount "type=bind,source=$PWD/build/security-scan/image.tar,target=/image.tar,readonly" \
  --mount "type=bind,source=$PWD/build/security-scan/cache,target=/cache" \
  --mount "type=bind,source=$PWD/build/security-scan/scratch,target=/tmp" \
  --mount "type=bind,source=$PWD/build/reports/security,target=/reports" \
  aquasec/trivy:0.76.1 image \
  --input /image.tar --cache-dir /cache --scanners vuln \
  --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 \
  --timeout 10m --no-progress --format json --output /reports/trivy.json
```

If the gate fails, review the report's package, installed version, fixed version, and advisory. Update the affected dependency or rebuild with a patched base image, then rerun the gate. Do not disable the check to obtain a green build. The [official image installation reference](https://trivy.dev/docs/latest/getting-started/installation/) and [archive scanning reference](https://trivy.dev/docs/latest/target/container_image/) describe the scanner invocation.

For object-level authorization background, see [OWASP API Security](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/). For session and CSRF behavior, see [Spring Security reference](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
