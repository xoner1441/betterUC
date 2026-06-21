"use strict";

const crypto = require("crypto");
const fs = require("fs");
const fsp = fs.promises;
const http = require("http");
const path = require("path");
const { Readable } = require("stream");
const { URL } = require("url");
const { WebSocketServer } = require("ws");
const { startDiscordBot } = require("./discordBot");

const PORT = Number(process.env.PORT || 3000);
const MAX_CLIENTS = Number(process.env.MAX_CLIENTS || 500);
const PING_TTL_MS = Number(process.env.PING_TTL_MS || 15000);
const PUBLIC_DIR = process.env.PUBLIC_DIR || path.join(__dirname, "public");
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, "data");
const STORE_FILE = path.join(DATA_DIR, "accounts.json");
const BACKUP_DIR = process.env.BACKUP_DIR || path.join(DATA_DIR, "backups");
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
const GITHUB_RELEASES_URL = "https://github.com/xoner1441/betterUC/releases";
const GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/xoner1441/betterUC/releases/latest";
const PUBLIC_BASE_URL = String(process.env.PUBLIC_BASE_URL || "https://betteruc.de").replace(/\/+$/, "");
const RELEASE_CACHE_TTL_MS = Number(process.env.RELEASE_CACHE_TTL_MS || 5 * 60 * 1000);

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
let latestReleaseCache = { fetchedAt: 0, release: null };
const clients = new Set();
const rateLimits = new Map();
let discordBot = { notifyStateChanged() {}, stop() {} };

function nowIso() {
  return new Date().toISOString();
}

function json(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "access-control-allow-origin": "*"
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

async function loadStore() {
  await fsp.mkdir(DATA_DIR, { recursive: true });
  try {
    const raw = await fsp.readFile(STORE_FILE, "utf8");
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed.accounts)) {
      store = { version: 1, ...parsed };
      return;
    }
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
  await saveStore();
}

async function saveStore() {
  await fsp.mkdir(DATA_DIR, { recursive: true });
  const tmp = `${STORE_FILE}.tmp`;
  await fsp.writeFile(tmp, JSON.stringify(store, null, 2), "utf8");
  await fsp.rename(tmp, STORE_FILE);
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
    name: fileName,
    size: stat.size,
    createdAt: stat.mtime.toISOString()
  };
}

async function ensureDailyStoreBackup() {
  const today = backupDateKey();
  const backups = await listStoreBackups();
  if (backups.some(backup => backup.name.startsWith(`accounts-${today}`))) return null;
  return createStoreBackup("daily");
}

function scheduleStoreBackups() {
  clearInterval(backupTimer);
  ensureDailyStoreBackup().catch(error => console.error("Could not create betterUC daily backup", error));
  backupTimer = setInterval(() => {
    ensureDailyStoreBackup().catch(error => console.error("Could not create betterUC daily backup", error));
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

function publicStats(account) {
  const stats = account && account.stats && typeof account.stats === "object" ? account.stats : {};
  return {
    bankMoney: cleanStatNumber(stats.bankMoney),
    cashMoney: cleanStatNumber(stats.cashMoney),
    factionDisplay: cleanStatText(stats.factionDisplay || ""),
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
    lastPanelLoginAt: account.lastPanelLoginAt || null,
    tokenPrefix: account.tokenPrefix || "",
    hasWebPassword: Boolean(account.webPasswordHash),
    statsHistory: publicStatsHistory(account),
    stats
  };
}

function adminAccount(account) {
  const onlineClient = onlineClientForAccount(account);
  return {
    ...publicAccount(account),
    discordId: account.discordId || "",
    discordLinkedAt: account.discordLinkedAt || null,
    online: Boolean(onlineClient),
    connectedAt: onlineClient ? onlineClient.connectedAt : null,
    stats: publicStats(account),
    statsHistory: publicStatsHistory(account)
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
    lastVersion: ""
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
  if (Object.hasOwn(source, "factionDisplay")) setStat("factionDisplay", cleanStatText(source.factionDisplay || ""));
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

  if (info.name) account.minecraftName = info.name;
  if (info.uuid) account.minecraftUuid = info.uuid;
  if (info.server) account.lastServer = info.server;
  if (info.channel) account.lastChannel = info.channel;
  if (info.version) account.lastVersion = info.version;
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

function onlinePlayersForResponse() {
  return uniqueOnlineClients().map(client => ({
    name: client.name,
    uuid: client.uuid,
    server: client.server,
    channel: client.channel,
    faction: (client.account && client.account.faction) || client.faction || "",
    version: client.version || (client.account && client.account.lastVersion) || "",
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

function normalizeReleaseVersion(value) {
  return String(value || "").trim().replace(/^v/i, "");
}

function isBetterUcJarAsset(name, url) {
  const value = `${name || ""} ${url || ""}`.toLowerCase();
  return value.includes("betteruc")
    && value.endsWith(".jar")
    && !value.includes("sources")
    && !value.includes("dev")
    && !value.includes("-all");
}

function releaseResponse(release, req) {
  return {
    ok: true,
    version: normalizeReleaseVersion(release.tagName),
    tagName: release.tagName,
    name: release.name || release.tagName,
    body: release.body || "",
    publishedAt: release.publishedAt || null,
    htmlUrl: release.htmlUrl || GITHUB_RELEASES_URL,
    downloadPage: absolutePublicUrl(req, "/download"),
    downloadUrl: absolutePublicUrl(req, "/download/latest.jar"),
    assetName: release.assetName || "",
    assetSize: release.assetSize || 0
  };
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
  const jarAsset = assets.find(asset => isBetterUcJarAsset(asset.name, asset.browser_download_url));
  const release = {
    tagName: String(data.tag_name || data.name || "").trim(),
    name: String(data.name || data.tag_name || "").trim(),
    body: String(data.body || ""),
    publishedAt: data.published_at || null,
    htmlUrl: String(data.html_url || GITHUB_RELEASES_URL),
    assetName: jarAsset ? String(jarAsset.name || "") : "",
    assetUrl: jarAsset ? String(jarAsset.browser_download_url || "") : "",
    assetSize: jarAsset ? Number(jarAsset.size || 0) : 0
  };

  if (!release.tagName) {
    throw new Error("Latest release has no tag name");
  }

  latestReleaseCache = { fetchedAt: now, release };
  return release;
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

  if (!release.assetUrl) {
    text(res, 404, "No betterUC jar asset found in latest release");
    return;
  }

  if (req.method === "HEAD") {
    res.writeHead(200, {
      "content-type": "application/java-archive",
      "content-disposition": `attachment; filename="${release.assetName || `betterUC-${normalizeReleaseVersion(release.tagName)}.jar`}"`,
      "cache-control": "no-cache"
    });
    res.end();
    return;
  }

  try {
    const upstream = await fetch(release.assetUrl, {
      headers: { "User-Agent": "betterUC-download-proxy" }
    });
    if (!upstream.ok || !upstream.body) {
      throw new Error(`Release asset HTTP ${upstream.status}`);
    }

    const headers = {
      "content-type": upstream.headers.get("content-type") || "application/java-archive",
      "content-disposition": `attachment; filename="${release.assetName || `betterUC-${normalizeReleaseVersion(release.tagName)}.jar`}"`,
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
      "access-control-allow-methods": "GET,POST,PATCH,OPTIONS",
      "access-control-allow-headers": "content-type,authorization,x-betteruc-token,x-betteruc-admin,x-betteruc-session"
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
      adminConfigured: Boolean(ADMIN_KEY),
      github: GITHUB_RELEASES_URL,
      time: Date.now()
    });
    return;
  }

  if (req.method === "GET" && url.pathname === "/api/releases/latest") {
    try {
      json(res, 200, releaseResponse(await fetchLatestRelease(), req));
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

  if (req.method === "GET" && url.pathname === "/api/admin/accounts") {
    if (!requireAdmin(req, res, url)) return;
    json(res, 200, {
      ok: true,
      accounts: store.accounts.map(adminAccount),
      players: onlinePlayersForResponse(),
      backups: await listStoreBackups(),
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
    const backup = await createStoreBackup("manual");
    json(res, 201, {
      ok: true,
      backup,
      backups: await listStoreBackups()
    });
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
      lastVersion: ""
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
      lastVersion: ""
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
      json(res, 401, { ok: false, error: "Access Code fehlt oder ist ungueltig." });
      return;
    }

    json(res, 200, {
      ok: true,
      players: onlinePlayersForResponse()
    });
    return;
  }

  if (req.method === "POST" && url.pathname === "/api/user/register") {
    if (isRateLimited(req, "user-register", 8, 60_000)) {
      json(res, 429, { ok: false, error: "Zu viele Versuche. Bitte kurz warten." });
      return;
    }

    const auth = authenticate(req, url);
    if (!auth || !auth.account || auth.account.id === "legacy") {
      json(res, 401, { ok: false, error: "Access Code fehlt oder ist ungueltig." });
      return;
    }

    const body = await readJsonBody(req);
    if (!isValidPassword(body.password)) {
      json(res, 400, { ok: false, error: "Passwort muss 6 bis 72 Zeichen lang sein." });
      return;
    }

    const account = auth.account;
    if (Object.hasOwn(body, "minecraftName")) {
      const minecraftName = cleanMinecraftName(body.minecraftName);
      if (minecraftName === null) {
        json(res, 400, { ok: false, error: "Minecraft-Name muss 3-16 Zeichen haben." });
        return;
      }
      if (minecraftName) account.minecraftName = minecraftName;
    }
    if (Object.hasOwn(body, "minecraftUuid")) {
      account.minecraftUuid = cleanSmallLabel(body.minecraftUuid || "", "");
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

  if (req.method === "POST" && url.pathname === "/api/user/stats") {
    const auth = authenticate(req, url);
    if (!auth || !auth.account || auth.account.id === "legacy") {
      json(res, 401, { ok: false, error: "Access Code fehlt oder ist ungueltig." });
      return;
    }

    const body = await readJsonBody(req, 16384);
    const account = auth.account;
    if (Object.hasOwn(body, "minecraftName")) {
      const minecraftName = cleanMinecraftName(body.minecraftName);
      if (minecraftName) account.minecraftName = minecraftName;
    }
    if (Object.hasOwn(body, "minecraftUuid")) account.minecraftUuid = cleanSmallLabel(body.minecraftUuid || "", "");
    if (Object.hasOwn(body, "server")) account.lastServer = cleanSmallLabel(body.server || "", "").toLowerCase();
    if (Object.hasOwn(body, "version")) account.lastVersion = cleanSmallLabel(body.version || "", "");
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
  if (pathname === "/impressum") {
    pathname = "/impressum.html";
  }
  if (pathname === "/datenschutz") {
    pathname = "/datenschutz.html";
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
    res.writeHead(200, {
      "content-type": MIME_TYPES.get(ext) || "application/octet-stream",
      "cache-control": ext === ".html" ? "no-cache" : "public, max-age=3600"
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

  if (req.method === "GET" && url.pathname === "/health") {
    json(res, 200, {
      ok: true,
      name: "betterUC Relay",
      clients: clients.size,
      accounts: store.accounts.length,
      ttlMs: PING_TTL_MS,
      time: Date.now()
    });
    return;
  }

  if (url.pathname === "/download/latest.jar") {
    await handleLatestJarDownload(req, res);
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
  updateAccountFromClient(client.account, client);
}

function sameAudience(sender, target, payload) {
  if (!sender || !target) return false;
  if (sender.server !== target.server) return false;

  const scope = cleanChannel(payload.scope || "channel");
  if (scope === "faction") {
    return Boolean(sender.faction)
      && Boolean(target.faction)
      && sender.faction.toLowerCase() === target.faction.toLowerCase();
  }
  if (scope === "global") {
    return true;
  }

  if (sender.role === "admin" || target.role === "admin") return true;

  return sender.channel === target.channel;
}

function broadcastPing(sender, payload) {
  const marker = {
    type: "ping",
    id: crypto.randomUUID(),
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

function handleWsMessage(client, raw) {
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
  }
}

function handleWsConnection(ws, req, auth, url) {
  const client = {
    ws,
    account: auth.account,
    authType: auth.type,
    role: cleanRole(auth.role),
    priority: rolePriority(auth.role),
    name: cleanMinecraftName(url.searchParams.get("name")) || auth.account.minecraftName || "unknown",
    uuid: cleanSmallLabel(url.searchParams.get("uuid") || "", ""),
    server: normalizeServerId(url.searchParams.get("server") || "unknown"),
    channel: cleanChannel(url.searchParams.get("channel") || "global"),
    faction: cleanSmallLabel(url.searchParams.get("faction") || "", ""),
    version: cleanSmallLabel(url.searchParams.get("version") || "", ""),
    connectedAt: nowIso()
  };

  const replacedServers = replaceExistingClientSessions(client);
  clients.add(client);
  updateAccountFromClient(client.account, client);
  ws.send(JSON.stringify({
    type: "welcome",
    verified: client.authType !== "legacy",
    role: client.role,
    priority: client.priority,
    admin: client.role === "admin",
    ttlMs: PING_TTL_MS
  }));
  broadcastPresence(client.server);
  for (const server of replacedServers) {
    if (server !== client.server) broadcastPresence(server);
  }

  ws.on("message", raw => handleWsMessage(client, raw));
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
  scheduleStoreBackups();
  startDiscordBot({
    getOnlinePlayers: onlinePlayersForResponse,
    getAccounts: () => store.accounts.map(adminAccount),
    findAccountByMinecraftName: name => {
      const account = findAccountByMinecraftName(name);
      return account ? adminAccount(account) : null;
    },
    createAccessAccount,
    resetAccessCodeByMinecraftName,
    revokeAccountByMinecraftName,
    linkDiscordAccountByCode,
    unlinkDiscordAccount,
    findAccountByDiscordId
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
    console.log(`betterUC platform listening on ${PORT}`);
  });
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
