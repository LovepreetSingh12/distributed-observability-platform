# Observatory

A local, working event observability platform: Java 21 SDK → HTTP ingestion → Kafka → MongoDB → tenant-scoped query API → React dashboards.

## Start the demo

Requirements: Docker Engine with Compose v2, Java 21 or newer, Maven 3.9+, Node 22.12+ (or Node 24), npm, Python 3. Allow about 3 GB of memory for the demo. The backend compiles with `--release 21`; service containers run Java 21.

```bash
./scripts/demo.sh
```

Open **http://localhost:5173**. Sign in as `demo-admin@localhost`, using `DEMO_PASSWORD` from the generated `.env`. No fixed passwords or API keys are committed. `.env` is created with owner-only permissions and is excluded from Git and Docker builds. Do not copy `.env.example` with blank secrets over the generated file.

The seed also creates `demo-viewer@localhost`, `other-admin@localhost`, and `other-viewer@localhost`, using the same generated development password. The `other` tenant provides an isolation test. Seed data is only enabled by `DEMO_SEED=true` in the local Compose definition; it is off in application defaults. Existing seed credentials are not reset on restart.

```bash
# Generate the deterministic mixture again (new event IDs, repeatable payloads).
python3 scripts/emit-demo.py
# Exercise actual SDK, ingestion, persistence, tenant isolation and dashboard APIs.
# Also saves an Application overview dashboard for browser exploration.
python3 scripts/smoke.py
# Stop, retaining Kafka/Mongo volumes.
docker compose --env-file .env -f infra/compose.yaml down
```

The UI supports application/key management, immutable JSON schemas and example validation, filters and trace lookup, expandable event JSON, cursor pagination, and saved dashboards with draggable/resizable panels. Administrators can create dashboards from a four-panel starting layout. Numeric panels use queries such as `{"eventType":"ORDER_CREATED","schemaVersion":1,"field":"payload.amount"}`.

## Development and tests

```bash
mvn -f backend/pom.xml -Dapi.version=1.44 -Ddebug=false verify
npm --prefix frontend ci
npm --prefix frontend run build
# Run Vite against the Compose platform API on port 8080.
npm --prefix frontend run dev
```

`api.version=1.44` is for Testcontainers against newer Docker daemons with an elevated minimum API version; it is not application configuration. Tests use real ephemeral Mongo replica sets. Docker is required; container tests are not silently skipped.

```bash
# Optional browser setup and smoke test. Run scripts/smoke.py first.
PLAYWRIGHT_BROWSERS_PATH=/tmp/observatory-browsers frontend/node_modules/.bin/playwright install chromium
PLAYWRIGHT_BROWSERS_PATH=/tmp/observatory-browsers npm --prefix frontend run test:browser
```

On Ubuntu 26.04 with Playwright 1.58.2, set `PLAYWRIGHT_HOST_PLATFORM_OVERRIDE=ubuntu24.04-x64` for installation and test execution. See [verification](docs/verification.md) for the actual executed checks and their limits.

Service ports bound to localhost: UI 5173, platform 8080, ingestion 8081, sample 8083, Mongo 27017, Kafka 9092. The worker exposes health only inside Docker on 8082. Service health endpoints are `/actuator/health`. The Mongo replica set advertises `mongo:27017` for Docker clients. A host debugger can use `mongodb://localhost:27017/observatory?replicaSet=rs0&directConnection=true`.

For backend edits, rebuild using Maven and run `docker compose --env-file .env -f infra/compose.yaml up -d --build`. Dockerfiles package prebuilt Maven jars and Vite assets; `demo.sh` performs both builds first.

## SDK use

Install reactor artifacts with `mvn -f backend/pom.xml install`. Add `dev.observatory:logging-spring-boot-starter:1.0.0` to your Java 21 Spring Boot application.

```yaml
observatory:
  logging:
    endpoint: http://localhost:8081/api/v1/events/batch
    api-key: ${OBSERVATORY_API_KEY}
    application-id: order-service
    environment: development
    capacity: 4096
    batch-count: 50
    batch-bytes: 262144
    flush-interval: 1s
    retry-lifetime: 2m
    shutdown-timeout: 6s
    drop-oldest: false
    logback: true
```

```java
eventLogger.info("ORDER_CREATED", 1, "Order created successfully",
    Map.of("orderId", "ORD-123", "amount", 1499, "currency", "INR"));
```

The default overflow policy drops the newest event, without blocking the caller. `drop-oldest: true` opts into dropping the oldest queued event. Calls perform serialization/queue work, never HTTP I/O. Batch count or estimated byte volume triggers the sender; actual serialized bytes enforce the exact outbound batch ceiling. HTTP connect/request timeouts are 2/5 seconds. Retry delays use capped exponential backoff with jitter and `Retry-After`. Counters are exposed through `EventLogger.counters()` and `/demo/counters` in the sample. MDC `traceId` and `spanId` are captured. The Logback bridge captures logger, thread and truncated exception stack details; it excludes transport logs and protects against recursion. Applications must not log secrets in their own log messages.

**The SDK queue is in memory. Crashes, queue overflow, retry expiry and bounded shutdown can lose events. Delivery is not durable or exactly once.** Acknowledged ingestion means Kafka has accepted the event; dashboards may lag behind while the worker catches up.

## Modules

| Module | Responsibility |
| --- | --- |
| event-contract | Envelope, validation, shared schema/security utilities; server dependencies optional |
| logging-sdk | Bounded asynchronous sender and Logback bridge |
| logging-spring-boot-starter | Properties and auto-configuration |
| ingestion-service | Credential-derived identity, validation, local rate limiting, Kafka acknowledgment |
| storage-worker | Batch persistence, replay deduplication, DLQ and offset safety |
| platform-service | Authentication, memberships, applications, schemas, queries, dashboards |
| sample-application | Business events, ordinary logs, intentional error and invalid payload endpoints |
| frontend | React/TypeScript, Tailwind, Recharts, grid dashboard editor |

[View the verified dashboard screenshot](docs/dashboard.png).

## Further documentation

- [Architecture and dependency references](docs/architecture.md)
- [Event contract](docs/event-contract.md)
- [HTTP API and query model](docs/api.md)
- [Delivery semantics and DLQ replay](docs/delivery-semantics.md)
- [Production considerations and limitations](docs/production-considerations.md)
