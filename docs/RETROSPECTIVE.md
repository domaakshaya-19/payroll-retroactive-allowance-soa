# Retrospective

This records observations from implementing and running verification, not fictional team feedback.

**Worked:** the existing pure/domain/security tests provided a stable baseline. A real broker and packaged processes let publication, receipt, database effects and discovery be checked together. Persistent event IDs and business keys kept repeated delivery/restart safe. Keeping load and test-count evidence machine-generated prevented estimated or stale numbers from entering the report.

**Needed correction:** the integration harness initially lacked a Kafka runtime dependency. Real replica restart exposed a discovery-cache convergence race that the stub-backend test could not reveal. A bounded registry/cache readiness condition was added. These failures remain documented; they were not disabled or excluded. The deliberate required-field mutation is separately recorded as an expected negative contract failure.

**Decisions:** use an actual embedded KRaft broker for repeatable CI when Docker is unavailable; still launch the six real app types and two Retro processes. Generate isolated TLS/JWT keys and distinct database passwords per run. Keep raw logs/keys out of evidence exports. Stop application processes through local JMX in the recovery demonstration, and retain the independently owned Retro file store.

**Next improvements outside this verification scope:** a production database migration strategy; storage high availability; longer sustained load/soak runs; wider issuer/key-rotation coverage; and explicit reconciliation of payroll closure racing approval. The 50-request result is evidence for this local scenario, not a production sizing recommendation.
