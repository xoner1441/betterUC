"use strict";

const fs = require("fs");
const path = require("path");
const { createDatabase } = require("../database");

async function main() {
  const replace = process.argv.includes("--replace");
  const verifyOnly = process.argv.includes("--verify-only");
  const sourceArg = process.argv.slice(2).find(argument => !argument.startsWith("--"));
  const sourceFile = path.resolve(sourceArg || path.join(__dirname, "..", "data", "accounts.json"));
  const parsed = JSON.parse(fs.readFileSync(sourceFile, "utf8"));
  const sourceAccounts = Array.isArray(parsed.accounts) ? parsed.accounts : null;
  if (!sourceAccounts) {
    throw new Error(`JSON store does not contain an accounts array: ${sourceFile}`);
  }

  const database = createDatabase({ required: true });
  if (!database.enabled) {
    throw new Error("DATABASE_URL is not configured.");
  }

  try {
    await database.initialize();
    const existing = await database.loadAccounts();
    if (existing.length > 0 && !replace && !verifyOnly) {
      throw new Error(
        `PostgreSQL already contains ${existing.length} accounts. Use --replace only after creating a backup.`
      );
    }

    if (!verifyOnly) {
      await database.replaceAccounts(sourceAccounts);
    }
    const imported = await database.loadAccounts();
    if (imported.length !== sourceAccounts.length) {
      throw new Error(`Import verification failed: expected ${sourceAccounts.length}, loaded ${imported.length}.`);
    }

    const importedById = new Map(imported.map(account => [account.id, account]));
    const criticalFields = [
      "minecraftName", "minecraftUuid", "faction", "role", "status",
      "tokenHash", "tokenPrefix", "webPasswordHash", "webPasswordSalt", "discordId"
    ];
    for (const sourceAccount of sourceAccounts) {
      const importedAccount = importedById.get(sourceAccount.id);
      if (!importedAccount) {
        throw new Error(`Import verification failed: account ${sourceAccount.id} is missing.`);
      }
      for (const field of criticalFields) {
        if (String(sourceAccount[field] || "") !== String(importedAccount[field] || "")) {
          throw new Error(`Import verification failed for account ${sourceAccount.id}: ${field}.`);
        }
      }
      if ((sourceAccount.statsHistory || []).length !== (importedAccount.statsHistory || []).length) {
        throw new Error(`Import verification failed for account ${sourceAccount.id}: stats history length.`);
      }
    }

    const action = verifyOnly ? "Verified" : "Imported and verified";
    console.log(`${action} ${imported.length} accounts from ${sourceFile}.`);
  } finally {
    await database.close();
  }
}

main().catch(error => {
  console.error("PostgreSQL import failed:", error.message);
  process.exit(1);
});
