# Deploy to the Hetzner server

Run this from Windows PowerShell in the project root:

```powershell
scp -r .\server\* root@65.109.175.203:/opt/betteruc-relay/
```

Then run this on the server as `root`:

```bash
cd /opt/betteruc-relay
npm install --omit=dev

grep -q '^TOKEN_PEPPER=' /etc/betteruc-relay.env || echo "TOKEN_PEPPER=$(openssl rand -hex 32)" >> /etc/betteruc-relay.env
grep -q '^ALLOW_LEGACY_TOKEN=' /etc/betteruc-relay.env || echo "ALLOW_LEGACY_TOKEN=true" >> /etc/betteruc-relay.env
grep -q '^ADMIN_KEY=' /etc/betteruc-relay.env || echo "ADMIN_KEY=$(openssl rand -base64 32 | tr -d '=+/')" >> /etc/betteruc-relay.env
grep -q '^BACKUP_RETENTION_DAYS=' /etc/betteruc-relay.env || echo "BACKUP_RETENTION_DAYS=30" >> /etc/betteruc-relay.env

# Optional Discord bot. Fill these manually if the bot should run on the relay:
# DISCORD_BOT_TOKEN=...
# DISCORD_GUILD_ID=...
# DISCORD_TICKET_CATEGORY_NAME=Tickets
# DISCORD_TEAM_ROLE_NAMES=Owner,Admin,Helper
# DISCORD_MOD_USER_ROLE_NAME=Mod-User
# DISCORD_USER_ROLE_NAME=
# DISCORD_VIP_ROLE_NAME=VIP
# DISCORD_PARTNER_ROLE_NAME=Partner
# DISCORD_HELPER_ROLE_NAME=Helper
# DISCORD_ADMIN_ROLE_NAME=Admin
# DISCORD_ROLE_SYNC_MS=300000
# DISCORD_UPDATE_CHANNEL_NAME=updates
# DISCORD_RELEASE_REPO=xoner1441/betterUC
# DISCORD_RELEASE_CHECK_MS=900000
# DISCORD_ANNOUNCE_EXISTING_RELEASE=false

cat > /etc/caddy/Caddyfile <<'EOF'
betteruc.de, www.betteruc.de {
    reverse_proxy 127.0.0.1:3000
}

ping.betteruc.de {
    reverse_proxy 127.0.0.1:3000
}
EOF

caddy fmt --overwrite /etc/caddy/Caddyfile
caddy validate --config /etc/caddy/Caddyfile
systemctl reload caddy
systemctl restart betteruc-relay
systemctl status betteruc-relay --no-pager
```

After deployment:

```bash
curl http://127.0.0.1:3000/health
curl http://127.0.0.1:3000/api/status
```

Read the fallback admin key:

```bash
grep '^ADMIN_KEY=' /etc/betteruc-relay.env
```

Open:

```text
https://betteruc.de/admin
```

Admin users can also open `/admin` from the Userpanel without entering this key.
Backups are written to `/opt/betteruc-relay/data/backups` once per day. The admin panel also has a manual backup button.

## PostgreSQL persistence

Install PostgreSQL once on the Hetzner server:

```bash
apt update
apt install -y postgresql postgresql-client

DB_PASSWORD="$(openssl rand -hex 32)"
sudo -u postgres psql -v ON_ERROR_STOP=1 <<SQL
create user betteruc_app with password '$DB_PASSWORD';
create database betteruc owner betteruc_app;
alter database betteruc set timezone to 'UTC';
SQL

echo "DATABASE_URL=postgresql://betteruc_app:$DB_PASSWORD@127.0.0.1:5432/betteruc" >> /etc/betteruc-relay.env
echo "DATABASE_REQUIRED=false" >> /etc/betteruc-relay.env
echo "DB_POOL_MAX=10" >> /etc/betteruc-relay.env
chmod 600 /etc/betteruc-relay.env
```

PostgreSQL stays bound to localhost. Do not add port `5432` to the Hetzner or UFW firewall.

Deploy the database-enabled server files, then verify the migration before making PostgreSQL mandatory:

```bash
cd /opt/betteruc-relay
npm install --omit=dev
set -a
. /etc/betteruc-relay.env
set +a
npm run db:check
systemctl restart betteruc-relay
curl -s http://127.0.0.1:3000/health
```

The first database start imports `/opt/betteruc-relay/data/accounts.json` only when PostgreSQL has no accounts.
The JSON file continues as a recovery mirror. The health response must report `"persistence":"postgres"`.

For a controlled manual import instead of the automatic first-start import:

```bash
cd /opt/betteruc-relay
npm run db:import-json
```

The importer refuses to overwrite a populated database. Use `--replace` only after creating a backup.
Use `npm run db:import-json -- --verify-only` to compare the existing database with the JSON mirror without writing data.

After checking the account count, Adminpanel, Userpanel, Discord links and one Mod connection, require PostgreSQL:

```bash
sed -i 's/^DATABASE_REQUIRED=.*/DATABASE_REQUIRED=true/' /etc/betteruc-relay.env
systemctl restart betteruc-relay
systemctl status betteruc-relay --no-pager
```

Create a daily PostgreSQL backup job:

```bash
install -d -m 700 /opt/betteruc-relay/data/postgres-backups

cat > /usr/local/sbin/backup-betteruc-db <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
set -a
. /etc/betteruc-relay.env
set +a
target="/opt/betteruc-relay/data/postgres-backups/betteruc-$(date -u +%Y-%m-%dT%H-%M-%SZ).dump"
pg_dump "$DATABASE_URL" --format=custom --file="$target"
find /opt/betteruc-relay/data/postgres-backups -type f -name '*.dump' -mtime +30 -delete
EOF

chmod 700 /usr/local/sbin/backup-betteruc-db
(crontab -l 2>/dev/null; echo '25 3 * * * /usr/local/sbin/backup-betteruc-db') | crontab -
/usr/local/sbin/backup-betteruc-db
```

Restore into an empty database with `pg_restore --clean --if-exists --dbname="$DATABASE_URL" <dump-file>`.

## Discord bot

The relay can run the Discord support bot when `DISCORD_BOT_TOKEN` and `DISCORD_GUILD_ID` are present in
`/etc/betteruc-relay.env`. Invite the bot with both scopes:

```text
bot
applications.commands
```

The bot supports:

- ticket channels through `/ticket` and `/ticket-panel`
- automatic GitHub release posts in `DISCORD_UPDATE_CHANNEL_NAME`
- role sync for `Mod-User`, `VIP`, `Helper` and `Admin` based on linked betterUC accounts

After changing the env file, restart:

```bash
systemctl restart betteruc-relay
systemctl status betteruc-relay --no-pager
```

## Automatic GitHub deployment

The repository contains `.github/workflows/deploy-server.yml`. After the secrets below are configured, every push to
`main` that changes files in `server/` automatically uploads the relay/website and restarts `betteruc-relay`.

Create a deploy key on your Windows PC:

```powershell
ssh-keygen -t ed25519 -C "betteruc-github-deploy" -f "$env:USERPROFILE\.ssh\betteruc_github_deploy" -N ""
Get-Content "$env:USERPROFILE\.ssh\betteruc_github_deploy.pub" | ssh root@65.109.175.203 "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys"
Get-Content "$env:USERPROFILE\.ssh\betteruc_github_deploy" -Raw
```

Add these GitHub repository secrets under `Settings -> Secrets and variables -> Actions`:

```text
DEPLOY_HOST=65.109.175.203
DEPLOY_USER=root
DEPLOY_PORT=22
DEPLOY_PATH=/opt/betteruc-relay
DEPLOY_SSH_KEY=<private key output from the last PowerShell command>
```

After that, push a server/website change or start the workflow manually from the GitHub Actions tab.
