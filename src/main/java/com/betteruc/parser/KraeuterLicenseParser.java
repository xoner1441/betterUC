package com.betteruc.parser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads only the player's Kräuter-Lizenz row from the server's /licenses output. */
public final class KraeuterLicenseParser {
    private static final Pattern ROW = Pattern.compile(
            "Kr(?:ä|ae)uter\\s*-\\s*Lizenz\\s*:\\s*(Nicht\\s+vorhanden|Vorhanden\\s+bis\\s+(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(\\d{2}:\\d{2}))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm", Locale.GERMAN)
            .withResolverStyle(ResolverStyle.STRICT);

    private KraeuterLicenseParser() { }

    public record Status(boolean present, LocalDateTime expiresAt) { }

    public static Status parse(String line) {
        if (line == null) return null;
        Matcher match = ROW.matcher(line);
        if (!match.find()) return null;
        if (match.group(2) == null) return new Status(false, null);
        try {
            return new Status(true, LocalDateTime.parse(match.group(2) + " " + match.group(3), DATE_TIME));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static String format(LocalDateTime dateTime) { return DATE_TIME.format(dateTime); }
}
