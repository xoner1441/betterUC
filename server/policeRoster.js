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

const PIXEL_GLYPHS = Object.freeze({
  " ": "00000/00000/00000/00000/00000/00000/00000",
  A: "01110/10001/10001/11111/10001/10001/10001",
  B: "11110/10001/10001/11110/10001/10001/11110",
  C: "01111/10000/10000/10000/10000/10000/01111",
  D: "11110/10001/10001/10001/10001/10001/11110",
  E: "11111/10000/10000/11110/10000/10000/11111",
  F: "11111/10000/10000/11110/10000/10000/10000",
  G: "01111/10000/10000/10111/10001/10001/01111",
  H: "10001/10001/10001/11111/10001/10001/10001",
  I: "11111/00100/00100/00100/00100/00100/11111",
  J: "00111/00010/00010/00010/10010/10010/01100",
  K: "10001/10010/10100/11000/10100/10010/10001",
  L: "10000/10000/10000/10000/10000/10000/11111",
  M: "10001/11011/10101/10101/10001/10001/10001",
  N: "10001/11001/10101/10011/10001/10001/10001",
  O: "01110/10001/10001/10001/10001/10001/01110",
  P: "11110/10001/10001/11110/10000/10000/10000",
  Q: "01110/10001/10001/10001/10101/10010/01101",
  R: "11110/10001/10001/11110/10100/10010/10001",
  S: "01111/10000/10000/01110/00001/00001/11110",
  T: "11111/00100/00100/00100/00100/00100/00100",
  U: "10001/10001/10001/10001/10001/10001/01110",
  V: "10001/10001/10001/10001/10001/01010/00100",
  W: "10001/10001/10001/10101/10101/10101/01010",
  X: "10001/10001/01010/00100/01010/10001/10001",
  Y: "10001/10001/01010/00100/00100/00100/00100",
  Z: "11111/00001/00010/00100/01000/10000/11111",
  0: "01110/10001/10011/10101/11001/10001/01110",
  1: "00100/01100/00100/00100/00100/00100/01110",
  2: "01110/10001/00001/00010/00100/01000/11111",
  3: "11110/00001/00001/01110/00001/00001/11110",
  4: "00010/00110/01010/10010/11111/00010/00010",
  5: "11111/10000/10000/11110/00001/00001/11110",
  6: "01110/10000/10000/11110/10001/10001/01110",
  7: "11111/00001/00010/00100/01000/01000/01000",
  8: "01110/10001/10001/01110/10001/10001/01110",
  9: "01110/10001/10001/01111/00001/00001/01110",
  "/": "00001/00010/00010/00100/01000/01000/10000",
  ":": "00000/00100/00100/00000/00100/00100/00000",
  ".": "00000/00000/00000/00000/00000/00100/00100",
  "-": "00000/00000/00000/11111/00000/00000/00000",
  _: "00000/00000/00000/00000/00000/00000/11111",
  "~": "00000/00000/01001/10110/00000/00000/00000",
  "?": "01110/10001/00001/00010/00100/00000/00100"
});

function normalizePixelText(value) {
  return String(value ?? "")
    .replace(/Ä/g, "AE").replace(/Ö/g, "OE").replace(/Ü/g, "UE")
    .replace(/ä/g, "AE").replace(/ö/g, "OE").replace(/ü/g, "UE")
    .replace(/ß/g, "SS")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toUpperCase()
    .replace(/[^A-Z0-9 _./:~-]/g, "?");
}

function pixelTextSvg(value, x, y, options = {}) {
  const scale = positiveInteger(options.scale, 1);
  const maxChars = positiveInteger(options.maxChars, 0, 0);
  let text = normalizePixelText(value);
  if (maxChars && text.length > maxChars) text = `${text.slice(0, Math.max(1, maxChars - 1))}~`;
  const step = 6 * scale;
  const width = text.length ? text.length * step - scale : 0;
  let cursor = Number(x);
  if (options.align === "center") cursor -= width / 2;
  if (options.align === "right") cursor -= width;
  const rectangles = [];
  for (const character of text) {
    const rows = (PIXEL_GLYPHS[character] || PIXEL_GLYPHS["?"]).split("/");
    for (let row = 0; row < rows.length; row += 1) {
      for (let column = 0; column < rows[row].length; column += 1) {
        if (rows[row][column] === "1") {
          rectangles.push(`<rect x="${cursor + column * scale}" y="${Number(y) + row * scale}" width="${scale}" height="${scale}"/>`);
        }
      }
    }
    cursor += step;
  }
  return `<g fill="${options.color || "#111820"}">${rectangles.join("")}</g>`;
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
  return Buffer.from(`
    <svg width="128" height="128" xmlns="http://www.w3.org/2000/svg">
      <rect width="128" height="128" fill="${background}"/>
      <rect x="10" y="10" width="108" height="108" fill="none" stroke="#111820" stroke-width="8"/>
      <rect x="30" y="40" width="22" height="22" fill="#111820"/>
      <rect x="76" y="40" width="22" height="22" fill="#111820"/>
      <rect x="42" y="82" width="44" height="10" fill="#ffffff"/>
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
    const columns = 4;
    const pagePadding = 24;
    const cardGap = 10;
    const rowGap = 8;
    const cardWidth = Math.floor((IMAGE_WIDTH - pagePadding * 2 - cardGap * (columns - 1)) / columns);
    const cardHeight = 82;
    const sectionHeaderHeight = 32;
    const sectionGap = 14;
    const headerHeight = 176;
    const footerHeight = 64;
    let contentHeight = 0;
    for (const group of roster.groups) {
      contentHeight += sectionHeaderHeight + Math.ceil(group.members.length / columns) * (cardHeight + rowGap) + sectionGap;
    }
    const height = headerHeight + contentHeight + footerHeight;
    const fragments = [];
    const composites = [];
    const resizedHeadEntries = await mapLimit(roster.members, 6, async member => {
      const head = await getHead(member.uuid, member.username);
      const resized = await sharp(head).resize(46, 46, { kernel: "nearest" }).png().toBuffer();
      return [member.username.toLowerCase(), resized];
    });
    const resizedHeads = new Map(resizedHeadEntries);
    let y = headerHeight;

    for (const group of roster.groups) {
      fragments.push(`
        <rect x="${pagePadding}" y="${y}" width="${IMAGE_WIDTH - pagePadding * 2}" height="26" rx="2" fill="${group.key === "leader" ? "#e8bd4b" : "#54bde9"}" stroke="#111820" stroke-width="2"/>
        ${pixelTextSvg(group.label, pagePadding + 12, y + 6, { scale: 2, maxChars: 22 })}
        ${pixelTextSvg(group.members.length, IMAGE_WIDTH - pagePadding - 12, y + 6, { scale: 2, align: "right" })}
      `);
      y += sectionHeaderHeight;
      for (let index = 0; index < group.members.length; index += 1) {
        const member = group.members[index];
        const column = index % columns;
        const row = Math.floor(index / columns);
        const x = pagePadding + column * (cardWidth + cardGap);
        const cardY = y + row * (cardHeight + rowGap);
        fragments.push(`
          <rect x="${x + 4}" y="${cardY + 4}" width="${cardWidth}" height="${cardHeight}" rx="3" fill="#111820" opacity="0.18"/>
          <rect x="${x}" y="${cardY}" width="${cardWidth}" height="${cardHeight}" rx="3" fill="#f9fbfd" stroke="#111820" stroke-width="2"/>
          <rect x="${x + (cardWidth - 50) / 2}" y="${cardY + 5}" width="50" height="50" fill="#dbeef8" stroke="#111820" stroke-width="2"/>
          ${pixelTextSvg(member.username, x + cardWidth / 2, cardY + 62, { scale: 2, maxChars: 13, align: "center" })}
        `);
        composites.push({
          input: resizedHeads.get(member.username.toLowerCase()),
          left: Math.round(x + (cardWidth - 46) / 2),
          top: cardY + 7,
          blend: "over"
        });
      }
      y += Math.ceil(group.members.length / columns) * (cardHeight + rowGap) + sectionGap;
    }

    const svg = Buffer.from(`
      <svg width="${IMAGE_WIDTH}" height="${height}" xmlns="http://www.w3.org/2000/svg">
        <rect width="${IMAGE_WIDTH}" height="${height}" fill="#eef6fa"/>
        <rect x="0" y="0" width="${IMAGE_WIDTH}" height="16" fill="#111820"/>
        <rect x="0" y="16" width="${IMAGE_WIDTH}" height="8" fill="#2ba8e0"/>
        ${pixelTextSvg("UNICACITY POLICE DEPARTMENT", 26, 40, { scale: 2, color: "#2787b5" })}
        ${pixelTextSvg("POLIZEI", 26, 70, { scale: 7 })}
        ${pixelTextSvg("MITGLIEDERUEBERSICHT", 26, 130, { scale: 2, color: "#52626f" })}
        <rect x="540" y="62" width="194" height="64" rx="3" fill="#ffffff" stroke="#111820" stroke-width="3"/>
        ${pixelTextSvg(`${roster.count}/${roster.slotLimit}`, 637, 73, { scale: 5, align: "center" })}
        ${pixelTextSvg("MITGLIEDER", 637, 111, { scale: 1, align: "center", color: "#2787b5" })}
        <rect x="24" y="156" width="712" height="4" fill="#111820"/>
        <rect x="24" y="164" width="712" height="4" fill="#2ba8e0"/>
        ${fragments.join("\n")}
        <rect x="0" y="${height - footerHeight}" width="${IMAGE_WIDTH}" height="${footerHeight}" fill="#111820"/>
        ${pixelTextSvg("KLICKEN: BETTERUC.DE/POLIZEI/MITGLIEDER", IMAGE_WIDTH / 2, height - 45, { scale: 2, align: "center", color: "#ffffff" })}
        ${pixelTextSvg("AUTOMATISCH SYNCHRONISIERT", IMAGE_WIDTH / 2, height - 19, { scale: 1, align: "center", color: "#94a9b5" })}
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
