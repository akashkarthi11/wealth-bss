# Wealth API migration tracker

Target: migrate the existing `wealth` backend into the single Spring Boot application in `wealth-bss`. The frontend is out of scope and must remain compatible with the existing `/api/*` paths, HTTP methods, request bodies, response shapes, authentication and authorization rules.

## Guardrails

- Work on `development` only.
- Keep the original `wealth`, `wealth-bff` and `wealth-batch` repositories intact as references.
- Keep Neon PostgreSQL as the source of truth.
- Do not run schema migrations or destructive SQL against production as part of code migration.
- Do not enable database access in deployment until the existing database configuration is deliberately verified.
- Preserve per-user ownership checks, role restrictions, audit behavior, idempotency and cache invalidation.
- Keep provider credentials server-side.
- Do not migrate frontend files.

## Current BSS coverage (reviewed 2026-11-25)

| Area | Existing source API | BSS status |
|---|---|---|
| Health | `/api/health` | Not migrated (Actuator health exists; source API contract still needs porting) |
| Authentication / sessions | `/api/auth`, `/api/clear-session` | Initial parity implemented: GET current user; POST login/register/logout/reset; compatible scrypt hashes and `wealth_session` HMAC cookie; session-version revocation; password policy and rate limits. Needs live DB/cookie integration tests and live database/cookie integration tests |
| Accounts | `/api/accounts` | Partial: authenticated account list only; account creation and FD terms update missing |
| Ledger | `/api/ledger` | Partial: basic list/create; cursor contract, reward enrichment, external references and delete semantics missing |
| Dashboard | `/api/dashboard` | Not migrated |
| Balances / snapshots | `/api/balances`, `/api/snapshot` | Not migrated |
| Holdings / positions / prices | `/api/holdings`, `/api/positions`, `/api/prices`, `/api/valuation` | Partial: fixed-deposit and holdings write workflows not migrated; no full read model |
| Interest / obligations / planned bills | `/api/interest/*`, `/api/interest-accrual`, `/api/obligations`, `/api/planned-bills`, `/api/nightly` | Not migrated |
| Rewards / offers / splits / lending | `/api/rewards/*`, `/api/account-offers`, `/api/offer-earnings`, `/api/splits/*`, `/api/loans/*`, `/api/lend-view` | Not migrated |
| Review / reconciliation / data quality | `/api/transaction-review`, `/api/transaction-accept`, `/api/reconcile`, `/api/data-quality`, `/api/balancereview` | Not migrated |
| Jobs / queue / backup | `/api/jobs/*`, `/api/backup` | Not migrated |
| Stock advisor / AI research | `/api/stock-advisor/*`, `/api/ai-data`, `/api/providers`, `/api/api-usage` | Not migrated |
| Imports / uploads / broker | `/api/email-imports/*`, `/api/uploads`, `/api/angel`, `/api/broker` | Not migrated |
| Roles / menus | `/api/roles` | Not migrated |

## Migration sequence

1. **Compatibility foundation:** authentication/session parity, API error handling, ownership/role checks, request validation and integration-test fixtures.
2. **Core money APIs:** accounts, ledger, balances, snapshots and dashboard. Preserve exact response shapes and business rules.
3. **Investments:** holdings, positions, prices and valuation.
4. **Cash-flow features:** interest, obligations, planned bills, rewards, offers, splits and lending.
5. **Operational workflows:** review, reconciliation, data quality, imports and uploads.
6. **Automation:** durable job queue, workers, schedules, interest accrual and backup.
7. **Research/provider services:** stock advisor, AI research, provider routing and usage tracking.
8. **Cutover:** compare old/new API responses against the same non-production test data; switch clients only after parity. No production DB migration or cutover is implied by this tracker.

## Acceptance criteria for every migrated route

- Same URL, HTTP method, query parameters, request and response contract.
- Same authentication, role and per-user ownership enforcement.
- Same financial calculations, transaction semantics, idempotency and audit effects.
- Validation and error status parity.
- Automated unit/integration tests, including cross-user access rejection.
- No secrets or personal financial data committed to the repository.
- Deployment health verified before marking the route complete.

## Progress

- [x] Spring Boot BSS builds, tests, publishes AMD64 image and deploys to the Oracle VM.
- [x] Actuator health endpoint returns `UP` after deployment.
- [x] Initial authentication controller and scrypt compatibility tests compile and pass in CI (live DB/session integration still pending).
- [ ] API-by-API migration and parity tests.
- [ ] Frontend cutover (explicitly out of scope until backend parity is proven).


## Service boundary (2026-11-25)

- BFF public compatibility base: `/wealth/api/v1` in `wealth-bff`.
- BSS internal API base: `/wealth-service/api/v1` in `wealth-bss`.
- BFF forwards method, suffix, query string, body, cookies and authorization headers to BSS; BSS retains ownership of business logic and database access.
- Existing BSS endpoints are mounted under the internal base: `/auth`, `/clear-session`, `/accounts`, `/ledger`.
- GrowX orchestration has not yet been imported: no GrowX repository/source was found in the connected GitHub repositories. Do not treat the existing Wealth stock-advisor debate as GrowX until its source is identified.


## Plan and store decisions (2026-11-25)

- The full plan (phases, rules, risks, open decisions) is in the `wealth` repo: `docs/plans/2026-11-25-springboot-migration-plan.md`.
  This tracker remains the route-by-route status list.
- Cutover is per route at the edge: the old app's `vercel.json` rewrites one migrated `/api/<route>` at a time to the BFF.
  The migration sequence above stays the same. Step 8 becomes per route, and a data move (Phase 7) follows it.
- A new Neon database (env prefix `DB_URL_`, Neon Auth and Realtime turned on) and a new `REDIS_URL` were added to Vercel project
  `portfolio` (wealth-ui). They are the **Phase 7 target**. Until then, the BSS uses the **same** database and Redis as the Node app, so
  rows and cache invalidation stay in one place. BSS development and parity tests use a `dev` Neon branch of the new project.
- `WEALTH_JDBC_URL` must be in JDBC form (`jdbc:postgresql://<host>/<db>?sslmode=require`, plus `&prepareThreshold=0` on the `-pooler`
  host). The deploy step passes neither `WEALTH_DB_USERNAME` nor `WEALTH_DB_PASSWORD`, so the credentials go in the URL or the step must
  pass them.
