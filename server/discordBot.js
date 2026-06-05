"use strict";

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

function roleLabel(role) {
  if (role === "admin") return "Admin";
  if (role === "helper") return "Helper";
  if (role === "vip") return "VIP";
  return "User";
}

function roleColor(role) {
  if (role === "admin") return 0xff4d5a;
  if (role === "helper") return 0xfacc15;
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
  const vips = activeAccounts.filter(account => account.role === "vip").length;

  return new EmbedBuilder()
    .setTitle("betterUC Relay")
    .setColor(players.length ? 0x22c55e : 0xfacc15)
    .addFields(
      { name: "Online", value: String(players.length), inline: true },
      { name: "Accounts", value: String(activeAccounts.length), inline: true },
      { name: "Team/VIP", value: `Admin ${admins} | Helper ${helpers} | VIP ${vips}`, inline: true }
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

async function addModUserRole(member) {
  if (!member || !member.guild || !MOD_USER_ROLE_NAME) return;
  await member.guild.roles.fetch().catch(() => null);
  const role = member.guild.roles.cache.find(entry => entry.name.toLowerCase() === MOD_USER_ROLE_NAME.toLowerCase());
  if (!role || member.roles.cache.has(role.id)) return;
  await member.roles.add(role).catch(error => console.warn("Could not add Mod-User role", error.message));
}

async function removeModUserRole(member) {
  if (!member || !member.guild || !MOD_USER_ROLE_NAME) return;
  await member.guild.roles.fetch().catch(() => null);
  const role = member.guild.roles.cache.find(entry => entry.name.toLowerCase() === MOD_USER_ROLE_NAME.toLowerCase());
  if (!role || !member.roles.cache.has(role.id)) return;
  await member.roles.remove(role).catch(error => console.warn("Could not remove Mod-User role", error.message));
}

function ticketCategoryOverwrites(guild, teamRoles) {
  const overwrites = [
    {
      id: guild.roles.everyone.id,
      deny: ["ViewChannel"]
    }
  ];
  for (const role of teamRoles.values()) {
    overwrites.push({
      id: role.id,
      allow: ["ViewChannel", "SendMessages", "ReadMessageHistory", "ManageMessages"]
    });
  }
  return overwrites;
}

function ticketChannelOverwrites(guild, openerId, teamRoles) {
  return [
    ...ticketCategoryOverwrites(guild, teamRoles),
    {
      id: openerId,
      allow: ["ViewChannel", "SendMessages", "ReadMessageHistory", "AttachFiles"]
    }
  ];
}

async function openTicket(interaction, topic) {
  const guild = interaction.guild;
  if (!guild) {
    await interaction.reply({ content: "Tickets koennen nur auf dem Server erstellt werden.", ephemeral: true });
    return;
  }

  await guild.roles.fetch().catch(() => null);
  const teamRoles = resolveTeamRoles(guild);
  const category = await findOrCreateTicketCategory(guild, teamRoles);
  const openTicket = guild.channels.cache.find(channel =>
    channel.type === ChannelType.GuildText
    && channel.name.startsWith(`ticket-${ticketPrefix(topic)}-`)
    && channel.topic
    && channel.topic.includes(`discord:${interaction.user.id}`)
  );
  if (openTicket) {
    await interaction.reply({ content: `Du hast bereits ein offenes Ticket: ${openTicket}`, ephemeral: true });
    return;
  }

  const channel = await guild.channels.create({
    name: `ticket-${ticketPrefix(topic)}-${slug(interaction.user.username)}`,
    type: ChannelType.GuildText,
    parent: category.id,
    topic: `betterUC Ticket | ${ticketLabel(topic)} | discord:${interaction.user.id}`,
    permissionOverwrites: ticketChannelOverwrites(guild, interaction.user.id, teamRoles)
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
  await interaction.reply({ content: `Ticket erstellt: ${channel}`, ephemeral: true });
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
  await interaction.reply({ content: "Ticket geschlossen. Ein Teammitglied kann den Channel nun bei Bedarf loeschen.", ephemeral: false });
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
        await removeModUserRole(member);
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
      await addModUserRole(await interactionMember(interaction));
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
      await removeModUserRole(await interactionMember(interaction));
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

async function syncModUserRole(client, context) {
  if (!GUILD_ID || !MOD_USER_ROLE_NAME) return;
  const guild = await client.guilds.fetch(GUILD_ID).catch(() => null);
  if (!guild) return;
  await guild.roles.fetch().catch(() => null);
  const role = guild.roles.cache.find(entry => entry.name.toLowerCase() === MOD_USER_ROLE_NAME.toLowerCase());
  if (!role) return;

  for (const account of context.getAccounts()) {
    if (!account.discordId) continue;
    const member = await guild.members.fetch(account.discordId).catch(() => null);
    if (!member) continue;
    if (account.status === "revoked") {
      if (member.roles.cache.has(role.id)) {
        await member.roles.remove(role).catch(error => console.warn("Could not remove Mod-User role", account.minecraftName, error.message));
      }
      continue;
    }
    if (!member.roles.cache.has(role.id)) {
      await member.roles.add(role).catch(error => console.warn("Could not add Mod-User role", account.minecraftName, error.message));
    }
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
    roleSyncTimer = setInterval(() => {
      syncModUserRole(client, context).catch(error => console.warn("Discord role sync failed", error.message));
    }, 10 * 60 * 1000);
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
      client.destroy();
    }
  };
}

module.exports = {
  startDiscordBot
};
