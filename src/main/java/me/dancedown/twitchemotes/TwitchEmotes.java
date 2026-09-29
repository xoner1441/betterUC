package me.dancedown.twitchemotes;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import me.dancedown.twitchemotes.config.TwitchEmotesConfig;
import me.dancedown.twitchemotes.emote.EmoteRegistry;
import me.dancedown.twitchemotes.emote.image.EmoteImage;
import me.dancedown.twitchemotes.emote.image.EmoteImageCache;
import me.dancedown.twitchemotes.emote.type.Emote;
import me.dancedown.twitchemotes.emote.type.EmoteScope;
import me.dancedown.twitchemotes.network.BTTVEmoteProvider;
import me.dancedown.twitchemotes.network.EmoteProvider;
import me.dancedown.twitchemotes.network.FFZEmoteProvider;
import me.dancedown.twitchemotes.network.ProviderType;
import me.dancedown.twitchemotes.network.STVEmoteProvider;
import me.dancedown.twitchemotes.network.TwitchEmoteProvider;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/**
 * Embedded image-emote runtime, adapted from DanceDown/TwitchEmotes (MIT).
 * It loads the public global sets without requiring a Twitch login.
 */
public final class TwitchEmotes {
    private static final int CHAT_REFRESH_INTERVAL_TICKS = 10;
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final AtomicBoolean LOADING = new AtomicBoolean();
    private static final ExecutorService LOAD_EXECUTOR = Executors.newFixedThreadPool(4, daemonFactory("betteruc-emotes-load"));
    private static final ExecutorService REGISTRY_EXECUTOR = Executors.newSingleThreadExecutor(daemonFactory("betteruc-emotes-registry"));
    private static final ExecutorService IMAGE_EXECUTOR = Executors.newFixedThreadPool(3, daemonFactory("betteruc-emotes-images"));
    private static final Map<ProviderType, EmoteProvider> PROVIDERS = new EnumMap<>(ProviderType.class);

    public static final Logger LOGGER = BetterUCMod.LOGGER;
    public static final TwitchEmotesConfig CONFIG = new TwitchEmotesConfig();
    public static final EmoteRegistry EMOTE_REGISTRY = new EmoteRegistry();
    public static final EmoteImageCache EMOTE_IMAGE_CACHE = new EmoteImageCache(IMAGE_EXECUTOR);
    public static final AtomicBoolean chatRefreshNeeded = new AtomicBoolean();

    private static int refreshCounter;
    private static boolean lastEnabled;

    private TwitchEmotes() {
    }

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) return;

        PROVIDERS.put(ProviderType.TWITCH, new TwitchEmoteProvider());
        PROVIDERS.put(ProviderType.STV, new STVEmoteProvider());
        PROVIDERS.put(ProviderType.BTTV, new BTTVEmoteProvider());
        PROVIDERS.put(ProviderType.FFZ, new FFZEmoteProvider());

        syncEnabled();
        lastEnabled = CONFIG.enabled;
        ClientTickEvents.END_CLIENT_TICK.register(TwitchEmotes::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
        if (CONFIG.enabled) reload();
    }

    public static void reload() {
        if (!INITIALIZED.get() || !CONFIG.enabled || !LOADING.compareAndSet(false, true)) return;
        LOAD_EXECUTOR.execute(() -> {
            try {
                for (Map.Entry<ProviderType, EmoteProvider> entry : PROVIDERS.entrySet()) {
                    if (!CONFIG.isProviderEnabled(entry.getKey())) continue;
                    ProviderType type = entry.getKey();
                    try {
                        List<Emote> emotes = entry.getValue().getGlobalEmotes(CONFIG);
                        REGISTRY_EXECUTOR.submit(() ->
                                EMOTE_REGISTRY.replaceAll(type, EmoteScope.GLOBAL, emotes)
                        ).get(15, TimeUnit.SECONDS);
                        LOGGER.info("betterUC Emotes: {} globale {}-Emotes geladen", emotes.size(), type.getDisplayName());
                    } catch (Exception error) {
                        LOGGER.warn("betterUC Emotes: {} konnte nicht geladen werden", type.getDisplayName(), error);
                    }
                }
                chatRefreshNeeded.set(true);
            } finally {
                LOADING.set(false);
            }
        });
    }

    private static void tick(Minecraft client) {
        syncEnabled();
        if (CONFIG.enabled && !lastEnabled) reload();
        if (CONFIG.enabled && CONFIG.animateEmotes) EMOTE_IMAGE_CACHE.foreach(EmoteImage::tick);
        lastEnabled = CONFIG.enabled;

        if (++refreshCounter >= CHAT_REFRESH_INTERVAL_TICKS) {
            refreshCounter = 0;
            if (chatRefreshNeeded.compareAndSet(true, false)
                    && client.gui != null && client.gui.hud != null) {
                client.gui.hud.getChat().rescaleChat();
            }
        }
    }

    private static void syncEnabled() {
        CONFIG.enabled = BetterUCConfig.INSTANCE.chatEmojisEnabled;
    }

    private static void shutdown() {
        EMOTE_IMAGE_CACHE.foreach(EmoteImage::close);
        LOAD_EXECUTOR.shutdownNow();
        REGISTRY_EXECUTOR.shutdownNow();
        IMAGE_EXECUTOR.shutdownNow();
    }

    private static ThreadFactory daemonFactory(String baseName) {
        return runnable -> {
            Thread thread = new Thread(runnable, baseName);
            thread.setDaemon(true);
            return thread;
        };
    }
}
