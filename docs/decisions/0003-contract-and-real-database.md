# ADR 0003: Generated API contract and PostgreSQL migration tests

Status: Accepted

## Context

The existing project already generates interfaces and request models from OpenAPI. H2 alone cannot verify the behavior of the intended deployment database.

## Decision

Keep OpenAPI as the contract source and publish the same JSON through Swagger UI. Use PostgreSQL as the normal database and Liquibase for versioned schema changes. Retain H2 for explicitly local development and fast tests. Require PostgreSQL Testcontainers verification in CI.

## Consequences

Contract changes must be made in the source specification, and generated Java stays outside source control. Fast tests work without Docker, while integration tests require a Docker daemon and fail visibly if unavailable. Database changes need upgrade and data migration review in addition to successful startup on an empty database.
