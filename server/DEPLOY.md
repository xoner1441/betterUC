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

Read the admin key:

```bash
grep '^ADMIN_KEY=' /etc/betteruc-relay.env
```

Open:

```text
https://betteruc.de/admin
```
