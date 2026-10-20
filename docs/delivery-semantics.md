# Delivery semantics

## SDK to Kafka

The SDK queue is bounded and memory-only. Queue overflow, a process crash, exhausted retry lifetime or shutdown deadline can lose events. The default overflow policy drops newest; callers can opt into dropping oldest. Counters distinguish queued, accepted, retried, rejected, dropped and currently pending. `accepted` counts HTTP ingestion outcomes, not Mongo writes. Serialization and enqueueing run on the caller thread; HTTP runs on a single background sender. Sender wakeups are coalesced so the task queue cannot grow with every log call.

Retries use the original generated eventId, exponential backoff (250ms base, 30s cap plus jitter), a maximum two-minute lifetime by default, and `Retry-After` seconds or HTTP date. Shutdown is bounded to six seconds by default; unresolved queued events are dropped. A retryable/ambiguous result may already have reached Kafka.

Ingestion only says accepted after the producer future completes. Producer idempotence and `acks=all` reduce duplicate broker writes, but cannot deduplicate HTTP retries or events produced across sessions. The worker handles those replays by identity.

Kafka partition key: `tenantId:applicationId:(traceId or eventId)`. Same-application trace events share a partition. Ordering is partition-local, with no cross-partition global order and no promise that processing order matches occurredAt. Topics are explicitly created, auto-creation is disabled, and the worker uses `observatory-storage-v1` as its dedicated group.

## Kafka to Mongo

Normal Mongo `events` collection, not a time-series collection. Unique `(tenantId,eventId)` identifies a stored event. The worker reads batches, parses envelopes and writes valid documents with unordered bulk inserts, majority write concern and journaling. Duplicate-key results are successful replays. Indexes are created before listening.

Partial bulk outcomes are inspected individually: duplicate errors are ignored; permanent document/validation failures (codes 2/121) go to DLQ; write-concern errors and other failures cause the entire batch to retry. Successfully inserted rows are harmless duplicates on replay. Invalid JSON or missing persisted identity/timestamps goes to DLQ before acknowledgment.

The batch is acknowledged only after **all** records have either persisted, been recognized as duplicates, or received a successful DLQ producer acknowledgment. DLQ publication failure throws and prevents acknowledgment. There is no recoverer that logs-and-skips. Spring Kafka's batch fallback retry handler pauses the consumer while retrying, polls to maintain group membership, and uses exponential delays capped at ten seconds. Retries intentionally continue while dependencies are unavailable; the *delay* is bounded, not the total number of database attempts. This avoids silently committing unresolved records. Extended outages can still exceed Kafka topic retention and require operator intervention.

Kafka offset commits and Mongo writes are **not atomic**. Kafka transactions cannot make external Mongo writes atomic with offsets. A crash after a write but before commit causes replay; the unique index makes it idempotent. This is **at-least-once processing with idempotent persistence**, not exactly once. Group rebalances, uncertain acknowledgments and DLQ retries may duplicate DLQ records as well.

## DLQ replay

DLQ JSON contains `original` (the original string, including eventId when available), `key`, `sourceTopic`, `partition`, `offset`, `failedAt`, and `reason`. Restrict operator access because the original payload can contain sensitive application data.

Export a finite set, inspect it, repair malformed original records if necessary, then replay:

```bash
docker compose --env-file .env -f infra/compose.yaml exec -T kafka \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:19092 \
  --topic events.dlq.v1 --from-beginning --max-messages 100 --timeout-ms 10000 \
  > /tmp/observatory-dlq.jsonl
python3 scripts/replay-dlq.py /tmp/observatory-dlq.jsonl
```

An idle export timeout can exit nonzero; inspect the exported file. Replay is an operator tool, publishes through Kafka with acknowledgments, and preserves tenant, eventId and original expiry. Never invent a new eventId for a record with an existing identity. Malformed records without identity require manual repair and cannot be replayed blindly. Expired records can be deleted soon after replay; increasing retention is a separate administrative decision. Replaying the same file again is safe for already persisted event identities. The command does not delete or mark DLQ records; keep an operational replay audit externally.
