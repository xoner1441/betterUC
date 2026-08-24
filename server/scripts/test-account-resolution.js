"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { selectPreferredAccount } = require("../accountResolution");

test("prefers the active account with the established cloud profile", () => {
  const accounts = [
    { id: "older", status: "active", role: "admin", tokenHash: "one" },
    { id: "established", status: "active", role: "admin", tokenHash: "two", discordId: "linked" }
  ];
  const revisions = new Map([["older", 2], ["established", 352]]);
  assert.equal(selectPreferredAccount(accounts, revisions).id, "established");
});

test("never selects a revoked duplicate over an active account", () => {
  const accounts = [
    { id: "revoked", status: "revoked", role: "admin" },
    { id: "active", status: "active", role: "user" }
  ];
  const revisions = new Map([["revoked", 900], ["active", 1]]);
  assert.equal(selectPreferredAccount(accounts, revisions).id, "active");
});

test("uses linked account data as a tie breaker", () => {
  const accounts = [
    { id: "plain", status: "active", role: "admin" },
    { id: "linked", status: "active", role: "admin", discordId: "discord-user" }
  ];
  assert.equal(selectPreferredAccount(accounts).id, "linked");
});
