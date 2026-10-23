#!/bin/sh
set -eu

roles=${1:-document_clerk,team_lead,assessor,finance,system}
subject=${2:-local-user}
tenant=${3:-local-tenant}
secret=${JWT_SECRET:-local-development-signing-key-at-least-32-bytes}

python3 - "$roles" "$subject" "$tenant" "$secret" <<'PY'
import base64, hashlib, hmac, json, sys, time

roles, subject, tenant, secret = sys.argv[1:]
encode = lambda value: base64.urlsafe_b64encode(value).rstrip(b"=")
header = encode(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
now = int(time.time())
payload = encode(json.dumps({
    "iss": "claimworkflow", "aud": ["claimworkflow-api"], "sub": subject,
    "tenant_id": tenant, "roles": roles.split(","), "iat": now, "exp": now + 3600
}, separators=(",", ":")).encode())
message = header + b"." + payload
signature = encode(hmac.new(secret.encode(), message, hashlib.sha256).digest())
print((message + b"." + signature).decode())
PY
