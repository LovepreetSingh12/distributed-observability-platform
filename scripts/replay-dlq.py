#!/usr/bin/env python3
"""Replay an exported JSONL DLQ file through Kafka, retaining original identities."""
import json,sys,subprocess,uuid
from pathlib import Path
if len(sys.argv)!=2:raise SystemExit('Usage: python3 scripts/replay-dlq.py exported-dlq.jsonl')
lines=[]
for line in Path(sys.argv[1]).read_text().splitlines():
    record=json.loads(line);original=json.loads(record['original']);uuid.UUID(original['eventId'])
    for field in ['tenantId','applicationId','receivedAt','expiresAt']:assert original.get(field),f'Missing {field}; repair before replay'
    key=record.get('key') or ':'.join([original['tenantId'],original['applicationId'],original.get('traceId') or original['eventId']])
    assert '\t' not in key and '\n' not in key
    lines.append(key+'\t'+json.dumps(original,separators=(',',':')))
subprocess.run(['docker','compose','--env-file','.env','-f','infra/compose.yaml','exec','-T','kafka','/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','kafka:19092','--topic','events.ingested.v1','--property','parse.key=true','--property','key.separator=\t','--producer-property','acks=all','--producer-property','enable.idempotence=true'],input=('\n'.join(lines)+'\n').encode(),check=True)
print(f'Replayed {len(lines)} records; eventId, tenant and retention timestamps preserved.')
