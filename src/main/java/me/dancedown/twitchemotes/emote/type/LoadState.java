package me.dancedown.twitchemotes.emote.type;

import org.jetbrains.annotations.Nullable;

public record LoadState(boolean globalsLoaded, @Nullable String channelLoaded) {}

