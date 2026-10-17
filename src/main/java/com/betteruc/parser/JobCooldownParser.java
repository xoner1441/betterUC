package com.betteruc.parser;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the cooldown lore used by the server's Job-Center. */
public final class JobCooldownParser {
    private static final Pattern DURATION_PART_PATTERN = Pattern.compile(
            "(?i)(\\d+)\\s*(tag|tage|tagen|stunde|stunden|std|minute|minuten|min|sekunde|sekunden|sek)\\b"
    );

    private JobCooldownParser() {
    }

    /** @return remaining seconds, {@code 0} when ready, or {@code null} for unrelated lore. */
    public static Long parseSeconds(String raw) {
        String line = normalize(raw);
        if (line.equals("keine wartezeit") || line.equals("keine wartezeit.")
                || line.equals("bereit") || line.equals("bereit.")) return 0L;
        if (!line.contains("wartezeit") && !line.contains("cooldown")
                && !line.startsWith("noch ") && !line.contains("verfugbar in")) return null;

        Matcher matcher = DURATION_PART_PATTERN.matcher(line);
        long total = 0L;
        while (matcher.find()) {
            long value = number(matcher.group(1));
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            long multiplier = unit.startsWith("tag") ? 86_400L
                    : unit.startsWith("st") ? 3_600L
                    : unit.startsWith("min") ? 60L : 1L;
            total = saturatingAdd(total, saturatingMultiply(value, multiplier));
        }
        return total > 0L ? total : null;
    }

    private static long number(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
