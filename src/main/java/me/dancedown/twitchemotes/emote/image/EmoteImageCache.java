package me.dancedown.twitchemotes.emote.image;

import me.dancedown.twitchemotes.TwitchEmotes;
import me.dancedown.twitchemotes.emote.render.BakedEmoteGlyph;
import me.dancedown.twitchemotes.emote.type.Emote;
import me.dancedown.twitchemotes.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class EmoteImageCache {

    private final Executor DOWNLOAD_PARSE_EXECUTOR;
    private final HashMap<String, CompletableFuture<EmoteImage>> EMOTE_IMAGE_CACHE = new HashMap<>();
    private final HashMap<String, BakedEmoteGlyph> EMOTE_GLYPH_CACHE = new HashMap<>();
    private final HashMap<String, Set<String>> REVERSE_DEPENDENCIES = new HashMap<>();
    private final HashMap<String, Set<String>> DEPENDENCIES = new HashMap<>();

    private final Object SYNC_LOCK = new Object();

    public EmoteImageCache(Executor downloadParseExecutor) {
        this.DOWNLOAD_PARSE_EXECUTOR = downloadParseExecutor;
    }

    /**
     * @param key The key of the EmoteImage
     * @return if the cache contains or will contain an element for the given key
     */
    public boolean contains(@NonNull String key) {
        return EMOTE_IMAGE_CACHE.containsKey(key);
    }

    /**
     * Adds a completable future containing the emote image to the cache and saves its dependencies.
     * If the key is already contained, removes the old key.
     * Cancels the future, if any of the dependencies are invalid.
     * @param key The key for that completable future
     * @param future The completable future containing the emote image
     * @param dependencies A set of keys that the emote image depends on
     */
    public void add(@NonNull String key, @NonNull CompletableFuture<EmoteImage> future, @NonNull Set<String> dependencies) {
        synchronized (SYNC_LOCK) {
            for(String dependency : dependencies) {
                if(!contains(dependency)) {
                    cancel(future);
                    return;
                }
            }

            if(contains(key))
                remove(key);

            EMOTE_IMAGE_CACHE.put(key, future);
            DEPENDENCIES.put(key, new HashSet<>(dependencies));
            dependencies.forEach(dep -> REVERSE_DEPENDENCIES.computeIfAbsent(dep, _ -> new HashSet<>()).add(key));
        }
    }

    /**
     * Gets an emote image from the cache
     * @param key The key of the image in the cache
     * @return The emote image or <code>null</code> if it's not contained or has an invalid value
     */
    public EmoteImage get(@NonNull String key) {
        CompletableFuture<EmoteImage> future = EMOTE_IMAGE_CACHE.get(key);
        if (future != null && future.isDone() && !future.isCancelled() && !future.isCompletedExceptionally())
            return future.getNow(null);
        return null;
    }

    /**
     * Removes the key from the cache and cancels its future.
     * Also removes every glyph containing the emote name from the cache.
     * Dependent emote images are also removed.
     * @param key The key of the emote
     */
    public void remove(@NonNull String key) {
        synchronized (SYNC_LOCK) {
            // goes through the list of emotes that depend on this emote and recursively remove them
            Set<String> reverseDependencies = REVERSE_DEPENDENCIES.get(key);
            if(reverseDependencies != null)
                for(String pre : new HashSet<>(reverseDependencies))
                    remove(pre);

            // goes through the emotes this one depends on and removes itself from their list of emotes that depend on it
            Set<String> dependencies = DEPENDENCIES.get(key);
            if(dependencies != null)
                for(String dep : new HashSet<>(dependencies)) {
                    Set<String> preReverseDependencies = REVERSE_DEPENDENCIES.get(dep);
                    if(preReverseDependencies != null)
                        preReverseDependencies.remove(key);
                }

            // Removes its own dependencies so it can be deleted
            DEPENDENCIES.remove(key);

            // Deletes itself
            CompletableFuture<EmoteImage> removedImageFuture = EMOTE_IMAGE_CACHE.remove(key);
            EMOTE_GLYPH_CACHE.remove(key);
            if (removedImageFuture == null)
                return;
            cancel(removedImageFuture);
        }
    }

    /**
     * Calls the consumer for every successfully loaded emote
     * @param consumer The consumer to be called
     */
    public void foreach(Consumer<EmoteImage> consumer) {
        synchronized (SYNC_LOCK) {
            for (CompletableFuture<EmoteImage> future : EMOTE_IMAGE_CACHE.values()) {
                if (future.isDone() && !future.isCancelled() && !future.isCompletedExceptionally()) {
                    EmoteImage value = future.getNow(null);
                    if (value != null)
                        consumer.accept(value);
                }
            }
        }
    }

    /**
     * As EmoteImages have to be closed after being created, this method closes the emote image upon completion
     * instead of cancelling the future which may result in open emote images because images could be created
     * before the future is done.
     * @param future The future to cancel
     */
    private void cancel(CompletableFuture<EmoteImage> future) {
        future.whenCompleteAsync((emoteImage, _) -> {
            if(emoteImage != null) {
                TwitchEmotes.chatRefreshNeeded.set(true);
                emoteImage.close();
            }
        }, Minecraft.getInstance());
    }

    /**
     * Adds the BakedEmoteGlyph to the cache with the style as identifier.
     * @param name The name of the emote as key
     * @param glyph The glyph corresponding to the style as value
     */
    public void addGlyph(@NonNull String name, @NonNull BakedEmoteGlyph glyph) {
        EMOTE_GLYPH_CACHE.put(name, glyph);
    }

    /**
     * Retrieves the glyph to a custom emote style.
     * @param name The custom style
     * @return The prepared BakedEmoteGlyph in the cache or <code>null</code> if style isn't in the cache
     */
    public BakedEmoteGlyph getGlyph(@NonNull String name) {
        return EMOTE_GLYPH_CACHE.getOrDefault(name, null);
    }

    /**
     * Downloads the emote image from the URL and parses it asynchronously
     * @param emote The emote to download
     * @return A completable future containing the resulting emote image
     */
    public CompletableFuture<EmoteImage> downloadEmoteImage(Emote emote) {
        CompletableFuture<EmoteImage> future = CompletableFuture
                .supplyAsync(() -> NetworkHandler.download(emote.url()), DOWNLOAD_PARSE_EXECUTOR)
                .thenApply(bytes -> {
                    if(bytes == null)
                        TwitchEmotes.LOGGER.warn("Request for {} ({}) with URL {} returned null!", emote.name(), emote.id(), emote.url());
                    try {
                        return new EmoteImageParser(emote.id(), emote.name(), emote.scale()).parse(bytes, emote.format());
                    } catch (Throwable e) {
                        TwitchEmotes.LOGGER.error("FAIL", e);
                        TwitchEmotes.LOGGER.warn("Failed to parse emote image for {} ({}) with URL {}", emote.name(), emote.id(), emote.url());
                        throw new CompletionException(e);
                    }
                });
        future.thenRun(() -> TwitchEmotes.chatRefreshNeeded.set(true));
        return future;
    }

    /**
     * Creates a new image from the 2 given emote images
     * @param textureNames The names of the emotes
     * @param baseImage The base image that the overlay image is applied to
     * @param overlayImage The overlay image that is drawn on top of the base image
     * @return A completable future containing the resulting emote image
     */
    public CompletableFuture<EmoteImage> overlayEmoteImage(String textureNames, @NonNull EmoteImage baseImage, @NonNull EmoteImage overlayImage) {
        CompletableFuture<EmoteImage> future = CompletableFuture.supplyAsync(() ->
                EmoteImageParser.parseOverlay(textureNames, baseImage, overlayImage), DOWNLOAD_PARSE_EXECUTOR);
        future.thenRun(() -> TwitchEmotes.chatRefreshNeeded.set(true));
        return future;
    }
}

