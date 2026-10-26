# B1–B6 test review

| Requirement | Verification boundary | Tests |
|---|---|---|
| B1 | Plain JUnit 5 + AssertJ, fixed dates, BigDecimal | `CoreRetroUnitTest` normal two-period increase/no affected periods; invalid money/date/version and immutable payslip tests in domain suites |
| B2 | `@DataJpaTest`, actual in-memory H2 constraints | Rule version uniqueness, consumed eventId PK distinct from business key, adjustment event/period/type key, issued statement period key, snapshot period/type key and required amount |
| B3 | `@SpringBootTest`, real embedded Kafka and packaged app JVMs | `PayrollSystemTest`: real Auth/Gateway/Allowance/PayPeriod/two Retro/Eureka; outbox publication, actual receipt and committed downstream state; clerk approval 403 |
| B4 | Published JSON Schemas and actual provider/consumer representations | Producer outbox contract, consumer fixture/rejection, HistoricalSnapshot DTO/REST schema, actual PayPeriod REST payloads in system test |
| B5 | Real broker offsets and persistent domain state | Duplicate sends acknowledged; actual group offsets catch up; both consumers stop/restart and replay; invalid event reaches actual DLT |
| B6 | Real Eureka and TLS HTTP endpoints | Six application names/seven registrations, shared group with two members, observed backend headers, stop one JVM, eight successful reads, restart |
| Load | Real authenticated Gateway → Retro → H2 reads | 5 excluded warm-ups then 50 measured requests at concurrency 5, all results retained, nearest-rank p50/p95, error count/rate |

## Result discipline

Use `scripts/Verify-B1-B6.ps1 -IncludeContractBreak`. It first requires the deliberate field mutation to produce exactly one assertion failure and zero errors. It then runs the entire normal Maven reactor. It does not use `@Disabled`, assumptions, ignored failures or a profile that excludes a failing normal test. The negative run is expected to be red; normal verification must be green. Sanitized XML-derived counts and method outcomes are in `docs/evidence/automated-tests.json`; the negative run has its own `negative-contract.json`.

Only current-run Surefire reports are counted. A failed or partial build records the failure and modules not executed rather than reporting stale success. Load statistics come from `verification/target/evidence/load-test.json`, copied after execution; none are prefilled. Private test keys and raw app logs stay under ignored `target/` directories. Public demo passwords are documented intentionally; production/signing/database secrets are generated or read from the environment.

## Findings during implementation

The initial system harness compile failed because the Kafka runtime test dependency was absent; it was added. The first executable system run had 9 passing tests and 1 failure: the replica-balancing assertion sampled before Gateway's discovery cache converged after a restart. The test now waits a bounded period for the expected registry/cache state while requiring successful HTTP responses. This is eventual-consistency synchronization, not a retry that hides a failed assertion. The later final-run evidence is the authority for current status.

## Limits

The system broker is a real embedded KRaft broker; Docker Kafka is an alternative manual-demo lane. The test does not pretend to run Docker when its daemon is unavailable. Service processes, HTTPS verification, signature checks, Eureka, Kafka sends/consumption, H2 persistence and failover are real. It is a small synthetic benchmark, not a capacity/SLA claim. The Retro store remains a single development database process. Cross-service payroll closure and approval are not a distributed transaction. Schema evolution requires explicit migration/version compatibility, not an automatic rewrite of historical events.

CI is configured for Temurin 17; local evidence records the actual local runtime. A workflow file is not evidence that a remote GitHub job has run.
