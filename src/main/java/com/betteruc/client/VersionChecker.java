package com.betteruc.client;

import com.betteruc.BetterUCMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionChecker {
    private static final String OWNER = "xoner1441";
    private static final String REPO = "betterUC";
    private static final String REPO_URL = "https://github.com/" + OWNER + "/" + REPO;
    private static final String LATEST_RELEASE_URL = REPO_URL + "/releases/latest";
    private static final URI LATEST_RELEASE_API = URI.create("https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest");
    private static final URI RAW_GRADLE_PROPERTIES_MAIN = URI.create("https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/main/gradle.properties");
    private static final URI RAW_GRADLE_PROPERTIES_MASTER = URI.create("https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/master/gradle.properties");
    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HTML_URL_PATTERN = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MOD_VERSION_PATTERN = Pattern.compile("(?m)^mod_version\\s*=\\s*([^\\r\\n]+)\\s*$");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final long CHECK_COOLDOWN_MS = 5 * 60 * 1000L;

    private static volatile boolean checkRunning = false;
    private static volatile boolean notifiedThisSession = false;
    private static volatile long lastCheckMs = 0L;

    private VersionChecker() {
    }

    public static void checkOnJoin(MinecraftClient client) {
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
                        client.player.sendMessage(buildUpdateMessage(currentVersion, latestVersion), false);
                    });
                });
    }

    private static LatestVersion fetchLatestVersion() {
        Optional<LatestVersion> release = fetchLatestRelease();
        if (release.isPresent()) {
            return release.get();
        }

        Optional<String> mainVersion = fetchVersionFromGradleProperties(RAW_GRADLE_PROPERTIES_MAIN);
        if (mainVersion.isPresent()) {
            return new LatestVersion(mainVersion.get(), REPO_URL);
        }

        return fetchVersionFromGradleProperties(RAW_GRADLE_PROPERTIES_MASTER)
                .map(version -> new LatestVersion(version, REPO_URL))
                .orElse(null);
    }

    private static Optional<LatestVersion> fetchLatestRelease() {
        String body = fetchText(LATEST_RELEASE_API).orElse("");
        String tagName = findFirst(TAG_NAME_PATTERN, body).orElse("");
        if (tagName.isBlank()) {
            return Optional.empty();
        }

        String url = findFirst(HTML_URL_PATTERN, body).orElse(LATEST_RELEASE_URL);
        return Optional.of(new LatestVersion(tagName, url));
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

    private static Text buildUpdateMessage(String currentVersion, LatestVersion latestVersion) {
        String normalizedCurrent = normalizeVersion(currentVersion);
        String normalizedLatest = normalizeVersion(latestVersion.version());
        MutableText message = Text.literal("\u00A7e[betterUC] Update verf\u00FCgbar! \u00A77Du nutzt \u00A7c"
                + normalizedCurrent + "\u00A77, aktuell ist \u00A7a" + normalizedLatest + "\u00A77.\n");
        MutableText link = Text.literal("\u00A7b" + latestVersion.url())
                .setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(latestVersion.url()))));

        return message.append(Text.literal("\u00A77Download: ")).append(link);
    }

    private record LatestVersion(String version, String url) {
    }
}
