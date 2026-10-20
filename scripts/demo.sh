#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ ! -f .env ]; then
  umask 077
  python3 - <<'PY'
import secrets
from pathlib import Path
Path('.env').write_text('DEMO_PASSWORD='+secrets.token_urlsafe(24)+'\nDEMO_API_KEY='+secrets.token_urlsafe(32)+'\n')
PY
fi
mvn -q -f backend/pom.xml -DskipTests package
npm --prefix frontend ci
npm --prefix frontend run build
docker compose --env-file .env -f infra/compose.yaml up -d --build --wait --wait-timeout 300
python3 scripts/emit-demo.py
python3 scripts/smoke.py
printf '\nOpen http://localhost:5173 · demo-admin@localhost · password in .env\n'
