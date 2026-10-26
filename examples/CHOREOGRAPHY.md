# Payroll retro choreography, replay and two-instance recovery

The Allowance app owns rule versions and its outbox. It publishes `AllowanceRuleBackdated`; it never invokes Retro or coordinates its work. Retro independently records Kafka receipt and processes a persistent workflow. Its only cross-domain data access is authenticated, discovery-based REST to PayPeriod for immutable issued snapshots and the open payroll period. No domain reads another domain's tables.

## Deterministic example

Development seeds remain **EMP001 / HOUSING / version 1 / 2000.00**, effective **2026-08-01**. August and September are **CLOSED** with immutable issued statements recording 2000.00 each. October is **OPEN**. `payroll.business-date` defaults to **2026-10-25**, independent of the host calendar; this bounds valid backdates. Issued periods and calculations use explicit dates. Authentication expiry and operational timestamps still use the real clock.

Backdating to 3000.00 effective August 1 appends version 2 and produces exactly two **DRAFT** adjustments:

| Source period | Original statement | New allowance | Difference | Payment payroll |
|---|---:|---:|---:|---|
| August 2026 | 2000.00 | 3000.00 | 1000.00 | October 2026 |
| September 2026 | 2000.00 | 3000.00 | 1000.00 | October 2026 |

Both rows store the triggering `eventId`, statement ID, source period ID, version numbers, and October `paymentPeriodId` **at creation**, before approval. Only an entitled `payrollApprover` can approve. Approval cannot move an adjustment to another payment period. Approved October total becomes **2000.00** after approving both. Old payslips and rule versions stay unchanged.

## Event schema and ordering

The contract is `contracts/.../AllowanceRuleBackdated.java`; the published JSON Schema is `contracts/src/main/resources/schemas/allowance-rule-backdated-v1.schema.json`.

```json
{
  "eventId": "ebbe594b-59d3-4bf0-9c04-e97c466e69ca",
  "aggregateId": "EMP001:HOUSING",
  "schemaVersion": 1,
  "occurredAt": "2026-10-25T00:00:00Z",
  "correlationId": "f9c16f53-808a-449b-8b2b-a6df7385f557",
  "eventType": "AllowanceRuleBackdated",
  "employeeId": "EMP001",
  "allowanceType": "HOUSING",
  "oldVersion": 1,
  "newVersion": 2,
  "oldAmount": 2000.00,
  "newAmount": 3000.00,
  "effectiveFrom": "2026-08-01"
}
```

Kafka topic: **soa.s08.t04.events**. Message key: **aggregateId**, exactly `employeeId + ':' + allowanceType`. The single Allowance outbox dispatcher orders pending messages by aggregate and version, sends with idempotent producer configuration and `acks=all`, and deletes only after acknowledgement. A broker failure retains the outbox row. Do not run multiple independent Allowance outbox dispatchers without adding dispatcher coordination. Retro has two consumers, not two Allowance dispatchers.

Schema validation rejects missing/extra/duplicate fields, trailing JSON, wrong JSON types, unsupported schema versions, malformed UUID/date values, invalid identifiers, negative/over-precision amounts, unchanged amounts, invalid version transitions, non-month-start/future effective dates, inconsistent aggregate identity and mismatched Kafka keys. The old `previousVersion` event field is replaced by **oldVersion**. Stored adjustment DTOs still call the original snapshot version `previousVersion`.

Permanently invalid events bypass transient retries and go immediately to **soa.s08.t04.events.DLT**. The DLT retains original key/value, source topic, partition, offset and exception class, plus safe `payroll-error-code` and `payroll-failed-at` headers. Codes include `UNSUPPORTED_SCHEMA_VERSION`, `EVENT_FIELDS_MISMATCH`, `EVENT_FIELD_TYPE_MISMATCH`, `INVALID_EVENT_VALUES`, `AGGREGATE_KEY_MISMATCH`, `CONFLICTING_EVENT_ID` and `CONFLICTING_AGGREGATE_VERSION`. Raw exception messages and stack traces are excluded to prevent payload/credential leakage. Other delivery failures retry twice, then carry `DELIVERY_RETRIES_EXHAUSTED`. Failed DLT publication does not acknowledge successful recovery.

## Durable workflows and duplicate protection

The consumer group **retro-backdate-v1** is identical on both replicas. Cooperative partition assignment keeps each aggregate key on one consumer at a time. Kafka commits a record only after the receipt transaction returns successfully. `eventId` is the inbox primary key; employee/type/new-version uniqueness rejects the same business version under another event UUID. Same UUID with different content is permanently invalid. Replaying identical content is a no-op, including after process restart.

Workers in either replica lock a shared aggregate cursor and the workflow row, then commit adjustments, cursor advancement and workflow completion atomically. A later version waits as `WAITING_PREDECESSOR` until its predecessor completes. Unique `(event_id, source_period_id, allowance_type)` constraints independently protect adjustment creation. The cursor starts at initial rule version 1; missing historical events must be replayed rather than silently skipped. A crash before commit rolls back; a crash after commit is recognized on redelivery. Concurrent approval uses a database lock; one succeeds and the other returns 409.

Snapshot or open-payroll failures roll back all adjustments. A separate transaction persists `RETRY_PENDING` and an attempt count. After five failures, `FAILED` requires the existing approver-only retry endpoint. Restart automatically resumes `RECEIVED`, `RETRY_PENDING` and `WAITING_PREDECESSOR` rows. Neither Kafka offsets nor database files should be deleted as a recovery shortcut.

## Run two Retro instances on Windows

Use the root README for JDK 17+, Maven, TLS trust, passwords and Docker prerequisites. Stop any existing Retro instance before switching its database from embedded mode to cluster mode. All commands run from the project root in the same initialized PowerShell shell.

```powershell
mvn -B -ntp verify
docker compose up -d
. .\scripts\Initialize-Local.ps1
.\scripts\Start-Local.ps1 -Services discovery-server
# Wait for Started Application in .local/discovery-server.log.
.\scripts\Start-Local.ps1 -Services auth-service,allowance-rule-service,pay-period-service,api-gateway
.\scripts\Start-Local.ps1 -Services retro-adjustment-service -RetroInstances 2
```

This starts Retro at **8083** and **8085**, both advertising `retro-adjustment-service` to Eureka with different instance IDs. The Gateway remains **8080** and uses `lb://retro-adjustment-service`. Its GET-only retry filter can retry a stale/failed instance once against the next instance. Writes are never automatically retried. Registration and load-balancer caches converge asynchronously; wait for registration before exercising the demo.

The `cluster` profile connects both replicas to one independent **Retro-owned file-backed H2 process**, loopback port **9094**. Its base directory is restricted to `data/retro-adjustment-service`; it does not expose the other domains' files. Its JDBC clients use only `RETRO_DB_USER` / `RETRO_DB_PASSWORD`. The server process receives none of the application password variables. Keeping it independent means stopping either Retro JVM does not stop the database. Allowance and PayPeriod continue using their separate embedded files and distinct credentials. Embedded single-instance mode remains the default when `-RetroCluster` / `-RetroInstances 2` is omitted. Never run two embedded replicas against separate files: that would invalidate deduplication.

Local PID files and per-instance logs are under `.local/`. `X-Payroll-Instance` on Retro responses identifies the serving instance. The independent store has `.local/retro-store.log` and `.local/retro-store.error.log`. H2 TCP is loopback-only development storage; this is application-instance recovery, not database-server high availability. If the store itself fails, restart it with `java --source 17 scripts/StartRetroStore.java "$PWD"`; pending workflows remain in the file. Keep the same database credentials.

## API sample requests

Staff demo users are `clerk / Clerk@123` and `approver / Approver@123`, scoped to EMP001. The initialization script enables the local issuer; its private key is generated under ignored `.local/`. See `examples/SECURITY.md` for curl/Postman checks. With the development CA trusted:

```powershell
$base='https://localhost:8080'
$login=@{username='clerk';password=$env:CLERK_PASSWORD}|ConvertTo-Json
$clerk=(Invoke-RestMethod "$base/api/auth/token" -Method Post -ContentType application/json -Body $login).accessToken
$headers=@{Authorization="Bearer $clerk";'X-Correlation-ID'=[guid]::NewGuid().ToString()}
$before=Invoke-RestMethod "$base/api/pay-periods/employees/EMP001/snapshots" -Headers $headers
Invoke-RestMethod "$base/api/allowances/employees/EMP001/HOUSING/backdate" -Method Post -Headers $headers -ContentType application/json -Body '{"expectedVersion":1,"monthlyAmount":3000.00,"effectiveFrom":"2026-08-01"}'

# Poll until COMPLETED. The worker/outbox normally run every five seconds.
$workflows=Invoke-RestMethod "$base/api/retro-adjustments/employees/EMP001/workflows" -Headers $headers
$workflows|Format-Table eventId,aggregateId,correlationId,status
$adjustments=Invoke-RestMethod "$base/api/retro-adjustments/employees/EMP001" -Headers $headers
$adjustments|Format-Table id,eventId,sourcePeriodId,paymentPeriodId,difference,status

$login=@{username='approver';password=$env:APPROVER_PASSWORD}|ConvertTo-Json
$approver=(Invoke-RestMethod "$base/api/auth/token" -Method Post -ContentType application/json -Body $login).accessToken
$approvalHeaders=@{Authorization="Bearer $approver"}
foreach($adjustment in $adjustments){Invoke-RestMethod "$base/api/retro-adjustments/employees/EMP001/$($adjustment.id)/approve" -Method Post -Headers $approvalHeaders}
Invoke-RestMethod "$base/api/retro-adjustments/employees/EMP001/approved/current-open" -Headers $approvalHeaders
# total = 2000.00; snapshots below still contain the original 2000.00 amounts.
$after=Invoke-RestMethod "$base/api/pay-periods/employees/EMP001/snapshots" -Headers $headers
```

Endpoint additions: `GET /api/retro-adjustments/employees/{employeeId}/workflows/{eventId}/event` exports the persisted, validated event for that employee. `POST .../workflows/{eventId}/retry` is for FAILED processing, not duplicate re-application. All read APIs require a staff role and that exact signed employee entitlement. Validation failures return sanitized 400 problem responses; stale rule versions, repeat approvals, closed-period issuance and changed payment-period targets return 409. Missing records return 404. Anonymous protected requests return 401; clerk approval and unauthorized employee access return 403. PUT/PATCH/DELETE of issued statements are denied and reissuing a closed period is rejected.

## Explicit replay demonstration

After the workflow completes, export its actual envelope; do not invent new UUIDs to bypass deduplication. This deliberately republishes the **same event** with its aggregate key, preserving business identity:

```powershell
$allWorkflows=Invoke-RestMethod "$base/api/retro-adjustments/employees/EMP001/workflows" -Headers $headers
$workflow=$allWorkflows | Where-Object status -eq COMPLETED | Select-Object -First 1
$eventJson=(Invoke-WebRequest "$base/api/retro-adjustments/employees/EMP001/workflows/$($workflow.eventId)/event" -Headers $headers).Content
# Keep raw JSON to preserve nanosecond timestamps and decimal precision across PowerShell versions.
$line=$workflow.aggregateId+'|'+$eventJson.Trim()
[IO.File]::WriteAllText((Join-Path $PWD '.local/replay.txt'),$line+[Environment]::NewLine,[Text.UTF8Encoding]::new($false))
Get-Content .local/replay.txt | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic soa.s08.t04.events --property parse.key=true --property 'key.separator=|'
# After delivery: still exactly two adjustments and approved total 2000.00.
Invoke-RestMethod "$base/api/retro-adjustments/employees/EMP001" -Headers $headers
Invoke-RestMethod "$base/api/retro-adjustments/employees/EMP001/approved/current-open" -Headers $approvalHeaders
```

To inspect permanently invalid messages and retained error headers:

```powershell
docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic soa.s08.t04.events.DLT --from-beginning --max-messages 1 --timeout-ms 10000 --property print.key=true --property print.headers=true
```

Correct a DLT message's schema/value/key issue before deliberate replay. Preserve a legitimate business event's eventId, correlationId and aggregate/version identity. A conflict means the version already has an accepted identity; changing UUIDs is not a valid repair. DLT payloads contain payroll data and belong in controlled local storage.

## Stop one instance, recover and restart consumers

The startup script enables Spring's local admin MXBean. The stop helper uses JDK Attach/JMX available only to the same OS user, verifies app name and port before stopping, and requests Spring context shutdown. It exposes no HTTP shutdown endpoint. Spring drains requests, completes/rolls back worker transactions and deregisters from Eureka. The independent H2 process remains running.

```powershell
# Observe discovery-based balancing without changing data.
1..6 | ForEach-Object { (Invoke-WebRequest "$base/api/retro-adjustments/employees/EMP001" -Headers $headers).Headers['X-Payroll-Instance'] }
.\scripts\Stop-Local.ps1 -Service retro-adjustment-service -Port 8083
# Requests should keep succeeding via 8085; a stale registration can trigger one GET retry.
1..6 | ForEach-Object { (Invoke-WebRequest "$base/api/retro-adjustments/employees/EMP001" -Headers $headers).Headers['X-Payroll-Instance'] }
# Restart only the stopped member, retaining the shared store and consumer group.
.\scripts\Start-Local.ps1 -Services retro-adjustment-service -RetroPort 8083 -RetroCluster
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group retro-backdate-v1 --members --verbose
```

For a full consumer restart and offset replay, stop **both** members first. Do not reset an active group's offsets. Keep the inbox/adjustment database intact:

```powershell
.\scripts\Stop-Local.ps1 -Service retro-adjustment-service -Port 8083
.\scripts\Stop-Local.ps1 -Service retro-adjustment-service -Port 8085
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group retro-backdate-v1 --topic soa.s08.t04.events --reset-offsets --to-earliest --execute
.\scripts\Start-Local.ps1 -Services retro-adjustment-service -RetroInstances 2
# Existing eventIds remain unique; replay must not add adjustments or approvals.
```

Graceful stop is preferred. If a JVM is forcibly terminated, open JDBC transactions roll back, Kafka reassigns its partitions, and another worker resumes persisted pending work. A write interrupted after commit may have succeeded: read its status before retrying. Gateway intentionally retries reads only.

## Tests and compatibility

`mvn -B -ntp verify` exercises envelope/value/key rejection, DLT destination/key/value/error headers, duplicate delivery across concurrent workers, one-time concurrent approval, aggregate version waiting, immutable histories, October linkage/total, shared file-backed JDBC access and credentials, real-JWT authorization, and live HTTP Gateway load balancing/failover with two test backends. These tests do not require Docker Kafka. Demo commands above are a separate live Kafka/Eureka demonstration.

Schema v1 adds required fields to the previous starter envelope. Legacy pending outbox/inbox payloads without those fields are not silently accepted or rewritten; archive/migrate them deliberately before upgrading a populated development store. H2 `ddl-auto=update` is a local-development convenience, not a production data migration. Do not delete issued statements or committed adjustments to force a replay.
