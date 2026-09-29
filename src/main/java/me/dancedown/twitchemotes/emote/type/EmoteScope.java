package me.dancedown.twitchemotes.emote.type;

public enum EmoteScope {
    GLOBAL("global"), CHANNEL("channel");

    final String displayName;
    EmoteScope(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

