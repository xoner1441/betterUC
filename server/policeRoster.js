"use strict";

const crypto = require("crypto");
const sharp = require("sharp");
const {
  normalizeMembers,
  parseUnitOverrides,
  rosterVersion
} = require("./teamSpeakFactionSync");

const DEFAULT_API_URL = "https://api.unicacity.eu/api/factions/police/members";
const DEFAULT_HEAD_BASE_URL = "https://mc-heads.net/head";
const DEFAULT_ROSTER_CACHE_MS = 5 * 60 * 1000;
const DEFAULT_HEAD_CACHE_MS = 24 * 60 * 60 * 1000;
const IMAGE_WIDTH = 760;

function xml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

function positiveInteger(value, fallback, minimum = 1) {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= minimum ? parsed : fallback;
}

function normalizedUuid(value) {
  const uuid = String(value || "").replace(/[^a-fA-F0-9]/g, "").toLowerCase();
  return uuid.length === 32 ? uuid : "";
}

function groupMembers(members) {
  const leaders = members.filter(member => member.isLeader || member.rankNumber >= 5);
  const council = members.filter(member => !leaders.includes(member) && member.rankNumber === 4);
  const regular = members.filter(member => !leaders.includes(member) && !council.includes(member));
  const groups = [];
  if (leaders.length) groups.push({ key: "leader", label: "LEITUNG", members: leaders });
  if (council.length) groups.push({ key: "council", label: "POLIZEIRAT", members: council });

  const rankGroups = new Map();
  for (const member of regular) {
    const key = `${member.rankNumber}:${member.rankName}`;
    if (!rankGroups.has(key)) {
      rankGroups.set(key, {
        key: `rank-${member.rankNumber}`,
        label: String(member.rankName || "MEMBER").toLocaleUpperCase("de-DE"),
        members: []
      });
    }
    rankGroups.get(key).members.push(member);
  }
  groups.push(...rankGroups.values());
  return groups;
}

function memberUnit(member, unitOverrides) {
  if (member.isLeader || member.rankNumber >= 5) return "CHIEF";
  return unitOverrides.get(member.username.toLowerCase()) || "UCPD";
}

function colorForName(name) {
  const digest = crypto.createHash("sha256").update(String(name)).digest();
  return `rgb(${70 + digest[0] % 80},${100 + digest[1] % 90},${130 + digest[2] % 90})`;
}

function placeholderHeadSvg(username) {
  const background = colorForName(username);
  const initial = xml(String(username || "?").slice(0, 1).toUpperCase());
  return Buffer.from(`
    <svg width="128" height="128" xmlns="http://www.w3.org/2000/svg">
      <rect width="128" height="128" fill="${background}"/>
      <rect x="10" y="10" width="108" height="108" fill="none" stroke="#111820" stroke-width="8"/>
      <text x="64" y="84" text-anchor="middle" font-family="Arial, sans-serif" font-size="62" font-weight="800" fill="#fff">${initial}</text>
    </svg>
  `);
}

async function mapLimit(values, limit, callback) {
  const results = new Array(values.length);
  let nextIndex = 0;
  const workers = Array.from({ length: Math.min(limit, values.length) }, async () => {
    while (nextIndex < values.length) {
      const index = nextIndex++;
      results[index] = await callback(values[index], index);
    }
  });
  await Promise.all(workers);
  return results;
}

function createPoliceRosterService(options = {}) {
  const fetchImpl = options.fetchImpl || globalThis.fetch;
  const apiUrl = String(options.apiUrl || DEFAULT_API_URL);
  const headBaseUrl = String(options.headBaseUrl || DEFAULT_HEAD_BASE_URL).replace(/\/+$/, "");
  const slotLimit = positiveInteger(options.slotLimit, 42);
  const rosterCacheMs = positiveInteger(options.rosterCacheMs, DEFAULT_ROSTER_CACHE_MS, 1000);
  const headCacheMs = positiveInteger(options.headCacheMs, DEFAULT_HEAD_CACHE_MS, 1000);
  const unitOverrides = options.unitOverrides instanceof Map
    ? options.unitOverrides
    : parseUnitOverrides(options.unitOverrides || "FABI1441:SWAT,mteii:SWAT,74nici:SWAT");
  let rosterCache = null;
  let imageCache = null;
  const headCache = new Map();

  if (typeof fetchImpl !== "function") {
    throw new Error("Die Police-Roster-API benötigt eine Node-Version mit fetch-Unterstützung.");
  }

  async function fetchBuffer(url, accept) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), positiveInteger(options.timeoutMs, 10_000));
    timeout.unref?.();
    try {
      const response = await fetchImpl(url, {
        headers: {
          Accept: accept,
          "User-Agent": "betterUC-Police-Roster/1.0"
        },
        signal: controller.signal
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return Buffer.from(await response.arrayBuffer());
    } finally {
      clearTimeout(timeout);
    }
  }

  async function getRoster(force = false) {
    const now = Date.now();
    if (!force && rosterCache && rosterCache.expiresAt > now) return rosterCache.value;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), positiveInteger(options.timeoutMs, 10_000));
    timeout.unref?.();
    try {
      const response = await fetchImpl(apiUrl, {
        headers: {
          Accept: "application/json",
          "User-Agent": "betterUC-Police-Roster/1.0"
        },
        signal: controller.signal
      });
      if (!response.ok) throw new Error(`UnicaCity-API antwortete mit HTTP ${response.status}.`);
      const members = normalizeMembers(await response.json()).map(member => ({
        ...member,
        uuid: normalizedUuid(member.uuid),
        unit: memberUnit(member, unitOverrides)
      }));
      const value = {
        members,
        groups: groupMembers(members),
        count: members.length,
        slotLimit,
        hash: rosterVersion(members),
        source: apiUrl,
        generatedAt: new Date().toISOString()
      };
      rosterCache = { value, expiresAt: now + rosterCacheMs };
      return value;
    } catch (error) {
      if (rosterCache) return rosterCache.value;
      throw error;
    } finally {
      clearTimeout(timeout);
    }
  }

  async function getHead(uuid, username = "?") {
    const normalized = normalizedUuid(uuid);
    const cacheKey = normalized || `name:${String(username).toLowerCase()}`;
    const cached = headCache.get(cacheKey);
    if (cached && cached.expiresAt > Date.now()) return cached.buffer;

    let source = placeholderHeadSvg(username);
    if (normalized) {
      try {
        source = await fetchBuffer(`${headBaseUrl}/${normalized}/128`, "image/*");
      } catch (error) {
        console.warn(`Minecraft-Kopf für ${username} konnte nicht geladen werden: ${error.message}`);
      }
    }
    const buffer = await sharp(source)
      .resize(128, 128, { fit: "contain", kernel: "nearest" })
      .png()
      .toBuffer();
    headCache.set(cacheKey, { buffer, expiresAt: Date.now() + headCacheMs });
    return buffer;
  }

  async function renderImage(roster) {
    const columns = 2;
    const pagePadding = 34;
    const cardGap = 14;
    const cardWidth = Math.floor((IMAGE_WIDTH - pagePadding * 2 - cardGap) / columns);
    const cardHeight = 96;
    const sectionHeaderHeight = 44;
    const sectionGap = 22;
    const headerHeight = 222;
    const footerHeight = 72;
    let contentHeight = 0;
    for (const group of roster.groups) {
      contentHeight += sectionHeaderHeight + Math.ceil(group.members.length / columns) * (cardHeight + cardGap) + sectionGap;
    }
    const height = headerHeight + contentHeight + footerHeight;
    const fragments = [];
    const composites = [];
    const resizedHeadEntries = await mapLimit(roster.members, 6, async member => {
      const head = await getHead(member.uuid, member.username);
      const resized = await sharp(head).resize(64, 64, { kernel: "nearest" }).png().toBuffer();
      return [member.username.toLowerCase(), resized];
    });
    const resizedHeads = new Map(resizedHeadEntries);
    let y = headerHeight;

    for (const group of roster.groups) {
      fragments.push(`
        <rect x="34" y="${y}" width="692" height="34" rx="2" fill="${group.key === "leader" ? "#e8bd4b" : "#54bde9"}" stroke="#111820" stroke-width="3"/>
        <text x="50" y="${y + 24}" font-family="Arial, sans-serif" font-size="17" font-weight="900" letter-spacing="1.8" fill="#111820">${xml(group.label)}</text>
        <text x="708" y="${y + 24}" text-anchor="end" font-family="Arial, sans-serif" font-size="14" font-weight="800" fill="#111820">${group.members.length}</text>
      `);
      y += sectionHeaderHeight;
      for (let index = 0; index < group.members.length; index += 1) {
        const member = group.members[index];
        const column = index % columns;
        const row = Math.floor(index / columns);
        const x = pagePadding + column * (cardWidth + cardGap);
        const cardY = y + row * (cardHeight + cardGap);
        fragments.push(`
          <rect x="${x + 5}" y="${cardY + 5}" width="${cardWidth}" height="${cardHeight}" rx="4" fill="#111820" opacity="0.22"/>
          <rect x="${x}" y="${cardY}" width="${cardWidth}" height="${cardHeight}" rx="4" fill="#f9fbfd" stroke="#111820" stroke-width="3"/>
          <rect x="${x + 12}" y="${cardY + 12}" width="72" height="72" fill="#dbeef8" stroke="#111820" stroke-width="2"/>
          <text x="${x + 98}" y="${cardY + 34}" font-family="Arial, sans-serif" font-size="19" font-weight="900" fill="#111820">${xml(member.username)}</text>
          <text x="${x + 98}" y="${cardY + 57}" font-family="Arial, sans-serif" font-size="13" font-weight="700" fill="#52626f">${xml(member.rankName)}</text>
          <rect x="${x + 98}" y="${cardY + 67}" width="66" height="19" rx="3" fill="#111820"/>
          <text x="${x + 131}" y="${cardY + 81}" text-anchor="middle" font-family="Arial, sans-serif" font-size="11" font-weight="900" fill="#ffffff">${xml(member.unit)}</text>
          <text x="${x + cardWidth - 14}" y="${cardY + 81}" text-anchor="end" font-family="Arial, sans-serif" font-size="12" font-weight="800" fill="#277da8">RANG ${member.rankNumber}</text>
        `);
        composites.push({
          input: resizedHeads.get(member.username.toLowerCase()),
          left: x + 16,
          top: cardY + 16,
          blend: "over"
        });
      }
      y += Math.ceil(group.members.length / columns) * (cardHeight + cardGap) + sectionGap;
    }

    const svg = Buffer.from(`
      <svg width="${IMAGE_WIDTH}" height="${height}" xmlns="http://www.w3.org/2000/svg">
        <rect width="${IMAGE_WIDTH}" height="${height}" fill="#eef6fa"/>
        <rect x="0" y="0" width="${IMAGE_WIDTH}" height="16" fill="#111820"/>
        <rect x="0" y="16" width="${IMAGE_WIDTH}" height="9" fill="#2ba8e0"/>
        <text x="34" y="75" font-family="Arial, sans-serif" font-size="16" font-weight="900" letter-spacing="2.4" fill="#2787b5">UNICACITY POLICE DEPARTMENT</text>
        <text x="34" y="125" font-family="Arial, sans-serif" font-size="42" font-weight="900" letter-spacing="1.2" fill="#111820">MITGLIEDERÜBERSICHT</text>
        <text x="34" y="157" font-family="Arial, sans-serif" font-size="17" font-weight="600" fill="#52626f">Aktueller Personalbestand und verfügbare Dienstnummern</text>
        <rect x="34" y="180" width="692" height="4" fill="#111820"/>
        <rect x="558" y="147" width="168" height="31" rx="3" fill="#ffffff" stroke="#111820" stroke-width="3"/>
        <text x="642" y="168" text-anchor="middle" font-family="Arial, sans-serif" font-size="14" font-weight="900" fill="#111820">SLOTS ${roster.count}/${roster.slotLimit}</text>
        ${fragments.join("\n")}
        <rect x="0" y="${height - footerHeight}" width="${IMAGE_WIDTH}" height="${footerHeight}" fill="#111820"/>
        <text x="34" y="${height - 38}" font-family="Arial, sans-serif" font-size="15" font-weight="800" fill="#ffffff">Vollständige Ansicht: betteruc.de/polizei/mitglieder</text>
        <text x="34" y="${height - 17}" font-family="Arial, sans-serif" font-size="11" font-weight="600" fill="#94a9b5">Quelle: offizielle UnicaCity Fraktions-API · Stand ${xml(new Date(roster.generatedAt).toLocaleString("de-DE"))}</text>
      </svg>
    `);
    const base = await sharp(svg).png().toBuffer();
    return sharp(base).composite(composites).png({ compressionLevel: 9 }).toBuffer();
  }

  async function getImage() {
    const roster = await getRoster();
    if (imageCache && imageCache.hash === roster.hash) return imageCache;
    const buffer = await renderImage(roster);
    imageCache = { buffer, hash: roster.hash, generatedAt: roster.generatedAt };
    return imageCache;
  }

  return {
    getHead,
    getImage,
    getRoster,
    renderImage
  };
}

module.exports = {
  createPoliceRosterService,
  groupMembers,
  normalizedUuid,
  placeholderHeadSvg
};
