package com.betteruc.parser;

import com.betteruc.PlayerNameUtil;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MemberInfoParser {

    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "mitglieder\\s+(?:von|vom|des)?\\s*(.+?)\\s*\\((\\d+)(?:/\\d+)?\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ENTRY_PATTERN = Pattern.compile("^-?\\s*\\d+\\s*\\|\\s*(.+)$");
    private static final Pattern NAME_TOKEN_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16})(?![A-Za-z0-9_])");
    private static final Pattern LEGACY_FORMATTING_PATTERN = Pattern.compile("(?i)\u00A7[0-9A-FK-OR]");
    private static final Pattern DECORATION_PATTERN = Pattern.compile("^[=\\-\\s:>\\u00BB]+$");
    private static final Pattern NO_RESULTS_PATTERN = Pattern.compile("keine\\s+mitglieder", Pattern.CASE_INSENSITIVE);
    private static final Set<String> NAME_STOPWORDS = new HashSet<>(Arrays.asList(
            "online", "offline", "afk", "rang", "rank", "mitglied", "mitglieder",
            "leader", "coleader", "co", "status", "seite", "page", "ping", "ms",
            "kills", "kill", "k", "tode", "deaths", "death", "kd", "kdr",
            "fraktion", "faction", "von", "vom", "des", "der", "die", "das"
    ));

    private MemberInfoParser() {
    }

    public static Header parseHeader(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Matcher matcher = HEADER_PATTERN.matcher(raw);
        if (!matcher.find()) return null;
        return new Header(matcher.group(1), parseExpectedEntries(matcher.group(2)));
    }

    public static ParsedLine parseLine(String raw, MinecraftClient client) {
        String trimmed = raw == null ? "" : raw.trim();

        Matcher entryMatcher = ENTRY_PATTERN.matcher(trimmed);
        boolean hasStructuredEntry = entryMatcher.find();
        if (hasStructuredEntry || trimmed.contains("|")) {
            String payload = hasStructuredEntry ? entryMatcher.group(1) : trimmed;
            return ParsedLine.names(collectNamesFromPayload(payload, client));
        }

        boolean looksLikeInlineMemberList = trimmed.contains(",")
                || trimmed.startsWith(">")
                || trimmed.startsWith("\u00BB");
        if (!trimmed.isBlank() && looksLikeInlineMemberList) {
            List<String> names = collectNamesFromPayload(trimmed, client);
            if (!names.isEmpty()) {
                return ParsedLine.names(names);
            }
        }

        if (trimmed.isBlank() || DECORATION_PATTERN.matcher(trimmed).matches()) {
            return ParsedLine.decoration();
        }

        if (NO_RESULTS_PATTERN.matcher(trimmed).find()) {
            return ParsedLine.noResults();
        }

        return ParsedLine.other();
    }

    public static boolean shouldFinishByExpectedMemberCount(int expectedMemberEntries, int currentMemberCount) {
        return expectedMemberEntries >= 0 && currentMemberCount >= expectedMemberEntries;
    }

    private static int parseExpectedEntries(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        try {
            return Math.max(Integer.parseInt(raw.trim()), 0);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static List<String> collectNamesFromPayload(String payload, MinecraftClient client) {
        if (payload == null || payload.isBlank()) return new ArrayList<>();
        String sanitized = LEGACY_FORMATTING_PATTERN.matcher(payload).replaceAll("");
        if (sanitized.isBlank()) return new ArrayList<>();
        return extractLikelyMemberNames(sanitized, PlayerNameUtil.getOnlinePlayerNamesLowercase(client));
    }

    private static List<String> extractLikelyMemberNames(String payload, Set<String> onlineLowercase) {
        List<String> result = new ArrayList<>();
        if (payload == null || payload.isBlank()) return result;

        if (payload.contains("|")) {
            String[] parts = payload.split("\\|");
            if (parts.length > 0) {
                List<String> primary = extractNamesFromSegment(parts[0]);
                if (!primary.isEmpty()) {
                    return prioritizeOnlineNames(primary, onlineLowercase);
                }
            }

            for (int i = 1; i < parts.length; i++) {
                List<String> fallback = extractNamesFromSegment(parts[i]);
                for (String candidate : fallback) {
                    if (result.stream().noneMatch(s -> s.equalsIgnoreCase(candidate))) {
                        result.add(candidate);
                    }
                }
            }
            return prioritizeOnlineNames(result, onlineLowercase);
        }

        return prioritizeOnlineNames(extractNamesFromSegment(payload), onlineLowercase);
    }

    private static List<String> extractNamesFromSegment(String segment) {
        List<String> names = new ArrayList<>();
        if (segment == null || segment.isBlank()) return names;

        String cleaned = segment
                .replaceAll("\\[[^\\]]*\\]", " ")
                .replaceAll("[(){}<>]", " ")
                .replace('\u00A0', ' ')
                .trim();

        Matcher matcher = NAME_TOKEN_PATTERN.matcher(cleaned);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (!isLikelyMemberName(token)) continue;
            if (names.stream().noneMatch(s -> s.equalsIgnoreCase(token))) {
                names.add(token);
            }
        }
        return names;
    }

    private static boolean isLikelyMemberName(String token) {
        if (token == null || token.isBlank()) return false;
        if (!token.matches("[A-Za-z0-9_]{3,16}")) return false;
        return !NAME_STOPWORDS.contains(token.toLowerCase(Locale.ROOT));
    }

    private static List<String> prioritizeOnlineNames(List<String> candidates, Set<String> onlineLowercase) {
        if (candidates == null || candidates.isEmpty()) return new ArrayList<>();
        if (onlineLowercase == null || onlineLowercase.isEmpty()) return candidates;

        List<String> onlineMatches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            if (onlineLowercase.contains(candidate.toLowerCase(Locale.ROOT))) {
                onlineMatches.add(candidate);
            }
        }
        return onlineMatches.isEmpty() ? candidates : onlineMatches;
    }

    public record Header(String factionName, int expectedEntries) {
    }

    public record ParsedLine(Type type, List<String> names) {
        public static ParsedLine names(List<String> names) {
            return new ParsedLine(Type.NAMES, names == null ? new ArrayList<>() : names);
        }

        public static ParsedLine decoration() {
            return new ParsedLine(Type.DECORATION, new ArrayList<>());
        }

        public static ParsedLine noResults() {
            return new ParsedLine(Type.NO_RESULTS, new ArrayList<>());
        }

        public static ParsedLine other() {
            return new ParsedLine(Type.OTHER, new ArrayList<>());
        }
    }

    public enum Type {
        NAMES,
        DECORATION,
        NO_RESULTS,
        OTHER
    }
}
