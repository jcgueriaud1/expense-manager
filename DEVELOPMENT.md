# Development

## Prerequisites

- **JDK 25** (ADR-0014). Point `JAVA_HOME` at a Java 25 install before building.
- **Docker** must be running. Local dev and the integration test layer use real
  PostgreSQL — Docker Compose for local, Testcontainers for tests. No H2, ever
  (ADR-0004).

## Build & Run Commands

```bash
./mvnw                            # Run in dev mode (default goal: spring-boot:run)
./mvnw clean package              # Production build (JAR in target/)
./mvnw test                       # Run all tests
./mvnw test -Dtest=ClassName      # Run a single test class
```

`./mvnw` is the single documented command to run the app locally. It boots in
the **`local`** profile (the default), and Spring Boot's Docker Compose support
auto-starts the PostgreSQL service in [`compose.yaml`](compose.yaml) and wires
the datasource to it — no manual DB setup. The app runs on port 8080
(configurable via `PORT`).

## Profiles

Four profiles override a base `application.properties` (ADR-0013):

| Profile   | Postgres                      | OAuth / login            | Logging        |
|-----------|-------------------------------|--------------------------|----------------|
| `local`   | Docker Compose (auto-started) | form-stub (Phase 1.3)    | human-readable |
| `test`    | Testcontainers                | form-stub                | human-readable |
| `staging` | external managed              | real Google OAuth        | structured JSON |
| `prod`    | external managed              | real Google OAuth        | structured JSON |

`local` is the default when no profile is set. Select another with
`SPRING_PROFILES_ACTIVE=staging` (or `prod`/`test`).

## Environment variables & secrets

No secrets live in git. Credentials come from environment variables, with safe
defaults **only** in `local` (ADR-0013). In `staging`/`prod` these have **no
defaults** — a missing variable is a hard startup failure.

| Variable               | Purpose                     | `local` default                  |
|------------------------|-----------------------------|----------------------------------|
| `DB_URL`               | JDBC URL                    | `jdbc:postgresql://localhost:5432/expense_manager` |
| `DB_USERNAME`          | DB user                     | `expense`                        |
| `DB_PASSWORD`          | DB password                 | `expense`                        |
| `GOOGLE_CLIENT_ID`     | Google OAuth client id      | `local-dev-client-id`            |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret  | `local-dev-client-secret`        |
| `ADMIN_EMAIL`          | Seed admin identity         | `jean-christophe@vaadin.com`     |
| `PORT`                 | HTTP port                   | `8080`                           |

In `local`, Docker Compose supplies the datasource connection automatically, so
`DB_*` only matter if you point at an external DB instead of Compose.

## Docker

To build a Docker image, run:

```bash
docker build -t my-application:latest .
```

If you use commercial components, pass the license key as a build secret:

```bash
docker build --secret id=proKey,src=$HOME/.vaadin/proKey .
```
