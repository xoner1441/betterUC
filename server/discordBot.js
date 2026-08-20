"use strict";

const fs = require("fs");
const fsp = fs.promises;
const path = require("path");

let ActionRowBuilder;
let ActivityType;
let AttachmentBuilder;
let ButtonBuilder;
let ButtonStyle;
let ChannelType;
let Client;
let EmbedBuilder;
let GatewayIntentBits;
let ModalBuilder;
let PermissionFlagsBits;
let SlashCommandBuilder;
let TextInputBuilder;
let TextInputStyle;

const BOT_TOKEN = clean(process.env.DISCORD_BOT_TOKEN);
const GUILD_ID = clean(process.env.DISCORD_GUILD_ID);
const TICKET_CATEGORY_NAME = clean(process.env.DISCORD_TICKET_CATEGORY_NAME) || "Tickets";
const TEAM_ROLE_NAMES = listEnv(process.env.DISCORD_TEAM_ROLE_NAMES || "Owner,Admin,Helper");
const MOD_USER_ROLE_NAME = clean(process.env.DISCORD_MOD_USER_ROLE_NAME) || "Mod-User";
const USER_ROLE_NAME = clean(process.env.DISCORD_USER_ROLE_NAME);
const VIP_ROLE_NAME = clean(process.env.DISCORD_VIP_ROLE_NAME) || "VIP";
const PARTNER_ROLE_NAME = clean(process.env.DISCORD_PARTNER_ROLE_NAME) || "Partner";
const HELPER_ROLE_NAME = clean(process.env.DISCORD_HELPER_ROLE_NAME) || "Helper";
const ADMIN_ROLE_NAME = clean(process.env.DISCORD_ADMIN_ROLE_NAME) || "Admin";
const UPDATE_CHANNEL_NAME = clean(process.env.DISCORD_UPDATE_CHANNEL_NAME) || "updates";
const CHANGELOG_CHANNEL_ID = clean(process.env.DISCORD_CHANGELOG_CHANNEL_ID);
const CHANGELOG_CHANNEL_NAME = clean(process.env.DISCORD_CHANGELOG_CHANNEL_NAME) || "changelog";
const UPDATE_NOTIFY_ROLE_NAME = clean(process.env.DISCORD_UPDATE_NOTIFY_ROLE_NAME) || "betterUC Updates";
const UPDATE_NOTIFY_ROLE_CREATE_MISSING = String(
  process.env.DISCORD_UPDATE_NOTIFY_ROLE_CREATE_MISSING || "true"
).toLowerCase() !== "false";
const ANNOUNCEMENT_CHANNEL_ID = clean(process.env.DISCORD_ANNOUNCEMENT_CHANNEL_ID);
const ANNOUNCEMENT_CHANNEL_NAME = clean(process.env.DISCORD_ANNOUNCEMENT_CHANNEL_NAME) || "ank\u00fcndigungen";
const TICKET_LOG_CHANNEL_ID = clean(process.env.DISCORD_TICKET_LOG_CHANNEL_ID);
const TICKET_LOG_CHANNEL_NAME = clean(process.env.DISCORD_TICKET_LOG_CHANNEL_NAME) || "ticket-logs";
const TICKET_TRANSCRIPT_DIR = process.env.DISCORD_TICKET_TRANSCRIPT_DIR
  || path.join(process.env.DATA_DIR || path.join(__dirname, "data"), "ticket-transcripts");
const SUGGESTION_CHANNEL_ID = clean(process.env.DISCORD_SUGGESTION_CHANNEL_ID);
const SUGGESTION_CHANNEL_NAME = clean(process.env.DISCORD_SUGGESTION_CHANNEL_NAME) || "vorschl\u00e4ge";
const SUGGESTION_GUIDE_ENABLED = String(process.env.DISCORD_SUGGESTION_GUIDE_ENABLED || "true").toLowerCase() !== "false";
const SUGGESTION_GUIDE_DELAY_MS = Math.max(500, Number(process.env.DISCORD_SUGGESTION_GUIDE_DELAY_MS || 1500));
const BUG_FORUM_CHANNEL_ID = clean(process.env.DISCORD_BUG_FORUM_CHANNEL_ID);
const BUG_FORUM_CHANNEL_NAME = clean(process.env.DISCORD_BUG_FORUM_CHANNEL_NAME) || "bug-reports";
const MONITOR_CHANNEL_ID = clean(process.env.DISCORD_MONITOR_CHANNEL_ID);
const MONITOR_CHANNEL_NAME = clean(process.env.DISCORD_MONITOR_CHANNEL_NAME) || "systemstatus";
const MONITOR_ENABLED = String(process.env.DISCORD_MONITOR_ENABLED || "true").toLowerCase() !== "false";
const MONITOR_CHECK_MS = Math.max(30 * 1000, Number(process.env.DISCORD_MONITOR_CHECK_MS || 60 * 1000));
const MONITOR_PIN_MESSAGE = String(process.env.DISCORD_MONITOR_PIN_MESSAGE || "true").toLowerCase() !== "false";
const BACKUP_MAX_AGE_HOURS = Math.max(1, Number(process.env.DISCORD_BACKUP_MAX_AGE_HOURS || 36));
const CLOUD_ERROR_ALERT_COUNT = Math.max(1, Number(process.env.DISCORD_CLOUD_ERROR_ALERT_COUNT || 5));
const WEEKLY_CHANNEL_ID = clean(process.env.DISCORD_WEEKLY_CHANNEL_ID);
const WEEKLY_CHANNEL_NAME = clean(process.env.DISCORD_WEEKLY_CHANNEL_NAME) || "wochenstatistik";
const WEEKLY_REPORT_DAY = Math.min(6, Math.max(0, Number(process.env.DISCORD_WEEKLY_REPORT_DAY || 1)));
const WEEKLY_REPORT_HOUR_UTC = Math.min(23, Math.max(0, Number(process.env.DISCORD_WEEKLY_REPORT_HOUR_UTC || 8)));
const ROLE_SYNC_CREATE_MISSING = String(process.env.DISCORD_ROLE_SYNC_CREATE_MISSING || "true").toLowerCase() !== "false";
const PUBLIC_BASE_URL = clean(process.env.PUBLIC_BASE_URL) || "https://betteruc.de";
const RELEASE_REPO = clean(process.env.DISCORD_RELEASE_REPO) || "xoner1441/betterUC";
const RELEASE_CHECK_MS = Math.max(5 * 60 * 1000, Number(process.env.DISCORD_RELEASE_CHECK_MS || 15 * 60 * 1000));
const ANNOUNCE_EXISTING_RELEASE = String(process.env.DISCORD_ANNOUNCE_EXISTING_RELEASE || "false").toLowerCase() === "true";
const PUBLIC_DOWNLOAD_URL = clean(process.env.PUBLIC_DOWNLOAD_URL) || "https://betteruc.de/download";
const PUBLIC_CHANGELOG_URL = clean(process.env.PUBLIC_CHANGELOG_URL) || `${PUBLIC_BASE_URL}/changelog`;
const CHANGELOG_DATA_URL = clean(process.env.DISCORD_CHANGELOG_DATA_URL) || `${PUBLIC_BASE_URL}/data/changelog.json`;
const CHANGELOG_DATA_FILE = process.env.DISCORD_CHANGELOG_DATA_FILE
  || path.join(__dirname, "public", "data", "changelog.json");
const CHANGELOG_CACHE_MS = Math.max(30 * 1000, Number(process.env.DISCORD_CHANGELOG_CACHE_MS || 5 * 60 * 1000));
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, "data");
const BOT_STATE_FILE = process.env.DISCORD_BOT_STATE_FILE || path.join(DATA_DIR, "discord-bot-state.json");

let botState = {};
let changelogCache = {
  loadedAt: 0,
  value: null
};

function loadDiscord() {
  if (Client) return;
  ({
    ActionRowBuilder,
    ActivityType,
    AttachmentBuilder,
    ButtonBuilder,
    ButtonStyle,
    ChannelType,
    Client,
    EmbedBuilder,
    GatewayIntentBits,
    ModalBuilder,
    PermissionFlagsBits,
    SlashCommandBuilder,
    TextInputBuilder,
    TextInputStyle
  } = require("discord.js"));
}

function clean(value) {
  return String(value || "").trim();
}

function listEnv(value) {
  return String(value || "")
    .split(",")
    .map(entry => entry.trim())
    .filter(Boolean);
}

async function readBotState() {
  try {
    const raw = await fsp.readFile(BOT_STATE_FILE, "utf8");
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch (error) {
    if (error.code === "ENOENT") return {};
    console.warn("Could not read Discord bot state", error.message);
    return {};
  }
}

async function writeBotState(state) {
  try {
    await fsp.mkdir(path.dirname(BOT_STATE_FILE), { recursive: true });
    const tmp = `${BOT_STATE_FILE}.tmp`;
    await fsp.writeFile(tmp, JSON.stringify(state, null, 2), "utf8");
    await fsp.rename(tmp, BOT_STATE_FILE);
  } catch (error) {
    console.warn("Could not write Discord bot state", error.message);
  }
}

function roleLabel(role) {
  if (role === "admin") return "Admin";
  if (role === "helper") return "Helper";
  if (role === "partner") return "Partner";
  if (role === "vip") return "VIP";
  return "User";
}

function roleColor(role) {
  if (role === "admin") return 0xff4d5a;
  if (role === "helper") return 0xfacc15;
  if (role === "partner") return 0x22d3ee;
  if (role === "vip") return 0x6d28d9;
  return 0x22c55e;
}

function display(value, fallback = "-") {
  const raw = String(value || "").trim();
  return raw || fallback;
}

function formatMoney(value) {
  if (typeof value !== "number") return "-";
  return `${value.toLocaleString("de-DE")}$`;
}

function formatStats(account) {
  const stats = account && account.stats ? account.stats : {};
  return [
    `Bank: ${formatMoney(stats.bankMoney)}`,
    `Bargeld: ${formatMoney(stats.cashMoney)}`,
    `Fraktion: ${display(stats.factionDisplay || account?.faction)}`,
    `Haeuser: ${display(stats.houses)}`,
    `Treuebonus: ${stats.loyaltyBonus ?? "-"}`,
    `Spielzeit: ${stats.playTimeHours ?? "-"}h`,
    `Votepoints: ${stats.votepoints ?? "-"}`,
    `Warns: ${display(stats.warns)}`
  ].join("\n");
}

function buildCommands() {
  return [
    new SlashCommandBuilder()
      .setName("online")
      .setDescription("Zeigt alle gerade verbundenen betterUC Mod-User."),
    new SlashCommandBuilder()
      .setName("relay")
      .setDescription("Zeigt den aktuellen Status vom betterUC Relay."),
    new SlashCommandBuilder()
      .setName("user")
      .setDescription("Zeigt bekannte betterUC Daten zu einem Minecraft-Spieler.")
      .addStringOption(option => option
        .setName("name")
        .setDescription("Minecraft-Name")
        .setRequired(true)),
    new SlashCommandBuilder()
      .setName("me")
      .setDescription("Zeigt deinen verknuepften betterUC Account."),
    new SlashCommandBuilder()
      .setName("link")
      .setDescription("Verknuepft deinen Discord-Account mit deinem betterUC Access-Code.")
      .addStringOption(option => option
        .setName("code")
        .setDescription("Dein betterUC Access-Code")
        .setRequired(true)),
    new SlashCommandBuilder()
      .setName("unlink")
      .setDescription("Loest die Verknuepfung zwischen Discord und betterUC."),
    new SlashCommandBuilder()
      .setName("broadcast")
      .setDescription("Sendet eine wichtige betterUC-Ankuendigung an Discord und ingame.")
      .addStringOption(option => option
        .setName("nachricht")
        .setDescription("Text der Ankuendigung")
        .setMaxLength(300)
        .setRequired(true)),
    new SlashCommandBuilder()
      .setName("ticket")
      .setDescription("Oeffnet ein privates Support-Ticket.")
      .addStringOption(option => option
        .setName("thema")
        .setDescription("Worum geht es?")
        .setRequired(true)
        .addChoices(
          { name: "Support", value: "support" },
          { name: "Bug melden", value: "bug" },
          { name: "Access-Code Problem", value: "access" },
          { name: "Account Problem", value: "account" }
        )),
    new SlashCommandBuilder()
      .setName("ticket-panel")
      .setDescription("Postet ein Ticket-Panel mit Buttons in diesen Channel.")
      .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild),
    new SlashCommandBuilder()
      .setName("updates")
      .setDescription("Prueft oder postet betterUC GitHub-Updates.")
      .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild)
      .addSubcommand(subcommand => subcommand
        .setName("check")
        .setDescription("Prueft, ob ein neues betterUC-Release existiert."))
      .addSubcommand(subcommand => subcommand
        .setName("post_latest")
        .setDescription("Postet das aktuelle betterUC-Release erneut in den Update-Channel.")),
    new SlashCommandBuilder()
      .setName("changelog")
      .setDescription("Zeigt die Neuerungen einer betterUC-Version.")
      .addStringOption(option => option
        .setName("version")
        .setDescription("Optional: zum Beispiel 1.4.0")
        .setMaxLength(32)
        .setRequired(false)),
    new SlashCommandBuilder()
      .setName("update-benachrichtigung")
      .setDescription("Schaltet deine Benachrichtigung bei neuen betterUC-Versionen um.")
      .addSubcommand(subcommand => subcommand
        .setName("an")
        .setDescription("Benachrichtigt dich bei neuen betterUC-Versionen."))
      .addSubcommand(subcommand => subcommand
        .setName("aus")
        .setDescription("Deaktiviert deine Update-Benachrichtigung.")),
    new SlashCommandBuilder()
      .setName("diagnose")
      .setDescription("Prueft den betterUC-Status eines Spielers fuer den Support.")
      .addStringOption(option => option
        .setName("spieler")
        .setDescription("Minecraft-Name")
        .setRequired(true)),
    new SlashCommandBuilder()
      .setName("code")
      .setDescription("Access-Codes ueber Discord verwalten.")
      .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild)
      .addSubcommand(subcommand => subcommand
        .setName("create")
        .setDescription("Erstellt einen neuen Access-Code.")
        .addStringOption(option => option
          .setName("name")
          .setDescription("Minecraft-Name")
          .setRequired(true))
        .addStringOption(option => option
          .setName("rolle")
          .setDescription("betterUC Rolle")
          .setRequired(false)
          .addChoices(
            { name: "User", value: "user" },
            { name: "VIP", value: "vip" },
            { name: "Partner", value: "partner" },
            { name: "Helper", value: "helper" },
            { name: "Admin", value: "admin" }
          ))
        .addStringOption(option => option
          .setName("fraktion")
          .setDescription("Optional: Fraktion")
          .setRequired(false)))
      .addSubcommand(subcommand => subcommand
        .setName("reset")
        .setDescription("Generiert fuer einen Account einen neuen Access-Code.")
        .addStringOption(option => option
          .setName("name")
          .setDescription("Minecraft-Name")
          .setRequired(true)))
      .addSubcommand(subcommand => subcommand
        .setName("revoke")
        .setDescription("Sperrt einen Account.")
        .addStringOption(option => option
          .setName("name")
          .setDescription("Minecraft-Name")
          .setRequired(true))),
    new SlashCommandBuilder()
      .setName("rollen-sync")
      .setDescription("Legt betterUC-Rollen an und synchronisiert alle verknuepften Nutzer.")
      .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild),
    new SlashCommandBuilder()
      .setName("systemstatus")
      .setDescription("Prueft Relay, Website, PostgreSQL und Backups.")
      .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild),
    new SlashCommandBuilder()
      .setName("vorschlag")
      .setDescription("betterUC Vorschlaege einreichen und verwalten.")
      .addSubcommand(subcommand => subcommand
        .setName("erstellen")
        .setDescription("Reicht einen neuen Vorschlag ein.")
        .addStringOption(option => option
          .setName("titel")
          .setDescription("Kurzer Titel")
          .setMaxLength(80)
          .setRequired(true))
        .addStringOption(option => option
          .setName("beschreibung")
          .setDescription("Was soll geaendert werden und warum?")
          .setMaxLength(1000)
          .setRequired(true)))
      .addSubcommand(subcommand => subcommand
        .setName("status")
        .setDescription("Aendert den Status eines Vorschlags.")
        .addIntegerOption(option => option
          .setName("id")
          .setDescription("Vorschlags-ID")
          .setMinValue(1)
          .setRequired(true))
        .addStringOption(option => option
          .setName("status")
          .setDescription("Neuer Bearbeitungsstatus")
          .setRequired(true)
          .addChoices(
            { name: "Offen", value: "open" },
            { name: "Geplant", value: "planned" },
            { name: "In Arbeit", value: "in_progress" },
            { name: "Umgesetzt", value: "implemented" },
            { name: "Abgelehnt", value: "rejected" }
          ))
        .addStringOption(option => option
          .setName("notiz")
          .setDescription("Optionale Begruendung")
          .setMaxLength(300)
          .setRequired(false))),
    new SlashCommandBuilder()
      .setName("wochenstatistik")
      .setDescription("Zeigt die betterUC-Auswertung der letzten sieben Tage.")
      .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild)
  ].map(command => command.toJSON());
}

function onlineEmbed(players) {
  const sorted = [...players].sort((a, b) => {
    const priority = (b.priority || 0) - (a.priority || 0);
    if (priority !== 0) return priority;
    return String(a.name || "").localeCompare(String(b.name || ""));
  });
  const lines = sorted.slice(0, 40).map(player => {
    const bits = [
      `**${display(player.name, "unknown")}**`,
      roleLabel(player.role),
      display(player.faction, ""),
      player.version ? `Mod v${player.version}` : "",
      player.gameVersion ? `MC ${player.gameVersion}` : ""
    ].filter(Boolean);
    return bits.join(" | ");
  });

  return new EmbedBuilder()
    .setTitle("betterUC Mod-User online")
    .setColor(0x38bdf8)
    .setDescription(lines.length ? lines.join("\n") : "Aktuell ist kein Mod-User verbunden.")
    .setFooter({ text: `${players.length} online` });
}

function relayEmbed(players, accounts) {
  const activeAccounts = accounts.filter(account => account.status !== "revoked");
  const admins = activeAccounts.filter(account => account.role === "admin").length;
  const helpers = activeAccounts.filter(account => account.role === "helper").length;
  const partners = activeAccounts.filter(account => account.role === "partner").length;
  const vips = activeAccounts.filter(account => account.role === "vip").length;

  return new EmbedBuilder()
    .setTitle("betterUC Relay")
    .setColor(players.length ? 0x22c55e : 0xfacc15)
    .addFields(
      { name: "Online", value: String(players.length), inline: true },
      { name: "Accounts", value: String(activeAccounts.length), inline: true },
      { name: "Rollen", value: `Admin ${admins} | Helper ${helpers} | Partner ${partners} | VIP ${vips}`, inline: true }
    )
    .setTimestamp(new Date());
}

function userEmbed(account, onlinePlayer) {
  const role = account?.role || onlinePlayer?.role || "user";
  const name = account?.minecraftName || onlinePlayer?.name || "unknown";
  const embed = new EmbedBuilder()
    .setTitle(name)
    .setColor(roleColor(role))
    .addFields(
      { name: "Rolle", value: roleLabel(role), inline: true },
      { name: "Status", value: onlinePlayer ? "Online" : display(account?.status, "offline"), inline: true },
      { name: "Fraktion", value: display(account?.stats?.factionDisplay || account?.faction || onlinePlayer?.faction), inline: true }
    );

  if (account) {
    embed.addFields({ name: "Tracking", value: formatStats(account), inline: false });
    if (account.lastSeenAt) embed.setFooter({ text: `Zuletzt gesehen: ${account.lastSeenAt}` });
  }

  return embed;
}

function trimText(value, maxLength = 900) {
  const raw = String(value || "").trim();
  if (raw.length <= maxLength) return raw;
  return `${raw.slice(0, maxLength - 3).trim()}...`;
}

function normalizeVersion(value) {
  return clean(value).replace(/^v/i, "");
}

function isChangelogData(value) {
  return Boolean(value && Array.isArray(value.releases));
}

async function readCentralChangelog(options = {}) {
  const force = Boolean(options.force);
  if (!force
      && changelogCache.value
      && Date.now() - changelogCache.loadedAt < CHANGELOG_CACHE_MS) {
    return changelogCache.value;
  }

  let localError;
  try {
    const raw = await fsp.readFile(CHANGELOG_DATA_FILE, "utf8");
    const parsed = JSON.parse(raw);
    if (!isChangelogData(parsed)) throw new Error("ungueltiges Changelog-Format");
    changelogCache = { loadedAt: Date.now(), value: parsed };
    return parsed;
  } catch (error) {
    localError = error;
  }

  try {
    const response = await fetch(CHANGELOG_DATA_URL, {
      headers: {
        "User-Agent": "betterUC-discord-bot",
        "Accept": "application/json"
      }
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const parsed = await response.json();
    if (!isChangelogData(parsed)) throw new Error("ungueltiges Changelog-Format");
    changelogCache = { loadedAt: Date.now(), value: parsed };
    return parsed;
  } catch (remoteError) {
    console.warn(
      "Could not load central betterUC changelog",
      `local: ${localError?.message || "unknown"}; remote: ${remoteError.message}`
    );
    return null;
  }
}

function findChangelogRelease(changelog, version) {
  if (!isChangelogData(changelog)) return null;
  const wanted = normalizeVersion(version);
  if (wanted) {
    return changelog.releases.find(entry => normalizeVersion(entry?.version) === wanted) || null;
  }
  return changelog.releases.find(entry => entry?.current)
    || changelog.releases[0]
    || null;
}

function changelogReleaseBody(entry) {
  const changes = Array.isArray(entry?.changes)
    ? entry.changes.map(change => clean(change)).filter(Boolean)
    : [];
  if (changes.length > 0) {
    return trimText(changes.map(change => `\u2022 ${change}`).join("\n"), 3900);
  }

  const pageLines = Array.isArray(entry?.pages)
    ? entry.pages.flatMap(page => {
        const title = clean(page?.title);
        const lines = Array.isArray(page?.lines)
          ? page.lines.map(line => clean(line)).filter(Boolean)
          : [];
        if (lines.length === 0) return [];
        return [
          title ? `**${title}**` : "",
          ...lines.map(line => `\u2022 ${line}`)
        ].filter(Boolean);
      })
    : [];
  return trimText(pageLines.join("\n"), 3900);
}

function releaseEmbed(release, changelogEntry = null) {
  const version = normalizeVersion(changelogEntry?.version || release?.tag_name || release?.name);
  const tag = version ? `v${version}` : "neues Release";
  const body = changelogReleaseBody(changelogEntry)
    || "Die vollst\u00e4ndigen Neuerungen findest du im betterUC-Changelog.";
  const embed = new EmbedBuilder()
    .setTitle(`betterUC ${tag} ist verf\u00fcgbar`)
    .setURL(PUBLIC_DOWNLOAD_URL)
    .setColor(0x38bdf8)
    .setDescription(body)
    .addFields(
      { name: "Download", value: PUBLIC_DOWNLOAD_URL, inline: true },
      { name: "Changelog", value: PUBLIC_CHANGELOG_URL, inline: true }
    );

  if (changelogEntry?.date) {
    embed.setFooter({ text: `Release ${changelogEntry.date}` });
  }
  if (release?.published_at) {
    embed.setTimestamp(new Date(release.published_at));
  }
  return embed;
}

function changelogEmbed(release, changelogEntry = null) {
  const version = normalizeVersion(changelogEntry?.version || release?.tag_name || release?.name);
  const body = changelogReleaseBody(changelogEntry)
    || "F\u00fcr diese Version sind noch keine zentralen Changelog-Eintr\u00e4ge verf\u00fcgbar.";
  const embed = new EmbedBuilder()
    .setTitle(`betterUC v${version || "unbekannt"} | Changelog`)
    .setURL(PUBLIC_CHANGELOG_URL)
    .setColor(0x22c55e)
    .setDescription(body)
    .addFields(
      { name: "Download", value: PUBLIC_DOWNLOAD_URL, inline: true },
      { name: "Alle Versionen", value: PUBLIC_CHANGELOG_URL, inline: true }
    );
  if (changelogEntry?.date) {
    embed.setFooter({ text: `Ver\u00f6ffentlicht am ${changelogEntry.date}` });
  }
  if (release?.published_at) {
    embed.setTimestamp(new Date(release.published_at));
  }
  return embed;
}

function releaseLinks(release) {
  const row = new ActionRowBuilder().addComponents(
    new ButtonBuilder()
      .setLabel("Herunterladen")
      .setStyle(ButtonStyle.Link)
      .setURL(PUBLIC_DOWNLOAD_URL),
    new ButtonBuilder()
      .setLabel("Alle \u00c4nderungen")
      .setStyle(ButtonStyle.Link)
      .setURL(PUBLIC_CHANGELOG_URL)
  );
  if (/^https?:\/\//i.test(clean(release?.html_url))) {
    row.addComponents(
      new ButtonBuilder()
        .setLabel("GitHub Release")
        .setStyle(ButtonStyle.Link)
        .setURL(release.html_url)
    );
  }
  return row;
}

async function fetchLatestRelease() {
  const response = await fetch(`https://api.github.com/repos/${RELEASE_REPO}/releases/latest`, {
    headers: {
      "User-Agent": "betterUC-discord-bot",
      "Accept": "application/vnd.github+json"
    }
  });
  if (!response.ok) {
    throw new Error(`GitHub Release Check fehlgeschlagen: HTTP ${response.status}`);
  }
  return response.json();
}

async function fetchReleaseByVersion(version) {
  const normalized = normalizeVersion(version);
  if (!normalized) return fetchLatestRelease();
  const tags = [`v${normalized}`, normalized];
  for (const tag of tags) {
    const response = await fetch(
      `https://api.github.com/repos/${RELEASE_REPO}/releases/tags/${encodeURIComponent(tag)}`,
      {
        headers: {
          "User-Agent": "betterUC-discord-bot",
          "Accept": "application/vnd.github+json"
        }
      }
    );
    if (response.ok) return response.json();
    if (response.status !== 404) {
      throw new Error(`GitHub Release Check fehlgeschlagen: HTTP ${response.status}`);
    }
  }
  return null;
}

async function findTextChannelByName(guild, name) {
  await guild.channels.fetch().catch(() => null);
  const lower = String(name || "").toLowerCase();
  return guild.channels.cache.find(channel =>
    channel.type === ChannelType.GuildText
    && channel.name.toLowerCase() === lower
  ) || null;
}

async function resolveTextChannel(guild, id, name) {
  if (!guild) return null;
  if (id) {
    const channel = await guild.channels.fetch(id).catch(() => null);
    if (channel && channel.type === ChannelType.GuildText) return channel;
  }
  return name ? findTextChannelByName(guild, name) : null;
}

async function resolveForumChannel(guild, id, name) {
  if (!guild) return null;
  if (id) {
    const channel = await guild.channels.fetch(id).catch(() => null);
    if (channel && channel.type === ChannelType.GuildForum) return channel;
  }
  await guild.channels.fetch().catch(() => null);
  const lower = String(name || "").toLowerCase();
  return guild.channels.cache.find(channel =>
    channel.type === ChannelType.GuildForum
    && channel.name.toLowerCase() === lower
  ) || null;
}

function bugReportEmbed(report) {
  const fields = [
    { name: "Gemeldet von", value: display(report.reporterName), inline: true },
    { name: "betterUC", value: display(report.modVersion), inline: true },
    { name: "Minecraft / Client", value: `${display(report.gameVersion)} / ${display(report.clientName)}`, inline: true }
  ];
  if (report.steps) {
    fields.push({ name: "Schritte zum Nachstellen", value: display(report.steps).slice(0, 1024), inline: false });
    if (report.steps.length > 1024) {
      fields.push({ name: "Schritte (Fortsetzung)", value: report.steps.slice(1024, 2048), inline: false });
    }
  }
  const embed = new EmbedBuilder()
    .setColor(0xf59e0b)
    .setTitle(clean(report.title).slice(0, 256))
    .setDescription(display(report.description).slice(0, 4096))
    .setFields(fields)
    .setFooter({ text: "Direkt aus dem betterUC Client gemeldet" })
    .setTimestamp();
  if (report.screenshotUrl) embed.setImage(report.screenshotUrl);
  return embed;
}

async function ensureUpdateNotificationRole(guild) {
  if (!guild || !UPDATE_NOTIFY_ROLE_NAME) return null;
  await guild.roles.fetch().catch(() => null);
  const existing = roleByName(guild, UPDATE_NOTIFY_ROLE_NAME);
  if (existing || !UPDATE_NOTIFY_ROLE_CREATE_MISSING) return existing;

  const botMember = guild.members.me || await guild.members.fetch(guild.client.user.id).catch(() => null);
  if (!botMember?.permissions.has(PermissionFlagsBits.ManageRoles)) {
    console.warn("Discord update role cannot be created: Manage Roles permission is missing");
    return null;
  }
  return guild.roles.create({
    name: UPDATE_NOTIFY_ROLE_NAME,
    color: 0x38bdf8,
    hoist: false,
    mentionable: true,
    reason: "betterUC opt-in update notifications"
  });
}

function diagnosticAgeLabel(value) {
  if (!value) return "nie";
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return display(value);
  const minutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
  if (minutes < 2) return "gerade eben";
  if (minutes < 60) return `vor ${minutes} Minuten`;
  const hours = Math.floor(minutes / 60);
  if (hours < 48) return `vor ${hours} Stunden`;
  return `vor ${Math.floor(hours / 24)} Tagen`;
}

function diagnosticState(ok, good, bad) {
  return `${ok ? "\u2705" : "\u274c"} ${ok ? good : bad}`;
}

function diagnosticEmbed(account, onlinePlayer) {
  const cloud = account?.cloudSettings || null;
  const cloudExists = Boolean(cloud && (cloud.exists ?? cloud.revision !== null));
  const latestVersion = normalizeVersion(botState.latestReleaseTag);
  const currentVersion = normalizeVersion(onlinePlayer?.version || account?.lastVersion);
  const versionCurrent = Boolean(currentVersion) && (!latestVersion || currentVersion === latestVersion);
  const statsAt = account?.lastStatsAt || account?.stats?.updatedAt;
  const lines = [
    diagnosticState(account?.status === "active", "Access aktiv", `Access ${display(account?.status, "unbekannt")}`),
    diagnosticState(Boolean(onlinePlayer), "Relay verbunden", `Relay offline, letzter Kontakt ${diagnosticAgeLabel(account?.lastSeenAt)}`),
    diagnosticState(Boolean(account?.discordId), "Discord verkn\u00fcpft", "Discord nicht verkn\u00fcpft"),
    diagnosticState(Boolean(account?.hasWebPassword), "Webprofil eingerichtet", "Kein Webpasswort eingerichtet"),
    diagnosticState(cloudExists, `Cloud-Sync Revision ${cloud?.revision ?? "-"}`, "Kein Cloud-Profil vorhanden"),
    diagnosticState(Boolean(statsAt), `Stats aktualisiert ${diagnosticAgeLabel(statsAt)}`, "Noch keine Stats empfangen"),
    diagnosticState(versionCurrent, `Mod-Version ${currentVersion || "nicht erkannt"}`, `Veraltet: ${currentVersion || "unbekannt"}, aktuell ${latestVersion}`)
  ];
  if (Number(cloud?.conflicts24h || 0) > 0 || Number(cloud?.errors24h || 0) > 0) {
    lines.push(`\u26a0\ufe0f Cloud (24h): ${Number(cloud?.conflicts24h || 0)} Konflikte, ${Number(cloud?.errors24h || 0)} Fehler`);
  }

  return new EmbedBuilder()
    .setTitle(`betterUC Diagnose | ${display(account?.minecraftName || onlinePlayer?.name)}`)
    .setColor(lines.some(line => line.startsWith("\u274c")) ? 0xfacc15 : 0x22c55e)
    .setDescription(lines.join("\n"))
    .addFields(
      { name: "Rolle", value: roleLabel(account?.role || onlinePlayer?.role), inline: true },
      { name: "Fraktion", value: display(account?.stats?.factionDisplay || account?.faction || onlinePlayer?.faction), inline: true },
      { name: "Minecraft", value: display(onlinePlayer?.gameVersion || account?.lastGameVersion, "nicht erkannt"), inline: true },
      { name: "Server", value: display(account?.lastServer, "nicht erkannt"), inline: true },
      { name: "Channel", value: display(account?.lastChannel, "nicht erkannt"), inline: true },
      { name: "Letzter Weblogin", value: diagnosticAgeLabel(account?.lastPanelLoginAt), inline: true }
    )
    .setFooter({ text: "Es werden keine Access-Codes oder Passw\u00f6rter angezeigt." })
    .setTimestamp(new Date());
}

function announcementEmbed(event) {
  const origin = event.origin === "discord" ? "Discord" : "Minecraft";
  return new EmbedBuilder()
    .setTitle("Wichtige betterUC-Ank\u00fcndigung")
    .setColor(0xff4d5a)
    .setDescription(trimText(event.message, 300))
    .addFields(
      { name: "Gesendet von", value: display(event.sender, "betterUC Team"), inline: true },
      { name: "Herkunft", value: origin, inline: true }
    )
    .setTimestamp(event.createdAt ? new Date(event.createdAt) : new Date());
}

async function checkGithubRelease(client, options = {}) {
  if (!GUILD_ID || !UPDATE_CHANNEL_NAME) {
    return { status: "disabled" };
  }

  const release = await fetchLatestRelease();
  const releaseKey = String(release.id || release.tag_name || release.html_url || "").trim();
  if (!releaseKey) return { status: "missing_release_key" };

  const previousKey = botState.latestReleaseKey || "";
  const firstRun = !previousKey;
  const changed = previousKey !== releaseKey;
  const legacyPendingPost = Boolean(botState.latestReleasePendingPost && previousKey === releaseKey);
  const pendingUpdatePost = Boolean(botState.latestReleasePendingUpdatePost && previousKey === releaseKey);
  const pendingChangelogPost = Boolean(botState.latestReleasePendingChangelogPost && previousKey === releaseKey);
  const newReleasePost = changed && (!firstRun || ANNOUNCE_EXISTING_RELEASE || options.announceExisting);
  const shouldPostUpdate = options.forcePost || legacyPendingPost || pendingUpdatePost || newReleasePost;
  const shouldPostChangelog = options.forcePost || pendingChangelogPost || newReleasePost;

  botState.latestReleaseKey = releaseKey;
  botState.latestReleaseTag = release.tag_name || "";
  botState.latestReleaseCheckedAt = new Date().toISOString();

  if (!shouldPostUpdate && !shouldPostChangelog) {
    await writeBotState(botState);
    return { status: firstRun ? "initialized" : "unchanged", release };
  }

  const changelog = await readCentralChangelog({
    force: changed
      || options.forcePost
      || legacyPendingPost
      || pendingUpdatePost
      || pendingChangelogPost
  });
  const changelogEntry = findChangelogRelease(changelog, release.tag_name || release.name);
  if (!changelogEntry) {
    if (shouldPostUpdate) botState.latestReleasePendingUpdatePost = true;
    if (shouldPostChangelog) botState.latestReleasePendingChangelogPost = true;
    await writeBotState(botState);
    console.warn(
      `Release ${release.tag_name || release.name || releaseKey} wartet auf den zentralen Changelog.`
    );
    return { status: "waiting_for_changelog", release };
  }

  const guild = await client.guilds.fetch(GUILD_ID);
  const failures = [];
  let updatePosted = false;
  let changelogPosted = false;

  if (shouldPostUpdate) {
    const updateChannel = await findTextChannelByName(guild, UPDATE_CHANNEL_NAME);
    if (!updateChannel) {
      botState.latestReleasePendingUpdatePost = true;
      failures.push(`Update-Channel '${UPDATE_CHANNEL_NAME}' nicht gefunden`);
    } else {
      try {
        const updateRole = await ensureUpdateNotificationRole(guild).catch(error => {
          console.warn("Discord update notification role unavailable", error.message);
          return null;
        });
        await updateChannel.send({
          content: updateRole ? `<@&${updateRole.id}>` : undefined,
          embeds: [releaseEmbed(release, changelogEntry)],
          components: [releaseLinks(release)],
          allowedMentions: updateRole ? { roles: [updateRole.id] } : { parse: [] }
        });
        updatePosted = true;
        botState.latestReleasePendingUpdatePost = false;
        botState.latestReleasePostedAt = new Date().toISOString();
      } catch (error) {
        botState.latestReleasePendingUpdatePost = true;
        failures.push(`Update-Post fehlgeschlagen: ${error.message}`);
      }
    }
  }

  if (shouldPostChangelog) {
    const changelogChannel = await resolveTextChannel(guild, CHANGELOG_CHANNEL_ID, CHANGELOG_CHANNEL_NAME);
    if (!changelogChannel) {
      botState.latestReleasePendingChangelogPost = true;
      failures.push(`Changelog-Channel '${CHANGELOG_CHANNEL_ID || CHANGELOG_CHANNEL_NAME}' nicht gefunden`);
    } else {
      const updateChannel = await findTextChannelByName(guild, UPDATE_CHANNEL_NAME);
      try {
        if (!updateChannel || changelogChannel.id !== updateChannel.id || !updatePosted) {
          await changelogChannel.send({
            embeds: [changelogEmbed(release, changelogEntry)],
            components: [releaseLinks(release)],
            allowedMentions: { parse: [] }
          });
        }
        changelogPosted = true;
        botState.latestReleasePendingChangelogPost = false;
        botState.latestReleaseChangelogPostedAt = new Date().toISOString();
      } catch (error) {
        botState.latestReleasePendingChangelogPost = true;
        failures.push(`Changelog-Post fehlgeschlagen: ${error.message}`);
      }
    }
  }

  delete botState.latestReleasePendingPost;
  await writeBotState(botState);
  if (failures.length) {
    throw new Error(`Release teilweise ver\u00f6ffentlicht: ${failures.join("; ")}.`);
  }
  return { status: "posted", release, updatePosted, changelogPosted };
}

function ticketLabel(topic) {
  if (topic === "bug") return "Bug melden";
  if (topic === "access") return "Access-Code Problem";
  if (topic === "account") return "Account Problem";
  return "Support";
}

function ticketPrefix(topic) {
  if (topic === "bug") return "bug";
  if (topic === "access") return "access";
  if (topic === "account") return "account";
  return "support";
}

function slug(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[^a-z0-9_-]/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 32) || "user";
}

async function findOrCreateTicketCategory(guild, teamRoles) {
  const existing = guild.channels.cache.find(channel =>
    channel.type === ChannelType.GuildCategory
    && channel.name.toLowerCase() === TICKET_CATEGORY_NAME.toLowerCase()
  );
  if (existing) return existing;

  return guild.channels.create({
    name: TICKET_CATEGORY_NAME,
    type: ChannelType.GuildCategory,
    permissionOverwrites: ticketCategoryOverwrites(guild, teamRoles)
  });
}

function resolveTeamRoles(guild) {
  const lowerNames = TEAM_ROLE_NAMES.map(name => name.toLowerCase());
  return guild.roles.cache.filter(role => lowerNames.includes(role.name.toLowerCase()));
}

function betterUcRoleName(role) {
  if (role === "admin") return ADMIN_ROLE_NAME;
  if (role === "helper") return HELPER_ROLE_NAME;
  if (role === "partner") return PARTNER_ROLE_NAME;
  if (role === "vip") return VIP_ROLE_NAME;
  return USER_ROLE_NAME;
}

function managedBetterUcRoleNames() {
  return [MOD_USER_ROLE_NAME, USER_ROLE_NAME, VIP_ROLE_NAME, PARTNER_ROLE_NAME, HELPER_ROLE_NAME, ADMIN_ROLE_NAME]
    .map(clean)
    .filter(Boolean);
}

function desiredBetterUcRoleNames(account) {
  if (!account || account.status === "revoked") return [];
  return [MOD_USER_ROLE_NAME, betterUcRoleName(account.role)]
    .map(clean)
    .filter(Boolean);
}

function roleByName(guild, roleName) {
  const lower = clean(roleName).toLowerCase();
  if (!lower) return null;
  return guild.roles.cache.find(entry => entry.name.toLowerCase() === lower) || null;
}

function managedRoleSpecs() {
  return [
    { name: MOD_USER_ROLE_NAME, color: 0xffffff },
    { name: USER_ROLE_NAME, color: 0xffffff },
    { name: VIP_ROLE_NAME, color: roleColor("vip") },
    { name: PARTNER_ROLE_NAME, color: roleColor("partner") },
    { name: HELPER_ROLE_NAME, color: roleColor("helper") },
    { name: ADMIN_ROLE_NAME, color: roleColor("admin") }
  ].filter(spec => clean(spec.name));
}

async function ensureManagedBetterUcRoles(guild) {
  await guild.roles.fetch().catch(() => null);
  if (!ROLE_SYNC_CREATE_MISSING) return 0;
  const botMember = guild.members.me || await guild.members.fetch(guild.client.user.id).catch(() => null);
  if (!botMember?.permissions.has(PermissionFlagsBits.ManageRoles)) {
    console.warn("Discord role sync cannot create roles: Manage Roles permission is missing");
    return 0;
  }
  let created = 0;
  for (const spec of managedRoleSpecs()) {
    if (roleByName(guild, spec.name)) continue;
    await guild.roles.create({
      name: spec.name,
      color: spec.color,
      hoist: false,
      mentionable: false,
      reason: "betterUC role synchronization"
    });
    created++;
  }
  return created;
}

async function syncBetterUcRoles(member, account) {
  if (!member || !member.guild) return;
  await member.guild.roles.fetch().catch(() => null);
  const desired = new Set(desiredBetterUcRoleNames(account).map(name => name.toLowerCase()));

  for (const roleName of managedBetterUcRoleNames()) {
    const role = roleByName(member.guild, roleName);
    if (!role) continue;
    const shouldHave = desired.has(roleName.toLowerCase());
    const hasRole = member.roles.cache.has(role.id);
    if (shouldHave && !hasRole) {
      await member.roles.add(role).catch(error => console.warn("Could not add betterUC Discord role", roleName, account?.minecraftName || "", error.message));
    } else if (!shouldHave && hasRole) {
      await member.roles.remove(role).catch(error => console.warn("Could not remove betterUC Discord role", roleName, account?.minecraftName || "", error.message));
    }
  }
}

async function removeBetterUcRoles(member) {
  if (!member || !member.guild) return;
  await member.guild.roles.fetch().catch(() => null);
  for (const roleName of managedBetterUcRoleNames()) {
    const role = roleByName(member.guild, roleName);
    if (!role || !member.roles.cache.has(role.id)) continue;
    await member.roles.remove(role).catch(error => console.warn("Could not remove betterUC Discord role", roleName, error.message));
  }
}

function ticketCategoryOverwrites(guild, teamRoles) {
  const overwrites = [
    {
      id: guild.roles.everyone.id,
      deny: [PermissionFlagsBits.ViewChannel]
    }
  ];
  for (const role of teamRoles.values()) {
    overwrites.push({
      id: role.id,
      allow: [
        PermissionFlagsBits.ViewChannel,
        PermissionFlagsBits.SendMessages,
        PermissionFlagsBits.ReadMessageHistory,
        PermissionFlagsBits.ManageMessages
      ]
    });
  }
  return overwrites;
}

function ticketChannelOverwrites(guild, openerId, teamRoles) {
  return [
    ...ticketCategoryOverwrites(guild, teamRoles),
    {
      id: openerId,
      allow: [
        PermissionFlagsBits.ViewChannel,
        PermissionFlagsBits.SendMessages,
        PermissionFlagsBits.ReadMessageHistory,
        PermissionFlagsBits.AttachFiles
      ]
    }
  ];
}

function isTicketTeamMember(interaction) {
  if (interaction.memberPermissions?.has(PermissionFlagsBits.ManageChannels)) return true;
  const memberRoles = interaction.member?.roles?.cache;
  if (!memberRoles) return false;
  const allowed = new Set(TEAM_ROLE_NAMES.map(name => name.toLowerCase()));
  return memberRoles.some(role => allowed.has(role.name.toLowerCase()));
}

function ticketOpenerId(channel) {
  const match = String(channel?.topic || "").match(/discord:(\d+)/);
  return match ? match[1] : "";
}

function ticketAccountText(account) {
  if (!account) {
    return "Kein betterUC-Account mit diesem Discord-Nutzer verkn\u00fcpft.";
  }
  return [
    `Minecraft: **${display(account.minecraftName)}**`,
    `Rolle: **${roleLabel(account.role)}**`,
    `Fraktion: **${display(account.stats?.factionDisplay || account.faction)}**`,
    `Mod-Version: **${display(account.lastVersion)}**`,
    `Minecraft: **${display(account.lastGameVersion)}**`,
    `Letzter Kontakt: **${display(account.lastSeenAt)}**`
  ].join("\n");
}

function ticketControls() {
  return new ActionRowBuilder().addComponents(
    new ButtonBuilder()
      .setCustomId("ticket:claim")
      .setLabel("\u00dcbernehmen")
      .setStyle(ButtonStyle.Primary),
    new ButtonBuilder()
      .setCustomId("ticket:close")
      .setLabel("Ticket schlie\u00dfen")
      .setStyle(ButtonStyle.Danger)
  );
}

async function fetchTicketMessages(channel, limit = 1000) {
  const messages = [];
  let before;
  while (messages.length < limit) {
    const batch = await channel.messages.fetch({ limit: Math.min(100, limit - messages.length), before });
    if (batch.size === 0) break;
    messages.push(...batch.values());
    before = batch.last().id;
    if (batch.size < 100) break;
  }
  return messages.sort((a, b) => a.createdTimestamp - b.createdTimestamp);
}

async function createTicketTranscript(channel) {
  const messages = await fetchTicketMessages(channel);
  const lines = [
    `betterUC Ticket-Transkript: #${channel.name}`,
    `Channel-ID: ${channel.id}`,
    `Erstellt: ${new Date().toISOString()}`,
    ""
  ];
  for (const message of messages) {
    const timestamp = new Date(message.createdTimestamp).toISOString();
    const author = message.author?.tag || message.author?.username || "Unbekannt";
    const content = clean(message.cleanContent || message.content).replace(/\r?\n/g, " ") || "[Embed/Anhang]";
    const attachments = [...message.attachments.values()].map(entry => entry.url).join(" ");
    lines.push(`[${timestamp}] ${author}: ${content}${attachments ? ` | ${attachments}` : ""}`);
  }
  await fsp.mkdir(TICKET_TRANSCRIPT_DIR, { recursive: true });
  const fileName = `${slug(channel.name)}-${channel.id}.txt`;
  const filePath = path.join(TICKET_TRANSCRIPT_DIR, fileName);
  await fsp.writeFile(filePath, lines.join("\n"), "utf8");
  return { fileName, filePath, messageCount: messages.length };
}

async function deferEphemeral(interaction) {
  if (!interaction.deferred && !interaction.replied) {
    await interaction.deferReply({ ephemeral: true });
  }
}

async function respondEphemeral(interaction, content) {
  if (interaction.deferred) {
    await interaction.editReply({ content });
  } else if (interaction.replied) {
    await interaction.followUp({ content, ephemeral: true });
  } else {
    await interaction.reply({ content, ephemeral: true });
  }
}

function ticketErrorMessage(error) {
  const code = error?.code ? ` (${error.code})` : "";
  const message = String(error?.message || "").trim();
  if (message.includes("Missing Permissions") || error?.code === 50013) {
    return "Ticket konnte nicht erstellt werden: Dem Bot fehlen Discord-Rechte. Gib dem Bot bitte 'Kanäle verwalten', 'Nachrichten senden' und Zugriff auf die Ticket-Kategorie.";
  }
  if (message.includes("Maximum number of channels") || error?.code === 30013) {
    return "Ticket konnte nicht erstellt werden: Der Discord-Server hat das Channel-Limit erreicht.";
  }
  if (message) {
    return `Ticket konnte nicht erstellt werden: ${message}${code}`;
  }
  return "Ticket konnte nicht erstellt werden. Bitte pruefe die Bot-Rechte und versuche es erneut.";
}

async function ensureTicketBotPermissions(guild) {
  const botMember = guild.members.me || await guild.members.fetch(guild.client.user.id).catch(() => null);
  if (!botMember) {
    throw new Error("Bot-Mitglied konnte auf diesem Discord-Server nicht geladen werden.");
  }
  const missing = [];
  if (!botMember.permissions.has(PermissionFlagsBits.ManageChannels)) missing.push("Kanäle verwalten");
  if (!botMember.permissions.has(PermissionFlagsBits.SendMessages)) missing.push("Nachrichten senden");
  if (!botMember.permissions.has(PermissionFlagsBits.ViewChannel)) missing.push("Kanäle ansehen");
  if (missing.length > 0) {
    throw new Error(`Dem Bot fehlen Rechte: ${missing.join(", ")}.`);
  }
}

async function openTicket(interaction, topic, context) {
  await deferEphemeral(interaction);

  const guild = interaction.guild;
  if (!guild) {
    await respondEphemeral(interaction, "Tickets koennen nur auf dem Server erstellt werden.");
    return;
  }

  try {
    await ensureTicketBotPermissions(guild);
    await guild.roles.fetch().catch(() => null);
    await guild.channels.fetch().catch(() => null);
    const teamRoles = resolveTeamRoles(guild);
    const category = await findOrCreateTicketCategory(guild, teamRoles);
    const openTicket = guild.channels.cache.find(channel =>
      channel.type === ChannelType.GuildText
      && channel.name.startsWith(`ticket-${ticketPrefix(topic)}-`)
      && channel.topic
      && channel.topic.includes(`discord:${interaction.user.id}`)
    );
    if (openTicket) {
      await respondEphemeral(interaction, `Du hast bereits ein offenes Ticket: ${openTicket}`);
      return;
    }

    const channel = await guild.channels.create({
      name: `ticket-${ticketPrefix(topic)}-${slug(interaction.user.username)}`,
      type: ChannelType.GuildText,
      parent: category.id,
      topic: `betterUC Ticket | ${ticketLabel(topic)} | discord:${interaction.user.id}`,
      permissionOverwrites: ticketChannelOverwrites(guild, interaction.user.id, teamRoles),
      reason: `betterUC ticket opened by ${interaction.user.tag}`
    });

    const account = context.findAccountByDiscordId(interaction.user.id);
    await context.createDiscordTicket({
      guildId: guild.id,
      channelId: channel.id,
      openerDiscordId: interaction.user.id,
      accountId: account?.id || null,
      topic
    });

    const embed = new EmbedBuilder()
      .setTitle(`Ticket: ${ticketLabel(topic)}`)
      .setColor(0x38bdf8)
      .setDescription([
        `${interaction.user}, beschreibe dein Anliegen bitte moeglichst genau.`,
        "Ein Teammitglied meldet sich dann hier im Ticket.",
        "",
        ticketAccountText(account)
      ].join("\n"))
      .setFooter({ text: "betterUC Support | Transkript wird beim Schlie\u00dfen gespeichert" });
    await channel.send({ content: `${interaction.user}`, embeds: [embed], components: [ticketControls()] });
    await respondEphemeral(interaction, `Ticket erstellt: ${channel}`);
  } catch (error) {
    console.error("Discord ticket create error", error);
    await respondEphemeral(interaction, ticketErrorMessage(error));
  }
}

async function claimTicket(interaction, context) {
  const channel = interaction.channel;
  if (!channel || channel.type !== ChannelType.GuildText || !channel.name.startsWith("ticket-")) {
    await interaction.reply({ content: "Dieser Button funktioniert nur in einem offenen Ticket.", ephemeral: true });
    return;
  }
  if (!isTicketTeamMember(interaction)) {
    await interaction.reply({ content: "Nur das Support-Team kann Tickets \u00fcbernehmen.", ephemeral: true });
    return;
  }
  const ticket = await context.claimDiscordTicket(channel.id, interaction.user.id);
  if (!ticket) {
    await interaction.reply({ content: "Das Ticket ist bereits geschlossen oder nicht registriert.", ephemeral: true });
    return;
  }
  await interaction.reply({ content: `${interaction.user} hat dieses Ticket \u00fcbernommen.` });
}

async function showCloseTicketModal(interaction) {
  const channel = interaction.channel;
  if (!channel || channel.type !== ChannelType.GuildText || !channel.name.startsWith("ticket-")) {
    await interaction.reply({ content: "Dieser Button funktioniert nur in einem offenen Ticket.", ephemeral: true });
    return;
  }

  const modal = new ModalBuilder()
    .setCustomId("ticket:close-modal")
    .setTitle("Ticket schlie\u00dfen");
  const reason = new TextInputBuilder()
    .setCustomId("reason")
    .setLabel("Abschlussgrund")
    .setStyle(TextInputStyle.Paragraph)
    .setMinLength(3)
    .setMaxLength(500)
    .setRequired(true);
  modal.addComponents(new ActionRowBuilder().addComponents(reason));
  await interaction.showModal(modal);
}

async function closeTicket(interaction, context) {
  const channel = interaction.channel;
  if (!channel || channel.type !== ChannelType.GuildText || !channel.name.startsWith("ticket-")) {
    await interaction.reply({ content: "Dieses Ticket ist nicht mehr offen.", ephemeral: true });
    return;
  }
  await deferEphemeral(interaction);
  const closeReason = clean(interaction.fields.getTextInputValue("reason"));
  const transcript = await createTicketTranscript(channel);
  const openerId = ticketOpenerId(channel);

  const closedName = `closed-${channel.name.replace(/^ticket-/, "")}`.slice(0, 100);
  await channel.setName(closedName).catch(() => null);
  if (openerId) {
    await channel.permissionOverwrites.edit(openerId, { SendMessages: false }).catch(() => null);
  }
  await context.closeDiscordTicket(channel.id, closeReason, transcript.filePath);
  const ticketLogChannel = context.getTicketLogChannel();
  if (ticketLogChannel) {
    const logEmbed = new EmbedBuilder()
      .setTitle(`Ticket geschlossen: ${channel.name}`)
      .setColor(0xff4d5a)
      .addFields(
        { name: "Grund", value: closeReason, inline: false },
        { name: "Geschlossen von", value: interaction.user.tag, inline: true },
        { name: "Nachrichten", value: String(transcript.messageCount), inline: true }
      )
      .setTimestamp();
    await ticketLogChannel.send({
      embeds: [logEmbed],
      files: [new AttachmentBuilder(transcript.filePath, { name: transcript.fileName })],
      allowedMentions: { parse: [] }
    }).catch(error => console.warn("Could not upload ticket transcript", error.message));
  }
  const deleteRow = new ActionRowBuilder().addComponents(
    new ButtonBuilder()
      .setCustomId("ticket:delete")
      .setLabel("Ticket loeschen")
      .setStyle(ButtonStyle.Danger)
  );
  await interaction.editReply({ content: "Ticket geschlossen und Transkript gespeichert." });
  await channel.send({
    content: `Ticket geschlossen. **Grund:** ${closeReason}`,
    components: [deleteRow],
    allowedMentions: { parse: [] }
  });
}

async function deleteTicket(interaction) {
  const channel = interaction.channel;
  if (!channel || channel.type !== ChannelType.GuildText || !channel.name.startsWith("closed-")) {
    await interaction.reply({ content: "Dieses Ticket muss erst geschlossen werden.", ephemeral: true });
    return;
  }
  if (!interaction.memberPermissions?.has(PermissionFlagsBits.ManageChannels)) {
    await interaction.reply({ content: "Nur Teammitglieder mit Channel-Rechten koennen Tickets loeschen.", ephemeral: true });
    return;
  }
  await interaction.reply({ content: "Ticket wird geloescht...", ephemeral: true });
  setTimeout(() => {
    channel.delete("betterUC ticket closed").catch(error => console.warn("Could not delete ticket", error.message));
  }, 1500);
}

function ticketPanelPayload() {
  const embed = new EmbedBuilder()
    .setTitle("betterUC Support")
    .setColor(0x38bdf8)
    .setDescription("Waehle ein Thema aus, dann erstellt der Bot ein privates Ticket fuer dich.");

  const row = new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId("ticket:open:support").setLabel("Support").setStyle(ButtonStyle.Primary),
    new ButtonBuilder().setCustomId("ticket:open:bug").setLabel("Bug melden").setStyle(ButtonStyle.Secondary),
    new ButtonBuilder().setCustomId("ticket:open:access").setLabel("Access-Code").setStyle(ButtonStyle.Secondary),
    new ButtonBuilder().setCustomId("ticket:open:account").setLabel("Account").setStyle(ButtonStyle.Secondary)
  );

  return { embeds: [embed], components: [row] };
}

const SUGGESTION_STATUS = Object.freeze({
  open: { label: "Offen", color: 0x38bdf8 },
  planned: { label: "Geplant", color: 0xfacc15 },
  in_progress: { label: "In Arbeit", color: 0xf97316 },
  implemented: { label: "Umgesetzt", color: 0x22c55e },
  rejected: { label: "Abgelehnt", color: 0xff4d5a }
});

function suggestionEmbed(suggestion, authorName = "betterUC Nutzer") {
  const state = SUGGESTION_STATUS[suggestion.status] || SUGGESTION_STATUS.open;
  const fields = [
    { name: "Status", value: state.label, inline: true },
    { name: "Stimmen", value: `\u25b2 ${suggestion.upvotes || 0} | \u25bc ${suggestion.downvotes || 0}`, inline: true },
    { name: "Eingereicht von", value: display(authorName), inline: true }
  ];
  if (suggestion.statusNote) fields.push({ name: "Team-Notiz", value: suggestion.statusNote, inline: false });
  return new EmbedBuilder()
    .setTitle(`#${suggestion.id} | ${suggestion.title}`)
    .setColor(state.color)
    .setDescription(suggestion.description)
    .addFields(fields)
    .setTimestamp(suggestion.createdAt ? new Date(suggestion.createdAt) : new Date());
}

function suggestionVoteRow(suggestion) {
  const votingDisabled = suggestion.status === "implemented" || suggestion.status === "rejected";
  return new ActionRowBuilder().addComponents(
    new ButtonBuilder()
      .setCustomId(`suggestion:vote:${suggestion.id}:up`)
      .setLabel(`Daf\u00fcr (${suggestion.upvotes || 0})`)
      .setStyle(ButtonStyle.Success)
      .setDisabled(votingDisabled),
    new ButtonBuilder()
      .setCustomId(`suggestion:vote:${suggestion.id}:down`)
      .setLabel(`Dagegen (${suggestion.downvotes || 0})`)
      .setStyle(ButtonStyle.Danger)
      .setDisabled(votingDisabled)
  );
}

function suggestionGuidePayload() {
  const embed = new EmbedBuilder()
    .setTitle("betterUC Vorschläge")
    .setColor(0x38bdf8)
    .setDescription([
      "Du hast eine Idee für betterUC oder möchtest eine bestehende Funktion verbessern?",
      "",
      "Nutze **`/vorschlag erstellen`**, um einen neuen Vorschlag einzureichen.",
      "",
      "Der Bot führt dich anschließend durch die Erstellung. Beschreibe deine Idee verständlich und erkläre kurz, welchen Vorteil sie für Nutzer bietet.",
      "",
      "Bitte erstelle pro Idee einen eigenen Vorschlag."
    ].join("\n"))
    .setFooter({ text: "betterUC Community • Vorschlagssystem" });

  return {
    embeds: [embed],
    components: [],
    allowedMentions: { parse: [] }
  };
}

function suggestionAuthor(context, suggestion) {
  const discordId = clean(suggestion?.authorDiscordId);
  if (discordId) return `<@${discordId}>`;
  const account = context.getAccounts().find(entry => entry.id === suggestion.accountId);
  return account?.minecraftName || "Unbekannt";
}

async function updateSuggestionMessage(client, context, suggestion) {
  if (!suggestion?.channelId || !suggestion?.messageId) return;
  const channel = await client.channels.fetch(suggestion.channelId).catch(() => null);
  if (!channel?.isTextBased()) return;
  const message = await channel.messages.fetch(suggestion.messageId).catch(() => null);
  if (!message) return;
  await message.edit({
    embeds: [suggestionEmbed(suggestion, suggestionAuthor(context, suggestion))],
    components: [suggestionVoteRow(suggestion)]
  });
}

async function handleSuggestionCommand(interaction, context) {
  const subcommand = interaction.options.getSubcommand();
  if (subcommand === "erstellen") {
    const account = context.findAccountByDiscordId(interaction.user.id);
    if (!account) {
      await interaction.reply({ content: "Verkn\u00fcpfe zuerst deinen betterUC-Account mit `/link`.", ephemeral: true });
      return;
    }
    const channel = context.getSuggestionChannel();
    if (!channel) {
      await interaction.reply({ content: "Der Vorschlagskanal ist noch nicht eingerichtet.", ephemeral: true });
      return;
    }
    await deferEphemeral(interaction);
    const suggestion = await context.createDiscordSuggestion({
      guildId: interaction.guildId,
      authorDiscordId: interaction.user.id,
      accountId: account.id,
      title: clean(interaction.options.getString("titel", true)),
      description: clean(interaction.options.getString("beschreibung", true))
    });
    const message = await channel.send({
      embeds: [suggestionEmbed(suggestion, suggestionAuthor(context, suggestion))],
      components: [suggestionVoteRow(suggestion)],
      allowedMentions: { parse: [] }
    });
    const attached = await context.attachDiscordSuggestionMessage(suggestion.id, channel.id, message.id);
    context.refreshSuggestionGuide?.();
    await interaction.editReply({ content: `Vorschlag #${attached.id} wurde in ${channel} ver\u00f6ffentlicht.` });
    return;
  }

  if (!hasManageGuild(interaction)) {
    await interaction.reply({ content: "Nur das Team kann den Vorschlagsstatus \u00e4ndern.", ephemeral: true });
    return;
  }
  const id = interaction.options.getInteger("id", true);
  const suggestion = await context.updateDiscordSuggestionStatus(
    id,
    interaction.options.getString("status", true),
    clean(interaction.options.getString("notiz") || "")
  );
  if (!suggestion) {
    await interaction.reply({ content: "Vorschlag nicht gefunden.", ephemeral: true });
    return;
  }
  await updateSuggestionMessage(interaction.client, context, suggestion);
  await interaction.reply({
    content: `Vorschlag #${id} steht jetzt auf **${SUGGESTION_STATUS[suggestion.status]?.label || suggestion.status}**.`,
    ephemeral: true
  });
}

async function voteSuggestion(interaction, context) {
  const parts = interaction.customId.split(":");
  const id = Number(parts[2]);
  const vote = parts[3] === "down" ? -1 : 1;
  const account = context.findAccountByDiscordId(interaction.user.id);
  if (!account) {
    await interaction.reply({ content: "Zum Abstimmen musst du deinen betterUC-Account mit `/link` verkn\u00fcpfen.", ephemeral: true });
    return;
  }
  const existing = await context.getDiscordSuggestion(id);
  if (!existing) {
    await interaction.reply({ content: "Vorschlag nicht gefunden.", ephemeral: true });
    return;
  }
  if (existing.status === "implemented" || existing.status === "rejected") {
    await interaction.reply({ content: "Die Abstimmung f\u00fcr diesen Vorschlag ist beendet.", ephemeral: true });
    return;
  }
  const suggestion = await context.voteDiscordSuggestion(id, interaction.user.id, vote);
  await interaction.update({
    embeds: [suggestionEmbed(suggestion, suggestionAuthor(context, suggestion))],
    components: [suggestionVoteRow(suggestion)]
  });
}

function bytesLabel(value) {
  const bytes = Number(value || 0);
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

async function publicHealthStatus() {
  try {
    const response = await fetch(`${PUBLIC_BASE_URL.replace(/\/+$/, "")}/health`, {
      headers: { "User-Agent": "betterUC-monitor" },
      signal: AbortSignal.timeout(5000)
    });
    return { ok: response.ok, status: response.status, error: response.ok ? "" : `HTTP ${response.status}` };
  } catch (error) {
    return { ok: false, status: 0, error: error.message || "nicht erreichbar" };
  }
}

async function systemReport(context) {
  const [snapshot, publicHealth] = await Promise.all([
    context.getSystemSnapshot(),
    publicHealthStatus()
  ]);
  const issues = [];
  const database = snapshot.database;
  if (!database?.connected) issues.push(snapshot.databaseError || "PostgreSQL nicht erreichbar");
  if (database?.pendingMigrations?.length) issues.push(`${database.pendingMigrations.length} ausstehende Migration(en)`);
  if ((database?.counts?.cloudErrors24h || 0) >= CLOUD_ERROR_ALERT_COUNT) {
    issues.push(`${database.counts.cloudErrors24h} Cloud-Fehler in 24h`);
  }
  if (!publicHealth.ok) issues.push(`Website-/Healthcheck: ${publicHealth.error}`);
  const postgresBackup = snapshot.backups.find(entry => entry.type === "postgres");
  const backupAgeHours = postgresBackup
    ? (Date.now() - new Date(postgresBackup.createdAt).getTime()) / 3600000
    : Number.POSITIVE_INFINITY;
  if (snapshot.persistenceMode === "postgres" && backupAgeHours > BACKUP_MAX_AGE_HOURS) {
    issues.push(postgresBackup ? `PostgreSQL-Backup ist ${Math.floor(backupAgeHours)}h alt` : "Kein PostgreSQL-Backup gefunden");
  }
  return { snapshot, publicHealth, postgresBackup, backupAgeHours, issues, healthy: issues.length === 0 };
}

function systemStatusEmbed(report) {
  const { snapshot, publicHealth, postgresBackup, issues, healthy } = report;
  return new EmbedBuilder()
    .setTitle(healthy ? "betterUC Systemstatus: Alles bereit" : "betterUC Systemstatus: Handlungsbedarf")
    .setColor(healthy ? 0x22c55e : 0xff4d5a)
    .addFields(
      { name: "Relay", value: `Online | ${snapshot.onlinePlayers.length} Mod-User`, inline: true },
      { name: "Website", value: publicHealth.ok ? `Online | HTTP ${publicHealth.status}` : `Fehler | ${publicHealth.error}`, inline: true },
      { name: "PostgreSQL", value: snapshot.database?.connected ? `Online | ${bytesLabel(snapshot.database.sizeBytes)}` : `Fehler | ${display(snapshot.databaseError)}`, inline: true },
      { name: "Migrationen", value: snapshot.database?.pendingMigrations?.length ? `${snapshot.database.pendingMigrations.length} offen` : "Aktuell", inline: true },
      { name: "Letztes DB-Backup", value: postgresBackup ? new Date(postgresBackup.createdAt).toLocaleString("de-DE") : "Nicht gefunden", inline: true },
      { name: "Cloud-Fehler (24h)", value: String(snapshot.database?.counts?.cloudErrors24h || 0), inline: true },
      { name: "Bewertung", value: issues.length ? issues.map(issue => `- ${issue}`).join("\n") : "Keine Probleme erkannt.", inline: false }
    )
    .setFooter({ text: `Live-Status | Aktualisierung alle ${Math.round(MONITOR_CHECK_MS / 1000)} Sekunden` })
    .setTimestamp(new Date(snapshot.checkedAt));
}

function distributionLabel(entries) {
  if (!entries?.length) return "Keine Daten";
  return entries.slice(0, 8).map(entry => `${entry.label}: **${entry.count}**`).join("\n");
}

function weeklyStatsEmbed(stats, onlinePlayers) {
  return new EmbedBuilder()
    .setTitle("betterUC Wochenstatistik")
    .setColor(0x38bdf8)
    .setDescription(`Auswertung seit ${new Date(stats.since).toLocaleString("de-DE")}`)
    .addFields(
      { name: "Accounts", value: `Aktiv: **${stats.accounts.active}**\nDiscord-verkn\u00fcpft: **${stats.accounts.linked}**\nJetzt online: **${onlinePlayers.length}**`, inline: true },
      { name: "Rollen", value: `Admins: **${stats.accounts.admins}**\nHelper: **${stats.accounts.helpers}**\nPartner: **${stats.accounts.partners}**\nVIP: **${stats.accounts.vips}**`, inline: true },
      { name: "Community", value: `Vorschl\u00e4ge: **${stats.suggestions.created}**\nStimmen: **${stats.suggestions.votes}**`, inline: true },
      { name: "Support", value: `Tickets erstellt: **${stats.tickets.opened}**\nGeschlossen: **${stats.tickets.closed}**\n\u00d8 Abschluss: **${stats.tickets.averageCloseMinutes} Min.**`, inline: true },
      { name: "Cloud", value: `Syncs: **${stats.cloud.syncs}**\nKonflikte: **${stats.cloud.conflicts}**\nFehler: **${stats.cloud.errors}**`, inline: true },
      { name: "Mod-Versionen", value: distributionLabel(stats.versions), inline: true },
      { name: "Minecraft-Versionen", value: distributionLabel(stats.gameVersions), inline: true }
    )
    .setTimestamp();
}

function isoWeekStart(date = new Date()) {
  const result = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  const day = result.getUTCDay() || 7;
  result.setUTCDate(result.getUTCDate() - day + 1);
  return result;
}

function hasManageGuild(interaction) {
  return Boolean(interaction.memberPermissions?.has(PermissionFlagsBits.ManageGuild));
}

async function handleCodeCommand(interaction, context) {
  if (!hasManageGuild(interaction)) {
    await interaction.reply({ content: "Dafuer brauchst du Discord-Adminrechte.", ephemeral: true });
    return;
  }

  const subcommand = interaction.options.getSubcommand();
  const name = interaction.options.getString("name", true);

  try {
    if (subcommand === "create") {
      const role = interaction.options.getString("rolle") || "user";
      const faction = interaction.options.getString("fraktion") || "";
      const result = await context.createAccessAccount({
        minecraftName: name,
        role,
        faction,
        createdBy: `discord:${interaction.user.id}`
      });
      await interaction.reply({
        content: [
          `Access-Code fuer **${result.account.minecraftName}** erstellt.`,
          "",
          "```text",
          result.accessCode,
          "```",
          "Der Code wird nur hier angezeigt."
        ].join("\n"),
        ephemeral: true
      });
      return;
    }

    if (subcommand === "reset") {
      const result = await context.resetAccessCodeByMinecraftName(name);
      await interaction.reply({
        content: [
          `Neuer Access-Code fuer **${result.account.minecraftName}**:`,
          "",
          "```text",
          result.accessCode,
          "```"
        ].join("\n"),
        ephemeral: true
      });
      return;
    }

    if (subcommand === "revoke") {
      const account = await context.revokeAccountByMinecraftName(name);
      if (account.discordId && interaction.guild) {
        const member = await interaction.guild.members.fetch(account.discordId).catch(() => null);
        await removeBetterUcRoles(member);
      }
      await interaction.reply({ content: `Account **${account.minecraftName}** wurde gesperrt.`, ephemeral: true });
    }
  } catch (error) {
    await interaction.reply({ content: error.message || "Aktion fehlgeschlagen.", ephemeral: true });
  }
}

async function interactionMember(interaction) {
  if (!interaction.guild) return interaction.member;
  return interaction.guild.members.fetch(interaction.user.id).catch(() => interaction.member);
}

async function handleCommand(interaction, context) {
  if (interaction.commandName === "online") {
    await interaction.reply({ embeds: [onlineEmbed(context.getOnlinePlayers())] });
    return;
  }

  if (interaction.commandName === "relay") {
    await interaction.reply({
      embeds: [relayEmbed(context.getOnlinePlayers(), context.getAccounts())],
      ephemeral: true
    });
    return;
  }

  if (interaction.commandName === "user") {
    const name = interaction.options.getString("name", true);
    const account = context.findAccountByMinecraftName(name);
    const onlinePlayer = context.getOnlinePlayers()
      .find(player => String(player.name || "").toLowerCase() === name.toLowerCase());
    if (!account && !onlinePlayer) {
      await interaction.reply({ content: "Zu diesem Spieler wurden keine betterUC Daten gefunden.", ephemeral: true });
      return;
    }
    await interaction.reply({ embeds: [userEmbed(account, onlinePlayer)], ephemeral: true });
    return;
  }

  if (interaction.commandName === "me") {
    const account = context.findAccountByDiscordId(interaction.user.id);
    if (!account) {
      await interaction.reply({
        content: "Dein Discord-Account ist noch nicht verknuepft. Nutze `/link code:<dein-code>`.",
        ephemeral: true
      });
      return;
    }
    const onlinePlayer = context.getOnlinePlayers()
      .find(player => String(player.name || "").toLowerCase() === String(account.minecraftName || "").toLowerCase());
    await interaction.reply({ embeds: [userEmbed(account, onlinePlayer)], ephemeral: true });
    return;
  }

  if (interaction.commandName === "link") {
    try {
      const account = await context.linkDiscordAccountByCode(
        interaction.options.getString("code", true),
        interaction.user.id
      );
      await syncBetterUcRoles(await interactionMember(interaction), account);
      await interaction.reply({
        content: `Verknuepft mit **${account.minecraftName}**. Der Bot kann dich jetzt als betterUC Mod-User erkennen.`,
        ephemeral: true
      });
    } catch (error) {
      await interaction.reply({ content: error.message || "Verknuepfung fehlgeschlagen.", ephemeral: true });
    }
    return;
  }

  if (interaction.commandName === "unlink") {
    try {
      const account = await context.unlinkDiscordAccount(interaction.user.id);
      await removeBetterUcRoles(await interactionMember(interaction));
      await interaction.reply({
        content: `Verknuepfung zu **${account.minecraftName}** wurde geloest.`,
        ephemeral: true
      });
    } catch (error) {
      await interaction.reply({ content: error.message || "Verknuepfung fehlgeschlagen.", ephemeral: true });
    }
    return;
  }

  if (interaction.commandName === "broadcast") {
    const result = await context.sendAnnouncementFromDiscord({
      discordId: interaction.user.id,
      discordName: interaction.member?.displayName || interaction.user.globalName || interaction.user.username,
      discordMessageUrl: null,
      message: interaction.options.getString("nachricht", true)
    });
    if (!result.ok) {
      await interaction.reply({ content: result.error || "Ank\u00fcndigung konnte nicht gesendet werden.", ephemeral: true });
      return;
    }
    await context.publishAnnouncement(result.event);
    await interaction.reply({ content: "Ank\u00fcndigung wurde an Discord und alle verbundenen Mod-Nutzer gesendet.", ephemeral: true });
    return;
  }

  if (interaction.commandName === "ticket") {
    await openTicket(interaction, interaction.options.getString("thema", true), context);
    return;
  }

  if (interaction.commandName === "ticket-panel") {
    await interaction.channel.send(ticketPanelPayload());
    await interaction.reply({ content: "Ticket-Panel wurde gepostet.", ephemeral: true });
    return;
  }

  if (interaction.commandName === "changelog") {
    const requestedVersion = normalizeVersion(interaction.options.getString("version") || "");
    await interaction.deferReply();
    try {
      const changelog = await readCentralChangelog();
      const changelogEntry = findChangelogRelease(changelog, requestedVersion);
      let release = changelogEntry
        ? {
            tag_name: `v${changelogEntry.version}`,
            name: changelogEntry.version,
            html_url: `https://github.com/${RELEASE_REPO}/releases/tag/v${changelogEntry.version}`
          }
        : null;

      if (!release) {
        try {
          release = requestedVersion
            ? await fetchReleaseByVersion(requestedVersion)
            : await fetchLatestRelease();
        } catch (error) {
          throw new Error(`Der zentrale Changelog und der GitHub-Fallback sind nicht erreichbar: ${error.message}`);
        }
      }

      if (!release) {
        if (requestedVersion) {
          await interaction.editReply({
            content: `Die Version **${requestedVersion}** wurde im betterUC-Changelog nicht gefunden.`
          });
          return;
        }
        throw new Error("Der betterUC-Changelog ist derzeit nicht erreichbar.");
      }

      await interaction.editReply({
        embeds: [releaseEmbed(release, changelogEntry)],
        components: [releaseLinks(release)]
      });
    } catch (error) {
      await interaction.editReply({
        content: error.message || "Der betterUC-Changelog konnte nicht geladen werden."
      });
    }
    return;
  }

  if (interaction.commandName === "update-benachrichtigung") {
    if (!interaction.guild) {
      await interaction.reply({ content: "Diese Einstellung ist nur auf dem betterUC Discord verf\u00fcgbar.", ephemeral: true });
      return;
    }
    const member = await interactionMember(interaction);
    const role = await ensureUpdateNotificationRole(interaction.guild);
    if (!role || !member?.roles) {
      await interaction.reply({
        content: "Die Update-Rolle konnte nicht eingerichtet werden. Bitte melde das dem betterUC Team.",
        ephemeral: true
      });
      return;
    }
    const enabled = interaction.options.getSubcommand() === "an";
    try {
      if (enabled) {
        await member.roles.add(role, "betterUC update notifications enabled");
      } else {
        await member.roles.remove(role, "betterUC update notifications disabled");
      }
      await interaction.reply({
        content: enabled
          ? "Update-Benachrichtigungen sind jetzt **aktiv**."
          : "Update-Benachrichtigungen sind jetzt **deaktiviert**.",
        ephemeral: true
      });
    } catch (error) {
      await interaction.reply({
        content: "Die Update-Rolle konnte nicht ge\u00e4ndert werden. Pr\u00fcfe bitte die Rollenreihenfolge des Bots.",
        ephemeral: true
      });
    }
    return;
  }

  if (interaction.commandName === "diagnose") {
    if (!isTicketTeamMember(interaction)) {
      await interaction.reply({ content: "Dieser Befehl ist nur f\u00fcr das betterUC Team verf\u00fcgbar.", ephemeral: true });
      return;
    }
    await deferEphemeral(interaction);
    const name = interaction.options.getString("spieler", true);
    const account = await context.getAccountDiagnostic(name);
    const onlinePlayer = context.getOnlinePlayers()
      .find(player => String(player.name || "").toLowerCase() === name.toLowerCase());
    if (!account && !onlinePlayer) {
      await interaction.editReply({ content: "Zu diesem Spieler wurden keine betterUC-Daten gefunden." });
      return;
    }
    await interaction.editReply({ embeds: [diagnosticEmbed(account, onlinePlayer)] });
    return;
  }

  if (interaction.commandName === "updates") {
    if (!hasManageGuild(interaction)) {
      await interaction.reply({ content: "Dafuer brauchst du Discord-Adminrechte.", ephemeral: true });
      return;
    }
    const subcommand = interaction.options.getSubcommand();
    try {
      const result = await checkGithubRelease(interaction.client, {
        forcePost: subcommand === "post_latest",
        announceExisting: true
      });
      if (result.status === "posted") {
        await interaction.reply({ content: "Update wurde im Discord-Update-Channel gepostet.", ephemeral: true });
      } else if (result.status === "waiting_for_changelog") {
        await interaction.reply({
          content: "Das Release wurde erkannt, aber der passende zentrale Changelog ist noch nicht auf dem Server angekommen. Der Bot versucht es automatisch erneut.",
          ephemeral: true
        });
      } else if (result.status === "unchanged" || result.status === "initialized") {
        await interaction.reply({
          content: `Kein neues Release. Aktuell erkannt: ${result.release?.tag_name || "unbekannt"}.`,
          ephemeral: true
        });
      } else {
        await interaction.reply({ content: `Update-Check Status: ${result.status}.`, ephemeral: true });
      }
    } catch (error) {
      await interaction.reply({ content: error.message || "Update-Check fehlgeschlagen.", ephemeral: true });
    }
    return;
  }

  if (interaction.commandName === "rollen-sync") {
    if (!hasManageGuild(interaction)) {
      await interaction.reply({ content: "Daf\u00fcr brauchst du Discord-Adminrechte.", ephemeral: true });
      return;
    }
    await deferEphemeral(interaction);
    const result = await context.syncRoles();
    await interaction.editReply({
      content: `Rollensync abgeschlossen: **${result.synced}** Nutzer synchronisiert, **${result.created}** Rollen neu angelegt.`
    });
    return;
  }

  if (interaction.commandName === "systemstatus") {
    if (!hasManageGuild(interaction)) {
      await interaction.reply({ content: "Daf\u00fcr brauchst du Discord-Adminrechte.", ephemeral: true });
      return;
    }
    await deferEphemeral(interaction);
    const report = await systemReport(context);
    await interaction.editReply({ embeds: [systemStatusEmbed(report)] });
    return;
  }

  if (interaction.commandName === "vorschlag") {
    await handleSuggestionCommand(interaction, context);
    return;
  }

  if (interaction.commandName === "wochenstatistik") {
    if (!hasManageGuild(interaction)) {
      await interaction.reply({ content: "Daf\u00fcr brauchst du Discord-Adminrechte.", ephemeral: true });
      return;
    }
    await deferEphemeral(interaction);
    const stats = await context.getDiscordWeeklyStats(new Date(Date.now() - 7 * 24 * 60 * 60 * 1000));
    await interaction.editReply({ embeds: [weeklyStatsEmbed(stats, context.getOnlinePlayers())] });
    return;
  }

  if (interaction.commandName === "code") {
    await handleCodeCommand(interaction, context);
  }
}

async function handleInteraction(interaction, context) {
  try {
    if (interaction.isChatInputCommand()) {
      await handleCommand(interaction, context);
      return;
    }

    if (interaction.isButton()) {
      if (interaction.customId.startsWith("ticket:open:")) {
        await openTicket(interaction, interaction.customId.split(":")[2] || "support", context);
        return;
      }
      if (interaction.customId === "ticket:claim") {
        await claimTicket(interaction, context);
        return;
      }
      if (interaction.customId === "ticket:close") {
        await showCloseTicketModal(interaction);
        return;
      }
      if (interaction.customId === "ticket:delete") {
        await deleteTicket(interaction);
        return;
      }
      if (interaction.customId.startsWith("suggestion:vote:")) {
        await voteSuggestion(interaction, context);
      }
      return;
    }

    if (interaction.isModalSubmit() && interaction.customId === "ticket:close-modal") {
      await closeTicket(interaction, context);
    }
  } catch (error) {
    console.error("Discord interaction error", error);
    if (interaction.deferred || interaction.replied) {
      await interaction.followUp({ content: "Discord-Aktion fehlgeschlagen.", ephemeral: true }).catch(() => null);
    } else {
      await interaction.reply({ content: "Discord-Aktion fehlgeschlagen.", ephemeral: true }).catch(() => null);
    }
  }
}

async function syncBetterUcRoleState(client, context) {
  if (!GUILD_ID) return { created: 0, synced: 0 };
  const guild = await client.guilds.fetch(GUILD_ID).catch(() => null);
  if (!guild) return { created: 0, synced: 0 };
  const created = await ensureManagedBetterUcRoles(guild);
  let synced = 0;

  for (const account of context.getAccounts()) {
    if (!account.discordId) continue;
    const member = await guild.members.fetch(account.discordId).catch(() => null);
    if (!member) continue;
    await syncBetterUcRoles(member, account);
    synced++;
  }
  return { created, synced };
}

async function startDiscordBot(context) {
  if (!BOT_TOKEN) {
    return {
      notifyStateChanged() {},
      publishAnnouncement() { return Promise.resolve(); },
      createBugReport() { return Promise.reject(new Error("Discord-Bot ist nicht konfiguriert.")); },
      stop() {}
    };
  }

  try {
    loadDiscord();
  } catch (error) {
    console.error("Discord bot is enabled, but discord.js is not installed. Run npm install in the server directory.", error.message);
    return {
      notifyStateChanged() {},
      publishAnnouncement() { return Promise.resolve(); },
      createBugReport() { return Promise.reject(new Error("Discord-Bot ist nicht verfuegbar.")); },
      stop() {}
    };
  }

  const intents = [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMessages];
  const client = new Client({
    intents
  });

  let ready = false;
  let presenceTimer = null;
  let roleSyncTimer = null;
  let releaseCheckTimer = null;
  let monitorTimer = null;
  let monitorRefreshTimer = null;
  let monitorRun = Promise.resolve();
  let weeklyTimer = null;
  let suggestionGuideTimer = null;
  let suggestionGuideRun = Promise.resolve();
  let announcementChannel = null;
  let ticketLogChannel = null;
  let suggestionChannel = null;
  let bugForumChannel = null;
  let monitorChannel = null;
  let weeklyChannel = null;
  let changelogChannel = null;

  const publishAnnouncement = async event => {
    if (!announcementChannel) return;
    await announcementChannel.send({
      embeds: [announcementEmbed(event)],
      allowedMentions: { parse: [] }
    });
  };

  const createBugReport = async report => {
    if (!ready || !bugForumChannel) {
      throw new Error("Discord Bug-Forum ist nicht verfuegbar.");
    }
    const files = [];
    if (report.logExcerpt) {
      files.push(new AttachmentBuilder(Buffer.from(report.logExcerpt, "utf8"), {
        name: "betteruc-latest-log.txt",
        description: "Vom Spieler freigegebener Auszug aus latest.log"
      }));
    }
    const newTag = bugForumChannel.availableTags?.find(tag => tag.name.toLowerCase() === "neu");
    const thread = await bugForumChannel.threads.create({
      name: clean(report.title).slice(0, 100),
      reason: `betterUC Bugreport von ${display(report.reporterName)}`,
      appliedTags: newTag ? [newTag.id] : [],
      message: {
        embeds: [bugReportEmbed(report)],
        files,
        allowedMentions: { parse: [] }
      }
    });
    return {
      threadId: thread.id,
      url: thread.url || `https://discord.com/channels/${thread.guildId}/${thread.id}`
    };
  };

  const updateSuggestionGuide = async () => {
    if (!SUGGESTION_GUIDE_ENABLED || !suggestionChannel) return null;

    let guideMessage = null;
    if (botState.suggestionGuideChannelId === suggestionChannel.id && botState.suggestionGuideMessageId) {
      guideMessage = await suggestionChannel.messages.fetch(botState.suggestionGuideMessageId).catch(() => null);
    }

    const newestMessages = await suggestionChannel.messages.fetch({ limit: 1 }).catch(() => null);
    const newestMessage = newestMessages?.first?.() || null;
    if (guideMessage && newestMessage?.id === guideMessage.id) {
      await guideMessage.edit(suggestionGuidePayload());
      return guideMessage;
    }

    if (guideMessage) {
      await guideMessage.delete().catch(error => {
        console.warn("Could not move betterUC suggestion guide", error.message);
      });
    }

    guideMessage = await suggestionChannel.send(suggestionGuidePayload());
    botState.suggestionGuideMessageId = guideMessage.id;
    botState.suggestionGuideChannelId = suggestionChannel.id;
    await writeBotState(botState);
    return guideMessage;
  };

  const queueSuggestionGuide = (delayMs = SUGGESTION_GUIDE_DELAY_MS) => {
    if (!SUGGESTION_GUIDE_ENABLED || !suggestionChannel) return;
    clearTimeout(suggestionGuideTimer);
    suggestionGuideTimer = setTimeout(() => {
      suggestionGuideRun = suggestionGuideRun
        .catch(() => null)
        .then(() => updateSuggestionGuide())
        .catch(error => console.warn("Discord suggestion guide update failed", error.message));
    }, Math.max(0, delayMs));
  };

  const commandContext = {
    ...context,
    publishAnnouncement,
    getTicketLogChannel: () => ticketLogChannel,
    getSuggestionChannel: () => suggestionChannel,
    refreshSuggestionGuide: () => queueSuggestionGuide(),
    syncRoles: () => syncBetterUcRoleState(client, context)
  };

  const updateMonitorMessage = async report => {
    if (!monitorChannel) return null;

    let message = null;
    if (botState.monitorMessageChannelId === monitorChannel.id && botState.monitorMessageId) {
      message = await monitorChannel.messages.fetch(botState.monitorMessageId).catch(() => null);
    }

    const payload = {
      embeds: [systemStatusEmbed(report)],
      allowedMentions: { parse: [] }
    };
    if (message) {
      await message.edit(payload);
    } else {
      message = await monitorChannel.send(payload);
      botState.monitorMessageId = message.id;
      botState.monitorMessageChannelId = monitorChannel.id;
    }
    if (MONITOR_PIN_MESSAGE && !message.pinned) {
      await message.pin("betterUC permanent system status").catch(error => {
        console.warn("Could not pin betterUC system status", error.message);
      });
    }
    return message;
  };

  const runMonitor = async () => {
    if (!MONITOR_ENABLED) return null;
    const report = await systemReport(commandContext);
    await updateMonitorMessage(report);
    botState.monitorState = report.healthy ? "healthy" : "degraded";
    botState.monitorCheckedAt = new Date().toISOString();
    await writeBotState(botState);
    return report;
  };

  const queueMonitor = () => {
    monitorRun = monitorRun.catch(() => null).then(() => runMonitor());
    return monitorRun;
  };

  const runWeeklyReport = async (forcePost = false) => {
    if (!weeklyChannel) return null;
    const now = new Date();
    const weekStart = isoWeekStart(now);
    const weekKey = weekStart.toISOString().slice(0, 10);
    const due = now.getUTCDay() === WEEKLY_REPORT_DAY && now.getUTCHours() >= WEEKLY_REPORT_HOUR_UTC;
    if (!forcePost && (!due || botState.weeklyReportKey === weekKey)) return null;
    const stats = await context.getDiscordWeeklyStats(new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000));
    await weeklyChannel.send({ embeds: [weeklyStatsEmbed(stats, context.getOnlinePlayers())] });
    botState.weeklyReportKey = weekKey;
    botState.weeklyReportPostedAt = now.toISOString();
    await writeBotState(botState);
    return stats;
  };

  const updatePresence = () => {
    if (!ready || !client.user) return;
    const count = context.getOnlinePlayers().length;
    client.user.setPresence({
      status: count > 0 ? "online" : "idle",
      activities: [{
        name: `${count} Mod-User online`,
        type: ActivityType.Watching
      }]
    });
  };

  const notifyStateChanged = () => {
    clearTimeout(presenceTimer);
    presenceTimer = setTimeout(updatePresence, 750);
    clearTimeout(monitorRefreshTimer);
    monitorRefreshTimer = setTimeout(() => {
      if (!ready || !monitorChannel) return;
      queueMonitor().catch(error => console.warn("Discord live system monitoring failed", error.message));
    }, 1000);
  };

  client.once("ready", async () => {
    ready = true;
    botState = await readBotState();
    console.log(`betterUC Discord bot logged in as ${client.user.tag}`);
    try {
      if (GUILD_ID) {
        const guild = await client.guilds.fetch(GUILD_ID);
        await guild.commands.set(buildCommands());
        console.log(`betterUC Discord commands synced for ${guild.name}`);
        announcementChannel = await resolveTextChannel(guild, ANNOUNCEMENT_CHANNEL_ID, ANNOUNCEMENT_CHANNEL_NAME);
        ticketLogChannel = await resolveTextChannel(guild, TICKET_LOG_CHANNEL_ID, TICKET_LOG_CHANNEL_NAME);
        suggestionChannel = await resolveTextChannel(guild, SUGGESTION_CHANNEL_ID, SUGGESTION_CHANNEL_NAME);
        bugForumChannel = await resolveForumChannel(guild, BUG_FORUM_CHANNEL_ID, BUG_FORUM_CHANNEL_NAME);
        monitorChannel = await resolveTextChannel(guild, MONITOR_CHANNEL_ID, MONITOR_CHANNEL_NAME);
        weeklyChannel = await resolveTextChannel(guild, WEEKLY_CHANNEL_ID, WEEKLY_CHANNEL_NAME);
        changelogChannel = await resolveTextChannel(guild, CHANGELOG_CHANNEL_ID, CHANGELOG_CHANNEL_NAME);
        await ensureUpdateNotificationRole(guild).catch(error => {
          console.warn("Discord update notification role setup failed", error.message);
        });
        if (!announcementChannel) {
          console.warn(`Discord announcement channel not found (${ANNOUNCEMENT_CHANNEL_ID || ANNOUNCEMENT_CHANNEL_NAME})`);
        }
        if (!ticketLogChannel) console.warn(`Discord ticket log channel not found (${TICKET_LOG_CHANNEL_ID || TICKET_LOG_CHANNEL_NAME})`);
        if (!suggestionChannel) console.warn(`Discord suggestion channel not found (${SUGGESTION_CHANNEL_ID || SUGGESTION_CHANNEL_NAME})`);
        if (!bugForumChannel) console.warn(`Discord bug forum not found (${BUG_FORUM_CHANNEL_ID || BUG_FORUM_CHANNEL_NAME})`);
        if (MONITOR_ENABLED && !monitorChannel) console.warn(`Discord monitor channel not found (${MONITOR_CHANNEL_ID || MONITOR_CHANNEL_NAME})`);
        if (!weeklyChannel) console.warn(`Discord weekly channel not found (${WEEKLY_CHANNEL_ID || WEEKLY_CHANNEL_NAME})`);
        if (!changelogChannel) console.warn(`Discord changelog channel not found (${CHANGELOG_CHANNEL_ID || CHANGELOG_CHANNEL_NAME})`);
      } else {
        await client.application.commands.set(buildCommands());
        console.log("betterUC Discord commands synced globally");
      }
    } catch (error) {
      console.error("Could not sync betterUC Discord commands", error);
    }
    if (suggestionChannel) {
      await updateSuggestionGuide().catch(error => console.warn("Discord suggestion guide setup failed", error.message));
    }
    updatePresence();
    syncBetterUcRoleState(client, context).catch(error => console.warn("Discord role sync failed", error.message));
    roleSyncTimer = setInterval(() => {
      syncBetterUcRoleState(client, context).catch(error => console.warn("Discord role sync failed", error.message));
    }, Math.max(60 * 1000, Number(process.env.DISCORD_ROLE_SYNC_MS || 5 * 60 * 1000)));
    queueMonitor().catch(error => console.warn("Discord system monitoring failed", error.message));
    monitorTimer = setInterval(() => {
      queueMonitor().catch(error => console.warn("Discord system monitoring failed", error.message));
    }, MONITOR_CHECK_MS);
    runWeeklyReport().catch(error => console.warn("Discord weekly report failed", error.message));
    weeklyTimer = setInterval(() => {
      runWeeklyReport().catch(error => console.warn("Discord weekly report failed", error.message));
    }, 60 * 60 * 1000);
    checkGithubRelease(client).catch(error => console.warn("Discord release check failed", error.message));
    releaseCheckTimer = setInterval(() => {
      checkGithubRelease(client).catch(error => console.warn("Discord release check failed", error.message));
    }, RELEASE_CHECK_MS);
  });

  client.on("interactionCreate", interaction => handleInteraction(interaction, commandContext));
  client.on("messageCreate", async message => {
    if (suggestionChannel
        && message.guild?.id === GUILD_ID
        && message.channel.id === suggestionChannel.id
        && !message.author.bot) {
      queueSuggestionGuide();
    }
  });
  client.on("error", error => console.error("Discord bot error", error));
  client.on("warn", message => console.warn("Discord bot warning", message));

  await client.login(BOT_TOKEN);

  return {
    notifyStateChanged,
    publishAnnouncement,
    createBugReport,
    stop() {
      clearTimeout(presenceTimer);
      clearTimeout(monitorRefreshTimer);
      clearTimeout(suggestionGuideTimer);
      clearInterval(roleSyncTimer);
      clearInterval(releaseCheckTimer);
      clearInterval(monitorTimer);
      clearInterval(weeklyTimer);
      client.destroy();
    }
  };
}

module.exports = {
  startDiscordBot
};
