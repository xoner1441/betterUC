package me.dancedown.twitchemotes.network;

public enum ProviderType {
    TWITCH("Twitch"),
    FFZ("FrankerFaceZ"),
    BTTV("BetterTTV"),
    STV("7tv");

    final String displayName;
    ProviderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

