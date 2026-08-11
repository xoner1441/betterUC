"use strict";

const crypto = require("crypto");
const fsp = require("fs").promises;
const path = require("path");
const { Pool } = require("pg");

const ROLE_VALUES = new Set(["user", "vip", "partner", "helper", "admin"]);
const STATUS_VALUES = new Set(["active", "revoked"]);

function nullableInteger(value) {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) ? parsed : null;
}

function iso(value) {
  if (!value) return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function text(value, fallback = "") {
  return typeof value === "string" ? value : fallback;
}

function role(value) {
  const normalized = text(value, "user").trim().toLowerCase();
  return ROLE_VALUES.has(normalized) ? normalized : "user";
}

function status(value) {
  const normalized = text(value, "active").trim().toLowerCase();
  return STATUS_VALUES.has(normalized) ? normalized : "active";
}

function optional(target, key, value) {
  if (value !== null && value !== undefined && value !== "") {
    target[key] = value;
  }
}

function accountFromRow(row) {
  const account = {
    id: row.id,
    minecraftName: row.minecraft_name,
    minecraftUuid: row.minecraft_uuid || "",
    faction: row.faction || "",
    role: role(row.role),
    status: status(row.status),
    createdAt: iso(row.created_at),
    createdBy: row.created_by || "server",
    lastSeenAt: iso(row.last_seen_at),
    lastStatsAt: iso(row.last_stats_at),
    lastServer: row.last_server || "",
    lastChannel: row.last_channel || "",
    lastVersion: row.last_version || "",
    lastGameVersion: row.last_game_version || ""
  };
  optional(account, "activatedAt", iso(row.activated_at));
  optional(account, "revokedAt", iso(row.revoked_at));
  optional(account, "resetAt", iso(row.reset_at));
  optional(account, "updatedAt", iso(row.updated_at));
  return account;
}

function suggestionFromRow(row) {
  if (!row) return null;
  return {
    id: Number(row.id),
    guildId: row.guild_id || "",
    channelId: row.channel_id || "",
    messageId: row.message_id || "",
    authorDiscordId: row.author_discord_id || "",
    accountId: row.account_id || null,
    title: row.title || "",
    description: row.description || "",
    status: row.status || "open",
    statusNote: row.status_note || "",
    upvotes: Number(row.upvotes || 0),
    downvotes: Number(row.downvotes || 0),
    createdAt: iso(row.created_at),
    updatedAt: iso(row.updated_at)
  };
}

function screenshotUploadFromRow(row) {
  if (!row) return null;
  return {
    id: row.id,
    accountId: row.account_id || null,
    originalName: row.original_name || "",
    storageName: row.storage_name || "",
    byteSize: Number(row.byte_size || 0),
    createdAt: iso(row.created_at),
    expiresAt: iso(row.expires_at),
    deletedAt: iso(row.deleted_at)
  };
}

function createDatabase(options = {}) {
  const connectionString = text(options.connectionString || process.env.DATABASE_URL).trim();
  const required = String(options.required ?? process.env.DATABASE_REQUIRED ?? "false").toLowerCase() === "true";
  const logger = options.logger || console;
  if (!connectionString) {
    const unavailable = async () => {
      throw new Error("PostgreSQL cloud settings are unavailable.");
    };
    return {
      enabled: false,
      required,
      async initialize() {},
      async loadAccounts() { return []; },
      async replaceAccounts() {},
      getCloudSettings: unavailable,
      putCloudSettings: unavailable,
      listCloudSettingsMetadata: unavailable,
      getCloudSettingsHistory: unavailable,
      recordCloudSyncEvent: unavailable,
      restoreCloudSettings: unavailable,
      deleteCloudSettings: unavailable,
      listFeatureFlags: unavailable,
      updateFeatureFlag: unavailable,
      listWasteDropAreas: unavailable,
      upsertWasteDropArea: unavailable,
      deleteWasteDropArea: unavailable,
      createScreenshotUpload: unavailable,
      getScreenshotUpload: unavailable,
      listExpiredScreenshotUploads: unavailable,
      markScreenshotUploadDeleted: unavailable,
      createDiscordTicket: unavailable,
      claimDiscordTicket: unavailable,
      closeDiscordTicket: unavailable,
      createDiscordSuggestion: unavailable,
      attachDiscordSuggestionMessage: unavailable,
      getDiscordSuggestion: unavailable,
      voteDiscordSuggestion: unavailable,
      updateDiscordSuggestionStatus: unavailable,
      recordDiscordActivity: unavailable,
      getDiscordWeeklyStats: unavailable,
      getOverview: unavailable,
      async close() {}
    };
  }

  const pool = new Pool({
    connectionString,
    max: Math.max(1, Number(process.env.DB_POOL_MAX || 10)),
    connectionTimeoutMillis: Math.max(1000, Number(process.env.DB_CONNECT_TIMEOUT_MS || 5000)),
    idleTimeoutMillis: Math.max(1000, Number(process.env.DB_IDLE_TIMEOUT_MS || 30000))
  });
  pool.on("error", error => logger.error("Unexpected PostgreSQL pool error", error));

  async function migrate() {
    const migrationsDir = path.join(__dirname, "migrations");
    const files = (await fsp.readdir(migrationsDir))
      .filter(name => /^\d+.*\.sql$/i.test(name))
      .sort();
    const client = await pool.connect();
    try {
      await client.query(`
        create table if not exists schema_migrations (
          version text primary key,
          checksum text not null,
          applied_at timestamptz not null default now()
        )
      `);
      for (const file of files) {
        const sql = await fsp.readFile(path.join(migrationsDir, file), "utf8");
        const checksum = crypto.createHash("sha256").update(sql).digest("hex");
        const existing = await client.query(
          "select checksum from schema_migrations where version = $1",
          [file]
        );
        if (existing.rowCount > 0) {
          if (existing.rows[0].checksum !== checksum) {
            throw new Error(`Database migration ${file} changed after it was applied.`);
          }
          continue;
        }
        await client.query("begin");
        try {
          await client.query(sql);
          await client.query(
            "insert into schema_migrations(version, checksum) values ($1, $2)",
            [file, checksum]
          );
          await client.query("commit");
          logger.log(`Applied PostgreSQL migration ${file}`);
        } catch (error) {
          await client.query("rollback");
          throw error;
        }
      }
    } finally {
      client.release();
    }
  }

  async function initialize() {
    await pool.query("select 1");
    await migrate();
  }

  async function loadAccounts() {
    const [accountsResult, tokensResult, credentialsResult, statsResult, historyResult, discordResult] = await Promise.all([
      pool.query("select * from accounts order by created_at, id"),
      pool.query("select * from access_tokens"),
      pool.query("select * from web_credentials"),
      pool.query("select * from player_stats"),
      pool.query("select * from stats_history order by account_id, recorded_at"),
      pool.query("select * from discord_links")
    ]);
    const accounts = accountsResult.rows.map(accountFromRow);
    const byId = new Map(accounts.map(account => [account.id, account]));

    for (const row of tokensResult.rows) {
      const account = byId.get(row.account_id);
      if (!account) continue;
      account.tokenHash = row.token_hash;
      account.tokenPrefix = row.token_prefix || "";
    }
    for (const row of credentialsResult.rows) {
      const account = byId.get(row.account_id);
      if (!account) continue;
      optional(account, "webPasswordHash", row.password_hash);
      optional(account, "webPasswordSalt", row.password_salt);
      optional(account, "webPasswordSetAt", iso(row.password_set_at));
      optional(account, "webPasswordClearedAt", iso(row.password_cleared_at));
      optional(account, "webSessionsInvalidAfter", iso(row.sessions_invalid_after));
      optional(account, "lastPanelLoginAt", iso(row.last_panel_login_at));
    }
    for (const row of statsResult.rows) {
      const account = byId.get(row.account_id);
      if (!account) continue;
      account.stats = {
        bankMoney: nullableInteger(row.bank_money),
        cashMoney: nullableInteger(row.cash_money),
        factionDisplay: row.faction_display || "",
        houses: row.houses || "",
        loyaltyBonus: nullableInteger(row.loyalty_bonus),
        playTimeHours: nullableInteger(row.play_time_hours),
        votepoints: nullableInteger(row.votepoints),
        warns: row.warns || "",
        updatedAt: iso(row.updated_at)
      };
    }
    for (const row of historyResult.rows) {
      const account = byId.get(row.account_id);
      if (!account) continue;
      if (!Array.isArray(account.statsHistory)) account.statsHistory = [];
      account.statsHistory.push({
        at: iso(row.recorded_at),
        bankMoney: nullableInteger(row.bank_money),
        cashMoney: nullableInteger(row.cash_money),
        factionDisplay: row.faction_display || "",
        houses: row.houses || "",
        loyaltyBonus: nullableInteger(row.loyalty_bonus),
        playTimeHours: nullableInteger(row.play_time_hours),
        votepoints: nullableInteger(row.votepoints),
        warns: row.warns || ""
      });
    }
    for (const row of discordResult.rows) {
      const account = byId.get(row.account_id);
      if (!account) continue;
      account.discordId = row.discord_id;
      optional(account, "discordLinkedAt", iso(row.linked_at));
      optional(account, "discordUnlinkedAt", iso(row.unlinked_at));
    }
    return accounts;
  }

  async function replaceAccounts(accounts) {
    const snapshot = Array.isArray(accounts) ? accounts : [];
    const client = await pool.connect();
    try {
      await client.query("begin");
      for (const account of snapshot) {
        await client.query(`
          insert into accounts (
            id, minecraft_name, minecraft_uuid, faction, role, status, created_at, created_by,
            activated_at, revoked_at, reset_at, updated_at, last_seen_at, last_stats_at,
            last_server, last_channel, last_version, last_game_version
          ) values (
            $1, $2, $3, $4, $5, $6, coalesce($7::timestamptz, now()), $8,
            $9, $10, $11, $12, $13, $14, $15, $16, $17, $18
          )
          on conflict (id) do update set
            minecraft_name = excluded.minecraft_name,
            minecraft_uuid = excluded.minecraft_uuid,
            faction = excluded.faction,
            role = excluded.role,
            status = excluded.status,
            created_by = excluded.created_by,
            activated_at = excluded.activated_at,
            revoked_at = excluded.revoked_at,
            reset_at = excluded.reset_at,
            updated_at = excluded.updated_at,
            last_seen_at = excluded.last_seen_at,
            last_stats_at = excluded.last_stats_at,
            last_server = excluded.last_server,
            last_channel = excluded.last_channel,
            last_version = excluded.last_version,
            last_game_version = excluded.last_game_version
        `, [
          account.id,
          text(account.minecraftName),
          text(account.minecraftUuid),
          text(account.faction),
          role(account.role),
          status(account.status),
          iso(account.createdAt),
          text(account.createdBy, "server"),
          iso(account.activatedAt),
          iso(account.revokedAt),
          iso(account.resetAt),
          iso(account.updatedAt),
          iso(account.lastSeenAt),
          iso(account.lastStatsAt),
          text(account.lastServer),
          text(account.lastChannel),
          text(account.lastVersion),
          text(account.lastGameVersion)
        ]);

        if (account.tokenHash) {
          await client.query(`
            insert into access_tokens(account_id, token_hash, token_prefix, updated_at)
            values ($1, $2, $3, now())
            on conflict (account_id) do update set
              token_hash = excluded.token_hash,
              token_prefix = excluded.token_prefix,
              updated_at = now()
          `, [account.id, account.tokenHash, text(account.tokenPrefix)]);
        } else {
          await client.query("delete from access_tokens where account_id = $1", [account.id]);
        }

        const hasWebCredentialState = Boolean(
          account.webPasswordHash
          || account.webPasswordSalt
          || account.webPasswordSetAt
          || account.webPasswordClearedAt
          || account.webSessionsInvalidAfter
          || account.lastPanelLoginAt
        );
        if (hasWebCredentialState) {
          await client.query(`
            insert into web_credentials (
              account_id, password_hash, password_salt, password_set_at, password_cleared_at,
              sessions_invalid_after, last_panel_login_at
            ) values ($1, $2, $3, $4, $5, $6, $7)
            on conflict (account_id) do update set
              password_hash = excluded.password_hash,
              password_salt = excluded.password_salt,
              password_set_at = excluded.password_set_at,
              password_cleared_at = excluded.password_cleared_at,
              sessions_invalid_after = excluded.sessions_invalid_after,
              last_panel_login_at = excluded.last_panel_login_at
          `, [
            account.id,
            account.webPasswordHash || null,
            account.webPasswordSalt || null,
            iso(account.webPasswordSetAt),
            iso(account.webPasswordClearedAt),
            iso(account.webSessionsInvalidAfter),
            iso(account.lastPanelLoginAt)
          ]);
        } else {
          await client.query("delete from web_credentials where account_id = $1", [account.id]);
        }

        const stats = account.stats && typeof account.stats === "object" ? account.stats : null;
        if (stats) {
          await client.query(`
            insert into player_stats (
              account_id, bank_money, cash_money, faction_display, houses, loyalty_bonus,
              play_time_hours, votepoints, warns, updated_at
            ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, coalesce($10::timestamptz, now()))
            on conflict (account_id) do update set
              bank_money = excluded.bank_money,
              cash_money = excluded.cash_money,
              faction_display = excluded.faction_display,
              houses = excluded.houses,
              loyalty_bonus = excluded.loyalty_bonus,
              play_time_hours = excluded.play_time_hours,
              votepoints = excluded.votepoints,
              warns = excluded.warns,
              updated_at = excluded.updated_at
          `, [
            account.id,
            nullableInteger(stats.bankMoney),
            nullableInteger(stats.cashMoney),
            text(stats.factionDisplay),
            text(stats.houses),
            nullableInteger(stats.loyaltyBonus),
            nullableInteger(stats.playTimeHours),
            nullableInteger(stats.votepoints),
            text(stats.warns),
            iso(stats.updatedAt)
          ]);
        } else {
          await client.query("delete from player_stats where account_id = $1", [account.id]);
        }

        await client.query("delete from stats_history where account_id = $1", [account.id]);
        for (const entry of Array.isArray(account.statsHistory) ? account.statsHistory.slice(-20) : []) {
          const recordedAt = iso(entry.at || entry.updatedAt);
          if (!recordedAt) continue;
          await client.query(`
            insert into stats_history (
              account_id, recorded_at, bank_money, cash_money, faction_display, houses,
              loyalty_bonus, play_time_hours, votepoints, warns
            ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
            on conflict (account_id, recorded_at) do update set
              bank_money = excluded.bank_money,
              cash_money = excluded.cash_money,
              faction_display = excluded.faction_display,
              houses = excluded.houses,
              loyalty_bonus = excluded.loyalty_bonus,
              play_time_hours = excluded.play_time_hours,
              votepoints = excluded.votepoints,
              warns = excluded.warns
          `, [
            account.id,
            recordedAt,
            nullableInteger(entry.bankMoney),
            nullableInteger(entry.cashMoney),
            text(entry.factionDisplay),
            text(entry.houses),
            nullableInteger(entry.loyaltyBonus),
            nullableInteger(entry.playTimeHours),
            nullableInteger(entry.votepoints),
            text(entry.warns)
          ]);
        }

        if (account.discordId) {
          await client.query(`
            insert into discord_links(account_id, discord_id, linked_at, unlinked_at)
            values ($1, $2, $3, $4)
            on conflict (account_id) do update set
              discord_id = excluded.discord_id,
              linked_at = excluded.linked_at,
              unlinked_at = excluded.unlinked_at
          `, [account.id, account.discordId, iso(account.discordLinkedAt), iso(account.discordUnlinkedAt)]);
        } else {
          await client.query("delete from discord_links where account_id = $1", [account.id]);
        }
      }

      const accountIds = snapshot.map(account => account.id);
      if (accountIds.length === 0) {
        await client.query("delete from accounts");
      } else {
        await client.query("delete from accounts where not (id = any($1::uuid[]))", [accountIds]);
      }
      await client.query("commit");
    } catch (error) {
      await client.query("rollback");
      throw error;
    } finally {
      client.release();
    }
  }

  function cloudSettingsFromRow(row) {
    if (!row) return null;
    return {
      schemaVersion: Number(row.schema_version),
      revision: Number(row.revision),
      settings: row.settings && typeof row.settings === "object" ? row.settings : {},
      updatedAt: iso(row.updated_at),
      updatedByVersion: row.updated_by_version || ""
    };
  }

  function cloudHistoryFromRow(row) {
    if (!row) return null;
    return {
      id: Number(row.id),
      revision: Number(row.revision),
      schemaVersion: Number(row.schema_version),
      recordedAt: iso(row.recorded_at),
      updatedByVersion: row.updated_by_version || "",
      action: row.action || "update",
      restoredFromRevision: row.restored_from_revision === null
        ? null
        : Number(row.restored_from_revision)
    };
  }

  async function recordCloudHistory(client, accountId, profile, action, restoredFromRevision = null) {
    await client.query(`
      insert into cloud_settings_history (
        account_id, revision, schema_version, settings, recorded_at,
        updated_by_version, action, restored_from_revision
      ) values ($1, $2, $3, $4::jsonb, now(), $5, $6, $7)
    `, [
      accountId,
      Number(profile.revision),
      Number(profile.schemaVersion),
      JSON.stringify(profile.settings || {}),
      text(profile.updatedByVersion),
      action,
      restoredFromRevision
    ]);
    await client.query(`
      delete from cloud_settings_history
      where account_id = $1
        and id not in (
          select id
          from cloud_settings_history
          where account_id = $1
          order by recorded_at desc, id desc
          limit 20
        )
    `, [accountId]);
  }

  async function getCloudSettings(accountId) {
    const result = await pool.query(
      "select schema_version, revision, settings, updated_at, updated_by_version from cloud_settings where account_id = $1",
      [accountId]
    );
    return result.rowCount > 0 ? cloudSettingsFromRow(result.rows[0]) : null;
  }

  async function putCloudSettings(accountId, update) {
    const client = await pool.connect();
    try {
      await client.query("begin");
      await client.query("select id from accounts where id = $1 for update", [accountId]);
      const currentResult = await client.query(
        "select schema_version, revision, settings, updated_at, updated_by_version from cloud_settings where account_id = $1 for update",
        [accountId]
      );
      const current = currentResult.rowCount > 0 ? cloudSettingsFromRow(currentResult.rows[0]) : null;
      const expectedRevision = current ? current.revision : 0;
      if (Number(update.baseRevision) !== expectedRevision) {
        await client.query("rollback");
        return { conflict: true, current };
      }

      const nextRevision = expectedRevision + 1;
      const result = await client.query(`
        insert into cloud_settings (
          account_id, schema_version, revision, settings, updated_at, updated_by_version
        ) values ($1, $2, $3, $4::jsonb, now(), $5)
        on conflict (account_id) do update set
          schema_version = excluded.schema_version,
          revision = excluded.revision,
          settings = excluded.settings,
          updated_at = now(),
          updated_by_version = excluded.updated_by_version
        returning schema_version, revision, settings, updated_at, updated_by_version
      `, [
        accountId,
        Number(update.schemaVersion),
        nextRevision,
        JSON.stringify(update.settings || {}),
        text(update.updatedByVersion)
      ]);
      const savedProfile = cloudSettingsFromRow(result.rows[0]);
      await recordCloudHistory(client, accountId, savedProfile, "update");
      await client.query(`
        insert into audit_log(account_id, actor, action, details)
        values ($1, $2, 'cloud_settings.updated', $3::jsonb)
      `, [
        accountId,
        `mod:${text(update.updatedByVersion, "unknown")}`,
        JSON.stringify({ revision: nextRevision, schemaVersion: Number(update.schemaVersion) })
      ]);
      await client.query("commit");
      return { conflict: false, current: savedProfile };
    } catch (error) {
      await client.query("rollback");
      throw error;
    } finally {
      client.release();
    }
  }

  async function listCloudSettingsMetadata() {
    const result = await pool.query(`
      select
        accounts.id as account_id,
        current_settings.schema_version,
        current_settings.revision,
        current_settings.updated_at,
        current_settings.updated_by_version,
        coalesce(history_stats.history_count, 0)::integer as history_count,
        latest_event.event_type as last_event_type,
        latest_event.revision as last_event_revision,
        latest_event.mod_version as last_event_mod_version,
        latest_event.detail as last_event_detail,
        latest_event.created_at as last_event_at,
        coalesce(event_stats.event_count, 0)::integer as event_count,
        coalesce(event_stats.conflicts_24h, 0)::integer as conflicts_24h,
        coalesce(event_stats.errors_24h, 0)::integer as errors_24h
      from accounts
      left join cloud_settings current_settings on current_settings.account_id = accounts.id
      left join lateral (
        select count(*)::integer as history_count
        from cloud_settings_history history
        where history.account_id = accounts.id
      ) history_stats on true
      left join lateral (
        select
          count(*)::integer as event_count,
          count(*) filter (
            where event_type = 'conflict'
              and created_at >= now() - interval '24 hours'
          )::integer as conflicts_24h,
          count(*) filter (
            where event_type = 'error'
              and created_at >= now() - interval '24 hours'
          )::integer as errors_24h
        from cloud_sync_events sync_event
        where sync_event.account_id = accounts.id
      ) event_stats on true
      left join lateral (
        select event_type, revision, mod_version, detail, created_at
        from cloud_sync_events latest
        where latest.account_id = accounts.id
        order by latest.created_at desc, latest.id desc
        limit 1
      ) latest_event on true
      where current_settings.revision is not null
        or coalesce(history_stats.history_count, 0) > 0
        or coalesce(event_stats.event_count, 0) > 0
      order by current_settings.updated_at desc nulls last, accounts.id
    `);
    return result.rows.map(row => ({
      accountId: row.account_id,
      exists: row.revision !== null,
      schemaVersion: row.schema_version === null ? null : Number(row.schema_version),
      revision: row.revision === null ? null : Number(row.revision),
      updatedAt: iso(row.updated_at),
      updatedByVersion: row.updated_by_version || "",
      historyCount: Number(row.history_count || 0),
      lastEventType: row.last_event_type || "",
      lastEventRevision: row.last_event_revision === null ? null : Number(row.last_event_revision),
      lastEventModVersion: row.last_event_mod_version || "",
      lastEventDetail: row.last_event_detail || "",
      lastEventAt: iso(row.last_event_at),
      conflicts24h: Number(row.conflicts_24h || 0),
      errors24h: Number(row.errors_24h || 0)
    }));
  }

  async function recordCloudSyncEvent(accountId, event = {}) {
    const eventType = text(event.type).trim().toLowerCase();
    if (!["download", "upload", "conflict", "error"].includes(eventType)) {
      throw new Error("Invalid cloud sync event type.");
    }
    const revision = nullableInteger(event.revision);
    const schemaVersion = nullableInteger(event.schemaVersion);
    const modVersion = text(event.modVersion).trim().slice(0, 64);
    const detail = text(event.detail).trim().slice(0, 500);
    await pool.query(`
      insert into cloud_sync_events (
        account_id, event_type, revision, schema_version, mod_version, detail
      ) values ($1, $2, $3, $4, $5, $6)
    `, [accountId, eventType, revision, schemaVersion, modVersion, detail]);
    await pool.query(`
      delete from cloud_sync_events
      where account_id = $1
        and id not in (
          select id
          from cloud_sync_events
          where account_id = $1
          order by created_at desc, id desc
          limit 200
        )
    `, [accountId]);
  }

  async function getCloudSettingsHistory(accountId, limit = 20) {
    const safeLimit = Math.max(1, Math.min(20, Number(limit) || 20));
    const result = await pool.query(`
      select
        id, revision, schema_version, recorded_at, updated_by_version,
        action, restored_from_revision
      from cloud_settings_history
      where account_id = $1
      order by recorded_at desc, id desc
      limit $2
    `, [accountId, safeLimit]);
    return result.rows.map(cloudHistoryFromRow);
  }

  async function restoreCloudSettings(accountId, historyId, actor = "admin:panel") {
    const client = await pool.connect();
    try {
      await client.query("begin");
      await client.query("select id from accounts where id = $1 for update", [accountId]);
      const sourceResult = await client.query(`
        select
          id, revision, schema_version, settings, recorded_at,
          updated_by_version, action, restored_from_revision
        from cloud_settings_history
        where account_id = $1 and id = $2
      `, [accountId, historyId]);
      if (sourceResult.rowCount === 0) {
        await client.query("rollback");
        return null;
      }

      const currentResult = await client.query(
        "select revision from cloud_settings where account_id = $1 for update",
        [accountId]
      );
      const maxHistoryResult = await client.query(
        "select coalesce(max(revision), 0)::bigint as revision from cloud_settings_history where account_id = $1",
        [accountId]
      );
      const currentRevision = currentResult.rowCount > 0 ? Number(currentResult.rows[0].revision) : 0;
      const maxHistoryRevision = Number(maxHistoryResult.rows[0].revision || 0);
      const nextRevision = Math.max(currentRevision, maxHistoryRevision) + 1;
      const source = sourceResult.rows[0];
      const result = await client.query(`
        insert into cloud_settings (
          account_id, schema_version, revision, settings, updated_at, updated_by_version
        ) values ($1, $2, $3, $4::jsonb, now(), $5)
        on conflict (account_id) do update set
          schema_version = excluded.schema_version,
          revision = excluded.revision,
          settings = excluded.settings,
          updated_at = now(),
          updated_by_version = excluded.updated_by_version
        returning schema_version, revision, settings, updated_at, updated_by_version
      `, [
        accountId,
        Number(source.schema_version),
        nextRevision,
        JSON.stringify(source.settings || {}),
        `restore:${text(source.updated_by_version, "unknown")}`
      ]);
      const restoredProfile = cloudSettingsFromRow(result.rows[0]);
      await recordCloudHistory(
        client,
        accountId,
        restoredProfile,
        "restore",
        Number(source.revision)
      );
      await client.query(`
        insert into audit_log(account_id, actor, action, details)
        values ($1, $2, 'cloud_settings.restored', $3::jsonb)
      `, [
        accountId,
        text(actor, "admin:panel"),
        JSON.stringify({
          revision: nextRevision,
          restoredFromRevision: Number(source.revision),
          historyId: Number(source.id)
        })
      ]);
      await client.query("commit");
      return restoredProfile;
    } catch (error) {
      await client.query("rollback");
      throw error;
    } finally {
      client.release();
    }
  }

  async function deleteCloudSettings(accountId, actor = "admin:panel") {
    const client = await pool.connect();
    try {
      await client.query("begin");
      await client.query("select id from accounts where id = $1 for update", [accountId]);
      const currentResult = await client.query(
        "select schema_version, revision, settings, updated_at, updated_by_version from cloud_settings where account_id = $1 for update",
        [accountId]
      );
      const current = currentResult.rowCount > 0 ? cloudSettingsFromRow(currentResult.rows[0]) : null;
      if (current) {
        await recordCloudHistory(client, accountId, current, "reset");
      }
      const result = await client.query("delete from cloud_settings where account_id = $1", [accountId]);
      await client.query(`
        insert into audit_log(account_id, actor, action, details)
        values ($1, $2, 'cloud_settings.reset', $3::jsonb)
      `, [
        accountId,
        text(actor, "admin:panel"),
        JSON.stringify({ previousRevision: current ? current.revision : null })
      ]);
      await client.query("commit");
      return result.rowCount > 0;
    } catch (error) {
      await client.query("rollback");
      throw error;
    } finally {
      client.release();
    }
  }

  function featureFlagFromRow(row) {
    if (!row) return null;
    return {
      key: row.key,
      enabled: Boolean(row.enabled),
      label: row.label || row.key,
      description: row.description || "",
      updatedAt: iso(row.updated_at),
      updatedBy: row.updated_by || ""
    };
  }

  async function listFeatureFlags() {
    const result = await pool.query(`
      select key, enabled, label, description, updated_at, updated_by
      from feature_flags
      order by key
    `);
    return result.rows.map(featureFlagFromRow);
  }

  async function updateFeatureFlag(key, enabled, actor = "admin:panel") {
    const client = await pool.connect();
    try {
      await client.query("begin");
      const result = await client.query(`
        update feature_flags
        set enabled = $2, updated_at = now(), updated_by = $3
        where key = $1
        returning key, enabled, label, description, updated_at, updated_by
      `, [text(key).trim().toLowerCase(), Boolean(enabled), text(actor, "admin:panel")]);
      if (result.rowCount === 0) {
        await client.query("rollback");
        return null;
      }
      const flag = featureFlagFromRow(result.rows[0]);
      await client.query(`
        insert into audit_log(account_id, actor, action, details)
        values (null, $1, 'feature_flag.updated', $2::jsonb)
      `, [
        text(actor, "admin:panel"),
        JSON.stringify({ key: flag.key, enabled: flag.enabled })
      ]);
      await client.query("commit");
      return flag;
    } catch (error) {
      await client.query("rollback");
      throw error;
    } finally {
      client.release();
    }
  }

  function wasteDropAreaFromRow(row) {
    if (!row) return null;
    return {
      type: row.waste_type,
      x1: nullableInteger(row.x1),
      z1: nullableInteger(row.z1),
      x2: nullableInteger(row.x2),
      z2: nullableInteger(row.z2),
      dimension: row.dimension || "",
      updatedAt: iso(row.updated_at),
      updatedBy: row.updated_by || ""
    };
  }

  async function listWasteDropAreas() {
    const result = await pool.query(`
      select waste_type, x1, z1, x2, z2, dimension, updated_at, updated_by
      from waste_drop_areas
      order by waste_type
    `);
    return result.rows.map(wasteDropAreaFromRow);
  }

  async function upsertWasteDropArea(type, area, actor = "admin:mod") {
    const result = await pool.query(`
      insert into waste_drop_areas(
        waste_type, x1, z1, x2, z2, dimension, updated_at, updated_by
      ) values ($1, $2, $3, $4, $5, $6, now(), $7)
      on conflict (waste_type) do update set
        x1 = excluded.x1,
        z1 = excluded.z1,
        x2 = excluded.x2,
        z2 = excluded.z2,
        dimension = excluded.dimension,
        updated_at = now(),
        updated_by = excluded.updated_by
      returning waste_type, x1, z1, x2, z2, dimension, updated_at, updated_by
    `, [
      text(type).trim().toLowerCase(),
      nullableInteger(area && area.x1),
      nullableInteger(area && area.z1),
      nullableInteger(area && area.x2),
      nullableInteger(area && area.z2),
      text(area && area.dimension),
      text(actor, "admin:mod")
    ]);
    return wasteDropAreaFromRow(result.rows[0]);
  }

  async function deleteWasteDropArea(type, actor = "admin:mod") {
    const client = await pool.connect();
    try {
      await client.query("begin");
      const result = await client.query(
        "delete from waste_drop_areas where waste_type = $1 returning waste_type",
        [text(type).trim().toLowerCase()]
      );
      await client.query(`
        insert into audit_log(account_id, actor, action, details)
        values (null, $1, 'waste_area.deleted', $2::jsonb)
      `, [text(actor, "admin:mod"), JSON.stringify({ type })]);
      await client.query("commit");
      return result.rowCount > 0;
    } catch (error) {
      await client.query("rollback");
      throw error;
    } finally {
      client.release();
    }
  }

  async function createScreenshotUpload(upload = {}) {
    const result = await pool.query(`
      insert into screenshot_uploads (
        id, account_id, original_name, storage_name, byte_size, expires_at
      ) values ($1, $2, $3, $4, $5, $6)
      returning *
    `, [
      text(upload.id),
      upload.accountId || null,
      text(upload.originalName),
      text(upload.storageName),
      Number(upload.byteSize || 0),
      upload.expiresAt
    ]);
    return screenshotUploadFromRow(result.rows[0]);
  }

  async function getScreenshotUpload(id) {
    const result = await pool.query(`
      select *
      from screenshot_uploads
      where id = $1 and deleted_at is null
      limit 1
    `, [text(id)]);
    return screenshotUploadFromRow(result.rows[0]);
  }

  async function listExpiredScreenshotUploads(limit = 100) {
    const safeLimit = Math.min(1000, Math.max(1, Number(limit || 100)));
    const result = await pool.query(`
      select *
      from screenshot_uploads
      where deleted_at is null and expires_at <= now()
      order by expires_at asc
      limit $1
    `, [safeLimit]);
    return result.rows.map(screenshotUploadFromRow);
  }

  async function markScreenshotUploadDeleted(id) {
    const result = await pool.query(`
      update screenshot_uploads
      set deleted_at = now()
      where id = $1 and deleted_at is null
      returning id
    `, [text(id)]);
    return result.rowCount > 0;
  }

  async function createDiscordTicket(ticket = {}) {
    const result = await pool.query(`
      insert into discord_tickets (
        guild_id, channel_id, opener_discord_id, account_id, topic
      ) values ($1, $2, $3, $4, $5)
      on conflict (channel_id) do update set
        opener_discord_id = excluded.opener_discord_id,
        account_id = excluded.account_id,
        topic = excluded.topic
      returning *
    `, [
      text(ticket.guildId),
      text(ticket.channelId),
      text(ticket.openerDiscordId),
      ticket.accountId || null,
      text(ticket.topic, "support")
    ]);
    return result.rows[0] || null;
  }

  async function claimDiscordTicket(channelId, claimedByDiscordId) {
    const result = await pool.query(`
      update discord_tickets
      set claimed_by_discord_id = $2,
          claimed_at = coalesce(claimed_at, now())
      where channel_id = $1 and status = 'open' and claimed_by_discord_id is null
      returning *
    `, [text(channelId), text(claimedByDiscordId)]);
    return result.rows[0] || null;
  }

  async function closeDiscordTicket(channelId, closeReason, transcriptPath = "") {
    const result = await pool.query(`
      update discord_tickets
      set status = 'closed',
          close_reason = $2,
          transcript_path = $3,
          closed_at = now()
      where channel_id = $1
      returning *
    `, [text(channelId), text(closeReason), text(transcriptPath)]);
    return result.rows[0] || null;
  }

  async function createDiscordSuggestion(suggestion = {}) {
    const result = await pool.query(`
      insert into discord_suggestions (
        guild_id, author_discord_id, account_id, title, description
      ) values ($1, $2, $3, $4, $5)
      returning *
    `, [
      text(suggestion.guildId),
      text(suggestion.authorDiscordId),
      suggestion.accountId || null,
      text(suggestion.title),
      text(suggestion.description)
    ]);
    return suggestionFromRow(result.rows[0]);
  }

  async function attachDiscordSuggestionMessage(id, channelId, messageId) {
    const result = await pool.query(`
      update discord_suggestions
      set channel_id = $2, message_id = $3, updated_at = now()
      where id = $1
      returning *
    `, [Number(id), text(channelId), text(messageId)]);
    return suggestionFromRow(result.rows[0]);
  }

  async function getDiscordSuggestion(id) {
    const result = await pool.query(`
      select suggestion.*,
        count(*) filter (where vote.vote = 1)::integer as upvotes,
        count(*) filter (where vote.vote = -1)::integer as downvotes
      from discord_suggestions suggestion
      left join discord_suggestion_votes vote on vote.suggestion_id = suggestion.id
      where suggestion.id = $1
      group by suggestion.id
    `, [Number(id)]);
    return suggestionFromRow(result.rows[0]);
  }

  async function voteDiscordSuggestion(id, discordId, vote) {
    const normalizedVote = Number(vote) === -1 ? -1 : 1;
    await pool.query(`
      insert into discord_suggestion_votes (suggestion_id, discord_id, vote)
      values ($1, $2, $3)
      on conflict (suggestion_id, discord_id) do update set
        vote = excluded.vote,
        voted_at = now()
    `, [Number(id), text(discordId), normalizedVote]);
    return getDiscordSuggestion(id);
  }

  async function updateDiscordSuggestionStatus(id, suggestionStatus, note = "") {
    const allowed = new Set(["open", "planned", "in_progress", "implemented", "rejected"]);
    const normalized = text(suggestionStatus).trim().toLowerCase();
    if (!allowed.has(normalized)) throw new Error("Invalid suggestion status.");
    const result = await pool.query(`
      update discord_suggestions
      set status = $2, status_note = $3, updated_at = now()
      where id = $1
      returning *
    `, [Number(id), normalized, text(note)]);
    return result.rows[0] ? getDiscordSuggestion(id) : null;
  }

  async function recordDiscordActivity(eventType, accountId = null, details = {}) {
    const result = await pool.query(`
      insert into discord_activity_events (event_type, account_id, details)
      values ($1, $2, $3::jsonb)
      returning id, created_at
    `, [text(eventType), accountId || null, JSON.stringify(details && typeof details === "object" ? details : {})]);
    return {
      id: Number(result.rows[0].id),
      createdAt: iso(result.rows[0].created_at)
    };
  }

  async function getDiscordWeeklyStats(since) {
    const from = iso(since) || new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString();
    const [activityResult, ticketResult, suggestionResult, cloudResult, accountResult, versionResult, gameVersionResult] = await Promise.all([
      pool.query(`
        select event_type, count(*)::integer as count
        from discord_activity_events
        where created_at >= $1
        group by event_type
      `, [from]),
      pool.query(`
        select
          count(*)::integer as opened,
          count(*) filter (where status = 'closed')::integer as closed,
          coalesce(avg(extract(epoch from (closed_at - opened_at)) / 60)
            filter (where closed_at is not null), 0)::integer as average_close_minutes
        from discord_tickets
        where opened_at >= $1
      `, [from]),
      pool.query(`
        select
          count(*)::integer as created,
          count(*) filter (where status = 'implemented')::integer as implemented,
          (select count(*)::integer from discord_suggestion_votes where voted_at >= $1) as votes
        from discord_suggestions
        where created_at >= $1
      `, [from]),
      pool.query(`
        select
          count(*)::integer as syncs,
          count(*) filter (where event_type = 'conflict')::integer as conflicts,
          count(*) filter (where event_type = 'error')::integer as errors
        from cloud_sync_events
        where created_at >= $1
      `, [from]),
      pool.query(`
        select
          count(*) filter (where status = 'active')::integer as active,
          count(*) filter (where discord_id is not null and status = 'active')::integer as linked,
          count(*) filter (where role = 'admin' and status = 'active')::integer as admins,
          count(*) filter (where role = 'helper' and status = 'active')::integer as helpers,
          count(*) filter (where role = 'partner' and status = 'active')::integer as partners,
          count(*) filter (where role = 'vip' and status = 'active')::integer as vips
        from accounts
        left join discord_links on discord_links.account_id = accounts.id
      `),
      pool.query(`
        select last_version as label, count(*)::integer as count
        from accounts
        where status = 'active' and last_version <> ''
        group by last_version
        order by count desc, label
      `),
      pool.query(`
        select last_game_version as label, count(*)::integer as count
        from accounts
        where status = 'active' and last_game_version <> ''
        group by last_game_version
        order by count desc, label
      `)
    ]);
    const activities = Object.fromEntries(activityResult.rows.map(row => [row.event_type, Number(row.count || 0)]));
    const tickets = ticketResult.rows[0] || {};
    const suggestions = suggestionResult.rows[0] || {};
    const cloud = cloudResult.rows[0] || {};
    const accounts = accountResult.rows[0] || {};
    return {
      since: from,
      activities,
      tickets: {
        opened: Number(tickets.opened || 0),
        closed: Number(tickets.closed || 0),
        averageCloseMinutes: Number(tickets.average_close_minutes || 0)
      },
      suggestions: {
        created: Number(suggestions.created || 0),
        implemented: Number(suggestions.implemented || 0),
        votes: Number(suggestions.votes || 0)
      },
      cloud: {
        syncs: Number(cloud.syncs || 0),
        conflicts: Number(cloud.conflicts || 0),
        errors: Number(cloud.errors || 0)
      },
      accounts: {
        active: Number(accounts.active || 0),
        linked: Number(accounts.linked || 0),
        admins: Number(accounts.admins || 0),
        helpers: Number(accounts.helpers || 0),
        partners: Number(accounts.partners || 0),
        vips: Number(accounts.vips || 0)
      },
      versions: versionResult.rows.map(row => ({ label: row.label, count: Number(row.count || 0) })),
      gameVersions: gameVersionResult.rows.map(row => ({ label: row.label, count: Number(row.count || 0) }))
    };
  }

  async function getOverview() {
    const migrationsDir = path.join(__dirname, "migrations");
    const expectedMigrations = (await fsp.readdir(migrationsDir))
      .filter(name => /^\d+.*\.sql$/i.test(name))
      .sort();
    const [databaseResult, migrationsResult, countsResult] = await Promise.all([
      pool.query(`
        select
          current_database() as database_name,
          current_setting('server_version') as server_version,
          pg_database_size(current_database())::text as database_size
      `),
      pool.query("select version, applied_at from schema_migrations order by version"),
      pool.query(`
        select
          (select count(*) from accounts)::integer as accounts,
          (select count(*) from cloud_settings)::integer as cloud_profiles,
          (select count(*) from cloud_settings_history)::integer as cloud_revisions,
          (select count(*) from cloud_sync_events where created_at >= now() - interval '24 hours')::integer as cloud_sync_events_24h,
          (select count(*) from cloud_sync_events where event_type = 'conflict' and created_at >= now() - interval '24 hours')::integer as cloud_conflicts_24h,
          (select count(*) from cloud_sync_events where event_type = 'error' and created_at >= now() - interval '24 hours')::integer as cloud_errors_24h,
          (select count(*) from player_stats)::integer as stats_profiles,
          (select count(*) from web_credentials where password_hash is not null)::integer as web_profiles,
          (select count(*) from feature_flags)::integer as feature_flags,
          (select count(*) from feature_flags where not enabled)::integer as disabled_feature_flags,
          (select count(*) from audit_log)::integer as audit_entries
      `)
    ]);
    const databaseRow = databaseResult.rows[0] || {};
    const counts = countsResult.rows[0] || {};
    const migrations = migrationsResult.rows.map(row => ({
      version: row.version,
      appliedAt: iso(row.applied_at)
    }));
    const applied = new Set(migrations.map(entry => entry.version));
    return {
      connected: true,
      databaseName: databaseRow.database_name || "",
      serverVersion: databaseRow.server_version || "",
      sizeBytes: Number(databaseRow.database_size || 0),
      migrations,
      expectedMigrations,
      pendingMigrations: expectedMigrations.filter(version => !applied.has(version)),
      latestMigration: migrations.at(-1) || null,
      counts: {
        accounts: Number(counts.accounts || 0),
        cloudProfiles: Number(counts.cloud_profiles || 0),
        cloudRevisions: Number(counts.cloud_revisions || 0),
        cloudSyncEvents24h: Number(counts.cloud_sync_events_24h || 0),
        cloudConflicts24h: Number(counts.cloud_conflicts_24h || 0),
        cloudErrors24h: Number(counts.cloud_errors_24h || 0),
        statsProfiles: Number(counts.stats_profiles || 0),
        webProfiles: Number(counts.web_profiles || 0),
        featureFlags: Number(counts.feature_flags || 0),
        disabledFeatureFlags: Number(counts.disabled_feature_flags || 0),
        auditEntries: Number(counts.audit_entries || 0)
      }
    };
  }

  return {
    enabled: true,
    required,
    initialize,
    loadAccounts,
    replaceAccounts,
    getCloudSettings,
    putCloudSettings,
    listCloudSettingsMetadata,
    getCloudSettingsHistory,
    recordCloudSyncEvent,
    restoreCloudSettings,
    deleteCloudSettings,
    listFeatureFlags,
    updateFeatureFlag,
    listWasteDropAreas,
    upsertWasteDropArea,
    deleteWasteDropArea,
    createScreenshotUpload,
    getScreenshotUpload,
    listExpiredScreenshotUploads,
    markScreenshotUploadDeleted,
    createDiscordTicket,
    claimDiscordTicket,
    closeDiscordTicket,
    createDiscordSuggestion,
    attachDiscordSuggestionMessage,
    getDiscordSuggestion,
    voteDiscordSuggestion,
    updateDiscordSuggestionStatus,
    recordDiscordActivity,
    getDiscordWeeklyStats,
    getOverview,
    async close() { await pool.end(); }
  };
}

module.exports = { createDatabase };
