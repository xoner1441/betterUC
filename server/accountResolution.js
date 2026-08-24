"use strict";

const ROLE_PRIORITY = Object.freeze({
  user: 0,
  vip: 1,
  partner: 2,
  helper: 3,
  admin: 4
});

function cloudRevision(revisions, accountId) {
  const value = revisions instanceof Map
    ? revisions.get(accountId)
    : revisions && revisions[accountId];
  const revision = Number(value || 0);
  return Number.isFinite(revision) ? revision : 0;
}

function timestamp(value) {
  const parsed = Date.parse(value || "");
  return Number.isFinite(parsed) ? parsed : 0;
}

function preference(account, revisions) {
  return [
    String(account && account.status || "active").toLowerCase() === "active" ? 1 : 0,
    cloudRevision(revisions, account && account.id),
    account && account.discordId ? 1 : 0,
    account && account.webPasswordHash ? 1 : 0,
    account && account.stats ? 1 : 0,
    Array.isArray(account && account.statsHistory) ? account.statsHistory.length : 0,
    account && account.tokenHash ? 1 : 0,
    ROLE_PRIORITY[String(account && account.role || "user").toLowerCase()] || 0,
    timestamp(account && (account.updatedAt || account.lastSeenAt || account.createdAt))
  ];
}

function comparePreferred(left, right, revisions) {
  const leftPreference = preference(left, revisions);
  const rightPreference = preference(right, revisions);
  for (let index = 0; index < leftPreference.length; index += 1) {
    if (leftPreference[index] !== rightPreference[index]) {
      return rightPreference[index] - leftPreference[index];
    }
  }
  return String(left && left.id || "").localeCompare(String(right && right.id || ""));
}

function selectPreferredAccount(accounts, revisions = new Map()) {
  const candidates = Array.isArray(accounts) ? accounts.filter(Boolean) : [];
  if (candidates.length === 0) return null;
  return [...candidates].sort((left, right) => comparePreferred(left, right, revisions))[0];
}

module.exports = { selectPreferredAccount };
