package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class BugReportClient {
    private static final int MAX_LOG_BYTES = 96 * 1024;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private BugReportClient() {
    }

    public static CompletableFuture<Result> submit(
            String title,
            String description,
            String steps,
            Path screenshot,
            boolean attachLog
    ) {
        String token = safe(BetterUCConfig.INSTANCE.pingRelayToken);
        if (token.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Bitte zuerst einen gültigen Access Code eintragen."));
        }
        CompletableFuture<String> screenshotUpload = screenshot == null
                ? CompletableFuture.completedFuture("")
                : ScreenshotActionsClient.uploadForBug(screenshot);
        return screenshotUpload.thenCompose(screenshotUrl -> CompletableFuture.supplyAsync(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("title", title.trim());
            body.addProperty("description", description.trim());
            body.addProperty("steps", steps.trim());
            body.addProperty("screenshotUrl", screenshotUrl);
            body.addProperty("logExcerpt", attachLog ? readLogExcerpt(token) : "");
            body.addProperty("modVersion", modVersion());
            body.addProperty("gameVersion", SharedConstants.getCurrentVersion().name());
            body.addProperty("clientName", Minecraft.getInstance().getLaunchedVersion());

            HttpRequest request = HttpRequest.newBuilder(apiUri(BetterUCConfig.INSTANCE.pingRelayUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                JsonObject json = null;
                try {
                    json = JsonParser.parseString(response.body()).getAsJsonObject();
                } catch (RuntimeException ignored) {
                    // Some reverse proxies return HTML for a missing or unavailable endpoint.
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String error = json != null && json.has("error")
                            ? json.get("error").getAsString()
                            : response.statusCode() == 404
                                    ? "Die Bugmelde-Funktion ist auf dem Relay noch nicht installiert."
                                    : "Bugmeldung fehlgeschlagen (HTTP " + response.statusCode() + ").";
                    throw new IllegalStateException(error);
                }
                if (json == null) throw new IllegalStateException("Das Relay lieferte eine ungültige Antwort.");
                String url = json.has("url") ? json.get("url").getAsString() : "";
                if (url.isBlank()) throw new IllegalStateException("Das Relay lieferte keinen Discord-Link.");
                return new Result(url);
            } catch (IOException exception) {
                throw new CompletionException(new IllegalStateException("Das betterUC Relay ist nicht erreichbar.", exception));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CompletionException(new IllegalStateException("Die Bugmeldung wurde abgebrochen.", exception));
            }
        }));
    }

    private static String readLogExcerpt(String token) {
        Path log = Minecraft.getInstance().gameDirectory.toPath().resolve("logs").resolve("latest.log");
        if (!Files.isRegularFile(log)) return "";
        try (FileChannel channel = FileChannel.open(log, StandardOpenOption.READ)) {
            int length = (int) Math.min(channel.size(), MAX_LOG_BYTES);
            ByteBuffer buffer = ByteBuffer.allocate(length);
            channel.position(Math.max(0, channel.size() - length));
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                // Read only the bounded tail of the file.
            }
            String text = new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
            return token.isBlank() ? text : text.replace(token, "[ACCESS_CODE ENTFERNT]");
        } catch (IOException exception) {
            BetterUCMod.LOGGER.warn("Could not read latest.log for bug report", exception);
            return "";
        }
    }

    private static URI apiUri(String relayUrl) {
        String raw = safe(relayUrl);
        if (raw.isBlank()) raw = BetterUCConfig.DEFAULT_PING_RELAY_URL;
        URI relay = URI.create(raw);
        String scheme = "wss".equalsIgnoreCase(relay.getScheme()) ? "https"
                : "ws".equalsIgnoreCase(relay.getScheme()) ? "http"
                : relay.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Ungültige Relay-Adresse.");
        }
        return URI.create(scheme + "://" + relay.getAuthority() + "/api/bugs");
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer(BetterUCMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record Result(String url) {
    }
}
