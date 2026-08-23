"use strict";

const { startTeamSpeakFactionSync } = require("../teamSpeakFactionSync");

const sync = startTeamSpeakFactionSync();
if (!sync.enabled) {
  process.exitCode = 1;
} else {
  const keepAlive = setInterval(() => {}, 60 * 60 * 1000);
  const stop = signal => {
    clearInterval(keepAlive);
    sync.stop();
    console.log(`TeamSpeak-Fraktionssync beendet (${signal}).`);
    process.exit(0);
  };
  process.once("SIGTERM", () => stop("SIGTERM"));
  process.once("SIGINT", () => stop("SIGINT"));
}
