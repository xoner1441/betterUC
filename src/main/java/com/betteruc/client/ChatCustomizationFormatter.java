package com.betteruc.client;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatCustomizationFormatter {
    private static final long PENDING_TTL_MS = 4000L;

    private static final Pattern WANTED_START_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:»\\s*)?HQ:\\s*Gesuchter:\\s*([^\\.]+)\\.\\s*Grund:\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WANTED_LEVEL_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:»\\s*)?HQ:\\s*([^\\s]+)'s\\s+momentanes\\s+WantedLevel:\\s*(\\d+)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern KILLED_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:»\\s*)?HQ:\\s*(.+?)\\s+wurde\\s+von\\s+(.+?)\\s+getötet\\.\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JAILED_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:»\\s*)?HQ:\\s*(.+?)\\s+wurde\\s+von\\s+(.+?)\\s+eingesperrt\\.\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SEARCH_REASON_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:»\\s*)?HQ\\s+Fahndungsgrund:\\s*(.+?)\\s*\\|\\s*Fahndungszeit:\\s*(\\d+)\\s*Minuten\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CHANGED_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:»\\s*)?HQ:\\s*(.+?)\\s+hat\\s+(.+?)\\s+WantedPunkte\\s+verändert!\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NEW_REASON_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:»\\s*)?HQ\\s+Neuer\\s+Grund:\\s*(.+?)\\s*\\[(\\d+)\\s*»\\s*(\\d+)\\s*WantedPunkte\\]\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DELETED_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:»\\s*)?HQ:\\s*(.+?)\\s+hat\\s+(.+?)'s\\s+Akten\\s+gelöscht,\\s*over\\.\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PAY_SENT_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?Du\\s+hast\\s+([^\\s]+)\\s+([+-]?[0-9.]+)\\$\\s+gegeben!\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PAY_RECEIVED_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?([^\\s]+)\\s+hat\\s+dir\\s+([+-]?[0-9.]+)\\$\\s+gegeben!\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SUPPORT_REQUEST_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?(.+?)!\\s+(.+?)\\s+([^\\s]+)\\s+ben\\u00F6tigt\\s+Unterst\\u00FCtzung\\s+in\\s+der\\s+N\\u00E4he\\s+von\\s+(.+?)!\\s*\\((\\d+)\\s*Meter\\s+entfernt\\)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SUPPORT_RESPONSE_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?(.+?)\\s+([^\\s]+)\\s+kommt\\s+zum\\s+Verst\\u00E4rkungsruf\\s+von\\s+([^\\s]+)!\\s*\\((\\d+)\\s*Meter\\s+entfernt\\)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private static Pending pending;

    private ChatCustomizationFormatter() {
    }

    public static Result transform(String raw) {
        return transform(raw, true, true);
    }

    public static Result transform(String raw, boolean wpsHqEnabled, boolean reinfEnabled) {
        String clean = normalize(raw);
        if (clean.isEmpty()) return null;

        clearExpiredPending();
        if (!wpsHqEnabled) {
            pending = null;
        }

        if (wpsHqEnabled) {
        Matcher paySent = PAY_SENT_PATTERN.matcher(clean);
        if (paySent.matches()) {
            return Result.replace(List.of(
                    payHeadline("Du", paySent.group(1)),
                    amountDetail("-" + cleanAmount(paySent.group(2)) + "$", Formatting.RED)
            ));
        }

        Matcher payReceived = PAY_RECEIVED_PATTERN.matcher(clean);
        if (payReceived.matches()) {
            return Result.replace(List.of(
                    payHeadline(payReceived.group(1), "Du"),
                    amountDetail("+" + cleanAmount(payReceived.group(2)) + "$", Formatting.GREEN)
            ));
        }
        }

        if (reinfEnabled) {
        Matcher supportRequest = SUPPORT_REQUEST_PATTERN.matcher(clean);
        if (supportRequest.matches()) {
            String action = supportActionLabel(supportRequest.group(1));
            String source = supportRequest.group(2);
            String player = supportRequest.group(3);
            String location = supportRequest.group(4);
            String meters = supportRequest.group(5);
            return Result.replace(List.of(
                    supportHeadline(action, player),
                    supportDetail(isKnownFaction(source) ? source : "", location, meters + "m")
            ));
        }

        Matcher supportResponse = SUPPORT_RESPONSE_PATTERN.matcher(clean);
        if (supportResponse.matches()) {
            String source = supportResponse.group(1);
            String actor = supportResponse.group(2);
            String target = supportResponse.group(3);
            String meters = supportResponse.group(4);
            return Result.replace(List.of(
                    supportHeadline("UNTERWEGS", actor),
                    supportDetail(isKnownFaction(source) ? source : "", "zu " + target, meters + "m")
            ));
        }
        }

        if (!wpsHqEnabled) return null;

        Matcher wantedStart = WANTED_START_PATTERN.matcher(clean);
        if (wantedStart.matches()) {
            pending = new Pending(PendingType.WANTED, "", wantedStart.group(1), wantedStart.group(2), System.currentTimeMillis());
            return Result.suppress();
        }

        Matcher wantedLevel = WANTED_LEVEL_PATTERN.matcher(clean);
        if (wantedLevel.matches() && matchesPending(PendingType.WANTED, wantedLevel.group(1))) {
            Pending current = pending;
            pending = null;
            return Result.replace(List.of(
                    headline("GESUCHT", current.target()),
                    detail(current.reason(), wantedLevel.group(2) + " Wanteds")
            ));
        }

        Matcher killed = KILLED_PATTERN.matcher(clean);
        if (killed.matches()) {
            pending = new Pending(PendingType.KILLED, killed.group(2), killed.group(1), "", System.currentTimeMillis());
            return Result.suppress();
        }

        Matcher jailed = JAILED_PATTERN.matcher(clean);
        if (jailed.matches()) {
            pending = new Pending(PendingType.JAILED, jailed.group(2), jailed.group(1), "", System.currentTimeMillis());
            return Result.suppress();
        }

        Matcher searchReason = SEARCH_REASON_PATTERN.matcher(clean);
        if (searchReason.matches() && (matchesPending(PendingType.KILLED) || matchesPending(PendingType.JAILED))) {
            Pending current = pending;
            pending = null;
            String action = current.type() == PendingType.KILLED ? "GETÖTET" : "INHAFTIERT";
            return Result.replace(List.of(
                    headline(action, current.actor(), current.target()),
                    detail(searchReason.group(1), searchReason.group(2) + " Minuten")
            ));
        }

        Matcher changed = CHANGED_PATTERN.matcher(clean);
        if (changed.matches()) {
            pending = new Pending(PendingType.CHANGED, changed.group(1), changed.group(2), "", System.currentTimeMillis());
            return Result.suppress();
        }

        Matcher newReason = NEW_REASON_PATTERN.matcher(clean);
        if (newReason.matches() && matchesPending(PendingType.CHANGED)) {
            Pending current = pending;
            pending = null;
            return Result.replace(List.of(
                    headline("VERÄNDERT", current.actor(), current.target()),
                    detail(newReason.group(1), newReason.group(2) + " » " + newReason.group(3) + " Wanteds")
            ));
        }

        Matcher deleted = DELETED_PATTERN.matcher(clean);
        if (deleted.matches()) {
            return Result.replace(List.of(
                    headline("GELÖSCHT", deleted.group(1), deleted.group(2)),
                    detail("Akten gelöscht", "")
            ));
        }

        return null;
    }

    public static void clearPending() {
        pending = null;
    }

    private static void clearExpiredPending() {
        if (pending != null && System.currentTimeMillis() - pending.createdAtMs() > PENDING_TTL_MS) {
            pending = null;
        }
    }

    private static boolean matchesPending(PendingType type) {
        return pending != null && pending.type() == type;
    }

    private static boolean matchesPending(PendingType type, String target) {
        return pending != null
                && pending.type() == type
                && key(pending.target()).equals(key(target));
    }

    private static Text headline(String action, String target) {
        return action(action)
                .append(separator(" ✦ "))
                .append(name(target));
    }

    private static Text headline(String action, String actor, String target) {
        return action(action)
                .append(separator(" ✦ "))
                .append(name(actor))
                .append(separator(" » "))
                .append(name(target));
    }

    private static Text detail(String reason, String suffix) {
        MutableText text = Text.literal("» ").formatted(Formatting.GRAY)
                .append(Text.literal(reason == null ? "" : reason.trim()).formatted(Formatting.BLUE));
        if (suffix != null && !suffix.isBlank()) {
            text.append(separator(" | "))
                    .append(Text.literal(suffix.trim()).formatted(Formatting.YELLOW));
        }
        return text;
    }

    private static Text amountDetail(String amount, Formatting color) {
        return Text.literal("\u00BB ").formatted(Formatting.GRAY)
                .append(Text.literal(amount == null ? "" : amount.trim()).formatted(color, Formatting.BOLD));
    }

    private static Text payHeadline(String actor, String target) {
        return action("PAY")
                .append(separator(" \u2726 "))
                .append(payName(actor))
                .append(separator(" \u00BB "))
                .append(payName(target));
    }

    private static Text supportHeadline(String action, String target) {
        return action(action)
                .append(separator(" \u2726 "))
                .append(supportName(target));
    }

    private static Text supportDetail(String source, String location, String suffix) {
        MutableText text = Text.literal("\u00BB ").formatted(Formatting.GRAY);
        boolean hasSource = source != null && !source.isBlank();
        if (hasSource) {
            text.append(Text.literal(source.trim()).formatted(Formatting.AQUA))
                    .append(separator(" | "));
        }
        text.append(Text.literal(location == null ? "" : location.trim()).formatted(Formatting.AQUA));
        if (suffix != null && !suffix.isBlank()) {
            text.append(separator(" | "))
                    .append(Text.literal(suffix.trim()).formatted(Formatting.YELLOW));
        }
        return text;
    }

    private static String supportActionLabel(String rawAction) {
        String value = rawAction == null ? "" : rawAction.trim();
        String normalized = key(value);
        if (normalized.equals("unterst\u00FCtzung ben\u00F6tigt")) return "REINF";
        if (normalized.equals("medic ben\u00F6tigt")) return "MEDIC";
        return value.isBlank() ? "REINF" : value.toUpperCase(Locale.ROOT);
    }

    private static boolean isKnownFaction(String source) {
        String normalized = key(source);
        return normalized.equals("polizei")
                || normalized.equals("fbi")
                || normalized.equals("rettungsdienst")
                || normalized.equals("la cosa nostra")
                || normalized.equals("westside ballas")
                || normalized.equals("calder\u00F3n kartell")
                || normalized.equals("calderon kartell")
                || normalized.equals("kerzakov familie")
                || normalized.equals("yakuza")
                || normalized.equals("s\u00F6ldner")
                || normalized.equals("soldner")
                || normalized.equals("news")
                || normalized.equals("ordo absolutus")
                || normalized.equals("zivilist");
    }

    private static MutableText action(String value) {
        return Text.literal(value).formatted(Formatting.RED, Formatting.BOLD);
    }

    private static MutableText name(String value) {
        return Text.literal(value == null ? "" : value.trim()).formatted(Formatting.BLUE);
    }

    private static MutableText supportName(String value) {
        return Text.literal(value == null ? "" : value.trim()).formatted(Formatting.AQUA);
    }

    private static MutableText payName(String value) {
        return Text.literal(value == null ? "" : value.trim()).formatted(Formatting.DARK_GREEN);
    }

    private static MutableText separator(String value) {
        return Text.literal(value).formatted(Formatting.DARK_GRAY);
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("§.", "").trim();
    }

    private static String cleanAmount(String amount) {
        if (amount == null) return "0";
        String cleaned = amount.trim();
        while (cleaned.startsWith("+") || cleaned.startsWith("-")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned.isBlank() ? "0" : cleaned;
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private enum PendingType {
        WANTED,
        KILLED,
        JAILED,
        CHANGED
    }

    private record Pending(PendingType type, String actor, String target, String reason, long createdAtMs) {
    }

    public static final class Result {
        private final boolean cancelOriginal;
        private final List<Text> replacementMessages;

        private Result(boolean cancelOriginal, List<Text> replacementMessages) {
            this.cancelOriginal = cancelOriginal;
            this.replacementMessages = replacementMessages == null ? List.of() : replacementMessages;
        }

        public static Result suppress() {
            return new Result(true, List.of());
        }

        public static Result replace(List<Text> replacementMessages) {
            return new Result(true, replacementMessages);
        }

        public boolean cancelOriginal() {
            return cancelOriginal;
        }

        public List<Text> replacementMessages() {
            return replacementMessages;
        }
    }
}
