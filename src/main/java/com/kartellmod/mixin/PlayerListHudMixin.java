package com.kartellmod.mixin;

import com.kartellmod.ServerGate;
import com.kartellmod.PlayerNameUtil;
import com.kartellmod.config.KartellConfig;
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
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Mixin(value = PlayerListHud.class, priority = 2000)
public abstract class PlayerListHudMixin {

    private static final String UC_TAG = "[UC]";
    private static final String R_TAG = "[R]";
    private static final Pattern UC_PREFIX_PATTERN = Pattern.compile("(^|[^A-Za-z0-9])UC(\\]|\\||\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern R_PREFIX_PATTERN = Pattern.compile("(^|[^A-Za-z0-9])R(\\]|\\||\\s|$)", Pattern.CASE_INSENSITIVE);

    @Inject(method = "collectPlayerEntries", at = @At("RETURN"), cancellable = true, require = 0)
    private void sortPlayerListGroups(CallbackInfoReturnable<List<PlayerListEntry>> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ServerGate.isAllowedServer(client)) return;
        List<PlayerListEntry> original = cir.getReturnValue();
        if ((original == null || original.isEmpty())
                && (client == null || client.getNetworkHandler() == null)) {
            return;
        }

        // Vanilla limits this list to 80 entries. We intentionally rebuild from the
        // full listed player set so all online players can appear and be recolored.
        List<PlayerListEntry> source = original;
        if (client != null && client.getNetworkHandler() != null) {
            List<PlayerListEntry> listed = new ArrayList<>(client.getNetworkHandler().getListedPlayerListEntries());
            if (!listed.isEmpty()) {
                source = listed;
            }
        }
        if (source == null || source.isEmpty()) return;

        List<PlayerListEntry> sorted = new ArrayList<>(source);
        // Stable sort by explicit group order:
        // dark blue > light blue > red > [UC] > [R] > faction(green) > rest(white).
        sorted.sort((a, b) -> compareForTabOrder(a, b));
        cir.setReturnValue(sorted);
    }

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void colorFactionNamesInTab(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (!ServerGate.isAllowedServer(MinecraftClient.getInstance())) return;
        if (entry == null) return;

        String profileName = getProfileName(entry);
        if (profileName == null || !KartellConfig.isFaction(profileName)) return;
        if (isColoredUcStyledEntry(entry)) return;

        Text current = cir.getReturnValue();
        if (current == null) return;

        int factionColor = KartellConfig.INSTANCE.factionColor;
        Text recolored = current.copy()
                .styled(style -> style.withColor(TextColor.fromRgb(factionColor)));
        cir.setReturnValue(recolored);
    }

    private int compareForTabOrder(PlayerListEntry a, PlayerListEntry b) {
        int pa = groupPriority(a);
        int pb = groupPriority(b);
        if (pa != pb) return Integer.compare(pa, pb);

        // Within dark blue / blue / red groups, [UC] players should be listed first.
        if (pa == 0 || pa == 1 || pa == 2) {
            int ucA = isUcPlayer(a) ? 0 : 1;
            int ucB = isUcPlayer(b) ? 0 : 1;
            if (ucA != ucB) return Integer.compare(ucA, ucB);
        }

        // Deterministic within same group to avoid unstable-looking mixes.
        String an = safeName(a).toLowerCase(Locale.ROOT);
        String bn = safeName(b).toLowerCase(Locale.ROOT);
        int byName = an.compareTo(bn);
        if (byName != 0) return byName;
        return Integer.compare(a == null ? 0 : a.getLatency(), b == null ? 0 : b.getLatency());
    }

    private int groupPriority(PlayerListEntry entry) {
        // Smaller number = shown earlier in tab list.
        // Requested order:
        // dark blue > light blue > red > [UC] > [R] > faction > rest
        // Special rules:
        // - [UC]/[R] players that are blue/red stay in their color groups.
        // - Faction [UC] entries that are not blue stay in faction group.

        Formatting color = getTeamColor(entry);
        if (color == Formatting.DARK_BLUE) return 0;
        if (color == Formatting.BLUE || color == Formatting.AQUA) return 1;
        if (isRedFormatting(color)) return 2;

        // Fallback for edge-cases where no team color is set but display text is colored.
        if (isDarkBlueEntry(entry)) return 0;
        if (isLightBlueEntry(entry)) return 1;
        if (isRedEntry(entry)) return 2;

        if (isUcPlayer(entry)) {
            if (isFactionEntry(entry) && !isColoredUcStyledEntry(entry)) return 5;
            return 3;
        }
        if (isRPlayer(entry)) return 4;
        if (isFactionEntry(entry)) return 5;
        return 6;
    }

    private boolean isFactionEntry(PlayerListEntry entry) {
        String profileName = getProfileName(entry);
        return profileName != null && KartellConfig.isFaction(profileName);
    }

    private boolean isDarkBlueEntry(PlayerListEntry entry) {
        if (entry == null) return false;
        if (isFactionEntry(entry)) return false;
        return hasDominantBlueDisplayColor(entry, true);
    }

    private boolean isLightBlueEntry(PlayerListEntry entry) {
        if (entry == null) return false;
        if (isFactionEntry(entry)) return false;
        return hasDominantBlueDisplayColor(entry, false);
    }

    private boolean isRedEntry(PlayerListEntry entry) {
        if (entry == null) return false;
        if (isFactionEntry(entry)) return false;

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
        Text display = ((PlayerListHud) (Object) this).getPlayerName(entry);
        if (display == null) return null;
        TextColor color = findFirstColor(display);
        return color == null ? null : color.getRgb();
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
}
