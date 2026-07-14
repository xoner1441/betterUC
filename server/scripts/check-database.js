"use strict";

const { createDatabase } = require("../database");

async function main() {
  const database = createDatabase({ required: true });
  if (!database.enabled) {
    throw new Error("DATABASE_URL is not configured.");
  }
  try {
    await database.initialize();
    const accounts = await database.loadAccounts();
    const withStats = accounts.filter(account => account.stats).length;
    const withWebLogin = accounts.filter(account => account.webPasswordHash).length;
    console.log("PostgreSQL connection: OK");
    console.log(`Accounts: ${accounts.length}`);
    console.log(`Accounts with stats: ${withStats}`);
    console.log(`Accounts with web login: ${withWebLogin}`);
  } finally {
    await database.close();
  }
}

main().catch(error => {
  console.error("PostgreSQL check failed:", error.message);
  process.exit(1);
});

