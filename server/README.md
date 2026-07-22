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
- `DISCORD_PARTNER_ROLE_NAME=Partner`
- `DISCORD_HELPER_ROLE_NAME=Helper`
- `DISCORD_ADMIN_ROLE_NAME=Admin`
- `DISCORD_ROLE_SYNC_MS=300000`
- `DISCORD_ROLE_SYNC_CREATE_MISSING=true` creates missing managed roles when the bot has Manage Roles
- `DISCORD_TICKET_LOG_CHANNEL_ID=...` private channel receiving ticket transcripts
- `DISCORD_TICKET_TRANSCRIPT_DIR=/opt/betteruc-relay/data/ticket-transcripts`
- `DISCORD_SUGGESTION_CHANNEL_ID=...` public channel for persistent suggestions and votes
- `DISCORD_SUGGESTION_GUIDE_ENABLED=true` keeps the `/vorschlag erstellen` guide at the bottom of the suggestion channel
- `DISCORD_SUGGESTION_GUIDE_DELAY_MS=1500` debounce before the guide is moved below new messages
- `DISCORD_MONITOR_CHANNEL_ID=...` channel containing one permanently updated live system-status message
- `DISCORD_MONITOR_CHECK_MS=60000`
- `DISCORD_MONITOR_PIN_MESSAGE=true` pins the live status message when the bot has Manage Messages
- `DISCORD_BACKUP_MAX_AGE_HOURS=36`
- `DISCORD_CLOUD_ERROR_ALERT_COUNT=5`
- `DISCORD_WEEKLY_CHANNEL_ID=...` channel for the automatic weekly report
- `DISCORD_WEEKLY_REPORT_DAY=1` UTC weekday (`0` Sunday, `1` Monday)
- `DISCORD_WEEKLY_REPORT_HOUR_UTC=8`
- `DISCORD_UPDATE_CHANNEL_NAME=updates`
- `DISCORD_GLOBAL_CHAT_ENABLED=false` enables the live bridge after Discord's Message Content Intent is enabled
- `DISCORD_GLOBAL_CHAT_CHANNEL_ID=...` Discord channel bridged with `/buc`
- `DISCORD_GLOBAL_CHAT_CHANNEL_NAME=betteruc-chat` fallback when no channel ID is configured
- `DISCORD_GLOBAL_CHAT_LOG_CHANNEL_ID=...` optional private moderation log channel
- `DISCORD_GLOBAL_CHAT_LOG_CHANNEL_NAME=betteruc-chat-log` fallback log channel name
- `DISCORD_ANNOUNCEMENT_CHANNEL_ID=...` channel for important betterUC announcements
- `DISCORD_ANNOUNCEMENT_CHANNEL_NAME=ankündigungen` fallback announcement channel name
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
- `/broadcast nachricht:<text>` sends an important announcement to Discord and all connected mod users; betterUC Admin only.
- `/ticket` opens a private support ticket.
- `/ticket-panel` posts a button-based ticket panel. Requires Discord `Manage Server`.
- Ticket buttons allow team members to claim a ticket. Closing requires a reason and stores/uploads a transcript.
- `/rollen-sync` creates missing betterUC roles and synchronizes every linked account. Requires Discord `Manage Server`.
- `/systemstatus` checks the public website, relay process, PostgreSQL, migrations, Cloud errors and backups.
- `/vorschlag erstellen` posts a persistent proposal with one vote per linked account.
- `/vorschlag status` changes a proposal to open, planned, in progress, implemented or rejected.
- The bot maintains one suggestion guide message below all proposals and user messages.
- `/wochenstatistik` shows the last seven days of accounts, versions, chat, tickets, suggestions and Cloud activity.
- `/updates check` checks GitHub releases.
- `/updates post_latest` posts the latest GitHub release to the update channel.
- `/code create`, `/code reset`, `/code revoke` manage access codes. Requires Discord `Manage Server`.

Invite the bot with the scopes `bot` and `applications.commands`.

Linked accounts are synced to Discord roles. Every active linked account gets `DISCORD_MOD_USER_ROLE_NAME`.
Accounts with betterUC roles `vip`, `partner`, `helper` and `admin` also get the configured role names above. Revoked or unlinked
accounts lose the managed betterUC roles again.

### Discord and `/buc` live chat

Enable the **Message Content Intent** for the bot in the Discord Developer Portal, create the configured chat and log
channels, and set `DISCORD_GLOBAL_CHAT_ENABLED=true`. Only Discord users linked to an active betterUC account through
`/link` can forward messages into Minecraft. Messages are limited to 180 characters and share the two-second cooldown
with ingame `/buc` messages. Bot messages are never forwarded and outgoing Discord messages cannot trigger mentions.
