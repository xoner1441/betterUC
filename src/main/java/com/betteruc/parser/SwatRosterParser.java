package com.betteruc.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SwatRosterParser {
    private static final Pattern CHAT_TIMESTAMP_PATTERN = Pattern.compile("^\\s*\\d{1,2}:\\d{2}:\\d{2}\\s+");
    private static final Pattern TEXT_FORMATTING_PATTERN = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");
    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "Mitglieder\\s+von\\s+SWAT\\s*\\((\\d+)\\s*/\\s*(\\d+)\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ROW_PATTERN = Pattern.compile("^\\s*[-\\u2010-\\u2015]?\\s*(\\d+)\\s*\\|\\s*(.+?)\\s*$");
    private static final Pattern MEMBER_PATTERN = Pattern.compile(
            "^([A-Za-z0-9_]{1,16})(?:\\s*\\[([LS])])?$",
            Pattern.CASE_INSENSITIVE
    );

    private SwatRosterParser() {
    }

    public static Header parseHeader(String raw) {
        Matcher matcher = HEADER_PATTERN.matcher(clean(raw));
        if (!matcher.find()) return null;
        try {
            int memberCount = Math.max(0, Integer.parseInt(matcher.group(1)));
            int slotLimit = Math.max(memberCount, Integer.parseInt(matcher.group(2)));
            return new Header(memberCount, slotLimit);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static List<Member> parseMemberRow(String raw) {
        Matcher row = ROW_PATTERN.matcher(clean(raw));
        if (!row.matches()) return List.of();

        final int factionRank;
        try {
            factionRank = Math.max(0, Integer.parseInt(row.group(1)));
        } catch (NumberFormatException ignored) {
            return List.of();
        }

        List<Member> members = new ArrayList<>();
        for (String token : row.group(2).split("\\s*,\\s*")) {
            Matcher member = MEMBER_PATTERN.matcher(token.trim());
            if (!member.matches()) continue;
            String badge = member.group(2) == null ? "" : member.group(2).toUpperCase(Locale.ROOT);
            String role = switch (badge) {
                case "L" -> "leader";
                case "S" -> "supervisor";
                default -> "member";
            };
            members.add(new Member(member.group(1), factionRank, role));
        }
        return List.copyOf(members);
    }

    public static String clean(String raw) {
        if (raw == null) return "";
        String cleaned = TEXT_FORMATTING_PATTERN.matcher(raw).replaceAll("");
        cleaned = CHAT_TIMESTAMP_PATTERN.matcher(cleaned).replaceFirst("");
        cleaned = cleaned.replaceFirst("^\\s*[»>]+\\s*", "");
        return cleaned.trim();
    }

    public record Header(int memberCount, int slotLimit) {
    }

    public record Member(String username, int factionRank, String role) {
    }
}
