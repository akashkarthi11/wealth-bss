# Wealth BSS

Spring Boot backend services for Wealth integrations and internal capabilities.

## Responsibilities
- Encapsulate external provider integrations.
- Provide internal service APIs for market data and research workflows.
- Keep provider credentials server-side.
- Apply timeouts, bounded retries, rate limits and clear provider error handling.

## Technology baseline
- Java 21
- Spring Boot 3.5.x
- Maven Wrapper
- Spring Web, Actuator and validation as required
- Docker image targeting OCI Ampere `linux/arm64`

## Development
The `development` branch is the only branch intended for automatic Oracle Cloud development deployment. Production deployment from `main` is not configured.

Do not commit secrets, database URLs, private keys, tokens, or personal financial data.


## Internal API routing

- Internal API base path: `/wealth-service/api/v1`.
- Frontend traffic must enter through the BFF at `/wealth/api/v1`; do not expose BSS directly to browsers.
- BSS owns business rules, persistence, integrations, orchestration and job execution.

## Oracle VM deploy secrets (`development` branch only)

The deploy job (`.github/workflows/ci.yml`) requires these repo secrets to be set, or it fails fast:
`ORACLE_HOST`, `ORACLE_USER`, `ORACLE_SSH_KEY`, `ORACLE_HOST_FINGERPRINT`, `WEALTH_JDBC_URL`, `REDIS_URL`,
`WEALTH_SESSION_SECRET`, `WEALTH_ACCESS_KEY`. The deployed container only receives `WEALTH_JDBC_URL` and
`REDIS_URL` (no separate DB username/password), so `WEALTH_JDBC_URL` must embed credentials as query params:
`postgresql://<host>/<database>?sslmode=require&user=<db-username>&password=<db-password>`.
