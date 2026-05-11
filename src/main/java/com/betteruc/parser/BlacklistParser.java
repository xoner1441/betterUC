package com.betteruc.parser;

import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BlacklistParser {

    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "blacklist\\s+.+\\s*\\(\\d+(?:/\\d+)?\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ENTRY_PATTERN = Pattern.compile(
            "(?:^|[\\u00BB>])\\s*(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]{3,16})\\s*\\|(.+)"
    );
    private static final Pattern DECORATION_PATTERN = Pattern.compile("^[=\\-\\s:>\\u00BB]+$");
    private static final Pattern NO_RESULTS_PATTERN = Pattern.compile(
            "keine\\s+.*blacklist|blacklist\\s+ist\\s+leer",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ADD_PATTERN = Pattern.compile(
            "Blacklist[\\]:\\s]+([A-Za-z0-9_]{3,16})\\s+wurde.*auf die Blacklist(?:\\s+gesetzt)?"
    );
    private static final Pattern REMOVE_PATTERN = Pattern.compile(
            "Blacklist[\\]:\\s]+([A-Za-z0-9_]{3,16})\\s+wurde.*von der Blacklist"
    );
    private static final Pattern KILLS_PATTERN = Pattern.compile("(\\d+)\\s*Kills");
    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d+)\\$");
    private static final Pattern VOGELFREI_TOKEN_PATTERN = Pattern.compile("(?i)\\b\\(?vogelfrei\\)?\\b");

    private BlacklistParser() {
    }

    public static boolean isHeader(String raw) {
        return raw != null && HEADER_PATTERN.matcher(raw).find();
    }

    public static boolean isDecoration(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        return DECORATION_PATTERN.matcher(trimmed).matches() || isHeader(trimmed);
    }

    public static boolean isNoResults(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        return NO_RESULTS_PATTERN.matcher(trimmed).find();
    }

    public static String parseRealtimeAdd(String raw) {
        Matcher matcher = ADD_PATTERN.matcher(raw == null ? "" : raw);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static String parseRealtimeRemove(String raw) {
        Matcher matcher = REMOVE_PATTERN.matcher(raw == null ? "" : raw);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static Entry parseEntry(String raw) {
        Matcher matcher = ENTRY_PATTERN.matcher(raw == null ? "" : raw);
        if (!matcher.find()) return null;

        String name = matcher.group(1);
        String rest = matcher.group(2);
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) return null;

        boolean hasVogelfrei = rest != null && VOGELFREI_TOKEN_PATTERN.matcher(rest).find();
        String normalizedReason = "";
        int kills = 0;
        int price = 0;

        if (rest != null) {
            String[] segments = rest.split("\\|");
            if (segments.length > 0) {
                String grundRaw = segments[0].trim()
                        .replaceAll("(?i)\\(?vogelfrei\\)?", "")
                        .trim();
                if (!grundRaw.isEmpty() || VOGELFREI_TOKEN_PATTERN.matcher(segments[0]).find()) {
                    normalizedReason = normalizeReasonSeparator(segments[0]);
                }
            }

            Matcher killsMatcher = KILLS_PATTERN.matcher(rest);
            if (killsMatcher.find()) kills = Integer.parseInt(killsMatcher.group(1));
            Matcher priceMatcher = PRICE_PATTERN.matcher(rest);
            if (priceMatcher.find()) price = Integer.parseInt(priceMatcher.group(1));
        }

        return new Entry(name, rest, normalizedReason, kills, price, hasVogelfrei);
    }

    public static String rewriteFormattedRest(String rest) {
        if (rest == null) return null;
        String formattedReason = normalizeReasonSeparator(rest.split("\\|", -1)[0]);
        return rest.replaceFirst("^[^|]*", Matcher.quoteReplacement(formattedReason));
    }

    public static String normalizeReasonSeparator(String reasonRaw) {
        if (reasonRaw == null) return "";
        String work = reasonRaw.trim();
        boolean hasVogelfrei = VOGELFREI_TOKEN_PATTERN.matcher(work).find();
        work = work.replaceAll("(?i)\\s*\\(?vogelfrei\\)?\\s*", "").trim();

        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        if (!work.isEmpty()) {
            for (String part : work.split("\\s*(?:,|\\+)\\s*")) {
                String cleaned = cleanReasonToken(part);
                if (!cleaned.isEmpty()) {
                    ordered.putIfAbsent(cleaned.toLowerCase(), cleaned);
                }
            }
        }

        String joined = String.join(" + ", ordered.values());
        if (hasVogelfrei) {
            joined = joined.isEmpty() ? "(Vogelfrei)" : joined + " (Vogelfrei)";
        }
        return joined;
    }

    private static String cleanReasonToken(String raw) {
        if (raw == null) return "";

        String cleaned = raw
                .replace("\\\"", "\"")
                .replace("\\", " ")
                .replaceAll("[\\u0022\\u201C\\u201D\\u201E\\u00AB\\u00BB']", " ")
                .trim();

        return cleaned.replaceAll("\\s+", " ");
    }

    public record Entry(String name, String rest, String normalizedReason, int kills, int price, boolean hasVogelfrei) {
    }
}
