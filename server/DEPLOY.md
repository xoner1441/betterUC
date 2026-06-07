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
