"use strict";

const crypto = require("crypto");
const { execFile } = require("child_process");
const fs = require("fs");
const fsp = fs.promises;
const http = require("http");
const path = require("path");
const { Readable } = require("stream");
const { URL } = require("url");
const { promisify } = require("util");
const { WebSocketServer } = require("ws");
const { startDiscordBot } = require("./discordBot");
const { createDatabase } = require("./database");
const { createClipStorage } = require("./clipStorage");
const { createClipRoutes, CLIP_PAGE } = require("./clipRoutes");
const { selectPreferredAccount } = require("./accountResolution");
const { createPoliceRosterService } = require("./policeRoster");
const { createSwatRosterStore } = require("./swatRoster");
const { startTeamSpeakFactionSync } = require("./teamSpeakFactionSync");

const PORT = Number(process.env.PORT || 3000);
const MAX_CLIENTS = Number(process.env.MAX_CLIENTS || 500);
const PING_TTL_MS = Number(process.env.PING_TTL_MS || 15000);
const PUBLIC_DIR = process.env.PUBLIC_DIR || path.join(__dirname, "public");
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, "data");
const STORE_FILE = path.join(DATA_DIR, "accounts.json");
const SWAT_ROSTER_FILE = path.join(DATA_DIR, "swat-roster.json");
const BACKUP_DIR = process.env.BACKUP_DIR || path.join(DATA_DIR, "backups");
const POSTGRES_BACKUP_DIR = process.env.POSTGRES_BACKUP_DIR || path.join(DATA_DIR, "postgres-backups");
const SCREENSHOT_DIR = process.env.SCREENSHOT_DIR || path.join(DATA_DIR, "screenshots");
const SCREENSHOT_MAX_BYTES = Math.min(25 * 1024 * 1024, Math.max(1024, Number(process.env.SCREENSHOT_MAX_BYTES || 12 * 1024 * 1024)));
const SCREENSHOT_TTL_MS = Math.max(60 * 60 * 1000, Number(process.env.SCREENSHOT_TTL_MS || 7 * 24 * 60 * 60 * 1000));
const SCREENSHOT_CLEANUP_INTERVAL_MS = Math.max(60 * 1000, Number(process.env.SCREENSHOT_CLEANUP_INTERVAL_MS || 60 * 60 * 1000));
const BACKUP_RETENTION_DAYS = Number(process.env.BACKUP_RETENTION_DAYS || 30);
const BACKUP_INTERVAL_ENV = Number(process.env.BACKUP_INTERVAL_MS);
const BACKUP_INTERVAL_MS = Number.isFinite(BACKUP_INTERVAL_ENV)
  ? Math.max(60 * 60 * 1000, BACKUP_INTERVAL_ENV)
  : 24 * 60 * 60 * 1000;
const TOKEN_PEPPER = process.env.TOKEN_PEPPER || process.env.BETTERUC_TOKEN || "betteruc-local-pepper";
const LEGACY_RELAY_TOKEN = (process.env.BETTERUC_TOKEN || "").trim();
const ALLOW_LEGACY_TOKEN = String(process.env.ALLOW_LEGACY_TOKEN || "true").toLowerCase() !== "false";
const ADMIN_KEY = (process.env.ADMIN_KEY || "").trim();
const SESSION_SECRET = process.env.SESSION_SECRET || TOKEN_PEPPER;
const USER_SESSION_TTL_MS = Number(process.env.USER_SESSION_TTL_MS || 1000 * 60 * 60 * 24 * 14);
const MOD_SESSION_TTL_MS = Math.max(
  60 * 60 * 1000,
  Number(process.env.MOD_SESSION_TTL_MS || 1000 * 60 * 60 * 24 * 30)
);
const AUTH_CHALLENGE_TTL_MS = Math.max(
  15 * 1000,
  Math.min(2 * 60 * 1000, Number(process.env.AUTH_CHALLENGE_TTL_MS || 60 * 1000))
);
const MOJANG_HAS_JOINED_URL = process.env.MOJANG_HAS_JOINED_URL
  || "https://sessionserver.mojang.com/session/minecraft/hasJoined";
const GITHUB_RELEASES_URL = "https://github.com/xoner1441/betterUC/releases";
const GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/xoner1441/betterUC/releases/latest";
const PUBLIC_BASE_URL = String(process.env.PUBLIC_BASE_URL || "https://betteruc.de").replace(/\/+$/, "");
const SCREENSHOT_PUBLIC_BASE_URL = String(
  process.env.SCREENSHOT_PUBLIC_BASE_URL || PUBLIC_BASE_URL
).replace(/\/+$/, "");
const policeRoster = createPoliceRosterService({
  apiUrl: process.env.POLICE_ROSTER_API_URL,
  headBaseUrl: process.env.POLICE_ROSTER_HEAD_BASE_URL,
  rosterCacheMs: process.env.POLICE_ROSTER_CACHE_MS,
  headCacheMs: process.env.POLICE_ROSTER_HEAD_CACHE_MS,
  timeoutMs: process.env.POLICE_ROSTER_TIMEOUT_MS,
  slotLimit: process.env.POLICE_FACTION_SLOT_LIMIT,
  unitOverrides: process.env.TEAMSPEAK_FACTION_UNIT_OVERRIDES
});
const swatRoster = createSwatRosterStore({
  file: SWAT_ROSTER_FILE,
  renderer: policeRoster,
  slotLimit: process.env.TEAMSPEAK_SWAT_SLOT_LIMIT,
  supervisorOverrides: process.env.SWAT_ROSTER_SUPERVISOR_OVERRIDES
});
const SWAT_ROSTER_OWNER_NAME = String(process.env.SWAT_ROSTER_OWNER_NAME || "FABI1441").trim();
const RELEASE_CACHE_TTL_MS = Number(process.env.RELEASE_CACHE_TTL_MS || 5 * 60 * 1000);
const UPDATE_WATCH_INTERVAL_MS = Math.max(
  30 * 1000,
  Number(process.env.UPDATE_WATCH_INTERVAL_MS || 60 * 1000)
);
const CLOUD_SETTINGS_SCHEMA_VERSION = 1;
const CLOUD_SETTINGS_MAX_BYTES = 48 * 1024;
const ANNOUNCEMENT_MAX_LENGTH = 300;
const ANNOUNCEMENT_COOLDOWN_MS = 10000;
const PG_DUMP_BIN = process.env.PG_DUMP_BIN || "pg_dump";
const PG_DUMP_TIMEOUT_MS = Math.max(30_000, Number(process.env.PG_DUMP_TIMEOUT_MS || 5 * 60 * 1000));
const execFileAsync = promisify(execFile);

const FEATURE_FLAG_DEFINITIONS = Object.freeze([
  { key: "ping_system", label: "Ping-System", description: "Private globale, fraktions- und staatsbasierte Pings" },
  { key: "chat_customization", label: "WPS/HQ Customizations", description: "Kompakte WPS- und HQ-Nachrichten" },
  { key: "reinf_customization", label: "Reinf Customizations", description: "Kompakte Fraktions- und Bündnisrufe" },
  { key: "cloud_settings", label: "Cloud-Sync", description: "Synchronisierte Mod-Einstellungen" },
  { key: "auto_dropdrink", label: "Auto-Dropdrink", description: "Automatische Lieferjunge-Abgabe" },
  { key: "auto_fisher", label: "Auto-Fischer", description: "Automatische Fischer-Befehle" },
  { key: "auto_winzer", label: "Auto-Winzer", description: "Automatisches Leeren der Trauben-Fenster" },
  { key: "auto_gaertner", label: "Auto-Gärtner", description: "Automatische Blumenabgabe und Buschsammlung" },
  { key: "auto_muellmann", label: "Auto-Müllmann", description: "Automatische Müllsortierung in markierten Bereichen" },
  { key: "auto_money_transport", label: "Auto-Geldtransport", description: "Automatische Geldabgabe am erkannten Einzahlungsziel" },
  { key: "auto_transport", label: "Auto-Transport", description: "Scoreboard-gesteuerte Kistenabgabe am Lieferziel" }
]);

const MIME_TYPES = new Map([
  [".html", "text/html; charset=utf-8"],
  [".css", "text/css; charset=utf-8"],
  [".js", "application/javascript; charset=utf-8"],
  [".json", "application/json; charset=utf-8"],
  [".png", "image/png"],
  [".jpg", "image/jpeg"],
  [".jpeg", "image/jpeg"],
  [".webp", "image/webp"],
  [".ico", "image/x-icon"],
  [".jar", "application/java-archive"]
]);

let store = { version: 1, accounts: [] };
let saveTimer = null;
let backupTimer = null;
let updateWatchTimer = null;
let screenshotCleanupTimer = null;
let saveQueue = Promise.resolve();
let persistenceMode = "json";
let latestReleaseCache = { fetchedAt: 0, release: null };
let latestKnownReleaseVersion = "";
let wasteDropAreas = {};
let cloudRevisionByAccountId = new Map();
const clients = new Set();
const rateLimits = new Map();
const loginChallenges = new Map();
let lastAnnouncementAt = 0;
let discordBot = {
  notifyStateChanged() {},
  publishAnnouncement() { return Promise.resolve(); },
  createBugReport() { return Promise.reject(new Error("Discord-Bot ist nicht verfuegbar.")); },
  stop() {}
};
let teamSpeakFactionSync = { stop() {} };
const database = createDatabase();
const clipRoutes = createClipRoutes({ database, storage: createClipStorage(), authenticate, requireUserSession,
  json, readJsonBody, isRateLimited, screenshotGalleryItem, publicBaseUrl: PUBLIC_BASE_URL.replace(/\/$/, '') });
let clipCleanupTimer;

function nowIso() {
  return new Date().toISOString();
}

async function recordCloudSyncEvent(accountId, event) {
  if (persistenceMode !== "postgres" || !database.enabled || !accountId) return;
  try {
    await database.recordCloudSyncEvent(accountId, event);
  } catch (error) {
    console.error("Could not record cloud sync event", accountId, error);
  }
}

async function recordDiscordActivity(eventType, accountId = null, details = {}) {
  if (persistenceMode !== "postgres" || !database.enabled) return;
  try {
    await database.recordDiscordActivity(eventType, accountId, details);
  } catch (error) {
    console.error("Could not record Discord activity", eventType, error);
  }
}

function defaultFeatureFlags() {
  return FEATURE_FLAG_DEFINITIONS.map(flag => ({
    ...flag,
    enabled: true,
    updatedAt: null,
    updatedBy: "default"
  }));
}

async function loadFeatureFlags() {
  const defaults = defaultFeatureFlags();
  if (persistenceMode !== "postgres" || !database.enabled) return defaults;

  const stored = await database.listFeatureFlags();
  const storedByKey = new Map(stored.map(flag => [flag.key, flag]));
  return defaults.map(fallback => ({ ...fallback, ...(storedByKey.get(fallback.key) || {}) }));
}

const WASTE_TYPES = new Set(["glas", "metall", "abfall", "holz"]);

function publicWasteDropAreas() {
  const result = {};
  for (const type of WASTE_TYPES) {
    const area = wasteDropAreas[type];
    if (!area) continue;
    result[type] = {
      x1: Number.isInteger(area.x1) ? area.x1 : null,
      z1: Number.isInteger(area.z1) ? area.z1 : null,
      x2: Number.isInteger(area.x2) ? area.x2 : null,
      z2: Number.isInteger(area.z2) ? area.z2 : null,
      dimension: cleanDimension(area.dimension || ""),
      updatedAt: area.updatedAt || null
    };
  }
  return result;
}

async function loadWasteDropAreas() {
  wasteDropAreas = {};
  if (persistenceMode !== "postgres" || !database.enabled) return;
  const stored = await database.listWasteDropAreas();
  for (const area of stored) {
    if (area && WASTE_TYPES.has(area.type)) wasteDropAreas[area.type] = area;
  }
}

function sendWasteDropAreas(client) {
  if (!client || client.ws.readyState !== client.ws.OPEN) return;
  client.ws.send(JSON.stringify({
    type: "waste_areas",
    areas: publicWasteDropAreas()
  }));
}

function broadcastWasteDropAreas() {
  for (const client of clients) sendWasteDropAreas(client);
}

function cleanRelayMessage(value) {
  return String(value || "")
    .replace(/\u00a7./g, "")
    .replace(/[\u0000-\u001f\u007f]/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

function announcementRateLimit() {
  const now = Date.now();
  const remainingMs = ANNOUNCEMENT_COOLDOWN_MS - (now - lastAnnouncementAt);
  if (remainingMs > 0) {
    return `Bitte warte noch ${Math.ceil(remainingMs / 1000)} Sekunde(n), bevor du erneut sendest.`;
  }
  lastAnnouncementAt = now;
  return null;
}

function broadcastAnnouncement(sender, message, origin = "minecraft", metadata = {}) {
  const event = {
    type: "announcement",
    id: crypto.randomUUID(),
    sender: sender.name || (sender.account && sender.account.minecraftName) || "betterUC Team",
    role: "admin",
    message,
    origin,
    accountId: sender.account && sender.account.id !== "legacy"
      ? sender.account.id
      : (sender.id || null),
    discordId: metadata.discordId || null,
    discordName: metadata.discordName || null,
    discordMessageUrl: metadata.discordMessageUrl || null,
    createdAt: Date.now()
  };
  const raw = JSON.stringify({
    type: event.type,
    id: event.id,
    sender: event.sender,
    message: event.message,
    origin: event.origin,
    createdAt: event.createdAt
  });
  for (const target of clients) {
    if (target.ws.readyState !== target.ws.OPEN || target.authType === "legacy") continue;
    target.ws.send(raw);
  }
  return event;
}

async function sendAnnouncementFromDiscord(input) {
  const account = findAccountByDiscordId(input && input.discordId);
  if (!account || cleanRole(account.role) !== "admin") {
    return { ok: false, error: "Nur verkn\u00fcpfte betterUC-Admins d\u00fcrfen Ank\u00fcndigungen senden." };
  }
  const message = cleanRelayMessage(input.message);
  if (!message) return { ok: false, error: "Die Ank\u00fcndigung ist leer." };
  if (message.length > ANNOUNCEMENT_MAX_LENGTH) {
    return { ok: false, error: `Eine Ank\u00fcndigung darf maximal ${ANNOUNCEMENT_MAX_LENGTH} Zeichen lang sein.` };
  }
  const rateLimitError = announcementRateLimit();
  if (rateLimitError) return { ok: false, error: rateLimitError };

  const sender = {
    id: account.id,
    name: account.minecraftName || input.discordName || "betterUC Team",
    role: "admin",
    account
  };
  const event = broadcastAnnouncement(sender, message, "discord", {
    discordId: input.discordId,
    discordName: input.discordName,
    discordMessageUrl: input.discordMessageUrl
  });
  recordDiscordActivity("announcement.discord", account.id).catch(() => {});
  return { ok: true, event };
}

function json(res, status, payload, headers = {}) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "access-control-allow-origin": "*",
    ...headers
  });
  res.end(body);
}

function text(res, status, body) {
  res.writeHead(status, {
    "content-type": "text/plain; charset=utf-8",
    "cache-control": "no-store"
  });
  res.end(body);
}

function clientIp(req) {
  const forwarded = req.headers["x-forwarded-for"];
  if (typeof forwarded === "string" && forwarded.trim()) {
    return forwarded.split(",")[0].trim();
  }
  return req.socket.remoteAddress || "unknown";
}

function isRateLimited(req, bucket, limit, windowMs) {
  const key = `${bucket}:${clientIp(req)}`;
  const now = Date.now();
  const entry = rateLimits.get(key) || { count: 0, resetAt: now + windowMs };
  if (now > entry.resetAt) {
    entry.count = 0;
    entry.resetAt = now + windowMs;
  }
  entry.count += 1;
  rateLimits.set(key, entry);
  return entry.count > limit;
}

async function readJsonStore() {
  await fsp.mkdir(DATA_DIR, { recursive: true });
  try {
    const raw = await fsp.readFile(STORE_FILE, "utf8");
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed.accounts)) {
      return { version: 1, ...parsed };
    }
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
  return null;
}

async function writeJsonStore(snapshot = store) {
  await fsp.mkdir(DATA_DIR, { recursive: true });
  const tmp = `${STORE_FILE}.tmp`;
  await fsp.writeFile(tmp, JSON.stringify(snapshot, null, 2), "utf8");
  await fsp.rename(tmp, STORE_FILE);
}

async function loadStore() {
  const jsonStore = await readJsonStore();
  if (database.enabled) {
    try {
      await database.initialize();
      const databaseAccounts = await database.loadAccounts();
      if (databaseAccounts.length === 0 && jsonStore && jsonStore.accounts.length > 0) {
        await database.replaceAccounts(jsonStore.accounts);
        store = jsonStore;
        console.log(`Imported ${store.accounts.length} betterUC accounts from JSON into PostgreSQL.`);
      } else {
        store = { version: 1, accounts: databaseAccounts };
      }
      persistenceMode = "postgres";
      cloudRevisionByAccountId = new Map(
        (await database.listCloudSettingRevisions())
          .map(entry => [entry.accountId, Number(entry.revision || 0)])
      );
      await writeJsonStore(store);
      console.log(`betterUC persistence: PostgreSQL (${store.accounts.length} accounts)`);
      return;
    } catch (error) {
      if (database.required) throw error;
      console.error("PostgreSQL is unavailable; using the JSON fallback for this start.", error);
    }
  }

  persistenceMode = "json";
  cloudRevisionByAccountId = new Map();
  store = jsonStore || { version: 1, accounts: [] };
  await writeJsonStore(store);
  console.log(`betterUC persistence: JSON fallback (${store.accounts.length} accounts)`);
}

async function saveStore() {
  const snapshot = JSON.parse(JSON.stringify(store));
  const operation = saveQueue.catch(() => {}).then(async () => {
    if (persistenceMode === "postgres") {
      await database.replaceAccounts(snapshot.accounts);
    }
    await writeJsonStore(snapshot);
  });
  saveQueue = operation;
  return operation;
}

function backupDateKey(date = new Date()) {
  return date.toISOString().slice(0, 10);
}

function backupTimestamp(date = new Date()) {
  return date.toISOString().replace(/[:.]/g, "-");
}

async function listStoreBackups() {
  try {
    const entries = await fsp.readdir(BACKUP_DIR, { withFileTypes: true });
    const backups = await Promise.all(entries
      .filter(entry => entry.isFile() && entry.name.startsWith("accounts-") && entry.name.endsWith(".json"))
      .map(async entry => {
        const filePath = path.join(BACKUP_DIR, entry.name);
        const stat = await fsp.stat(filePath);
        return {
          type: "json",
          name: entry.name,
          size: stat.size,
          createdAt: stat.mtime.toISOString()
        };
      }));
    backups.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    return backups;
  } catch (error) {
    if (error.code === "ENOENT") return [];
    throw error;
  }
}

async function listPostgresBackups() {
  try {
    const entries = await fsp.readdir(POSTGRES_BACKUP_DIR, { withFileTypes: true });
    const backups = await Promise.all(entries
      .filter(entry => entry.isFile() && entry.name.startsWith("betteruc-") && entry.name.endsWith(".dump"))
      .map(async entry => {
        const filePath = path.join(POSTGRES_BACKUP_DIR, entry.name);
        const stat = await fsp.stat(filePath);
        return {
          type: "postgres",
          name: entry.name,
          size: stat.size,
          createdAt: stat.mtime.toISOString()
        };
      }));
    backups.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    return backups;
  } catch (error) {
    if (error.code === "ENOENT") return [];
    throw error;
  }
}

async function listPlatformBackups() {
  const [postgres, jsonBackups] = await Promise.all([
    listPostgresBackups(),
    listStoreBackups()
  ]);
  return [...postgres, ...jsonBackups]
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
}

async function discordSystemSnapshot() {
  let databaseOverview = null;
  let databaseError = null;
  if (persistenceMode === "postgres" && database.enabled) {
    try {
      databaseOverview = await database.getOverview();
    } catch (error) {
      databaseError = error.message || "PostgreSQL nicht erreichbar";
    }
  } else {
    databaseError = "PostgreSQL ist nicht aktiv";
  }
  const backups = await listPlatformBackups().catch(error => {
    console.error("Could not inspect backups for Discord monitoring", error);
    return [];
  });
  return {
    checkedAt: nowIso(),
    uptimeSeconds: Math.floor(process.uptime()),
    memoryBytes: process.memoryUsage().rss,
    persistenceMode,
    database: databaseOverview,
    databaseError,
    backups,
    onlinePlayers: onlinePlayersForResponse(),
    accountCount: store.accounts.length
  };
}

async function cleanupStoreBackups() {
  if (!Number.isFinite(BACKUP_RETENTION_DAYS) || BACKUP_RETENTION_DAYS <= 0) return;
  const backups = await listStoreBackups();
  const cutoff = Date.now() - BACKUP_RETENTION_DAYS * 24 * 60 * 60 * 1000;
  await Promise.all(backups
    .filter(backup => new Date(backup.createdAt).getTime() < cutoff)
    .map(backup => fsp.unlink(path.join(BACKUP_DIR, backup.name)).catch(error => {
      console.error("Could not remove old betterUC backup", backup.name, error);
    })));
}

async function createStoreBackup(reason = "scheduled") {
  await fsp.mkdir(BACKUP_DIR, { recursive: true });
  await saveStore();
  const cleanReason = String(reason || "manual").replace(/[^a-z0-9_-]/gi, "").toLowerCase() || "manual";
  const fileName = `accounts-${backupTimestamp()}-${cleanReason}.json`;
  const filePath = path.join(BACKUP_DIR, fileName);
  await fsp.copyFile(STORE_FILE, filePath);
  await cleanupStoreBackups();
  const stat = await fsp.stat(filePath);
  return {
    type: "json",
    name: fileName,
    size: stat.size,
    createdAt: stat.mtime.toISOString()
  };
}

async function cleanupPostgresBackups() {
  if (!Number.isFinite(BACKUP_RETENTION_DAYS) || BACKUP_RETENTION_DAYS <= 0) return;
  const backups = await listPostgresBackups();
  const cutoff = Date.now() - BACKUP_RETENTION_DAYS * 24 * 60 * 60 * 1000;
  await Promise.all(backups
    .filter(backup => new Date(backup.createdAt).getTime() < cutoff)
    .map(backup => fsp.unlink(path.join(POSTGRES_BACKUP_DIR, backup.name)).catch(error => {
      console.error("Could not remove old PostgreSQL backup", backup.name, error);
    })));
}

async function createPostgresBackup(reason = "scheduled") {
  if (persistenceMode !== "postgres" || !database.enabled) return null;
  const connectionString = String(process.env.DATABASE_URL || "").trim();
  if (!connectionString) throw new Error("DATABASE_URL fehlt für das PostgreSQL-Backup.");

  let backupUrl;
  let backupPassword = "";
  try {
    backupUrl = new URL(connectionString);
    backupPassword = decodeURIComponent(backupUrl.password || "");
    backupUrl.password = "";
  } catch {
    throw new Error("DATABASE_URL ist für das PostgreSQL-Backup ungültig.");
  }

  await fsp.mkdir(POSTGRES_BACKUP_DIR, { recursive: true });
  const cleanReason = String(reason || "manual").replace(/[^a-z0-9_-]/gi, "").toLowerCase() || "manual";
  const fileName = `betteruc-${backupTimestamp()}-${cleanReason}.dump`;
  const filePath = path.join(POSTGRES_BACKUP_DIR, fileName);
  try {
    await execFileAsync(PG_DUMP_BIN, [
      "--dbname", backupUrl.toString(),
      "--format=custom",
      "--no-owner",
      "--no-privileges",
      "--file", filePath
    ], {
      env: {
        ...process.env,
        ...(backupPassword ? { PGPASSWORD: backupPassword } : {})
      },
      timeout: PG_DUMP_TIMEOUT_MS,
      windowsHide: true,
      maxBuffer: 1024 * 1024
    });
  } catch (error) {
    await fsp.unlink(filePath).catch(() => {});
    const detail = String(error.stderr || error.message || "pg_dump fehlgeschlagen").trim();
    throw new Error(`PostgreSQL-Backup fehlgeschlagen: ${detail.slice(0, 300)}`);
  }
  await cleanupPostgresBackups();
  const stat = await fsp.stat(filePath);
  return {
    type: "postgres",
    name: fileName,
    size: stat.size,
    createdAt: stat.mtime.toISOString()
  };
}

async function createPlatformBackup(reason = "scheduled") {
  const postgres = await createPostgresBackup(reason);
  const jsonBackup = await createStoreBackup(reason);
  return { postgres, json: jsonBackup };
}

async function ensureDailyPlatformBackup() {
  const today = backupDateKey();
  const [postgresBackups, jsonBackups] = await Promise.all([
    listPostgresBackups(),
    listStoreBackups()
  ]);
  const result = { postgres: null, json: null };
  if (persistenceMode === "postgres"
      && !postgresBackups.some(backup => backup.name.startsWith(`betteruc-${today}`))) {
    result.postgres = await createPostgresBackup("daily");
  }
  if (!jsonBackups.some(backup => backup.name.startsWith(`accounts-${today}`))) {
    result.json = await createStoreBackup("daily");
  }
  return result;
}

function scheduleStoreBackups() {
  clearInterval(backupTimer);
  ensureDailyPlatformBackup().catch(error => console.error("Could not create betterUC daily backup", error));
  backupTimer = setInterval(() => {
    ensureDailyPlatformBackup().catch(error => console.error("Could not create betterUC daily backup", error));
  }, BACKUP_INTERVAL_MS);
}

function scheduleStoreSave() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    saveStore().catch(error => console.error("Could not save betterUC account store", error));
  }, 300);
}

function tokenHash(token) {
  return crypto.createHash("sha256").update(`${TOKEN_PEPPER}:${token}`).digest("hex");
}

function generateAccessCode() {
  return `buc_${crypto.randomBytes(24).toString("base64url")}`;
}

function passwordHash(password, salt) {
  return crypto.scryptSync(String(password), salt, 64).toString("base64url");
}

function normalizeWebPassword(password) {
  return String(password || "").trim();
}

function isValidPassword(password) {
  const raw = normalizeWebPassword(password);
  return raw.length >= 6 && raw.length <= 72;
}

function setWebPassword(account, password) {
  const normalized = normalizeWebPassword(password);
  const salt = crypto.randomBytes(16).toString("base64url");
  account.webPasswordSalt = salt;
  account.webPasswordHash = passwordHash(normalized, salt);
  account.webPasswordSetAt = nowIso();
}

function clearWebPassword(account) {
  delete account.webPasswordSalt;
  delete account.webPasswordHash;
  delete account.webPasswordSetAt;
  account.webPasswordClearedAt = nowIso();
}

function invalidateWebSessions(account) {
  account.webSessionsInvalidAfter = nowIso();
}

function verifyWebPassword(account, password) {
  if (!account || !account.webPasswordHash || !account.webPasswordSalt) return false;
  const expected = passwordHash(normalizeWebPassword(password), account.webPasswordSalt);
  return constantTimeEquals(expected, account.webPasswordHash);
}

function cleanMinecraftName(value) {
  const raw = String(value || "").trim();
  if (!raw) return "";
  return /^[A-Za-z0-9_]{3,16}$/.test(raw) ? raw : null;
}

function normalizeMinecraftUuid(value) {
  const compact = String(value || "").trim().toLowerCase().replace(/-/g, "");
  if (!/^[0-9a-f]{32}$/.test(compact)) return "";
  return `${compact.slice(0, 8)}-${compact.slice(8, 12)}-${compact.slice(12, 16)}-${compact.slice(16, 20)}-${compact.slice(20)}`;
}

function cleanSmallLabel(value, fallback = "") {
  const raw = String(value || "").trim();
  if (!raw) return fallback;
  return raw.replace(/[^\p{L}\p{N}_ .-]/gu, "").slice(0, 48).trim() || fallback;
}

function normalizeServerId(value) {
  const raw = String(value || "").trim().toLowerCase();
  if (!raw) return "unknown";
  const withoutScheme = raw.replace(/^[a-z]+:\/\//, "");
  const hostPart = withoutScheme.split("/")[0];
  const hostWithoutPort = hostPart.replace(/:\d+$/, "");
  if (hostWithoutPort === "unicacity.eu" || hostWithoutPort.endsWith(".unicacity.eu")) {
    return "unicacity.eu";
  }
  return cleanSmallLabel(hostPart, "unknown").toLowerCase();
}

function cleanStatText(value, fallback = "") {
  const raw = String(value || "").trim();
  if (!raw) return fallback;
  return raw.replace(/[^\p{L}\p{N}_ .,:/+()\\-]/gu, "").slice(0, 96).trim() || fallback;
}

function cleanStatNumber(value) {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return null;
  return Math.max(0, Math.min(999999999, Math.floor(parsed)));
}

function cleanChannel(value) {
  const raw = String(value || "").trim().toLowerCase();
  return raw.replace(/[^a-z0-9_-]/g, "").slice(0, 32) || "global";
}

function cleanDimension(value) {
  const raw = String(value || "").trim().toLowerCase();
  return raw.replace(/[^a-z0-9_:.\\/-]/g, "").slice(0, 80) || "unknown";
}

function cleanPingType(value) {
  const raw = String(value || "").trim().toLowerCase();
  if (raw === "danger" || raw === "gather") return raw;
  return "normal";
}

function cleanStatus(value) {
  return value === "revoked" ? "revoked" : "active";
}

function cleanRole(value) {
  const role = String(value || "").trim().toLowerCase();
  if (role === "admin" || role === "helper" || role === "partner" || role === "vip") return role;
  return "user";
}

function rolePriority(role) {
  const cleaned = cleanRole(role);
  if (cleaned === "admin") return 100;
  if (cleaned === "helper") return 85;
  if (cleaned === "partner") return 80;
  if (cleaned === "vip") return 75;
  return 50;
}

const KNOWN_FACTION_KEYS = new Set([
  "zivilist",
  "polizei",
  "fbi",
  "medic",
  "lcn",
  "ballas",
  "kartell",
  "kerzakov",
  "yakuza",
  "soeldner",
  "news",
  "ordo"
]);

function normalizeFactionKey(value) {
  const folded = String(value || "")
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
  if (!folded) return "";

  if (folded === "kartell" || folded === "calderon kartell" || folded.startsWith("calderon kartell ")) return "kartell";
  if (folded === "zivilist" || folded === "zivi" || folded === "ziv") return "zivilist";
  if (folded === "polizei" || folded.startsWith("polizei ")) return "polizei";
  if (folded === "fbi" || folded === "f b i" || folded.startsWith("fbi ") || folded.startsWith("f b i ")) return "fbi";
  if (folded === "rettungsdienst" || folded === "retungsdienst" || folded === "medic"
    || folded.startsWith("rettungsdienst ") || folded.startsWith("retungsdienst ") || folded.startsWith("medic ")) return "medic";
  if (folded === "la cosa nostra" || folded === "lcn" || folded.startsWith("la cosa nostra ")) return "lcn";
  if (folded === "westside ballas" || folded === "ballas" || folded.startsWith("westside ballas ")) return "ballas";
  if (folded === "soldner" || folded === "soeldner" || folded.startsWith("soldner ") || folded.startsWith("soeldner ")) return "soeldner";
  if (folded === "ordo absolutus" || folded === "ordo" || folded.startsWith("ordo absolutus ")) return "ordo";
  if (folded === "kerzakov" || folded === "kerzakov familie" || folded === "kerzakov family"
    || folded === "kf" || folded === "k f" || folded.startsWith("kerzakov familie ")
    || folded.startsWith("kerzakov family ")) return "kerzakov";
  if (folded === "yakuza" || folded.startsWith("yakuza ")) return "yakuza";
  if (folded === "news" || folded.startsWith("news ")) return "news";
  if (folded === "f b i") return "fbi";
  return folded;
}

function isKnownFaction(value) {
  return KNOWN_FACTION_KEYS.has(normalizeFactionKey(value));
}

function effectiveClientFaction(client) {
  const live = cleanSmallLabel(client && client.faction || "", "");
  if (isKnownFaction(live)) return live;

  const account = client && client.account;
  if (!account) return "";
  const stats = publicStats(account);
  if (isKnownFaction(stats.factionDisplay)) return stats.factionDisplay;
  return isKnownFaction(account.faction) ? account.faction : "";
}

function isStateFaction(value) {
  const faction = normalizeFactionKey(value);
  return faction === "polizei" || faction === "fbi" || faction === "medic";
}

function publicStats(account) {
  const stats = account && account.stats && typeof account.stats === "object" ? account.stats : {};
  const factionDisplay = cleanStatText(stats.factionDisplay || "");
  return {
    bankMoney: cleanStatNumber(stats.bankMoney),
    cashMoney: cleanStatNumber(stats.cashMoney),
    factionDisplay: isKnownFaction(factionDisplay) ? factionDisplay : "",
    houses: cleanStatText(stats.houses || ""),
    loyaltyBonus: cleanStatNumber(stats.loyaltyBonus),
    playTimeHours: cleanStatNumber(stats.playTimeHours),
    votepoints: cleanStatNumber(stats.votepoints),
    warns: cleanStatText(stats.warns || ""),
    updatedAt: stats.updatedAt || null
  };
}

function statsSnapshot(stats) {
  const source = stats && typeof stats === "object" ? stats : {};
  return {
    at: source.updatedAt || nowIso(),
    bankMoney: cleanStatNumber(source.bankMoney),
    cashMoney: cleanStatNumber(source.cashMoney),
    factionDisplay: cleanStatText(source.factionDisplay || ""),
    houses: cleanStatText(source.houses || ""),
    loyaltyBonus: cleanStatNumber(source.loyaltyBonus),
    playTimeHours: cleanStatNumber(source.playTimeHours),
    votepoints: cleanStatNumber(source.votepoints),
    warns: cleanStatText(source.warns || "")
  };
}

function sameStatsSnapshot(left, right) {
  if (!left || !right) return false;
  return left.bankMoney === right.bankMoney
    && left.cashMoney === right.cashMoney
    && left.factionDisplay === right.factionDisplay
    && left.houses === right.houses
    && left.loyaltyBonus === right.loyaltyBonus
    && left.playTimeHours === right.playTimeHours
    && left.votepoints === right.votepoints
    && left.warns === right.warns;
}

function publicStatsHistory(account) {
  const history = Array.isArray(account && account.statsHistory) ? account.statsHistory : [];
  return history
    .slice(-20)
    .map(statsSnapshot)
    .filter(entry => entry.at)
    .sort((a, b) => new Date(b.at) - new Date(a.at));
}

function appendStatsHistory(account, stats) {
  const snapshot = statsSnapshot(stats);
  const history = Array.isArray(account.statsHistory) ? account.statsHistory : [];
  const previous = history[history.length - 1];
  if (sameStatsSnapshot(previous, snapshot)) return;
  account.statsHistory = [...history, snapshot].slice(-20);
}

function onlineClientForAccount(account) {
  if (!account) return null;
  return [...clients].find(client => client.account && client.account.id === account.id) || null;
}

function onlineAccountCount() {
  return store.accounts.filter(account => onlineClientForAccount(account)).length;
}

function clientPresenceKey(client) {
  const accountId = client && client.account && client.account.id;
  if (accountId && accountId !== "legacy") return `account:${accountId}`;
  const uuid = cleanSmallLabel(client && client.uuid || "", "");
  if (uuid) return `uuid:${uuid.toLowerCase()}`;
  return `name:${String(client && client.name || "unknown").toLowerCase()}@${client && client.server || "unknown"}`;
}

function uniqueOnlineClients() {
  const byKey = new Map();
  for (const client of clients) {
    byKey.set(clientPresenceKey(client), client);
  }
  return [...byKey.values()];
}

function publicAccount(account) {
  const stats = publicStats(account);
  return {
    id: account.id,
    minecraftName: account.minecraftName || "",
    minecraftUuid: account.minecraftUuid || "",
    faction: account.faction || "",
    factionDisplay: stats.factionDisplay || account.faction || "",
    role: cleanRole(account.role),
    hasWebPassword: Boolean(account.webPasswordHash),
    tokenPrefix: account.tokenPrefix || "",
    createdAt: account.createdAt,
    lastSeenAt: account.lastSeenAt || null,
    lastStatsAt: account.lastStatsAt || null,
    lastServer: account.lastServer || "",
    lastChannel: account.lastChannel || "",
    lastVersion: account.lastVersion || "",
    lastGameVersion: account.lastGameVersion || "",
    status: account.status || "active",
    lastPanelLoginAt: account.lastPanelLoginAt || null,
    webPasswordSetAt: account.webPasswordSetAt || null,
    webPasswordClearedAt: account.webPasswordClearedAt || null,
    webSessionsInvalidAfter: account.webSessionsInvalidAfter || null
  };
}

function userPanelAccount(account) {
  const stats = publicStats(account);
  const onlineClient = onlineClientForAccount(account);
  return {
    id: account.id,
    minecraftName: account.minecraftName || "",
    minecraftUuid: account.minecraftUuid || "",
    faction: stats.factionDisplay || account.faction || "",
    factionDisplay: stats.factionDisplay || account.faction || "",
    role: cleanRole(account.role),
    status: account.status || "active",
    online: Boolean(onlineClient),
    connectedAt: onlineClient ? onlineClient.connectedAt : null,
    lastSeenAt: account.lastSeenAt || null,
    lastStatsAt: account.lastStatsAt || null,
    lastServer: account.lastServer || "",
    lastChannel: account.lastChannel || "",
    lastVersion: account.lastVersion || "",
    lastGameVersion: account.lastGameVersion || "",
    lastPanelLoginAt: account.lastPanelLoginAt || null,
    tokenPrefix: account.tokenPrefix || "",
    hasWebPassword: Boolean(account.webPasswordHash),
    statsHistory: publicStatsHistory(account),
    stats
  };
}

function adminAccount(account, cloudSettings = null) {
  const onlineClient = onlineClientForAccount(account);
  return {
    ...publicAccount(account),
    discordId: account.discordId || "",
    discordLinkedAt: account.discordLinkedAt || null,
    online: Boolean(onlineClient),
    connectedAt: onlineClient ? onlineClient.connectedAt : null,
    stats: publicStats(account),
    statsHistory: publicStatsHistory(account),
    cloudSettings
  };
}

async function createAccessAccount({ minecraftName, faction = "", role = "user", createdBy = "server" }) {
  const cleanedName = cleanMinecraftName(minecraftName);
  if (cleanedName === null || !cleanedName) {
    throw new Error("Minecraft-Name muss 3-16 Zeichen haben.");
  }

  const existing = findAccountByMinecraftName(cleanedName);
  if (existing) {
    throw new Error("Fuer diesen Minecraft-Namen existiert bereits ein aktiver Account. Nutze reset, wenn der Code verloren ist.");
  }

  const token = generateAccessCode();
  const account = {
    id: crypto.randomUUID(),
    tokenHash: tokenHash(token),
    tokenPrefix: token.slice(0, 10),
    minecraftName: cleanedName,
    minecraftUuid: "",
    faction: cleanSmallLabel(faction || ""),
    role: cleanRole(role),
    status: "active",
    createdAt: nowIso(),
    createdBy: cleanSmallLabel(createdBy || "server", "server"),
    lastSeenAt: null,
    lastServer: "",
    lastChannel: "",
    lastVersion: "",
    lastGameVersion: ""
  };

  store.accounts.push(account);
  await saveStore();
  return {
    accessCode: token,
    account: adminAccount(account)
  };
}

async function resetAccessCodeByMinecraftName(name) {
  const account = findAccountByMinecraftName(name);
  if (!account) {
    throw new Error("Account nicht gefunden.");
  }

  const token = generateAccessCode();
  account.tokenHash = tokenHash(token);
  account.tokenPrefix = token.slice(0, 10);
  account.status = "active";
  account.resetAt = nowIso();
  await saveStore();
  return {
    accessCode: token,
    account: adminAccount(account)
  };
}

async function revokeAccountByMinecraftName(name) {
  const account = findAccountByMinecraftName(name);
  if (!account) {
    throw new Error("Account nicht gefunden.");
  }

  account.status = "revoked";
  account.revokedAt = nowIso();
  closeConnectionsForAccount(account.id, "account_revoked");
  await saveStore();
  discordBot.notifyStateChanged();
  return adminAccount(account);
}

async function linkDiscordAccountByCode(accessCode, discordId) {
  const account = findAccountByToken(accessCode);
  if (!account || cleanStatus(account.status) !== "active") {
    throw new Error("Access-Code ungueltig oder gesperrt.");
  }

  const existing = store.accounts.find(entry =>
    entry.id !== account.id
    && cleanStatus(entry.status) === "active"
    && String(entry.discordId || "") === String(discordId || "")
  );
  if (existing) {
    throw new Error("Dieser Discord-Account ist bereits mit einem anderen betterUC Account verbunden.");
  }

  account.discordId = cleanSmallLabel(discordId || "", "");
  account.discordLinkedAt = nowIso();
  await saveStore();
  return adminAccount(account);
}

async function unlinkDiscordAccount(discordId) {
  const account = store.accounts.find(entry =>
    cleanStatus(entry.status) === "active"
    && String(entry.discordId || "") === String(discordId || "")
  );
  if (!account) {
    throw new Error("Dein Discord-Account ist mit keinem betterUC Account verbunden.");
  }

  delete account.discordId;
  account.discordUnlinkedAt = nowIso();
  await saveStore();
  return adminAccount(account);
}

function findAccountByDiscordId(discordId) {
  const account = store.accounts.find(entry =>
    cleanStatus(entry.status) === "active"
    && String(entry.discordId || "") === String(discordId || "")
  );
  return account ? adminAccount(account) : null;
}

function findAccountById(id) {
  return store.accounts.find(account => account.id === id) || null;
}

function findAccountByMinecraftName(name) {
  const cleaned = cleanMinecraftName(name);
  if (!cleaned) return null;
  const lower = cleaned.toLowerCase();
  return store.accounts.find(account =>
    cleanStatus(account.status) === "active"
    && String(account.minecraftName || "").toLowerCase() === lower
  ) || null;
}

function findAccountByMinecraftUuid(uuid, includeRevoked = false) {
  const normalized = normalizeMinecraftUuid(uuid);
  if (!normalized) return null;
  const matches = store.accounts.filter(account =>
    (includeRevoked || cleanStatus(account.status) === "active")
    && normalizeMinecraftUuid(account.minecraftUuid) === normalized
  );
  return selectPreferredAccount(matches, cloudRevisionByAccountId);
}

function findAccountByMinecraftLogin(name, password) {
  const cleaned = cleanMinecraftName(name);
  if (!cleaned) return null;
  const lower = cleaned.toLowerCase();
  return store.accounts.find(account =>
    cleanStatus(account.status) === "active"
    && String(account.minecraftName || "").toLowerCase() === lower
    && verifyWebPassword(account, password)
  ) || null;
}

function closeConnectionsForAccount(accountId, reason = "account_removed") {
  for (const client of clients) {
    if (!client.account || client.account.id !== accountId) continue;
    try {
      client.ws.close(1008, reason);
    } catch {
      client.ws.terminate();
    }
    clients.delete(client);
  }
}

function findAccountByToken(token) {
  if (!token) return null;
  const hash = tokenHash(token);
  return store.accounts.find(account => account.tokenHash === hash && account.status !== "revoked") || null;
}

function tokenFromRequest(req, url) {
  const headerToken = req.headers["x-betteruc-token"];
  if (typeof headerToken === "string" && headerToken.trim()) return headerToken.trim();

  const auth = req.headers.authorization;
  if (typeof auth === "string" && auth.toLowerCase().startsWith("bearer ")) {
    return auth.slice("bearer ".length).trim();
  }

  const queryToken = url.searchParams.get("token");
  return queryToken ? queryToken.trim() : "";
}

function adminTokenFromRequest(req, url) {
  const headerToken = req.headers["x-betteruc-admin"];
  if (typeof headerToken === "string" && headerToken.trim()) return headerToken.trim();

  const auth = req.headers.authorization;
  if (typeof auth === "string" && auth.toLowerCase().startsWith("bearer ")) {
    return auth.slice("bearer ".length).trim();
  }

  const queryToken = url.searchParams.get("adminKey");
  return queryToken ? queryToken.trim() : "";
}

function constantTimeEquals(left, right) {
  const leftBuffer = Buffer.from(String(left || ""));
  const rightBuffer = Buffer.from(String(right || ""));
  if (leftBuffer.length !== rightBuffer.length) return false;
  return crypto.timingSafeEqual(leftBuffer, rightBuffer);
}

function signSessionPayload(payload) {
  return crypto.createHmac("sha256", SESSION_SECRET).update(payload).digest("base64url");
}

function createModSession(account) {
  const issuedAt = Date.now();
  const expiresAt = issuedAt + MOD_SESSION_TTL_MS;
  const payload = Buffer.from(JSON.stringify({
    sub: account.id,
    aud: "betteruc-mod",
    uuid: normalizeMinecraftUuid(account.minecraftUuid),
    name: account.minecraftName || "",
    iat: issuedAt,
    exp: expiresAt
  })).toString("base64url");
  return {
    token: `${payload}.${signSessionPayload(payload)}`,
    expiresAt
  };
}

function verifyModSession(token) {
  const raw = String(token || "").trim();
  const dot = raw.indexOf(".");
  if (dot <= 0) return null;

  const payload = raw.slice(0, dot);
  const signature = raw.slice(dot + 1);
  if (!constantTimeEquals(signature, signSessionPayload(payload))) return null;

  try {
    const data = JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
    if (!data || data.aud !== "betteruc-mod" || data.exp < Date.now()) return null;
    const account = findAccountById(data.sub);
    if (!account || cleanStatus(account.status) !== "active") return null;
    const accountUuid = normalizeMinecraftUuid(account.minecraftUuid);
    if (!accountUuid || accountUuid !== normalizeMinecraftUuid(data.uuid)) return null;
    // Access-code based installations could leave multiple records for one
    // Minecraft UUID. Keep an already verified session on the established
    // active identity so cloud data and linked services remain together.
    return findAccountByMinecraftUuid(accountUuid) || account;
  } catch {
    return null;
  }
}

function createUserSession(account) {
  const payload = Buffer.from(JSON.stringify({
    sub: account.id,
    name: account.minecraftName || "",
    iat: Date.now(),
    exp: Date.now() + USER_SESSION_TTL_MS
  })).toString("base64url");
  return `${payload}.${signSessionPayload(payload)}`;
}

function sessionTokenFromRequest(req, url) {
  const headerToken = req.headers["x-betteruc-session"];
  if (typeof headerToken === "string" && headerToken.trim()) return headerToken.trim();

  const auth = req.headers.authorization;
  if (typeof auth === "string" && auth.toLowerCase().startsWith("bearer ")) {
    return auth.slice("bearer ".length).trim();
  }

  const queryToken = url.searchParams.get("session");
  return queryToken ? queryToken.trim() : "";
}

function verifyUserSession(token) {
  const raw = String(token || "").trim();
  const dot = raw.indexOf(".");
  if (dot <= 0) return null;

  const payload = raw.slice(0, dot);
  const signature = raw.slice(dot + 1);
  if (!constantTimeEquals(signature, signSessionPayload(payload))) return null;

  try {
    const data = JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
    if (!data || data.exp < Date.now()) return null;
    const account = findAccountById(data.sub);
    if (!account || cleanStatus(account.status) !== "active") return null;
    const invalidAfter = Date.parse(account.webSessionsInvalidAfter || "");
    if (Number.isFinite(invalidAfter)) {
      const issuedAt = Number(data.iat || 0);
      if (!issuedAt || issuedAt < invalidAfter) return null;
    }
    return account;
  } catch {
    return null;
  }
}

function requireUserSession(req, res, url) {
  const account = verifyUserSession(sessionTokenFromRequest(req, url));
  if (!account) {
    json(res, 401, { ok: false, error: "Login abgelaufen oder ungueltig." });
    return null;
  }
  return account;
}

function requireAdmin(req, res, url) {
  const sessionAccount = verifyUserSession(sessionTokenFromRequest(req, url));
  if (sessionAccount && cleanRole(sessionAccount.role) === "admin") {
    return true;
  }

  if (!ADMIN_KEY) {
    json(res, 503, { ok: false, error: "Admin-Zugriff ist nicht konfiguriert." });
    return false;
  }

  if (!constantTimeEquals(adminTokenFromRequest(req, url), ADMIN_KEY)) {
    json(res, 401, { ok: false, error: "Admin-Key fehlt oder ist ungueltig." });
    return false;
  }

  return true;
}

function authenticate(req, url) {
  const token = tokenFromRequest(req, url);
  if (!token) return null;

  const sessionAccount = verifyModSession(token);
  if (sessionAccount) {
    return {
      type: "session",
      role: cleanRole(sessionAccount.role),
      account: sessionAccount
    };
  }

  if (ALLOW_LEGACY_TOKEN && LEGACY_RELAY_TOKEN && token === LEGACY_RELAY_TOKEN) {
    return {
      type: "legacy",
      role: "user",
      account: {
        id: "legacy",
        minecraftName: cleanMinecraftName(url.searchParams.get("name")) || "",
        tokenPrefix: "legacy"
      }
    };
  }

  const account = findAccountByToken(token);
  if (account) {
    return {
      type: "access",
      role: cleanRole(account.role),
      account
    };
  }

  return null;
}

function pruneLoginChallenges() {
  const now = Date.now();
  for (const [id, challenge] of loginChallenges) {
    if (!challenge || challenge.expiresAt <= now) loginChallenges.delete(id);
  }
}

function createLoginChallenge(name, uuid) {
  pruneLoginChallenges();
  const challenge = {
    id: crypto.randomBytes(24).toString("base64url"),
    serverId: crypto.randomBytes(20).toString("hex"),
    name,
    uuid,
    expiresAt: Date.now() + AUTH_CHALLENGE_TTL_MS
  };
  loginChallenges.set(challenge.id, challenge);
  return challenge;
}

async function verifyMojangJoin(challenge) {
  const target = new URL(MOJANG_HAS_JOINED_URL);
  target.searchParams.set("username", challenge.name);
  target.searchParams.set("serverId", challenge.serverId);
  const response = await fetch(target, {
    headers: { accept: "application/json", "user-agent": "betterUC-platform/1.0" },
    signal: AbortSignal.timeout(8000)
  });
  if (response.status === 204 || response.status === 404) return null;
  if (!response.ok) throw new Error(`Mojang session server returned HTTP ${response.status}`);
  const profile = await response.json();
  const name = cleanMinecraftName(profile && profile.name);
  const uuid = normalizeMinecraftUuid(profile && profile.id);
  if (!name || !uuid) return null;
  return { name, uuid };
}

async function accountForVerifiedMinecraftProfile(profile, clientInfo = {}) {
  let account = findAccountByMinecraftUuid(profile.uuid, true);
  if (account && cleanStatus(account.status) === "revoked") {
    throw Object.assign(new Error("Dieser betterUC-Account ist gesperrt."), { statusCode: 403 });
  }

  if (!account) {
    const normalizedName = String(profile.name || "").toLowerCase();
    const nameMatches = store.accounts.filter(candidate =>
      cleanStatus(candidate.status) === "active"
      && String(candidate.minecraftName || "").toLowerCase() === normalizedName
      && !normalizeMinecraftUuid(candidate.minecraftUuid)
    );
    account = selectPreferredAccount(nameMatches, cloudRevisionByAccountId);
  }

  if (!account) {
    account = {
      id: crypto.randomUUID(),
      minecraftName: profile.name,
      minecraftUuid: profile.uuid,
      faction: "",
      role: "user",
      status: "active",
      createdAt: nowIso(),
      createdBy: "minecraft-session",
      lastSeenAt: null,
      lastServer: "",
      lastChannel: "",
      lastVersion: "",
      lastGameVersion: ""
    };
    store.accounts.push(account);
  }

  account.minecraftName = profile.name;
  account.minecraftUuid = profile.uuid;
  account.activatedAt = account.activatedAt || nowIso();
  account.lastSeenAt = nowIso();
  if (clientInfo.version) account.lastVersion = cleanSmallLabel(clientInfo.version, "");
  if (clientInfo.gameVersion) account.lastGameVersion = cleanSmallLabel(clientInfo.gameVersion, "");
  await saveStore();
  return account;
}

function mergeStats(account, incoming) {
  const previous = account.stats && typeof account.stats === "object" ? account.stats : {};
  const next = { ...previous };
  const source = incoming && typeof incoming === "object" ? incoming : {};
  let changed = false;

  const setStat = (key, value) => {
    if (next[key] !== value) changed = true;
    next[key] = value;
  };

  if (Object.hasOwn(source, "bankMoney")) setStat("bankMoney", cleanStatNumber(source.bankMoney));
  if (Object.hasOwn(source, "cashMoney")) setStat("cashMoney", cleanStatNumber(source.cashMoney));
  if (Object.hasOwn(source, "factionDisplay")) {
    const factionDisplay = cleanStatText(source.factionDisplay || "");
    if (isKnownFaction(factionDisplay)) setStat("factionDisplay", factionDisplay);
  }
  if (Object.hasOwn(source, "houses")) setStat("houses", cleanStatText(source.houses || ""));
  if (Object.hasOwn(source, "loyaltyBonus")) setStat("loyaltyBonus", cleanStatNumber(source.loyaltyBonus));
  if (Object.hasOwn(source, "playTimeHours")) setStat("playTimeHours", cleanStatNumber(source.playTimeHours));
  if (Object.hasOwn(source, "votepoints")) setStat("votepoints", cleanStatNumber(source.votepoints));
  if (Object.hasOwn(source, "warns")) setStat("warns", cleanStatText(source.warns || ""));

  next.updatedAt = nowIso();
  account.stats = next;
  account.lastStatsAt = next.updatedAt;
  if (changed) {
    appendStatsHistory(account, next);
  }
}

function updateAccountFromClient(account, info) {
  if (!account || account.id === "legacy") return;

  if (info.authType !== "session") {
    if (info.name) account.minecraftName = info.name;
    if (info.uuid) account.minecraftUuid = info.uuid;
  }
  if (info.server) account.lastServer = info.server;
  if (info.channel) account.lastChannel = info.channel;
  if (info.version) account.lastVersion = info.version;
  if (info.gameVersion) account.lastGameVersion = info.gameVersion;
  if (info.faction) account.faction = info.faction;
  account.lastSeenAt = nowIso();
  scheduleStoreSave();
}

async function readJsonBody(req, maxBytes = 32768) {
  const chunks = [];
  let length = 0;
  for await (const chunk of req) {
    length += chunk.length;
    if (length > maxBytes) {
      const error = new Error("body_too_large");
      error.statusCode = 413;
      throw error;
    }
    chunks.push(chunk);
  }
  if (chunks.length === 0) return {};
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

async function readRawBody(req, maxBytes) {
  const chunks = [];
  let length = 0;
  for await (const chunk of req) {
    length += chunk.length;
    if (length > maxBytes) {
      const error = new Error("body_too_large");
      error.statusCode = 413;
      throw error;
    }
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

function onlinePlayersForResponse() {
  return uniqueOnlineClients().map(client => ({
    name: client.name,
    uuid: client.uuid,
    server: client.server,
    channel: client.channel,
    faction: effectiveClientFaction(client),
    version: client.version || (client.account && client.account.lastVersion) || "",
    gameVersion: client.gameVersion || (client.account && client.account.lastGameVersion) || "",
    role: client.role,
    priority: client.priority,
    admin: client.role === "admin",
    connectedAt: client.connectedAt,
    accountId: client.account && client.account.id !== "legacy" ? client.account.id : null,
    verified: client.authType !== "legacy"
  }));
}

function presencePlayersForServer(server) {
  return uniqueOnlineClients()
    .filter(client => client.server === server)
    .map(client => ({
      name: client.name,
      uuid: client.uuid,
      role: client.role,
      priority: client.priority,
      admin: client.role === "admin"
    }));
}

function sendPresence(client) {
  if (!client || client.ws.readyState !== client.ws.OPEN) return;
  client.ws.send(JSON.stringify({
    type: "presence",
    server: client.server,
    players: presencePlayersForServer(client.server)
  }));
}

function broadcastPresence(server) {
  for (const client of clients) {
    if (client.server !== server) continue;
    sendPresence(client);
  }
  discordBot.notifyStateChanged();
}

function absolutePublicUrl(req, pathname) {
  const forwardedProto = req.headers["x-forwarded-proto"];
  const forwardedHost = req.headers["x-forwarded-host"];
  const proto = typeof forwardedProto === "string" && forwardedProto.trim()
    ? forwardedProto.split(",")[0].trim()
    : "https";
  const host = typeof forwardedHost === "string" && forwardedHost.trim()
    ? forwardedHost.split(",")[0].trim()
    : req.headers.host;
  const base = host ? `${proto}://${host}` : PUBLIC_BASE_URL;
  return `${base.replace(/\/+$/, "")}${pathname}`;
}

function cleanScreenshotName(value) {
  const cleaned = path.basename(String(value || "screenshot.png"))
    .replace(/[^a-zA-Z0-9._-]+/g, "-")
    .slice(0, 96);
  return cleaned.toLowerCase().endsWith(".png") ? cleaned : `${cleaned || "screenshot"}.png`;
}

function isPng(buffer) {
  return buffer.length >= 8
    && buffer[0] === 0x89
    && buffer[1] === 0x50
    && buffer[2] === 0x4e
    && buffer[3] === 0x47
    && buffer[4] === 0x0d
    && buffer[5] === 0x0a
    && buffer[6] === 0x1a
    && buffer[7] === 0x0a;
}

function screenshotPath(storageName) {
  return path.join(SCREENSHOT_DIR, path.basename(String(storageName || "")));
}

async function removeScreenshotUpload(upload) {
  if (!upload) return;
  try {
    await fsp.unlink(screenshotPath(upload.storageName));
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
  await database.markScreenshotUploadDeleted(upload.id);
}

function screenshotGalleryItem(upload) {
  return {
    id: upload.id,
    originalName: upload.originalName,
    byteSize: upload.byteSize,
    createdAt: upload.createdAt,
    expiresAt: upload.expiresAt,
    url: `/s/${upload.id}`
  };
}

async function cleanupExpiredScreenshots() {
  if (!database.enabled) return;
  for (;;) {
    const expired = await database.listExpiredScreenshotUploads(100);
    if (expired.length === 0) return;
    for (const upload of expired) {
      try {
        await removeScreenshotUpload(upload);
      } catch (error) {
        console.warn("Could not remove expired screenshot", upload.id, error.message);
      }
    }
    if (expired.length < 100) return;
  }
}

async function handleScreenshotUpload(req, res, url) {
  const auth = authenticate(req, url);
  if (!auth || !auth.account || auth.account.id === "legacy") {
    json(res, 401, { ok: false, error: "betterUC-Anmeldung fehlt oder ist ungueltig." });
    return;
  }
  if (!database.enabled) {
    json(res, 503, { ok: false, error: "Screenshot-Upload ist gerade nicht verfuegbar." });
    return;
  }
  const contentType = String(req.headers["content-type"] || "").split(";", 1)[0].trim().toLowerCase();
  if (contentType !== "image/png") {
    json(res, 415, { ok: false, error: "Nur PNG-Screenshots werden unterstuetzt." });
    return;
  }

  const image = await readRawBody(req, SCREENSHOT_MAX_BYTES);
  if (!isPng(image)) {
    json(res, 400, { ok: false, error: "Die Datei ist kein gueltiger PNG-Screenshot." });
    return;
  }

  await fsp.mkdir(SCREENSHOT_DIR, { recursive: true });
  const id = crypto.randomBytes(18).toString("base64url");
  const storageName = `${id}.png`;
  const originalName = cleanScreenshotName(req.headers["x-screenshot-name"]);
  const expiresAt = new Date(Date.now() + SCREENSHOT_TTL_MS).toISOString();
  const target = screenshotPath(storageName);
  await fsp.writeFile(target, image, { flag: "wx" });
  try {
    await database.createScreenshotUpload({
      id,
      accountId: auth.account.id,
      originalName,
      storageName,
      byteSize: image.length,
      expiresAt
    });
  } catch (error) {
    await fsp.unlink(target).catch(() => {});
    throw error;
  }

  json(res, 201, {
    ok: true,
    url: `${SCREENSHOT_PUBLIC_BASE_URL}/s/${id}`,
    expiresAt,
    expiresInDays: Math.round(SCREENSHOT_TTL_MS / (24 * 60 * 60 * 1000))
  });
}

function cleanBugText(value, maxLength) {
  return String(value || "")
    .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, "")
    .trim()
    .slice(0, maxLength);
}

function cleanBugScreenshotUrl(value) {
  const raw = cleanBugText(value, 512);
  if (!raw) return "";
  try {
    const parsed = new URL(raw);
    const expected = new URL(SCREENSHOT_PUBLIC_BASE_URL);
    if (parsed.origin !== expected.origin || !/^\/s\/[a-zA-Z0-9_-]{20,64}$/.test(parsed.pathname)) return "";
    return parsed.toString();
  } catch {
    return "";
  }
}

async function handleBugReport(req, res, url) {
  const auth = authenticate(req, url);
  if (!auth || !auth.account || auth.account.id === "legacy") {
    json(res, 401, { ok: false, error: "betterUC-Anmeldung fehlt oder ist ungueltig." });
    return;
  }
  if (isRateLimited(req, `bug-report:${auth.account.id}`, 5, 15 * 60 * 1000)) {
    json(res, 429, { ok: false, error: "Zu viele Bugmeldungen. Bitte warte einige Minuten." });
    return;
  }

  const body = await readJsonBody(req, 256 * 1024);
  const title = cleanBugText(body.title, 100);
  const description = cleanBugText(body.description, 3000);
  const steps = cleanBugText(body.steps, 2000);
  const logExcerpt = cleanBugText(body.logExcerpt, 96 * 1024);
  if (title.length < 5 || description.length < 10) {
    json(res, 400, { ok: false, error: "Titel und Beschreibung sind zu kurz." });
    return;
  }

  try {
    const result = await discordBot.createBugReport({
      reporterName: cleanBugText(auth.account.minecraftName, 32) || "Unbekannt",
      title,
      description,
      steps,
      screenshotUrl: cleanBugScreenshotUrl(body.screenshotUrl),
      logExcerpt,
      modVersion: cleanBugText(body.modVersion, 40),
      gameVersion: cleanBugText(body.gameVersion, 40),
      clientName: cleanBugText(body.clientName, 80)
    });
    await recordDiscordActivity("bug.minecraft", auth.account.id, { threadId: result.threadId, title });
    json(res, 201, { ok: true, threadId: result.threadId, url: result.url });
  } catch (error) {
    console.error("Could not create Discord bug report", error);
    json(res, 503, { ok: false, error: "Das Discord Bug-Forum ist gerade nicht erreichbar." });
  }
}

async function handleSharedScreenshot(req, res, id) {
  if (!database.enabled || !/^[a-zA-Z0-9_-]{20,64}$/.test(id)) {
    text(res, 404, "Screenshot nicht gefunden");
    return;
  }
  const upload = await database.getScreenshotUpload(id);
  if (!upload) {
    text(res, 404, "Screenshot nicht gefunden");
    return;
  }
  if (!upload.expiresAt || Date.parse(upload.expiresAt) <= Date.now()) {
    await removeScreenshotUpload(upload);
    text(res, 410, "Dieser Screenshot-Link ist abgelaufen");
    return;
  }

  try {
    const stat = await fsp.stat(screenshotPath(upload.storageName));
    const maxAge = Math.max(0, Math.floor((Date.parse(upload.expiresAt) - Date.now()) / 1000));
    res.writeHead(200, {
      "content-type": "image/png",
      "content-length": stat.size,
      "content-disposition": `${new URL(req.url, 'http://localhost').searchParams.get('download') === '1' ? 'attachment' : 'inline'}; filename="${cleanScreenshotName(upload.originalName)}"`,
      "cache-control": `public, max-age=${maxAge}, immutable`,
      "x-content-type-options": "nosniff"
    });
    fs.createReadStream(screenshotPath(upload.storageName)).pipe(res);
  } catch (error) {
    if (error.code === "ENOENT") {
      await database.markScreenshotUploadDeleted(upload.id);
      text(res, 404, "Screenshot nicht gefunden");
      return;
    }
    throw error;
  }
}

function normalizeReleaseVersion(value) {
  return String(value || "").trim().replace(/^v/i, "");
}

function releaseTargetForMinecraftVersion(value) {
  const raw = String(value || "").trim().toLowerCase();
  if (raw.startsWith("26.")) return "mc26.x";
  return raw ? "" : "mc26.x";
}

function cleanReleaseTarget(value, fallback = "mc26.x") {
  const raw = String(value || "").trim().toLowerCase();
  if (raw === "mc26.x" || raw === "mc26x" || raw === "26" || raw === "26.x" || raw.startsWith("26.")) {
    return "mc26.x";
  }
  return raw ? "" : fallback;
}

function releaseTargetFromRequest(url) {
  const pathname = String(url && url.pathname || "").toLowerCase();
  if (pathname.includes("mc26")) {
    return "mc26.x";
  }

  const explicit = url.searchParams.get("target") || url.searchParams.get("platform");
  if (explicit) return cleanReleaseTarget(explicit, "");
  return releaseTargetForMinecraftVersion(url.searchParams.get("mc") || url.searchParams.get("minecraft") || "");
}

function releaseDownloadPathForTarget() {
  return "/download/latest-mc26.x.jar";
}

function isBetterUcJarAsset(name, url) {
  const value = `${name || ""} ${url || ""}`.toLowerCase();
  return value.includes("betteruc")
    && value.endsWith(".jar")
    && !value.includes("sources")
    && !value.includes("dev")
    && !value.includes("-all");
}

function releaseAssetValue(asset) {
  return `${asset && asset.name || ""} ${asset && asset.url || ""}`.toLowerCase();
}

function releaseAssetMatchesTarget(asset, target) {
  const value = releaseAssetValue(asset);
  if (cleanReleaseTarget(target, "") !== "mc26.x") return false;
  return value.includes("mc26.x")
    || value.includes("mc26x")
    || value.includes("mc26-")
    || value.includes("mc26_")
    || (value.includes("mc26") && !value.includes("mc1.21") && !value.includes("1.21.10"));
}

function releaseAssetHasTargetMarker(asset) {
  const value = releaseAssetValue(asset);
  return value.includes("mc26")
    || value.includes("mc1.21")
    || value.includes("mc1_21")
    || value.includes("1.21.10")
    || value.includes("1.21.11");
}

function releaseAssetForTarget(release, target) {
  const assets = Array.isArray(release && release.assets) ? release.assets : [];
  const cleanedTarget = cleanReleaseTarget(target, "");
  if (cleanedTarget !== "mc26.x") return null;
  return assets.find(asset => releaseAssetMatchesTarget(asset, cleanedTarget))
    || assets.find(asset => !releaseAssetHasTargetMarker(asset))
    || null;
}

function releaseAvailableTargets(release) {
  return releaseAssetForTarget(release, "mc26.x") ? ["mc26.x"] : [];
}

function releaseResponse(release, req, target = "mc26.x") {
  const cleanedTarget = cleanReleaseTarget(target, "");
  const asset = releaseAssetForTarget(release, cleanedTarget);
  const downloadPath = releaseDownloadPathForTarget(cleanedTarget);
  return {
    ok: true,
    version: normalizeReleaseVersion(release.tagName),
    tagName: release.tagName,
    name: release.name || release.tagName,
    body: release.body || "",
    publishedAt: release.publishedAt || null,
    htmlUrl: release.htmlUrl || GITHUB_RELEASES_URL,
    downloadPage: absolutePublicUrl(req, "/download"),
    downloadUrl: asset ? absolutePublicUrl(req, downloadPath) : "",
    target: cleanedTarget,
    availableTargets: releaseAvailableTargets(release),
    assetName: asset ? asset.name : "",
    assetSize: asset ? asset.size : 0,
    sha256: asset ? normalizeReleaseDigest(asset.digest) : ""
  };
}

function normalizeReleaseDigest(value) {
  const normalized = String(value || "").trim().toLowerCase().replace(/^sha256:/, "");
  return /^[0-9a-f]{64}$/.test(normalized) ? normalized : "";
}

async function fetchLatestRelease() {
  const now = Date.now();
  if (latestReleaseCache.release && now - latestReleaseCache.fetchedAt < RELEASE_CACHE_TTL_MS) {
    return latestReleaseCache.release;
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 7000);
  const response = await fetch(GITHUB_LATEST_RELEASE_API, {
    signal: controller.signal,
    headers: {
      "Accept": "application/vnd.github+json",
      "User-Agent": "betterUC-download-page"
    }
  }).finally(() => clearTimeout(timeout));
  if (!response.ok) {
    throw new Error(`GitHub release API HTTP ${response.status}`);
  }

  const data = await response.json();
  const assets = Array.isArray(data.assets) ? data.assets : [];
  const jarAssets = assets
    .filter(asset => isBetterUcJarAsset(asset.name, asset.browser_download_url))
    .map(asset => ({
      name: String(asset.name || ""),
      url: String(asset.browser_download_url || ""),
      size: Number(asset.size || 0),
      digest: String(asset.digest || "")
    }));
  const defaultAsset = releaseAssetForTarget({ assets: jarAssets }, "mc26.x");
  const release = {
    tagName: String(data.tag_name || data.name || "").trim(),
    name: String(data.name || data.tag_name || "").trim(),
    body: String(data.body || ""),
    publishedAt: data.published_at || null,
    htmlUrl: String(data.html_url || GITHUB_RELEASES_URL),
    assets: jarAssets,
    assetName: defaultAsset ? defaultAsset.name : "",
    assetUrl: defaultAsset ? defaultAsset.url : "",
    assetSize: defaultAsset ? defaultAsset.size : 0
  };

  if (!release.tagName) {
    throw new Error("Latest release has no tag name");
  }

  latestReleaseCache = { fetchedAt: now, release };
  return release;
}

function versionParts(value) {
  return normalizeReleaseVersion(value)
    .split(/[.-]/)
    .map(part => {
      const match = String(part || "").match(/^\d+/);
      return match ? Number(match[0]) : 0;
    });
}

function isReleaseNewer(currentVersion, remoteVersion) {
  const current = versionParts(currentVersion);
  const remote = versionParts(remoteVersion);
  const length = Math.max(current.length, remote.length);
  for (let index = 0; index < length; index++) {
    const currentPart = current[index] || 0;
    const remotePart = remote[index] || 0;
    if (remotePart !== currentPart) return remotePart > currentPart;
  }
  return false;
}

function sendUpdateAvailable(client, release) {
  if (!client || client.ws.readyState !== client.ws.OPEN || !release) return;
  const version = normalizeReleaseVersion(release.tagName);
  if (!version || !isReleaseNewer(client.version, version)) return;
  client.ws.send(JSON.stringify({
    type: "update_available",
    version,
    pageUrl: `${PUBLIC_BASE_URL}/download`,
    publishedAt: release.publishedAt || null
  }));
}

function uniqueAccountsByMinecraftIdentity(accounts) {
  const unique = new Map();
  for (const account of accounts) {
    if (!account || cleanStatus(account.status) !== "active") continue;
    const uuid = normalizeMinecraftUuid(account.minecraftUuid);
    if (!uuid) continue;
    unique.set(uuid, account);
  }
  return [...unique.values()];
}

function versionDistribution(entries, versionSelector) {
  const counts = new Map();
  for (const entry of entries) {
    const version = cleanSmallLabel(versionSelector(entry) || "", "unbekannt");
    counts.set(version, (counts.get(version) || 0) + 1);
  }
  return [...counts.entries()]
    .map(([version, count]) => ({ version, count }))
    .sort((left, right) => right.count - left.count || left.version.localeCompare(right.version));
}

function activitySummary() {
  const now = Date.now();
  const knownAccounts = uniqueAccountsByMinecraftIdentity(store.accounts);
  const activeSince = duration => knownAccounts.filter(account => {
    const lastSeen = Date.parse(account.lastSeenAt || "");
    return Number.isFinite(lastSeen) && now - lastSeen <= duration;
  });
  const active24h = activeSince(24 * 60 * 60 * 1000);
  const active7d = activeSince(7 * 24 * 60 * 60 * 1000);
  const onlinePlayers = onlinePlayersForResponse();
  return {
    online: onlinePlayers.length,
    active24h: active24h.length,
    active7d: active7d.length,
    known: knownAccounts.length,
    versionsOnline: versionDistribution(onlinePlayers, entry => entry.version),
    versions7d: versionDistribution(active7d, entry => entry.lastVersion)
  };
}

function broadcastUpdateAvailable(release) {
  for (const client of clients) {
    sendUpdateAvailable(client, release);
  }
}

async function notifyClientAboutLatestRelease(client) {
  try {
    sendUpdateAvailable(client, await fetchLatestRelease());
  } catch (error) {
    console.warn("Could not check latest release for connected client", error.message);
  }
}

async function pollLatestRelease() {
  try {
    const release = await fetchLatestRelease();
    const version = normalizeReleaseVersion(release.tagName);
    if (!version) return;
    if (!latestKnownReleaseVersion) {
      latestKnownReleaseVersion = version;
      return;
    }
    if (isReleaseNewer(latestKnownReleaseVersion, version)) {
      latestKnownReleaseVersion = version;
      broadcastUpdateAvailable(release);
      console.log(`Broadcasted betterUC update ${version} to connected clients`);
    }
  } catch (error) {
    console.warn("Could not poll latest betterUC release", error.message);
  }
}

async function startReleaseWatcher() {
  await pollLatestRelease();
  updateWatchTimer = setInterval(() => {
    pollLatestRelease().catch(error => {
      console.warn("Could not poll latest betterUC release", error.message);
    });
  }, UPDATE_WATCH_INTERVAL_MS);
  if (typeof updateWatchTimer.unref === "function") updateWatchTimer.unref();
}

async function handleLatestJarDownload(req, res) {
  if (req.method !== "GET" && req.method !== "HEAD") {
    text(res, 405, "Method not allowed");
    return;
  }

  let release;
  try {
    release = await fetchLatestRelease();
  } catch (error) {
    console.error("Could not resolve latest betterUC release", error);
    text(res, 502, "Latest release is currently not available");
    return;
  }

  const target = releaseTargetFromRequest(new URL(req.url || "/", `http://${req.headers.host || "localhost"}`));
  if (target !== "mc26.x") {
    text(res, 404, "Only Minecraft 26.x is supported");
    return;
  }
  const asset = releaseAssetForTarget(release, target);
  if (!asset || !asset.url) {
    text(res, 404, "No betterUC jar asset found in latest release");
    return;
  }

  if (req.method === "HEAD") {
    res.writeHead(200, {
      "content-type": "application/java-archive",
      "content-disposition": `attachment; filename="${asset.name || `betterUC-${normalizeReleaseVersion(release.tagName)}.jar`}"`,
      "cache-control": "no-cache"
    });
    res.end();
    return;
  }

  try {
    const upstream = await fetch(asset.url, {
      headers: { "User-Agent": "betterUC-download-proxy" }
    });
    if (!upstream.ok || !upstream.body) {
      throw new Error(`Release asset HTTP ${upstream.status}`);
    }

    const headers = {
      "content-type": upstream.headers.get("content-type") || "application/java-archive",
      "content-disposition": `attachment; filename="${asset.name || `betterUC-${normalizeReleaseVersion(release.tagName)}.jar`}"`,
      "cache-control": "no-cache"
    };
    const length = upstream.headers.get("content-length");
    if (length) headers["content-length"] = length;

    res.writeHead(200, headers);
    Readable.fromWeb(upstream.body).pipe(res);
  } catch (error) {
    console.error("Could not stream latest betterUC jar", error);
    if (!res.headersSent) {
      text(res, 502, "Download is currently not available");
    } else {
      res.destroy(error);
    }
  }
}

async function handleApi(req, res, url) {
  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET,POST,PATCH,DELETE,OPTIONS",
      "access-control-allow-headers": "content-type,authorization,x-betteruc-token,x-betteruc-admin,x-betteruc-session,x-betteruc-version,x-screenshot-name"
    });
    res.end();
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/status") {
    json(res, 200, {
      ok: true,
      name: "betterUC Platform",
      version: "1.0.0",
      relay: {
        online: onlinePlayersForResponse().length,
        ttlMs: PING_TTL_MS,
        maxClients: MAX_CLIENTS
      },
      accounts: store.accounts.filter(account => account.status !== "revoked").length,
      persistence: persistenceMode,
      adminConfigured: Boolean(ADMIN_KEY),
      github: GITHUB_RELEASES_URL,
      time: Date.now()
    });
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/teamspeak/police-roster.json") {
    try {
      const roster = await policeRoster.getRoster();
      json(res, 200, {
        ok: true,
        count: roster.count,
        slotLimit: roster.slotLimit,
        hash: roster.hash,
        generatedAt: roster.generatedAt,
        groups: roster.groups.map(group => ({
          key: group.key,
          label: group.label,
          members: group.members.map(member => ({
            username: member.username,
            uuid: member.uuid,
            rankNumber: member.rankNumber,
            rankName: member.rankName,
            isLeader: member.isLeader,
            unit: member.unit,
            headUrl: `/api/teamspeak/police-head/${member.uuid || encodeURIComponent(member.username)}.png`
          }))
        }))
      }, {
        "cache-control": "public, max-age=300"
      });
    } catch (error) {
      console.error("Could not load police roster", error);
      json(res, 502, { ok: false, error: "Die Polizeiliste ist gerade nicht erreichbar." });
    }
    return;
  }

  if ((req.method === "GET" || req.method === "HEAD")
      && url.pathname === "/api/teamspeak/police-roster.png") {
    try {
      const image = await policeRoster.getImage();
      const etag = `\"police-${image.hash}\"`;
      if (req.headers["if-none-match"] === etag) {
        res.writeHead(304, { etag, "cache-control": "public, max-age=300" });
        res.end();
        return;
      }
      res.writeHead(200, {
        "content-type": "image/png",
        "content-length": image.buffer.length,
        "cache-control": "public, max-age=300",
        etag
      });
      res.end(req.method === "HEAD" ? undefined : image.buffer);
    } catch (error) {
      console.error("Could not render police roster", error);
      json(res, 502, { ok: false, error: "Die Polizeigrafik ist gerade nicht erreichbar." });
    }
    return;
  }

  const policeHeadMatch = url.pathname.match(/^\/api\/teamspeak\/police-head\/([a-zA-Z0-9_-]{1,64})\.png$/);
  if ((req.method === "GET" || req.method === "HEAD") && policeHeadMatch) {
    try {
      const key = decodeURIComponent(policeHeadMatch[1]);
      const roster = await policeRoster.getRoster();
      const member = roster.members.find(entry => entry.uuid === key.replace(/[^a-fA-F0-9]/g, "").toLowerCase())
        || roster.members.find(entry => entry.username.toLowerCase() === key.toLowerCase());
      const head = await policeRoster.getHead(member?.uuid || key, member?.username || key);
      res.writeHead(200, {
        "content-type": "image/png",
        "content-length": head.length,
        "cache-control": "public, max-age=86400"
      });
      res.end(req.method === "HEAD" ? undefined : head);
    } catch (error) {
      console.error("Could not load police member head", error);
      json(res, 502, { ok: false, error: "Der Spielerkopf ist gerade nicht erreichbar." });
    }
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/teamspeak/swat-roster.json") {
    const roster = swatRoster.getRoster();
    json(res, 200, {
      ok: true,
      count: roster.count,
      slotLimit: roster.slotLimit,
      hash: roster.hash,
      generatedAt: roster.generatedAt,
      groups: roster.groups.map(group => ({
        key: group.key,
        label: group.label,
        members: group.members.map(member => ({
          username: member.username,
          factionRank: member.factionRank,
          role: member.role,
          rankName: member.rankName,
          unit: member.unit,
          headUrl: `/api/teamspeak/swat-head/${encodeURIComponent(member.username)}.png`
        }))
      }))
    }, {
      "cache-control": "public, max-age=60"
    });
    return;
  }

  if ((req.method === "GET" || req.method === "HEAD")
      && url.pathname === "/api/teamspeak/swat-roster.png") {
    try {
      const image = await swatRoster.getImage();
      const etag = `"swat-${image.hash}"`;
      if (req.headers["if-none-match"] === etag) {
        res.writeHead(304, { etag, "cache-control": "public, max-age=60" });
        res.end();
        return;
      }
      res.writeHead(200, {
        "content-type": "image/png",
        "content-length": image.buffer.length,
        "cache-control": "public, max-age=60",
        etag
      });
      res.end(req.method === "HEAD" ? undefined : image.buffer);
    } catch (error) {
      console.error("Could not render SWAT roster", error);
      json(res, 502, { ok: false, error: "Die SWAT-Grafik ist gerade nicht erreichbar." });
    }
    return;
  }

  const swatHeadMatch = url.pathname.match(/^\/api\/teamspeak\/swat-head\/([a-zA-Z0-9_-]{1,64})\.png$/);
  if ((req.method === "GET" || req.method === "HEAD") && swatHeadMatch) {
    try {
      const username = decodeURIComponent(swatHeadMatch[1]);
      const head = await swatRoster.getHead("", username);
      res.writeHead(200, {
        "content-type": "image/png",
        "content-length": head.length,
        "cache-control": "public, max-age=86400"
      });
      res.end(req.method === "HEAD" ? undefined : head);
    } catch (error) {
      console.error("Could not load SWAT member head", error);
      json(res, 502, { ok: false, error: "Der Spielerkopf ist gerade nicht erreichbar." });
    }
    return;
  }

  if (req.method === "POST" && url.pathname === "/api/teamspeak/swat-roster") {
    if (isRateLimited(req, "swat-roster-upload", 6, 60_000)) {
      json(res, 429, { ok: false, error: "Die SWAT-Liste wurde zu häufig aktualisiert." });
      return;
    }
    const auth = authenticate(req, url);
    const accountName = String(auth?.account?.minecraftName || "");
    if (!auth || auth.type !== "session" || !auth.account
        || accountName.localeCompare(SWAT_ROSTER_OWNER_NAME, "de", { sensitivity: "base" }) !== 0) {
      json(res, 403, { ok: false, error: "Diese SWAT-Liste darf nur der konfigurierte Besitzer aktualisieren." });
      return;
    }
    try {
      const body = await readJsonBody(req, 32768);
      const roster = await swatRoster.update(body.roster || body, accountName);
      teamSpeakFactionSync.syncSwatNow?.();
      json(res, 200, {
        ok: true,
        count: roster.count,
        slotLimit: roster.slotLimit,
        hash: roster.hash,
        generatedAt: roster.generatedAt
      });
    } catch (error) {
      json(res, 400, { ok: false, error: error.message || "Die SWAT-Liste ist ungültig." });
    }
    return;
  }

  if (req.method === "POST" && url.pathname === "/api/auth/challenge") {
    if (isRateLimited(req, "minecraft-auth-challenge", 12, 60_000)) {
      json(res, 429, { ok: false, error: "Zu viele Anmeldeversuche. Bitte kurz warten." });
      return;
    }
    const body = await readJsonBody(req, 4096);
    const name = cleanMinecraftName(body.name);
    const uuid = normalizeMinecraftUuid(body.uuid);
    if (!name || !uuid) {
      json(res, 400, { ok: false, error: "Minecraft-Profil ist ungueltig." });
      return;
    }
    const challenge = createLoginChallenge(name, uuid);
    json(res, 201, {
      ok: true,
      challengeId: challenge.id,
      serverId: challenge.serverId,
      expiresAt: challenge.expiresAt
    });
    return;
  }

  if (req.method === "POST" && url.pathname === "/api/auth/complete") {
    if (isRateLimited(req, "minecraft-auth-complete", 12, 60_000)) {
      json(res, 429, { ok: false, error: "Zu viele Anmeldeversuche. Bitte kurz warten." });
      return;
    }
    const body = await readJsonBody(req, 4096);
    pruneLoginChallenges();
    const challengeId = String(body.challengeId || "").trim();
    const challenge = loginChallenges.get(challengeId);
    loginChallenges.delete(challengeId);
    if (!challenge || challenge.expiresAt <= Date.now()) {
      json(res, 401, { ok: false, error: "Anmelde-Challenge ist abgelaufen." });
      return;
    }
    const profile = await verifyMojangJoin(challenge);
    if (!profile
        || profile.name.toLowerCase() !== challenge.name.toLowerCase()
        || profile.uuid !== challenge.uuid) {
      json(res, 401, { ok: false, error: "Minecraft-Sitzung konnte nicht bestaetigt werden." });
      return;
    }
    const account = await accountForVerifiedMinecraftProfile(profile, {
      version: body.version,
      gameVersion: body.gameVersion
    });
    const session = createModSession(account);
    json(res, 200, {
      ok: true,
      sessionToken: session.token,
      expiresAt: session.expiresAt,
      account: publicAccount(account)
    });
    return;
  }

  if (req.method === "POST" && url.pathname === "/api/screenshots") {
    await handleScreenshotUpload(req, res, url);
    return;
  }

  if (req.method === "POST" && url.pathname === "/api/bugs") {
    await handleBugReport(req, res, url);
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/releases/latest") {
    try {
      const target = releaseTargetFromRequest(url);
      if (target !== "mc26.x") {
        json(res, 400, { ok: false, error: "Unterstützt wird ausschließlich Minecraft 26.x." });
        return;
      }
      json(res, 200, releaseResponse(await fetchLatestRelease(), req, target));
    } catch (error) {
      console.error("Could not load latest release", error);
      json(res, 502, { ok: false, error: "Aktueller Download ist gerade nicht erreichbar." });
    }
    return;
  }

  if (url.pathname === "/api/download/latest") {
    await handleLatestJarDownload(req, res);
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/client/features") {
    try {
      const features = await loadFeatureFlags();
      json(res, 200, {
        ok: true,
        features,
        revision: features.reduce((latest, flag) => Math.max(latest, Date.parse(flag.updatedAt || 0) || 0), 0)
      });
    } catch (error) {
      console.error("Could not load betterUC feature flags", error);
      json(res, 200, { ok: true, features: defaultFeatureFlags(), revision: 0, fallback: true });
    }
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/admin/features") {
    if (!requireAdmin(req, res, url)) return;
    try {
      json(res, 200, {
        ok: true,
        writable: persistenceMode === "postgres" && database.enabled,
        features: await loadFeatureFlags()
      });
    } catch (error) {
      console.error("Could not load feature flags for admin panel", error);
      json(res, 503, { ok: false, error: "Feature-Schalter konnten nicht geladen werden." });
    }
    return;
  }

  const adminFeatureMatch = url.pathname.match(/^\/api\/admin\/features\/([a-z0-9_]+)$/);
  if (adminFeatureMatch && req.method === "PATCH") {
    if (!requireAdmin(req, res, url)) return;
    if (persistenceMode !== "postgres" || !database.enabled) {
      json(res, 503, { ok: false, error: "Feature-Schalter benötigen PostgreSQL." });
      return;
    }
    const body = await readJsonBody(req);
    if (typeof body.enabled !== "boolean") {
      json(res, 400, { ok: false, error: "enabled muss true oder false sein." });
      return;
    }
    const feature = await database.updateFeatureFlag(adminFeatureMatch[1], body.enabled, "admin:panel");
    if (!feature) {
      json(res, 404, { ok: false, error: "Feature-Schalter wurde nicht gefunden." });
      return;
    }
    json(res, 200, { ok: true, feature });
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/admin/accounts") {
    if (!requireAdmin(req, res, url)) return;
    let cloudSettings = [];
    if (persistenceMode === "postgres" && database.enabled) {
      try {
        cloudSettings = await database.listCloudSettingsMetadata();
      } catch (error) {
        console.error("Could not load cloud settings metadata for admin panel", error);
      }
    }
    const cloudSettingsByAccount = new Map(cloudSettings.map(entry => [entry.accountId, entry]));
    json(res, 200, {
      ok: true,
      accounts: store.accounts.map(account => adminAccount(account, cloudSettingsByAccount.get(account.id) || null)),
      players: onlinePlayersForResponse(),
      backups: await listPlatformBackups(),
      totals: {
        accounts: store.accounts.length,
        active: store.accounts.filter(account => cleanStatus(account.status) === "active").length,
        revoked: store.accounts.filter(account => cleanStatus(account.status) === "revoked").length,
        online: onlineAccountCount(),
        helper: store.accounts.filter(account => cleanRole(account.role) === "helper").length,
        partner: store.accounts.filter(account => cleanRole(account.role) === "partner").length,
        vip: store.accounts.filter(account => cleanRole(account.role) === "vip").length,
        admin: store.accounts.filter(account => cleanRole(account.role) === "admin").length
      }
    });
    return;
  }

  if (req.method === "POST" && url.pathname === "/api/admin/backups") {
    if (!requireAdmin(req, res, url)) return;
    try {
      const backup = await createPlatformBackup("manual");
      json(res, 201, {
        ok: true,
        backup,
        backups: await listPlatformBackups()
      });
    } catch (error) {
      console.error("Could not create manual betterUC backup", error);
      json(res, 500, { ok: false, error: error.message || "Backup konnte nicht erstellt werden." });
    }
    return;
  }

  if (req.method === "POST" && url.pathname === "/api/admin/accounts") {
    if (!requireAdmin(req, res, url)) return;
    const body = await readJsonBody(req);
    const minecraftName = cleanMinecraftName(body.minecraftName);
    if (minecraftName === null) {
      json(res, 400, { ok: false, error: "Minecraft-Name muss 3-16 Zeichen haben." });
      return;
    }

    const token = generateAccessCode();
    const account = {
      id: crypto.randomUUID(),
      tokenHash: tokenHash(token),
      tokenPrefix: token.slice(0, 10),
      minecraftName,
      minecraftUuid: "",
      faction: cleanSmallLabel(body.faction || ""),
      role: cleanRole(body.role),
      status: "active",
      createdAt: nowIso(),
      createdBy: "admin",
      lastSeenAt: null,
      lastServer: "",
      lastChannel: "",
      lastVersion: "",
      lastGameVersion: ""
    };

    store.accounts.push(account);
    await saveStore();
    json(res, 201, {
      ok: true,
      accessCode: token,
      account: adminAccount(account)
    });
    return;
  }

  const adminAccountMatch = url.pathname.match(/^\/api\/admin\/accounts\/([^/]+)(?:\/([^/]+))?$/);
  if (adminAccountMatch) {
    if (!requireAdmin(req, res, url)) return;

    const account = findAccountById(adminAccountMatch[1]);
    const action = adminAccountMatch[2] || "";
    if (!account) {
      json(res, 404, { ok: false, error: "Account nicht gefunden." });
      return;
    }

    if (req.method === "PATCH" && !action) {
      const body = await readJsonBody(req);
      if (Object.hasOwn(body, "minecraftName")) {
        const minecraftName = cleanMinecraftName(body.minecraftName);
        if (minecraftName === null) {
          json(res, 400, { ok: false, error: "Minecraft-Name muss 3-16 Zeichen haben." });
          return;
        }
        account.minecraftName = minecraftName;
      }
      if (Object.hasOwn(body, "faction")) {
        account.faction = cleanSmallLabel(body.faction || "");
      }
      if (Object.hasOwn(body, "role")) {
        account.role = cleanRole(body.role);
      }
      if (Object.hasOwn(body, "status")) {
        account.status = cleanStatus(body.status);
      }
      account.updatedAt = nowIso();
      await saveStore();
      json(res, 200, { ok: true, account: adminAccount(account) });
      return;
    }

    if (req.method === "POST" && action === "revoke") {
      account.status = "revoked";
      account.revokedAt = nowIso();
      await saveStore();
      json(res, 200, { ok: true, account: adminAccount(account) });
      return;
    }

    if (req.method === "POST" && action === "activate") {
      account.status = "active";
      account.activatedAt = nowIso();
      await saveStore();
      json(res, 200, { ok: true, account: adminAccount(account) });
      return;
    }

    if (req.method === "POST" && action === "reset-code") {
      const token = generateAccessCode();
      account.tokenHash = tokenHash(token);
      account.tokenPrefix = token.slice(0, 10);
      account.status = "active";
      account.resetAt = nowIso();
      await saveStore();
      json(res, 200, {
        ok: true,
        accessCode: token,
        account: adminAccount(account)
      });
      return;
    }

    if (req.method === "POST" && action === "web-password") {
      const body = await readJsonBody(req);
      if (!isValidPassword(body.password)) {
        json(res, 400, { ok: false, error: "Passwort muss 6 bis 72 Zeichen lang sein." });
        return;
      }
      setWebPassword(account, body.password);
      invalidateWebSessions(account);
      account.updatedAt = nowIso();
      await saveStore();
      json(res, 200, { ok: true, account: adminAccount(account) });
      return;
    }

    if (req.method === "POST" && action === "clear-web-password") {
      clearWebPassword(account);
      invalidateWebSessions(account);
      account.updatedAt = nowIso();
      await saveStore();
      json(res, 200, { ok: true, account: adminAccount(account) });
      return;
    }

    if (req.method === "POST" && action === "logout-web") {
      invalidateWebSessions(account);
      account.updatedAt = nowIso();
      await saveStore();
      json(res, 200, { ok: true, account: adminAccount(account) });
      return;
    }

    if (req.method === "GET" && action === "cloud-history") {
      if (persistenceMode !== "postgres" || !database.enabled) {
        json(res, 503, { ok: false, error: "Cloud-Verlauf ist ohne PostgreSQL nicht verfügbar." });
        return;
      }
      json(res, 200, {
        ok: true,
        history: await database.getCloudSettingsHistory(account.id)
      });
      return;
    }

    if (req.method === "POST" && action === "restore-cloud") {
      if (persistenceMode !== "postgres" || !database.enabled) {
        json(res, 503, { ok: false, error: "Cloud-Verlauf ist ohne PostgreSQL nicht verfügbar." });
        return;
      }
      const body = await readJsonBody(req);
      const historyId = Number(body.historyId);
      if (!Number.isSafeInteger(historyId) || historyId <= 0) {
        json(res, 400, { ok: false, error: "Ungültige Cloud-Revision." });
        return;
      }
      const profile = await database.restoreCloudSettings(account.id, historyId, "admin:panel");
      if (!profile) {
        json(res, 404, { ok: false, error: "Cloud-Revision wurde nicht gefunden." });
        return;
      }
      cloudRevisionByAccountId.set(account.id, Number(profile.revision || 0));
      json(res, 200, { ok: true, profile });
      return;
    }

    if (req.method === "POST" && action === "reset-cloud") {
      if (persistenceMode !== "postgres" || !database.enabled) {
        json(res, 503, { ok: false, error: "Cloud-Profile sind ohne PostgreSQL nicht verfügbar." });
        return;
      }
      const deleted = await database.deleteCloudSettings(account.id, "admin:panel");
      if (deleted) cloudRevisionByAccountId.delete(account.id);
      json(res, 200, {
        ok: true,
        deleted,
        account: adminAccount(account, null)
      });
      return;
    }

    if (req.method === "POST" && action === "delete") {
      closeConnectionsForAccount(account.id);
      store.accounts = store.accounts.filter(existing => existing.id !== account.id);
      await saveStore();
      json(res, 200, { ok: true, deletedId: account.id });
      return;
    }

    json(res, 405, { ok: false, error: "Admin-Aktion nicht unterstuetzt." });
    return;
  }

  if (req.method === "POST" && url.pathname === "/api/access") {
    if (isRateLimited(req, "access", 8, 60_000)) {
      json(res, 429, { ok: false, error: "Zu viele Versuche. Bitte kurz warten." });
      return;
    }

    const body = await readJsonBody(req);
    const minecraftName = cleanMinecraftName(body.minecraftName);
    if (minecraftName === null) {
      json(res, 400, { ok: false, error: "Minecraft-Name muss 3-16 Zeichen haben." });
      return;
    }

    const faction = cleanSmallLabel(body.faction || "");
    const token = generateAccessCode();
    const account = {
      id: crypto.randomUUID(),
      tokenHash: tokenHash(token),
      tokenPrefix: token.slice(0, 10),
      minecraftName,
      minecraftUuid: "",
      faction,
      role: "user",
      status: "active",
      createdAt: nowIso(),
      lastSeenAt: null,
      lastServer: "",
      lastChannel: "",
      lastVersion: "",
      lastGameVersion: ""
    };

    store.accounts.push(account);
    await saveStore();
    json(res, 201, {
      ok: true,
      accessCode: token,
      account: publicAccount(account)
    });
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/players") {
    const auth = authenticate(req, url);
    if (!auth) {
      json(res, 401, { ok: false, error: "betterUC-Anmeldung fehlt oder ist ungueltig." });
      return;
    }

    json(res, 200, {
      ok: true,
      players: onlinePlayersForResponse(),
      summary: activitySummary()
    });
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/admin/database") {
    if (!requireAdmin(req, res, url)) return;
    if (persistenceMode !== "postgres" || !database.enabled) {
      json(res, 200, {
        ok: true,
        persistence: persistenceMode,
        database: { connected: false, counts: { accounts: store.accounts.length } }
      });
      return;
    }
    try {
      json(res, 200, {
        ok: true,
        persistence: persistenceMode,
        database: await database.getOverview()
      });
    } catch (error) {
      console.error("Could not load PostgreSQL overview", error);
      json(res, 503, { ok: false, error: "Datenbankstatus konnte nicht geladen werden." });
    }
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/user/settings") {
    const auth = authenticate(req, url);
    if (!auth || !auth.account || auth.account.id === "legacy") {
      json(res, 401, { ok: false, error: "betterUC-Anmeldung fehlt oder ist ungueltig." });
      return;
    }
    if (persistenceMode !== "postgres") {
      json(res, 503, { ok: false, error: "Cloud-Einstellungen sind momentan nicht verfuegbar." });
      return;
    }

    const profile = await database.getCloudSettings(auth.account.id);
    await recordCloudSyncEvent(auth.account.id, {
      type: "download",
      revision: profile?.revision ?? 0,
      schemaVersion: profile?.schemaVersion ?? CLOUD_SETTINGS_SCHEMA_VERSION,
      modVersion: cleanSmallLabel(req.headers["x-betteruc-version"] || "", ""),
      detail: profile ? "Cloud-Profil geladen" : "Noch kein Cloud-Profil"
    });
    json(res, 200, {
      ok: true,
      exists: Boolean(profile),
      profile
    });
    return;
  }

  if (req.method === "PUT" && url.pathname === "/api/user/settings") {
    if (isRateLimited(req, "cloud-settings", 30, 60_000)) {
      json(res, 429, { ok: false, error: "Zu viele Cloud-Aktualisierungen. Bitte kurz warten." });
      return;
    }
    const auth = authenticate(req, url);
    if (!auth || !auth.account || auth.account.id === "legacy") {
      json(res, 401, { ok: false, error: "betterUC-Anmeldung fehlt oder ist ungueltig." });
      return;
    }
    if (persistenceMode !== "postgres") {
      json(res, 503, { ok: false, error: "Cloud-Einstellungen sind momentan nicht verfuegbar." });
      return;
    }

    const body = await readJsonBody(req, CLOUD_SETTINGS_MAX_BYTES + 4096);
    const schemaVersion = Number(body.schemaVersion);
    const baseRevision = Number(body.baseRevision);
    const settings = body.settings;
    const modVersion = cleanSmallLabel(body.modVersion || req.headers["x-betteruc-version"] || "", "");
    if (!Number.isSafeInteger(schemaVersion) || schemaVersion !== CLOUD_SETTINGS_SCHEMA_VERSION) {
      await recordCloudSyncEvent(auth.account.id, {
        type: "error",
        revision: baseRevision,
        schemaVersion,
        modVersion,
        detail: "Nicht unterstützte Einstellungs-Version"
      });
      json(res, 400, { ok: false, error: "Nicht unterstuetzte Einstellungs-Version." });
      return;
    }
    if (!Number.isSafeInteger(baseRevision) || baseRevision < 0) {
      await recordCloudSyncEvent(auth.account.id, {
        type: "error",
        schemaVersion,
        modVersion,
        detail: "Ungültige Cloud-Revision"
      });
      json(res, 400, { ok: false, error: "Ungueltige Cloud-Revision." });
      return;
    }
    if (!settings || typeof settings !== "object" || Array.isArray(settings)) {
      await recordCloudSyncEvent(auth.account.id, {
        type: "error",
        revision: baseRevision,
        schemaVersion,
        modVersion,
        detail: "Einstellungen sind kein JSON-Objekt"
      });
      json(res, 400, { ok: false, error: "Einstellungen muessen ein JSON-Objekt sein." });
      return;
    }
    if (Buffer.byteLength(JSON.stringify(settings), "utf8") > CLOUD_SETTINGS_MAX_BYTES) {
      await recordCloudSyncEvent(auth.account.id, {
        type: "error",
        revision: baseRevision,
        schemaVersion,
        modVersion,
        detail: "Einstellungsprofil überschreitet das Größenlimit"
      });
      json(res, 413, { ok: false, error: "Das Einstellungsprofil ist zu gross." });
      return;
    }

    const result = await database.putCloudSettings(auth.account.id, {
      schemaVersion,
      baseRevision,
      settings,
      updatedByVersion: modVersion
    });
    if (result.conflict) {
      cloudRevisionByAccountId.set(auth.account.id, Number(result.current?.revision || 0));
      await recordCloudSyncEvent(auth.account.id, {
        type: "conflict",
        revision: result.current?.revision ?? baseRevision,
        schemaVersion,
        modVersion,
        detail: `Client-Revision ${baseRevision}, Cloud-Revision ${result.current?.revision ?? 0}`
      });
      json(res, 409, {
        ok: false,
        error: "Die Cloud-Einstellungen wurden zwischenzeitlich geaendert.",
        profile: result.current
      });
      return;
    }
    cloudRevisionByAccountId.set(auth.account.id, Number(result.current?.revision || 0));
    await recordCloudSyncEvent(auth.account.id, {
      type: "upload",
      revision: result.current?.revision,
      schemaVersion,
      modVersion,
      detail: "Cloud-Profil gespeichert"
    });
    json(res, 200, { ok: true, profile: result.current });
    return;
  }

  if (req.method === "POST" && url.pathname === "/api/user/register") {
    if (isRateLimited(req, "user-register", 8, 60_000)) {
      json(res, 429, { ok: false, error: "Zu viele Versuche. Bitte kurz warten." });
      return;
    }

    const auth = authenticate(req, url);
    if (!auth || !auth.account || auth.account.id === "legacy") {
      json(res, 401, { ok: false, error: "betterUC-Anmeldung fehlt oder ist ungueltig." });
      return;
    }

    const body = await readJsonBody(req);
    if (!isValidPassword(body.password)) {
      json(res, 400, { ok: false, error: "Passwort muss 6 bis 72 Zeichen lang sein." });
      return;
    }

    const account = auth.account;
    if (auth.type !== "session" && Object.hasOwn(body, "minecraftName")) {
      const minecraftName = cleanMinecraftName(body.minecraftName);
      if (minecraftName === null) {
        json(res, 400, { ok: false, error: "Minecraft-Name muss 3-16 Zeichen haben." });
        return;
      }
      if (minecraftName) account.minecraftName = minecraftName;
    }
    if (auth.type !== "session" && Object.hasOwn(body, "minecraftUuid")) {
      account.minecraftUuid = normalizeMinecraftUuid(body.minecraftUuid) || account.minecraftUuid;
    }
    if (Object.hasOwn(body, "faction")) {
      account.faction = cleanSmallLabel(body.faction || "");
    }

    setWebPassword(account, body.password);
    account.updatedAt = nowIso();
    await saveStore();
    json(res, 200, {
      ok: true,
      message: "Web-Login wurde eingerichtet.",
      user: userPanelAccount(account)
    });
    return;
  }

  if (req.method === "POST" && url.pathname === "/api/user/login") {
    if (isRateLimited(req, "user-login", 12, 60_000)) {
      json(res, 429, { ok: false, error: "Zu viele Login-Versuche. Bitte kurz warten." });
      return;
    }

    const body = await readJsonBody(req);
    const minecraftName = cleanMinecraftName(body.minecraftName);
    if (minecraftName === null || !minecraftName) {
      json(res, 400, { ok: false, error: "Bitte gib deinen Minecraft-Namen ein." });
      return;
    }

    const account = findAccountByMinecraftLogin(minecraftName, body.password || "");
    if (!account) {
      json(res, 401, { ok: false, error: "Name oder Passwort ist falsch." });
      return;
    }

    account.lastPanelLoginAt = nowIso();
    await saveStore();
    json(res, 200, {
      ok: true,
      sessionToken: createUserSession(account),
      user: userPanelAccount(account)
    });
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/user/me") {
    const account = requireUserSession(req, res, url);
    if (!account) return;
    json(res, 200, {
      ok: true,
      user: userPanelAccount(account)
    });
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/user/screenshots") {
    const account = requireUserSession(req, res, url);
    if (!account) return;
    if (!database.enabled) {
      json(res, 503, { ok: false, error: "Screenshot-Galerie ist gerade nicht verfügbar." });
      return;
    }
    const uploads = await database.listScreenshotUploadsForAccount(account.id, 60);
    json(res, 200, {
      ok: true,
      screenshots: uploads.map(screenshotGalleryItem)
    });
    return;
  }

  const userScreenshotMatch = url.pathname.match(/^\/api\/user\/screenshots\/([a-zA-Z0-9_-]{20,64})$/);
  if (req.method === "DELETE" && userScreenshotMatch) {
    const account = requireUserSession(req, res, url);
    if (!account) return;
    if (!database.enabled) {
      json(res, 503, { ok: false, error: "Screenshot-Galerie ist gerade nicht verfügbar." });
      return;
    }
    const upload = await database.getScreenshotUpload(userScreenshotMatch[1]);
    if (!upload || upload.accountId !== account.id) {
      json(res, 404, { ok: false, error: "Screenshot nicht gefunden." });
      return;
    }
    await removeScreenshotUpload(upload);
    json(res, 200, { ok: true });
    return;
  }

  if (req.method === "POST" && url.pathname === "/api/user/stats") {
    const auth = authenticate(req, url);
    if (!auth || !auth.account || auth.account.id === "legacy") {
      json(res, 401, { ok: false, error: "betterUC-Anmeldung fehlt oder ist ungueltig." });
      return;
    }

    const body = await readJsonBody(req, 16384);
    const account = auth.account;
    if (auth.type !== "session" && Object.hasOwn(body, "minecraftName")) {
      const minecraftName = cleanMinecraftName(body.minecraftName);
      if (minecraftName) account.minecraftName = minecraftName;
    }
    if (auth.type !== "session" && Object.hasOwn(body, "minecraftUuid")) {
      account.minecraftUuid = normalizeMinecraftUuid(body.minecraftUuid) || account.minecraftUuid;
    }
    if (Object.hasOwn(body, "server")) account.lastServer = cleanSmallLabel(body.server || "", "").toLowerCase();
    if (Object.hasOwn(body, "version")) account.lastVersion = cleanSmallLabel(body.version || "", "");
    if (Object.hasOwn(body, "gameVersion")) account.lastGameVersion = cleanSmallLabel(body.gameVersion || "", "");
    if (Object.hasOwn(body, "faction")) account.faction = cleanSmallLabel(body.faction || "");
    account.lastSeenAt = nowIso();
    mergeStats(account, body.stats || body);
    await saveStore();
    json(res, 200, {
      ok: true,
      user: userPanelAccount(account)
    });
    return;
  }

  json(res, 404, { ok: false, error: "API route not found" });
}

async function serveStatic(req, res, url) {
  let pathname = decodeURIComponent(url.pathname);
  if (pathname === "/admin") {
    pathname = "/admin.html";
  }
  if (pathname === "/panel") {
    pathname = "/panel.html";
  }
  if (pathname === "/access") {
    pathname = "/access.html";
  }
  if (pathname === "/download") {
    pathname = "/download.html";
  }
  if (pathname === "/changelog") {
    pathname = "/changelog.html";
  }
  if (pathname === "/impressum") {
    pathname = "/impressum.html";
  }
  if (pathname === "/datenschutz") {
    pathname = "/datenschutz.html";
  }
  if (pathname === "/polizei/mitglieder" || pathname === "/polizei/mitglieder/") {
    pathname = "/polizei-mitglieder.html";
  }
  if (pathname === "/polizei/swat" || pathname === "/polizei/swat/") {
    pathname = "/polizei-swat.html";
  }
  if (pathname === "/" || pathname === "/updates") {
    pathname = "/index.html";
  }

  const target = path.normalize(path.join(PUBLIC_DIR, pathname));
  if (!target.startsWith(path.normalize(PUBLIC_DIR))) {
    text(res, 403, "Forbidden");
    return;
  }

  try {
    const stat = await fsp.stat(target);
    if (!stat.isFile()) {
      text(res, 404, "Not found");
      return;
    }

    const ext = path.extname(target).toLowerCase();
    const requiresRevalidation = ext === ".html" || ext === ".js" || ext === ".css";
    res.writeHead(200, {
      "content-type": MIME_TYPES.get(ext) || "application/octet-stream",
      "cache-control": requiresRevalidation ? "no-cache" : "public, max-age=3600"
    });
    fs.createReadStream(target).pipe(res);
  } catch (error) {
    if (error.code === "ENOENT") {
      await serveStatic(req, res, new URL("/index.html", url));
      return;
    }
    throw error;
  }
}

async function handleHttp(req, res) {
  const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
  if (await clipRoutes.handle(req, res, url)) return;
  if (req.method === 'GET' && CLIP_PAGE.test(url.pathname)) {
    const body = await fsp.readFile(path.join(PUBLIC_DIR, 'clip.html'));
    res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store',
      'referrer-policy': 'no-referrer', 'x-robots-tag': 'noindex, nofollow', 'x-content-type-options': 'nosniff',
      'content-security-policy': "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' https://*.r2.cloudflarestorage.com; media-src 'self' https://*.r2.cloudflarestorage.com; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'" });
    res.end(body); return;
  }

  if (req.method === "GET" && url.pathname === "/health") {
    json(res, 200, {
      ok: true,
      name: "betterUC Relay",
      clients: clients.size,
      accounts: store.accounts.length,
      persistence: persistenceMode,
      ttlMs: PING_TTL_MS,
      time: Date.now()
    });
    return;
  }

  if (/^\/download\/latest(?:-mc26\.x)?\.jar$/i.test(url.pathname)) {
    await handleLatestJarDownload(req, res);
    return;
  }

  if (/^\/download\/latest-[^/]+\.jar$/i.test(url.pathname)) {
    text(res, 404, "Only the mc26.x release target is available");
    return;
  }

  const screenshotMatch = req.method === "GET" ? url.pathname.match(/^\/s\/([a-zA-Z0-9_-]{20,64})$/) : null;
  if (screenshotMatch) {
    await handleSharedScreenshot(req, res, screenshotMatch[1]);
    return;
  }

  if (url.pathname.startsWith("/api/")) {
    await handleApi(req, res, url);
    return;
  }

  await serveStatic(req, res, url);
}

function safeNumber(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function updateClientInfo(client, payload) {
  client.name = cleanMinecraftName(payload.name) || client.name || "unknown";
  client.uuid = cleanSmallLabel(payload.uuid || client.uuid || "", "");
  client.server = normalizeServerId(payload.server || client.server || "unknown");
  client.channel = cleanChannel(payload.channel || client.channel || "global");
  if (Object.hasOwn(payload, "faction")) {
    client.faction = cleanSmallLabel(payload.faction || "", "");
  }
  client.version = cleanSmallLabel(payload.version || client.version || "", "");
  client.gameVersion = cleanSmallLabel(payload.gameVersion || client.gameVersion || "", "");
  updateAccountFromClient(client.account, client);
}

function sameAudience(sender, target, payload) {
  if (!sender || !target) return false;
  if (sender.server !== target.server) return false;

  const scope = cleanChannel(payload.audience || payload.scope || "channel");
  if (scope === "faction") {
    const senderFaction = normalizeFactionKey(effectiveClientFaction(sender));
    const targetFaction = normalizeFactionKey(effectiveClientFaction(target));
    return Boolean(senderFaction)
      && Boolean(targetFaction)
      && senderFaction === targetFaction;
  }
  if (scope === "state") {
    return isStateFaction(effectiveClientFaction(sender))
      && isStateFaction(effectiveClientFaction(target));
  }
  if (scope === "global") {
    return true;
  }

  if (sender.role === "admin" || target.role === "admin") return true;

  return sender.channel === target.channel;
}

function broadcastPing(sender, payload) {
  const requestedId = String(payload.id || "").trim();
  const marker = {
    type: "ping",
    id: /^[0-9a-fA-F-]{36}$/.test(requestedId) ? requestedId : crypto.randomUUID(),
    pingType: cleanPingType(payload.pingType),
    sender: sender.name || "unknown",
    label: cleanSmallLabel(payload.label || "Ping", "Ping"),
    dimension: cleanDimension(payload.dimension || "unknown"),
    x: safeNumber(payload.x, 0),
    y: safeNumber(payload.y, 0),
    z: safeNumber(payload.z, 0),
    color: /^#?[0-9A-Fa-f]{6}$/.test(String(payload.color || ""))
      ? (String(payload.color).startsWith("#") ? String(payload.color) : `#${payload.color}`)
      : "#38BDF8",
    role: sender.role,
    priority: sender.priority,
    admin: sender.role === "admin",
    createdAt: Date.now(),
    expiresAt: Date.now() + PING_TTL_MS
  };

  const raw = JSON.stringify(marker);
  for (const target of clients) {
    if (target.ws.readyState !== target.ws.OPEN) continue;
    if (!sameAudience(sender, target, payload)) continue;
    target.ws.send(raw);
  }
}

async function handleWsMessage(client, raw) {
  let payload;
  try {
    payload = JSON.parse(String(raw));
  } catch {
    return;
  }

  if (payload.type === "hello") {
    const oldServer = client.server;
    updateClientInfo(client, payload);
    client.ws.send(JSON.stringify({
      type: "hello_ack",
      verified: client.authType !== "legacy",
      role: client.role,
      priority: client.priority,
      admin: client.role === "admin",
      accountId: client.account && client.account.id !== "legacy" ? client.account.id : null
    }));
    if (oldServer !== client.server) {
      broadcastPresence(oldServer);
    }
    broadcastPresence(client.server);
    return;
  }

  if (payload.type === "ping") {
    broadcastPing(client, payload);
    return;
  }

  if (payload.type === "announcement_send") {
    if (client.authType === "legacy" || client.role !== "admin" || !client.account || client.account.id === "legacy") {
      client.ws.send(JSON.stringify({
        type: "announcement_error",
        message: "Nur betterUC-Admins d\u00fcrfen Ank\u00fcndigungen senden."
      }));
      return;
    }
    const message = cleanRelayMessage(payload.message);
    if (!message) {
      client.ws.send(JSON.stringify({ type: "announcement_error", message: "Die Ank\u00fcndigung ist leer." }));
      return;
    }
    if (message.length > ANNOUNCEMENT_MAX_LENGTH) {
      client.ws.send(JSON.stringify({
        type: "announcement_error",
        message: `Eine Ank\u00fcndigung darf maximal ${ANNOUNCEMENT_MAX_LENGTH} Zeichen lang sein.`
      }));
      return;
    }
    const rateLimitError = announcementRateLimit();
    if (rateLimitError) {
      client.ws.send(JSON.stringify({ type: "announcement_error", message: rateLimitError }));
      return;
    }
    const event = broadcastAnnouncement(client, message, "minecraft");
    recordDiscordActivity("announcement.minecraft", client.account.id).catch(() => {});
    discordBot.publishAnnouncement(event).catch(error => {
      console.warn("Could not publish betterUC announcement to Discord", error.message);
    });
    return;
  }

  if (payload.type === "waste_area_update") {
    if (client.role !== "admin" || client.authType === "legacy") {
      client.ws.send(JSON.stringify({
        type: "waste_area_error",
        message: "Nur betterUC-Admins dürfen Müllbereiche ändern."
      }));
      return;
    }
    if (persistenceMode !== "postgres" || !database.enabled) {
      client.ws.send(JSON.stringify({
        type: "waste_area_error",
        message: "Die Datenbank ist derzeit nicht verfügbar."
      }));
      return;
    }

    const wasteType = String(payload.wasteType || "").trim().toLowerCase();
    const action = String(payload.action || "").trim().toLowerCase();
    if (!WASTE_TYPES.has(wasteType) || !["pos1", "pos2", "clear"].includes(action)) {
      client.ws.send(JSON.stringify({ type: "waste_area_error", message: "Ungültige Bereichsdaten." }));
      return;
    }

    const actor = `admin:${client.account.minecraftName || client.name || client.account.id}`;
    if (action === "clear") {
      await database.deleteWasteDropArea(wasteType, actor);
      delete wasteDropAreas[wasteType];
    } else {
      const x = Number(payload.x);
      const z = Number(payload.z);
      const dimension = cleanDimension(payload.dimension || "");
      if (!Number.isSafeInteger(x) || !Number.isSafeInteger(z) || dimension === "unknown") {
        client.ws.send(JSON.stringify({ type: "waste_area_error", message: "Position oder Dimension ist ungültig." }));
        return;
      }

      const current = wasteDropAreas[wasteType] || {
        type: wasteType,
        x1: null,
        z1: null,
        x2: null,
        z2: null,
        dimension
      };
      if (current.dimension && current.dimension !== dimension) {
        current.x1 = null;
        current.z1 = null;
        current.x2 = null;
        current.z2 = null;
      }
      current.dimension = dimension;
      current[action === "pos1" ? "x1" : "x2"] = x;
      current[action === "pos1" ? "z1" : "z2"] = z;
      wasteDropAreas[wasteType] = await database.upsertWasteDropArea(wasteType, current, actor);
    }

    broadcastWasteDropAreas();
    client.ws.send(JSON.stringify({
      type: "waste_area_saved",
      wasteType,
      action
    }));
  }
}

function handleWsConnection(ws, req, auth, url) {
  const sessionAuthenticated = auth.type === "session";
  const client = {
    ws,
    account: auth.account,
    authType: auth.type,
    role: cleanRole(auth.role),
    priority: rolePriority(auth.role),
    name: sessionAuthenticated
      ? auth.account.minecraftName
      : cleanMinecraftName(url.searchParams.get("name")) || auth.account.minecraftName || "unknown",
    uuid: sessionAuthenticated
      ? normalizeMinecraftUuid(auth.account.minecraftUuid)
      : normalizeMinecraftUuid(url.searchParams.get("uuid")) || "",
    server: normalizeServerId(url.searchParams.get("server") || "unknown"),
    channel: cleanChannel(url.searchParams.get("channel") || "global"),
    faction: cleanSmallLabel(url.searchParams.get("faction") || "", ""),
    version: cleanSmallLabel(url.searchParams.get("version") || "", ""),
    gameVersion: cleanSmallLabel(url.searchParams.get("gameVersion") || "", ""),
    connectedAt: nowIso()
  };

  const replacedServers = replaceExistingClientSessions(client);
  clients.add(client);
  updateAccountFromClient(client.account, client);
  recordDiscordActivity("client.connected", client.account && client.account.id !== "legacy" ? client.account.id : null, {
    modVersion: client.version,
    gameVersion: client.gameVersion,
    server: client.server
  }).catch(() => {});
  ws.send(JSON.stringify({
    type: "welcome",
    verified: client.authType !== "legacy",
    role: client.role,
    priority: client.priority,
    admin: client.role === "admin",
    ttlMs: PING_TTL_MS
  }));
  sendWasteDropAreas(client);
  notifyClientAboutLatestRelease(client).catch(error => {
    console.warn("Could not send update state to connected client", error.message);
  });
  broadcastPresence(client.server);
  for (const server of replacedServers) {
    if (server !== client.server) broadcastPresence(server);
  }

  ws.on("message", raw => {
    handleWsMessage(client, raw).catch(error => {
      console.error("Could not handle betterUC websocket message", error);
      if (ws.readyState === ws.OPEN) {
        ws.send(JSON.stringify({ type: "waste_area_error", message: "Bereich konnte nicht gespeichert werden." }));
      }
    });
  });
  ws.on("close", () => removeClient(client));
  ws.on("error", () => removeClient(client));
}

function replaceExistingClientSessions(client) {
  const accountId = client && client.account && client.account.id;
  if (!accountId || accountId === "legacy") return new Set();

  const replacedServers = new Set();
  for (const existing of [...clients]) {
    if (!existing.account || existing.account.id !== accountId) continue;
    replacedServers.add(existing.server);
    clients.delete(existing);
    try {
      existing.ws.close(4000, "Replaced by newer betterUC connection");
    } catch {
      // Closing an already broken socket is harmless; the new connection continues.
    }
  }
  return replacedServers;
}

function removeClient(client) {
  const server = client.server;
  clients.delete(client);
  broadcastPresence(server);
}

async function main() {
  await loadStore();
  await swatRoster.load();
  await loadWasteDropAreas();
  await fsp.mkdir(SCREENSHOT_DIR, { recursive: true });
  await cleanupExpiredScreenshots();
  await clipRoutes.cleanup().catch(() => console.warn('Clip cleanup temporarily unavailable'));
  clipCleanupTimer = setInterval(() => { clipRoutes.cleanup().catch(() => console.warn('Clip cleanup will retry')); }, 60000);
  clipCleanupTimer.unref?.();
  screenshotCleanupTimer = setInterval(() => {
    cleanupExpiredScreenshots().catch(error => {
      console.warn("Could not clean expired screenshots", error.message);
    });
  }, SCREENSHOT_CLEANUP_INTERVAL_MS);
  screenshotCleanupTimer.unref?.();
  scheduleStoreBackups();
  startReleaseWatcher().catch(error => {
    console.warn("Could not start betterUC release watcher", error.message);
  });
  teamSpeakFactionSync = startTeamSpeakFactionSync({
    getSwatRoster: () => swatRoster.getRoster()
  });
  startDiscordBot({
    getOnlinePlayers: onlinePlayersForResponse,
    getAccounts: () => store.accounts.map(adminAccount),
    findAccountByMinecraftName: name => {
      const account = findAccountByMinecraftName(name);
      return account ? adminAccount(account) : null;
    },
    getAccountDiagnostic: async name => {
      const account = findAccountByMinecraftName(name);
      if (!account) return null;
      let cloudSettings = null;
      if (database.enabled) {
        try {
          const metadata = await database.listCloudSettingsMetadata();
          cloudSettings = metadata.find(entry => entry.accountId === account.id) || null;
        } catch (error) {
          console.warn("Could not load cloud metadata for Discord diagnosis", error.message);
        }
      }
      return adminAccount(account, cloudSettings);
    },
    createAccessAccount,
    resetAccessCodeByMinecraftName,
    revokeAccountByMinecraftName,
    linkDiscordAccountByCode,
    unlinkDiscordAccount,
    findAccountByDiscordId,
    sendAnnouncementFromDiscord,
    getSystemSnapshot: discordSystemSnapshot,
    createDiscordTicket: ticket => database.createDiscordTicket(ticket),
    claimDiscordTicket: (channelId, discordId) => database.claimDiscordTicket(channelId, discordId),
    closeDiscordTicket: (channelId, reason, transcriptPath) => database.closeDiscordTicket(channelId, reason, transcriptPath),
    createDiscordSuggestion: suggestion => database.createDiscordSuggestion(suggestion),
    attachDiscordSuggestionMessage: (id, channelId, messageId) => database.attachDiscordSuggestionMessage(id, channelId, messageId),
    getDiscordSuggestion: id => database.getDiscordSuggestion(id),
    voteDiscordSuggestion: (id, discordId, vote) => database.voteDiscordSuggestion(id, discordId, vote),
    updateDiscordSuggestionStatus: (id, status, note) => database.updateDiscordSuggestionStatus(id, status, note),
    getDiscordWeeklyStats: since => database.getDiscordWeeklyStats(since),
    recordDiscordActivity
  })
    .then(bot => {
      discordBot = bot;
      discordBot.notifyStateChanged();
    })
    .catch(error => console.error("Could not start betterUC Discord bot", error));

  const server = http.createServer((req, res) => {
    handleHttp(req, res).catch(error => {
      console.error("HTTP error", error);
      json(res, error.statusCode || 500, { ok: false, error: "Serverfehler" });
    });
  });

  const wss = new WebSocketServer({ noServer: true });
  server.on("upgrade", (req, socket, head) => {
    const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
    if (url.pathname !== "/ws") {
      socket.destroy();
      return;
    }
    if (clients.size >= MAX_CLIENTS) {
      socket.write("HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\n\r\n");
      socket.destroy();
      return;
    }

    const auth = authenticate(req, url);
    if (!auth) {
      socket.write("HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n");
      socket.destroy();
      return;
    }

    wss.handleUpgrade(req, socket, head, ws => handleWsConnection(ws, req, auth, url));
  });

  server.listen(PORT, () => {
    console.log(`betterUC platform listening on ${PORT} with ${persistenceMode} persistence`);
  });

  let stopping = false;
  const stop = async signal => {
    if (stopping) return;
    stopping = true;
    console.log(`Stopping betterUC platform (${signal})...`);
    clearTimeout(saveTimer);
    clearInterval(backupTimer);
    clearInterval(updateWatchTimer);
    clearInterval(screenshotCleanupTimer);
    clearInterval(clipCleanupTimer);
    try {
      await saveStore();
    } catch (error) {
      console.error("Could not flush betterUC data during shutdown", error);
    }
    try {
      teamSpeakFactionSync.stop();
    } catch (error) {
      console.error("Could not stop TeamSpeak faction sync", error);
    }
    try {
      await discordBot.stop();
    } catch (error) {
      console.error("Could not stop betterUC Discord bot", error);
    }
    const serverClosed = new Promise(resolve => server.close(resolve));
    for (const client of [...clients]) {
      clients.delete(client);
      try {
        client.ws.terminate();
      } catch {
        // The socket may already have closed while shutdown was in progress.
      }
    }
    await new Promise(resolve => wss.close(resolve));
    if (typeof server.closeAllConnections === "function") {
      server.closeAllConnections();
    }
    await serverClosed;
    await database.close();
    process.exit(0);
  };
  process.once("SIGTERM", () => stop("SIGTERM"));
  process.once("SIGINT", () => stop("SIGINT"));
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
