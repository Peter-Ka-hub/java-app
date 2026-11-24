# 0004 — Patch managed dependencies when security fixes precede a Boot release

Status: accepted, 2026-10-21.

## Context

The first container vulnerability scan found fixable HIGH/CRITICAL findings in the Jackson and Tomcat versions managed by Spring Boot 4.1.1. Depending on a current framework release alone does not guarantee that every managed component contains the latest security patches.

## Decision

Override the Boot dependency properties for Jackson 2 to `2.21.8`, Jackson 3 to `3.1.8`, and Tomcat to `11.0.27`. These are published maintenance releases within the existing version lines. Use BOM properties for both Jackson generations so their modules stay aligned; override the Tomcat property so all embedded Tomcat modules stay aligned.

Verify every change with the local and PostgreSQL suites, a container build, and the same Trivy image scan used in CI. The gate fails on HIGH/CRITICAL findings with an available fix and also fails if the scanner cannot complete.

## Consequences

Maintain these explicit properties until a tested Boot upgrade manages versions containing the same or newer fixes, then remove the overrides. Dependency review includes both the framework BOM and these maintenance pins. A passing vulnerability gate describes its configured severity/fixability scope and database snapshot; it is not a guarantee against every vulnerability.

References: [Apache Tomcat 11 security advisories](https://tomcat.apache.org/security-11.html), [Jackson parsing advisory](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-p6pp-m3f8-5c89), [Jackson polymorphic type validation advisory](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-m8xf-2cr7-77ph).
