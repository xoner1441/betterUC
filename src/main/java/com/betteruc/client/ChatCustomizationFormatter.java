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
    private static final long PENDING_TICKET_TTL_MS = 5 * 60 * 1000L;
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
            "^\\s*(?:\\[betterUC\\s+Second\\s+Chat\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?(?:HQ:\\s*)?(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+)\\s+wurde\\s+von\\s+(.+?)\\s+get(?:ötet|oetet|otet)\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JAILED_PATTERN = Pattern.compile(
            "^\\s*(?:\\[betterUC\\s+Second\\s+Chat\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?(?:HQ:\\s*)?(?:\\[[^\\]]+\\]\\s*)?(.+?)\\s+wurde\\s+von\\s+(.+?)\\s+eingesperrt[.!]?\\s*(?:\\[[^\\]]*\\]\\s*)*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ARREST_OR_KILL_WITH_REASON_PATTERN = Pattern.compile(
            "^\\s*(?:\\[betterUC\\s+Second\\s+Chat\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?(?:HQ:\\s*)?(?:\\[[^\\]]+\\]\\s*)?(.+?)\\s+wurde\\s+von\\s+(.+?)\\s+(get(?:ötet|oetet|otet)|eingesperrt)[.!]?\\s*(?:\\R|\\\\n)\\s*(?:\\[betterUC\\s+Second\\s+Chat\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:?\\s*Fahndungsgrund:\\s*(.+?)\\s*[|¦]\\s*Fahndungszeit:\\s*(\\d+)\\s*(Minute(?:n)?|Stunde(?:n)?)\\.?\\s*(?:\\[[^\\]]*\\]\\s*)*$",
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
    private static final Pattern LICENSE_SEIZED_PATTERN = Pattern.compile(
            "^\\s*(?:(?:\\[System\\]\\s*)?\\[CHAT\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?(?:HQ:\\s*)?Beamt(?:er|in)\\s+((?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]+)\\s+hat\\s+((?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]+)['’]?s\\s+F(?:ü|ue)hrerschein\\s+abgenommen[.!]?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LICENSE_RETURNED_PATTERN = Pattern.compile(
            "^\\s*(?:(?:\\[System\\]\\s*)?\\[CHAT\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?(?:HQ:\\s*)?Beamt(?:er|in)\\s+((?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]+)\\s+hat\\s+((?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]+)['’]?s\\s+F(?:ü|ue)hrerschein\\s+zur(?:ü|ue)ckgegeben[.!]?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TICKET_ISSUED_PATTERN = Pattern.compile(
            "^\\s*(?:\\[betterUC\\s+Second\\s+Chat\\]\\s*)?(?:(?:\\[System\\]\\s*)?\\[CHAT\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:\\s*Officer\\s+((?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]+)\\s+hat\\s+((?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]+)\\s+ein\\s+Ticket\\s+(?:über|ueber)\\s+([0-9][0-9.,]*)\\$\\s+ausgestellt[.!]?\\s*Best(?:ä|ae)tigung\\s+ausstehend,?\\s*over\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SEARCH_REASON_PATTERN = Pattern.compile(
            "^\\s*(?:\\[betterUC\\s+Second\\s+Chat\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:?\\s*Fahndungsgrund:\\s*(.+?)\\s*[|¦]\\s*Fahndungszeit:\\s*(\\d+)\\s*(Minute(?:n)?|Stunde(?:n)?)\\.?\\s*(?:\\[[^\\]]*\\]\\s*)*$",
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
            "^\\s*(?:\\[betterUC\\s+Second\\s+Chat\\]\\s*)?(?:(?:\\[System\\]\\s*)?\\[CHAT\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:\\s*(?:.*?\\s+)?((?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]+)\\s+hat\\s+((?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]+)(?:(?:['’]?s)|(?:\\s+(?:seine|ihre|die)))\\s+Akten\\s+gel[öo]scht,\\s*over\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PERSONAL_PLANTAGE_BURNED_PATTERN = Pattern.compile(
            "^\\s*(?:(?:\\[System\\]\\s*)?\\[CHAT\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:[^\\p{L}\\p{N}_\\[]+\\s*)?Du\\s+hast\\s+erfolgreich\\s+eine\\s+(Pulver|Kr(?:ä|ae|a)uter)\\s*-?\\s*Plant(?:age)?\\s+verbrannt[.!]?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HQ_PLANTAGE_BURNED_PATTERN = Pattern.compile(
            "^\\s*(?:\\[betterUC\\s+Second\\s+Chat\\]\\s*)?(?:(?:\\[System\\]\\s*)?\\[CHAT\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:\\s*Agent\\s+((?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]+)\\s+hat\\s+erfolgreich\\s+eine\\s+(Pulver|Kr(?:ä|ae|a)uter)\\s*-?\\s*Plant(?:age)?\\s+verbrannt,?\\s*over\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EMERGENCY_INCOMING_BLOCK_PATTERN = Pattern.compile(
            "^\\s*(?:\\[betterUC\\s+Second\\s+Chat\\]\\s*)?(?:(?:\\[System\\]\\s*)?\\[CHAT\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:\\s*Achtung!\\s*Ein\\s+Notruf\\s+von\\s+((?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]+)\\s*\\((\\d+)\\)\\s*:\\s*(.+?)\\s*(?:\\R|\\\\n)\\s*(?:\\[betterUC\\s+Second\\s+Chat\\]\\s*)?(?:(?:\\[System\\]\\s*)?\\[CHAT\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:\\s*Der\\s+n(?:ä|ae)(?:chste|heste)\\s+Punkt\\s+ist\\s+(.+?)\\.\\s*Die\\s+n(?:ä|ae)(?:chsten|hesten)\\s+Personen\\s+sind\\s+(.+?)[.!]?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EMERGENCY_INCOMING_PATTERN = Pattern.compile(
            "^\\s*(?:\\[betterUC\\s+Second\\s+Chat\\]\\s*)?(?:(?:\\[System\\]\\s*)?\\[CHAT\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:\\s*Achtung!\\s*Ein\\s+Notruf\\s+von\\s+((?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]+)\\s*\\((\\d+)\\)\\s*:\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EMERGENCY_DETAILS_PATTERN = Pattern.compile(
            "^\\s*(?:\\[betterUC\\s+Second\\s+Chat\\]\\s*)?(?:(?:\\[System\\]\\s*)?\\[CHAT\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:\\s*Der\\s+n(?:ä|ae)(?:chste|heste)\\s+Punkt\\s+ist\\s+(.+?)\\.\\s*Die\\s+n(?:ä|ae)(?:chsten|hesten)\\s+Personen\\s+sind\\s+(.+?)[.!]?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EMERGENCY_ACCEPTED_PATTERN = Pattern.compile(
            "^\\s*(?:\\[betterUC\\s+Second\\s+Chat\\]\\s*)?(?:(?:\\[System\\]\\s*)?\\[CHAT\\]\\s*)?(?:\\d{1,2}:\\d{2}:\\d{2}\\s*)?(?:\\W+\\s*)?HQ:\\s*((?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]+)\\s+hat\\s+den\\s+Notruf\\s+von\\s+((?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]+)\\s+angenommen,?\\s*over\\.?\\s*(?:(?:\\R|\\\\n)?\\s*\\((\\d+)\\s*m\\s+entfernt\\))?\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EMERGENCY_PERSON_PATTERN = Pattern.compile(
            "((?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]+)\\s*\\((\\d+)\\s*m\\)",
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
    private static PendingTicket pendingTicket;

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
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        Result result = transform(
                original == null ? "" : original.getString(),
                wpsHqEnabled,
                reinfEnabled,
                config.chatActionTextStyle,
                config.chatHeadlineSeparatorStyle,
                config.chatCustomizationGradientEnabled,
                GradientPalette.configured(config)
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
        return transform(raw, wpsHqEnabled, reinfEnabled, actionTextStyle, separatorStyle,
                gradientEnabled, GradientPalette.defaults());
    }

    static Result transform(
            String raw,
            boolean wpsHqEnabled,
            boolean reinfEnabled,
            String actionTextStyle,
            String separatorStyle,
            boolean gradientEnabled,
            GradientPalette palette
    ) {
        HeadlineStyle headlineStyle = new HeadlineStyle(
                actionTextStyle,
                separatorStyle,
                gradientEnabled,
                palette == null ? GradientPalette.defaults() : palette
        );
        String clean = normalize(raw);
        if (clean.isEmpty()) return null;

        clearExpiredPending();
        if (!wpsHqEnabled) {
            pending = null;
            pendingTicket = null;
        }

        if (wpsHqEnabled) {
        Matcher paySent = PAY_SENT_PATTERN.matcher(clean);
        if (paySent.matches()) {
            return Result.replace(List.of(
                    payHeadline("Du", paySent.group(1), headlineStyle),
                    payAmountDetail("-" + cleanAmount(paySent.group(2)) + "$", false, headlineStyle)
            ));
        }

        Matcher payReceived = PAY_RECEIVED_PATTERN.matcher(clean);
        if (payReceived.matches()) {
            return Result.replace(List.of(
                    payHeadline(payReceived.group(1), "Du", headlineStyle),
                    payAmountDetail("+" + cleanAmount(payReceived.group(2)) + "$", true, headlineStyle)
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

        Matcher emergencyIncomingBlock = EMERGENCY_INCOMING_BLOCK_PATTERN.matcher(clean);
        if (emergencyIncomingBlock.matches()) {
            return Result.replaceHq(emergencyIncomingMessages(
                    playerName(emergencyIncomingBlock.group(1)),
                    emergencyIncomingBlock.group(2),
                    cleanEmergencyMessage(emergencyIncomingBlock.group(3)),
                    emergencyIncomingBlock.group(4),
                    emergencyIncomingBlock.group(5),
                    headlineStyle
            ));
        }

        Matcher emergencyIncoming = EMERGENCY_INCOMING_PATTERN.matcher(clean);
        if (emergencyIncoming.matches()) {
            return Result.replaceHq(List.of(
                    emergencyIncomingHeadline(playerName(emergencyIncoming.group(1)),
                            emergencyIncoming.group(2), headlineStyle),
                    emergencyMessageDetail(cleanEmergencyMessage(emergencyIncoming.group(3)), headlineStyle)
            ));
        }

        Matcher emergencyDetails = EMERGENCY_DETAILS_PATTERN.matcher(clean);
        if (emergencyDetails.matches()) {
            return Result.replaceHq(List.of(
                    emergencyLocationDetail(emergencyDetails.group(1), emergencyDetails.group(2), headlineStyle)
            ));
        }

        Matcher emergencyAccepted = EMERGENCY_ACCEPTED_PATTERN.matcher(clean);
        if (emergencyAccepted.matches()) {
            List<Component> messages = new ArrayList<>();
            messages.add(emergencyAcceptedHeadline(
                    playerName(emergencyAccepted.group(1)),
                    playerName(emergencyAccepted.group(2)),
                    headlineStyle
            ));
            if (emergencyAccepted.group(3) != null) {
                messages.add(emergencyDistanceDetail(emergencyAccepted.group(3), headlineStyle));
            }
            return Result.replaceHq(messages);
        }

        Matcher personalPlantageBurned = PERSONAL_PLANTAGE_BURNED_PATTERN.matcher(clean);
        if (personalPlantageBurned.matches()) {
            return Result.replace(List.of(
                    plantageSuccessHeadline(headlineStyle)
            ));
        }

        Matcher hqPlantageBurned = HQ_PLANTAGE_BURNED_PATTERN.matcher(clean);
        if (hqPlantageBurned.matches()) {
            String plantageType = plantageType(hqPlantageBurned.group(2));
            return Result.replaceHq(List.of(
                    plantageHeadline(playerName(hqPlantageBurned.group(1)), headlineStyle),
                    plantageDetail(plantageType + "-Plantage erfolgreich zerstört", headlineStyle)
            ));
        }

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

        Matcher arrestOrKillWithReason = ARREST_OR_KILL_WITH_REASON_PATTERN.matcher(clean);
        if (arrestOrKillWithReason.matches()) {
            PendingType type = arrestOrKillWithReason.group(3).toLowerCase(Locale.ROOT).startsWith("eingesperrt")
                    ? PendingType.JAILED
                    : PendingType.KILLED;
            return Result.replace(messages(
                    headline(fahndungAction(type), playerName(arrestOrKillWithReason.group(2)),
                            playerName(arrestOrKillWithReason.group(1)), headlineStyle),
                    reasonDetails(arrestOrKillWithReason.group(4),
                            arrestOrKillWithReason.group(5) + " " + arrestOrKillWithReason.group(6), headlineStyle)
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

        Matcher licenseSeized = LICENSE_SEIZED_PATTERN.matcher(clean);
        if (licenseSeized.matches()) {
            return Result.replace(List.of(
                    headline("FÜHRERSCHEIN ABNAHME", playerName(licenseSeized.group(1)),
                            playerName(licenseSeized.group(2)), headlineStyle)
            ));
        }

        Matcher licenseReturned = LICENSE_RETURNED_PATTERN.matcher(clean);
        if (licenseReturned.matches()) {
            return Result.replace(List.of(
                    positiveHeadline("FÜHRERSCHEIN RÜCKGABE", playerName(licenseReturned.group(1)),
                            playerName(licenseReturned.group(2)), headlineStyle)
            ));
        }

        Matcher ticketIssued = TICKET_ISSUED_PATTERN.matcher(clean);
        if (ticketIssued.matches()) {
            String actor = playerName(ticketIssued.group(1));
            String target = playerName(ticketIssued.group(2));
            String amount = cleanAmount(ticketIssued.group(3)) + "$";
            pendingTicket = new PendingTicket(actor, target, amount, System.currentTimeMillis());
            return Result.replace(List.of(
                    ticketHeadline("TICKET AUSGESTELLT", actor, target, headlineStyle),
                    ticketDetail(amount, "Bestätigung ausstehend", false, headlineStyle)
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
            String actor = playerName(deleted.group(1));
            String target = playerName(deleted.group(2));
            if (matchesPendingTicket(actor, target)) {
                PendingTicket current = pendingTicket;
                pendingTicket = null;
                return Result.replace(List.of(
                        positiveHeadline("TICKET BESTÄTIGT", actor, target, headlineStyle),
                        ticketDetail(current.amount(), "Akten gelöscht", true, headlineStyle)
                ));
            }
            return Result.replace(List.of(
                    headline("AKTEN GELÖSCHT", actor, target, headlineStyle)
            ));
        }

        return null;
    }

    public static void clearPending() {
        pending = null;
        pendingTicket = null;
    }

    private static void clearExpiredPending() {
        if (pending != null && System.currentTimeMillis() - pending.createdAtMs() > PENDING_TTL_MS) {
            pending = null;
        }
        if (pendingTicket != null
                && System.currentTimeMillis() - pendingTicket.createdAtMs() > PENDING_TICKET_TTL_MS) {
            pendingTicket = null;
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

    private static boolean matchesPendingTicket(String actor, String target) {
        return pendingTicket != null
                && key(pendingTicket.actor()).equals(key(actor))
                && key(pendingTicket.target()).equals(key(target));
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

    private static Component positiveHeadline(String action, String actor, String target, HeadlineStyle headlineStyle) {
        return positiveAction(action, headlineStyle)
                .append(separator(headlineLeadSeparator(headlineStyle)))
                .append(actorName(actor, headlineStyle))
                .append(separator(headlineDirectionSeparator(headlineStyle)))
                .append(targetName(target, headlineStyle));
    }

    private static Component ticketHeadline(String action, String actor, String target, HeadlineStyle headlineStyle) {
        return ticketAction(action, headlineStyle)
                .append(separator(headlineLeadSeparator(headlineStyle)))
                .append(actorName(actor, headlineStyle))
                .append(separator(headlineDirectionSeparator(headlineStyle)))
                .append(targetName(target, headlineStyle));
    }

    private static Component plantageSuccessHeadline(HeadlineStyle headlineStyle) {
        return plantageAction("PLANTAGE VERBRANNT", headlineStyle)
                .append(separator(headlineLeadSeparator(headlineStyle)))
                .append(plantageDetailText("Erfolgreich", headlineStyle));
    }

    private static Component plantageHeadline(String actor, HeadlineStyle headlineStyle) {
        return plantageAction("PLANTAGE VERBRANNT", headlineStyle)
                .append(separator(headlineLeadSeparator(headlineStyle)))
                .append(actorName(actor, headlineStyle));
    }

    private static Component plantageDetail(String value, HeadlineStyle headlineStyle) {
        return Component.literal("\u00BB ").withStyle(ChatFormatting.GRAY)
                .append(plantageDetailText(value, headlineStyle));
    }

    private static String plantageType(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replace("ä", "ae");
        return normalized.startsWith("kraeuter") || normalized.startsWith("krauter")
                ? "Kräuter"
                : "Pulver";
    }

    private static List<Component> emergencyIncomingMessages(
            String caller,
            String callId,
            String message,
            String location,
            String people,
            HeadlineStyle headlineStyle
    ) {
        return List.of(
                emergencyIncomingHeadline(caller, callId, headlineStyle),
                emergencyMessageDetail(message, headlineStyle),
                emergencyLocationDetail(location, people, headlineStyle)
        );
    }

    private static Component emergencyIncomingHeadline(
            String caller,
            String callId,
            HeadlineStyle headlineStyle
    ) {
        return emergencyAction("NOTRUF", false, headlineStyle)
                .append(separator(" \u2726 "))
                .append(actorName(caller, headlineStyle))
                .append(separator(" \u00BB "))
                .append(Component.literal("ID " + callId).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    private static Component emergencyAcceptedHeadline(
            String officer,
            String caller,
            HeadlineStyle headlineStyle
    ) {
        return emergencyAction("NOTRUF ANGENOMMEN", true, headlineStyle)
                .append(separator(" \u2726 "))
                .append(actorName(officer, headlineStyle))
                .append(separator(" \u00BB "))
                .append(targetName(caller, headlineStyle));
    }

    private static Component emergencyMessageDetail(String message, HeadlineStyle headlineStyle) {
        return Component.literal("\u00BB ").withStyle(ChatFormatting.GRAY)
                .append(emergencyMessageText(message, headlineStyle));
    }

    private static Component emergencyLocationDetail(
            String location,
            String people,
            HeadlineStyle headlineStyle
    ) {
        MutableComponent result = Component.literal("\u00BB ").withStyle(ChatFormatting.GRAY)
                .append(emergencyDetailText(location, headlineStyle));
        Matcher matcher = EMERGENCY_PERSON_PATTERN.matcher(people == null ? "" : people);
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            result.append(separator(" \u00B7 "))
                    .append(targetName(playerName(matcher.group(1)), headlineStyle))
                    .append(separator(" "))
                    .append(emergencyDistanceText(matcher.group(2)));
        }
        if (!matched && people != null && !people.isBlank()) {
            result.append(separator(" \u00B7 "))
                    .append(emergencyDetailText(people.trim(), headlineStyle));
        }
        return result;
    }

    private static Component emergencyDistanceDetail(String meters, HeadlineStyle headlineStyle) {
        return Component.literal("\u00BB ").withStyle(ChatFormatting.GRAY)
                .append(emergencyDistanceText(meters))
                .append(separator(" "))
                .append(emergencyMessageText("entfernt", headlineStyle));
    }

    private static MutableComponent emergencyDistanceText(String meters) {
        String value = meters == null ? "0" : meters.trim();
        int distance;
        try {
            distance = Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            distance = Integer.MAX_VALUE;
        }
        ChatFormatting color = distance <= 25
                ? ChatFormatting.GREEN
                : distance <= 75 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return Component.literal(value + "m").withStyle(color, ChatFormatting.BOLD);
    }

    private static String cleanEmergencyMessage(String value) {
        String result = value == null ? "" : value.trim();
        while (!result.isEmpty() && (result.charAt(0) == '"'
                || result.charAt(0) == '\u201E'
                || result.charAt(0) == '\u201C')) {
            result = result.substring(1).trim();
        }
        while (!result.isEmpty()) {
            char last = result.charAt(result.length() - 1);
            if (last == '"' || last == '\u201C' || last == '\u201D' || last == '.') {
                result = result.substring(0, result.length() - 1).trim();
            } else {
                break;
            }
        }
        return result;
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

    private static Component payAmountDetail(String amount, boolean incoming, HeadlineStyle headlineStyle) {
        GradientPalette palette = headlineStyle.palette();
        MutableComponent value = headlineStyle.gradientEnabled()
                ? gradientText(
                        amount == null ? "" : amount.trim(),
                        incoming ? palette.payIncomingStart() : palette.payOutgoingStart(),
                        incoming ? palette.payIncomingEnd() : palette.payOutgoingEnd(),
                        true
                )
                : Component.literal(amount == null ? "" : amount.trim()).withStyle(
                        incoming ? ChatFormatting.GREEN : ChatFormatting.RED,
                        ChatFormatting.BOLD
                );
        return Component.literal("\u00BB ").withStyle(ChatFormatting.GRAY).append(value);
    }

    private static Component ticketDetail(
            String amount,
            String status,
            boolean confirmed,
            HeadlineStyle headlineStyle
    ) {
        GradientPalette palette = headlineStyle.palette();
        MutableComponent amountText = headlineStyle.gradientEnabled()
                ? gradientText(amount, palette.hqTicketDetailStart(), palette.hqTicketDetailEnd(), true)
                : Component.literal(amount).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        MutableComponent statusText;
        if (headlineStyle.gradientEnabled()) {
            statusText = confirmed
                    ? gradientText(status, palette.hqPositiveStart(), palette.hqPositiveEnd(), true)
                    : gradientText(status, palette.hqTicketDetailStart(), palette.hqTicketDetailEnd(), true);
        } else {
            statusText = Component.literal(status).withStyle(
                    confirmed ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
                    ChatFormatting.BOLD
            );
        }
        return Component.literal("\u00BB ").withStyle(ChatFormatting.GRAY)
                .append(amountText)
                .append(separator(" | "))
                .append(statusText);
    }

    private static Component payHeadline(String actor, String target, HeadlineStyle headlineStyle) {
        return payAction("PAY", headlineStyle)
                .append(separator(headlineLeadSeparator(headlineStyle)))
                .append(headlineStyle.gradientEnabled() ? payActorName(actor, headlineStyle) : payName(actor))
                .append(separator(headlineDirectionSeparator(headlineStyle)))
                .append(headlineStyle.gradientEnabled() ? payTargetName(target, headlineStyle) : payName(target));
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

    public static List<Component> hqGradientPreview() {
        HeadlineStyle style = previewHeadlineStyle();
        return List.of(
                headline("INHAFTIERT", "FABI1441", "Spieler", style),
                detail("Versuchter Mord", "2 Minuten", style),
                positiveHeadline("FÜHRERSCHEIN RÜCKGABE", "FABI1441", "Spieler", style),
                ticketHeadline("TICKET AUSGESTELLT", "FABI1441", "Spieler", style),
                ticketDetail("230$", "Bestätigung ausstehend", false, style),
                plantageHeadline("FABI1441", style),
                plantageDetail("Pulver-Plantage erfolgreich zerstört", style)
        );
    }

    public static List<Component> payGradientPreview() {
        HeadlineStyle style = previewHeadlineStyle();
        return List.of(
                payHeadline("Du", "Spieler", style),
                payAmountDetail("-230$", false, style),
                payAmountDetail("+230$", true, style)
        );
    }

    public static List<Component> emergencyGradientPreview() {
        HeadlineStyle style = previewHeadlineStyle();
        return List.of(
                emergencyIncomingHeadline("ardasaatci", "221", style),
                emergencyMessageDetail("hilfe eymen sagt mir er will kämpfen", style),
                emergencyLocationDetail("Tellerwäscher", "Eymenn (3m), _toobi (36m)", style),
                emergencyAcceptedHeadline("_toobi", "ardasaatci", style),
                emergencyDistanceDetail("11", style)
        );
    }

    private static HeadlineStyle previewHeadlineStyle() {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        return new HeadlineStyle(
                config.chatActionTextStyle,
                config.chatHeadlineSeparatorStyle,
                true,
                GradientPalette.configured(config)
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
            return gradientText(label, headlineStyle.palette().hqActionStart(),
                    headlineStyle.palette().hqActionEnd(), true);
        }
        return Component.literal(label).withStyle(ChatFormatting.RED);
    }

    private static MutableComponent positiveAction(String value, HeadlineStyle headlineStyle) {
        String label = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS.equals(headlineStyle.actionTextStyle())) {
            label = SmallCapsText.convert(label);
        }
        if (headlineStyle.gradientEnabled()) {
            return gradientText(label, headlineStyle.palette().hqPositiveStart(),
                    headlineStyle.palette().hqPositiveEnd(), true);
        }
        return Component.literal(label).withStyle(ChatFormatting.GREEN);
    }

    private static MutableComponent ticketAction(String value, HeadlineStyle headlineStyle) {
        String label = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS.equals(headlineStyle.actionTextStyle())) {
            label = SmallCapsText.convert(label);
        }
        if (headlineStyle.gradientEnabled()) {
            return gradientText(label, headlineStyle.palette().hqTicketActionStart(),
                    headlineStyle.palette().hqTicketActionEnd(), true);
        }
        return Component.literal(label).withStyle(ChatFormatting.GOLD);
    }

    private static MutableComponent plantageAction(String value, HeadlineStyle headlineStyle) {
        String label = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS.equals(headlineStyle.actionTextStyle())) {
            label = SmallCapsText.convert(label);
        }
        if (headlineStyle.gradientEnabled()) {
            return gradientText(label, headlineStyle.palette().hqPlantageActionStart(),
                    headlineStyle.palette().hqPlantageActionEnd(), true);
        }
        return Component.literal(label).withStyle(ChatFormatting.RED);
    }

    private static MutableComponent plantageDetailText(String value, HeadlineStyle headlineStyle) {
        String text = value == null ? "" : value.trim();
        if (headlineStyle.gradientEnabled()) {
            return gradientText(text, headlineStyle.palette().hqPlantageDetailStart(),
                    headlineStyle.palette().hqPlantageDetailEnd(), true);
        }
        return Component.literal(text).withStyle(ChatFormatting.GOLD);
    }

    private static MutableComponent emergencyAction(
            String value,
            boolean accepted,
            HeadlineStyle headlineStyle
    ) {
        String label = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS.equals(headlineStyle.actionTextStyle())) {
            label = SmallCapsText.convert(label);
        }
        if (headlineStyle.gradientEnabled()) {
            GradientPalette palette = headlineStyle.palette();
            return gradientText(
                    label,
                    accepted ? palette.hqEmergencyAcceptedStart() : palette.hqEmergencyActionStart(),
                    accepted ? palette.hqEmergencyAcceptedEnd() : palette.hqEmergencyActionEnd(),
                    true
            );
        }
        return Component.literal(label).withStyle(
                accepted ? ChatFormatting.GREEN : ChatFormatting.RED,
                ChatFormatting.BOLD
        );
    }

    private static MutableComponent emergencyMessageText(String value, HeadlineStyle headlineStyle) {
        String text = value == null ? "" : value.trim();
        if (headlineStyle.gradientEnabled()) {
            return gradientText(text, headlineStyle.palette().hqEmergencyTextStart(),
                    headlineStyle.palette().hqEmergencyTextEnd(), true);
        }
        return Component.literal(text).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
    }

    private static MutableComponent emergencyDetailText(String value, HeadlineStyle headlineStyle) {
        String text = value == null ? "" : value.trim();
        if (headlineStyle.gradientEnabled()) {
            return gradientText(text, headlineStyle.palette().hqEmergencyDetailStart(),
                    headlineStyle.palette().hqEmergencyDetailEnd(), true);
        }
        return Component.literal(text).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD);
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
                ? gradientText(name, headlineStyle.palette().hqActorStart(),
                        headlineStyle.palette().hqActorEnd(), false)
                : Component.literal(name).withStyle(ChatFormatting.BLUE);
    }

    private static MutableComponent targetName(String value, HeadlineStyle headlineStyle) {
        String name = value == null ? "" : value.trim();
        return headlineStyle.gradientEnabled()
                ? gradientText(name, headlineStyle.palette().hqTargetStart(),
                        headlineStyle.palette().hqTargetEnd(), false)
                : Component.literal(name).withStyle(ChatFormatting.BLUE);
    }

    private static MutableComponent detailText(String value, HeadlineStyle headlineStyle) {
        String text = value == null ? "" : value.trim();
        return headlineStyle.gradientEnabled()
                ? gradientText(text, headlineStyle.palette().hqDetailStart(),
                        headlineStyle.palette().hqDetailEnd(), true)
                : Component.literal(text).withStyle(ChatFormatting.BLUE);
    }

    private static MutableComponent payAction(String value, HeadlineStyle headlineStyle) {
        String label = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS.equals(headlineStyle.actionTextStyle())) {
            label = SmallCapsText.convert(label);
        }
        if (headlineStyle.gradientEnabled()) {
            return gradientText(label, headlineStyle.palette().payActionStart(),
                    headlineStyle.palette().payActionEnd(), true);
        }
        return Component.literal(label).withStyle(ChatFormatting.RED);
    }

    private static MutableComponent payActorName(String value, HeadlineStyle headlineStyle) {
        String name = value == null ? "" : value.trim();
        return gradientText(name, headlineStyle.palette().payActorStart(),
                headlineStyle.palette().payActorEnd(), false);
    }

    private static MutableComponent payTargetName(String value, HeadlineStyle headlineStyle) {
        String name = value == null ? "" : value.trim();
        return gradientText(name, headlineStyle.palette().payTargetStart(),
                headlineStyle.palette().payTargetEnd(), false);
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
                .replaceAll("\\p{Cf}", "")
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

    private record PendingTicket(String actor, String target, String amount, long createdAtMs) {
    }

    record GradientPalette(
            int hqActionStart,
            int hqActionEnd,
            int hqActorStart,
            int hqActorEnd,
            int hqTargetStart,
            int hqTargetEnd,
            int hqDetailStart,
            int hqDetailEnd,
            int hqPositiveStart,
            int hqPositiveEnd,
            int hqTicketActionStart,
            int hqTicketActionEnd,
            int hqTicketDetailStart,
            int hqTicketDetailEnd,
            int hqPlantageActionStart,
            int hqPlantageActionEnd,
            int hqPlantageDetailStart,
            int hqPlantageDetailEnd,
            int hqEmergencyActionStart,
            int hqEmergencyActionEnd,
            int hqEmergencyAcceptedStart,
            int hqEmergencyAcceptedEnd,
            int hqEmergencyTextStart,
            int hqEmergencyTextEnd,
            int hqEmergencyDetailStart,
            int hqEmergencyDetailEnd,
            int payActionStart,
            int payActionEnd,
            int payActorStart,
            int payActorEnd,
            int payTargetStart,
            int payTargetEnd,
            int payOutgoingStart,
            int payOutgoingEnd,
            int payIncomingStart,
            int payIncomingEnd
    ) {
        static GradientPalette defaults() {
            return new GradientPalette(
                    BetterUCConfig.DEFAULT_CHAT_HQ_ACTION_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_HQ_ACTION_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_HQ_ACTOR_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_HQ_ACTOR_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_HQ_TARGET_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_HQ_TARGET_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_HQ_DETAIL_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_HQ_DETAIL_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_HQ_POSITIVE_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_HQ_POSITIVE_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_HQ_TICKET_ACTION_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_HQ_TICKET_ACTION_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_HQ_TICKET_DETAIL_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_HQ_TICKET_DETAIL_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_HQ_PLANTAGE_ACTION_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_HQ_PLANTAGE_ACTION_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_HQ_PLANTAGE_DETAIL_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_HQ_PLANTAGE_DETAIL_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_HQ_EMERGENCY_ACTION_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_HQ_EMERGENCY_ACTION_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_HQ_EMERGENCY_ACCEPTED_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_HQ_EMERGENCY_ACCEPTED_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_HQ_EMERGENCY_TEXT_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_HQ_EMERGENCY_TEXT_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_HQ_EMERGENCY_DETAIL_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_HQ_EMERGENCY_DETAIL_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_PAY_ACTION_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_PAY_ACTION_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_PAY_ACTOR_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_PAY_ACTOR_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_PAY_TARGET_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_PAY_TARGET_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_PAY_OUTGOING_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_PAY_OUTGOING_GRADIENT_END,
                    BetterUCConfig.DEFAULT_CHAT_PAY_INCOMING_GRADIENT_START,
                    BetterUCConfig.DEFAULT_CHAT_PAY_INCOMING_GRADIENT_END
            );
        }

        static GradientPalette configured(BetterUCConfig config) {
            return new GradientPalette(
                    config.chatHqActionGradientStart,
                    config.chatHqActionGradientEnd,
                    config.chatHqActorGradientStart,
                    config.chatHqActorGradientEnd,
                    config.chatHqTargetGradientStart,
                    config.chatHqTargetGradientEnd,
                    config.chatHqDetailGradientStart,
                    config.chatHqDetailGradientEnd,
                    config.chatHqPositiveGradientStart,
                    config.chatHqPositiveGradientEnd,
                    config.chatHqTicketActionGradientStart,
                    config.chatHqTicketActionGradientEnd,
                    config.chatHqTicketDetailGradientStart,
                    config.chatHqTicketDetailGradientEnd,
                    config.chatHqPlantageActionGradientStart,
                    config.chatHqPlantageActionGradientEnd,
                    config.chatHqPlantageDetailGradientStart,
                    config.chatHqPlantageDetailGradientEnd,
                    config.chatHqEmergencyActionGradientStart,
                    config.chatHqEmergencyActionGradientEnd,
                    config.chatHqEmergencyAcceptedGradientStart,
                    config.chatHqEmergencyAcceptedGradientEnd,
                    config.chatHqEmergencyTextGradientStart,
                    config.chatHqEmergencyTextGradientEnd,
                    config.chatHqEmergencyDetailGradientStart,
                    config.chatHqEmergencyDetailGradientEnd,
                    config.chatPayActionGradientStart,
                    config.chatPayActionGradientEnd,
                    config.chatPayActorGradientStart,
                    config.chatPayActorGradientEnd,
                    config.chatPayTargetGradientStart,
                    config.chatPayTargetGradientEnd,
                    config.chatPayOutgoingGradientStart,
                    config.chatPayOutgoingGradientEnd,
                    config.chatPayIncomingGradientStart,
                    config.chatPayIncomingGradientEnd
            );
        }

        GradientPalette withHqAction(int start, int end) {
            return new GradientPalette(
                    start, end, hqActorStart, hqActorEnd, hqTargetStart, hqTargetEnd,
                    hqDetailStart, hqDetailEnd, hqPositiveStart, hqPositiveEnd,
                    hqTicketActionStart, hqTicketActionEnd, hqTicketDetailStart, hqTicketDetailEnd,
                    hqPlantageActionStart, hqPlantageActionEnd, hqPlantageDetailStart, hqPlantageDetailEnd,
                    hqEmergencyActionStart, hqEmergencyActionEnd,
                    hqEmergencyAcceptedStart, hqEmergencyAcceptedEnd,
                    hqEmergencyTextStart, hqEmergencyTextEnd,
                    hqEmergencyDetailStart, hqEmergencyDetailEnd,
                    payActionStart, payActionEnd, payActorStart, payActorEnd, payTargetStart, payTargetEnd,
                    payOutgoingStart, payOutgoingEnd, payIncomingStart, payIncomingEnd
            );
        }

        GradientPalette withPayAction(int start, int end) {
            return new GradientPalette(
                    hqActionStart, hqActionEnd, hqActorStart, hqActorEnd, hqTargetStart, hqTargetEnd,
                    hqDetailStart, hqDetailEnd, hqPositiveStart, hqPositiveEnd,
                    hqTicketActionStart, hqTicketActionEnd, hqTicketDetailStart, hqTicketDetailEnd,
                    hqPlantageActionStart, hqPlantageActionEnd, hqPlantageDetailStart, hqPlantageDetailEnd,
                    hqEmergencyActionStart, hqEmergencyActionEnd,
                    hqEmergencyAcceptedStart, hqEmergencyAcceptedEnd,
                    hqEmergencyTextStart, hqEmergencyTextEnd,
                    hqEmergencyDetailStart, hqEmergencyDetailEnd,
                    start, end, payActorStart, payActorEnd, payTargetStart, payTargetEnd,
                    payOutgoingStart, payOutgoingEnd, payIncomingStart, payIncomingEnd
            );
        }

        GradientPalette withPayOutgoing(int start, int end) {
            return new GradientPalette(
                    hqActionStart, hqActionEnd, hqActorStart, hqActorEnd, hqTargetStart, hqTargetEnd,
                    hqDetailStart, hqDetailEnd, hqPositiveStart, hqPositiveEnd,
                    hqTicketActionStart, hqTicketActionEnd, hqTicketDetailStart, hqTicketDetailEnd,
                    hqPlantageActionStart, hqPlantageActionEnd, hqPlantageDetailStart, hqPlantageDetailEnd,
                    hqEmergencyActionStart, hqEmergencyActionEnd,
                    hqEmergencyAcceptedStart, hqEmergencyAcceptedEnd,
                    hqEmergencyTextStart, hqEmergencyTextEnd,
                    hqEmergencyDetailStart, hqEmergencyDetailEnd,
                    payActionStart, payActionEnd, payActorStart, payActorEnd, payTargetStart, payTargetEnd,
                    start, end, payIncomingStart, payIncomingEnd
            );
        }
    }

    private record HeadlineStyle(
            String actionTextStyle,
            String separatorStyle,
            boolean gradientEnabled,
            GradientPalette palette
    ) {
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
        private final boolean hq;

        private Result(boolean cancelOriginal, List<Component> replacementMessages, boolean reinforcement, boolean hq) {
            this.cancelOriginal = cancelOriginal;
            this.replacementMessages = replacementMessages == null ? List.of() : replacementMessages;
            this.reinforcement = reinforcement;
            this.hq = hq;
        }

        public static Result suppress() {
            return new Result(true, List.of(), false, false);
        }

        public static Result replace(List<Component> replacementMessages) {
            return new Result(true, replacementMessages, false, false);
        }

        static Result replaceHq(List<Component> replacementMessages) {
            return new Result(true, replacementMessages, false, true);
        }

        static Result replaceReinforcement(List<Component> replacementMessages) {
            return new Result(true, replacementMessages, true, false);
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

        public boolean hq() {
            return hq;
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
            return new Result(cancelOriginal, List.copyOf(interactiveMessages), reinforcement, hq);
        }
    }
}
