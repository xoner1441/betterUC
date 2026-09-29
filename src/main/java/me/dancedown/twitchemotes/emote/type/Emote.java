package me.dancedown.twitchemotes.emote.type;

public record Emote(
        String id,
        String name,
        String url,
        EmoteFormat format,
        boolean overlay,
        int scale) {}

