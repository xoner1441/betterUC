# betterUC Platform

Requires Node.js 20.9 or newer. The generated police roster PNG uses Sharp's current, security-patched libvips build.

Node service for the betterUC website, automatic Minecraft authentication and WebSocket ping relay.

## Routes

- `GET /` website
- `GET /download` download section
- `GET /updates` update section
- `GET /access` access-code section
- `POST /api/access` creates a personal access code
- `GET /api/status` public status
- `POST /api/auth/challenge` creates a one-time Minecraft session challenge
- `POST /api/auth/complete` verifies the Mojang session and issues a betterUC mod session
- `GET /api/players` online users plus 24h/7d/version statistics, requires a mod session or legacy access code
- `POST /api/screenshots` uploads an authenticated PNG screenshot for seven days
- `POST /api/bugs` creates a public Discord forum report; requires an access code
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
- `SESSION_SECRET=...` secret for signed web and mod sessions
- `MOD_SESSION_TTL_MS=2592000000` lifetime of an automatically issued mod session
- `AUTH_CHALLENGE_TTL_MS=60000` lifetime of a one-time Minecraft challenge
- `MOJANG_HAS_JOINED_URL=...` optional session-server override for local integration tests
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
- `DISCORD_UPDATE_CHANNEL_NAME=updates`
- `DISCORD_CHANGELOG_CHANNEL_ID=...` channel receiving the complete changelog after every release
- `DISCORD_CHANGELOG_CHANNEL_NAME=changelog` fallback changelog channel name
- `DISCORD_UPDATE_NOTIFY_ROLE_NAME=betterUC Updates` opt-in role mentioned for new releases
- `DISCORD_UPDATE_NOTIFY_ROLE_CREATE_MISSING=true` creates the opt-in role when necessary
- `DISCORD_TICKET_LOG_CHANNEL_ID=...` private channel receiving ticket transcripts
- `DISCORD_TICKET_TRANSCRIPT_DIR=/opt/betteruc-relay/data/ticket-transcripts`
- `DISCORD_SUGGESTION_CHANNEL_ID=...` public channel for persistent suggestions and votes
- `DISCORD_BUG_FORUM_CHANNEL_ID=...` public forum receiving authenticated in-game bug reports
- `DISCORD_BUG_FORUM_CHANNEL_NAME=bug-reports` fallback forum channel name
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
- `DISCORD_ANNOUNCEMENT_CHANNEL_ID=...` channel for important betterUC announcements
- `DISCORD_ANNOUNCEMENT_CHANNEL_NAME=ankündigungen` fallback announcement channel name
- `DISCORD_RELEASE_REPO=xoner1441/betterUC`
- `DISCORD_RELEASE_CHECK_MS=900000`
- `DISCORD_ANNOUNCE_EXISTING_RELEASE=false`
- `DISCORD_CHANGELOG_DATA_URL=https://betteruc.de/data/changelog.json`
- `PUBLIC_CHANGELOG_URL=https://betteruc.de/changelog`
- `TEAMSPEAK_FACTION_SYNC_ENABLED=true` enables the official UnicaCity faction list sync
- `TEAMSPEAK_FACTION_SLUG=police` selects the police faction
- `TEAMSPEAK_FACTION_SYNC_MS=600000` refresh interval, with a minimum of one minute
- `TEAMSPEAK_FACTION_RENDER_MODE=image` embeds the generated betterUC roster image instead of raw member text
- `TEAMSPEAK_FACTION_SLOT_LIMIT=42` police faction slot limit shown below the roster
- `TEAMSPEAK_FACTION_SECTION_START=PERSONALAKTE` start heading of the managed description section
- `TEAMSPEAK_FACTION_SECTION_END=STRAFZAHLUNGEN` end heading after the managed description section
- `TEAMSPEAK_FACTION_UNIT_OVERRIDES=FABI1441:SWAT,mteii:SWAT` optional units missing from the public API
- `TEAMSPEAK_QUERY_HOST=...` TeamSpeak ServerQuery host
- `TEAMSPEAK_QUERY_PORT=10011` TeamSpeak raw ServerQuery port
- `TEAMSPEAK_QUERY_USERNAME=...` restricted ServerQuery login
- `TEAMSPEAK_QUERY_PASSWORD=...` restricted ServerQuery password
- `TEAMSPEAK_VIRTUAL_SERVER_PORT=9987` TeamSpeak virtual server port
- `TEAMSPEAK_VIRTUAL_SERVER_ID=...` optional virtual server ID instead of the port
- `TEAMSPEAK_CHANNEL_ID=109` personnel department channel whose description receives the police member list
- `TEAMSPEAK_SWAT_CHANNEL_ID=108` SWAT channel whose description receives the authenticated in-game SWAT list
- `TEAMSPEAK_SWAT_SLOT_LIMIT=13` fallback slot limit for the SWAT list
- `TEAMSPEAK_SWAT_SECTION_START=EINHEITSLISTE` start marker in the SWAT channel description
- `TEAMSPEAK_SWAT_SECTION_END=` optional end marker; empty replaces the old SWAT roster through the end while preserving closing BBCode
- `SWAT_ROSTER_OWNER_NAME=FABI1441` only Mojang-authenticated mod account allowed to upload `/sfinfoall SWAT`
- `TEAMSPEAK_QUERY_TIMEOUT_MS=10000`
- `PUBLIC_BASE_URL=https://betteruc.de` public origin used by the clickable TeamSpeak image

The mod silently runs `/sfinfoall SWAT` after joining only for `SWAT_ROSTER_OWNER_NAME`, suppresses the command output, and uploads the parsed `[L]`/`[S]` roles through the authenticated mod session. By default the SWAT sync replaces everything after the `EINHEITSLISTE` heading, while preserving trailing closing BBCode tags.
- `POLICE_ROSTER_HEAD_BASE_URL=https://mc-heads.net/head` Minecraft head image provider; images are cached by betterUC

Access codes are only shown once. The server stores SHA-256 hashes with a secret pepper.
Account data is backed up automatically once per day and can also be backed up manually in the admin panel.

## TeamSpeak police member list

When `TEAMSPEAK_FACTION_SYNC_ENABLED=true`, the relay reads the official police roster from
`https://api.unicacity.eu/api/factions/police/members` and groups the players by rank. It reads the existing TeamSpeak
channel description and replaces only the content between the `PERSONALAKTE` and `STRAFZAHLUNGEN` headings. With the
default `image` render mode this section contains a cache-busted, clickable PNG. It links to the responsive public view
at `/polizei/mitglieder`; the PNG itself is served at `/api/teamspeak/police-roster.png`. Both views include cached 3D
Minecraft heads. The TeamSpeak PNG stays below 2048 pixels and uses an embedded pixel glyph renderer, so it does not
depend on fonts installed on the relay host. Introductory text and the penalty catalog remain unchanged. The description is only written when that
managed section differs. Set `TEAMSPEAK_FACTION_RENDER_MODE=text` to keep the legacy raw-text list. The query user
should be restricted to the selected virtual server and the permissions to read the channel information and edit that
channel description.

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
- `/updates post_latest` posts the latest release with the central betterUC changelog to the update channel.
- `/changelog` shows the latest published betterUC changes.
- `/changelog version:<version>` shows the changes for a specific published version.
- `/update-benachrichtigung an|aus` manages the optional release notification role.
- `/diagnose spieler:<name>` shows a private support diagnosis for team members.
- `/code create`, `/code reset`, `/code revoke` manage access codes. Requires Discord `Manage Server`.

Invite the bot with the scopes `bot` and `applications.commands`.

Linked accounts are synced to Discord roles. Every active linked account gets `DISCORD_MOD_USER_ROLE_NAME`.
Accounts with betterUC roles `vip`, `partner`, `helper` and `admin` also get the configured role names above. Revoked or unlinked
accounts lose the managed betterUC roles again.
The update notification role is deliberately independent of account role synchronization and is only changed through
`/update-benachrichtigung`. Every detected GitHub release is posted to the update channel and, with the full central
release text, to the configured changelog channel.
