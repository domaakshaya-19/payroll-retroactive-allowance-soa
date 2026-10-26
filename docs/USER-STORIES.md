# User stories and acceptance criteria

| ID | Story | Acceptance evidence |
|---|---|---|
| US-01 | As a payroll clerk, I create an employee/type rule and backdate its amount without destroying history. | Append-only versions; stale/duplicate versions return 409; producer event satisfies schema. `AllowanceWorkflowTest`. |
| US-02 | As a payroll clerk, I preserve exactly what an issued payroll statement paid. | Issued snapshots remain 2000.00 after rule correction; edits/reissue rejected; unique statement/allowance keys. `StatementHistoryTest`, system test 10. |
| US-03 | As a payroll approver, I review exactly two traceable August/September differences before approval into October. | Two DRAFT rows of 1000.00 tied to source statements, event and October; approved total 2000.00. `CoreRetroUnitTest`, system tests 2 and 6. |
| US-04 | As an employee-data custodian, I restrict records to explicit employee entitlements and fixed roles. | Anonymous 401, clerk approval 403, EMP999 403; both staff roles can read EMP001. Signed HTTP tests and system tests 2/10. |
| US-05 | As an operator, I replay and restart safely without double credit. | Same event twice; both consumers stop/restart; replay preserves two effects and existing approval state. System tests 3/4. |
| US-06 | As an operator, I inspect invalid events without losing diagnostic context. | Real invalid Kafka event arrives on DLT with original key/value/offset and safe error code. System test 5. |
| US-07 | As a service consumer, I detect an incompatible required-field contract before deployment. | Test-only `newAmount` → `correctedAmount` mutation causes one expected failure; normal contract suite remains green. `Test-Contract-Break.ps1`. |
| US-08 | As a support engineer, I keep authenticated reads available while one Retro instance stops. | Eureka has six app names/seven instances; Gateway reaches both replicas; eight reads succeed after one stops. System tests 1/7. |
| US-09 | As a reviewer, I see measured load behavior, not estimates. | Exactly 50 authenticated HTTPS GETs, concurrency 5, nearest-rank p50/p95 and error rate exported. System test 8. |
| US-10 | As a reviewer, I reproduce all verification without committing secrets. | One PowerShell verification command, CI definition, schemas, fixtures, Postman examples and sanitized evidence. No private keys/tokens in tracked artifacts. |

The business calendar is fixed to 2026-10-25 for this demo. JWT expiry and operational timestamps use the actual clock. October-only backdates have no affected closed periods and create zero additional adjustments (system test 9). No frontend, external identity provider or paid integration is involved.
