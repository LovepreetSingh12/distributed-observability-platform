# Verification record

Executed locally on 2026-10-20 (Asia/Kolkata).

## Passing checks

| Check | Actual result |
| --- | --- |
| `mvn -f backend/pom.xml -Dapi.version=1.44 -Ddebug=false verify` | 14 tests, 0 failures, 0 errors, 0 skipped |
| `npm --prefix frontend run build` | TypeScript and Vite production build passed |
| npm dependency audit during final dependency installation | 0 vulnerabilities reported |
| `./scripts/demo.sh` | Built artifacts, started Compose, waited for health, emitted demo data, ran smoke, exited 0 |
| `python3 scripts/smoke.py` after final backend deployment | All API/pipeline assertions passed |
| `npm --prefix frontend run test:browser` with documented Chromium environment variables | 1 comprehensive Chromium workflow test passed; no page exceptions |
| Final Compose health | frontend, ingestion, kafka, mongo, platform, sample and worker all running/healthy |

Build/test host: OpenJDK 25.0.4.1, Maven 3.9.12, Node 24.21.0, Docker 29.8.2. Maven emits Java 21 bytecode. All running backend containers use the pinned Java 21 JRE image. MongoDB Testcontainers launched real replica sets rather than an in-memory substitute.

## Backend test coverage

- Four contract tests: tenant/server-field rejection, timestamp window, strict textual timestamps/integer versions, JSON Schema payload validation and remote-reference rejection.
- Two SDK tests: real local HTTP partial responses, retries retaining original eventId, rejection excluded from retry, Retry-After parsing.
- One ingestion test: mixed accepted/rejected outcomes, mismatched application identity, Kafka failure returning retryable, revoked/missing credential lookup returning 401.
- Four worker tests: real Mongo duplicate/partial unordered bulk results, no acknowledgment on a database failure, no acknowledgment on DLQ failure, and acknowledgment after successful DLQ publication.
- Three platform integration tests: real HTTP auth and real Mongo persistence, cross-tenant isolation, membership/VIEWER permissions, query restrictions/unknown scope fields, stable same-timestamp cursor pagination, immutable published schemas and hashed/revoked keys.

## End-to-end smoke coverage

The smoke uses the actual sample application's SDK, HTTP ingestion, Kafka broker, storage consumer and MongoDB replica set. It confirms that an SDK order reaches the query API, a repeated eventId stores one event, invalid payloads and identity mismatches are rejected per event, and a revoked credential cannot ingest. It checks isolation against a second seeded tenant, VIEWER mutation denial, server-owned tenant scope, query allowlists, schema immutability, example validation, SDK rejection counters, numeric aggregation and persisted dashboards. It also checks frontend HTTP serving.

The deterministic demo emitted 60 orders plus normal logs and six intentional exceptions. Repeated demo runs generate new event IDs while retaining deterministic payload contents.

## Browser verification

Chromium exercised login, opening a live dashboard with rendered chart bars, moving/resizing panels and saving, explorer filtering and expandable JSON, creating/validating/publishing a schema, reading ingestion failure diagnostics, and API-key creation/revocation. It captured [the dashboard screenshot](dashboard.png). The screenshot was visually reviewed after chart animations were disabled to keep polling stable.

Playwright 1.58.2 does not directly recognize this Ubuntu 26.04 host. The Ubuntu 24.04 Chromium binary was installed and successfully run using:

```bash
PLAYWRIGHT_HOST_PLATFORM_OVERRIDE=ubuntu24.04-x64 \
PLAYWRIGHT_BROWSERS_PATH=/tmp/observatory-browsers \
  npm --prefix frontend run test:browser
```

The browser test deliberately changes the demo dashboard layout and creates a new published `BROWSER_*` schema. Running the demo/smoke again restores the default overview layout; published schema history is retained.

## Failures found and repaired during verification

- Forbidden viewer actions originally became 401 through error dispatch; corrected to 403 and retested.
- Unknown JSON request fields originally fell through to 503; corrected to permanent 400 and covered by regression tests.
- Frontend health checks used IPv6 `localhost` against an IPv4 Nginx listener; changed to `127.0.0.1`.
- Dependency installation reported an affected Vite version and a deprecated grid artifact; replaced with pinned patched versions and reran the build/audit.
- JSON mapping could coerce numeric timestamps and fractional schema versions; explicit envelope type checks now reject them.

## Verification limits

No infrastructure or browser workflow remained blocked. This is not a load/failover certification. Database/DLQ failure tests mock those failure boundaries while asserting acknowledgment behavior; they do not simulate every broker rebalance or long-lived replica outage. The DLQ replay CLI is provided and documented but was not exercised against a manually repaired real DLQ export. Multi-node production operation, prolonged retention expiry and disaster recovery were not tested. Vite reports a nonfatal bundle-size advisory (roughly 613 kB before gzip). See [production considerations](production-considerations.md) for functional and operational limits.
