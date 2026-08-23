"use strict";

const crypto = require("crypto");
const net = require("net");

const DEFAULT_API_BASE_URL = "https://api.unicacity.eu/api/factions";
const DEFAULT_SYNC_INTERVAL_MS = 10 * 60 * 1000;
const DEFAULT_QUERY_TIMEOUT_MS = 10 * 1000;
const MIN_SYNC_INTERVAL_MS = 60 * 1000;
const TEAM_SPEAK_ROSTER_RENDER_REVISION = "ts3-4";

function envBoolean(value, fallback = false) {
  if (value === undefined || value === null || String(value).trim() === "") return fallback;
  return ["1", "true", "yes", "on"].includes(String(value).trim().toLowerCase());
}

function positiveInteger(value, fallback, minimum = 1) {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= minimum ? parsed : fallback;
}

function safeLabel(value, fallback) {
  const normalized = String(value || "")
    .replace(/[\r\n\[\]]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  return normalized || fallback;
}

function queryEscape(value) {
  return String(value)
    .replace(/\\/g, "\\\\")
    .replace(/\//g, "\\/")
    .replace(/ /g, "\\s")
    .replace(/\|/g, "\\p")
    .replace(/\n/g, "\\n")
    .replace(/\r/g, "\\r")
    .replace(/\t/g, "\\t")
    .replace(/\f/g, "\\f")
    .replace(/\v/g, "\\v");
}

function queryUnescape(value) {
  const replacements = {
    "\\": "\\",
    "/": "/",
    s: " ",
    p: "|",
    a: "\x07",
    b: "\b",
    f: "\f",
    n: "\n",
    r: "\r",
    t: "\t",
    v: "\v"
  };
  return String(value).replace(/\\([\\/spabfnrtv])/g, (_, character) => replacements[character]);
}

function parseQueryRow(line) {
  const values = {};
  for (const token of String(line || "").split(" ")) {
    const separator = token.indexOf("=");
    if (separator <= 0) continue;
    values[token.slice(0, separator)] = queryUnescape(token.slice(separator + 1));
  }
  return values;
}

function normalizeMembers(payload) {
  if (!Array.isArray(payload)) {
    throw new Error("Die UnicaCity-API hat keine Mitgliederliste geliefert.");
  }

  const members = payload
    .filter(entry => entry && typeof entry.username === "string" && entry.username.trim())
    .map(entry => ({
      username: safeLabel(entry.username, "Unbekannt"),
      uuid: String(entry.uuid || "").replace(/[^a-fA-F0-9]/g, "").toLowerCase(),
      rankNumber: Number.isFinite(Number(entry.rankNumber)) ? Number(entry.rankNumber) : -1,
      rankName: safeLabel(entry.rankName, "Mitglied"),
      isLeader: entry.isLeader === true
    }))
    .sort((left, right) => (
      right.rankNumber - left.rankNumber
      || Number(right.isLeader) - Number(left.isLeader)
      || left.username.localeCompare(right.username, "de", { sensitivity: "base" })
    ));

  if (members.length === 0) {
    throw new Error("Die UnicaCity-API hat eine leere Mitgliederliste geliefert.");
  }
  return members;
}

function parseUnitOverrides(value) {
  const overrides = new Map();
  for (const entry of String(value || "").split(",")) {
    const separator = entry.indexOf(":");
    if (separator <= 0) continue;
    const username = entry.slice(0, separator).trim().toLowerCase();
    const unit = entry.slice(separator + 1).trim().toUpperCase().replace(/[^A-Z0-9_-]/g, "");
    if (username && unit) overrides.set(username, unit);
  }
  return overrides;
}

function memberUnit(member, overrides) {
  if (member.isLeader || member.rankNumber >= 5) return "CHIEF";
  return overrides.get(member.username.toLowerCase()) || "UCPD";
}

function formatPersonnelSection(members, options = {}) {
  const overrides = options.unitOverrides instanceof Map
    ? options.unitOverrides
    : parseUnitOverrides(options.unitOverrides);
  const slotLimit = positiveInteger(options.slotLimit, 42);
  const leaders = members.filter(member => member.isLeader || member.rankNumber >= 5);
  const council = members.filter(member => !leaders.includes(member) && member.rankNumber === 4);
  const regular = members.filter(member => !leaders.includes(member) && !council.includes(member));
  const lines = [];

  const addRows = entries => {
    for (const member of entries) {
      lines.push(`[${memberUnit(member, overrides)}] | ${member.rankNumber} | ${member.username}`);
    }
  };

  if (leaders.length > 0) {
    lines.push("Leader");
    addRows(leaders);
  }
  if (council.length > 0) {
    lines.push("Polizeirat");
    addRows(council);
  }
  if (regular.length > 0) {
    lines.push("Member");
    let previousRank = null;
    for (const member of regular) {
      const rankKey = `${member.rankNumber}:${member.rankName}`;
      if (rankKey !== previousRank) {
        lines.push(member.rankName);
        previousRank = rankKey;
      }
      lines.push(`[${memberUnit(member, overrides)}] | ${member.rankNumber} | ${member.username}`);
    }
  }
  lines.push(`Slots: ${members.length}/${slotLimit}`);
  return lines.join("\n");
}

function rosterVersion(members) {
  const value = members
    .map(member => `${member.username}:${member.uuid}:${member.rankNumber}:${member.rankName}:${member.isLeader ? 1 : 0}`)
    .join("|");
  return crypto.createHash("sha256").update(value).digest("hex").slice(0, 12);
}

function formatPersonnelImageEmbed(members, options = {}) {
  const baseUrl = String(options.publicBaseUrl || "https://betteruc.de").replace(/\/+$/, "");
  const version = `${TEAM_SPEAK_ROSTER_RENDER_REVISION}-${rosterVersion(members)}`;
  return `[url=${baseUrl}/polizei/mitglieder][img]${baseUrl}/api/teamspeak/police-roster.png?v=${version}[/img][/url]`;
}

function replacePersonnelSection(currentDescription, personnelSection, options = {}) {
  const current = String(currentDescription || "");
  const startLabel = String(options.startLabel || "PERSONALAKTE").trim();
  const endLabel = String(options.endLabel || "STRAFZAHLUNGEN").trim();
  const searchable = current.toLocaleUpperCase("de-DE");
  const startLabelIndex = searchable.indexOf(startLabel.toLocaleUpperCase("de-DE"));
  if (startLabelIndex < 0) {
    throw new Error(`Startmarke "${startLabel}" wurde in der Channelbeschreibung nicht gefunden.`);
  }

  const startTagEnd = searchable.indexOf("[/SIZE]", startLabelIndex);
  if (startTagEnd < 0) {
    throw new Error(`Nach der Startmarke "${startLabel}" fehlt ein schließendes [size]-Tag.`);
  }

  let contentStart = startTagEnd + "[/size]".length;
  const followingSizeTag = current.slice(contentStart).match(/^\s*\[size=[^\]]+\]/i);
  if (followingSizeTag) contentStart += followingSizeTag[0].length;

  const endLabelIndex = searchable.indexOf(endLabel.toLocaleUpperCase("de-DE"), contentStart);
  if (endLabelIndex < 0) {
    throw new Error(`Endmarke "${endLabel}" wurde in der Channelbeschreibung nicht gefunden.`);
  }

  let contentEnd = searchable.lastIndexOf("[SIZE=", endLabelIndex);
  if (contentEnd < contentStart) contentEnd = endLabelIndex;

  const prefix = current.slice(0, contentStart).replace(/\s*$/, "");
  const suffix = current.slice(contentEnd).replace(/^\s*/, "");
  return `${prefix}\n${String(personnelSection || "").trim()}\n${suffix}`;
}

async function fetchJson(url, timeoutMs, fetchImpl) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  timeout.unref?.();
  try {
    const response = await fetchImpl(url, {
      headers: {
        Accept: "application/json",
        "User-Agent": "betterUC-TeamSpeak-Faction-Sync/1.0"
      },
      signal: controller.signal
    });
    if (!response.ok) {
      throw new Error(`UnicaCity-API antwortete mit HTTP ${response.status}.`);
    }
    return await response.json();
  } finally {
    clearTimeout(timeout);
  }
}

function connectQuery(config) {
  const socket = net.createConnection({ host: config.host, port: config.port });
  socket.setEncoding("utf8");
  socket.setTimeout(config.timeoutMs);

  let input = "";
  let pending = null;
  let bannerLineCount = 0;
  let readySettled = false;
  let resolveReady;
  let rejectReady;
  const connected = new Promise((resolve, reject) => {
    resolveReady = resolve;
    rejectReady = reject;
  });
  const readyTimeout = setTimeout(() => {
    if (readySettled) return;
    readySettled = true;
    rejectReady(new Error("TeamSpeak ServerQuery hat keine Begrüßung gesendet."));
    socket.destroy();
  }, config.timeoutMs);
  readyTimeout.unref?.();

  const finishReady = error => {
    if (readySettled) return;
    readySettled = true;
    clearTimeout(readyTimeout);
    if (error) rejectReady(error);
    else resolveReady();
  };

  const failPending = error => {
    if (!pending) return;
    const current = pending;
    pending = null;
    clearTimeout(current.timeout);
    current.reject(error);
  };

  socket.on("data", chunk => {
    input += chunk;
    let newlineIndex;
    while ((newlineIndex = input.indexOf("\n")) >= 0) {
      const line = input.slice(0, newlineIndex).replace(/^\r|\r$/g, "");
      input = input.slice(newlineIndex + 1);
      if (!pending) {
        if (line.trim()) bannerLineCount += 1;
        if (line.includes("Welcome to the TeamSpeak") || bannerLineCount >= 2) finishReady();
        continue;
      }
      if (!line.startsWith("error ")) {
        if (line) pending.lines.push(line);
        continue;
      }

      const current = pending;
      pending = null;
      clearTimeout(current.timeout);
      const id = Number(line.match(/\bid=(\d+)/)?.[1] || -1);
      const message = line.match(/\bmsg=([^ ]*)/)?.[1] || "unknown";
      if (id === 0) current.resolve(current.lines);
      else current.reject(new Error(`TeamSpeak ${current.label} fehlgeschlagen (Fehler ${id}: ${message}).`));
    }
  });
  socket.on("timeout", () => socket.destroy(new Error("TeamSpeak ServerQuery Zeitüberschreitung.")));
  socket.on("error", error => {
    finishReady(error);
    failPending(error);
  });
  socket.on("close", () => {
    const error = new Error("TeamSpeak ServerQuery hat die Verbindung beendet.");
    finishReady(error);
    failPending(error);
  });

  const command = (value, label) => {
    if (pending) return Promise.reject(new Error("TeamSpeak ServerQuery-Befehle dürfen nicht parallel laufen."));
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        if (!pending) return;
        pending = null;
        reject(new Error(`TeamSpeak ${label} hat das Zeitlimit überschritten.`));
        socket.destroy();
      }, config.timeoutMs);
      timeout.unref?.();
      pending = { resolve, reject, timeout, label, lines: [] };
      socket.write(`${value}\n`);
    });
  };

  return { socket, connected, command };
}

async function updateChannelDescription(config, description) {
  const query = connectQuery(config);
  try {
    await query.connected;
    await query.command(
      `login client_login_name=${queryEscape(config.username)} client_login_password=${queryEscape(config.password)}`,
      "Anmeldung"
    );
    const useTarget = config.virtualServerId
      ? `sid=${config.virtualServerId}`
      : `port=${config.virtualServerPort}`;
    await query.command(`use ${useTarget}`, "Serverauswahl");
    await query.command(
      `channeledit cid=${config.channelId} channel_description=${queryEscape(description)}`,
      "Channel-Aktualisierung"
    );
  } finally {
    query.socket.end();
  }
}

async function synchronizeFactionChannel(config, members) {
  const query = connectQuery(config);
  try {
    await query.connected;
    await query.command(
      `login client_login_name=${queryEscape(config.username)} client_login_password=${queryEscape(config.password)}`,
      "Anmeldung"
    );
    const useTarget = config.virtualServerId
      ? `sid=${config.virtualServerId}`
      : `port=${config.virtualServerPort}`;
    await query.command(`use ${useTarget}`, "Serverauswahl");
    const responseLines = await query.command(`channelinfo cid=${config.channelId}`, "Channel-Abfrage");
    const infoLine = responseLines.find(line => line.includes("channel_description="));
    const currentDescription = infoLine ? parseQueryRow(infoLine).channel_description : undefined;
    if (currentDescription === undefined) {
      throw new Error("TeamSpeak hat keine lesbare Channelbeschreibung geliefert.");
    }

    const personnelSection = config.renderMode === "image"
      ? formatPersonnelImageEmbed(members, { publicBaseUrl: config.publicBaseUrl })
      : formatPersonnelSection(members, {
        slotLimit: config.slotLimit,
        unitOverrides: config.unitOverrides
      });
    const desiredDescription = replacePersonnelSection(currentDescription, personnelSection, {
      startLabel: config.sectionStartLabel,
      endLabel: config.sectionEndLabel
    });
    if (desiredDescription === currentDescription) {
      return { updated: false, description: currentDescription };
    }

    await query.command(
      `channeledit cid=${config.channelId} channel_description=${queryEscape(desiredDescription)}`,
      "Channel-Aktualisierung"
    );
    return { updated: true, description: desiredDescription };
  } finally {
    query.socket.end();
  }
}

function readConfig(env) {
  const slug = String(env.TEAMSPEAK_FACTION_SLUG || "police").trim().toLowerCase();
  const apiBaseUrl = String(env.TEAMSPEAK_FACTION_API_BASE_URL || DEFAULT_API_BASE_URL).replace(/\/+$/, "");
  return {
    enabled: envBoolean(env.TEAMSPEAK_FACTION_SYNC_ENABLED, false),
    slug,
    apiUrl: `${apiBaseUrl}/${encodeURIComponent(slug)}/members`,
    slotLimit: positiveInteger(env.TEAMSPEAK_FACTION_SLOT_LIMIT, 42),
    unitOverrides: parseUnitOverrides(env.TEAMSPEAK_FACTION_UNIT_OVERRIDES),
    renderMode: String(env.TEAMSPEAK_FACTION_RENDER_MODE || "image").trim().toLowerCase(),
    publicBaseUrl: String(env.PUBLIC_BASE_URL || "https://betteruc.de").replace(/\/+$/, ""),
    sectionStartLabel: env.TEAMSPEAK_FACTION_SECTION_START || "PERSONALAKTE",
    sectionEndLabel: env.TEAMSPEAK_FACTION_SECTION_END || "STRAFZAHLUNGEN",
    syncIntervalMs: positiveInteger(
      env.TEAMSPEAK_FACTION_SYNC_MS,
      DEFAULT_SYNC_INTERVAL_MS,
      MIN_SYNC_INTERVAL_MS
    ),
    timeoutMs: positiveInteger(env.TEAMSPEAK_QUERY_TIMEOUT_MS, DEFAULT_QUERY_TIMEOUT_MS, 1000),
    host: String(env.TEAMSPEAK_QUERY_HOST || "").trim(),
    port: positiveInteger(env.TEAMSPEAK_QUERY_PORT, 10011),
    username: String(env.TEAMSPEAK_QUERY_USERNAME || "").trim(),
    password: String(env.TEAMSPEAK_QUERY_PASSWORD || ""),
    virtualServerId: positiveInteger(env.TEAMSPEAK_VIRTUAL_SERVER_ID, 0),
    virtualServerPort: positiveInteger(env.TEAMSPEAK_VIRTUAL_SERVER_PORT, 9987),
    channelId: positiveInteger(env.TEAMSPEAK_CHANNEL_ID, 0)
  };
}

function startTeamSpeakFactionSync(options = {}) {
  const env = options.env || process.env;
  const fetchImpl = options.fetchImpl || globalThis.fetch;
  const logger = options.logger || console;
  const config = readConfig(env);

  if (!config.enabled) {
    return { enabled: false, stop() {} };
  }
  if (typeof fetchImpl !== "function") {
    logger.warn("TeamSpeak-Fraktionssync deaktiviert: fetch ist in dieser Node-Version nicht verfügbar.");
    return { enabled: false, stop() {} };
  }

  const missing = [];
  if (!config.host) missing.push("TEAMSPEAK_QUERY_HOST");
  if (!config.username) missing.push("TEAMSPEAK_QUERY_USERNAME");
  if (!config.password) missing.push("TEAMSPEAK_QUERY_PASSWORD");
  if (!config.channelId) missing.push("TEAMSPEAK_CHANNEL_ID");
  if (missing.length > 0) {
    logger.warn(`TeamSpeak-Fraktionssync deaktiviert: ${missing.join(", ")} fehlt.`);
    return { enabled: false, stop() {} };
  }

  let timer = null;
  let stopped = false;
  let running = false;

  const syncNow = async () => {
    if (stopped || running) return false;
    running = true;
    try {
      const payload = await fetchJson(config.apiUrl, config.timeoutMs, fetchImpl);
      const members = normalizeMembers(payload);
      const result = await synchronizeFactionChannel(config, members);
      if (result.updated) {
        logger.log(`TeamSpeak-Polizeiliste synchronisiert (${members.length} Mitglieder).`);
      }
      return result.updated;
    } catch (error) {
      logger.warn("TeamSpeak-Polizeiliste konnte nicht synchronisiert werden:", error.message);
      return false;
    } finally {
      running = false;
    }
  };

  logger.log(`TeamSpeak-Fraktionssync aktiv (${config.slug}, alle ${Math.round(config.syncIntervalMs / 60000)} Minuten).`);
  syncNow();
  timer = setInterval(syncNow, config.syncIntervalMs);
  timer.unref?.();

  return {
    enabled: true,
    syncNow,
    stop() {
      stopped = true;
      clearInterval(timer);
    }
  };
}

module.exports = {
  formatPersonnelImageEmbed,
  formatPersonnelSection,
  normalizeMembers,
  parseUnitOverrides,
  queryEscape,
  queryUnescape,
  readConfig,
  replacePersonnelSection,
  rosterVersion,
  startTeamSpeakFactionSync,
  synchronizeFactionChannel,
  TEAM_SPEAK_ROSTER_RENDER_REVISION,
  updateChannelDescription
};
