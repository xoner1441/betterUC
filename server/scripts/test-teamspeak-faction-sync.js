"use strict";

const assert = require("node:assert/strict");
const net = require("node:net");
const test = require("node:test");
const {
  formatPersonnelImageEmbed,
  formatPersonnelSection,
  normalizeMembers,
  queryEscape,
  queryUnescape,
  replacePersonnelSection,
  synchronizeFactionChannel,
  updateChannelDescription
} = require("../teamSpeakFactionSync");

const SAMPLE_MEMBERS = [
  { username: "FABI1441", rankNumber: 4, rankName: "Commander", isLeader: false },
  { username: "36Flo", rankNumber: 6, rankName: "Chief", isLeader: true },
  { username: "mteii", rankNumber: 4, rankName: "Commander", isLeader: false }
];

test("normalizes, sorts and formats the official police roster", () => {
  const members = normalizeMembers(SAMPLE_MEMBERS);
  assert.deepEqual(members.map(member => member.username), ["36Flo", "FABI1441", "mteii"]);
  const personnel = formatPersonnelSection(members, { slotLimit: 42 });
  assert.match(personnel, /Leader/);
  assert.match(personnel, /Polizeirat/);
  assert.match(personnel, /\[UCPD\] \| 4 \| FABI1441/);
  assert.match(personnel, /Slots: 3\/42/);
});

test("creates a cache-busted, clickable TeamSpeak roster image", () => {
  const members = normalizeMembers([
    { ...SAMPLE_MEMBERS[0], uuid: "75b7ddae-c453-4f24-a7fe-6e0e9dbdc9ae" },
    ...SAMPLE_MEMBERS.slice(1)
  ]);
  const embed = formatPersonnelImageEmbed(members, { publicBaseUrl: "https://betteruc.de/" });
  assert.match(embed, /^\[url=https:\/\/betteruc\.de\/polizei\/mitglieder\]/);
  assert.match(embed, /\[img\]https:\/\/betteruc\.de\/api\/teamspeak\/police-roster\.png\?v=ts3-3-[a-f0-9]{12}\[\/img\]/);
  assert.match(embed, /\[\/url\]$/);
  assert.equal(members.find(member => member.username === "FABI1441").uuid.length, 32);
});

test("escapes TeamSpeak ServerQuery parameters", () => {
  assert.equal(queryEscape("name with/pipe|line\n"), "name\\swith\\/pipe\\pline\\n");
  assert.equal(queryUnescape("name\\swith\\/pipe\\pline\\n"), "name with/pipe|line\n");
});

test("replaces only the personnel section in the existing description", () => {
  const current = [
    "[center]",
    "[img]https://example.test/header.png[/img]",
    "[size=15]• PERSONALAKTE •[/size] [size=12]",
    "Leader",
    "[CHIEF] | 6 | AlterName",
    "Slots: 1/42",
    "[size=18]STRAFZAHLUNGEN[/size]",
    "Ingame -",
    "Fehlverhalten 2.000$",
    "[/size] [/center]"
  ].join("\n");
  const members = normalizeMembers(SAMPLE_MEMBERS);
  const personnel = formatPersonnelSection(members, {
    slotLimit: 42,
    unitOverrides: "FABI1441:SWAT"
  });
  const result = replacePersonnelSection(current, personnel);

  assert.match(result, /\[img\]https:\/\/example\.test\/header\.png\[\/img\]/);
  assert.match(result, /\[SWAT\] \| 4 \| FABI1441/);
  assert.match(result, /Slots: 3\/42/);
  assert.doesNotMatch(result, /AlterName/);
  assert.match(result, /\[size=18\]STRAFZAHLUNGEN\[\/size\]\nIngame -\nFehlverhalten 2\.000\$/);
});

test("logs in and updates only the configured TeamSpeak channel", async () => {
  const commands = [];
  const server = net.createServer(socket => {
    socket.setEncoding("utf8");
    socket.write("TS3\nWelcome to the TeamSpeak 3 ServerQuery interface\n");
    let input = "";
    socket.on("data", chunk => {
      input += chunk;
      let newlineIndex;
      while ((newlineIndex = input.indexOf("\n")) >= 0) {
        const command = input.slice(0, newlineIndex).replace(/\r$/, "");
        input = input.slice(newlineIndex + 1);
        if (!command) continue;
        commands.push(command);
        socket.write("error id=0 msg=ok\n");
      }
    });
  });

  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const address = server.address();

  try {
    await updateChannelDescription({
      host: "127.0.0.1",
      port: address.port,
      timeoutMs: 2000,
      username: "query user",
      password: "p|ass",
      virtualServerId: 0,
      virtualServerPort: 9987,
      channelId: 321
    }, "[b]Polizei[/b]\n• FABI1441");
  } finally {
    await new Promise(resolve => server.close(resolve));
  }

  assert.equal(commands[0], "login client_login_name=query\\suser client_login_password=p\\pass");
  assert.equal(commands[1], "use port=9987");
  assert.equal(
    commands[2],
    "channeledit cid=321 channel_description=[b]Polizei[\\/b]\\n•\\sFABI1441"
  );
  assert.equal(commands.length, 3);
});

test("reads the live channel template and avoids unchanged writes", async () => {
  const commands = [];
  let currentDescription = [
    "[center]",
    "[img]https://example.test/header.png[/img]",
    "[size=15]• PERSONALAKTE •[/size] [size=12]",
    "Leader",
    "[CHIEF] | 6 | AlterName",
    "Slots: 1/42",
    "[size=18]STRAFZAHLUNGEN[/size]",
    "Ingame -",
    "Fehlverhalten 2.000$",
    "[/size] [/center]"
  ].join("\n");
  const server = net.createServer(socket => {
    socket.setEncoding("utf8");
    socket.write("TS3\nWelcome to the TeamSpeak 3 ServerQuery interface\n");
    let input = "";
    socket.on("data", chunk => {
      input += chunk;
      let newlineIndex;
      while ((newlineIndex = input.indexOf("\n")) >= 0) {
        const command = input.slice(0, newlineIndex).replace(/\r$/, "");
        input = input.slice(newlineIndex + 1);
        if (!command) continue;
        commands.push(command);
        if (command.startsWith("channelinfo ")) {
          socket.write(`channel_description=${queryEscape(currentDescription)}\n`);
        } else if (command.startsWith("channeledit ")) {
          const marker = " channel_description=";
          currentDescription = queryUnescape(command.slice(command.indexOf(marker) + marker.length));
        }
        socket.write("error id=0 msg=ok\n");
      }
    });
  });

  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const address = server.address();
  const config = {
    host: "127.0.0.1",
    port: address.port,
    timeoutMs: 2000,
    username: "query",
    password: "secret",
    virtualServerId: 0,
    virtualServerPort: 9987,
    channelId: 109,
    slotLimit: 42,
    unitOverrides: new Map([["fabi1441", "SWAT"]]),
    sectionStartLabel: "PERSONALAKTE",
    sectionEndLabel: "STRAFZAHLUNGEN"
  };

  try {
    const first = await synchronizeFactionChannel(config, normalizeMembers(SAMPLE_MEMBERS));
    const editCountAfterFirstSync = commands.filter(command => command.startsWith("channeledit ")).length;
    const second = await synchronizeFactionChannel(config, normalizeMembers(SAMPLE_MEMBERS));
    const editCountAfterSecondSync = commands.filter(command => command.startsWith("channeledit ")).length;

    assert.equal(first.updated, true);
    assert.equal(second.updated, false);
    assert.equal(editCountAfterFirstSync, 1);
    assert.equal(editCountAfterSecondSync, 1);
    assert.match(currentDescription, /\[SWAT\] \| 4 \| FABI1441/);
    assert.match(currentDescription, /Fehlverhalten 2\.000\$/);
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
});
