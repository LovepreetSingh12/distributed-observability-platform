# Event contract

```json
{
  "eventId": "59891c76-56c2-4acb-97c6-b9fe7aad872e",
  "baseSchemaVersion": 1,
  "eventType": "ORDER_CREATED",
  "payloadSchemaVersion": 1,
  "occurredAt": "2026-10-20T10:00:00Z",
  "applicationId": "order-service",
  "environment": "development",
  "level": "INFO",
  "message": "Order created",
  "traceId": "optional-trace",
  "spanId": "optional-span",
  "payload": {"orderId": "ORD-123", "amount": 1499, "currency": "INR"}
}
```

Refresh `occurredAt` when sending this example. UUIDs must be canonical. Base version is exactly 1; payload versions are positive integers and must reference an exact published schema for the authenticated tenant and event type. Event types use `[A-Z][A-Z0-9_]{0,99}`. Supported levels: TRACE, DEBUG, INFO, WARN, ERROR.

Bounds: application ID 100 characters, environment 64, message 4096, trace/span 128 each; nonoptional text cannot be blank. Payload must be an object, at most 32 KiB UTF-8 JSON and nesting depth 12 (scalar leaves count as one). `occurredAt` is between seven days ago and five minutes in the future, relative to ingestion time. Unknown envelope properties are rejected, including client `tenantId`, `receivedAt`, and `expiresAt`.

Ingestion derives tenant/application identity from the API-key hash lookup, rejects an application mismatch, adds server `receivedAt`, and adds `expiresAt = receivedAt + ingestion.retention-days` (14 by default). Server retention is not a payload schema setting. Dates become BSON dates at persistence. `eventId` is generated once by the SDK and reused for all retries.

## Payload schema lifecycle

Identity is `(tenantId, eventType, version)`. Schemas describe **only payload**, so they cannot alter the platform envelope. JSON Schema draft 2020-12 is mandatory; root type must be `object`. External `$ref`/`$dynamicRef` references are rejected, so ingestion never fetches remote schemas. Internal fragment references are supported. Schemas are limited to 32 KiB and depth 16.

A draft may be updated and validated against example payloads. Publishing freezes it. Changes require a new version; there is no overwrite or deletion API for published versions. Publishing uses a comparison against the validated schema content to avoid racing draft writes. Historical versions remain available. Built-in `ORDER_CREATED` and `APPLICATION_LOG` version 1 schemas are seeded per development tenant.

Published compiled schemas use a bounded 512-entry LRU cache in each ingestion instance. Immutable entries do not require timed refresh; eviction or restart causes a database reread. Unknown versions are not cached, so publishing makes them visible on the next request. Dependency failures return retryable outcomes; missing/unpublished versions return permanent rejection.

Compatibility is explicit, not inferred: applications must choose their payload schema version. Required-field additions, type changes or stricter validation can break older producers, so publish a new version and migrate applications intentionally. Automatic compatibility classification is not implemented.

Only top-level scalar payload properties marked `"x-queryable": true` may be filtered or aggregated. Numeric sum/average additionally require JSON Schema `type: number` or `integer`. Payload queries must supply event type and version, ensuring the query's field types correspond to the records selected.
