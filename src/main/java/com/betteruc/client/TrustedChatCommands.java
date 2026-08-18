package com.betteruc.client;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class TrustedChatCommands {

    private static final int MAX_COMMANDS = 256;
    private static final long TRUST_TTL_MS = 30L * 60L * 1000L;
    private static final Map<String, Long> TRUSTED_UNTIL = new LinkedHashMap<>();

    private TrustedChatCommands() {
    }

    public static synchronized void remember(Component message) {
        if (message == null) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + TRUST_TTL_MS;
        message.visit((style, text) -> {
            if (style.getClickEvent() instanceof ClickEvent.RunCommand runCommand) {
                String command = normalize(runCommand.command());
                if (!command.isEmpty()) {
                    TRUSTED_UNTIL.remove(command);
                    TRUSTED_UNTIL.put(command, expiresAt);
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        prune(System.currentTimeMillis());
    }

    public static synchronized boolean isTrusted(String command) {
        long now = System.currentTimeMillis();
        prune(now);
        Long expiresAt = TRUSTED_UNTIL.get(normalize(command));
        return expiresAt != null && expiresAt >= now;
    }

    public static synchronized void clear() {
        TRUSTED_UNTIL.clear();
    }

    private static void prune(long now) {
        TRUSTED_UNTIL.entrySet().removeIf(entry -> entry.getValue() < now);
        while (TRUSTED_UNTIL.size() > MAX_COMMANDS) {
            Iterator<String> iterator = TRUSTED_UNTIL.keySet().iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private static String normalize(String command) {
        if (command == null) {
            return "";
        }
        String normalized = command.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }
}
