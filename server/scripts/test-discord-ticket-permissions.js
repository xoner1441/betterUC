"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { ticketPermissionTest } = require("../discordBot");

const { ticketTeamRoleNames, hasTicketTeamRole, isBetterUcTicketTeamAccount } = ticketPermissionTest;

test("configured helper and admin roles always join the ticket team", () => {
  assert.deepEqual(
    ticketTeamRoleNames(["Owner"], "Helper", "Admin"),
    ["Owner", "Helper", "Admin"]
  );
});

test("role names are deduplicated case-insensitively", () => {
  assert.deepEqual(
    ticketTeamRoleNames(["Owner", "helper", "ADMIN"], "Helper", "Admin"),
    ["Owner", "helper", "ADMIN"]
  );
});

test("helper and admin members pass while unrelated roles do not", () => {
  const allowed = ticketTeamRoleNames(["Owner"], "Support Helper", "Admin");
  assert.equal(hasTicketTeamRole([{ name: "Support Helper" }], allowed), true);
  assert.equal(hasTicketTeamRole([{ name: "Admin" }], allowed), true);
  assert.equal(hasTicketTeamRole([{ name: "VIP" }], allowed), false);
});

test("linked betterUC helper and admin accounts are ticket team members", () => {
  assert.equal(isBetterUcTicketTeamAccount({ role: "helper", status: "active" }), true);
  assert.equal(isBetterUcTicketTeamAccount({ role: "admin", status: "active" }), true);
  assert.equal(isBetterUcTicketTeamAccount({ role: "helper", status: "revoked" }), false);
  assert.equal(isBetterUcTicketTeamAccount({ role: "vip", status: "active" }), false);
});
