package com.betteruc.mixin;

import com.betteruc.ServerGate;
import com.betteruc.PlayerNameUtil;
import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Mixin(value = PlayerListHud.class, priority = 2000)
public abstract class PlayerListHudMixin {

    private static final String UC_TAG = "[UC]";
    private static final String R_TAG = "[R]";
    private static final String B_TAG = "[B]";
    private static final Pattern UC_PREFIX_PATTERN = Pattern.compile("(^|[^A-Za-z0-9])UC(\\]|\\||\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern R_PREFIX_PATTERN = Pattern.compile("(^|[^A-Za-z0-9])R(\\]|\\||\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern B_PREFIX_PATTERN = Pattern.compile("(^|[^A-Za-z0-9])B(\\]|\\||\\s|$)", Pattern.CASE_INSENSITIVE);

    @Inject(method = "collectPlayerEntries", at = @At("RETURN"), cancellable = true, require = 0)
    private void sortPlayerListGroups(CallbackInfoReturnable<List<PlayerListEntry>> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ServerGate.isAllowedServer(client)) return;
        List<PlayerListEntry> source = cir.getReturnValue();
        if (source == null || source.isEmpty()) return;

        int targetSize = source.size();

        // Keep vanilla entry selection size (including cap) and only reorder it.
        // Missing faction entries are merged in first so they are less likely to disappear.
        List<PlayerListEntry> sorted = new ArrayList<>(source);
        mergeMissingFactionEntries(client, sorted, targetSize);

        // Stable sort by explicit group order:
        // dark blue > light blue > dark red > [UC] > [R] > [B] > selected faction > rest.
        Map<PlayerListEntry, SortInfo> sortInfoByEntry = new IdentityHashMap<>(sorted.size());
        for (PlayerListEntry entry : sorted) {
            sortInfoByEntry.put(entry, buildSortInfo(entry));
        }
        sorted.sort((a, b) -> compareForTabOrder(a, b, sortInfoByEntry));
        if (sorted.size() > targetSize) {
            sorted = new ArrayList<>(sorted.subList(0, targetSize));
        }
        cir.setReturnValue(sorted);
    }

    private void mergeMissingFactionEntries(MinecraftClient client, List<PlayerListEntry> working, int targetSize) {
        if (client == null || client.getNetworkHandler() == null) return;
        if (working == null || working.isEmpty() || targetSize <= 0) return;

        var listedEntries = client.getNetworkHandler().getListedPlayerListEntries();
        if (listedEntries == null || listedEntries.isEmpty()) return;

        Set<String> presentKeys = new HashSet<>(Math.max(16, working.size() * 2));
        for (PlayerListEntry entry : working) {
            String key = entryKey(entry);
            if (key != null) {
                presentKeys.add(key);
            }
        }

        for (PlayerListEntry candidate : listedEntries) {
            if (candidate == null) continue;
            String key = entryKey(candidate);
            if (key == null || presentKeys.contains(key)) continue;
            if (!isSelectedFactionEntry(candidate)) continue;

            working.add(candidate);
            presentKeys.add(key);
        }
    }

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void colorFactionNamesInTab(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (!ServerGate.isAllowedServer(MinecraftClient.getInstance())) return;
        if (entry == null) return;

        String profileName = getProfileName(entry);
        if (profileName == null || !BetterUCConfig.shouldColorFactionInTab(profileName)) return;
        if (isColoredUcStyledEntry(entry)) return;

        Text current = cir.getReturnValue();
        if (current == null) return;

        int factionColor = BetterUCConfig.INSTANCE.factionColor;
        Text recolored = current.copy()
                .styled(style -> style.withColor(TextColor.fromRgb(factionColor)));
        cir.setReturnValue(recolored);
    }

    private SortInfo buildSortInfo(PlayerListEntry entry) {
        return new SortInfo(
                groupPriority(entry),
                safeName(entry).toLowerCase(Locale.ROOT),
                entry == null ? 0 : entry.getLatency()
        );
    }

    private int compareForTabOrder(PlayerListEntry a, PlayerListEntry b, Map<PlayerListEntry, SortInfo> sortInfoByEntry) {
        SortInfo ai = sortInfoByEntry.get(a);
        SortInfo bi = sortInfoByEntry.get(b);
        if (ai == null) ai = buildSortInfo(a);
        if (bi == null) bi = buildSortInfo(b);

        if (ai.priority != bi.priority) return Integer.compare(ai.priority, bi.priority);

        // Deterministic within same group to avoid unstable-looking mixes.
        int byName = ai.nameLower.compareTo(bi.nameLower);
        if (byName != 0) return byName;
        return Integer.compare(ai.latency, bi.latency);
    }

    private static final class SortInfo {
        private final int priority;
        private final String nameLower;
        private final int latency;

        private SortInfo(int priority, String nameLower, int latency) {
            this.priority = priority;
            this.nameLower = nameLower == null ? "" : nameLower;
            this.latency = latency;
        }
    }

    private int groupPriority(PlayerListEntry entry) {
        // Smaller number = shown earlier in tab list.
        // Requested order:
        // dark blue > light blue > dark red > colored [UC] prefix > [R] > [B] > selected faction > rest.
        boolean isUc = isUcPlayer(entry);
        boolean isR = !isUc && hasRMarker(entry);
        boolean isB = !isUc && !isR && hasBMarker(entry);
        boolean hasExplicitPrefixGroup = isUc || isR || isB;

        Formatting color = getTeamColor(entry);
        if (!hasExplicitPrefixGroup) {
            if (color == Formatting.DARK_BLUE) return 0;
            if (color == Formatting.BLUE || color == Formatting.AQUA) return 1;
            if (isRedFormatting(color)) return 2;

            // Fallback for edge-cases where no team color is set but display text is colored.
            if (isDarkBlueEntry(entry)) return 0;
            if (isLightBlueEntry(entry)) return 1;
            if (isRedEntry(entry)) return 2;
        }

        if (isUc) return 3;
        if (isR) return 4;
        if (isB) return 5;
        if (isSelectedFactionEntry(entry)) return 6;
        return 7;
    }

    private boolean isSelectedFactionEntry(PlayerListEntry entry) {
        String profileName = getProfileName(entry);
        return profileName != null && BetterUCConfig.shouldSortAsSelectedFactionInTab(profileName);
    }

    private boolean isDarkBlueEntry(PlayerListEntry entry) {
        if (entry == null) return false;
        if (isSelectedFactionEntry(entry)) return false;
        return hasDominantBlueDisplayColor(entry, true);
    }

    private boolean isLightBlueEntry(PlayerListEntry entry) {
        if (entry == null) return false;
        if (isSelectedFactionEntry(entry)) return false;
        return hasDominantBlueDisplayColor(entry, false);
    }

    private boolean isRedEntry(PlayerListEntry entry) {
        if (entry == null) return false;
        if (isSelectedFactionEntry(entry)) return false;

        Integer rgb = getEffectiveDisplayRgb(entry);
        return rgb != null && isRedLike(rgb);
    }

    private Formatting getTeamColor(PlayerListEntry entry) {
        Team team = entry == null ? null : entry.getScoreboardTeam();
        if (team == null) return null;

        try {
            return team.getColor();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isRedFormatting(Formatting formatting) {
        return formatting == Formatting.RED || formatting == Formatting.DARK_RED;
    }

    private boolean hasDominantBlueDisplayColor(PlayerListEntry entry, boolean darkBlue) {
        Integer rgb = getEffectiveDisplayRgb(entry);
        if (rgb == null) return false;

        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        if (darkBlue) {
            return b >= 120 && r <= 70 && g <= 90 && b >= r + 50 && b >= g + 30;
        }
        return b >= 170 && r >= 30 && g >= 30 && r <= 170 && g <= 170 && b >= r + 40 && b >= g + 40;
    }

    private Integer getEffectiveDisplayRgb(PlayerListEntry entry) {
        if (entry == null) return null;

        Text display = entry.getDisplayName();
        if (display != null) {
            TextColor color = findFirstColor(display);
            if (color != null) return color.getRgb();
        }

        Team team = entry.getScoreboardTeam();
        if (team != null) {
            Text prefix = team.getPrefix();
            if (prefix != null) {
                TextColor color = findFirstColor(prefix);
                if (color != null) return color.getRgb();
            }
        }

        return null;
    }

    private TextColor findFirstColor(Text text) {
        if (text == null) return null;
        TextColor own = text.getStyle().getColor();
        if (own != null) return own;

        for (Text sibling : text.getSiblings()) {
            TextColor child = findFirstColor(sibling);
            if (child != null) return child;
        }
        return null;
    }

    private boolean isRedLike(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return r >= 140 && g <= 120 && b <= 120 && r >= g + 25 && r >= b + 25;
    }

    private boolean isBlueLike(TextColor color) {
        if (color == null) return false;
        int rgb = color.getRgb();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return b >= 120 && b >= r + 30 && b >= g + 20;
    }

    private boolean isUcPlayer(PlayerListEntry entry) {
        if (entry == null) return false;

        Team team = entry.getScoreboardTeam();
        if (team != null) {
            Text prefix = team.getPrefix();
            if (prefix != null && hasUcTag(prefix.getString())) {
                return true;
            }
        }

        Text display = entry.getDisplayName();
        if (display != null) {
            String plain = display.getString();
            if (hasUcTag(plain)) return true;
        }

        return false;
    }

    private boolean isBlueUcPlayer(PlayerListEntry entry) {
        if (!isUcPlayer(entry)) return false;

        Formatting teamColor = getTeamColor(entry);
        if (teamColor == Formatting.DARK_BLUE || teamColor == Formatting.BLUE || teamColor == Formatting.AQUA) {
            return true;
        }

        Team team = entry == null ? null : entry.getScoreboardTeam();
        if (team != null) {
            Text prefix = team.getPrefix();
            if (prefix != null && hasUcTag(prefix.getString()) && isBlueLike(findFirstColor(prefix))) {
                return true;
            }
        }

        Text display = entry == null ? null : entry.getDisplayName();
        return display != null && hasUcTag(display.getString()) && isBlueLike(findFirstColor(display));
    }

    private boolean isColoredUcStyledEntry(PlayerListEntry entry) {
        if (entry == null || !isUcPlayer(entry)) return false;

        Formatting teamColor = getTeamColor(entry);
        if (teamColor == Formatting.DARK_BLUE
                || teamColor == Formatting.BLUE
                || teamColor == Formatting.AQUA
                || teamColor == Formatting.RED
                || teamColor == Formatting.DARK_RED) {
            return true;
        }

        Team team = entry.getScoreboardTeam();
        if (team != null) {
            Text prefix = team.getPrefix();
            if (prefix != null && hasLeadingUcTag(prefix.getString())) {
                if (hasBlueOrRedColor(prefix)) {
                    return true;
                }
            }
        }

        Text display = entry.getDisplayName();
        if (display != null && hasLeadingUcTag(display.getString())) {
            return hasBlueOrRedColor(display);
        }

        return false;
    }

    private boolean hasBlueOrRedColor(Text text) {
        if (text == null) return false;
        TextColor own = text.getStyle().getColor();
        if (own != null && (isBlueLike(own) || isRedLike(own.getRgb()))) {
            return true;
        }
        for (Text sibling : text.getSiblings()) {
            if (hasBlueOrRedColor(sibling)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRPlayer(PlayerListEntry entry) {
        if (entry == null) return false;
        if (isUcPlayer(entry)) return false;
        return hasRMarker(entry);
    }

    private boolean hasRMarker(PlayerListEntry entry) {
        if (entry == null) return false;

        Team team = entry.getScoreboardTeam();
        if (team != null) {
            String teamName = team.getName();
            if (teamName != null) {
                String upper = teamName.toUpperCase(Locale.ROOT);
                if ("R".equals(upper) || upper.contains(R_TAG)) {
                    return true;
                }
            }

            Text prefix = team.getPrefix();
            if (prefix != null) {
                String prefixPlain = prefix.getString();
                if (hasRTag(prefixPlain)) {
                    return true;
                }
            }
        }

        Text display = entry.getDisplayName();
        if (display != null) {
            String plain = display.getString();
            return hasRTag(plain);
        }

        return false;
    }

    private boolean isBPlayer(PlayerListEntry entry) {
        if (entry == null) return false;
        if (isUcPlayer(entry) || isRPlayer(entry)) return false;
        return hasBMarker(entry);
    }

    private boolean hasBMarker(PlayerListEntry entry) {
        if (entry == null) return false;

        Team team = entry.getScoreboardTeam();
        if (team != null) {
            String teamName = team.getName();
            if (teamName != null) {
                String upper = teamName.toUpperCase(Locale.ROOT);
                if ("B".equals(upper) || upper.contains(B_TAG)) {
                    return true;
                }
            }

            Text prefix = team.getPrefix();
            if (prefix != null) {
                String prefixPlain = prefix.getString();
                if (hasBTag(prefixPlain)) {
                    return true;
                }
            }
        }

        Text display = entry.getDisplayName();
        if (display != null) {
            String plain = display.getString();
            return hasBTag(plain);
        }

        return false;
    }

    private boolean hasUcTag(String plain) {
        if (plain == null || plain.isBlank()) return false;
        String normalized = plain.replace('\u00A0', ' ').trim();
        if (normalized.startsWith(UC_TAG) || normalized.contains(" " + UC_TAG)) return true;
        return UC_PREFIX_PATTERN.matcher(normalized).find();
    }

    private boolean hasLeadingUcTag(String plain) {
        if (plain == null || plain.isBlank()) return false;
        String normalized = plain
                .replace('\u00A0', ' ')
                .replace("|", "]")
                .trim()
                .toUpperCase(Locale.ROOT);

        return normalized.startsWith("[UC]") || normalized.startsWith("UC]");
    }

    private boolean hasRTag(String plain) {
        if (plain == null || plain.isBlank()) return false;
        String normalized = plain.replace('\u00A0', ' ').trim();
        if (normalized.startsWith(R_TAG) || normalized.contains(" " + R_TAG)) return true;
        return R_PREFIX_PATTERN.matcher(normalized).find();
    }

    private boolean hasBTag(String plain) {
        if (plain == null || plain.isBlank()) return false;
        String normalized = plain.replace('\u00A0', ' ').trim();
        if (normalized.startsWith(B_TAG) || normalized.contains(" " + B_TAG)) return true;
        return B_PREFIX_PATTERN.matcher(normalized).find();
    }

    private String safeName(PlayerListEntry entry) {
        String profile = getProfileName(entry);
        if (profile != null && !profile.isBlank()) return profile;
        if (entry != null && entry.getDisplayName() != null) {
            String plain = entry.getDisplayName().getString();
            if (plain != null && !plain.isBlank()) return plain;
        }
        return "";
    }

    private String getProfileName(PlayerListEntry entry) {
        if (entry == null || entry.getProfile() == null) return null;
        return PlayerNameUtil.resolveProfileName(entry.getProfile());
    }

    private String entryKey(PlayerListEntry entry) {
        if (entry == null || entry.getProfile() == null) return null;

        String profileName = getProfileName(entry);
        if (profileName != null && !profileName.isBlank()) {
            return "name:" + profileName.toLowerCase(Locale.ROOT);
        }
        String fallback = safeName(entry);
        if (!fallback.isBlank()) {
            return "fallback:" + fallback.toLowerCase(Locale.ROOT);
        }
        return null;
    }
}
