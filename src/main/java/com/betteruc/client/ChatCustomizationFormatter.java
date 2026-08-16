package com.betteruc.client;

import com.betteruc.config.BetterUCConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class ChatCustomizationFormatter {
    private static final long PENDING_TTL_MS = 30000L;
    private static final int ACTION_GRADIENT_START = 0xFF5555;
    private static final int ACTION_GRADIENT_END = 0xAA0000;
    private static final int ACTOR_GRADIENT_START = 0x55FFFF;
    private static final int ACTOR_GRADIENT_END = 0x5555FF;
    private static final int TARGET_GRADIENT_START = 0x5555FF;
    private static final int TARGET_GRADIENT_END = 0xAA55FF;
    private static final int DETAIL_GRADIENT_START = 0x55AAFF;
    private static final int DETAIL_GRADIENT_END = 0x5555FF;
    private static final int POSITIVE_GRADIENT_START = 0x55FF55;
    private static final int POSITIVE_GRADIENT_END = 0x00AA00;
    private static final Pattern PLAYER_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_]{2,16}");
    private static final Pattern TEAM_PLAYER_PATTERN = Pattern.compile(
            "\\[UC\\]\\s*([A-Za-z0-9_]{2,16})",
            Pattern.CASE_INSENSITIVE
    );

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
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?(?:HQ:\\s*)?(?:[\\p{L} ]+\\s+)?(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+)\\s+hat\\s+(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+)\\s+(?:(?:die|seine|ihre)\\s+)?Drogen\\s+abgenommen[.!]?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SEARCH_REASON_PATTERN = Pattern.compile(
            "^\\s*(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:?\\s*Fahndungsgrund:\\s*(.+?)\\s*[|¦]\\s*Fahndungszeit:\\s*(\\d+)\\s*(Minute(?:n)?|Stunde(?:n)?)\\.?\\s*(?:\\[[^\\]]*\\]\\s*)*$",
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

    public static Result transform(
            Component original,
            boolean wpsHqEnabled,
            boolean reinfEnabled
    ) {
        Result result = transform(
                original == null ? "" : original.getString(),
                wpsHqEnabled,
                reinfEnabled,
                BetterUCConfig.INSTANCE.chatActionTextStyle,
                BetterUCConfig.INSTANCE.chatHeadlineSeparatorStyle,
                BetterUCConfig.INSTANCE.chatCustomizationGradientEnabled
        );
        return result == null ? null : result.withInteractionFrom(original);
    }

    public static Result transform(String raw, boolean wpsHqEnabled, boolean reinfEnabled) {
        return transform(
                raw,
                wpsHqEnabled,
                reinfEnabled,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );
    }

    static Result transform(
            String raw,
            boolean wpsHqEnabled,
            boolean reinfEnabled,
            String actionTextStyle,
            String separatorStyle
    ) {
        return transform(raw, wpsHqEnabled, reinfEnabled, actionTextStyle, separatorStyle, false);
    }

    static Result transform(
            String raw,
            boolean wpsHqEnabled,
            boolean reinfEnabled,
            String actionTextStyle,
            String separatorStyle,
            boolean gradientEnabled
    ) {
        HeadlineStyle headlineStyle = new HeadlineStyle(actionTextStyle, separatorStyle, gradientEnabled);
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
                    payHeadline("Du", paySent.group(1), headlineStyle),
                    amountDetail("-" + cleanAmount(paySent.group(2)) + "$", ChatFormatting.RED, headlineStyle)
            ));
        }

        Matcher payReceived = PAY_RECEIVED_PATTERN.matcher(clean);
        if (payReceived.matches()) {
            return Result.replace(List.of(
                    payHeadline(payReceived.group(1), "Du", headlineStyle),
                    amountDetail("+" + cleanAmount(payReceived.group(2)) + "$", ChatFormatting.GREEN, headlineStyle)
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
            return Result.replaceReinforcement(List.of(
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
            return Result.replaceReinforcement(List.of(
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
                    headline("GESUCHT", current.target(), headlineStyle),
                    reasonDetails(current.reason(), wantedLevel.group(2) + " Wanteds", headlineStyle)
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
                    headline("WAFFEN ABNAHME", playerName(weaponSeized.group(1)), playerName(weaponSeized.group(2)), headlineStyle)
            ));
        }

        Matcher drugSeized = DRUG_SEIZED_PATTERN.matcher(clean);
        if (drugSeized.matches()) {
            return Result.replace(List.of(
                    headline("DROGEN ABNAHME", playerName(drugSeized.group(1)), playerName(drugSeized.group(2)), headlineStyle)
            ));
        }

        Matcher searchReason = SEARCH_REASON_PATTERN.matcher(clean);
        if (searchReason.matches() && isFahndungPending()) {
            Pending current = pending;
            pending = null;
            String action = fahndungAction(current.type());
            return Result.replace(messages(
                    headline(action, current.actor(), current.target(), headlineStyle),
                    reasonDetails(searchReason.group(1), searchReason.group(2) + " " + searchReason.group(3), headlineStyle)
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
                    headline("VERÄNDERT", current.actor(), current.target(), headlineStyle),
                    reasonDetails(newReason.group(1), newReason.group(2) + " » " + newReason.group(3) + " Wanteds", headlineStyle)
            ));
        }

        Matcher deleted = DELETED_PATTERN.matcher(clean);
        if (deleted.matches()) {
            return Result.replace(List.of(
                    headline("GELÖSCHT", playerName(deleted.group(1)), playerName(deleted.group(2)), headlineStyle),
                    detail("Akten gelöscht", "", headlineStyle)
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

    private static Component headline(String action, String target, HeadlineStyle headlineStyle) {
        return action(action, headlineStyle)
                .append(separator(headlineLeadSeparator(headlineStyle)))
                .append(targetName(target, headlineStyle));
    }

    private static Component headline(String action, String actor, String target, HeadlineStyle headlineStyle) {
        return action(action, headlineStyle)
                .append(separator(headlineLeadSeparator(headlineStyle)))
                .append(actorName(actor, headlineStyle))
                .append(separator(headlineDirectionSeparator(headlineStyle)))
                .append(targetName(target, headlineStyle));
    }

    private static Component detail(String reason, String suffix, HeadlineStyle headlineStyle) {
        MutableComponent text = Component.literal("» ").withStyle(ChatFormatting.GRAY)
                .append(detailText(reason, headlineStyle));
        if (suffix != null && !suffix.isBlank()) {
            text.append(separator(" | "))
                    .append(detailText(suffix, headlineStyle));
        }
        return text;
    }

    private static List<Component> messages(Component headline, List<Component> details) {
        List<Component> messages = new ArrayList<>(1 + details.size());
        messages.add(headline);
        messages.addAll(details);
        return messages;
    }

    private static List<Component> reasonDetails(String reason, String suffix, HeadlineStyle headlineStyle) {
        String normalizedReason = normalizeReason(reason);
        if (suffix == null || suffix.isBlank()) {
            return List.of(detail(normalizedReason, "", headlineStyle));
        }

        return List.of(
                detail(normalizedReason, "", headlineStyle),
                valueDetail(suffix, headlineStyle)
        );
    }

    private static String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isBlank()) return "";

        return normalized
                .replaceAll("\\s*\\+\\s*", " + ")
                .replaceAll("(?i)\\bDrogenabgabe\\s*(5|10|15)\\s*g?\\b", "DA $1g");
    }

    private static Component valueDetail(String value, HeadlineStyle headlineStyle) {
        return Component.literal("» ").withStyle(ChatFormatting.GRAY)
                .append(detailText(value, headlineStyle));
    }

    private static Component amountDetail(String amount, ChatFormatting color, HeadlineStyle headlineStyle) {
        MutableComponent value = headlineStyle.gradientEnabled()
                ? gradientText(
                        amount == null ? "" : amount.trim(),
                        color == ChatFormatting.GREEN ? POSITIVE_GRADIENT_START : ACTION_GRADIENT_START,
                        color == ChatFormatting.GREEN ? POSITIVE_GRADIENT_END : ACTION_GRADIENT_END,
                        true
                )
                : Component.literal(amount == null ? "" : amount.trim()).withStyle(color, ChatFormatting.BOLD);
        return Component.literal("\u00BB ").withStyle(ChatFormatting.GRAY).append(value);
    }

    private static Component payHeadline(String actor, String target, HeadlineStyle headlineStyle) {
        return action("PAY", headlineStyle)
                .append(separator(headlineLeadSeparator(headlineStyle)))
                .append(headlineStyle.gradientEnabled() ? actorName(actor, headlineStyle) : payName(actor))
                .append(separator(headlineDirectionSeparator(headlineStyle)))
                .append(headlineStyle.gradientEnabled() ? targetName(target, headlineStyle) : payName(target));
    }

    private static Component supportHeadline(String action, String target) {
        return supportText(action == null ? "" : action.trim().toLowerCase(Locale.ROOT),
                BetterUCConfig.INSTANCE.reinfLabelColor)
                .append(separator(" \u25C6 "))
                .append(supportName(target));
    }

    private static Component supportDetail(String source, String location, String suffix) {
        MutableComponent text = Component.literal("\u00BB ").withStyle(ChatFormatting.GRAY);
        boolean hasSource = source != null && !source.isBlank();
        if (hasSource) {
            text.append(supportText(source.trim(), BetterUCConfig.INSTANCE.reinfTextColor))
                    .append(separator(" | "));
        }
        text.append(supportText(location == null ? "" : location.trim(), BetterUCConfig.INSTANCE.reinfTextColor));
        if (suffix != null && !suffix.isBlank()) {
            text.append(separator(" | "))
                    .append(supportText(suffix.trim(), BetterUCConfig.INSTANCE.reinfDistanceColor));
        }
        return text;
    }

    public static List<Component> reinforcementPreview() {
        return List.of(
                supportHeadline("REINF", "FABI1441"),
                supportDetail("FBI", "Bank", "120m")
        );
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

    private static MutableComponent action(String value, HeadlineStyle headlineStyle) {
        String label = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS.equals(headlineStyle.actionTextStyle())) {
            label = SmallCapsText.convert(label);
        }
        if (headlineStyle.gradientEnabled()) {
            return gradientText(label, ACTION_GRADIENT_START, ACTION_GRADIENT_END, true);
        }
        return Component.literal(label).withStyle(ChatFormatting.RED);
    }

    private static String headlineLeadSeparator(HeadlineStyle headlineStyle) {
        return BetterUCConfig.CHAT_SEPARATOR_TECHNICAL.equals(headlineStyle.separatorStyle())
                ? " // "
                : " \u25C6 ";
    }

    private static String headlineDirectionSeparator(HeadlineStyle headlineStyle) {
        return BetterUCConfig.CHAT_SEPARATOR_TECHNICAL.equals(headlineStyle.separatorStyle())
                ? " \u2192 "
                : " \u00BB ";
    }

    private static MutableComponent actorName(String value, HeadlineStyle headlineStyle) {
        String name = value == null ? "" : value.trim();
        return headlineStyle.gradientEnabled()
                ? gradientText(name, ACTOR_GRADIENT_START, ACTOR_GRADIENT_END, false)
                : Component.literal(name).withStyle(ChatFormatting.BLUE);
    }

    private static MutableComponent targetName(String value, HeadlineStyle headlineStyle) {
        String name = value == null ? "" : value.trim();
        return headlineStyle.gradientEnabled()
                ? gradientText(name, TARGET_GRADIENT_START, TARGET_GRADIENT_END, false)
                : Component.literal(name).withStyle(ChatFormatting.BLUE);
    }

    private static MutableComponent detailText(String value, HeadlineStyle headlineStyle) {
        String text = value == null ? "" : value.trim();
        return headlineStyle.gradientEnabled()
                ? gradientText(text, DETAIL_GRADIENT_START, DETAIL_GRADIENT_END, true)
                : Component.literal(text).withStyle(ChatFormatting.BLUE);
    }

    private static MutableComponent gradientText(
            String value,
            int startColor,
            int endColor,
            boolean bold
    ) {
        MutableComponent result = Component.empty();
        int[] codePoints = (value == null ? "" : value).codePoints().toArray();
        for (int index = 0; index < codePoints.length; index++) {
            float progress = codePoints.length <= 1 ? 0.0F : (float) index / (codePoints.length - 1);
            Style style = Style.EMPTY.withColor(interpolateColor(startColor, endColor, progress));
            if (bold) {
                style = style.withBold(true);
            }
            result.append(Component.literal(new String(Character.toChars(codePoints[index]))).setStyle(style));
        }
        return result;
    }

    private static int interpolateColor(int startColor, int endColor, float progress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        int red = Math.round(((startColor >> 16) & 0xFF) * (1.0F - clamped)
                + ((endColor >> 16) & 0xFF) * clamped);
        int green = Math.round(((startColor >> 8) & 0xFF) * (1.0F - clamped)
                + ((endColor >> 8) & 0xFF) * clamped);
        int blue = Math.round((startColor & 0xFF) * (1.0F - clamped)
                + (endColor & 0xFF) * clamped);
        return (red << 16) | (green << 8) | blue;
    }

    private static MutableComponent supportName(String value) {
        return supportText(value == null ? "" : value.trim(), BetterUCConfig.INSTANCE.reinfTextColor);
    }

    private static MutableComponent supportText(String value, int configuredColor) {
        int color = BetterUCConfig.INSTANCE.reinfUniformColorEnabled
                ? BetterUCConfig.INSTANCE.reinfUniformColor
                : configuredColor;
        return Component.literal(value == null ? "" : value)
                .setStyle(Style.EMPTY.withColor(color & 0xFFFFFF));
    }

    private static MutableComponent payName(String value) {
        return Component.literal(value == null ? "" : value.trim()).withStyle(ChatFormatting.DARK_GREEN);
    }

    private static MutableComponent separator(String value) {
        return Component.literal(value).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        return raw
                .replace('\u00A0', ' ')
                .replaceAll("(?i)\\u00A7[0-9A-FK-ORX]", "")
                .replaceAll("[\\u00AD\\u200B-\\u200D\\u2060\\uFEFF]", "")
                .trim();
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
        String normalized = normalize(value);
        Matcher teamPlayer = TEAM_PLAYER_PATTERN.matcher(normalized);
        if (teamPlayer.find()) {
            return "[UC]" + teamPlayer.group(1);
        }
        String cleaned = normalized
                .replaceAll("\\[[^\\]]+\\]", " ")
                .trim();
        String player = lastPlayerToken(cleaned);
        if (player.isBlank()) {
            player = lastPlayerToken(normalized);
        }
        return player.isBlank() ? cleaned : player;
    }

    private static String lastPlayerToken(String value) {
        Matcher matcher = PLAYER_TOKEN_PATTERN.matcher(value == null ? "" : value);
        String last = "";
        while (matcher.find()) {
            last = matcher.group();
        }
        return last;
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

    private record HeadlineStyle(String actionTextStyle, String separatorStyle, boolean gradientEnabled) {
    }

    private static Style findInteractiveStyle(Component component) {
        if (component == null) return null;
        if (component.getStyle().getClickEvent() != null) {
            return component.getStyle();
        }
        for (Component sibling : component.getSiblings()) {
            Style style = findInteractiveStyle(sibling);
            if (style != null) {
                return style;
            }
        }
        return null;
    }

    public static final class Result {
        private final boolean cancelOriginal;
        private final List<Component> replacementMessages;
        private final boolean reinforcement;

        private Result(boolean cancelOriginal, List<Component> replacementMessages, boolean reinforcement) {
            this.cancelOriginal = cancelOriginal;
            this.replacementMessages = replacementMessages == null ? List.of() : replacementMessages;
            this.reinforcement = reinforcement;
        }

        public static Result suppress() {
            return new Result(true, List.of(), false);
        }

        public static Result replace(List<Component> replacementMessages) {
            return new Result(true, replacementMessages, false);
        }

        static Result replaceReinforcement(List<Component> replacementMessages) {
            return new Result(true, replacementMessages, true);
        }

        public boolean cancelOriginal() {
            return cancelOriginal;
        }

        public List<Component> replacementMessages() {
            return replacementMessages;
        }

        public boolean reinforcement() {
            return reinforcement;
        }

        private Result withInteractionFrom(Component original) {
            Style sourceStyle = findInteractiveStyle(original);
            if (sourceStyle == null || sourceStyle.getClickEvent() == null
                    || replacementMessages.isEmpty()) {
                return this;
            }

            List<Component> interactiveMessages = new ArrayList<>(replacementMessages.size());
            for (Component replacement : replacementMessages) {
                MutableComponent interactive = replacement.copy();
                interactive.setStyle(interactive.getStyle().withClickEvent(sourceStyle.getClickEvent()));
                interactiveMessages.add(interactive);
            }
            return new Result(cancelOriginal, List.copyOf(interactiveMessages), reinforcement);
        }
    }
}
