package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.gui.UpdateRestartScreen;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VersionChecker {
    private static final String OWNER = "xoner1441";
    private static final String REPO = "betterUC";
    private static final String REPO_URL = "https://github.com/" + OWNER + "/" + REPO;
    private static final String LATEST_RELEASE_URL = REPO_URL + "/releases/latest";
    private static final String PUBLIC_DOWNLOAD_URL = "https://betteruc.de/download";
    private static final URI WEBSITE_RELEASE_API = URI.create("https://betteruc.de/api/releases/latest");
    private static final URI LATEST_RELEASE_API = URI.create("https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest");
    private static final URI RAW_GRADLE_PROPERTIES_MAIN = URI.create("https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/main/gradle.properties");
    private static final URI RAW_GRADLE_PROPERTIES_MASTER = URI.create("https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/master/gradle.properties");
    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HTML_URL_PATTERN = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MOD_VERSION_PATTERN = Pattern.compile("(?m)^mod_version\\s*=\\s*([^\\r\\n]+)\\s*$");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final long CHECK_COOLDOWN_MS = 5 * 60 * 1000L;
    private static final long MIN_JAR_SIZE_BYTES = 50_000L;

    private static volatile boolean checkRunning = false;
    private static volatile boolean installRunning = false;
    private static volatile boolean confirmationRequested = false;
    private static volatile boolean restartTriggered = false;
    private static volatile PreparedUpdate preparedUpdate;
    private static volatile String notifiedVersion = "";
    private static volatile long lastCheckMs = 0L;

    private VersionChecker() {
    }

    public static void checkOnJoin(Minecraft client) {
        lastCheckMs = 0L;
        checkForUpdates(client, true);
    }

    public static void tick(Minecraft client) {
        checkForUpdates(client, false);
    }

    public static void onRelayUpdateAvailable(Minecraft client, String announcedVersion) {
        if (client == null || !isRemoteNewer(getCurrentVersion(), announcedVersion)) {
            return;
        }
        checkForUpdates(client, true);
    }

    private static void checkForUpdates(Minecraft client, boolean bypassCooldown) {
        if (client == null || client.player == null || checkRunning) return;
        long now = System.currentTimeMillis();
        if (!bypassCooldown && now - lastCheckMs < CHECK_COOLDOWN_MS) {
            return;
        }

        lastCheckMs = now;
        checkRunning = true;
        CompletableFuture.supplyAsync(VersionChecker::fetchLatestVersion)
                .whenComplete((latestVersion, error) -> {
                    checkRunning = false;
                    if (error != null) {
                        BetterUCMod.LOGGER.warn("Could not check betterUC version", error);
                        return;
                    }
                    if (latestVersion == null || latestVersion.version().isBlank()) {
                        return;
                    }

                    String currentVersion = getCurrentVersion();
                    if (!isRemoteNewer(currentVersion, latestVersion.version())) {
                        return;
                    }

                    client.execute(() -> {
                        if (client.player == null) return;
                        announceUpdate(client, currentVersion, latestVersion);
                    });
                });
    }

    public static void installLatestUpdate(Minecraft client, boolean manual) {
        if (client == null) {
            return;
        }
        PreparedUpdate ready = preparedUpdate;
        if (ready != null && isRemoteNewer(getCurrentVersion(), ready.latestVersion().version())) {
            openRestartConfirmation(client, ready);
            return;
        }
        if (installRunning) {
            confirmationRequested = manual;
            sendLocalMessage(client, "\u00A7e[betterUC] Download l\u00E4uft bereits. Das Best\u00E4tigungsfenster \u00F6ffnet sich danach automatisch.");
            return;
        }

        if (manual) {
            sendLocalMessage(client, "\u00A77[betterUC] Suche nach neuer Version...");
        }

        installRunning = true;
        CompletableFuture.supplyAsync(() -> {
                    LatestVersion latestVersion = fetchLatestVersion();
                    if (latestVersion == null || latestVersion.version().isBlank()) {
                        return PrepareOutcome.message("\u00A7c[betterUC] Konnte keine aktuelle betterUC-Version finden.");
                    }

                    String currentVersion = getCurrentVersion();
                    if (!isRemoteNewer(currentVersion, latestVersion.version())) {
                        return PrepareOutcome.message("\u00A7a[betterUC] Du nutzt bereits die aktuelle Version \u00A7f"
                                + normalizeVersion(currentVersion) + "\u00A7a.");
                    }

                    return prepareDownload(latestVersion);
                })
                .whenComplete((outcome, error) -> {
                    installRunning = false;
                    client.execute(() -> {
                        if (error != null) {
                            BetterUCMod.LOGGER.warn("Could not prepare betterUC auto update", error);
                            sendLocalMessage(client, "\u00A7c[betterUC] Auto-Update fehlgeschlagen: " + safeError(error));
                            return;
                        }
                        if (outcome != null && outcome.ready() && preparedUpdate != null) {
                            confirmationRequested = false;
                            openRestartConfirmation(client, preparedUpdate);
                        } else if (outcome != null && !outcome.message().isBlank()) {
                            sendLocalMessage(client, outcome.message());
                        }
                    });
                });
    }

    public static String preparedVersion() {
        PreparedUpdate update = preparedUpdate;
        return update == null ? "" : normalizeVersion(update.latestVersion().version());
    }

    public static boolean preparedRestartSupported() {
        PreparedUpdate update = preparedUpdate;
        return update != null && update.restartCommand() != null;
    }

    public static boolean confirmInstallAndRestart(Minecraft client) {
        PreparedUpdate update = preparedUpdate;
        if (client == null || update == null || restartTriggered) return false;

        try {
            Path script = createInstallScript(
                    update.stagingDirectory(),
                    update.installDirectories(),
                    update.downloadedJar(),
                    update.targetName(),
                    update.restartCommand()
            );
            if (!startInstallScript(script)) {
                sendLocalMessage(client, "\u00A7c[betterUC] Das Installationsprogramm konnte nicht gestartet werden.");
                return false;
            }

            restartTriggered = true;
            client.stop();
            return true;
        } catch (Exception e) {
            BetterUCMod.LOGGER.warn("Could not launch betterUC update restart", e);
            sendLocalMessage(client, "\u00A7c[betterUC] Neustart fehlgeschlagen: " + safeError(e));
            return false;
        }
    }

    private static void announceUpdate(Minecraft client, String currentVersion, LatestVersion latestVersion) {
        String normalizedLatest = normalizeVersion(latestVersion.version());
        boolean firstNotice = !normalizedLatest.equalsIgnoreCase(notifiedVersion);
        if (firstNotice) {
            notifiedVersion = normalizedLatest;
            client.player.sendSystemMessage(buildUpdateMessage(currentVersion, latestVersion));
        }

        if (preparedUpdate != null || installRunning || !latestVersion.hasJarAsset()) {
            return;
        }
        if (BetterUCConfig.INSTANCE.autoUpdateEnabled) {
            prepareKnownUpdate(client, latestVersion);
        }
    }

    private static void prepareKnownUpdate(Minecraft client, LatestVersion latestVersion) {
        if (client == null || latestVersion == null || installRunning || preparedUpdate != null) return;

        installRunning = true;
        sendLocalMessage(client, "\u00A77[betterUC] Auto-Updater l\u00E4dt Version \u00A7f"
                + normalizeVersion(latestVersion.version()) + "\u00A77 im Hintergrund...");
        CompletableFuture.supplyAsync(() -> prepareDownload(latestVersion))
                .whenComplete((outcome, error) -> {
                    installRunning = false;
                    client.execute(() -> {
                        if (error != null) {
                            BetterUCMod.LOGGER.warn("Could not prepare betterUC auto update", error);
                            sendLocalMessage(client, "\u00A7c[betterUC] Auto-Update fehlgeschlagen: " + safeError(error));
                            return;
                        }
                        if (outcome != null && outcome.ready()) {
                            if (confirmationRequested && preparedUpdate != null) {
                                confirmationRequested = false;
                                openRestartConfirmation(client, preparedUpdate);
                            } else {
                                sendReadyMessage(client);
                            }
                        } else if (outcome != null && !outcome.message().isBlank()) {
                            sendLocalMessage(client, outcome.message());
                        }
                    });
                });
    }

    private static void openRestartConfirmation(Minecraft client, PreparedUpdate update) {
        if (client == null || update == null) return;
        ClientCompat.setScreen(client, new UpdateRestartScreen(
                ClientCompat.currentScreen(client),
                normalizeVersion(update.latestVersion().version()),
                update.restartCommand() != null
        ));
    }

    private static LatestVersion fetchLatestVersion() {
        Optional<LatestVersion> websiteRelease = fetchLatestWebsiteRelease();
        if (websiteRelease.isPresent()) {
            return websiteRelease.get();
        }

        Optional<LatestVersion> release = fetchLatestRelease();
        if (release.isPresent()) {
            return release.get();
        }

        Optional<String> mainVersion = fetchVersionFromGradleProperties(RAW_GRADLE_PROPERTIES_MAIN);
        if (mainVersion.isPresent()) {
            return new LatestVersion(mainVersion.get(), REPO_URL, "", "", "");
        }

        return fetchVersionFromGradleProperties(RAW_GRADLE_PROPERTIES_MASTER)
                .map(version -> new LatestVersion(version, REPO_URL, "", "", ""))
                .orElse(null);
    }

    private static Optional<LatestVersion> fetchLatestWebsiteRelease() {
        String body = fetchText(websiteReleaseApi()).orElse("");
        if (body.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                return Optional.empty();
            }

            JsonObject object = root.getAsJsonObject();
            boolean ok = !object.has("ok") || object.get("ok").getAsBoolean();
            if (!ok) {
                return Optional.empty();
            }

            String version = jsonString(object, "version");
            if (version.isBlank()) {
                version = jsonString(object, "tagName");
            }
            if (version.isBlank()) {
                return Optional.empty();
            }

            String pageUrl = jsonString(object, "downloadPage");
            if (pageUrl.isBlank()) {
                pageUrl = PUBLIC_DOWNLOAD_URL;
            }

            String assetName = jsonString(object, "assetName");
            String downloadUrl = jsonString(object, "downloadUrl");
            String sha256 = normalizeSha256(jsonString(object, "sha256"));
            if (!downloadUrl.isBlank() && !looksLikeJarDownloadUrl(downloadUrl)) {
                downloadUrl = "";
            }

            return Optional.of(new LatestVersion(version, pageUrl, assetName, downloadUrl, sha256));
        } catch (RuntimeException e) {
            BetterUCMod.LOGGER.warn("Could not parse betterUC website release JSON", e);
            return Optional.empty();
        }
    }

    private static Optional<LatestVersion> fetchLatestRelease() {
        String body = fetchText(LATEST_RELEASE_API).orElse("");
        Optional<LatestVersion> parsed = parseLatestReleaseJson(body);
        if (parsed.isPresent()) {
            return parsed;
        }

        String tagName = findFirst(TAG_NAME_PATTERN, body).orElse("");
        if (tagName.isBlank()) {
            return Optional.empty();
        }

        String url = findFirst(HTML_URL_PATTERN, body).orElse(LATEST_RELEASE_URL);
        return Optional.of(new LatestVersion(tagName, url, "", "", ""));
    }

    private static Optional<LatestVersion> parseLatestReleaseJson(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                return Optional.empty();
            }

            JsonObject object = root.getAsJsonObject();
            String tagName = jsonString(object, "tag_name");
            if (tagName.isBlank()) {
                return Optional.empty();
            }

            String url = jsonString(object, "html_url");
            if (url.isBlank()) {
                url = LATEST_RELEASE_URL;
            }

            String releaseTarget = preferredReleaseTarget();
            JsonObject selectedAsset = null;
            JsonObject unversionedFallback = null;
            JsonArray assets = object.has("assets") && object.get("assets").isJsonArray()
                    ? object.getAsJsonArray("assets")
                    : new JsonArray();
            for (JsonElement assetElement : assets) {
                if (!assetElement.isJsonObject()) {
                    continue;
                }

                JsonObject asset = assetElement.getAsJsonObject();
                String name = jsonString(asset, "name");
                String downloadUrl = jsonString(asset, "browser_download_url");
                if (!isBetterUcJarAsset(name, downloadUrl)) {
                    continue;
                }

                String assetValue = assetTargetValue(name, downloadUrl);
                if (matchesReleaseTarget(assetValue, releaseTarget)) {
                    selectedAsset = asset;
                    break;
                }
                if (!hasReleaseTargetMarker(assetValue) && unversionedFallback == null) {
                    unversionedFallback = asset;
                }
            }

            if (selectedAsset == null) {
                selectedAsset = unversionedFallback;
            }

            String assetName = selectedAsset == null ? "" : jsonString(selectedAsset, "name");
            String assetUrl = selectedAsset == null ? "" : jsonString(selectedAsset, "browser_download_url");
            String sha256 = selectedAsset == null ? "" : normalizeSha256(jsonString(selectedAsset, "digest"));
            return Optional.of(new LatestVersion(tagName, url, assetName, assetUrl, sha256));
        } catch (RuntimeException e) {
            BetterUCMod.LOGGER.warn("Could not parse betterUC latest release JSON", e);
            return Optional.empty();
        }
    }

    private static String jsonString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return "";
        }

        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }

        try {
            return element.getAsString().trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static boolean isBetterUcJarAsset(String name, String downloadUrl) {
        String value = (name == null ? "" : name) + " " + (downloadUrl == null ? "" : downloadUrl);
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("betteruc")
                && lower.endsWith(".jar")
                && !lower.contains("sources")
                && !lower.contains("dev")
                && !lower.contains("-all");
    }

    private static boolean looksLikeJarDownloadUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            return URI.create(value).getPath().toLowerCase(Locale.ROOT).endsWith(".jar");
        } catch (RuntimeException e) {
            return value.toLowerCase(Locale.ROOT).contains(".jar");
        }
    }

    private static URI websiteReleaseApi() {
        String minecraftVersion = getMinecraftVersion();
        String query = "target=" + urlEncode(preferredReleaseTarget())
                + "&mc=" + urlEncode(minecraftVersion);
        return URI.create(WEBSITE_RELEASE_API + "?" + query);
    }

    private static String preferredReleaseTarget() {
        String minecraftVersion = getMinecraftVersion().toLowerCase(Locale.ROOT);
        if (minecraftVersion.startsWith("1.21.10")) {
            return "mc1.21.10";
        }
        if (minecraftVersion.startsWith("26.")) {
            return "mc26.x";
        }
        return "mc26.x";
    }

    private static String getMinecraftVersion() {
        return FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String assetTargetValue(String name, String downloadUrl) {
        return ((name == null ? "" : name) + " " + (downloadUrl == null ? "" : downloadUrl))
                .toLowerCase(Locale.ROOT);
    }

    private static boolean matchesReleaseTarget(String lowerValue, String target) {
        String value = lowerValue == null ? "" : lowerValue.toLowerCase(Locale.ROOT);
        String cleanedTarget = target == null ? "" : target.toLowerCase(Locale.ROOT);
        if ("mc1.21.10".equals(cleanedTarget)) {
            return value.contains("mc1.21.10")
                    || value.contains("mc1_21_10")
                    || value.contains("1.21.10");
        }
        if ("mc26.x".equals(cleanedTarget)) {
            return value.contains("mc26.x")
                    || value.contains("mc26x")
                    || value.contains("mc26-")
                    || value.contains("mc26_")
                    || (value.contains("mc26") && !value.contains("mc1.21") && !value.contains("1.21.10"));
        }
        return false;
    }

    private static boolean hasReleaseTargetMarker(String lowerValue) {
        String value = lowerValue == null ? "" : lowerValue.toLowerCase(Locale.ROOT);
        return value.contains("mc26")
                || value.contains("mc1.21")
                || value.contains("mc1_21")
                || value.contains("1.21.10");
    }

    private static Optional<String> fetchVersionFromGradleProperties(URI uri) {
        return fetchText(uri).flatMap(body -> findFirst(MOD_VERSION_PATTERN, body));
    }

    private static Optional<String> fetchText(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "betterUC-version-checker")
                .GET()
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return Optional.ofNullable(response.body());
        } catch (IOException e) {
            BetterUCMod.LOGGER.warn("Could not fetch betterUC version info from {}", uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            BetterUCMod.LOGGER.warn("Interrupted while checking betterUC version from {}", uri, e);
        }

        return Optional.empty();
    }

    private static Optional<String> findFirst(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1).trim());
    }

    private static PrepareOutcome prepareDownload(LatestVersion latestVersion) {
        PreparedUpdate existing = preparedUpdate;
        if (existing != null
                && normalizeVersion(existing.latestVersion().version())
                .equalsIgnoreCase(normalizeVersion(latestVersion.version()))) {
            return PrepareOutcome.prepared();
        }
        if (!latestVersion.hasJarAsset()) {
            return PrepareOutcome.message("\u00A7e[betterUC] Update verf\u00FCgbar, aber es wurde keine betterUC-JAR gefunden.\n"
                    + "\u00A77Download: \u00A7b" + latestVersion.url());
        }

        try {
            Path currentJar = currentModJarPath();
            if (!Files.isRegularFile(currentJar)) {
                return PrepareOutcome.message("\u00A7c[betterUC] Auto-Updater kann nur aus einer geladenen Mod-JAR heraus installieren.");
            }

            List<Path> installDirectories = findInstallDirectories(currentJar);
            if (installDirectories.isEmpty()) {
                return PrepareOutcome.message("\u00A7c[betterUC] Mods-Ordner konnte nicht erkannt werden.");
            }

            String targetName = sanitizeJarName(latestVersion);
            Path stagingDir = stableStagingDirectory();
            Files.createDirectories(stagingDir);
            Path downloadedJar = stagingDir.resolve(targetName);
            downloadJar(latestVersion, downloadedJar);

            try {
                validateDownloadedJar(downloadedJar, latestVersion);
            } catch (IOException validationError) {
                Files.deleteIfExists(downloadedJar);
                throw validationError;
            }

            RestartCommand restartCommand = captureRestartCommand().orElse(null);
            preparedUpdate = new PreparedUpdate(
                    latestVersion,
                    stagingDir,
                    List.copyOf(installDirectories),
                    downloadedJar,
                    targetName,
                    restartCommand
            );
            BetterUCMod.LOGGER.info("Prepared betterUC update for {} install director{}: {}",
                    installDirectories.size(), installDirectories.size() == 1 ? "y" : "ies", installDirectories);
            return PrepareOutcome.prepared();
        } catch (Exception e) {
            BetterUCMod.LOGGER.warn("Could not prepare betterUC install", e);
            return PrepareOutcome.message("\u00A7c[betterUC] Auto-Update fehlgeschlagen: " + safeError(e));
        }
    }

    private static void downloadJar(LatestVersion latestVersion, Path downloadedJar) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(latestVersion.assetDownloadUrl()))
                .timeout(Duration.ofSeconds(45))
                .header("User-Agent", "betterUC-auto-updater")
                .GET()
                .build();
        HttpResponse<Path> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(
                downloadedJar,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        ));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(downloadedJar);
            throw new IOException("Download HTTP " + response.statusCode());
        }
    }

    private static void validateDownloadedJar(Path downloadedJar, LatestVersion latestVersion) throws IOException {
        if (!Files.isRegularFile(downloadedJar) || Files.size(downloadedJar) < MIN_JAR_SIZE_BYTES) {
            throw new IOException("Download war ung\u00FCltig oder unvollst\u00E4ndig");
        }

        String expectedHash = normalizeSha256(latestVersion.sha256());
        if (!expectedHash.isBlank()) {
            String actualHash = sha256(downloadedJar);
            if (!expectedHash.equalsIgnoreCase(actualHash)) {
                throw new IOException("SHA-256-Pr\u00FCfung fehlgeschlagen");
            }
        }

        try (ZipFile zip = new ZipFile(downloadedJar.toFile())) {
            ZipEntry metadataEntry = zip.getEntry("fabric.mod.json");
            if (metadataEntry == null) {
                throw new IOException("fabric.mod.json fehlt");
            }
            JsonObject metadata;
            try (var stream = zip.getInputStream(metadataEntry);
                 var reader = new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)) {
                metadata = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (RuntimeException e) {
                throw new IOException("fabric.mod.json ist ung\u00FCltig", e);
            }

            if (!BetterUCMod.MOD_ID.equals(jsonString(metadata, "id"))) {
                throw new IOException("Die heruntergeladene JAR ist nicht betterUC");
            }
            String jarVersion = normalizeVersion(jsonString(metadata, "version"));
            if (!normalizeVersion(latestVersion.version()).equalsIgnoreCase(jarVersion)) {
                throw new IOException("JAR-Version stimmt nicht mit dem Release \u00FCberein");
            }
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 ist nicht verf\u00FCgbar", e);
        }
    }

    private static Path currentModJarPath() throws IOException {
        return FabricLoader.getInstance()
                .getModContainer(BetterUCMod.MOD_ID)
                .flatMap(container -> container.getOrigin().getPaths().stream()
                        .filter(path -> Files.isRegularFile(path) && path.toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .findFirst())
                .orElseThrow(() -> new IOException("Loaded betterUC jar path not found"));
    }

    private static Path stableStagingDirectory() {
        String userHome = System.getProperty("user.home", "").trim();
        if (!userHome.isEmpty()) {
            return Path.of(userHome).resolve(".betteruc").resolve("updates");
        }
        return FabricLoader.getInstance().getConfigDir().resolve("betteruc-updates");
    }

    private static List<Path> findInstallDirectories(Path currentJar) throws IOException {
        Set<Path> directories = new LinkedHashSet<>();
        Path currentModsDirectory = currentJar.toAbsolutePath().normalize().getParent();
        addInstallDirectory(directories, currentModsDirectory, true);

        if (!isLabyModPath(currentJar)) {
            return List.copyOf(directories);
        }

        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            return List.copyOf(directories);
        }

        String minecraftVersion = getMinecraftVersion();
        Path roamingDirectory = Path.of(appData).toAbsolutePath().normalize();
        collectLabyModpackDirectories(
                roamingDirectory.resolve(".minecraft").resolve("labymod-neo").resolve("modpacks"),
                minecraftVersion,
                directories
        );
        collectLabyInstanceDirectories(
                roamingDirectory.resolve("LabyMod").resolve("instances"),
                minecraftVersion,
                directories
        );
        return List.copyOf(directories);
    }

    private static void collectLabyModpackDirectories(Path modpacksRoot, String minecraftVersion,
                                                       Set<Path> directories) throws IOException {
        if (!Files.isDirectory(modpacksRoot)) {
            return;
        }

        try (var modpacks = Files.list(modpacksRoot)) {
            for (Path modpack : modpacks.filter(Files::isDirectory).toList()) {
                addInstallDirectory(
                        directories,
                        modpack.resolve("fabric").resolve(minecraftVersion).resolve("mods"),
                        false
                );
            }
        }
    }

    private static void collectLabyInstanceDirectories(Path instancesRoot, String minecraftVersion,
                                                        Set<Path> directories) throws IOException {
        if (!Files.isDirectory(instancesRoot)) {
            return;
        }

        try (var instances = Files.list(instancesRoot)) {
            for (Path instance : instances.filter(Files::isDirectory).toList()) {
                addInstallDirectory(
                        directories,
                        instance.resolve("loader").resolve("fabric").resolve(minecraftVersion).resolve("mods"),
                        false
                );
            }
        }
    }

    private static void addInstallDirectory(Set<Path> directories, Path directory, boolean required) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            if (required) {
                throw new IOException("Loaded betterUC mods directory not found");
            }
            return;
        }

        Path normalized = directory.toAbsolutePath().normalize();
        if (required || containsBetterUcJar(normalized)) {
            directories.add(normalized);
        }
    }

    private static boolean containsBetterUcJar(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.anyMatch(path -> {
                if (!Files.isRegularFile(path)) {
                    return false;
                }
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.startsWith("betteruc-") && name.endsWith(".jar");
            });
        }
    }

    private static boolean isLabyModPath(Path path) {
        String normalized = path.toAbsolutePath().normalize().toString()
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);
        return normalized.contains("/labymod/") || normalized.contains("/labymod-neo/");
    }

    private static String sanitizeJarName(LatestVersion latestVersion) {
        String name = latestVersion.assetName();
        if (name == null || name.isBlank()) {
            name = "betterUC-" + normalizeVersion(latestVersion.version()) + ".jar";
        }

        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            name += ".jar";
        }
        return name;
    }

    private static Optional<RestartCommand> captureRestartCommand() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        String command = info.command().orElse("").trim();
        String[] arguments = info.arguments().orElse(new String[0]);
        if (command.isBlank() || arguments.length == 0) {
            return Optional.empty();
        }

        String workingDirectory = System.getProperty("user.dir", "").trim();
        if (workingDirectory.isBlank()) {
            workingDirectory = Path.of(".").toAbsolutePath().normalize().toString();
        }
        return Optional.of(new RestartCommand(command, List.of(arguments), workingDirectory));
    }

    private static Path createInstallScript(Path stagingDir, List<Path> installDirectories,
                                            Path downloadedJar, String targetName,
                                            RestartCommand restartCommand) throws IOException {
        if (isWindows()) {
            Path script = stagingDir.resolve("install-betteruc-update.ps1");
            Path logFile = stagingDir.resolve("install-betteruc-update.log");
            Path backupDir = stagingDir.resolve("backup");
            String restartBlock = createPowerShellRestartBlock(restartCommand);
            String content = "$ErrorActionPreference = 'Stop'\r\n"
                    + "$pidToWait = " + ProcessHandle.current().pid() + "\r\n"
                    + "$modsDirs = @(" + joinPowerShellPaths(installDirectories) + ")\r\n"
                    + "$downloadedJar = " + psQuote(downloadedJar) + "\r\n"
                    + "$targetName = " + psQuote(targetName) + "\r\n"
                    + "$logFile = " + psQuote(logFile) + "\r\n"
                    + "$backupDir = " + psQuote(backupDir) + "\r\n"
                    + "function Write-Log($message) { Add-Content -LiteralPath $logFile -Value ((Get-Date -Format o) + ' ' + $message) }\r\n"
                    + "Write-Log 'Waiting for Minecraft process to exit.'\r\n"
                    + "while (Get-Process -Id $pidToWait -ErrorAction SilentlyContinue) { Start-Sleep -Milliseconds 750 }\r\n"
                    + "Start-Sleep -Seconds 2\r\n"
                    + "New-Item -ItemType Directory -Path $backupDir -Force | Out-Null\r\n"
                    + "$installed = 0\r\n"
                    + "$directoryIndex = 0\r\n"
                    + "foreach ($modsDir in $modsDirs) {\r\n"
                    + "  try {\r\n"
                    + "    if (-not (Test-Path -LiteralPath $modsDir -PathType Container)) { Write-Log ('Skipped missing directory ' + $modsDir); continue }\r\n"
                    + "    $targetJar = Join-Path $modsDir $targetName\r\n"
                    + "    $temporaryJar = $targetJar + '.new'\r\n"
                    + "    Copy-Item -LiteralPath $downloadedJar -Destination $temporaryJar -Force\r\n"
                    + "    $oldJars = @(Get-ChildItem -LiteralPath $modsDir -Filter 'betterUC-*.jar' -File -ErrorAction SilentlyContinue)\r\n"
                    + "    foreach ($oldJar in $oldJars) {\r\n"
                    + "      $backupName = ($directoryIndex.ToString() + '-' + $oldJar.Name)\r\n"
                    + "      Copy-Item -LiteralPath $oldJar.FullName -Destination (Join-Path $backupDir $backupName) -Force\r\n"
                    + "    }\r\n"
                    + "    $oldJars | Remove-Item -Force\r\n"
                    + "    Move-Item -LiteralPath $temporaryJar -Destination $targetJar -Force\r\n"
                    + "    $installed++\r\n"
                    + "    $directoryIndex++\r\n"
                    + "    Write-Log ('Installed ' + $targetJar)\r\n"
                    + "  } catch { Write-Log ('Failed ' + $modsDir + ': ' + $_.Exception.Message) }\r\n"
                    + "}\r\n"
                    + "if ($installed -eq 0) { throw 'No betterUC installation could be updated.' }\r\n"
                    + "Remove-Item -LiteralPath $downloadedJar -Force -ErrorAction SilentlyContinue\r\n"
                    + "Write-Log ('Update completed for ' + $installed + ' installation(s).')\r\n"
                    + restartBlock;
            Files.writeString(script, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return script;
        }

        Path script = stagingDir.resolve("install-betteruc-update.sh");
        Path logFile = stagingDir.resolve("install-betteruc-update.log");
        String installCalls = createShellInstallCalls(installDirectories);
        String restartBlock = createShellRestartBlock(restartCommand);
        String content = "#!/bin/sh\n"
                + "set -eu\n"
                + "pid_to_wait=" + ProcessHandle.current().pid() + "\n"
                + "downloaded_jar=" + shQuote(downloadedJar.toString()) + "\n"
                + "target_name=" + shQuote(targetName) + "\n"
                + "log_file=" + shQuote(logFile.toString()) + "\n"
                + "installed=0\n"
                + "install_into() {\n"
                + "  mods_dir=\"$1\"\n"
                + "  if [ ! -d \"$mods_dir\" ]; then echo \"$(date -Iseconds) Skipped missing directory $mods_dir\" >> \"$log_file\"; return; fi\n"
                + "  target_jar=\"$mods_dir/$target_name\"\n"
                + "  rm -f \"$mods_dir\"/betterUC-*.jar\n"
                + "  cp \"$downloaded_jar\" \"$target_jar\"\n"
                + "  installed=$((installed + 1))\n"
                + "  echo \"$(date -Iseconds) Installed $target_jar\" >> \"$log_file\"\n"
                + "}\n"
                + "echo \"$(date -Iseconds) Waiting for Minecraft process to exit.\" >> \"$log_file\"\n"
                + "while kill -0 \"$pid_to_wait\" 2>/dev/null; do sleep 1; done\n"
                + "sleep 2\n"
                + installCalls
                + "if [ \"$installed\" -eq 0 ]; then echo \"$(date -Iseconds) No betterUC installation could be updated.\" >> \"$log_file\"; exit 1; fi\n"
                + "rm -f \"$downloaded_jar\"\n"
                + "echo \"$(date -Iseconds) Update completed for $installed installation(s).\" >> \"$log_file\"\n"
                + restartBlock;
        Files.writeString(script, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        script.toFile().setExecutable(true);
        return script;
    }

    private static String joinPowerShellPaths(List<Path> paths) {
        List<String> quotedPaths = new ArrayList<>();
        for (Path path : paths) {
            quotedPaths.add(psQuote(path));
        }
        return String.join(", ", quotedPaths);
    }

    private static String createPowerShellRestartBlock(RestartCommand restartCommand) {
        if (restartCommand == null) {
            return "Write-Log 'Automatic restart is unavailable; waiting for a manual start.'\r\n"
                    + "Remove-Item -LiteralPath $MyInvocation.MyCommand.Path -Force -ErrorAction SilentlyContinue\r\n";
        }

        List<String> quotedArguments = new ArrayList<>();
        for (String argument : restartCommand.arguments()) {
            quotedArguments.add(psQuote(argument));
        }
        return "$restartCommand = " + psQuote(restartCommand.command()) + "\r\n"
                + "$restartArgs = @(" + String.join(", ", quotedArguments) + ")\r\n"
                + "$restartDir = " + psQuote(restartCommand.workingDirectory()) + "\r\n"
                + "try {\r\n"
                + "  Set-Location -LiteralPath $restartDir\r\n"
                + "  Write-Log ('Restarting Minecraft with ' + $restartCommand)\r\n"
                + "  Remove-Item -LiteralPath $MyInvocation.MyCommand.Path -Force -ErrorAction SilentlyContinue\r\n"
                + "  & $restartCommand @restartArgs\r\n"
                + "} catch { Write-Log ('Automatic restart failed: ' + $_.Exception.Message) }\r\n";
    }

    private static String createShellRestartBlock(RestartCommand restartCommand) {
        if (restartCommand == null) {
            return "echo \"$(date -Iseconds) Automatic restart is unavailable; waiting for a manual start.\" >> \"$log_file\"\n"
                    + "rm -f \"$0\"\n";
        }

        StringBuilder command = new StringBuilder();
        command.append(shQuote(restartCommand.command()));
        for (String argument : restartCommand.arguments()) {
            command.append(' ').append(shQuote(argument));
        }
        return "cd " + shQuote(restartCommand.workingDirectory()) + "\n"
                + "rm -f \"$0\"\n"
                + "nohup " + command + " >/dev/null 2>&1 &\n"
                + "echo \"$(date -Iseconds) Minecraft restart launched.\" >> \"$log_file\"\n";
    }

    private static String createShellInstallCalls(List<Path> paths) {
        StringBuilder calls = new StringBuilder();
        for (Path path : paths) {
            calls.append("install_into ").append(shQuote(path.toString())).append("\n");
        }
        return calls.toString();
    }

    private static boolean startInstallScript(Path script) {
        try {
            ProcessBuilder builder;
            if (isWindows()) {
                builder = new ProcessBuilder(
                        "powershell.exe",
                        "-NoProfile",
                        "-ExecutionPolicy",
                        "Bypass",
                        "-WindowStyle",
                        "Hidden",
                        "-File",
                        script.toString()
                );
            } else {
                builder = new ProcessBuilder("sh", script.toString());
            }
            builder.start();
            return true;
        } catch (IOException e) {
            BetterUCMod.LOGGER.warn("Could not start betterUC install script {}", script, e);
            return false;
        }
    }

    private static String psQuote(Path path) {
        return psQuote(path.toString());
    }

    private static String psQuote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String shQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String safeError(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static String getCurrentVersion() {
        return FabricLoader.getInstance()
                .getModContainer(BetterUCMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static boolean isRemoteNewer(String currentVersion, String remoteVersion) {
        String current = normalizeVersion(currentVersion);
        String remote = normalizeVersion(remoteVersion);
        if (current.isBlank() || remote.isBlank() || "unknown".equalsIgnoreCase(current)) {
            return false;
        }

        String[] currentParts = current.split("[.-]");
        String[] remoteParts = remote.split("[.-]");
        int length = Math.max(currentParts.length, remoteParts.length);
        for (int i = 0; i < length; i++) {
            int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
            int remotePart = i < remoteParts.length ? parseVersionPart(remoteParts[i]) : 0;
            if (remotePart != currentPart) {
                return remotePart > currentPart;
            }
        }

        return false;
    }

    private static int parseVersionPart(String value) {
        Matcher matcher = Pattern.compile("^\\d+").matcher(value);
        if (!matcher.find()) {
            return 0;
        }

        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }

        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }

        int buildMetadataIndex = normalized.indexOf('+');
        if (buildMetadataIndex >= 0) {
            normalized = normalized.substring(0, buildMetadataIndex);
        }

        return normalized.trim();
    }

    private static String normalizeSha256(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("sha256:")) {
            normalized = normalized.substring("sha256:".length());
        }
        return normalized.matches("[0-9a-f]{64}") ? normalized : "";
    }

    private static Component buildUpdateMessage(String currentVersion, LatestVersion latestVersion) {
        String normalizedCurrent = normalizeVersion(currentVersion);
        String normalizedLatest = normalizeVersion(latestVersion.version());
        MutableComponent message = Component.literal("\u00A7e[betterUC] Update verf\u00FCgbar! \u00A77Du nutzt \u00A7c"
                + normalizedCurrent + "\u00A77, aktuell ist \u00A7a" + normalizedLatest + "\u00A77.\n");
        MutableComponent link = Component.literal("\u00A7b" + latestVersion.url())
                .setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(latestVersion.url()))));

        message.append(Component.literal("\u00A77Details: ")).append(link);
        if (latestVersion.hasJarAsset()) {
            MutableComponent command = Component.literal("\n\u00A7a[Jetzt installieren]")
                    .setStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/betterucupdate")));
            message.append(Component.literal("\u00A77 ")).append(command);
        }
        return message;
    }

    private static void sendReadyMessage(Minecraft client) {
        if (client == null || client.player == null || preparedUpdate == null) return;
        MutableComponent message = Component.literal("\u00A7a[betterUC] Update \u00A7f"
                + preparedVersion() + "\u00A7a ist bereit. ");
        MutableComponent action = Component.literal("\u00A7e[Installieren & neu starten]")
                .setStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/betterucupdate")));
        client.player.sendSystemMessage(message.append(action));
    }

    private static void sendLocalMessage(Minecraft client, String message) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }

    private record LatestVersion(String version, String url, String assetName, String assetDownloadUrl, String sha256) {
        private boolean hasJarAsset() {
            return assetDownloadUrl != null && !assetDownloadUrl.isBlank();
        }
    }

    private record PreparedUpdate(
            LatestVersion latestVersion,
            Path stagingDirectory,
            List<Path> installDirectories,
            Path downloadedJar,
            String targetName,
            RestartCommand restartCommand
    ) {
    }

    private record RestartCommand(String command, List<String> arguments, String workingDirectory) {
    }

    private record PrepareOutcome(boolean ready, String message) {
        private static PrepareOutcome prepared() {
            return new PrepareOutcome(true, "");
        }

        private static PrepareOutcome message(String message) {
            return new PrepareOutcome(false, message == null ? "" : message);
        }
    }
}
