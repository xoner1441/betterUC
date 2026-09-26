package com.betteruc.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Replaces readable chat shortcodes locally while retaining Minecraft text actions and styling. */
public final class ChatEmojiFormatter {

    private static final List<Emoji> EMOJI_LIST = List.of(
            new Emoji("heart", "❤", "Herz", ChatFormatting.RED),
            new Emoji("star", "★", "Stern", ChatFormatting.YELLOW),
            new Emoji("smile", "☺", "Lächeln", ChatFormatting.YELLOW),
            new Emoji("sad", "☹", "Traurig", ChatFormatting.GRAY),
            new Emoji("skull", "☠", "Totenkopf", ChatFormatting.DARK_GRAY),
            new Emoji("check", "✔", "Bestätigt", ChatFormatting.GREEN),
            new Emoji("cross", "✖", "Abgelehnt", ChatFormatting.RED),
            new Emoji("warning", "⚠", "Warnung", ChatFormatting.GOLD),
            new Emoji("sparkles", "✦", "Funkeln", ChatFormatting.LIGHT_PURPLE),
            new Emoji("diamond", "◆", "Diamant", ChatFormatting.AQUA),
            new Emoji("music", "♪", "Musik", ChatFormatting.LIGHT_PURPLE),
            new Emoji("sun", "☀", "Sonne", ChatFormatting.YELLOW),
            new Emoji("umbrella", "☂", "Regenschirm", ChatFormatting.AQUA),
            new Emoji("coffee", "☕", "Kaffee", ChatFormatting.GOLD),
            new Emoji("swords", "⚔", "Schwerter", ChatFormatting.GRAY),
            new Emoji("crown", "♛", "Krone", ChatFormatting.GOLD),
            new Emoji("plane", "✈", "Flugzeug", ChatFormatting.WHITE),
            new Emoji("mail", "✉", "Nachricht", ChatFormatting.AQUA),
            new Emoji("flower", "✿", "Blume", ChatFormatting.LIGHT_PURPLE),
            new Emoji("lightning", "⚡", "Blitz", ChatFormatting.YELLOW)
    );
    private static final Map<String, Emoji> EMOJIS_BY_SHORTCODE = EMOJI_LIST.stream()
            .collect(Collectors.toUnmodifiableMap(Emoji::shortcode, Function.identity()));
    private static final Map<String, Emoji> EMOJIS_BY_SYMBOL = EMOJI_LIST.stream()
            .collect(Collectors.toUnmodifiableMap(Emoji::symbol, Function.identity()));
    private static final Pattern EMOJI_PATTERN = Pattern.compile(
            ":([a-z][a-z0-9_-]{1,20}):|(" + EMOJI_LIST.stream()
                    .map(Emoji::symbol)
                    .map(Pattern::quote)
                    .collect(Collectors.joining("|")) + ")",
            Pattern.CASE_INSENSITIVE
    );

    private ChatEmojiFormatter() {
    }

    public static List<PickerEntry> pickerEntries() {
        return EMOJI_LIST.stream()
                .map(emoji -> new PickerEntry(emoji.symbol(), emoji.label(), emoji.color()))
                .toList();
    }

    public static Component replace(Component message, boolean enabled) {
        if (!enabled || message == null || message.getString().isBlank()) {
            return message;
        }

        MutableComponent result = Component.empty();
        boolean[] changed = {false};
        message.visit((style, text) -> {
            appendSegment(result, style, text, changed);
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? result : message;
    }

    private static void appendSegment(
            MutableComponent target,
            Style style,
            String text,
            boolean[] changed
    ) {
        if (text == null || text.isEmpty()) return;

        Matcher matcher = EMOJI_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            Emoji emoji = matcher.group(1) == null
                    ? EMOJIS_BY_SYMBOL.get(matcher.group(2))
                    : EMOJIS_BY_SHORTCODE.get(matcher.group(1).toLowerCase(Locale.ROOT));
            if (emoji == null) continue;

            if (matcher.start() > cursor) {
                target.append(Component.literal(text.substring(cursor, matcher.start())).setStyle(style));
            }
            target.append(Component.literal(emoji.symbol()).setStyle(style.withColor(emoji.color())));
            cursor = matcher.end();
            changed[0] = true;
        }

        if (cursor < text.length()) {
            target.append(Component.literal(text.substring(cursor)).setStyle(style));
        }
    }

    private record Emoji(String shortcode, String symbol, String label, ChatFormatting color) {
    }

    public record PickerEntry(String symbol, String label, ChatFormatting color) {
    }
}
