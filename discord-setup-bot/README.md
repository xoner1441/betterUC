# betterUC Discord Setup Bot

Dieses Tool richtet einen Discord-Server für betterUC automatisch ein.

Es erstellt:

- Rollen: `Owner`, `Admin`, `Helper`, `VIP`, `Mod-User`, `User`, `Muted`
- Kategorien und Textkanaele
- einfache Rechte für Team-, Info-, Support- und Community-Bereiche
- Startnachrichten für Willkommen, Regeln, Download, Access-Hilfe und Updates

Der Bot ist als einmaliges Setup-Tool gedacht. Danach kannst du ihn stoppen oder vom Server entfernen.

## 1. Bot erstellen

1. Oeffne das Discord Developer Portal.
2. Erstelle eine neue Application, z.B. `betterUC Setup`.
3. Gehe zu `Bot` und erstelle/reset den Bot-Token.
4. Kopiere den Token, aber poste ihn nirgendwo.

## 2. Bot einladen

Der Bot braucht für das Setup hohe Rechte. Am einfachsten gibst du ihm beim Einladen einmalig `Administrator`.

Nutze im Developer Portal unter `OAuth2 > URL Generator`:

- Scope: `bot` und `applications.commands`
- Bot Permissions: `Administrator`

Danach laedst du ihn auf deinen Discord-Server ein.

## 3. Config ausfuellen

Kopiere `.env.example` zu `.env` und fuelle aus:

```env
DISCORD_BOT_TOKEN=dein_bot_token
DISCORD_GUILD_ID=deine_server_id
DISCORD_CLIENT_ID=deine_bot_client_id
```

Die Server-ID bekommst du in Discord, wenn der Entwicklermodus aktiv ist:

`Einstellungen > Erweitert > Entwicklermodus`

Dann Rechtsklick auf deinen Server und `Server-ID kopieren`.

## 4. Erst testen

Optional kannst du vor dem echten Setup testen:

```powershell
$env:DRY_RUN="true"
node .\discord-setup-bot\setup-discord.js
```

## 5. Setup ausfuehren

```powershell
node .\discord-setup-bot\setup-discord.js
```

Wenn lokal kein `node` installiert ist, kannst du den Codex-Node benutzen:

```powershell
& "C:\Users\huhns\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe" .\discord-setup-bot\setup-discord.js
```

## 6. Bestehende Bot-Texte aktualisieren

Wenn der Bot die Startnachrichten schon gepostet hat und du nur die Texte aktualisieren willst:

```powershell
& "C:\Users\huhns\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe" .\discord-setup-bot\setup-discord.js --refresh-messages
```

## Hinweise

- `.env` ist in `.gitignore` eingetragen und soll nicht auf GitHub.
- Existierende Rollen/Kanäle mit gleichem Namen werden wiederverwendet.
- Startnachrichten werden nur in leere Kanäle gepostet, damit sie nicht dauernd doppelt erscheinen.
- Die Rollen-Reihenfolge kann Discord je nach Bot-Rollenposition begrenzen. Nach dem Setup kannst du die Rollen im Server manuell verschieben.
