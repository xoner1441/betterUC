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
    lastVersion: row.last_version || ""
  };
  optional(account, "activatedAt", iso(row.activated_at));
  optional(account, "revokedAt", iso(row.revoked_at));
  optional(account, "resetAt", iso(row.reset_at));
  optional(account, "updatedAt", iso(row.updated_at));
  return account;
}

function createDatabase(options = {}) {
  const connectionString = text(options.connectionString || process.env.DATABASE_URL).trim();
  const required = String(options.required ?? process.env.DATABASE_REQUIRED ?? "false").toLowerCase() === "true";
  const logger = options.logger || console;
  if (!connectionString) {
    return {
      enabled: false,
      required,
      async initialize() {},
      async loadAccounts() { return []; },
      async replaceAccounts() {},
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
            last_server, last_channel, last_version
          ) values (
            $1, $2, $3, $4, $5, $6, coalesce($7::timestamptz, now()), $8,
            $9, $10, $11, $12, $13, $14, $15, $16, $17
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
            last_version = excluded.last_version
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
          text(account.lastVersion)
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

  return {
    enabled: true,
    required,
    initialize,
    loadAccounts,
    replaceAccounts,
    async close() { await pool.end(); }
  };
}

module.exports = { createDatabase };
