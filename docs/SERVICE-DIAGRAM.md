# Payroll-retro service diagram

```mermaid
flowchart LR
  Client[Postman / curl / verification client] -->|HTTPS :8080 + signed JWT| GW[API Gateway]
  GW -->|lb://auth-service| Auth[Auth :8084]
  GW -->|lb://allowance-rule-service| Rules[AllowanceRule :8081]
  GW -->|lb://pay-period-service| Periods[PayPeriod :8082]
  GW -->|lb://retro-adjustment-service| R1[Retro replica 1 :8083]
  GW -->|same discovered service| R2[Retro replica 2 :8085]
  Rules --> ADB[(Allowance-only H2 file)]
  Rules --> OB[Transactional outbox]
  OB -->|AllowanceRuleBackdated key = aggregateId| Kafka[Kafka: soa.s08.t04.events]
  Kafka -->|one shared consumer group| R1
  Kafka -->|cooperative partition assignment| R2
  R1 --> RDB[(Retro-only shared file-backed H2 server :9094)]
  R2 --> RDB
  R1 -->|authenticated REST snapshots/open period| Periods
  R2 -->|authenticated REST snapshots/open period| Periods
  Periods --> PDB[(PayPeriod-only H2 file / immutable statements)]
  Kafka -->|invalid schema/key/value + error context| DLT[soa.s08.t04.events.DLT]
  Registry[Eureka :8761] -. registration / discovery .- Auth
  Registry -. registration / discovery .- GW
  Registry -. registration / discovery .- Rules
  Registry -. registration / discovery .- Periods
  Registry -. registration / discovery .- R1
  Registry -. registration / discovery .- R2
```

Eureka also registers itself: six application names, seven instances with two Retro replicas. The two replicas share only their own domain database. No cross-database queries or shared JPA entities exist. Rules publish independently; Retro reacts, persists receipt/workflow state and retrieves historical snapshots through REST. Approval is distinct from calculation and never rewrites an issued statement.

The default single-instance Retro profile embeds its H2 file. The optional local cluster profile uses an independently running loopback H2 server over the same Retro-owned file, so stopping one application does not stop storage. This is application failover, not database high availability.

The verification module uses the same packaged apps and HTTPS with isolated random ports, generated test keys and fresh data directories. Its real embedded KRaft broker removes the need for a Docker daemon in CI; no controllers or database repositories are mocked in that system test.
