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
- `POST /api/admin/backups` creates an immediate `accounts.json` backup, requires admin access
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
- `BACKUP_DIR=/opt/betteruc-relay/data/backups`
- `BACKUP_RETENTION_DAYS=30`
- `MAX_CLIENTS=500`
- `PING_TTL_MS=15000`
- `DATA_DIR=/opt/betteruc-relay/data`
- `DISCORD_BOT_TOKEN=...` optional, starts the Discord support bot
- `DISCORD_GUILD_ID=...` Discord server ID for fast slash-command sync
- `DISCORD_TICKET_CATEGORY_NAME=Tickets`
- `DISCORD_TEAM_ROLE_NAMES=Owner,Admin,Helper`
- `DISCORD_MOD_USER_ROLE_NAME=Mod-User`
- `DISCORD_USER_ROLE_NAME=...` optional extra role for normal users
- `DISCORD_VIP_ROLE_NAME=VIP`
- `DISCORD_HELPER_ROLE_NAME=Helper`
- `DISCORD_ADMIN_ROLE_NAME=Admin`
- `DISCORD_ROLE_SYNC_MS=300000`
- `DISCORD_UPDATE_CHANNEL_NAME=updates`
- `DISCORD_RELEASE_REPO=xoner1441/betterUC`
- `DISCORD_RELEASE_CHECK_MS=900000`
- `DISCORD_ANNOUNCE_EXISTING_RELEASE=false`

Access codes are only shown once. The server stores SHA-256 hashes with a secret pepper.
Account data is backed up automatically once per day and can also be backed up manually in the admin panel.

## Discord bot

If `DISCORD_BOT_TOKEN` is configured, the relay also starts the betterUC Discord bot.

Slash commands:

- `/online` shows connected betterUC mod users.
- `/relay` shows relay/account totals.
- `/user name:<name>` shows known account and tracking data.
- `/me` shows your linked betterUC account.
- `/link code:<access-code>` links Discord to a betterUC account and gives the `Mod-User` role when possible.
- `/unlink` removes that Discord link.
- `/ticket` opens a private support ticket.
- `/ticket-panel` posts a button-based ticket panel. Requires Discord `Manage Server`.
- `/updates check` checks GitHub releases.
- `/updates post_latest` posts the latest GitHub release to the update channel.
- `/code create`, `/code reset`, `/code revoke` manage access codes. Requires Discord `Manage Server`.

Invite the bot with the scopes `bot` and `applications.commands`.

Linked accounts are synced to Discord roles. Every active linked account gets `DISCORD_MOD_USER_ROLE_NAME`.
Accounts with betterUC roles `vip`, `helper` and `admin` also get the configured role names above. Revoked or unlinked
accounts lose the managed betterUC roles again.
