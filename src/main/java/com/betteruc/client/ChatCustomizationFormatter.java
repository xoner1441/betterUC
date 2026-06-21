package com.betteruc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ChatCustomizationFormatter {
    private static final long PENDING_TTL_MS = 30000L;
    private static final Pattern PLAYER_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_]{2,16}");

    private static final Pattern WANTED_START_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:\\s*Gesuchter:.*?(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+).*?Grund.*?[:\\s]+(.+?)(?:\\s*\\[.*?\\])?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WANTED_LEVEL_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:\\s*(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+?)(?:['’]?s)\\s+momentanes\\s+WantedLevel.*?(\\d+)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern KILLED_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?(?:HQ:\\s*)?(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+)\\s+wurde\\s+von\\s+(.+?)\\s+get(?:ötet|oetet|otet)\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JAILED_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?(?:HQ:\\s*)?(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+)\\s+wurde\\s+von\\s+(.+?)\\s+eingesperrt\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WEAPON_SEIZED_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?(?:HQ:\\s*)?(?:[\\p{L} ]+\\s+)?(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+)\\s+hat\\s+(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+)\\s+die\\s+Waffen\\s+abgenommen\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DRUG_SEIZED_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?(?:HQ:\\s*)?(?:[\\p{L} ]+\\s+)?(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+)\\s+hat\\s+(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+)\\s+(?:die\\s+)?Drogen\\s+abgenommen\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SEARCH_REASON_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:?\\s*Fahndungsgrund:\\s*(.+?)\\s*[|¦]\\s*Fahndungszeit:\\s*(\\d+)\\s*(Minute(?:n)?|Stunde(?:n)?)\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CHANGED_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:\\s*(?:.*?\\s+)?(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+)\\s+hat\\s+(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+)['’]?s\\s+WantedPunkte\\s+verändert!\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NEW_REASON_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:?\\s*Neuer\\s+Grund:\\s*(.+?)\\s*(?:[|¦]\\s*)?\\[(\\d+)\\s*[»>]\\s*(\\d+)\\s*WantedPunkte\\](?:\\s*\\[.*?\\])?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DELETED_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:\\s*(?:.*?\\s+)?(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+)\\s+hat\\s+(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+)['’]?s\\s+Akten\\s+gel[öo]scht,\\s*over\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PAY_SENT_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:[^\\p{L}\\p{N}_\\[]+\\s*)?Du\\s+hast\\s+([^\\s]+)\\s+([+-]?[0-9.]+)\\$\\s+gegeben!\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PAY_RECEIVED_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:[^\\p{L}\\p{N}_\\[]+\\s*)?([^\\s]+)\\s+hat\\s+dir\\s+([+-]?[0-9.]+)\\$\\s+gegeben!\\s*$",
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
                    amountDetail("-" + cleanAmount(paySent.group(2)) + "$", ChatFormatting.RED)
            ));
        }

        Matcher payReceived = PAY_RECEIVED_PATTERN.matcher(clean);
        if (payReceived.matches()) {
            return Result.replace(List.of(
                    payHeadline(payReceived.group(1), "Du"),
                    amountDetail("+" + cleanAmount(payReceived.group(2)) + "$", ChatFormatting.GREEN)
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
            pending = new Pending(PendingType.WANTED, "", playerName(wantedStart.group(1)), wantedStart.group(2), System.currentTimeMillis());
            return Result.suppress();
        }

        Matcher wantedLevel = WANTED_LEVEL_PATTERN.matcher(clean);
        if (wantedLevel.matches() && matchesPending(PendingType.WANTED, playerName(wantedLevel.group(1)))) {
            Pending current = pending;
            pending = null;
            return Result.replace(messages(
                    headline("GESUCHT", current.target()),
                    reasonDetails(current.reason(), wantedLevel.group(2) + " Wanteds")
            ));
        }

        Matcher killed = KILLED_PATTERN.matcher(clean);
        if (killed.matches()) {
            pending = new Pending(PendingType.KILLED, playerName(killed.group(2)), playerName(killed.group(1)), "", System.currentTimeMillis());
            return Result.suppress();
        }

        Matcher jailed = JAILED_PATTERN.matcher(clean);
        if (jailed.matches()) {
            pending = new Pending(PendingType.JAILED, playerName(jailed.group(2)), playerName(jailed.group(1)), "", System.currentTimeMillis());
            return Result.suppress();
        }

        Matcher weaponSeized = WEAPON_SEIZED_PATTERN.matcher(clean);
        if (weaponSeized.matches()) {
            return Result.replace(List.of(
                    headline("WAFFEN ABNAHME", playerName(weaponSeized.group(1)), playerName(weaponSeized.group(2)))
            ));
        }

        Matcher drugSeized = DRUG_SEIZED_PATTERN.matcher(clean);
        if (drugSeized.matches()) {
            return Result.replace(List.of(
                    headline("DROGEN ABNAHME", playerName(drugSeized.group(1)), playerName(drugSeized.group(2)))
            ));
        }

        Matcher searchReason = SEARCH_REASON_PATTERN.matcher(clean);
        if (searchReason.matches() && isFahndungPending()) {
            Pending current = pending;
            pending = null;
            String action = fahndungAction(current.type());
            return Result.replace(messages(
                    headline(action, current.actor(), current.target()),
                    reasonDetails(searchReason.group(1), searchReason.group(2) + " " + searchReason.group(3))
            ));
        }

        Matcher changed = CHANGED_PATTERN.matcher(clean);
        if (changed.matches()) {
            pending = new Pending(PendingType.CHANGED, playerName(changed.group(1)), playerName(changed.group(2)), "", System.currentTimeMillis());
            return Result.suppress();
        }

        Matcher newReason = NEW_REASON_PATTERN.matcher(clean);
        if (newReason.matches() && matchesPending(PendingType.CHANGED)) {
            Pending current = pending;
            pending = null;
            return Result.replace(messages(
                    headline("VERÄNDERT", current.actor(), current.target()),
                    reasonDetails(newReason.group(1), newReason.group(2) + " » " + newReason.group(3) + " Wanteds")
            ));
        }

        Matcher deleted = DELETED_PATTERN.matcher(clean);
        if (deleted.matches()) {
            return Result.replace(List.of(
                    headline("GELÖSCHT", playerName(deleted.group(1)), playerName(deleted.group(2))),
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

    private static boolean isFahndungPending() {
        return pending != null
                && (pending.type() == PendingType.KILLED
                || pending.type() == PendingType.JAILED);
    }

    private static String fahndungAction(PendingType type) {
        return switch (type) {
            case KILLED -> "GETÖTET";
            case JAILED -> "INHAFTIERT";
            default -> "HQ";
        };
    }

    private static Component headline(String action, String target) {
        return action(action)
                .append(separator(" ◆ "))
                .append(name(target));
    }

    private static Component headline(String action, String actor, String target) {
        return action(action)
                .append(separator(" ◆ "))
                .append(name(actor))
                .append(separator(" » "))
                .append(name(target));
    }

    private static Component detail(String reason, String suffix) {
        MutableComponent text = Component.literal("» ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(reason == null ? "" : reason.trim()).withStyle(ChatFormatting.BLUE));
        if (suffix != null && !suffix.isBlank()) {
            text.append(separator(" | "))
                    .append(Component.literal(suffix.trim()).withStyle(ChatFormatting.YELLOW));
        }
        return text;
    }

    private static List<Component> messages(Component headline, List<Component> details) {
        List<Component> messages = new ArrayList<>(1 + details.size());
        messages.add(headline);
        messages.addAll(details);
        return messages;
    }

    private static List<Component> reasonDetails(String reason, String suffix) {
        String normalizedReason = normalizeReason(reason);
        if (suffix == null || suffix.isBlank()) {
            return List.of(detail(normalizedReason, ""));
        }

        return List.of(
                detail(normalizedReason, ""),
                valueDetail(suffix)
        );
    }

    private static String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isBlank()) return "";

        return normalized
                .replaceAll("\\s*\\+\\s*", " + ")
                .replaceAll("(?i)\\bDrogenabgabe\\s*(5|10|15)\\s*g?\\b", "DA $1g");
    }

    private static Component valueDetail(String value) {
        return Component.literal("» ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value == null ? "" : value.trim()).withStyle(ChatFormatting.YELLOW));
    }

    private static Component amountDetail(String amount, ChatFormatting color) {
        return Component.literal("\u00BB ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(amount == null ? "" : amount.trim()).withStyle(color, ChatFormatting.BOLD));
    }

    private static Component payHeadline(String actor, String target) {
        return action("PAY")
                .append(separator(" \u25C6 "))
                .append(payName(actor))
                .append(separator(" \u00BB "))
                .append(payName(target));
    }

    private static Component supportHeadline(String action, String target) {
        return action(action)
                .append(separator(" \u25C6 "))
                .append(supportName(target));
    }

    private static Component supportDetail(String source, String location, String suffix) {
        MutableComponent text = Component.literal("\u00BB ").withStyle(ChatFormatting.GRAY);
        boolean hasSource = source != null && !source.isBlank();
        if (hasSource) {
            text.append(Component.literal(source.trim()).withStyle(ChatFormatting.AQUA))
                    .append(separator(" | "));
        }
        text.append(Component.literal(location == null ? "" : location.trim()).withStyle(ChatFormatting.AQUA));
        if (suffix != null && !suffix.isBlank()) {
            text.append(separator(" | "))
                    .append(Component.literal(suffix.trim()).withStyle(ChatFormatting.YELLOW));
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

    private static MutableComponent action(String value) {
        String label = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return Component.literal(label).withStyle(ChatFormatting.RED);
    }

    private static MutableComponent name(String value) {
        return Component.literal(value == null ? "" : value.trim()).withStyle(ChatFormatting.BLUE);
    }

    private static MutableComponent supportName(String value) {
        return Component.literal(value == null ? "" : value.trim()).withStyle(ChatFormatting.AQUA);
    }

    private static MutableComponent payName(String value) {
        return Component.literal(value == null ? "" : value.trim()).withStyle(ChatFormatting.DARK_GREEN);
    }

    private static MutableComponent separator(String value) {
        return Component.literal(value).withStyle(ChatFormatting.DARK_GRAY);
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

    private static String playerName(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("§.", "")
                .replaceAll("\\[[^\\]]+\\]", " ")
                .trim();
        Matcher matcher = PLAYER_TOKEN_PATTERN.matcher(cleaned);
        String last = "";
        while (matcher.find()) {
            last = matcher.group();
        }
        return last.isBlank() ? cleaned : last;
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
        private final List<Component> replacementMessages;

        private Result(boolean cancelOriginal, List<Component> replacementMessages) {
            this.cancelOriginal = cancelOriginal;
            this.replacementMessages = replacementMessages == null ? List.of() : replacementMessages;
        }

        public static Result suppress() {
            return new Result(true, List.of());
        }

        public static Result replace(List<Component> replacementMessages) {
            return new Result(true, replacementMessages);
        }

        public boolean cancelOriginal() {
            return cancelOriginal;
        }

        public List<Component> replacementMessages() {
            return replacementMessages;
        }
    }
}
