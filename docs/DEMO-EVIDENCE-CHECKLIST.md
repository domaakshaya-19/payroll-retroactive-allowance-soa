# Demo evidence checklist

Run from the root with JDK 17+, Maven and PowerShell:

```powershell
.\scripts\Verify-B1-B6.ps1 -IncludeContractBreak
```

The normal build starts real Kafka and packaged services on isolated random local ports; Docker is not required. It shuts down only the processes it started and uses fresh ignored data/key directories. Do not run it with `-DskipTests` or ignore its exit code.

| Requirement | Required evidence | Source |
|---|---|---|
| B1 normal | Two DRAFT differences of 1000.00, October link, 2000.00 total | Unit/domain reports and `system-facts.json` |
| B1 boundary | October effective date creates zero additional adjustments | Unit/domain reports and system test 9 |
| B1 invalid/immutable | Invalid change rejected; edit denied; closed period reissue rejected; snapshots unchanged | Domain reports and system test 10 |
| B2 rules | Duplicate employee/type/version rejected by DB | `AllowanceWorkflowTest.databaseRejectsDuplicateRuleVersion` |
| B2 inbox | Same eventId rejected even with a different business key | `RetroApprovalTest.consumedEventIdPrimaryKeyRejectsDuplicateIndependentlyOfBusinessKey` |
| B2 adjustment | Duplicate event/period/type rejected | `RetroApprovalTest.databaseRejectsDuplicateEventPeriodAdjustments` |
| B2 statement | Issued-period and allowance uniqueness; required amount | `StatementHistoryTest` |
| B3 | Authenticated Gateway backdate → actual Kafka → committed Retro state; clerk approval 403 | `PayrollSystemTest` and safe system facts |
| B4 positive | Event and REST schema/provider/consumer agreement | Contract test reports |
| B4 negative | Exactly one expected assertion failure for required-field rename; normal build then green | `negative-contract.json`, normal summary |
| B5 duplicate | Two additional sends, consumed offsets caught up, still two effects | System test 3 |
| B5 restart/replay | Both consumer processes stop, restart with retained DB/group, replay still two effects | System test 4 |
| B5 DLT | Unsupported schema event observed on actual DLT with source offset/error code | System test 5 |
| B6 | Six Eureka names, two Retro instances/group members, both selected by Gateway, one stopped and eight reads succeed | System tests 1/7 |
| Load | 50 measured authenticated GETs at concurrency 5; p50/p95/errors | `load-test.json` |
| Secrets | Evidence contains no tokens/private keys/passwords | Exporter allowlist; target/.local gitignored |

Current measured outcomes are in [automated counts/method results](evidence/automated-tests.json), [system facts](evidence/system-facts.json), [load results](evidence/load-test.json) and [expected contract failure](evidence/negative-contract.json). Missing files mean not yet evidenced, not a pass.

For the default-port manual Docker lane, follow [choreography/replay commands](../examples/CHOREOGRAPHY.md), then run `Seed-Demo.ps1`, `Demo-Reliability.ps1`, `Demo-Infrastructure.ps1`, and `Invoke-LoadTest.ps1`. The manually operated lane requires Docker and trust of the generated localhost certificate. The Postman collection is an optional client; interactive execution is not implied by automated results.
