package com.betteruc.hud;

import com.betteruc.BetterUCMod;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Local, rate-limited diagnostics for server money formats not yet understood by either HUD. */
public final class MoneyHudDiagnostics {
    private static final int MAX_ENTRIES_PER_GAME_SESSION = 50;
    private static final int MAX_LINE_LENGTH = 300;
    private static final long DUPLICATE_WINDOW_MS = 30_000L;
    private static final Pattern FORMATTING_PATTERN = Pattern.compile("\\u00A7.");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^\\s*\\d{1,2}:\\d{2}:\\d{2}\\s+");
    private static final Pattern MONEY_AMOUNT_PATTERN = Pattern.compile("[+-]?\\s*[0-9][0-9.,' ]*\\s*\\$");
    private static final Pattern MONEY_KEYWORD_PATTERN = Pattern.compile(
            "(?iu)(?:geld|bargeld|bank|konto|betrag|auszahlung|eingezahlt|einzahlen|"
                    + "abgehoben|überwiesen|ueberwiesen|gegeben|gehalt|einnahmen|nebenkosten|"
                    + "steuer|versicherung|taschengeld|verkauft|gekauft|reward|belohnung|casino)"
    );
    private static final Pattern FACTION_BANK_PREFIX_PATTERN = Pattern.compile("(?i)^\\[\\s*F-?Bank\\s*]");
    private static final Map<String, Long> RECENT_FINGERPRINTS = new LinkedHashMap<>();

    private static int recordedEntries;
    private static boolean limitNoticeWritten;

    private MoneyHudDiagnostics() {
    }

    public static void recordIfSuspicious(
            String raw,
            boolean bankRecognized,
            boolean cashRecognized,
            boolean playerOrigin
    ) {
        if (bankRecognized || cashRecognized || raw == null || raw.isBlank()) return;

        String withoutFormatting = FORMATTING_PATTERN.matcher(raw).replaceAll("");
        for (String rawLine : withoutFormatting.split("\\R+")) {
            String line = stripChatPrefix(rawLine);
            if (!isSuspiciousCandidate(line, playerOrigin)) continue;
            record(line, playerOrigin);
        }
    }

    static boolean isSuspiciousCandidate(String raw, boolean playerOrigin) {
        if (raw == null || raw.isBlank()) return false;
        String line = stripChatPrefix(FORMATTING_PATTERN.matcher(raw).replaceAll(""));
        if (!MONEY_AMOUNT_PATTERN.matcher(line).find()) return false;
        if (!MONEY_KEYWORD_PATTERN.matcher(line).find()) return false;

        // Player-origin packets also contain ordinary public chat. Only the known plugin-style
        // F-Bank prefix is diagnostic-worthy there, so players cannot fill the local log.
        return !playerOrigin || FACTION_BANK_PREFIX_PATTERN.matcher(line).find();
    }

    private static synchronized void record(String rawLine, boolean playerOrigin) {
        if (recordedEntries >= MAX_ENTRIES_PER_GAME_SESSION) {
            if (!limitNoticeWritten) {
                limitNoticeWritten = true;
                BetterUCMod.LOGGER.info(
                        "[Money HUD Diagnose] Sitzungslimit von {} Einträgen erreicht",
                        MAX_ENTRIES_PER_GAME_SESSION
                );
            }
            return;
        }

        long now = System.currentTimeMillis();
        String fingerprint = fingerprint(rawLine, playerOrigin);
        Long previous = RECENT_FINGERPRINTS.get(fingerprint);
        if (previous != null && now - previous >= 0L && now - previous <= DUPLICATE_WINDOW_MS) return;

        RECENT_FINGERPRINTS.put(fingerprint, now);
        if (RECENT_FINGERPRINTS.size() > MAX_ENTRIES_PER_GAME_SESSION) {
            String oldest = RECENT_FINGERPRINTS.keySet().iterator().next();
            RECENT_FINGERPRINTS.remove(oldest);
        }
        recordedEntries++;
        BetterUCMod.LOGGER.info(
                "[Money HUD Diagnose] Nicht erkannt ({}): {}",
                playerOrigin ? "Player-Paket" : "Server-Paket",
                sanitize(rawLine)
        );
    }

    private static String stripChatPrefix(String raw) {
        String line = TIMESTAMP_PATTERN.matcher(raw == null ? "" : raw).replaceFirst("");
        return line.replaceFirst("^\\s*[»>]+\\s*", "").trim();
    }

    private static String fingerprint(String raw, boolean playerOrigin) {
        return (playerOrigin ? "p:" : "s:") + raw.toLowerCase(Locale.ROOT)
                .replaceAll("[0-9][0-9.,' ]*", "#")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String sanitize(String raw) {
        String singleLine = raw.replace('\r', ' ').replace('\n', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\\t]]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (singleLine.length() <= MAX_LINE_LENGTH) return singleLine;
        return singleLine.substring(0, MAX_LINE_LENGTH) + "…";
    }
}
