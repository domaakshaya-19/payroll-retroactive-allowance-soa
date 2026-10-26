# Executed verification results — 26 October 2026

Only the verification module was changed for this fix. All ten B1–B6 system test methods remain enabled, with the same zero-error load assertion and strict failover/business assertions.

| Executed command | Passed | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `mvn -B -ntp -pl verification -am test` | 11 | 0 | 0 | 0 |
| `mvn -B -ntp verify` | 130 | 0 | 0 | 0 |

The targeted command includes ten system tests plus one contracts dependency test. Root verification includes those same tests, rather than 130 additional distinct tests. The root system suite took 203.9 seconds; the complete root build took 5 minutes 14 seconds and finished at 17:47:33 Asia/Kolkata. Execution used local Java 21.0.7 with the existing Java 17 compilation target.

Final root-run load: 50 authenticated HTTPS GETs, concurrency 5, five separate warm-ups excluded, all 50 HTTP 200, zero exceptions/errors, error rate 0%, nearest-rank p50 29.2217 ms, p95 79.9796 ms. Full samples are in `target/evidence/load-test.json`; actual Kafka/Eureka/replay/failover evidence is in `target/evidence/system-facts.json`. Both successful runs completed without generating a new Surefire EOF dump.

Diagnosis: the original failed load's Gateway audit log contains exactly 11 HTTP 500 responses in the measured interval, with refused connections to the restarting Retro instance. Registration was visible before authenticated HTTPS readiness. During diagnosis, an intermediate rerun had four passing and six failing system tests: the first failure was a replay-stage HTTP 503 after a transient successful Gateway probe, followed by dependent failures. Direct replica readiness plus bounded sustained Gateway readiness resolved both races. No failed test was skipped or disabled. The earlier EOF dump indicates a closed Surefire command stream during startup and does not prove an application crash; phase/exit/HTTP diagnostics now make startup failures actionable.
