package com.betteruc.parser;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the cooldown lore used by the server's Job-Center. */
public final class JobCooldownParser {
    private static final Pattern COOLDOWN_PATTERN = Pattern.compile(
            "(?i)^noch\\s+"
                    + "(?:(\\d+)\\s+(?:tag|tage|tagen)\\s+)?"
                    + "(?:(\\d+)\\s+(?:stunde|stunden)\\s+)?"
                    + "(?:(\\d+)\\s+(?:minute|minuten)\\s+)?"
                    + "(?:(\\d+)\\s+(?:sekunde|sekunden)\\s+)?"
                    + "wartezeit[.!]?$"
    );

    private JobCooldownParser() {
    }

    /** @return remaining seconds, {@code 0} when ready, or {@code null} for unrelated lore. */
    public static Long parseSeconds(String raw) {
        String line = normalize(raw);
        if (line.equals("keine wartezeit") || line.equals("keine wartezeit.")) return 0L;

        Matcher matcher = COOLDOWN_PATTERN.matcher(line);
        if (!matcher.matches()) return null;
        long days = number(matcher.group(1));
        long hours = number(matcher.group(2));
        long minutes = number(matcher.group(3));
        long seconds = number(matcher.group(4));
        long total = days * 86_400L + hours * 3_600L + minutes * 60L + seconds;
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
