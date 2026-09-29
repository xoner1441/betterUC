package me.dancedown.twitchemotes.emote;

import me.dancedown.twitchemotes.TwitchEmotes;
import me.dancedown.twitchemotes.emote.type.Emote;
import me.dancedown.twitchemotes.emote.type.EmoteEntry;
import me.dancedown.twitchemotes.emote.type.EmoteScope;
import me.dancedown.twitchemotes.network.ProviderType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class EmoteRegistry {

    private final EnumMap<ProviderType, EnumMap<EmoteScope, Set<String>>> INDEX = new EnumMap<>(ProviderType.class);
    private final ConcurrentMap<String, Emote> BY_NAME = new ConcurrentHashMap<>();
    private final Map<String, NavigableSet<EmoteEntry>> CANDIDATES = new HashMap<>();
    private final Comparator<EmoteEntry> PRIORITY_ORDER = Comparator.comparingInt(EmoteEntry::getPriority).reversed();

    public EmoteRegistry() {
        for (ProviderType p : ProviderType.values()) {
            EnumMap<EmoteScope, Set<String>> scopes = new EnumMap<>(EmoteScope.class);
            for (EmoteScope s : EmoteScope.values()) {
                scopes.put(s, ConcurrentHashMap.newKeySet());
            }
            INDEX.put(p, scopes);
        }
    }

    /**
     * Gets the emote by its name
     * @param key the name of the emote
     * @return the emote object or <code>null</code> if emote is not loaded
     */
    public Emote get(@NotNull String key) {
        return BY_NAME.get(key);
    }

    /**
     * @param name The name of the emote
     * @return Whether the emote is loaded (<code>true</code>) or not (<code>false</code>)
     */
    public boolean contains(@NotNull String name) {
        return BY_NAME.containsKey(name);
    }

    /**
     * Removes every emote from a given provider
     * @param provider The emote provider
     */
    public void removeProvider(@NotNull ProviderType provider) {
        removeAll(provider, EmoteScope.GLOBAL);
        removeAll(provider, EmoteScope.CHANNEL);
    }

    /**
     * Removes all emotes from a given provider and scope
     * @param provider The emote provider
     * @param emoteScope The scope of the emotes (global or channel emotes)
     */
    public void removeAll(@NotNull ProviderType provider, @NotNull EmoteScope emoteScope) {
        Set<String> names = INDEX.get(provider).get(emoteScope);
        for (String name : names) {
            NavigableSet<EmoteEntry> set = CANDIDATES.get(name);
            if (set == null) continue;

            set.removeIf(e -> e.getProvider() == provider && e.getScope() == emoteScope);
            Emote removedEmote;

            if (set.isEmpty()) {
                CANDIDATES.remove(name);
                removedEmote = BY_NAME.remove(name);
            } else {
                removedEmote = BY_NAME.put(name, set.first().getEmote());
            }

            // Clear EmoteImageCache of that emote
            if(removedEmote != null)
                TwitchEmotes.EMOTE_IMAGE_CACHE.remove(name);
        }
        names.clear();
    }

    /**
     * Removes old emotes from a provider and scope and adds new emotes from that provider and that scope
     * @param provider The emote provider
     * @param emoteScope The scope of the emotes (global or channel emotes)
     * @param emotes A list of Emotes to add
     */
    public void replaceAll(@NotNull ProviderType provider, @NotNull EmoteScope emoteScope, @NotNull List<Emote> emotes) {
        removeAll(provider, emoteScope);
        addAll(provider, emoteScope, emotes);
    }

    /**
     * Adds emotes to the registry by a given provider and scope
     * @param provider The emote provider
     * @param emoteScope The scope of the emotes
     * @param emotes A list of emotes to add
     */
    public void addAll(@NotNull ProviderType provider, @NotNull EmoteScope emoteScope, @NotNull List<Emote> emotes) {
        for (Emote emote : emotes)
            add(new EmoteEntry(emote, provider, emoteScope));
    }

    /**
     * Adds a single Emote to the registry
     * @param entry The EmoteEntry containing the emote
     */
    public void add(EmoteEntry entry) {
        String name = entry.getEmote().name();
        NavigableSet<EmoteEntry> set = CANDIDATES.computeIfAbsent(name, value -> new TreeSet<>(PRIORITY_ORDER));
        EmoteEntry before = set.isEmpty() ? null : set.first();
        if (!set.add(entry))
            return;

        EmoteEntry after = set.first();
        if (before != after)
            BY_NAME.put(name, Objects.requireNonNull(after).getEmote());

        INDEX.get(entry.getProvider()).get(entry.getScope()).add(name);
    }

    /**
     * @return All emote names available
     */
    public Collection<String> getKeys() {
        return BY_NAME.keySet();
    }
}

