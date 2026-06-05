#!/usr/bin/env node

const fs = require("node:fs");
const path = require("node:path");

const API = "https://discord.com/api/v10";
const ENV_PATH = path.join(__dirname, ".env");

const ChannelType = {
  GuildText: 0,
  GuildCategory: 4,
};

const OverwriteType = {
  Role: 0,
};

const Permissions = {
  CreateInstantInvite: 1n << 0n,
  KickMembers: 1n << 1n,
  BanMembers: 1n << 2n,
  Administrator: 1n << 3n,
  ManageChannels: 1n << 4n,
  ManageGuild: 1n << 5n,
  AddReactions: 1n << 6n,
  ViewAuditLog: 1n << 7n,
  ViewChannel: 1n << 10n,
  SendMessages: 1n << 11n,
  ManageMessages: 1n << 13n,
  ReadMessageHistory: 1n << 16n,
  MentionEveryone: 1n << 17n,
  ManageRoles: 1n << 28n,
  UseApplicationCommands: 1n << 31n,
  ModerateMembers: 1n << 40n,
};

const ROLE_DEFS = [
  {
    key: "owner",
    name: "Owner",
    color: 0xf8fafc,
    permissions: Permissions.Administrator,
    mentionable: false,
  },
  {
    key: "admin",
    name: "Admin",
    color: 0xef4444,
    permissions: Permissions.Administrator,
    mentionable: true,
  },
  {
    key: "helper",
    name: "Helper",
    color: 0xfacc15,
    permissions: Permissions.ManageMessages | Permissions.ModerateMembers,
    mentionable: true,
  },
  {
    key: "vip",
    name: "VIP",
    color: 0x7c3aed,
    permissions: 0n,
    mentionable: true,
  },
  {
    key: "modUser",
    name: "Mod-User",
    color: 0x22d3ee,
    permissions: 0n,
    mentionable: true,
  },
  {
    key: "user",
    name: "User",
    color: 0x22c55e,
    permissions: 0n,
    mentionable: false,
  },
  {
    key: "muted",
    name: "Muted",
    color: 0x64748b,
    permissions: 0n,
    mentionable: false,
  },
];

const CATEGORY_DEFS = [
  {
    name: "Start",
    channels: [
      { name: "willkommen", mode: "readonly" },
      { name: "regeln", mode: "readonly" },
      { name: "rollen", mode: "readonly" },
      { name: "faq", mode: "readonly" },
    ],
  },
  {
    name: "betterUC",
    channels: [
      { name: "download", mode: "readonly" },
      { name: "updates", mode: "readonly" },
      { name: "changelog", mode: "readonly" },
      { name: "access-code-hilfe", mode: "support" },
      { name: "website-status", mode: "readonly" },
    ],
  },
  {
    name: "Support",
    channels: [
      { name: "hilfe", mode: "support" },
      { name: "bugreports", mode: "support" },
      { name: "crash-reports", mode: "support" },
      { name: "feature-vorschlaege", mode: "support" },
    ],
  },
  {
    name: "Community",
    channels: [
      { name: "chat", mode: "public" },
      { name: "screenshots", mode: "public" },
      { name: "clips", mode: "public" },
      { name: "offtopic", mode: "public" },
    ],
  },
  {
    name: "Team",
    teamOnly: true,
    channels: [
      { name: "team-chat", mode: "team" },
      { name: "support-log", mode: "team" },
      { name: "admin-log", mode: "adminOnly" },
    ],
  },
];

const START_MESSAGES = {
  willkommen:
    "**betterUC Setup**\nWillkommen auf dem betterUC Discord. Hier findest du Downloads, Updates, Support und Hilfe zum Access-System.",
  regeln:
    "**Regeln**\n1. Bleib respektvoll.\n2. Keine Token, Passwörter oder privaten Daten posten.\n3. Bugs bitte mit Screenshot, Crashlog oder kurzer Beschreibung melden.\n4. Support bekommt nur genug Infos, um dir helfen zu können.",
  rollen:
    "**Rollen**\n`Admin` verwaltet den Server und das betterUC System.\n`Helper` unterstützt im Support.\n`VIP` ist eine besondere Community-Rolle.\n`Mod-User` markiert Nutzer der Mod.\n`User` ist die normale Basisrolle.",
  faq:
    "**FAQ**\n**Wo bekomme ich die Mod?** Im Download-Channel.\n**Wie verbinde ich die Mod?** Erstelle auf der Website einen Access Code und trage ihn im ClickGUI ein.\n**Wie komme ich ins Userpanel?** Ingame `/register <passwort>` nutzen und danach auf der Website einloggen.",
  download:
    "**Download**\nAktuelle Releases findest du hier:\nhttps://github.com/xoner1441/betterUC/releases/latest",
  updates:
    "**Updates**\nHier können später Release-Hinweise, GitHub-Links oder wichtige Wartungsinfos gepostet werden.",
  changelog:
    "**Changelog**\nDie wichtigsten Änderungen findest du auf der Website und in den GitHub Releases.",
  "access-code-hilfe":
    "**Access Code Hilfe**\n1. Website öffnen.\n2. Access Code erstellen.\n3. Code im betterUC ClickGUI eintragen.\n4. Danach verbindet sich das Ping- und Account-System automatisch.",
  "website-status":
    "**Website Status**\nDieser Channel ist für Hinweise zur Website, zum Relay und zu geplanten Wartungen gedacht.",
  bugreports:
    "**Bugreport Vorlage**\n**Was ist passiert?**\n**Was hast du gemacht?**\n**Screenshot/Crashlog:**\n**Mod-Version:**",
  "feature-vorschlaege":
    "**Feature-Vorschlag Vorlage**\n**Idee:**\n**Warum wäre das nützlich?**\n**Soll es für alle oder nur bestimmte Rollen gelten?**",
  "team-chat":
    "**Team**\nInterner Chat für Admins und Helper.",
  "support-log":
    "**Support Log**\nHier können Teamnotizen zu Supportfällen gesammelt werden.",
  "admin-log":
    "**Admin Log**\nInterner Adminbereich.",
};

main().catch(error => {
  console.error("");
  console.error("Setup fehlgeschlagen:");
  console.error(error.message || error);
  process.exit(1);
});

async function main() {
  loadEnvFile();

  if (process.argv.includes("--help") || process.argv.includes("-h")) {
    printHelp();
    return;
  }

  const shouldListGuilds = process.argv.includes("--list-guilds");
  const refreshMessages = process.argv.includes("--refresh-messages");
  const dryRun = parseBoolean(process.env.DRY_RUN) || process.argv.includes("--dry-run");
  const token = normalizeToken(process.env.DISCORD_BOT_TOKEN);
  const guildId = clean(process.env.DISCORD_GUILD_ID);
  const clientId = clean(process.env.DISCORD_CLIENT_ID);

  if (clientId) {
    console.log("Invite-Link:");
    console.log(`https://discord.com/oauth2/authorize?client_id=${encodeURIComponent(clientId)}&scope=bot%20applications.commands&permissions=8`);
    console.log("");
  }

  if (dryRun && (!token || !guildId)) {
    printPlan();
    return;
  }

  if (!token || token === "dein_bot_token") {
    throw new Error("DISCORD_BOT_TOKEN fehlt. Kopiere .env.example zu .env und trage den Bot-Token ein.");
  }

  const api = createDiscordApi(token, dryRun);
  const botUser = await api.get("/users/@me");

  if (shouldListGuilds) {
    const guilds = await api.get("/users/@me/guilds");
    console.log(`Verbunden als ${botUser.username}#${botUser.discriminator || "0000"}`);
    console.log("");
    if (!guilds.length) {
      console.log("Der Bot ist laut Discord auf keinem Server.");
      return;
    }
    console.log("Server, auf denen der Bot ist:");
    for (const guild of guilds) {
      console.log(`- ${guild.name}: ${guild.id}`);
    }
    return;
  }

  if (!guildId) {
    throw new Error("DISCORD_GUILD_ID fehlt. Trage die Server-ID in .env ein.");
  }

  const guild = await api.get(`/guilds/${guildId}`);

  console.log(`Verbunden als ${botUser.username}#${botUser.discriminator || "0000"}`);
  console.log(`Server: ${guild.name}`);
  console.log(dryRun ? "Modus: DRY_RUN (keine Änderungen)" : "Modus: Setup ausführen");
  console.log("");

  const roles = await ensureRoles(api, guildId);
  await trySetRolePositions(api, guildId, roles);
  const channels = await ensureChannels(api, guildId, roles);
  await seedMessages(api, channels, botUser.id, refreshMessages);

  console.log("");
  console.log("Fertig. Der betterUC Discord ist eingerichtet.");
  if (!dryRun) {
    console.log("Tipp: Bot-Rolle in Discord über die erstellten Rollen ziehen, falls Rollenfarben/Rechte nicht direkt greifen.");
  }
}

function loadEnvFile() {
  if (!fs.existsSync(ENV_PATH)) {
    return;
  }

  const lines = fs.readFileSync(ENV_PATH, "utf8").split(/\r?\n/);
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eq = trimmed.indexOf("=");
    if (eq === -1) continue;
    const key = trimmed.slice(0, eq).trim();
    let value = trimmed.slice(eq + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (!process.env[key]) {
      process.env[key] = value;
    }
  }
}

function createDiscordApi(token, dryRun) {
  return {
    get: path => request("GET", path),
    post: (path, body) => request("POST", path, body),
    patch: (path, body) => request("PATCH", path, body),
  };

  async function request(method, route, body = undefined, attempt = 0) {
    if (dryRun && method !== "GET") {
      console.log(`[dry-run] ${method} ${route}`);
      return { id: `dry-${Date.now()}-${Math.random()}`, ...body };
    }

    const response = await fetch(`${API}${route}`, {
      method,
      headers: {
        Authorization: `Bot ${token}`,
        "Content-Type": "application/json",
      },
      body: body ? JSON.stringify(body, (_, value) => typeof value === "bigint" ? value.toString() : value) : undefined,
    });

    if (response.status === 429 && attempt < 3) {
      const payload = await response.json().catch(() => ({}));
      const waitMs = Math.ceil((Number(payload.retry_after) || 1) * 1000);
      await sleep(waitMs);
      return request(method, route, body, attempt + 1);
    }

    if (response.status === 401) {
      throw new Error("Discord lehnt den Bot-Token ab. Prüfe DISCORD_BOT_TOKEN in discord-setup-bot/.env: Es muss der Token aus Developer Portal > Bot > Reset Token sein, nicht Client-ID, Client-Secret oder Public Key.");
    }

    if (!response.ok) {
      const detail = await response.text().catch(() => "");
      throw new Error(`${method} ${route} -> ${response.status} ${response.statusText}${detail ? ` | ${detail}` : ""}`);
    }

    if (response.status === 204) {
      return null;
    }

    return response.json();
  }
}

async function ensureRoles(api, guildId) {
  let existing = await api.get(`/guilds/${guildId}/roles`);
  const byName = new Map(existing.map(role => [role.name.toLowerCase(), role]));
  const roles = new Map();

  for (const def of ROLE_DEFS) {
    const payload = {
      name: def.name,
      color: def.color,
      hoist: def.key === "admin" || def.key === "helper",
      mentionable: def.mentionable,
      permissions: def.permissions.toString(),
    };

    const found = byName.get(def.name.toLowerCase());
    if (found) {
      console.log(`Rolle vorhanden: ${def.name}`);
      const updated = await api.patch(`/guilds/${guildId}/roles/${found.id}`, payload).catch(error => {
        console.warn(`Rolle konnte nicht aktualisiert werden (${def.name}): ${error.message}`);
        return found;
      });
      roles.set(def.key, updated || found);
      continue;
    }

    console.log(`Rolle erstellen: ${def.name}`);
    const created = await api.post(`/guilds/${guildId}/roles`, payload);
    roles.set(def.key, created);
  }

  roles.set("everyone", { id: guildId, name: "@everyone" });
  return roles;
}

async function trySetRolePositions(api, guildId, roles) {
  const ordered = ["muted", "user", "modUser", "vip", "helper", "admin", "owner"]
    .map((key, index) => {
      const role = roles.get(key);
      return role ? { id: role.id, position: index + 1 } : null;
    })
    .filter(Boolean);

  if (!ordered.length) return;

  try {
    await api.patch(`/guilds/${guildId}/roles`, ordered);
    console.log("Rollenpositionen gesetzt.");
  } catch (error) {
    console.warn(`Rollenpositionen konnten nicht automatisch gesetzt werden: ${error.message}`);
  }
}

async function ensureChannels(api, guildId, roles) {
  let existing = await api.get(`/guilds/${guildId}/channels`);
  const channelsByNameType = () => new Map(existing.map(channel => [`${channel.type}:${channel.name}:${channel.parent_id || ""}`, channel]));
  let map = channelsByNameType();
  const channelRefs = new Map();

  for (const categoryDef of CATEGORY_DEFS) {
    const categoryOverwrites = overwritesForMode(categoryDef.teamOnly ? "team" : "public", roles);
    const category = await ensureChannel(api, guildId, map, {
      name: categoryDef.name,
      type: ChannelType.GuildCategory,
      permission_overwrites: categoryOverwrites,
    });

    existing = await api.get(`/guilds/${guildId}/channels`);
    map = channelsByNameType();

    for (const channelDef of categoryDef.channels) {
      const channel = await ensureChannel(api, guildId, map, {
        name: channelDef.name,
        type: ChannelType.GuildText,
        parent_id: category.id,
        permission_overwrites: overwritesForMode(channelDef.mode, roles),
        topic: topicFor(channelDef.name),
      });
      channelRefs.set(channelDef.name, channel);
    }

    existing = await api.get(`/guilds/${guildId}/channels`);
    map = channelsByNameType();
  }

  return channelRefs;
}

async function ensureChannel(api, guildId, map, payload) {
  const key = `${payload.type}:${payload.name}:${payload.parent_id || ""}`;
  const fallbackKey = `${payload.type}:${payload.name}:`;
  const found = map.get(key) || map.get(fallbackKey);

  if (found) {
    console.log(`Kanal vorhanden: ${payload.name}`);
    return api.patch(`/channels/${found.id}`, payload).catch(error => {
      console.warn(`Kanal konnte nicht aktualisiert werden (${payload.name}): ${error.message}`);
      return found;
    });
  }

  console.log(`Kanal erstellen: ${payload.name}`);
  return api.post(`/guilds/${guildId}/channels`, payload);
}

function overwritesForMode(mode, roles) {
  const everyone = roles.get("everyone").id;
  const muted = roles.get("muted")?.id;
  const owner = roles.get("owner")?.id;
  const admin = roles.get("admin")?.id;
  const helper = roles.get("helper")?.id;

  const overwrites = [];

  if (mode === "team" || mode === "adminOnly") {
    overwrites.push(roleOverwrite(everyone, 0n, Permissions.ViewChannel));
    for (const roleId of [owner, admin]) {
      if (roleId) overwrites.push(roleOverwrite(roleId, Permissions.ViewChannel | Permissions.SendMessages | Permissions.ReadMessageHistory, 0n));
    }
    if (mode === "team" && helper) {
      overwrites.push(roleOverwrite(helper, Permissions.ViewChannel | Permissions.SendMessages | Permissions.ReadMessageHistory, 0n));
    }
    return overwrites;
  }

  if (mode === "readonly") {
    overwrites.push(roleOverwrite(everyone, Permissions.ViewChannel | Permissions.ReadMessageHistory, Permissions.SendMessages));
    for (const roleId of [owner, admin, helper]) {
      if (roleId) overwrites.push(roleOverwrite(roleId, Permissions.SendMessages | Permissions.ReadMessageHistory, 0n));
    }
  } else {
    overwrites.push(roleOverwrite(everyone, Permissions.ViewChannel | Permissions.SendMessages | Permissions.ReadMessageHistory | Permissions.UseApplicationCommands, 0n));
  }

  if (muted) {
    overwrites.push(roleOverwrite(muted, 0n, Permissions.SendMessages | Permissions.AddReactions));
  }

  return overwrites;
}

function roleOverwrite(id, allow, deny) {
  return {
    id,
    type: OverwriteType.Role,
    allow: allow.toString(),
    deny: deny.toString(),
  };
}

async function seedMessages(api, channels, botUserId, refreshMessages) {
  for (const [name, content] of Object.entries(START_MESSAGES)) {
    const channel = channels.get(name);
    if (!channel) continue;

    const messages = await api.get(`/channels/${channel.id}/messages?limit=5`).catch(() => []);
    const ownMessage = Array.isArray(messages)
      ? messages.find(message => message.author?.id === botUserId)
      : null;

    if (refreshMessages && ownMessage) {
      console.log(`Startnachricht aktualisieren: #${name}`);
      await api.patch(`/channels/${channel.id}/messages/${ownMessage.id}`, { content });
      continue;
    }

    if (refreshMessages && !ownMessage) {
      console.log(`Startnachricht neu posten: #${name}`);
      await api.post(`/channels/${channel.id}/messages`, { content });
      continue;
    }

    if (Array.isArray(messages) && messages.length > 0) {
      console.log(`Startnachricht übersprungen: #${name} ist nicht leer`);
      continue;
    }

    console.log(`Startnachricht posten: #${name}`);
    await api.post(`/channels/${channel.id}/messages`, { content });
  }
}

function topicFor(name) {
  const topics = {
    willkommen: "Startpunkt für neue betterUC Nutzer.",
    regeln: "Serverregeln und Hinweise.",
    rollen: "Rollenübersicht für betterUC.",
    faq: "Häufige Fragen zu betterUC.",
    download: "Aktuelle betterUC Downloads.",
    updates: "Update-Hinweise und Ankündigungen.",
    changelog: "Änderungen an Mod und Website.",
    "access-code-hilfe": "Hilfe beim Verbinden der Mod mit dem Access Code.",
    "website-status": "Statusinfos für Website und Relay.",
    hilfe: "Allgemeine Hilfe.",
    bugreports: "Fehlerberichte mit Screenshots oder Crashlogs.",
    "crash-reports": "Crashlogs und Startprobleme.",
    "feature-vorschlaege": "Ideen für neue betterUC Features.",
    chat: "Community-Chat.",
    screenshots: "Screenshots aus UnicaCity und betterUC.",
    clips: "Clips und kurze Videos.",
    offtopic: "Alles, was nicht direkt in die anderen Kanäle passt.",
    "team-chat": "Interner Team-Chat.",
    "support-log": "Interne Supportnotizen.",
    "admin-log": "Interner Adminbereich.",
  };
  return topics[name] || null;
}

function printPlan() {
  console.log("betterUC Discord Setup Plan");
  console.log("");
  console.log("Rollen:");
  for (const role of ROLE_DEFS) {
    console.log(`- ${role.name}`);
  }
  console.log("");
  console.log("Kategorien und Kanäle:");
  for (const category of CATEGORY_DEFS) {
    console.log(`- ${category.name}`);
    for (const channel of category.channels) {
      console.log(`  #${channel.name}`);
    }
  }
  console.log("");
  console.log("Zum echten Ausführen DISCORD_BOT_TOKEN und DISCORD_GUILD_ID in .env eintragen.");
}

function printHelp() {
  console.log("betterUC Discord Setup Bot");
  console.log("");
  console.log("Nutzung:");
  console.log("  node discord-setup-bot/setup-discord.js");
  console.log("  node discord-setup-bot/setup-discord.js --dry-run");
  console.log("  node discord-setup-bot/setup-discord.js --list-guilds");
  console.log("  node discord-setup-bot/setup-discord.js --refresh-messages");
  console.log("");
  console.log("Erforderliche .env Werte:");
  console.log("  DISCORD_BOT_TOKEN");
  console.log("  DISCORD_GUILD_ID");
  console.log("");
  console.log("Optional:");
  console.log("  DISCORD_CLIENT_ID");
  console.log("  DRY_RUN=true");
}

function parseBoolean(value) {
  return String(value || "").trim().toLowerCase() === "true";
}

function clean(value) {
  return String(value || "").trim();
}

function normalizeToken(value) {
  const token = clean(value);
  return token.toLowerCase().startsWith("bot ") ? token.slice(4).trim() : token;
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}
