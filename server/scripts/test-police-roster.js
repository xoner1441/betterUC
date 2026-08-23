"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const sharp = require("sharp");
const { createPoliceRosterService, groupMembers } = require("../policeRoster");

const MEMBERS = [
  {
    username: "36Flo",
    uuid: "b9a6fd1a-4223-4b8d-95bc-06eb7d9722f4",
    rankNumber: 6,
    rankName: "Chief",
    isLeader: true
  },
  {
    username: "FABI1441",
    uuid: "75b7ddae-c453-4f24-a7fe-6e0e9dbdc9ae",
    rankNumber: 4,
    rankName: "Commander",
    isLeader: false
  },
  {
    username: "pixel361",
    uuid: "42d436e8-9b7f-4f4b-9dd4-7cefc96f33df",
    rankNumber: 1,
    rankName: "Sergeant",
    isLeader: false
  }
];

async function fakeHead() {
  return sharp({
    create: { width: 16, height: 16, channels: 4, background: "#35aadd" }
  }).png().toBuffer();
}

test("groups the official roster into leadership, council and ranks", () => {
  const groups = groupMembers(MEMBERS);
  assert.deepEqual(groups.map(group => group.label), ["LEITUNG", "POLIZEIRAT", "SERGEANT"]);
  assert.deepEqual(groups.map(group => group.members.length), [1, 1, 1]);
});

test("loads members, caches heads and renders the TeamSpeak PNG", async () => {
  const head = await fakeHead();
  let apiRequests = 0;
  let headRequests = 0;
  const fetchImpl = async url => {
    if (String(url).includes("/members")) {
      apiRequests += 1;
      return new Response(JSON.stringify(MEMBERS), {
        status: 200,
        headers: { "content-type": "application/json" }
      });
    }
    headRequests += 1;
    return new Response(head, { status: 200, headers: { "content-type": "image/png" } });
  };
  const service = createPoliceRosterService({
    fetchImpl,
    apiUrl: "https://example.test/members",
    headBaseUrl: "https://heads.example.test/head",
    unitOverrides: "FABI1441:SWAT",
    slotLimit: 42
  });

  const roster = await service.getRoster();
  const image = await service.getImage();
  const secondImage = await service.getImage();
  const metadata = await sharp(image.buffer).metadata();

  assert.equal(roster.count, 3);
  assert.equal(roster.members.find(member => member.username === "FABI1441").unit, "SWAT");
  assert.match(roster.hash, /^[a-f0-9]{12}$/);
  assert.equal(metadata.format, "png");
  assert.equal(metadata.width, 620);
  assert.ok(metadata.height > 500);
  assert.ok(metadata.height < 2048, `TeamSpeak image is too tall: ${metadata.height}px`);
  assert.equal(image.buffer, secondImage.buffer);
  assert.equal(apiRequests, 1);
  assert.equal(headRequests, MEMBERS.length);
});

test("keeps a full 42-member roster below TeamSpeak's image height limit", async () => {
  const ranks = [5, 4, 3, 2, 1, ...Array(37).fill(0)];
  const members = ranks.map((rankNumber, index) => ({
    username: `Member${index + 1}`,
    uuid: "",
    rankNumber,
    rankName: rankNumber === 5 ? "Chief" : rankNumber === 4 ? "Commander" : `Rank ${rankNumber}`,
    isLeader: rankNumber === 5
  }));
  const service = createPoliceRosterService({ slotLimit: 42 });
  const buffer = await service.renderImage({
    members,
    groups: groupMembers(members),
    count: members.length,
    slotLimit: 42
  });
  const metadata = await sharp(buffer).metadata();

  assert.equal(metadata.width, 620);
  assert.ok(metadata.height < 2048, `Full TeamSpeak image is too tall: ${metadata.height}px`);
});
