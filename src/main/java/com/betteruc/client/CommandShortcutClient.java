package com.betteruc.client;

import com.betteruc.ServerGate;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CommandShortcutClient {

    private static final Pattern PLAYER_NAME_PATTERN =
            Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int MAX_ATTEMPTED_MURDER_PLAYERS = 6;

    private CommandShortcutClient() {
    }

    public static String rewriteOutgoingCommand(String input) {
        if (input == null || !ServerGate.isAllowedServer(Minecraft.getInstance())) {
            return input;
        }

        boolean slashPrefixed = input.startsWith("/");
        String command = (slashPrefixed ? input.substring(1) : input).trim();
        String[] parts = command.split("\\s+");
        if (parts.length < 2 || !parts[0].equalsIgnoreCase("vm")) {
            return lowercaseCommandName(input);
        }

        StringBuilder players = new StringBuilder();
        for (int index = 1; index < parts.length; index++) {
            if (index > MAX_ATTEMPTED_MURDER_PLAYERS
                    || !PLAYER_NAME_PATTERN.matcher(parts[index]).matches()) {
                return lowercaseCommandName(input);
            }
            if (!players.isEmpty()) {
                players.append(' ');
            }
            players.append(parts[index]);
        }

        return lowercaseCommandName(
                (slashPrefixed ? "/" : "") + buildAttemptedMurderServerCommand(players.toString())
        );
    }

    static String lowercaseCommandName(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        int commandStart = input.charAt(0) == '/' ? 1 : 0;
        int commandEnd = commandStart;
        while (commandEnd < input.length() && !Character.isWhitespace(input.charAt(commandEnd))) {
            commandEnd++;
        }
        if (commandEnd == commandStart) {
            return input;
        }

        String commandName = input.substring(commandStart, commandEnd);
        String lowercaseName = commandName.toLowerCase(Locale.ROOT);
        if (commandName.equals(lowercaseName)) {
            return input;
        }

        return input.substring(0, commandStart)
                + lowercaseName
                + input.substring(commandEnd);
    }

    public static String buildAttemptedMurderServerCommand(String playerInput) {
        if (playerInput == null || playerInput.isBlank()) {
            return null;
        }

        String[] players = playerInput.trim().split("\\s+");
        if (players.length > MAX_ATTEMPTED_MURDER_PLAYERS) {
            return null;
        }
        for (String player : players) {
            if (!PLAYER_NAME_PATTERN.matcher(player).matches()) {
                return null;
            }
        }

        return "asu " + String.join(" ", players) + " Versuchter Mord";
    }
}
