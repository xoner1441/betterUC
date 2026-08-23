"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs/promises");
const os = require("node:os");
const path = require("node:path");
const test = require("node:test");
const { createSwatRosterStore, normalizeMembers } = require("../swatRoster");

test("normalizes SWAT roles independently from faction ranks", () => {
  const members = normalizeMembers([
    { username: "FABI1441", factionRank: 4, role: "supervisor" },
    { username: "36Flo", factionRank: 6, role: "leader" },
    { username: "mteii", factionRank: 4, role: "member" }
  ]);
  assert.deepEqual(members.map(member => member.username), ["36Flo", "FABI1441", "mteii"]);
  assert.deepEqual(members.map(member => member.role), ["leader", "supervisor", "member"]);
});

test("persists and groups an uploaded SWAT roster", async () => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), "betteruc-swat-"));
  const file = path.join(directory, "swat-roster.json");
  let renderedTitle = "";
  const renderer = {
    getHead: async () => Buffer.from("head"),
    renderImage: async roster => {
      renderedTitle = roster.title;
      return Buffer.from("png");
    }
  };
  try {
    const store = createSwatRosterStore({ file, renderer, slotLimit: 13 });
    await store.load();
    const roster = await store.update({
      slotLimit: 13,
      members: [
        { username: "36Flo", factionRank: 6, role: "leader" },
        { username: "FABI1441", factionRank: 4, role: "supervisor" },
        { username: "mteii", factionRank: 4, role: "member" }
      ]
    }, "FABI1441");

    assert.equal(roster.count, 3);
    assert.equal(roster.slotLimit, 13);
    assert.deepEqual(roster.groups.map(group => group.label), ["Leitung", "SWAT-Prüfer", "Member"]);
    assert.deepEqual(roster.groups.map(group => group.members.length), [1, 1, 3]);
    assert.equal(roster.updatedBy, "FABI1441");
    assert.match(roster.hash, /^[a-f0-9]{12}$/);
    assert.equal((await store.getImage()).buffer.toString(), "png");
    assert.equal(renderedTitle, "S.W.A.T.");

    const reloaded = createSwatRosterStore({ file, renderer, slotLimit: 13 });
    await reloaded.load();
    assert.deepEqual(reloaded.getRoster().members.map(member => member.username), ["36Flo", "FABI1441", "mteii"]);
  } finally {
    await fs.rm(directory, { recursive: true, force: true });
  }
});

test("uses the current SWAT channel roster until the first automatic upload", async () => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), "betteruc-swat-default-"));
  const file = path.join(directory, "swat-roster.json");
  const renderer = {
    getHead: async () => Buffer.from("head"),
    renderImage: async () => Buffer.from("png")
  };
  try {
    const store = createSwatRosterStore({ file, renderer, slotLimit: 13 });
    await store.load();
    const roster = store.getRoster();
    assert.equal(roster.title, "S.W.A.T.");
    assert.equal(roster.subtitle, "EINHEITSLISTE");
    assert.equal(roster.count, 11);
    assert.equal(roster.slotLimit, 13);
    assert.deepEqual(roster.groups.map(group => group.members.length), [1, 2, 11]);
    assert.deepEqual(
      roster.groups.find(group => group.key === "member").members.slice(0, 6).map(member => member.username),
      ["36Flo", "DuckOderSo", "H4cksLikeLoris", "mteii", "73nici", "FABI1441"]
    );
  } finally {
    await fs.rm(directory, { recursive: true, force: true });
  }
});
