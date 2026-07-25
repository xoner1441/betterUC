package com.betteruc.parser;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FactionStatsParser {
    private static final Pattern CHAT_TIMESTAMP_PATTERN = Pattern.compile("^\\s*\\d{1,2}:\\d{2}:\\d{2}\\s+");
    private static final Pattern TEXT_FORMATTING_PATTERN = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");
    private static final Pattern FACTION_STATS_LINE_PATTERN = Pattern.compile(
            "^\\s*[-\\u2010-\\u2015\\u2212]?\\s*Fraktion\\s*:\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<KnownFaction> KNOWN_FACTIONS = List.of(
            new KnownFaction("zivilist", "Zivilist"),
            new KnownFaction("polizei", "Polizei"),
            new KnownFaction("fbi", "FBI"),
            new KnownFaction("medic", "Rettungsdienst"),
            new KnownFaction("lcn", "La Cosa Nostra"),
            new KnownFaction("ballas", "Westside Ballas"),
            new KnownFaction("kartell", "Calderon Kartell"),
            new KnownFaction("kerzakov", "Kerzakov Familie"),
            new KnownFaction("yakuza", "Yakuza"),
            new KnownFaction("soeldner", "S\u00F6ldner"),
            new KnownFaction("news", "News"),
            new KnownFaction("ordo", "Ordo Absolutus")
    );

    private FactionStatsParser() {
    }

    public static String displayFromStatsLine(String raw) {
        String cleaned = cleanStatsLine(raw);
        Matcher matcher = FACTION_STATS_LINE_PATTERN.matcher(cleaned);
        if (!matcher.matches()) return "";

        String display = matcher.group(1).trim();
        return queryFromStatsValue(display).isEmpty() ? "" : display;
    }

    public static String queryFromStatsValue(String raw) {
        String folded = fold(raw);
        if (folded.isEmpty()) return "";

        String bestQuery = "";
        int bestLength = 0;
        for (KnownFaction faction : KNOWN_FACTIONS) {
            String label = fold(faction.label());
            if (folded.equals(label)
                    || folded.startsWith(label + " ")
                    || folded.contains(" " + label + " ")
                    || folded.contains(" " + label)
                    || folded.contains(label + " ")) {
                if (label.length() > bestLength) {
                    bestQuery = faction.query();
                    bestLength = label.length();
                }
            }
        }

        return bestQuery;
    }

    public static String normalizeQuery(String raw) {
        String folded = fold(raw);
        if (folded.isEmpty()) return "";

        if (folded.equals("calderon kartell") || folded.equals("kartell")) return "kartell";
        if (folded.equals("zivilist") || folded.equals("zivi") || folded.equals("ziv")) return "zivilist";
        if (folded.equals("rettungsdienst") || folded.equals("retungsdienst") || folded.equals("medic")) return "medic";
        if (folded.equals("la cosa nostra") || folded.equals("lcn")) return "lcn";
        if (folded.equals("westside ballas") || folded.equals("ballas")) return "ballas";
        if (folded.equals("soldner") || folded.equals("soeldner")) return "soeldner";
        if (folded.equals("ordo absolutus") || folded.equals("ordo")) return "ordo";
        if (folded.equals("kf") || folded.equals("k f")
                || folded.equals("kerzakov") || folded.equals("kerzakov familie")
                || folded.equals("kerzakov family")) return "kerzakov";
        if (folded.equals("f b i")) return "fbi";
        return folded;
    }

    private static String cleanStatsLine(String raw) {
        if (raw == null) return "";
        String cleaned = TEXT_FORMATTING_PATTERN.matcher(raw).replaceAll("");
        cleaned = CHAT_TIMESTAMP_PATTERN.matcher(cleaned).replaceFirst("");
        cleaned = cleaned.replaceFirst("^\\s*(?:\u00C2?\u00BB|>)+\\s*", "");
        return cleaned.trim();
    }

    private static String fold(String raw) {
        if (raw == null) return "";
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record KnownFaction(String query, String label) {
    }
}
