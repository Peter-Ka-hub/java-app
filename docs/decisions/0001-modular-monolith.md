# ADR 0001: One deployable application with functional modules

Status: Accepted

## Context

School ownership, enrollment, and grading rules share transactional data. The project should teach useful Java backend practices and remain understandable and reproducible for a single developer.

## Decision

Use a modular monolith built with Spring MVC, application services, Spring Data JPA, and one PostgreSQL database. Organize code by function and keep transaction boundaries in the use-case services. Use explicit DTOs and avoid serializing JPA entities as HTTP responses.

## Consequences

Cross-module operations can use database transactions and the application has a simple deployment. Package boundaries and architectural discipline need review because deployment isolation does not enforce them. A future extracted service would need an explicit data ownership and consistency plan.
