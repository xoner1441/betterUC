package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static volatile boolean installPreparedThisSession = false;
    private static volatile boolean notifiedThisSession = false;
    private static volatile long lastCheckMs = 0L;

    private VersionChecker() {
    }

    public static void checkOnJoin(Minecraft client) {
        if (client == null || notifiedThisSession || checkRunning) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastCheckMs < CHECK_COOLDOWN_MS) {
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
                        if (client.player == null || notifiedThisSession) {
                            return;
                        }

                        notifiedThisSession = true;
                        client.player.sendSystemMessage(buildUpdateMessage(currentVersion, latestVersion));
                        if (BetterUCConfig.INSTANCE.autoUpdateEnabled && latestVersion.hasJarAsset()) {
                            installUpdate(client, latestVersion, false);
                        }
                    });
                });
    }

    public static void installLatestUpdate(Minecraft client, boolean manual) {
        if (client == null) {
            return;
        }
        if (installRunning) {
            sendLocalMessage(client, "\u00A7e[betterUC] Auto-Updater l\u00E4uft bereits.");
            return;
        }

        if (manual) {
            sendLocalMessage(client, "\u00A77[betterUC] Suche nach neuer Version...");
        }

        installRunning = true;
        CompletableFuture.supplyAsync(() -> {
                    LatestVersion latestVersion = fetchLatestVersion();
                    if (latestVersion == null || latestVersion.version().isBlank()) {
                        return InstallOutcome.message("\u00A7c[betterUC] Konnte keine aktuelle betterUC-Version finden.");
                    }

                    String currentVersion = getCurrentVersion();
                    if (!isRemoteNewer(currentVersion, latestVersion.version())) {
                        return InstallOutcome.message("\u00A7a[betterUC] Du nutzt bereits die aktuelle Version \u00A7f"
                                + normalizeVersion(currentVersion) + "\u00A7a.");
                    }

                    return prepareInstall(latestVersion);
                })
                .whenComplete((outcome, error) -> {
                    installRunning = false;
                    client.execute(() -> {
                        if (error != null) {
                            BetterUCMod.LOGGER.warn("Could not prepare betterUC auto update", error);
                            sendLocalMessage(client, "\u00A7c[betterUC] Auto-Update fehlgeschlagen: " + safeError(error));
                            return;
                        }
                        if (outcome == null || outcome.message().isBlank()) {
                            return;
                        }
                        sendLocalMessage(client, outcome.message());
                    });
                });
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
            return new LatestVersion(mainVersion.get(), REPO_URL, "", "");
        }

        return fetchVersionFromGradleProperties(RAW_GRADLE_PROPERTIES_MASTER)
                .map(version -> new LatestVersion(version, REPO_URL, "", ""))
                .orElse(null);
    }

    private static Optional<LatestVersion> fetchLatestWebsiteRelease() {
        String body = fetchText(WEBSITE_RELEASE_API).orElse("");
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
            if (!downloadUrl.isBlank() && !downloadUrl.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                downloadUrl = "";
            }

            return Optional.of(new LatestVersion(version, pageUrl, assetName, downloadUrl));
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
        return Optional.of(new LatestVersion(tagName, url, "", ""));
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

            String assetName = "";
            String assetUrl = "";
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
                if (isBetterUcJarAsset(name, downloadUrl)) {
                    assetName = name;
                    assetUrl = downloadUrl;
                    break;
                }
            }

            return Optional.of(new LatestVersion(tagName, url, assetName, assetUrl));
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

    private static InstallOutcome prepareInstall(LatestVersion latestVersion) {
        if (installPreparedThisSession) {
            return InstallOutcome.message("\u00A7e[betterUC] Update wurde bereits vorbereitet. Bitte Minecraft komplett schlie\u00DFen und neu starten.");
        }
        if (!latestVersion.hasJarAsset()) {
            return InstallOutcome.message("\u00A7e[betterUC] Update verf\u00FCgbar, aber es wurde keine betterUC-JAR gefunden.\n"
                    + "\u00A77Download: \u00A7b" + latestVersion.url());
        }

        try {
            Path currentJar = currentModJarPath();
            if (!Files.isRegularFile(currentJar)) {
                return InstallOutcome.message("\u00A7c[betterUC] Auto-Updater kann nur aus einer geladenen Mod-JAR heraus installieren.");
            }

            Path modsDir = currentJar.getParent();
            if (modsDir == null || !Files.isDirectory(modsDir)) {
                return InstallOutcome.message("\u00A7c[betterUC] Mods-Ordner konnte nicht erkannt werden.");
            }

            String targetName = sanitizeJarName(latestVersion);
            Path stagingDir = FabricLoader.getInstance().getConfigDir().resolve("betteruc-updates");
            Files.createDirectories(stagingDir);
            Path downloadedJar = stagingDir.resolve(targetName);
            downloadJar(latestVersion, downloadedJar);

            if (!Files.isRegularFile(downloadedJar) || Files.size(downloadedJar) < MIN_JAR_SIZE_BYTES) {
                Files.deleteIfExists(downloadedJar);
                return InstallOutcome.message("\u00A7c[betterUC] Download war ung\u00FCltig oder unvollst\u00E4ndig.");
            }

            Path script = createInstallScript(stagingDir, modsDir, downloadedJar, targetName);
            if (!startInstallScript(script)) {
                return InstallOutcome.message("\u00A7c[betterUC] Update wurde heruntergeladen, aber das Install-Script konnte nicht gestartet werden.");
            }

            installPreparedThisSession = true;
            return InstallOutcome.message("\u00A7a[betterUC] Update \u00A7f" + normalizeVersion(latestVersion.version())
                    + "\u00A7a wurde heruntergeladen.\n"
                    + "\u00A7eSchlie\u00DFe Minecraft komplett und starte danach neu, damit die neue Version greift.");
        } catch (Exception e) {
            BetterUCMod.LOGGER.warn("Could not prepare betterUC install", e);
            return InstallOutcome.message("\u00A7c[betterUC] Auto-Update fehlgeschlagen: " + safeError(e));
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

    private static Path currentModJarPath() throws IOException {
        return FabricLoader.getInstance()
                .getModContainer(BetterUCMod.MOD_ID)
                .flatMap(container -> container.getOrigin().getPaths().stream()
                        .filter(path -> Files.isRegularFile(path) && path.toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .findFirst())
                .orElseThrow(() -> new IOException("Loaded betterUC jar path not found"));
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

    private static Path createInstallScript(Path stagingDir, Path modsDir, Path downloadedJar, String targetName) throws IOException {
        if (isWindows()) {
            Path script = stagingDir.resolve("install-betteruc-update.ps1");
            Path logFile = stagingDir.resolve("install-betteruc-update.log");
            String content = "$ErrorActionPreference = 'Stop'\r\n"
                    + "$pidToWait = " + ProcessHandle.current().pid() + "\r\n"
                    + "$modsDir = " + psQuote(modsDir) + "\r\n"
                    + "$downloadedJar = " + psQuote(downloadedJar) + "\r\n"
                    + "$targetJar = Join-Path $modsDir " + psQuote(targetName) + "\r\n"
                    + "$logFile = " + psQuote(logFile) + "\r\n"
                    + "function Write-Log($message) { Add-Content -LiteralPath $logFile -Value ((Get-Date -Format o) + ' ' + $message) }\r\n"
                    + "Write-Log 'Waiting for Minecraft process to exit.'\r\n"
                    + "while (Get-Process -Id $pidToWait -ErrorAction SilentlyContinue) { Start-Sleep -Milliseconds 750 }\r\n"
                    + "Start-Sleep -Seconds 2\r\n"
                    + "Write-Log 'Replacing betterUC jars.'\r\n"
                    + "Get-ChildItem -LiteralPath $modsDir -Filter 'betterUC-*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force\r\n"
                    + "Copy-Item -LiteralPath $downloadedJar -Destination $targetJar -Force\r\n"
                    + "Remove-Item -LiteralPath $downloadedJar -Force -ErrorAction SilentlyContinue\r\n"
                    + "Write-Log ('Installed ' + $targetJar)\r\n";
            Files.writeString(script, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return script;
        }

        Path script = stagingDir.resolve("install-betteruc-update.sh");
        Path logFile = stagingDir.resolve("install-betteruc-update.log");
        String content = "#!/bin/sh\n"
                + "set -eu\n"
                + "pid_to_wait=" + ProcessHandle.current().pid() + "\n"
                + "mods_dir=" + shQuote(modsDir.toString()) + "\n"
                + "downloaded_jar=" + shQuote(downloadedJar.toString()) + "\n"
                + "target_jar=\"$mods_dir/" + targetName.replace("\"", "") + "\"\n"
                + "log_file=" + shQuote(logFile.toString()) + "\n"
                + "echo \"$(date -Iseconds) Waiting for Minecraft process to exit.\" >> \"$log_file\"\n"
                + "while kill -0 \"$pid_to_wait\" 2>/dev/null; do sleep 1; done\n"
                + "sleep 2\n"
                + "rm -f \"$mods_dir\"/betterUC-*.jar\n"
                + "cp \"$downloaded_jar\" \"$target_jar\"\n"
                + "rm -f \"$downloaded_jar\"\n"
                + "echo \"$(date -Iseconds) Installed $target_jar\" >> \"$log_file\"\n";
        Files.writeString(script, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        script.toFile().setExecutable(true);
        return script;
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

    private static Component buildUpdateMessage(String currentVersion, LatestVersion latestVersion) {
        String normalizedCurrent = normalizeVersion(currentVersion);
        String normalizedLatest = normalizeVersion(latestVersion.version());
        MutableComponent message = Component.literal("\u00A7e[betterUC] Update verf\u00FCgbar! \u00A77Du nutzt \u00A7c"
                + normalizedCurrent + "\u00A77, aktuell ist \u00A7a" + normalizedLatest + "\u00A77.\n");
        MutableComponent link = Component.literal("\u00A7b" + latestVersion.url())
                .setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(latestVersion.url()))));

        message.append(Component.literal("\u00A77Download: ")).append(link);
        if (latestVersion.hasJarAsset()) {
            MutableComponent command = Component.literal("\n\u00A7a[Auto installieren]")
                    .setStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/betterucupdate")));
            message.append(Component.literal("\u00A77 ")).append(command);
        }
        return message;
    }

    private static void installUpdate(Minecraft client, LatestVersion latestVersion, boolean manual) {
        if (client == null || latestVersion == null) {
            return;
        }
        if (installRunning) {
            return;
        }

        installRunning = true;
        if (manual) {
            sendLocalMessage(client, "\u00A77[betterUC] Update wird vorbereitet...");
        } else {
            sendLocalMessage(client, "\u00A77[betterUC] Auto-Updater l\u00E4dt die neue Version...");
        }

        CompletableFuture.supplyAsync(() -> prepareInstall(latestVersion))
                .whenComplete((outcome, error) -> {
                    installRunning = false;
                    client.execute(() -> {
                        if (error != null) {
                            BetterUCMod.LOGGER.warn("Could not install betterUC update", error);
                            sendLocalMessage(client, "\u00A7c[betterUC] Auto-Update fehlgeschlagen: " + safeError(error));
                            return;
                        }
                        if (outcome != null && !outcome.message().isBlank()) {
                            sendLocalMessage(client, outcome.message());
                        }
                    });
                });
    }

    private static void sendLocalMessage(Minecraft client, String message) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }

    private record LatestVersion(String version, String url, String assetName, String assetDownloadUrl) {
        private boolean hasJarAsset() {
            return assetDownloadUrl != null && !assetDownloadUrl.isBlank();
        }
    }

    private record InstallOutcome(String message) {
        private static InstallOutcome message(String message) {
            return new InstallOutcome(message == null ? "" : message);
        }
    }
}
