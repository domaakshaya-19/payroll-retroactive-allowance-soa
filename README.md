# Payroll retro

B1–B6 verification: [repeatable commands and evidence](docs/VERIFICATION.md), [measured results](docs/evidence/RESULTS.md), [service diagram](docs/SERVICE-DIAGRAM.md), [user stories](docs/USER-STORIES.md), [task board](docs/TASK-BOARD.md), [test review](docs/TEST-REVIEW.md), [retrospective](docs/RETROSPECTIVE.md), and [demo checklist](docs/DEMO-EVIDENCE-CHECKLIST.md).

See the [choreography, event schema, replay and two-instance recovery guide](examples/CHOREOGRAPHY.md) for the complete business workflow and repeatable demonstration commands.

Java 17 Maven reactor: six independently executable Spring Boot apps, a contracts JAR containing DTO records only, and a test-only verification module. Boot **3.5.13** and Cloud **2025.0.1** use the [official compatible release families](https://spring.io/projects/spring-cloud#overview). Maven compiles with `--release 17`.

| App | HTTPS port | Responsibility |
|---|---:|---|
| api-gateway | 8080 | JWT validation, explicit CORS, `lb://` routes |
| allowance-rule-service | 8081 | Employee allowance rules, own H2 file |
| pay-period-service | 8082 | Employee pay periods, own H2 file |
| retro-adjustment-service | 8083 | Kafka event workflows, period adjustments, approval |
| auth-service | 8084 | Local RSA JWT issuer |
| discovery-server | 8761 | Basic-authenticated Eureka, including self registration |

## Demo JWT security

Local demo users are **clerk / Clerk@123** (`payrollClerk`) and **approver / Approver@123** (`payrollApprover`), each explicitly entitled to **EMP001**. The local token endpoint requires `AUTH_DEMO_ENABLED=true`, which the initialization script sets. JWT signing material stays in environment-referenced, ignored `.local/` files. Gateway and domain services independently validate tokens and enforce employee/role permissions.

See [curl commands and Postman instructions](examples/SECURITY.md) and the [importable Postman collection](examples/payroll-security.postman_collection.json) for 401/403 checks and optional workflow requests. Existing environment passwords override the script defaults; the security guide shows how to select the exact requested demo credentials.

## Windows PowerShell startup

Prerequisites: JDK 17+ (`java` and `keytool` on PATH), Maven 3.6.3+, Docker Desktop with Linux containers. Run from this directory. A JDK 17 runtime is sufficient; a later installed JDK also builds Java 17 bytecode.

```powershell
mvn -B -ntp clean verify
docker compose up -d

# The leading dot and space retain environment variables in THIS shell.
. .\scripts\Initialize-Local.ps1

# Explicit allowed browser origins; no wildcards. Vite uses HTTPS port 5173.
$env:CORS_ORIGINS = 'https://localhost:3000,https://localhost:5173'
$env:CLERK_EMPLOYEES = 'EMP001'
$env:APPROVER_EMPLOYEES = 'EMP001'
$env:RETRO_SERVICE_EMPLOYEES = 'EMP001'

.\scripts\Start-Local.ps1 -Services discovery-server
# Wait for "Started Application" in .local\discovery-server.log.
Get-Content .local\discovery-server.log -Tail 20
.\scripts\Start-Local.ps1 -Services auth-service,allowance-rule-service,pay-period-service,retro-adjustment-service,api-gateway
```

Allow roughly 30ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“90 seconds for registrations and Gateway's discovery cache. The start script prints PIDs and logs, starts hidden JVMs, and passes the public certificate trust store to each JVM. Stop a managed app gracefully with `.\scripts\Stop-Local.ps1 -Service <service-name> -Port <port>`; it validates the local app identity and requests Spring shutdown through same-user local JMX. No HTTP shutdown endpoint is exposed. Stop Kafka with `docker compose down`; its named volume is retained.

`Initialize-Local.ps1` generates a 3072-bit RSA JWT key pair, a localhost SAN certificate, a PKCS12 TLS key store and a public trust store under ignored `.local/`. Every HTTP listener uses HTTPS, including Eureka and downstream Gateway connections. The JWT signing key is separate from the TLS key. Only Auth receives the private-key location; downstream validators receive the public key. No trust-all TLS setting is enabled. Eureka URLs use `https://localhost:8761/eureka/`, and every instance advertises its secure port.

For PowerShell requests, trust the public development certificate for your current Windows user (no administrator needed):

```powershell
Import-Certificate -FilePath .local\localhost.crt -CertStoreLocation Cert:\CurrentUser\Root
```

This explicitly trusts your generated development certificate; remove it when finished. The JVMs use the dedicated trust store rather than your global Java trust store. Certificate lifetime is 365 days; replace expired files deliberately and restart clients with the replacement trust store.

## React frontend (Windows PowerShell)

The minimal React + Vite workspace is in `frontend/`. It uses only React, React DOM and Vite, with a committed npm lockfile. Use Node.js **22.12+** and npm ([Vite requirements](https://vite.dev/guide/)). In development, every browser API request, including login, uses same-origin `/api` paths at **https://localhost:5173**. Vite proxies these to **https://localhost:8080**, verifying TLS and preserving JWT Authorization headers. Production builds retain direct requests to **https://localhost:8080**. JWTs remain in memory and are cleared on logout, expiry, HTTP 401 or page reload. Gateway and domain services still validate and authorize every request.

Keep the existing backend services running. Development uses the Vite proxy and needs no backend CORS change or restart. The browser does not send a cross-origin preflight to Gateway. Vite rewrites the upstream Host and removes the development Origin header so Spring does not reject the proxied POST as cross-origin; paths and Authorization headers pass through unchanged. Use `localhost`, not `127.0.0.1`, for the frontend origin. Production/preview direct requests still require the appropriate existing Gateway CORS configuration; the development proxy is not part of the production build or preview server.

The frontend uses its **own mkcert certificate and private key** in ignored `frontend/.local/`. It never reads the backend `../.local/localhost.p12`, and does not need `TLS_KEYSTORE_PASSWORD`. Keep the backend certificate, environment secrets and databases unchanged. [mkcert's setup guide](https://github.com/FiloSottile/mkcert) documents the local certificate authority and Windows installation.

Install mkcert once in Windows PowerShell:

```powershell
winget install --id FiloSottile.mkcert --exact --source winget
# After installation, open a new PowerShell window so PATH is refreshed.
```

In the new window, create/trust the separate frontend certificate and start Vite:

```powershell
Set-Location C:\Users\sreej\OneDrive\Desktop\Payroll\frontend

# Install mkcert's local CA into your machine's trust stores.
# Approve the Windows trust prompt if shown.
mkcert -install
if ($LASTEXITCODE -ne 0) { throw 'mkcert trust installation failed' }

New-Item -ItemType Directory -Force .\.local | Out-Null
mkcert -cert-file .\.local\localhost.pem -key-file .\.local\localhost-key.pem localhost 127.0.0.1 ::1
if ($LASTEXITCODE -ne 0) { throw 'Frontend certificate generation failed' }

npm.cmd ci
if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed' }
# Set before Node starts; trust the EXISTING backend public certificate.
$env:NODE_EXTRA_CA_CERTS = (Resolve-Path ..\.local\localhost.crt).Path
npm.cmd run dev
# Open https://localhost:5173
```

For an existing working frontend certificate, run only these commands in PowerShell:

```powershell
Set-Location C:\Users\sreej\OneDrive\Desktop\Payroll\frontend
$env:NODE_EXTRA_CA_CERTS = (Resolve-Path ..\.local\localhost.crt).Path
npm.cmd run dev
# Open https://localhost:5173
```

Node reads `NODE_EXTRA_CA_CERTS` at startup. Restart only Vite if it was started without this variable. It adds the existing backend public certificate to Node's trusted CAs; it does not access the backend keystore or change secrets. Never use `secure:false` or `NODE_TLS_REJECT_UNAUTHORIZED=0`.

Keep the existing frontend leaf files; regenerate only those if they expire. Do not commit or share the generated private key or mkcert CA private key. The CA is maintained in mkcert's user-local storage, outside the backend configuration. Vite reads the PEM certificate/key only at startup, for both dev and preview; missing files produce a setup message. Build and tests do not require these files. There is no HTTP fallback or TLS validation bypass.

Restart the browser after `mkcert -install`. Windows Edge/Chrome use the Windows trust store; browsers with a separate trust store may need the mkcert CA installed there as described in mkcert's guide. During development the browser connects only to Vite, and Node verifies Gateway TLS through `NODE_EXTRA_CA_CERTS`. For production/preview direct Gateway requests or manually opening Gateway in your browser, separately trust its existing public certificate if needed (from `frontend/`):

```powershell
Import-Certificate -FilePath ..\.local\localhost.crt -CertStoreLocation Cert:\CurrentUser\Root
```

In development, the browser Network tab should show login at `https://localhost:5173/api/auth/token`. If it still shows port 8080, reload the page from the dev server. Inspect the Vite terminal for proxy errors: `DEPTH_ZERO_SELF_SIGNED_CERT`, `SELF_SIGNED_CERT_IN_CHAIN` or `UNABLE_TO_VERIFY_LEAF_SIGNATURE` indicate Node trust issues. Confirm the certificate path above and restart only Vite. A Gateway 401 without authentication is expected; it does not indicate a TLS failure. No backend CORS settings are changed by this fix. Keep the frontend at port 5173. Do not rerun backend initialization, reset secrets or regenerate certificates to use this proxy.

Select clerk or approver and enter the configured demo password yourself; no password is prefilled or embedded. EMP001 is selected when permitted by the signed employee claim. Clerk can submit a backdated rule with explicit expected version, amount and a first-of-month date; approver can approve individual DRAFT adjustments. Both can view all allowance versions, periods, immutable issued snapshots, event/workflow trace IDs and approved current-open total. Statement views expose no edit controls. The initial backend business date is 2026-10-25; the server enforces that bound. Backdating is asynchronous: use **Refresh records** to follow Kafka workflow completion. Refresh after a version conflict before resubmitting. The UI never retries a write automatically.

For an untouched seeded EMP001, backdate HOUSING with expectedVersion 1, amount 3000.00, effective date 2026-08-01; wait/refresh for two DRAFT differences of 1000.00. Sign out and sign in as approver to approve both and see the current-open total of 2000.00. Use the actual latest version for existing databases; this UI does not reset or reseed data. Each later rule change follows the existing backend comparison semantics described below.

Build and focused client verification, from `frontend/`:

```powershell
npm.cmd run build
npm.cmd test
# Optional: serve the built files over the same local HTTPS origin.
# Uses the separate frontend/.local PEM certificate and key, just like dev.
npm.cmd run preview
```

Build output goes to ignored `frontend/dist/`. The build does not require a certificate, live backend or secrets. The Node tests verify session/expiry handling, request paths, validation and HTTP errors using isolated fetch stubs; they are not a browser/backend end-to-end test. The application itself always uses real Gateway requests. Backend business logic, databases and Maven verification remain unchanged.

### Phase 1 hackathon: read-only payroll exploration

Both clerk and approver can use **Explore · Read only** above the existing write form. This phase adds no backend endpoints, persistence, events or dependencies. JWT/employee permissions, frontend HTTPS PEM files, the development Gateway proxy and production API configuration are unchanged.

**What-If Payroll Simulator:** choose an existing allowance, enter a proposed monthly amount and a first-of-month effective date, then choose **Preview only**. It reads the employee records already loaded from Gateway and calculates locally. Only matching CLOSED issued periods starting on or after the proposed effective date are included, using each snapshot's original allowance and the prospective next rule version. The table shows original amount, corrected amount, monthly difference, source statement and existing approved differences; the summary shows affected months and gross adjustment. There is no save action in this view, no POST request and no Kafka publication. Previewing never fills or submits the separate real backdate form.

For EMP001's original seeded August/September snapshots of 2000.00, proposing HOUSING 3000.00 from 2026-08-01 previews two differences of 1000.00, gross 2000.00. These values are calculated from live returned snapshots, not preloaded demo results. Existing databases can show different source amounts. Effective October 1 produces no affected closed periods for that seed. Reductions produce negative gross differences; unchanged amounts produce zero differences. Arithmetic uses BigInt integer cents, including totals, rather than floating-point subtraction. Missing/failed source API responses block exploration instead of appearing as a zero payroll result.

Existing APPROVED differences are summed only for the same employee, allowance, source period and statement. They are displayed separately, not treated as cash paid and not deducted from the proposed gross. The original statement amount is the allowance recorded at issuance; the APIs do not independently confirm bank settlement. There is no accurate net-payable or outstanding-balance claim: settlement, taxes and deductions are not available. Successive rule changes follow the backend's existing comparison to original statements, not automatic netting against prior corrections. Preview does not validate server business-date bounds, concurrent version changes, workflow ordering or approval eligibility.

**Payroll Time Machine:** select a payroll month to see its immutable original statement beside all currently known rule versions effective by that period's end, plus adjustments linked by source period/statement and triggering event. The issued rule version is identified explicitly. Adjustment status, target payment period and workflow status remain separate from the original amount. Open periods show no issued statement rather than an assumed paid amount. This is a period lens over today's records, not a historical database rollback or exact “as known then” reconstruction: rule creation/publication and settlement timestamps are not exposed by the current DTOs. Use the existing **Refresh records** button to reload source data; previews are cleared during refresh and are never persisted.

The inspected existing GET contracts used by these views are:

| Gateway path (all under `/api`) | Source of truth |
| --- | --- |
| `/allowances/employees/{employeeId}` | Immutable allowance versions, amounts and effective dates |
| `/pay-periods/employees/{employeeId}` | Period dates and CLOSED/OPEN status |
| `/pay-periods/employees/{employeeId}/snapshots` | Issued statement IDs, original amounts, original versions and issuance timestamps |
| `/retro-adjustments/employees/{employeeId}` | Source statement/period, event, difference, approval status and target payroll |
| `/retro-adjustments/employees/{employeeId}/workflows` | Event workflow status and trace identity |

For the **CO-1 through CO-4** review, the official course-outcome definitions were not found in this repository, so exact numbering/assessment alignment remains to be confirmed against the hackathon rubric. Phase 1 supplies these concrete evidence areas without inventing outcome definitions:

| Evidence area | Demonstration |
| --- | --- |
| Service boundaries and API contracts | PayPeriod owns original statements; AllowanceRule owns versions; RetroAdjustment owns corrections. UI explains this SOA division. |
| Event choreography | UI explains real backdate → transactional outbox → Kafka → independent Retro processing; simulation has no event side effects. |
| Integrity and traceability | Time Machine retains issued amounts and links corrections to statement, period and triggering event; approval is distinguished from settlement. |
| Verification and responsible results | Exact-cent calculations and focused tests cover increases, decreases, no affected periods, approval separation, employee/type filtering, invalid inputs, source links and no mutation/network effects. |

Run from `frontend/`: `npm.cmd run build` and `npm.cmd test`. Phase 1 execution: production build passed; **14 tests passed, 0 failures, 0 skipped** (six existing client tests plus eight exploration tests). Test DTO fixtures are isolated test inputs, not UI fallback data. No live browser/backend end-to-end run is claimed for this phase. No backend service, database or certificate was changed or restarted.

### Secrets and persistence

No passwords, private keys or tokens are committed. Initialization generates missing infrastructure secrets without printing or saving their values; the two staff passwords default to the public demo credentials listed above. Supply URL-safe `EUREKA_USER` / `EUREKA_PASSWORD` values because Eureka's connection URL includes userinfo (generated passwords are URL-safe). For repeat sessions, supply the **same** `TLS_KEYSTORE_PASSWORD`, `ALLOWANCE_DB_PASSWORD`, `PAY_PERIOD_DB_PASSWORD` and `RETRO_DB_PASSWORD` from your local secret store **before** initialization. Newly generated passwords cannot unlock existing key stores or databases. Keep the initializing shell open during a first session. To supply an environment secret without displaying it in shell history:

```powershell
$secure = Read-Host 'Existing TLS key store password' -AsSecureString
$env:TLS_KEYSTORE_PASSWORD = [System.Net.NetworkCredential]::new('', $secure).Password
```

Repeat with each existing database password as needed. Other secret variables are `EUREKA_PASSWORD`, `CLERK_PASSWORD`, `APPROVER_PASSWORD`, `RETRO_SERVICE_PASSWORD`; the corresponding usernames are `EUREKA_USER`, `ALLOWANCE_DB_USER`, `PAY_PERIOD_DB_USER`, `RETRO_DB_USER`. The TLS password is consumed through the environment by `keytool`, never embedded in its command line. The public trust store uses `changeit`, which protects no private material.

The start script removes the other services' database credentials and Auth secrets from each child environment. All processes use the project root as working directory. Database files are `data/allowance-rule-service/payroll`, `data/pay-period-service/payroll`, and `data/retro-adjustment-service/payroll`, with users `allowance_owner`, `period_owner`, `retro_owner` and independent random passwords. Single-instance mode uses embedded files with no H2 console or cross-domain repositories/joins. Optional two-instance Retro mode uses one independent loopback H2 server restricted to the Retro data directory, shared only by those replicas; see the recovery guide. Local processes run as your OS user: use separate OS identities/containers if filesystem-level isolation is required. Schema update is for local development.

## Core workflow and synthetic data

Development startup seeds **EMP001 / HOUSING** at **2000.00**, with August and September 2026 closed and issued, and October 2026 open. Seeding is idempotent and never rewrites an existing rule, period or statement. Set `PAYROLL_SEED_ENABLED=false` to disable it. Existing initialized databases are retained. The new tables do not migrate legacy starter rows automatically.

Rules are immutable versions identified by employee, allowance type and version number. Create establishes version 1; backdate requires `expectedVersion` and appends the next version while leaving all prior rows intact. A locked rule-series row serializes writers; unique constraints protect both the series identity and its version numbers. Backdates accept the first day of a month bounded by fixed `payroll.business-date` (default 2026-10-25), independent of the host calendar. Amounts use BigDecimal with at most two fractional digits and twelve integer digits; periods cover whole calendar months. There is no partial-month proration.

Backdating commits the rule version, version counter and `AllowanceRuleBackdated` outbox row in one local transaction. The outbox publisher sends to **soa.s08.t04.events**, keyed by aggregateId, and deletes a row only after broker acknowledgement. Schema-v1 event fields are `eventId`, `aggregateId`, `schemaVersion`, `occurredAt`, `correlationId`, `eventType`, `employeeId`, `allowanceType`, `oldVersion`, `newVersion`, `oldAmount`, `newAmount`, and `effectiveFrom`. The Kafka key is exactly `aggregateId` (`employeeId:allowanceType`). Optional `X-Correlation-ID` on backdate requests propagates into the event. Broker failures leave pending rows available for retry. Delivery is at least once.

PayPeriod issuance atomically saves an immutable statement header and immutable allowance snapshots and closes the period. Each snapshot retains the original paid amount, allowance type, rule version, period dates and issuance time. No update/delete APIs exist for closed periods or statements. Authenticated PUT/PATCH/DELETE requests under PayPeriod are explicitly denied with 403 at Gateway and the domain service. JPA immutable mappings, non-updatable columns and entity lifecycle checks protect the historical rows. A second issuance returns 409; issuance does not fetch or overwrite snapshots when rules change. A per-employee ledger lock and unique period keys protect period creation; only one open period is allowed per employee.

Retro's consumer validates and records each event in its own database before Kafka acknowledges delivery. The event UUID is the workflow primary key; a second unique constraint on employee/type/new version rejects conflicting identities. Exact duplicate delivery is a no-op. A scheduled worker locks the aggregate cursor and workflow, reads historical issued snapshots from PayPeriod through a **load-balanced RestClient**, and commits all period adjustments and `COMPLETED` status together. Each adjustment records the source event, period, statement, snapshot rule version, new rule version, original paid amount, corrected amount and difference. A unique `(event_id, source_period_id, allowance_type)` key provides a second deduplication boundary.

For each matching issued period starting on or after `effectiveFrom`, whose snapshot version precedes the event's new version, the calculation is **newAmount - snapshot.originalAmount**. Open periods have no issued snapshots and are excluded. Negative adjustments are supported. The event's old amount is preserved for traceability but never substitutes for the amount actually paid. Each rule-change event represents its own comparison to original statements; this implementation does not net successive rule-change events against earlier retro payments.

Processing failures roll back all adjustments and persist `RETRY_PENDING` with a sanitized error code. Five failed attempts result in `FAILED`; an employee-scoped approver can explicitly retry it. Restarting the service resumes recorded pending workflows. Malformed or conflicting Kafka messages use the Kafka retry/DLT policy instead. `RECEIVED`, `WAITING_PREDECESSOR`, `RETRY_PENDING`, `FAILED` and `COMPLETED` are visible through the workflow API.

Draft creation links both source statements and the current open payroll period fetched from PayPeriod. Approvals lock the adjustment, reject repeats or a changed payment-period target, and save the approval event in the existing transactional outbox. The current-open API returns only approved adjustments assigned to that open period and their sum. It does not rewrite a source payslip or automatically disburse money. Open-period lookup and local approval remain separate service transactions; if payroll closure races approval, reconciliation is required before closing that payment period.

## API and authorization

Gateway preserves paths and Authorization headers through explicit `lb://` Eureka routes and enforces staff roles and employee entitlements before forwarding. All six apps register with Eureka, including the discovery server itself. JWT validation, HTTPS, exact CORS origins and employee-level authorization remain enabled at service boundaries.

When `AUTH_DEMO_ENABLED=true`, Auth exposes demo-only `POST /api/auth/token` with `username` and `password`. The controller is absent by default. `clerk` receives `payrollClerk`; `approver` receives `payrollApprover`. `retro-service` uses `RETRO_SERVICE_PASSWORD` and receives only `payrollSnapshotReader`, scoped by `RETRO_SERVICE_EMPLOYEES` (default `EMP001`). This service principal can read snapshots/open periods but cannot create rules, issue statements, or approve adjustments. The startup script passes its password only to Auth and Retro. Retro obtains a short-lived token from `https://auth-service` and calls `https://pay-period-service` through service discovery; neither URL contains a fixed host/port. Five-second connection and ten-second response timeouts bound these calls.

Tokens are RS256 signed, last 900 seconds, and carry issuer, audience `payroll-api`, expiry, role and employee claims. Gateway and each domain verify the signature and required expiry with zero clock-skew allowance. Every endpoint also checks the signed employee entitlement. Authentication failures return 401; role/employee violations return 403. Domain input errors return sanitized `application/problem+json` responses: 400 for invalid amounts/dates, 404 for missing records, 409 for stale versions, duplicate operations or closed periods, and 503 when approval's current-open lookup is unavailable.

All paths below start with `/api` and include `/employees/{employeeId}`:

| Method | Path | Role |
|---|---|---|
| GET | `/allowances/employees/{employeeId}` | Clerk or approver; all rule versions |
| POST | `/allowances/employees/{employeeId}` | Clerk; create first version |
| POST | `/allowances/employees/{employeeId}/{allowanceType}/backdate` | Clerk |
| GET / POST | `/pay-periods/employees/{employeeId}` | Clerk or approver read; clerk create |
| POST | `/pay-periods/employees/{employeeId}/{periodId}/issue` | Clerk; issue and close atomically |
| GET | `/pay-periods/employees/{employeeId}/snapshots` | Clerk, approver or snapshot reader |
| GET | `/pay-periods/employees/{employeeId}/{periodId}/snapshots` | Same; historical snapshots for one period |
| GET | `/pay-periods/employees/{employeeId}/open` | Same; currently open period |
| GET | `/retro-adjustments/employees/{employeeId}` | Clerk or approver; traceable adjustments |
| GET | `/retro-adjustments/employees/{employeeId}/workflows` | Clerk or approver |
| GET | `/retro-adjustments/employees/{employeeId}/workflows/{eventId}/event` | Same; exact persisted envelope for replay |
| POST | `/retro-adjustments/employees/{employeeId}/workflows/{eventId}/retry` | Approver; FAILED only |
| POST | `/retro-adjustments/employees/{employeeId}/{id}/approve` | Approver |
| GET | `/retro-adjustments/employees/{employeeId}/approved/current-open` | Clerk or approver |

Create-rule body: `{"allowanceType":"HOUSING","monthlyAmount":2000.00,"effectiveFrom":"2026-08-01"}`. Create-period body: `{"startsOn":"2026-08-01","endsOn":"2026-08-31"}`. Issue body: `{"allowances":[{"allowanceType":"HOUSING","ruleVersion":1,"originalAmount":2000.00}]}`. Issue accepts an explicit original payroll snapshot rather than making a cross-database query. The former manually submitted Retro amount endpoint has been removed; Kafka workflows now create adjustments.

### Reproduce the required example

After startup, certificate trust and Eureka registration, use the same PowerShell shell initialized earlier. Start with a fresh synthetic dataset or an employee rule still at version 1:

```powershell
$login = @{username='clerk'; password=$env:CLERK_PASSWORD} | ConvertTo-Json
$token = Invoke-RestMethod https://localhost:8080/api/auth/token -Method Post -ContentType application/json -Body $login
$headers = @{Authorization="Bearer $($token.accessToken)"}
$before = Invoke-RestMethod https://localhost:8080/api/pay-periods/employees/EMP001/snapshots -Headers $headers
Invoke-RestMethod https://localhost:8080/api/allowances/employees/EMP001/HOUSING/backdate -Method Post -Headers $headers -ContentType application/json -Body '{"expectedVersion":1,"monthlyAmount":3000.00,"effectiveFrom":"2026-08-01"}'

# Poll until the workflow is COMPLETED (outbox and worker normally run every 5s).
Invoke-RestMethod https://localhost:8080/api/retro-adjustments/employees/EMP001/workflows -Headers $headers
$adjustments = Invoke-RestMethod https://localhost:8080/api/retro-adjustments/employees/EMP001 -Headers $headers
$adjustments | Format-Table sourcePeriodId,originallyPaid,newAmount,difference,status

# Expected: August 2000 -> 3000 = 1000; September 2000 -> 3000 = 1000.
$login = @{username='approver'; password=$env:APPROVER_PASSWORD} | ConvertTo-Json
$approvalToken = Invoke-RestMethod https://localhost:8080/api/auth/token -Method Post -ContentType application/json -Body $login
$approvalHeaders = @{Authorization="Bearer $($approvalToken.accessToken)"}
foreach ($adjustment in $adjustments) {
    Invoke-RestMethod "https://localhost:8080/api/retro-adjustments/employees/EMP001/$($adjustment.id)/approve" -Method Post -Headers $approvalHeaders
}
Invoke-RestMethod https://localhost:8080/api/retro-adjustments/employees/EMP001/approved/current-open -Headers $approvalHeaders
# Expected: October paymentPeriodId, total 2000.00, two approved adjustments.
$after = Invoke-RestMethod https://localhost:8080/api/pay-periods/employees/EMP001/snapshots -Headers $headers
# $before and $after retain the issued 2000.00 snapshots and original statement IDs.
```

## Kafka and DLT

Compose provides a single-node Kafka 3.9.1 KRaft broker bound to loopback `9092` with persistent storage. `KAFKA_BOOTSTRAP_SERVERS` overrides the default. Kafka is local plaintext development infrastructure; the six HTTP applications and internal REST requests use HTTPS.

Apps create `soa.s08.t04.events`, `soa.s08.t04.events.DLT`, `payroll.retro-approved.v1` and `payroll.retro-approved.v1.DLT`, each with three partitions and replication factor one. The rule consumer group is `retro-backdate-v1`. Permanent schema/value/key/identity failures go directly to `.DLT`; other consumer delivery failures retry twice at one-second intervals. Recovery preserves the original partition, key/value and source offset plus safe error-code/time headers. Failed DLT sends remain retryable. DLT replay is deliberate/manual; there is no automatic replay loop. Messages contain employee IDs and amounts, so Kafka storage carries payroll data. Audit logs remain redacted. Kafka at-least-once delivery can duplicate publication; database constraints and persistent workflow completion prevent duplicate adjustments for the same event.

## Audit and issuer replacement

`PAYROLL_AUDIT` records generated request IDs, HTTP method/status and redacted identity/employee fields, plus redacted Kafka receipt/failure notices. It excludes request paths, query strings, credentials, authorization headers, bodies and amounts. Do not enable HTTP wire logging or dump environment variables in logs. Local files are operational logs, not a tamper-proof compliance archive.

To replace Auth with an OAuth2/OIDC issuer, remove the local login route and obtain **access tokens** using Authorization Code + PKCE or the appropriate machine flow. In Gateway use `ReactiveJwtDecoders.fromIssuerLocation`, and in each domain use `JwtDecoders.fromIssuerLocation`, retaining explicit audience and required-expiry validation. That enables issuer discovery/JWKS and key rotation. Map the issuer's roles/groups to `ROLE_payrollClerk` / `ROLE_payrollApprover`, and populate an authoritative employee entitlement claim (or consult a dedicated entitlement API). Preserve the domain checks; OIDC authentication alone does not grant employee access. Set `JWT_ISSUER` to the trusted HTTPS issuer, and stop distributing the static public-key file. Local Auth is then retired rather than forwarding user passwords to the issuer.

## Verification

```powershell
mvn -B -ntp verify
docker compose config --quiet
```

Tests run on isolated in-memory H2 databases, not the file databases. They cover immutable rule versions and payslip snapshots, outbox rollback and acknowledgement behavior, August/September differences of exactly 1000.00 each, approved October total of 2000.00, event/period uniqueness, duplicate deliveries, snapshot-based arithmetic, negative differences, retries, rollback after partial processing, JWT validation, service roles and employee authorization. Additional tests cover schema/value/key validation, direct DLT handling with retained context, concurrent duplicate receipt/processing/approval, aggregate version sequencing, shared file-backed JDBC clients and real HTTP Gateway balancing/failover against two test backends. The domain-level Retro tests use explicit snapshot fixtures. The verification module additionally launches the real packaged HTTPS services and real embedded Kafka, exercises actual outbox publication/consumption, restart/replay, DLT, Eureka, two replicas, Gateway failover and a measured 50-request load test. No Docker daemon is needed for that automated system test. See docs/VERIFICATION.md for the expected negative contract run and exact current evidence.

