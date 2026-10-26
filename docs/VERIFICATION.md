# Reproduce B1–B6 verification

Prerequisites: JDK 17+ with `java`, `javac`, `keytool` and JDK Attach support; Maven; PowerShell. The tests target Java 17. A system run starts seven isolated app JVMs (six service types, two Retro instances), a Retro-owned H2 server, and a real embedded Kafka KRaft broker. Allow roughly 4–7 minutes and several GB of free memory. Docker is not required for the automated lane.

```powershell
# Root command. Runs the deliberate negative contract test first, then all normal tests.
.\scripts\Verify-B1-B6.ps1 -IncludeContractBreak
```

The negative command must fail specifically because required `newAmount` was renamed to incompatible `correctedAmount`. The script checks one assertion failure, zero errors, and both field names in the failure. It refuses to count a compilation/network/launch error as proof of a contract break. The normal `mvn verify` afterwards must execute every test, with zero failures/errors/skips. No normal test is disabled to make the build green.

Direct commands:

```powershell
mvn -B -ntp verify
# Expected failure only; inspect the targeted schema assertion, then run normal verification again.
mvn -B -ntp -Pcontract-negative -pl allowance-rule-service -am '-Dtest=AllowanceWorkflowTest#producerMatchesPublishedConsumerSchema' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The `verification` Maven module is test-only, not a seventh application. It must be last in the root reactor because it runs the six executable JARs built earlier. Running only that module requires those JARs to be up to date. It generates private keys, TLS certificates and distinct passwords under ignored `verification/target/system-run-*`; secrets are not embedded in source or command lines. Each app receives only its required sensitive environment variables. Child logs remain ignored. The harness stops only its own processes.

Use [the checklist](DEMO-EVIDENCE-CHECKLIST.md) for requirement-to-test mapping, [the test review](TEST-REVIEW.md) for boundaries and findings, and [measured results](evidence/RESULTS.md) for current exact counts and load statistics. The underlying JSON files record individual method outcomes, so partial builds and missing execution remain visible.

## Default-port manual demonstration

Follow the root README to initialize the local environment and trust the generated certificate. Then:

```powershell
docker compose up -d
.\scripts\Start-Local.ps1 -Services discovery-server
# Wait for discovery-server readiness.
.\scripts\Start-Local.ps1 -Services auth-service,allowance-rule-service,pay-period-service,api-gateway
.\scripts\Start-Local.ps1 -Services retro-adjustment-service -RetroInstances 2
.\scripts\Seed-Demo.ps1
# Submit the August 2000 -> 3000 backdate using the README/Postman example and wait for completion.
.\scripts\Demo-Reliability.ps1
.\scripts\Demo-Infrastructure.ps1
.\scripts\Invoke-LoadTest.ps1 -Requests 50 -Concurrency 5
```

The seed script never deletes/replaces history. It creates missing initial records only when compatible with the one-open-period invariant and verifies existing issued snapshots. Manual replay preserves the exact raw JSON envelope, eventId and aggregate key. Consumer-group offsets must catch up before state is checked. The infrastructure demo uses the managed PID files and same-user local JMX for graceful stop/restart. GET-only Gateway retries protect reads; writes are not blindly retried.

The provided Postman collection is at `examples/payroll-security.postman_collection.json`. The event schema is at `contracts/src/main/resources/schemas/allowance-rule-backdated-v1.schema.json`; the REST snapshot schema is alongside it. See [the diagram](SERVICE-DIAGRAM.md), [user stories](USER-STORIES.md), [task board](TASK-BOARD.md), and [retrospective](RETROSPECTIVE.md). CI runs the same PowerShell verification entry point using Temurin 17 and uploads only sanitized evidence.
