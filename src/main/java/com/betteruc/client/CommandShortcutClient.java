package com.betteruc.client;

import com.betteruc.ServerGate;
import net.minecraft.client.Minecraft;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandShortcutClient {

    private static final Pattern ATTEMPTED_MURDER_SHORTCUT_PATTERN =
            Pattern.compile("^vm\\s+([A-Za-z0-9_]{1,16})\\s*$", Pattern.CASE_INSENSITIVE);

    private CommandShortcutClient() {
    }

    public static String rewriteOutgoingCommand(String input) {
        if (input == null || !ServerGate.isAllowedServer(Minecraft.getInstance())) {
            return input;
        }

        boolean slashPrefixed = input.startsWith("/");
        String command = slashPrefixed ? input.substring(1) : input;
        Matcher matcher = ATTEMPTED_MURDER_SHORTCUT_PATTERN.matcher(command);
        if (!matcher.matches()) {
            return input;
        }

        return (slashPrefixed ? "/" : "")
                + "asu "
                + matcher.group(1)
                + " Versuchter Mord";
    }
}
