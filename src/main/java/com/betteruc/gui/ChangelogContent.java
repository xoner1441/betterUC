package com.betteruc.gui;

import com.betteruc.BetterUCMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ChangelogContent {

    private static final String RESOURCE_PATH = "/betteruc/changelog.json";
    private static final Page[] LATEST_PAGES = loadLatestPages();

    private ChangelogContent() {
    }

    public static Page[] latestPages() {
        return Arrays.copyOf(LATEST_PAGES, LATEST_PAGES.length);
    }

    public static Page[] allPages() {
        return latestPages();
    }

    public static Page[] clickGuiSections() {
        return latestPages();
    }

    private static Page[] loadLatestPages() {
        try (InputStream stream = ChangelogContent.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                BetterUCMod.LOGGER.warn("Bundled changelog resource {} is missing", RESOURCE_PATH);
                return fallbackPages();
            }

            JsonElement parsed = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
            if (!parsed.isJsonObject()) return fallbackPages();

            JsonObject root = parsed.getAsJsonObject();
            JsonObject release = currentRelease(root);
            JsonArray pages = array(release, "pages");
            if (pages.isEmpty()) {
                pages = releasePages(release);
            }

            List<Page> result = new ArrayList<>();

            for (JsonElement element : pages) {
                if (!element.isJsonObject()) continue;
                JsonObject page = element.getAsJsonObject();
                String title = text(page, "title");
                if (title.isBlank()) continue;

                JsonArray lineElements = array(page, "lines");
                List<String> lines = new ArrayList<>();
                for (JsonElement line : lineElements) {
                    if (!line.isJsonPrimitive()) continue;
                    String value = line.getAsString().trim();
                    if (!value.isBlank()) lines.add(value);
                }

                result.add(new Page(
                        textOr(page, "eyebrow", "AKTUELLE ÄNDERUNGEN"),
                        title,
                        text(page, "description"),
                        lines.toArray(String[]::new)
                ));
            }

            return result.isEmpty() ? fallbackPages() : result.toArray(Page[]::new);
        } catch (Exception e) {
            BetterUCMod.LOGGER.warn("Could not load bundled betterUC changelog", e);
            return fallbackPages();
        }
    }

    private static JsonObject currentRelease(JsonObject root) {
        JsonArray releases = array(root, "releases");
        JsonObject firstRelease = null;

        for (JsonElement element : releases) {
            if (!element.isJsonObject()) continue;
            JsonObject release = element.getAsJsonObject();
            if (firstRelease == null) firstRelease = release;

            JsonElement current = release.get("current");
            if (current != null && current.isJsonPrimitive() && current.getAsBoolean()) {
                return release;
            }
        }

        return firstRelease == null ? new JsonObject() : firstRelease;
    }

    private static JsonArray releasePages(JsonObject release) {
        JsonArray changes = array(release, "changes");
        if (changes.isEmpty()) return new JsonArray();

        String version = textOr(release, "version", "betterUC");
        JsonObject page = new JsonObject();
        page.addProperty("eyebrow", "NEU IN " + version);
        page.addProperty("title", "Änderungen in Version " + version);
        page.addProperty("description", "Die wichtigsten Neuerungen und Verbesserungen dieses Updates.");
        page.add("lines", changes);

        JsonArray pages = new JsonArray();
        pages.add(page);
        return pages;
    }

    private static JsonObject object(JsonObject parent, String name) {
        JsonElement value = parent == null ? null : parent.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String name) {
        JsonElement value = parent == null ? null : parent.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String text(JsonObject parent, String name) {
        try {
            JsonElement value = parent == null ? null : parent.get(name);
            return value != null && value.isJsonPrimitive() ? value.getAsString().trim() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String textOr(JsonObject parent, String name, String fallback) {
        String value = text(parent, name);
        return value.isBlank() ? fallback : value;
    }

    private static Page[] fallbackPages() {
        return new Page[]{
                new Page(
                        "AKTUELLE ÄNDERUNGEN",
                        "Changelog nicht verfügbar",
                        "Die eingebetteten Änderungen konnten nicht geladen werden.",
                        new String[]{"Die vollständige Versionshistorie findest du auf betteruc.de/changelog."}
                )
        };
    }

    public record Page(String eyebrow, String title, String description, String[] lines) {
    }
}
