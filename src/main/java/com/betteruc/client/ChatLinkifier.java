package com.betteruc.client;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class ChatLinkifier {

    private static final int MAX_URL_LENGTH = 2048;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}_])((?:https?://|www\\.)[^\\s<>\"']+)"
    );

    private ChatLinkifier() {
    }

    public static Component linkify(Component message, boolean enabled, boolean highlight) {
        if (!enabled || message == null || message.getString().isBlank()) {
            return message;
        }

        MutableComponent result = Component.empty();
        boolean[] changed = {false};
        message.visit((style, text) -> {
            appendSegment(result, style, text, highlight, changed);
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? result : message;
    }

    private static void appendSegment(
            MutableComponent target,
            Style style,
            String text,
            boolean highlight,
            boolean[] changed
    ) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (style.getClickEvent() != null) {
            target.append(Component.literal(text).setStyle(style));
            return;
        }

        Matcher matcher = URL_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            int urlStart = matcher.start(1);
            String candidate = matcher.group(1);
            int urlLength = clickableLength(candidate);
            URI uri = urlLength <= 0 ? null : toWebUri(candidate.substring(0, urlLength));
            if (uri == null) {
                continue;
            }

            if (urlStart > cursor) {
                target.append(Component.literal(text.substring(cursor, urlStart)).setStyle(style));
            }

            String visibleUrl = candidate.substring(0, urlLength);
            Style linkStyle = style.withClickEvent(new ClickEvent.OpenUrl(uri));
            if (style.getHoverEvent() == null) {
                linkStyle = linkStyle.withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("Link öffnen: " + visibleUrl)
                ));
            }
            if (highlight) {
                linkStyle = linkStyle.withColor(ChatFormatting.AQUA).withUnderlined(true);
            }
            target.append(Component.literal(visibleUrl).setStyle(linkStyle));

            int clickableEnd = urlStart + urlLength;
            int matchEnd = matcher.end(1);
            if (clickableEnd < matchEnd) {
                target.append(Component.literal(text.substring(clickableEnd, matchEnd)).setStyle(style));
            }
            cursor = matchEnd;
            changed[0] = true;
        }

        if (cursor < text.length()) {
            target.append(Component.literal(text.substring(cursor)).setStyle(style));
        }
    }

    private static int clickableLength(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return 0;
        }
        int end = Math.min(candidate.length(), MAX_URL_LENGTH);
        while (end > 0) {
            char last = candidate.charAt(end - 1);
            if (last == '.' || last == ',' || last == ';' || last == ':'
                    || last == '!' || last == '?' || last == ']' || last == '}') {
                end--;
                continue;
            }
            if (last == ')' && hasUnmatchedClosingParenthesis(candidate, end)) {
                end--;
                continue;
            }
            break;
        }
        return end;
    }

    private static boolean hasUnmatchedClosingParenthesis(String value, int end) {
        int balance = 0;
        for (int index = 0; index < end; index++) {
            char current = value.charAt(index);
            if (current == '(') {
                balance++;
            } else if (current == ')') {
                balance--;
            }
        }
        return balance < 0;
    }

    private static URI toWebUri(String visibleUrl) {
        if (visibleUrl == null || visibleUrl.isBlank() || visibleUrl.length() > MAX_URL_LENGTH) {
            return null;
        }
        String normalized = visibleUrl.toLowerCase(Locale.ROOT).startsWith("www.")
                ? "https://" + visibleUrl
                : visibleUrl;
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
