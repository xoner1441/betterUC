"use strict";

const fs = require("fs");
const fsp = fs.promises;
const path = require("path");

let ActionRowBuilder;
let ActivityType;
let ButtonBuilder;
let ButtonStyle;
let ChannelType;
let Client;
let EmbedBuilder;
let GatewayIntentBits;
let PermissionFlagsBits;
let SlashCommandBuilder;

const BOT_TOKEN = clean(process.env.DISCORD_BOT_TOKEN);
const GUILD_ID = clean(process.env.DISCORD_GUILD_ID);
const TICKET_CATEGORY_NAME = clean(process.env.DISCORD_TICKET_CATEGORY_NAME) || "Tickets";
const TEAM_ROLE_NAMES = listEnv(process.env.DISCORD_TEAM_ROLE_NAMES || "Owner,Admin,Helper");
const MOD_USER_ROLE_NAME = clean(process.env.DISCORD_MOD_USER_ROLE_NAME) || "Mod-User";
const USER_ROLE_NAME = clean(process.env.DISCORD_USER_ROLE_NAME);
const VIP_ROLE_NAME = clean(process.env.DISCORD_VIP_ROLE_NAME) || "VIP";
const PARTNER_ROLE_NAME = clean(process.env.DISCORD_PARTNER_ROLE_NAME) || "Partner";
const HELPER_ROLE_NAME = clean(process.env.DISCORD_HELPER_ROLE_NAME) || "Helper";
const ADMIN_ROLE_NAME = clean(process.env.DISCORD_ADMIN_ROLE_NAME) || "Admin";
const UPDATE_CHANNEL_NAME = clean(process.env.DISCORD_UPDATE_CHANNEL_NAME) || "updates";
const RELEASE_REPO = clean(process.env.DISCORD_RELEASE_REPO) || "xoner1441/betterUC";
const RELEASE_CHECK_MS = Math.max(5 * 60 * 1000, Number(process.env.DISCORD_RELEASE_CHECK_MS || 15 * 60 * 1000));
const ANNOUNCE_EXISTING_RELEASE = String(process.env.DISCORD_ANNOUNCE_EXISTING_RELEASE || "false").toLowerCase() === "true";
const PUBLIC_DOWNLOAD_URL = clean(process.env.PUBLIC_DOWNLOAD_URL) || "https://betteruc.de/download";
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, "data");
const BOT_STATE_FILE = process.env.DISCORD_BOT_STATE_FILE || path.join(DATA_DIR, "discord-bot-state.json");

let botState = {};

function loadDiscord() {
  if (Client) return;
  ({
    ActionRowBuilder,
    ActivityType,
    ButtonBuilder,
    ButtonStyle,
    ChannelType,
    Client,
    EmbedBuilder,
    GatewayIntentBits,
    PermissionFlagsBits,
    SlashCommandBuilder
  } = require("discord.js"));
}

function clean(value) {
  return String(value || "").trim();
}

function listEnv(value) {
  return String(value || "")
    .split(",")
    .map(entry => entry.trim())
    .filter(Boolean);
}

async function readBotState() {
  try {
    const raw = await fsp.readFile(BOT_STATE_FILE, "utf8");
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch (error) {
    if (error.code === "ENOENT") return {};
    console.warn("Could not read Discord bot state", error.message);
    return {};
  }
}

async function writeBotState(state) {
  try {
    await fsp.mkdir(path.dirname(BOT_STATE_FILE), { recursive: true });
    const tmp = `${BOT_STATE_FILE}.tmp`;
    await fsp.writeFile(tmp, JSON.stringify(state, null, 2), "utf8");
    await fsp.rename(tmp, BOT_STATE_FILE);
  } catch (error) {
    console.warn("Could not write Discord bot state", error.message);
  }
}

function roleLabel(role) {
  if (role === "admin") return "Admin";
  if (role === "helper") return "Helper";
  if (role === "partner") return "Partner";
  if (role === "vip") return "VIP";
  return "User";
}

function roleColor(role) {
  if (role === "admin") return 0xff4d5a;
  if (role === "helper") return 0xfacc15;
  if (role === "partner") return 0x22d3ee;
  if (role === "vip") return 0x6d28d9;
  return 0x22c55e;
}

function display(value, fallback = "-") {
  const raw = String(value || "").trim();
  return raw || fallback;
}

function formatMoney(value) {
  if (typeof value !== "number") return "-";
  return `${value.toLocaleString("de-DE")}$`;
}

function formatStats(account) {
  const stats = account && account.stats ? account.stats : {};
  return [
    `Bank: ${formatMoney(stats.bankMoney)}`,
    `Bargeld: ${formatMoney(stats.cashMoney)}`,
    `Fraktion: ${display(stats.factionDisplay || account?.faction)}`,
    `Haeuser: ${display(stats.houses)}`,
    `Treuebonus: ${stats.loyaltyBonus ?? "-"}`,
    `Spielzeit: ${stats.playTimeHours ?? "-"}h`,
    `Votepoints: ${stats.votepoints ?? "-"}`,
    `Warns: ${display(stats.warns)}`
  ].join("\n");
}

function buildCommands() {
  return [
    new SlashCommandBuilder()
      .setName("online")
      .setDescription("Zeigt alle gerade verbundenen betterUC Mod-User."),
    new SlashCommandBuilder()
      .setName("relay")
      .setDescription("Zeigt den aktuellen Status vom betterUC Relay."),
    new SlashCommandBuilder()
      .setName("user")
      .setDescription("Zeigt bekannte betterUC Daten zu einem Minecraft-Spieler.")
      .addStringOption(option => option
        .setName("name")
        .setDescription("Minecraft-Name")
        .setRequired(true)),
    new SlashCommandBuilder()
      .setName("me")
      .setDescription("Zeigt deinen verknuepften betterUC Account."),
    new SlashCommandBuilder()
      .setName("link")
      .setDescription("Verknuepft deinen Discord-Account mit deinem betterUC Access-Code.")
      .addStringOption(option => option
        .setName("code")
        .setDescription("Dein betterUC Access-Code")
        .setRequired(true)),
    new SlashCommandBuilder()
      .setName("unlink")
      .setDescription("Loest die Verknuepfung zwischen Discord und betterUC."),
    new SlashCommandBuilder()
      .setName("ticket")
      .setDescription("Oeffnet ein privates Support-Ticket.")
      .addStringOption(option => option
        .setName("thema")
        .setDescription("Worum geht es?")
        .setRequired(true)
        .addChoices(
          { name: "Support", value: "support" },
          { name: "Bug melden", value: "bug" },
          { name: "Access-Code Problem", value: "access" },
          { name: "Account Problem", value: "account" }
        )),
    new SlashCommandBuilder()
      .setName("ticket-panel")
      .setDescription("Postet ein Ticket-Panel mit Buttons in diesen Channel.")
      .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild),
    new SlashCommandBuilder()
      .setName("updates")
      .setDescription("Prueft oder postet betterUC GitHub-Updates.")
      .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild)
      .addSubcommand(subcommand => subcommand
        .setName("check")
        .setDescription("Prueft, ob ein neues betterUC-Release existiert."))
      .addSubcommand(subcommand => subcommand
        .setName("post_latest")
        .setDescription("Postet das aktuelle betterUC-Release erneut in den Update-Channel.")),
    new SlashCommandBuilder()
      .setName("code")
      .setDescription("Access-Codes ueber Discord verwalten.")
      .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild)
      .addSubcommand(subcommand => subcommand
        .setName("create")
        .setDescription("Erstellt einen neuen Access-Code.")
        .addStringOption(option => option
          .setName("name")
          .setDescription("Minecraft-Name")
          .setRequired(true))
        .addStringOption(option => option
          .setName("rolle")
          .setDescription("betterUC Rolle")
          .setRequired(false)
          .addChoices(
            { name: "User", value: "user" },
            { name: "VIP", value: "vip" },
            { name: "Partner", value: "partner" },
            { name: "Helper", value: "helper" },
            { name: "Admin", value: "admin" }
          ))
        .addStringOption(option => option
          .setName("fraktion")
          .setDescription("Optional: Fraktion")
          .setRequired(false)))
      .addSubcommand(subcommand => subcommand
        .setName("reset")
        .setDescription("Generiert fuer einen Account einen neuen Access-Code.")
        .addStringOption(option => option
          .setName("name")
          .setDescription("Minecraft-Name")
          .setRequired(true)))
      .addSubcommand(subcommand => subcommand
        .setName("revoke")
        .setDescription("Sperrt einen Account.")
        .addStringOption(option => option
          .setName("name")
          .setDescription("Minecraft-Name")
          .setRequired(true)))
  ].map(command => command.toJSON());
}

function onlineEmbed(players) {
  const sorted = [...players].sort((a, b) => {
    const priority = (b.priority || 0) - (a.priority || 0);
    if (priority !== 0) return priority;
    return String(a.name || "").localeCompare(String(b.name || ""));
  });
  const lines = sorted.slice(0, 40).map(player => {
    const bits = [
      `**${display(player.name, "unknown")}**`,
      roleLabel(player.role),
      display(player.faction, ""),
      player.version ? `v${player.version}` : ""
    ].filter(Boolean);
    return bits.join(" | ");
  });

  return new EmbedBuilder()
    .setTitle("betterUC Mod-User online")
    .setColor(0x38bdf8)
    .setDescription(lines.length ? lines.join("\n") : "Aktuell ist kein Mod-User verbunden.")
    .setFooter({ text: `${players.length} online` });
}

function relayEmbed(players, accounts) {
  const activeAccounts = accounts.filter(account => account.status !== "revoked");
  const admins = activeAccounts.filter(account => account.role === "admin").length;
  const helpers = activeAccounts.filter(account => account.role === "helper").length;
  const partners = activeAccounts.filter(account => account.role === "partner").length;
  const vips = activeAccounts.filter(account => account.role === "vip").length;

  return new EmbedBuilder()
    .setTitle("betterUC Relay")
    .setColor(players.length ? 0x22c55e : 0xfacc15)
    .addFields(
      { name: "Online", value: String(players.length), inline: true },
      { name: "Accounts", value: String(activeAccounts.length), inline: true },
      { name: "Rollen", value: `Admin ${admins} | Helper ${helpers} | Partner ${partners} | VIP ${vips}`, inline: true }
    )
    .setTimestamp(new Date());
}

function userEmbed(account, onlinePlayer) {
  const role = account?.role || onlinePlayer?.role || "user";
  const name = account?.minecraftName || onlinePlayer?.name || "unknown";
  const embed = new EmbedBuilder()
    .setTitle(name)
    .setColor(roleColor(role))
    .addFields(
      { name: "Rolle", value: roleLabel(role), inline: true },
      { name: "Status", value: onlinePlayer ? "Online" : display(account?.status, "offline"), inline: true },
      { name: "Fraktion", value: display(account?.stats?.factionDisplay || account?.faction || onlinePlayer?.faction), inline: true }
    );

  if (account) {
    embed.addFields({ name: "Tracking", value: formatStats(account), inline: false });
    if (account.lastSeenAt) embed.setFooter({ text: `Zuletzt gesehen: ${account.lastSeenAt}` });
  }

  return embed;
}

function trimText(value, maxLength = 900) {
  const raw = String(value || "").trim();
  if (raw.length <= maxLength) return raw;
  return `${raw.slice(0, maxLength - 3).trim()}...`;
}

function releaseEmbed(release) {
  const tag = display(release.tag_name || release.name, "neues Release");
  const url = PUBLIC_DOWNLOAD_URL;
  const body = trimText(release.body || "Keine Release Notes hinterlegt.");
  return new EmbedBuilder()
    .setTitle(`betterUC ${tag} ist verfügbar`)
    .setURL(url)
    .setColor(0x38bdf8)
    .setDescription(body)
    .addFields(
      { name: "Download", value: url, inline: false }
    )
    .setTimestamp(release.published_at ? new Date(release.published_at) : new Date());
}

async function fetchLatestRelease() {
  const response = await fetch(`https://api.github.com/repos/${RELEASE_REPO}/releases/latest`, {
    headers: {
      "User-Agent": "betterUC-discord-bot",
      "Accept": "application/vnd.github+json"
    }
  });
  if (!response.ok) {
    throw new Error(`GitHub Release Check fehlgeschlagen: HTTP ${response.status}`);
  }
  return response.json();
}

async function findTextChannelByName(guild, name) {
  await guild.channels.fetch().catch(() => null);
  const lower = String(name || "").toLowerCase();
  return guild.channels.cache.find(channel =>
    channel.type === ChannelType.GuildText
    && channel.name.toLowerCase() === lower
  ) || null;
}

async function checkGithubRelease(client, options = {}) {
  if (!GUILD_ID || !UPDATE_CHANNEL_NAME) {
    return { status: "disabled" };
  }

  const release = await fetchLatestRelease();
  const releaseKey = String(release.id || release.tag_name || release.html_url || "").trim();
  if (!releaseKey) return { status: "missing_release_key" };

  const previousKey = botState.latestReleaseKey || "";
  const firstRun = !previousKey;
  const changed = previousKey !== releaseKey;
  const pendingPost = Boolean(botState.latestReleasePendingPost && previousKey === releaseKey);
  const shouldPost = options.forcePost || pendingPost || (changed && (!firstRun || ANNOUNCE_EXISTING_RELEASE || options.announceExisting));

  botState.latestReleaseKey = releaseKey;
  botState.latestReleaseTag = release.tag_name || "";
  botState.latestReleaseCheckedAt = new Date().toISOString();

  if (!shouldPost) {
    await writeBotState(botState);
    return { status: firstRun ? "initialized" : "unchanged", release };
  }

  const guild = await client.guilds.fetch(GUILD_ID);
  const channel = await findTextChannelByName(guild, UPDATE_CHANNEL_NAME);
  if (!channel) {
    botState.latestReleasePendingPost = true;
    await writeBotState(botState);
    throw new Error(`Discord-Update-Channel '${UPDATE_CHANNEL_NAME}' nicht gefunden.`);
  }

  await channel.send({ embeds: [releaseEmbed(release)] });
  botState.latestReleasePendingPost = false;
  botState.latestReleasePostedAt = new Date().toISOString();
  await writeBotState(botState);
  return { status: "posted", release };
}

function ticketLabel(topic) {
  if (topic === "bug") return "Bug melden";
  if (topic === "access") return "Access-Code Problem";
  if (topic === "account") return "Account Problem";
  return "Support";
}

function ticketPrefix(topic) {
  if (topic === "bug") return "bug";
  if (topic === "access") return "access";
  if (topic === "account") return "account";
  return "support";
}

function slug(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[^a-z0-9_-]/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 32) || "user";
}

async function findOrCreateTicketCategory(guild, teamRoles) {
  const existing = guild.channels.cache.find(channel =>
    channel.type === ChannelType.GuildCategory
    && channel.name.toLowerCase() === TICKET_CATEGORY_NAME.toLowerCase()
  );
  if (existing) return existing;

  return guild.channels.create({
    name: TICKET_CATEGORY_NAME,
    type: ChannelType.GuildCategory,
    permissionOverwrites: ticketCategoryOverwrites(guild, teamRoles)
  });
}

function resolveTeamRoles(guild) {
  const lowerNames = TEAM_ROLE_NAMES.map(name => name.toLowerCase());
  return guild.roles.cache.filter(role => lowerNames.includes(role.name.toLowerCase()));
}

function betterUcRoleName(role) {
  if (role === "admin") return ADMIN_ROLE_NAME;
  if (role === "helper") return HELPER_ROLE_NAME;
  if (role === "partner") return PARTNER_ROLE_NAME;
  if (role === "vip") return VIP_ROLE_NAME;
  return USER_ROLE_NAME;
}

function managedBetterUcRoleNames() {
  return [MOD_USER_ROLE_NAME, USER_ROLE_NAME, VIP_ROLE_NAME, PARTNER_ROLE_NAME, HELPER_ROLE_NAME, ADMIN_ROLE_NAME]
    .map(clean)
    .filter(Boolean);
}

function desiredBetterUcRoleNames(account) {
  if (!account || account.status === "revoked") return [];
  return [MOD_USER_ROLE_NAME, betterUcRoleName(account.role)]
    .map(clean)
    .filter(Boolean);
}

function roleByName(guild, roleName) {
  const lower = clean(roleName).toLowerCase();
  if (!lower) return null;
  return guild.roles.cache.find(entry => entry.name.toLowerCase() === lower) || null;
}

async function syncBetterUcRoles(member, account) {
  if (!member || !member.guild) return;
  await member.guild.roles.fetch().catch(() => null);
  const desired = new Set(desiredBetterUcRoleNames(account).map(name => name.toLowerCase()));

  for (const roleName of managedBetterUcRoleNames()) {
    const role = roleByName(member.guild, roleName);
    if (!role) continue;
    const shouldHave = desired.has(roleName.toLowerCase());
    const hasRole = member.roles.cache.has(role.id);
    if (shouldHave && !hasRole) {
      await member.roles.add(role).catch(error => console.warn("Could not add betterUC Discord role", roleName, account?.minecraftName || "", error.message));
    } else if (!shouldHave && hasRole) {
      await member.roles.remove(role).catch(error => console.warn("Could not remove betterUC Discord role", roleName, account?.minecraftName || "", error.message));
    }
  }
}

async function removeBetterUcRoles(member) {
  if (!member || !member.guild) return;
  await member.guild.roles.fetch().catch(() => null);
  for (const roleName of managedBetterUcRoleNames()) {
    const role = roleByName(member.guild, roleName);
    if (!role || !member.roles.cache.has(role.id)) continue;
    await member.roles.remove(role).catch(error => console.warn("Could not remove betterUC Discord role", roleName, error.message));
  }
}

function ticketCategoryOverwrites(guild, teamRoles) {
  const overwrites = [
    {
      id: guild.roles.everyone.id,
      deny: [PermissionFlagsBits.ViewChannel]
    }
  ];
  for (const role of teamRoles.values()) {
    overwrites.push({
      id: role.id,
      allow: [
        PermissionFlagsBits.ViewChannel,
        PermissionFlagsBits.SendMessages,
        PermissionFlagsBits.ReadMessageHistory,
        PermissionFlagsBits.ManageMessages
      ]
    });
  }
  return overwrites;
}

function ticketChannelOverwrites(guild, openerId, teamRoles) {
  return [
    ...ticketCategoryOverwrites(guild, teamRoles),
    {
      id: openerId,
      allow: [
        PermissionFlagsBits.ViewChannel,
        PermissionFlagsBits.SendMessages,
        PermissionFlagsBits.ReadMessageHistory,
        PermissionFlagsBits.AttachFiles
      ]
    }
  ];
}

async function deferEphemeral(interaction) {
  if (!interaction.deferred && !interaction.replied) {
    await interaction.deferReply({ ephemeral: true });
  }
}

async function respondEphemeral(interaction, content) {
  if (interaction.deferred) {
    await interaction.editReply({ content });
  } else if (interaction.replied) {
    await interaction.followUp({ content, ephemeral: true });
  } else {
    await interaction.reply({ content, ephemeral: true });
  }
}

function ticketErrorMessage(error) {
  const code = error?.code ? ` (${error.code})` : "";
  const message = String(error?.message || "").trim();
  if (message.includes("Missing Permissions") || error?.code === 50013) {
    return "Ticket konnte nicht erstellt werden: Dem Bot fehlen Discord-Rechte. Gib dem Bot bitte 'Kanäle verwalten', 'Nachrichten senden' und Zugriff auf die Ticket-Kategorie.";
  }
  if (message.includes("Maximum number of channels") || error?.code === 30013) {
    return "Ticket konnte nicht erstellt werden: Der Discord-Server hat das Channel-Limit erreicht.";
  }
  if (message) {
    return `Ticket konnte nicht erstellt werden: ${message}${code}`;
  }
  return "Ticket konnte nicht erstellt werden. Bitte pruefe die Bot-Rechte und versuche es erneut.";
}

async function ensureTicketBotPermissions(guild) {
  const botMember = guild.members.me || await guild.members.fetch(guild.client.user.id).catch(() => null);
  if (!botMember) {
    throw new Error("Bot-Mitglied konnte auf diesem Discord-Server nicht geladen werden.");
  }
  const missing = [];
  if (!botMember.permissions.has(PermissionFlagsBits.ManageChannels)) missing.push("Kanäle verwalten");
  if (!botMember.permissions.has(PermissionFlagsBits.SendMessages)) missing.push("Nachrichten senden");
  if (!botMember.permissions.has(PermissionFlagsBits.ViewChannel)) missing.push("Kanäle ansehen");
  if (missing.length > 0) {
    throw new Error(`Dem Bot fehlen Rechte: ${missing.join(", ")}.`);
  }
}

async function openTicket(interaction, topic) {
  await deferEphemeral(interaction);

  const guild = interaction.guild;
  if (!guild) {
    await respondEphemeral(interaction, "Tickets koennen nur auf dem Server erstellt werden.");
    return;
  }

  try {
    await ensureTicketBotPermissions(guild);
    await guild.roles.fetch().catch(() => null);
    await guild.channels.fetch().catch(() => null);
    const teamRoles = resolveTeamRoles(guild);
    const category = await findOrCreateTicketCategory(guild, teamRoles);
    const openTicket = guild.channels.cache.find(channel =>
      channel.type === ChannelType.GuildText
      && channel.name.startsWith(`ticket-${ticketPrefix(topic)}-`)
      && channel.topic
      && channel.topic.includes(`discord:${interaction.user.id}`)
    );
    if (openTicket) {
      await respondEphemeral(interaction, `Du hast bereits ein offenes Ticket: ${openTicket}`);
      return;
    }

    const channel = await guild.channels.create({
      name: `ticket-${ticketPrefix(topic)}-${slug(interaction.user.username)}`,
      type: ChannelType.GuildText,
      parent: category.id,
      topic: `betterUC Ticket | ${ticketLabel(topic)} | discord:${interaction.user.id}`,
      permissionOverwrites: ticketChannelOverwrites(guild, interaction.user.id, teamRoles),
      reason: `betterUC ticket opened by ${interaction.user.tag}`
    });

    const embed = new EmbedBuilder()
      .setTitle(`Ticket: ${ticketLabel(topic)}`)
      .setColor(0x38bdf8)
      .setDescription([
        `${interaction.user}, beschreibe dein Anliegen bitte moeglichst genau.`,
        "Ein Teammitglied meldet sich dann hier im Ticket."
      ].join("\n"));
    const closeRow = new ActionRowBuilder().addComponents(
      new ButtonBuilder()
        .setCustomId("ticket:close")
        .setLabel("Ticket schliessen")
        .setStyle(ButtonStyle.Danger)
    );
    await channel.send({ content: `${interaction.user}`, embeds: [embed], components: [closeRow] });
    await respondEphemeral(interaction, `Ticket erstellt: ${channel}`);
  } catch (error) {
    console.error("Discord ticket create error", error);
    await respondEphemeral(interaction, ticketErrorMessage(error));
  }
}

async function closeTicket(interaction) {
  const channel = interaction.channel;
  if (!channel || channel.type !== ChannelType.GuildText || !channel.name.startsWith("ticket-")) {
    await interaction.reply({ content: "Dieser Button funktioniert nur in einem offenen Ticket.", ephemeral: true });
    return;
  }

  const closedName = `closed-${channel.name.replace(/^ticket-/, "")}`.slice(0, 100);
  await channel.setName(closedName).catch(() => null);
  await channel.permissionOverwrites.edit(interaction.user.id, { SendMessages: false }).catch(() => null);
  const deleteRow = new ActionRowBuilder().addComponents(
    new ButtonBuilder()
      .setCustomId("ticket:delete")
      .setLabel("Ticket loeschen")
      .setStyle(ButtonStyle.Danger)
  );
  await interaction.reply({
    content: "Ticket geschlossen. Ein Teammitglied kann den Channel nun loeschen.",
    components: [deleteRow],
    ephemeral: false
  });
}

async function deleteTicket(interaction) {
  const channel = interaction.channel;
  if (!channel || channel.type !== ChannelType.GuildText || !channel.name.startsWith("closed-")) {
    await interaction.reply({ content: "Dieses Ticket muss erst geschlossen werden.", ephemeral: true });
    return;
  }
  if (!interaction.memberPermissions?.has(PermissionFlagsBits.ManageChannels)) {
    await interaction.reply({ content: "Nur Teammitglieder mit Channel-Rechten koennen Tickets loeschen.", ephemeral: true });
    return;
  }
  await interaction.reply({ content: "Ticket wird geloescht...", ephemeral: true });
  setTimeout(() => {
    channel.delete("betterUC ticket closed").catch(error => console.warn("Could not delete ticket", error.message));
  }, 1500);
}

function ticketPanelPayload() {
  const embed = new EmbedBuilder()
    .setTitle("betterUC Support")
    .setColor(0x38bdf8)
    .setDescription("Waehle ein Thema aus, dann erstellt der Bot ein privates Ticket fuer dich.");

  const row = new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId("ticket:open:support").setLabel("Support").setStyle(ButtonStyle.Primary),
    new ButtonBuilder().setCustomId("ticket:open:bug").setLabel("Bug melden").setStyle(ButtonStyle.Secondary),
    new ButtonBuilder().setCustomId("ticket:open:access").setLabel("Access-Code").setStyle(ButtonStyle.Secondary),
    new ButtonBuilder().setCustomId("ticket:open:account").setLabel("Account").setStyle(ButtonStyle.Secondary)
  );

  return { embeds: [embed], components: [row] };
}

function hasManageGuild(interaction) {
  return Boolean(interaction.memberPermissions?.has(PermissionFlagsBits.ManageGuild));
}

async function handleCodeCommand(interaction, context) {
  if (!hasManageGuild(interaction)) {
    await interaction.reply({ content: "Dafuer brauchst du Discord-Adminrechte.", ephemeral: true });
    return;
  }

  const subcommand = interaction.options.getSubcommand();
  const name = interaction.options.getString("name", true);

  try {
    if (subcommand === "create") {
      const role = interaction.options.getString("rolle") || "user";
      const faction = interaction.options.getString("fraktion") || "";
      const result = await context.createAccessAccount({
        minecraftName: name,
        role,
        faction,
        createdBy: `discord:${interaction.user.id}`
      });
      await interaction.reply({
        content: [
          `Access-Code fuer **${result.account.minecraftName}** erstellt.`,
          "",
          "```text",
          result.accessCode,
          "```",
          "Der Code wird nur hier angezeigt."
        ].join("\n"),
        ephemeral: true
      });
      return;
    }

    if (subcommand === "reset") {
      const result = await context.resetAccessCodeByMinecraftName(name);
      await interaction.reply({
        content: [
          `Neuer Access-Code fuer **${result.account.minecraftName}**:`,
          "",
          "```text",
          result.accessCode,
          "```"
        ].join("\n"),
        ephemeral: true
      });
      return;
    }

    if (subcommand === "revoke") {
      const account = await context.revokeAccountByMinecraftName(name);
      if (account.discordId && interaction.guild) {
        const member = await interaction.guild.members.fetch(account.discordId).catch(() => null);
        await removeBetterUcRoles(member);
      }
      await interaction.reply({ content: `Account **${account.minecraftName}** wurde gesperrt.`, ephemeral: true });
    }
  } catch (error) {
    await interaction.reply({ content: error.message || "Aktion fehlgeschlagen.", ephemeral: true });
  }
}

async function interactionMember(interaction) {
  if (!interaction.guild) return interaction.member;
  return interaction.guild.members.fetch(interaction.user.id).catch(() => interaction.member);
}

async function handleCommand(interaction, context) {
  if (interaction.commandName === "online") {
    await interaction.reply({ embeds: [onlineEmbed(context.getOnlinePlayers())] });
    return;
  }

  if (interaction.commandName === "relay") {
    await interaction.reply({
      embeds: [relayEmbed(context.getOnlinePlayers(), context.getAccounts())],
      ephemeral: true
    });
    return;
  }

  if (interaction.commandName === "user") {
    const name = interaction.options.getString("name", true);
    const account = context.findAccountByMinecraftName(name);
    const onlinePlayer = context.getOnlinePlayers()
      .find(player => String(player.name || "").toLowerCase() === name.toLowerCase());
    if (!account && !onlinePlayer) {
      await interaction.reply({ content: "Zu diesem Spieler wurden keine betterUC Daten gefunden.", ephemeral: true });
      return;
    }
    await interaction.reply({ embeds: [userEmbed(account, onlinePlayer)], ephemeral: true });
    return;
  }

  if (interaction.commandName === "me") {
    const account = context.findAccountByDiscordId(interaction.user.id);
    if (!account) {
      await interaction.reply({
        content: "Dein Discord-Account ist noch nicht verknuepft. Nutze `/link code:<dein-code>`.",
        ephemeral: true
      });
      return;
    }
    const onlinePlayer = context.getOnlinePlayers()
      .find(player => String(player.name || "").toLowerCase() === String(account.minecraftName || "").toLowerCase());
    await interaction.reply({ embeds: [userEmbed(account, onlinePlayer)], ephemeral: true });
    return;
  }

  if (interaction.commandName === "link") {
    try {
      const account = await context.linkDiscordAccountByCode(
        interaction.options.getString("code", true),
        interaction.user.id
      );
      await syncBetterUcRoles(await interactionMember(interaction), account);
      await interaction.reply({
        content: `Verknuepft mit **${account.minecraftName}**. Der Bot kann dich jetzt als betterUC Mod-User erkennen.`,
        ephemeral: true
      });
    } catch (error) {
      await interaction.reply({ content: error.message || "Verknuepfung fehlgeschlagen.", ephemeral: true });
    }
    return;
  }

  if (interaction.commandName === "unlink") {
    try {
      const account = await context.unlinkDiscordAccount(interaction.user.id);
      await removeBetterUcRoles(await interactionMember(interaction));
      await interaction.reply({
        content: `Verknuepfung zu **${account.minecraftName}** wurde geloest.`,
        ephemeral: true
      });
    } catch (error) {
      await interaction.reply({ content: error.message || "Verknuepfung fehlgeschlagen.", ephemeral: true });
    }
    return;
  }

  if (interaction.commandName === "ticket") {
    await openTicket(interaction, interaction.options.getString("thema", true));
    return;
  }

  if (interaction.commandName === "ticket-panel") {
    await interaction.channel.send(ticketPanelPayload());
    await interaction.reply({ content: "Ticket-Panel wurde gepostet.", ephemeral: true });
    return;
  }

  if (interaction.commandName === "updates") {
    if (!hasManageGuild(interaction)) {
      await interaction.reply({ content: "Dafuer brauchst du Discord-Adminrechte.", ephemeral: true });
      return;
    }
    const subcommand = interaction.options.getSubcommand();
    try {
      const result = await checkGithubRelease(interaction.client, {
        forcePost: subcommand === "post_latest",
        announceExisting: true
      });
      if (result.status === "posted") {
        await interaction.reply({ content: "Update wurde im Discord-Update-Channel gepostet.", ephemeral: true });
      } else if (result.status === "unchanged" || result.status === "initialized") {
        await interaction.reply({
          content: `Kein neues Release. Aktuell erkannt: ${result.release?.tag_name || "unbekannt"}.`,
          ephemeral: true
        });
      } else {
        await interaction.reply({ content: `Update-Check Status: ${result.status}.`, ephemeral: true });
      }
    } catch (error) {
      await interaction.reply({ content: error.message || "Update-Check fehlgeschlagen.", ephemeral: true });
    }
    return;
  }

  if (interaction.commandName === "code") {
    await handleCodeCommand(interaction, context);
  }
}

async function handleInteraction(interaction, context) {
  try {
    if (interaction.isChatInputCommand()) {
      await handleCommand(interaction, context);
      return;
    }

    if (interaction.isButton()) {
      if (interaction.customId.startsWith("ticket:open:")) {
        await openTicket(interaction, interaction.customId.split(":")[2] || "support");
        return;
      }
      if (interaction.customId === "ticket:close") {
        await closeTicket(interaction);
        return;
      }
      if (interaction.customId === "ticket:delete") {
        await deleteTicket(interaction);
      }
    }
  } catch (error) {
    console.error("Discord interaction error", error);
    if (interaction.deferred || interaction.replied) {
      await interaction.followUp({ content: "Discord-Aktion fehlgeschlagen.", ephemeral: true }).catch(() => null);
    } else {
      await interaction.reply({ content: "Discord-Aktion fehlgeschlagen.", ephemeral: true }).catch(() => null);
    }
  }
}

async function syncBetterUcRoleState(client, context) {
  if (!GUILD_ID) return;
  const guild = await client.guilds.fetch(GUILD_ID).catch(() => null);
  if (!guild) return;
  await guild.roles.fetch().catch(() => null);

  for (const account of context.getAccounts()) {
    if (!account.discordId) continue;
    const member = await guild.members.fetch(account.discordId).catch(() => null);
    if (!member) continue;
    await syncBetterUcRoles(member, account);
  }
}

async function startDiscordBot(context) {
  if (!BOT_TOKEN) {
    return { notifyStateChanged() {}, stop() {} };
  }

  try {
    loadDiscord();
  } catch (error) {
    console.error("Discord bot is enabled, but discord.js is not installed. Run npm install in the server directory.", error.message);
    return { notifyStateChanged() {}, stop() {} };
  }

  const client = new Client({
    intents: [
      GatewayIntentBits.Guilds
    ]
  });

  let ready = false;
  let presenceTimer = null;
  let roleSyncTimer = null;
  let releaseCheckTimer = null;

  const updatePresence = () => {
    if (!ready || !client.user) return;
    const count = context.getOnlinePlayers().length;
    client.user.setPresence({
      status: count > 0 ? "online" : "idle",
      activities: [{
        name: `${count} Mod-User online`,
        type: ActivityType.Watching
      }]
    });
  };

  const notifyStateChanged = () => {
    clearTimeout(presenceTimer);
    presenceTimer = setTimeout(updatePresence, 750);
  };

  client.once("ready", async () => {
    ready = true;
    botState = await readBotState();
    console.log(`betterUC Discord bot logged in as ${client.user.tag}`);
    try {
      if (GUILD_ID) {
        const guild = await client.guilds.fetch(GUILD_ID);
        await guild.commands.set(buildCommands());
        console.log(`betterUC Discord commands synced for ${guild.name}`);
      } else {
        await client.application.commands.set(buildCommands());
        console.log("betterUC Discord commands synced globally");
      }
    } catch (error) {
      console.error("Could not sync betterUC Discord commands", error);
    }
    updatePresence();
    syncBetterUcRoleState(client, context).catch(error => console.warn("Discord role sync failed", error.message));
    roleSyncTimer = setInterval(() => {
      syncBetterUcRoleState(client, context).catch(error => console.warn("Discord role sync failed", error.message));
    }, Math.max(60 * 1000, Number(process.env.DISCORD_ROLE_SYNC_MS || 5 * 60 * 1000)));
    checkGithubRelease(client).catch(error => console.warn("Discord release check failed", error.message));
    releaseCheckTimer = setInterval(() => {
      checkGithubRelease(client).catch(error => console.warn("Discord release check failed", error.message));
    }, RELEASE_CHECK_MS);
  });

  client.on("interactionCreate", interaction => handleInteraction(interaction, context));
  client.on("error", error => console.error("Discord bot error", error));
  client.on("warn", message => console.warn("Discord bot warning", message));

  await client.login(BOT_TOKEN);

  return {
    notifyStateChanged,
    stop() {
      clearTimeout(presenceTimer);
      clearInterval(roleSyncTimer);
      clearInterval(releaseCheckTimer);
      client.destroy();
    }
  };
}

module.exports = {
  startDiscordBot
};
