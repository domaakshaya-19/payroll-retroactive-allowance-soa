# Task board

| Task | Deliverable | Execution status |
|---|---|---|
| B1 | JUnit 5 + AssertJ business cases and invalid/immutable checks | Added; final status comes from current-run evidence |
| B2 | Real H2 `@DataJpaTest` uniqueness and persistence constraints | Added; final status comes from current-run evidence |
| B3 | `@SpringBootTest` with real Kafka and packaged HTTPS services | Added; all methods must pass, no assumptions/disabled tests |
| B4 | Event producer/consumer and REST contract tests; incompatible-field run | Added; expected failure is recorded separately from normal results |
| B5 | Duplicate delivery, complete consumer restart/replay, actual DLT message | Included in the ordered real-system test |
| B6 | Actual Eureka registrations, two Retro JVMs, Gateway balancing/failover | Included in the ordered real-system test |
| Load | 50 requests / concurrency 5 with measured latency/errors | Included; exact measured values are exported |
| Docs | Diagram, schemas, Postman, seed/demo scripts, stories, review, retrospective, checklist | Supplied in this repository |
| CI | JDK 17 workflow with expected negative run then normal verification | Configured; remote execution is not claimed |

See [demo evidence checklist](DEMO-EVIDENCE-CHECKLIST.md) and [automated result data](evidence/automated-tests.json) for observed pass/fail status. This board is a work record, not invented historical sprint activity.
