package me.dancedown.twitchemotes.emote.type;

import me.dancedown.twitchemotes.network.ProviderType;

import java.util.Objects;

public final class EmoteEntry {
    private final Emote emote;
    private final ProviderType provider;
    private final EmoteScope emoteScope;
    private final int priority;

    public EmoteEntry(Emote emote, ProviderType provider, EmoteScope emoteScope) {
        this.emote = emote;
        this.provider = provider;
        this.emoteScope = emoteScope;

        int scopePriority = switch (emoteScope) {
            case CHANNEL -> 4;
            case GLOBAL -> 0;
        };

        int providerPriority = switch (provider) {
            case ProviderType.STV -> 3;
            case ProviderType.BTTV -> 2;
            case ProviderType.FFZ -> 1;
            case ProviderType.TWITCH -> 0;
        };

        priority = scopePriority + providerPriority;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        EmoteEntry that = (EmoteEntry) o;
        return emote.id().equals(that.emote.id()) && provider.equals(that.provider) && emoteScope.equals(that.emoteScope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(emote.id(), provider, emoteScope);
    }

    public Emote getEmote() {
        return emote;
    }

    public ProviderType getProvider() {
        return provider;
    }

    public EmoteScope getScope() {
        return emoteScope;
    }

    public int getPriority() {
        return priority;
    }
}

