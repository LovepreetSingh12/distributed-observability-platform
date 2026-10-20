# HTTP API

Platform base: `http://localhost:8080/api` (or `/api` through the UI proxy). Ingestion: `http://localhost:8081/api/v1/events/batch`. JSON only. Bearer user tokens and application keys are distinct. Never send them as query parameters.

## Browser authentication and management

| Method/path | Role | Body / behavior |
| --- | --- | --- |
| POST `/auth/login` | Public | `{email,password,tenant?}`; returns `{token,expiresIn:28800,role,email}`. Tenant selection must match membership. |
| GET `/auth/me` | Any member | Server-derived user/tenant/role |
| POST `/auth/logout` | Any member | Revoke current token |
| GET `/applications` | Any member | Tenant applications |
| POST `/applications` | ADMIN | `{applicationId,name}` |
| GET `/applications/{app}/keys` | Any member | Credential metadata only, never key hashes or raw secrets |
| POST `/applications/{app}/keys` | ADMIN | `{}` → `{keyId,apiKey}`; raw key revealed once |
| DELETE `/applications/{app}/keys/{id}` | ADMIN | Revoke credential within current tenant/application |
| GET `/schemas` | Any member | All own draft and published versions |
| POST `/schemas` | ADMIN | `{eventType,version,schema}`; create draft |
| PUT `/schemas/{type}/{version}` | ADMIN | Raw JSON Schema; only updates drafts |
| POST `/schemas/{type}/{version}/publish` | ADMIN | `{}`; immutable publication |
| POST `/schemas/{type}/{version}/validate` | ADMIN | Example payload → `{valid,errors}` |
| GET `/dashboards` | Any member | Saved tenant dashboard definitions |
| PUT `/dashboards/{uuid}` | ADMIN | Create/replace own dashboard definition |
| DELETE `/dashboards/{uuid}` | ADMIN | Delete own dashboard |

Validation errors are 400; duplicate identities 409; missing/expired credentials 401; role failures 403; unavailable dependencies 503 with generic details. Tenant-scoped collections return no foreign records, even with guessed identifiers. There is no frontend-supplied tenant scope for queries or mutations. Unknown typed request fields are rejected. Platform bodies are capped at 128 KiB. Auth endpoints never return password hashes. Bootstrap/membership administration is operational provisioning in this version, not a user-management UI.

Application records also expose best-effort cumulative `ingestion.accepted`, `ingestion.rejected`, and `ingestion.retryable` attempt counters, plus the last ingestion error and its timestamp. These are diagnostics, not billing or unique-event totals; retries can increment them again. The application UI displays them. Failure to write diagnostics never changes the ingestion outcome.

## Ingestion response contract

Send a JSON **array** of envelopes, authenticated with an application key. Defaults: 1–100 events, 512 KiB entire body, 500 events/application/second. Limits may be changed with `INGESTION_BATCH_LIMIT`, `INGESTION_RATE`, and `INGESTION_RETENTION_DAYS`. Body-size and payload-size caps are hard limits in this initial implementation. The rate limiter is a single-instance fixed one-second window, keyed by tenant and application; it is **not globally enforced across replicas**.

HTTP 200 contains one outcome per input entry, in input order:

```json
[
  {"eventId":"uuid-1","status":"accepted","reason":null},
  {"eventId":"uuid-2","status":"rejected","reason":"Application identity mismatch"},
  {"eventId":"uuid-3","status":"retryable","reason":"Publication or validation dependency unavailable"}
]
```

- `accepted`: Kafka acknowledged publication. It does not mean Mongo persistence or dashboard visibility is complete.
- `rejected`: permanently invalid envelope/payload/schema identity. Do not retry unchanged.
- `retryable`: publication/dependency failed or acknowledgment was ambiguous. Retry with the **same eventId**.
- Malformed entries without an ID receive an empty `eventId` and rejection.
- Batch-level 401: missing/revoked key. 413: body/count limit. 429: rate limit, `Retry-After: 1`. No events are published for these batch-level rejections.
- Invalid JSON is a batch-level 400. Infrastructure failures may produce 503. A disconnected/timed-out request is ambiguous; retry unchanged IDs. SDK retries 408, 429, 5xx, transport failures and per-event retryable outcomes. Other 4xx are terminal.

## Structured querying

`POST /query/events` and `POST /query/aggregate` are available to ADMIN and VIEWER. Both use **occurredAt**, with an inclusive `from` and exclusive `to`:

```json
{
  "from":"2026-10-20T09:00:00Z",
  "to":"2026-10-20T10:00:00Z",
  "applicationId":"order-service",
  "environment":"development",
  "level":"INFO",
  "eventType":"ORDER_CREATED",
  "schemaVersion":1,
  "traceId":"optional-trace",
  "filters":[{"field":"payload.amount","op":"gte","value":100}],
  "limit":50
}
```

Omit unneeded filters. Time range must be positive and at most 30 days. Maximum 5 payload filters. Operators: `eq`, `gt`, `gte`, `lt`, `lte`; ordered comparisons require numeric fields. Client Mongo operators, dotted arbitrary paths and pipelines are never accepted.

Events return `{events,nextCursor}`. Default page size 50, maximum 200. Descending `(occurredAt,eventId)` forms a stable cursor, using BSON millisecond timestamp precision. Repeat the original fixed range and filters when supplying `cursor`. Empty cursor means the last page. This is keyset pagination, not a database snapshot: late arriving events can appear before a previously read cursor and will be visible on refresh. Deletion/TTL may remove records between pages; fixed IDs prevent timestamp tie ambiguity.

Aggregation adds `metric`: `count`, `timeseries`, `group`, `sum`, or `avg`. `group` supplies `field` among applicationId/environment/level/eventType or a schema-allowlisted payload field. `sum`/`avg` require an allowed numeric payload field. `timeseries` supplies `bucketMinutes` (1–1440); select a width giving at most 500 buckets. No automatic zero-fill of empty buckets. Responses are arrays of `{_id: groupOrDateOrNull, value: number}`; empty input returns `[]`.

Grouping returns at most 50 distinct groups and rejects larger results instead of silently truncating. Aggregation disables disk spill, and all database queries have a 3-second execution limit. Cardinality limits constrain responses, not the number of distinct keys encountered during execution; the timeout/memory ceiling bounds expensive scans. No raw database syntax is accepted.

## Dashboard definition

```json
{
  "title":"Application overview",
  "timeRangeMinutes":60,
  "refreshSeconds":30,
  "panels":[{
    "i":"orders", "title":"Order total", "type":"sum",
    "x":0,"y":0,"w":6,"h":2,
    "query":{"eventType":"ORDER_CREATED","schemaVersion":1,"field":"payload.amount"}
  }]
}
```

Maximum 12 panels; 12-column grid; dimensions 1–12 columns and 1–6 rows; minimum refresh 5 seconds; maximum time range 30 days. Panel types: `logs`, `timeseries`, `count`, `bar`, `sum`, `avg`. Panels retain structured queries and run through the same server allowlists when rendered; invalid queries display errors. Polling refresh is supported, streaming is not.
