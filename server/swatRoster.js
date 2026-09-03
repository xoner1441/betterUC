"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const fsp = fs.promises;
const VALID_ROLES = new Set(["leader", "supervisor", "member"]);
const DEFAULT_SUPERVISOR_OVERRIDES = "mteii";
const DEFAULT_SWAT_MEMBERS = Object.freeze([
  { username: "36Flo", factionRank: 6, role: "leader" },
  { username: "DuckOderSo", factionRank: 5, role: "member" },
  { username: "H4cksLikeLoris", factionRank: 5, role: "member" },
  { username: "mteii", factionRank: 4, role: "supervisor" },
  { username: "73nici", factionRank: 4, role: "member" },
  { username: "FABI1441", factionRank: 3, role: "supervisor" },
  { username: "Aidjn", factionRank: 3, role: "member" },
  { username: "Schbastyyy787", factionRank: 3, role: "member" },
  { username: "reaax72", factionRank: 3, role: "member" },
  { username: "Eymenn", factionRank: 3, role: "member" },
  { username: "1022", factionRank: 3, role: "member" }
]);

function positiveInteger(value, fallback, maximum = 64) {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 && parsed <= maximum ? parsed : fallback;
}

function normalizeMember(entry) {
  if (!entry || !/^[A-Za-z0-9_]{1,16}$/.test(String(entry.username || ""))) return null;
  const role = String(entry.role || "member").trim().toLowerCase();
  return {
    username: String(entry.username).trim(),
    uuid: "",
    factionRank: Math.max(0, Math.min(99, Number.parseInt(entry.factionRank, 10) || 0)),
    role: VALID_ROLES.has(role) ? role : "member"
  };
}

function rolePriority(role) {
  if (role === "leader") return 2;
  if (role === "supervisor") return 1;
  return 0;
}

function parseUsernameSet(value) {
  const entries = value instanceof Set
    ? [...value]
    : Array.isArray(value)
      ? value
      : String(value || "").split(",");
  return new Set(entries
    .map(entry => String(entry || "").trim().toLowerCase())
    .filter(Boolean));
}

function normalizeMembers(entries, options = {}) {
  const supervisorOverrides = parseUsernameSet(options.supervisorOverrides);
  const byName = new Map();
  for (const entry of Array.isArray(entries) ? entries : []) {
    let member = normalizeMember(entry);
    if (!member) continue;
    const key = member.username.toLowerCase();
    if (member.role !== "leader" && supervisorOverrides.has(key)) {
      member = { ...member, role: "supervisor" };
    }
    const previous = byName.get(key);
    if (!previous || rolePriority(member.role) > rolePriority(previous.role)) byName.set(key, member);
  }
  return [...byName.values()].sort((left, right) => (
    rolePriority(right.role) - rolePriority(left.role)
    || right.factionRank - left.factionRank
    || left.username.localeCompare(right.username, "de", { sensitivity: "base" })
  ));
}

function rosterHash(members, slotLimit) {
  const value = `${slotLimit}|${members.map(member => (
    `${member.username}:${member.factionRank}:${member.role}`
  )).join("|")}`;
  return crypto.createHash("sha256").update(value).digest("hex").slice(0, 12);
}

function publicRoster(state) {
  const members = state.members.map(member => ({
    ...member,
    rankNumber: member.factionRank,
    rankName: member.role === "leader" ? "Leader" : member.role === "supervisor" ? "Supervisor" : "Mitglied",
    isLeader: member.role === "leader",
    unit: "SWAT"
  }));
  const allMembers = [...members].sort((left, right) => (
    right.factionRank - left.factionRank
    || rolePriority(right.role) - rolePriority(left.role)
    || left.username.localeCompare(right.username, "de", { sensitivity: "base" })
  ));
  const groups = [
    { key: "leader", label: "Leitung", members: members.filter(member => member.role === "leader") },
    { key: "supervisor", label: "SWAT-Prüfer", members: members.filter(member => member.role === "supervisor") },
    { key: "member", label: "Member", members: allMembers }
  ].filter(group => group.members.length > 0);
  return {
    title: "S.W.A.T.",
    kicker: "SPECIAL WEAPONS AND TACTICS",
    subtitle: "EINHEITSLISTE",
    slotLabel: "MITGLIEDER",
    footerLink: "KLICKEN: BETTERUC.DE/POLIZEI/SWAT",
    count: members.length,
    slotLimit: state.slotLimit,
    hash: state.hash,
    generatedAt: state.updatedAt,
    updatedBy: state.updatedBy,
    members,
    groups
  };
}

function createSwatRosterStore(options = {}) {
  if (!options.file) throw new Error("SWAT roster file is required.");
  if (!options.renderer || typeof options.renderer.renderImage !== "function") {
    throw new Error("SWAT roster renderer is required.");
  }
  const file = options.file;
  const defaultSlotLimit = positiveInteger(options.slotLimit, 13);
  const renderer = options.renderer;
  const supervisorOverrides = parseUsernameSet(
    options.supervisorOverrides === undefined
      ? DEFAULT_SUPERVISOR_OVERRIDES
      : options.supervisorOverrides
  );
  const defaultMembers = normalizeMembers(options.defaultMembers || DEFAULT_SWAT_MEMBERS, { supervisorOverrides });
  let state = {
    version: 1,
    slotLimit: defaultSlotLimit,
    updatedAt: null,
    updatedBy: "",
    members: defaultMembers,
    hash: rosterHash(defaultMembers, defaultSlotLimit)
  };
  let imageCache = null;

  async function load() {
    try {
      const parsed = JSON.parse(await fsp.readFile(file, "utf8"));
      const parsedMembers = normalizeMembers(parsed.members, { supervisorOverrides });
      const members = parsedMembers.length > 0 ? parsedMembers : defaultMembers;
      const slotLimit = positiveInteger(parsed.slotLimit, defaultSlotLimit);
      state = {
        version: 1,
        slotLimit,
        updatedAt: parsed.updatedAt || null,
        updatedBy: String(parsed.updatedBy || ""),
        members,
        hash: rosterHash(members, slotLimit)
      };
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
    return publicRoster(state);
  }

  async function save() {
    await fsp.mkdir(path.dirname(file), { recursive: true });
    const temporary = `${file}.tmp`;
    await fsp.writeFile(temporary, JSON.stringify(state, null, 2), "utf8");
    await fsp.rename(temporary, file);
  }

  async function update(payload, updatedBy) {
    const members = normalizeMembers(payload?.members, { supervisorOverrides });
    if (members.length === 0) throw new Error("Die SWAT-Liste enthält keine gültigen Mitglieder.");
    const slotLimit = positiveInteger(payload?.slotLimit, defaultSlotLimit);
    state = {
      version: 1,
      slotLimit,
      updatedAt: new Date().toISOString(),
      updatedBy: String(updatedBy || ""),
      members,
      hash: rosterHash(members, slotLimit)
    };
    imageCache = null;
    await save();
    return publicRoster(state);
  }

  function getRoster() {
    return publicRoster(state);
  }

  async function getImage() {
    const roster = getRoster();
    if (imageCache && imageCache.hash === roster.hash) return imageCache;
    const buffer = await renderer.renderImage(roster);
    imageCache = { buffer, hash: roster.hash, generatedAt: roster.generatedAt };
    return imageCache;
  }

  return {
    getHead: renderer.getHead,
    getImage,
    getRoster,
    load,
    update
  };
}

module.exports = {
  DEFAULT_SUPERVISOR_OVERRIDES,
  DEFAULT_SWAT_MEMBERS,
  createSwatRosterStore,
  normalizeMembers,
  publicRoster,
  rosterHash
};
