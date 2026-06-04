# betterUC Platform

Node service for the betterUC website, access-code API and WebSocket ping relay.

## Routes

- `GET /` website
- `GET /download` download section
- `GET /updates` update section
- `GET /access` access-code section
- `POST /api/access` creates a personal access code
- `GET /api/status` public status
- `GET /api/players` online mod users, requires an access code
- `GET /admin` admin control panel
- `GET /api/admin/accounts` list accounts, requires an admin user session or `ADMIN_KEY`
- `POST /api/admin/accounts` create a code, requires an admin user session or `ADMIN_KEY`
- `PATCH /api/admin/accounts/:id` edit account metadata, requires an admin user session or `ADMIN_KEY`
- `POST /api/admin/accounts/:id/revoke` revoke a code
- `POST /api/admin/accounts/:id/activate` reactivate a code
- `POST /api/admin/accounts/:id/reset-code` generate a new code once
- `POST /api/admin/accounts/:id/delete` delete an account
- `GET /health` relay health check
- `GET /ws` WebSocket relay endpoint

## Environment

- `PORT=3000`
- `BETTERUC_TOKEN=...` optional legacy shared token
- `TOKEN_PEPPER=...` secret pepper for access-code hashes
- `ADMIN_KEY=...` optional fallback secret for the admin control panel
- `MAX_CLIENTS=500`
- `PING_TTL_MS=15000`
- `DATA_DIR=/opt/betteruc-relay/data`

Access codes are only shown once. The server stores SHA-256 hashes with a secret pepper.
