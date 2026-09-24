"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { welcomeMessageTest } = require("../discordBot");

const { discordChannelMention, welcomeDate, welcomeDescription } = welcomeMessageTest;

test("welcome message contains the new member, number and configured channels", () => {
  const text = welcomeDescription("123", 18, { id: "456" }, { id: "789" });
  assert.match(text, /<@123>/);
  assert.match(text, /\[#18]/);
  assert.match(text, /<#456>/);
  assert.match(text, /<#789>/);
});

test("welcome channel mention has a readable fallback", () => {
  assert.equal(discordChannelMention(null, "download"), "#download");
});

test("welcome footer date uses German day-month-year formatting", () => {
  assert.equal(welcomeDate(new Date("2026-09-23T12:00:00Z")), "23.09.2026");
});
