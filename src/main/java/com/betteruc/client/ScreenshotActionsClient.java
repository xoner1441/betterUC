package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import javax.imageio.ImageIO;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ScreenshotActionsClient {
    private static final int MAX_TRACKED_SCREENSHOTS = 40;
    private static final long MAX_UPLOAD_BYTES = 12L * 1024L * 1024L;
    private static final int FILE_ACTION_ATTEMPTS = 6;
    private static final long FILE_ACTION_RETRY_MILLIS = 150L;
    private static final Map<String, Path> SCREENSHOTS = new LinkedHashMap<>();
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "betteruc-screenshot-io");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(IO_EXECUTOR)
            .build();

    private ScreenshotActionsClient() {
    }

    public static void initialize() {
        // Entry point kept explicit so screenshot services are initialized with the client.
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("buscreenshot")
                .then(ClientCommands.literal("copy")
                        .then(ClientCommands.argument("id", StringArgumentType.word())
                                .executes(context -> copyScreenshot(StringArgumentType.getString(context, "id")))))
                .then(ClientCommands.literal("upload")
                        .then(ClientCommands.argument("id", StringArgumentType.word())
                                .executes(context -> uploadScreenshot(StringArgumentType.getString(context, "id")))))
                .then(ClientCommands.literal("folder")
                        .then(ClientCommands.argument("id", StringArgumentType.word())
                                .executes(context -> openFolder(StringArgumentType.getString(context, "id")))))
                .then(ClientCommands.literal("delete")
                        .then(ClientCommands.argument("id", StringArgumentType.word())
                                .executes(context -> requestDelete(StringArgumentType.getString(context, "id")))))
                .then(ClientCommands.literal("confirm-delete")
                        .then(ClientCommands.argument("id", StringArgumentType.word())
                                .executes(context -> confirmDelete(StringArgumentType.getString(context, "id")))))
                .then(ClientCommands.literal("cancel-delete")
                        .executes(context -> {
                            sendMessage(Component.literal("\u00A77[betterUC] L\u00F6schen abgebrochen."));
                            return 1;
                        })));
    }

    public static void onScreenshotSaved(Path path) {
        if (!BetterUCConfig.INSTANCE.screenshotActionsEnabled || path == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            Path normalized = path.toAbsolutePath().normalize();
            String id = track(normalized);
            if (BetterUCConfig.INSTANCE.screenshotAutoCopyEnabled) {
                copyImageAsync(normalized, false);
            }
            sendMessage(buildActionsMessage(id));
        });
    }

    private static synchronized String track(Path path) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        SCREENSHOTS.put(id, path);
        while (SCREENSHOTS.size() > MAX_TRACKED_SCREENSHOTS) {
            String oldest = SCREENSHOTS.keySet().iterator().next();
            SCREENSHOTS.remove(oldest);
        }
        return id;
    }

    private static synchronized Path resolve(String id) {
        Path path = SCREENSHOTS.get(id);
        return path != null && Files.isRegularFile(path) ? path : null;
    }

    private static Component buildActionsMessage(String id) {
        MutableComponent message = Component.literal("\u00A7b[betterUC] \u00A77Screenshot: ");
        message.append(action("\u00A7a[Kopieren]", "/buscreenshot copy " + id));
        message.append(Component.literal(" "));
        message.append(action("\u00A7b[Hochladen]", "/buscreenshot upload " + id));
        message.append(Component.literal(" "));
        message.append(action("\u00A7e[Ordner]", "/buscreenshot folder " + id));
        message.append(Component.literal(" "));
        message.append(action("\u00A7c[L\u00F6schen]", "/buscreenshot delete " + id));
        return message;
    }

    private static MutableComponent action(String label, String command) {
        return Component.literal(label)
                .setStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static int copyScreenshot(String id) {
        Path path = resolve(id);
        if (path == null) {
            unavailable();
            return 0;
        }
        copyImageAsync(path, true);
        return 1;
    }

    private static void copyImageAsync(Path path, boolean notify) {
        IO_EXECUTOR.execute(() -> {
            try {
                BufferedImage image = readImageWithRetry(path);
                setClipboardWithRetry(image);
                if (notify) {
                    sendOnClient("\u00A7a[betterUC] Screenshot wurde in die Zwischenablage kopiert.");
                }
            } catch (Exception exception) {
                BetterUCMod.LOGGER.warn("Could not copy screenshot {}", path, exception);
                if (notify) {
                    sendOnClient("\u00A7c[betterUC] Screenshot konnte nicht kopiert werden.");
                }
            }
        });
    }

    private static int openFolder(String id) {
        Path path = resolve(id);
        if (path == null) {
            unavailable();
            return 0;
        }
        Util.getPlatform().openPath(path.getParent());
        return 1;
    }

    private static int requestDelete(String id) {
        Path path = resolve(id);
        if (path == null) {
            unavailable();
            return 0;
        }
        MutableComponent message = Component.literal("\u00A7e[betterUC] Screenshot wirklich l\u00F6schen? ");
        message.append(action("\u00A7c[Ja, l\u00F6schen]", "/buscreenshot confirm-delete " + id));
        message.append(Component.literal(" "));
        message.append(action("\u00A7a[Abbrechen]", "/buscreenshot cancel-delete"));
        sendMessage(message);
        return 1;
    }

    private static int confirmDelete(String id) {
        Path path = resolve(id);
        if (path == null) {
            unavailable();
            return 0;
        }
        IO_EXECUTOR.execute(() -> deleteWithRetry(id, path));
        return 1;
    }

    private static int uploadScreenshot(String id) {
        Path path = resolve(id);
        if (path == null) {
            unavailable();
            return 0;
        }

        String token = BetterUCConfig.INSTANCE.pingRelayToken == null
                ? ""
                : BetterUCConfig.INSTANCE.pingRelayToken.trim();
        if (token.isEmpty()) {
            sendMessage(Component.literal("\u00A7c[betterUC] F\u00FCr Uploads wird ein g\u00FCltiger Access Code ben\u00F6tigt."));
            return 0;
        }

        try {
            long size = Files.size(path);
            if (size <= 0 || size > MAX_UPLOAD_BYTES) {
                sendMessage(Component.literal("\u00A7c[betterUC] Der Screenshot ist leer oder gr\u00F6\u00DFer als 12 MB."));
                return 0;
            }
        } catch (IOException exception) {
            unavailable();
            return 0;
        }

        URI uploadUri;
        try {
            uploadUri = screenshotApiUri(BetterUCConfig.INSTANCE.pingRelayUrl);
        } catch (IllegalArgumentException exception) {
            sendMessage(Component.literal("\u00A7c[betterUC] Die Relay-Adresse ist ung\u00FCltig."));
            return 0;
        }

        sendMessage(Component.literal("\u00A77[betterUC] Screenshot wird hochgeladen..."));
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(uploadUri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "image/png")
                    .header("X-Screenshot-Name", path.getFileName().toString())
                    .POST(HttpRequest.BodyPublishers.ofFile(path))
                    .build();
        } catch (IOException exception) {
            unavailable();
            return 0;
        }

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        BetterUCMod.LOGGER.warn("Screenshot upload failed", throwable);
                        sendOnClient("\u00A7c[betterUC] Upload fehlgeschlagen. Bitte versuche es erneut.");
                        return;
                    }
                    handleUploadResponse(response);
                });
        return 1;
    }

    private static void handleUploadResponse(HttpResponse<String> response) {
        try {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message;
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    message = "\u00A7c[betterUC] Access Code ung\u00FCltig oder gesperrt.";
                } else if (response.statusCode() == 404) {
                    message = "\u00A7c[betterUC] Screenshot-Upload ist auf dem Relay noch nicht installiert (HTTP 404).";
                } else {
                    message = "\u00A7c[betterUC] Upload fehlgeschlagen (HTTP " + response.statusCode() + ").";
                }
                sendOnClient(message);
                return;
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String url = json.has("url") ? json.get("url").getAsString() : "";
            if (url.isBlank()) {
                throw new IllegalStateException("Upload response contains no URL");
            }

            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                client.keyboardHandler.setClipboard(url);
                MutableComponent message = Component.literal("\u00A7a[betterUC] Link kopiert. \u00A77Er ist 7 Tage g\u00FCltig. ");
                message.append(Component.literal("\u00A7b[Im Browser \u00F6ffnen]")
                        .setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))));
                sendMessage(message);
            });
        } catch (Exception exception) {
            BetterUCMod.LOGGER.warn("Could not parse screenshot upload response", exception);
            sendOnClient("\u00A7c[betterUC] Die Upload-Antwort war ung\u00FCltig.");
        }
    }

    private static URI screenshotApiUri(String relayUrl) {
        String raw = relayUrl == null ? "" : relayUrl.trim();
        if (raw.isEmpty()) {
            raw = BetterUCConfig.DEFAULT_PING_RELAY_URL;
        }
        URI relay = URI.create(raw);
        String scheme = relay.getScheme();
        String httpScheme = "wss".equalsIgnoreCase(scheme) ? "https"
                : "ws".equalsIgnoreCase(scheme) ? "http"
                : scheme;
        if (!"http".equalsIgnoreCase(httpScheme) && !"https".equalsIgnoreCase(httpScheme)) {
            throw new IllegalArgumentException("Unsupported relay scheme");
        }
        return URI.create(httpScheme + "://" + relay.getAuthority() + "/api/screenshots");
    }

    private static void unavailable() {
        sendMessage(Component.literal("\u00A7c[betterUC] Der Screenshot ist nicht mehr verf\u00FCgbar."));
    }

    private static void sendOnClient(String message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> sendMessage(Component.literal(message)));
    }

    private static void sendMessage(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(message);
        }
    }

    private static BufferedImage readImageWithRetry(Path path) throws Exception {
        Exception lastFailure = null;
        for (int attempt = 0; attempt < FILE_ACTION_ATTEMPTS; attempt++) {
            try {
                BufferedImage image = ImageIO.read(path.toFile());
                if (image != null) {
                    return image;
                }
                lastFailure = new IOException("Screenshot konnte nicht gelesen werden");
            } catch (IOException exception) {
                lastFailure = exception;
            }
            sleepBeforeRetry(attempt);
        }
        throw lastFailure == null ? new IOException("Screenshot konnte nicht gelesen werden") : lastFailure;
    }

    private static void setClipboardWithRetry(Image image) throws Exception {
        Exception lastFailure = null;
        for (int attempt = 0; attempt < FILE_ACTION_ATTEMPTS; attempt++) {
            try {
                EventQueue.invokeAndWait(() -> Toolkit.getDefaultToolkit()
                        .getSystemClipboard()
                        .setContents(new ImageTransferable(image), null));
                return;
            } catch (Exception exception) {
                lastFailure = exception;
                sleepBeforeRetry(attempt);
            }
        }
        throw lastFailure == null ? new IOException("Zwischenablage nicht erreichbar") : lastFailure;
    }

    private static void deleteWithRetry(String id, Path path) {
        IOException lastFailure = null;
        for (int attempt = 0; attempt < FILE_ACTION_ATTEMPTS; attempt++) {
            try {
                Files.deleteIfExists(path);
                if (!Files.exists(path)) {
                    synchronized (ScreenshotActionsClient.class) {
                        SCREENSHOTS.remove(id);
                    }
                    sendOnClient("\u00A7a[betterUC] Screenshot wurde gel\u00F6scht.");
                    return;
                }
            } catch (IOException exception) {
                lastFailure = exception;
            }
            sleepBeforeRetry(attempt);
        }

        BetterUCMod.LOGGER.warn("Could not delete screenshot {}", path, lastFailure);
        sendOnClient("\u00A7c[betterUC] Screenshot konnte nicht gel\u00F6scht werden.");
    }

    private static void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(FILE_ACTION_RETRY_MILLIS * (attempt + 1L));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record ImageTransferable(Image image) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
