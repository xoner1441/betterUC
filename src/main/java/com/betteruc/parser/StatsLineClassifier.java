package com.betteruc.parser;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.regex.Pattern;

public final class StatsLineClassifier {

    private static final Pattern HEADER_PATTERN = Pattern.compile("statistiken", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIMED_LINE_PATTERN = Pattern.compile("^\\d{1,2}:\\d{2}:\\d{2}\\s+-\\s+.*");
    private static final Pattern KEYWORD_PATTERN = Pattern.compile(
            "\\b(status|level|inventar|wanted\\s+punkte|geld|schwarzgeld|verwarnungen|zeit\\s+seit\\s+payday|experience|fraktion|haus|immobilien|beruf|votepoints|treuebonus|spielzeit|K/D)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HOVER_KEYWORD_PATTERN = Pattern.compile(
            "\\b(selfstorage|lagerhaus|buero|b\\u00FCro|kills|tode|k\\s*[/\\\\|]\\s*d|immobilien|details)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern KD_LINE_PATTERN = Pattern.compile(
            "^\\s*-\\sK/D:\\s[\\d.]+$",
            Pattern.CASE_INSENSITIVE
    );

    private StatsLineClassifier() {
    }

    public static boolean isHeader(String raw) {
        return raw != null && HEADER_PATTERN.matcher(raw).find();
    }

    public static boolean isDetailLine(String raw) {
        String trimmed = normalize(raw);
        if (trimmed.isEmpty()) return false;
        if (isHeader(trimmed)) return true;
        if (KD_LINE_PATTERN.matcher(trimmed).matches()) return true;
        if (startsWithListDash(trimmed) || trimmed.startsWith("=")) return true;
        if (KEYWORD_PATTERN.matcher(trimmed).find() && trimmed.contains(":")) return true;
        return TIMED_LINE_PATTERN.matcher(trimmed).matches();
    }

    public static boolean isImplicitDetailLine(String raw) {
        String trimmed = normalize(raw);
        if (trimmed.isEmpty()) return false;

        boolean timed = TIMED_LINE_PATTERN.matcher(trimmed).matches();
        boolean dashPrefixed = startsWithListDash(trimmed);
        boolean hasKeyword = KEYWORD_PATTERN.matcher(trimmed).find();

        if (timed && hasKeyword) return true;
        return dashPrefixed && hasKeyword;
    }

    public static boolean shouldForceHideLine(
            String raw,
            long now,
            long afkExitTailUntilMs,
            long mainWindowUntilMs,
            long dashWindowUntilMs
    ) {
        if (raw == null || raw.isBlank()) return false;
        String trimmed = normalize(raw.trim());

        if (now <= afkExitTailUntilMs && isAfkExitTailLine(trimmed)) {
            return true;
        }

        boolean inMainWindow = now <= mainWindowUntilMs;
        boolean inDashWindow = now <= dashWindowUntilMs;
        if (!inMainWindow && !inDashWindow) return false;

        if (trimmed.isEmpty()) return false;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("du bist nun nicht mehr im afk-modus")) return false;
        if (lower.contains("deine payday-zeit l")) return false;

        if (inMainWindow) {
            if (isHeader(trimmed)) return true;
            if (isDetailLine(trimmed)) return true;
            if (isImplicitDetailLine(trimmed)) return true;
        }

        return inDashWindow && isDashStatTailLine(trimmed);
    }

    public static boolean containsHoverSignal(Text text) {
        if (text == null) return false;

        String visible = normalize(text.getString());
        if (HOVER_KEYWORD_PATTERN.matcher(visible).find()) {
            return true;
        }

        if (text.getStyle() != null) {
            HoverEvent hover = text.getStyle().getHoverEvent();
            if (hover instanceof HoverEvent.ShowText showText) {
                Text hoverText = showText.value();
                String hoverRaw = hoverText == null ? "" : normalize(hoverText.getString());
                if (HOVER_KEYWORD_PATTERN.matcher(hoverRaw).find()) {
                    return true;
                }
            }
        }

        for (Text sibling : text.getSiblings()) {
            if (containsHoverSignal(sibling)) return true;
        }
        return false;
    }

    private static boolean startsWithListDash(String trimmed) {
        return trimmed.startsWith("-")
                || trimmed.startsWith("\u2013")
                || trimmed.startsWith("\u2014")
                || trimmed.startsWith("\u2010")
                || trimmed.startsWith("\u2011")
                || trimmed.startsWith("\u2012")
                || trimmed.startsWith("\u2015")
                || trimmed.startsWith("\u2212")
                || trimmed.startsWith("\uFE58")
                || trimmed.startsWith("\uFE63")
                || trimmed.startsWith("\uFF0D");
    }

    private static boolean isDashStatTailLine(String normalized) {
        if (normalized == null || normalized.isEmpty()) return false;

        String work = stripTimestampPrefix(normalized);
        if (!work.startsWith("-")) return false;
        if (KD_LINE_PATTERN.matcher(work).matches()) return true;

        return work.matches("^-\\s*[A-Za-z\\u00C4\\u00D6\\u00DC\\u00E4\\u00F6\\u00FC\\u00DF/._| -]{1,40}\\s*:?\\s*[0-9].*$");
    }

    private static boolean isAfkExitTailLine(String normalized) {
        if (normalized == null || normalized.isEmpty()) return false;

        String work = stripTimestampPrefix(normalized);
        String lower = work.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("-")) return false;

        if (lower.contains("immobilien")) return true;
        return lower.matches("^-\\s*k\\s*[/\\\\|._-]?\\s*d(\\s*:?\\s*.*)?$");
    }

    private static String stripTimestampPrefix(String normalized) {
        String work = normalized;
        if (work.matches("^\\d{1,2}:\\d{2}:\\d{2}\\s+.*$")) {
            work = work.replaceFirst("^\\d{1,2}:\\d{2}:\\d{2}\\s+", "");
        }
        return work;
    }

    private static String normalize(String input) {
        if (input == null || input.isEmpty()) return "";
        return input
                .replace('\u2044', '/')
                .replace('\u2215', '/')
                .replace('\\', '/')
                .replace('|', '/')
                .replace('\u2010', '-')
                .replace('\u2011', '-')
                .replace('\u2012', '-')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2015', '-')
                .replace('\u2212', '-')
                .replace('\uFE58', '-')
                .replace('\uFE63', '-')
                .replace('\uFF0D', '-');
    }
}
