package me.dancedown.twitchemotes.config;

import me.dancedown.twitchemotes.network.ProviderType;

/** Lightweight configuration facade backed by the betterUC chat-emote toggle. */
public final class TwitchEmotesConfig {
    public String twitchDisplayName;
    public String twitchUserName;
    public String twitchChannelName;
    public String twitchUserId;
    public String twitchChannelId;
    public String twitchClientId;
    public String twitchOAuthToken;
    public volatile boolean enabled = true;
    public boolean loadTwitch = true;
    public boolean load7TV = true;
    public boolean loadBTTV = true;
    public boolean loadFFZ = true;
    public boolean includeUnlisted = false;
    public int preferredQuality = 2;
    public boolean animateEmotes = true;
    public boolean overlayEmotes = false;

    public boolean isProviderEnabled(ProviderType type) {
        return switch (type) {
            case TWITCH -> loadTwitch;
            case STV -> load7TV;
            case BTTV -> loadBTTV;
            case FFZ -> loadFFZ;
        };
    }
}
