#!/usr/bin/env python3
"""Run against a started demo. No secrets are printed."""
import json,time,uuid,urllib.request,urllib.error,datetime,pathlib
secrets=dict(line.split('=',1) for line in pathlib.Path('.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
def request(path,body=None,token=None,port=8080,method=None,expect=200):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    req=urllib.request.Request(f'http://localhost:{port}'+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method or ('POST' if body is not None else 'GET'))
    try:
        with urllib.request.urlopen(req,timeout=30) as r: status=r.status;raw=r.read()
    except urllib.error.HTTPError as e:status=e.code;raw=e.read()
    assert status==expect,(path,status,raw.decode()[:300])
    return json.loads(raw) if raw else None

def login(email):return request('/api/auth/login',{'email':email,'password':secrets['DEMO_PASSWORD']})['token']
admin=login('demo-admin@localhost');other=login('other-admin@localhost');viewer=login('demo-viewer@localhost')
now=datetime.datetime.now(datetime.timezone.utc)
q={'from':(now-datetime.timedelta(hours=1)).isoformat(),'to':(now+datetime.timedelta(minutes=5)).isoformat(),'eventType':'ORDER_CREATED'}
request('/demo/orders/777',{},port=8083)
for _ in range(60):
    rows=request('/api/query/events',q,admin)['events']
    if any(r['payload'].get('orderId')=='ORD-777' for r in rows):break
    time.sleep(1)
else:raise AssertionError('SDK event was not persisted')
print('PASS SDK → ingestion → Kafka → worker → MongoDB → query')
key=request('/api/applications/order-service/keys',{},admin)
event={'eventId':str(uuid.uuid4()),'baseSchemaVersion':1,'eventType':'ORDER_CREATED','payloadSchemaVersion':1,'occurredAt':now.isoformat(),'applicationId':'order-service','environment':'development','level':'INFO','message':'Smoke idempotency','traceId':'smoke-'+str(uuid.uuid4()),'payload':{'orderId':'SMOKE','amount':1499,'currency':'INR'}}
bad={**event,'eventId':str(uuid.uuid4()),'payload':{'amount':'invalid'}}
mismatch={**event,'eventId':str(uuid.uuid4()),'applicationId':'unauthorized-service'}
result=request('/api/v1/events/batch',[event,bad,mismatch,None],key['apiKey'],8081)
assert [r['status'] for r in result]==['accepted','rejected','rejected','rejected'],result
request('/api/v1/events/batch',[event],key['apiKey'],8081)
for _ in range(30):
    rows=request('/api/query/events',{**q,'traceId':event['traceId']},admin)['events']
    if rows:break
    time.sleep(1)
assert len(rows)==1 and rows[0]['eventId']==event['eventId']
print('PASS partial outcomes, invalid payload, identity mismatch, duplicate persistence')
assert not request('/api/query/events',{**q,'traceId':event['traceId']},other)['events']
request('/api/applications',{'applicationId':'forbidden','name':'No'},viewer,expect=403)
request('/api/query/events',{**q,'tenantId':'other'},admin,expect=400)
request('/api/query/events',{**q,'filters':[{'field':'$where','op':'eq','value':'evil'}]},admin,expect=400)
print('PASS tenant isolation, viewer permissions, query restrictions')
request('/api/applications/order-service/keys/'+key['keyId'],token=admin,method='DELETE')
request('/api/v1/events/batch',[event],key['apiKey'],8081,expect=401)
request('/api/schemas/ORDER_CREATED/1',{'$schema':'https://json-schema.org/draft/2020-12/schema','type':'object'},admin,method='PUT',expect=400)
validation=request('/api/schemas/ORDER_CREATED/1/validate',bad['payload'],admin)
assert not validation['valid']
print('PASS credential revocation and immutable schema validation')
rejected=request('/demo/counters',port=8083)['rejected'];request('/demo/invalid',{},port=8083)
for _ in range(20):
    if request('/demo/counters',port=8083)['rejected']>rejected:break
    time.sleep(.5)
else:raise AssertionError('SDK did not observe permanent rejection')
panels=[{'i':'count','title':'Total events','type':'count','x':0,'y':0,'w':4,'h':2,'query':{}},{'i':'volume','title':'Event volume','type':'timeseries','x':4,'y':0,'w':8,'h':2,'query':{}},{'i':'levels','title':'Events by level','type':'bar','x':0,'y':2,'w':4,'h':3,'query':{'field':'level'}},{'i':'logs','title':'Recent events','type':'logs','x':4,'y':2,'w':8,'h':3,'query':{}}]
id='d1f4ea52-314f-4f82-bb45-8306f3807fc0'
request('/api/dashboards/'+id,{'title':'Application overview','panels':panels,'refreshSeconds':30,'timeRangeMinutes':60},admin,method='PUT')
assert any(d['dashboardId']==id for d in request('/api/dashboards',token=admin))
assert not any(d['dashboardId']==id for d in request('/api/dashboards',token=other))
assert request('/api/query/aggregate',{**q,'metric':'count'},admin)[0]['value']>0
assert request('/api/query/aggregate',{**q,'metric':'sum','field':'payload.amount','schemaVersion':1},admin)[0]['value']>0
print('PASS persisted dashboard, numeric aggregation and SDK rejection counters')
with urllib.request.urlopen('http://localhost:5173') as r:assert r.status==200 and b'Observatory' in r.read()
print('PASS frontend HTTP serving. Use browser-smoke for rendered UI verification.')
