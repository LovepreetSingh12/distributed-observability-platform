#!/usr/bin/env python3
import urllib.request,time
for i in range(1,61):
    urllib.request.urlopen(urllib.request.Request(f'http://localhost:8083/demo/orders/{i}',method='POST')).read()
    if i%10==0: urllib.request.urlopen(urllib.request.Request('http://localhost:8083/demo/error',method='POST')).read()
    time.sleep(.04)
print('Emitted 60 deterministic orders, application logs, and 6 intentional errors through the SDK.')
