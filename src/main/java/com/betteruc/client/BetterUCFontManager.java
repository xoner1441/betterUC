package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.PackVersion;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BetterUCFontManager {
    public static final String DEFAULT_FONT_ID = "";

    private static final String PACK_FOLDER_NAME = "betteruc-fonts";
    private static final String PACK_PROFILE_ID = "file/" + PACK_FOLDER_NAME;
    private static final String NAMESPACE = "betteruc";
    private static final Path FONTS_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("betteruc")
            .resolve("fonts");
    private static final Path PACK_DIR = FabricLoader.getInstance()
            .getGameDir()
            .resolve("resourcepacks")
            .resolve(PACK_FOLDER_NAME);

    private static List<FontOption> fontOptions = List.of(FontOption.DEFAULT);
    private static boolean attemptedAutoEnable = false;

    private BetterUCFontManager() {
    }

    public static void initialize() {
        rebuildPack();
    }

    public static void tick(MinecraftClient client) {
        if (attemptedAutoEnable) return;
        attemptedAutoEnable = true;
        if (!hasCustomFonts()) return;

        enablePack(client, false);
    }

    public static Path getFontsDir() {
        ensureFontsDir();
        return FONTS_DIR;
    }

    public static List<FontOption> getFontOptions() {
        refreshFontOptions();
        return fontOptions;
    }

    public static boolean hasCustomFonts() {
        return !customFontOptions().isEmpty();
    }

    public static String selectedFontLabel() {
        return selectedFontLabel(BetterUCConfig.INSTANCE.customHudFont);
    }

    public static String selectedFontLabel(String fontId) {
        List<FontOption> customFonts = customFontOptions();
        if (customFonts.isEmpty()) {
            return "Keine Fonts";
        }

        String selected = normalizedFontId(fontId);
        for (FontOption option : customFonts) {
            if (option.id.equals(selected)) {
                return option.label;
            }
        }
        return "Auswählen";
    }

    public static String nextCustomFontId(String current) {
        List<FontOption> options = customFontOptions();
        if (options.isEmpty()) return DEFAULT_FONT_ID;

        String selected = normalizedFontId(current);
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id.equals(selected)) {
                return options.get((i + 1) % options.size()).id;
            }
        }
        return options.get(0).id;
    }

    public static Text applyCustomHudFont(Text text) {
        return applyCustomHudFont(text, BetterUCConfig.INSTANCE.customHudFont);
    }

    public static Text applyCustomHudFont(Text text, String fontId) {
        Identifier identifier = selectedFontIdentifier(fontId);
        if (identifier == null) {
            return text;
        }
        return Text.literal(text == null ? "" : text.getString())
                .setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(identifier)));
    }

    public static String sanitizeFontId(String fontId) {
        return sanitizeFontChoice(fontId, DEFAULT_FONT_ID);
    }

    public static void rebuildAndReload(MinecraftClient client) {
        rebuildPack();
        enablePack(client, true);
    }

    public static void openFontsFolder(MinecraftClient client) {
        Path dir = getFontsDir();
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir.toFile());
                return;
            }
        } catch (Exception e) {
            BetterUCMod.LOGGER.warn("Could not open betterUC font folder {}", dir, e);
        }

        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("betterUC Fonts: " + dir), false);
        }
    }

    private static Identifier selectedFontIdentifier(String fontId) {
        String selected = normalizedFontId(fontId);
        if (selected.isEmpty()) return null;

        for (FontOption option : getFontOptions()) {
            if (option.id.equals(selected)) {
                return option.identifier;
            }
        }
        return null;
    }

    private static void rebuildPack() {
        ensureFontsDir();
        try {
            deleteGeneratedPack();
            Files.createDirectories(PACK_DIR.resolve("assets").resolve(NAMESPACE).resolve("font").resolve("custom"));
            Files.writeString(PACK_DIR.resolve("pack.mcmeta"), packMcmeta(), StandardCharsets.UTF_8);

            List<FontSource> sources = discoverFontSources();
            for (FontSource source : sources) {
                Path fontTarget = PACK_DIR
                        .resolve("assets")
                        .resolve(NAMESPACE)
                        .resolve("font")
                        .resolve("custom")
                        .resolve(source.resourceFileName);
                Files.copy(source.sourcePath, fontTarget, StandardCopyOption.REPLACE_EXISTING);

                Path jsonTarget = PACK_DIR
                        .resolve("assets")
                        .resolve(NAMESPACE)
                        .resolve("font")
                        .resolve(source.idPath + ".json");
                Files.writeString(jsonTarget, fontJson(source), StandardCharsets.UTF_8);
            }

            refreshFontOptions(sources);
            sanitizeSelectedFonts();
            BetterUCConfig.save();
        } catch (IOException e) {
            BetterUCMod.LOGGER.error("Failed to rebuild betterUC font resource pack", e);
            refreshFontOptions(List.of());
        }
    }

    private static void enablePack(MinecraftClient client, boolean forceReload) {
        if (client == null) return;
        ResourcePackManager manager = client.getResourcePackManager();
        manager.scanPacks();

        if (!manager.hasProfile(PACK_PROFILE_ID)) {
            BetterUCMod.LOGGER.warn("betterUC font resource pack profile {} is not available", PACK_PROFILE_ID);
            return;
        }

        boolean enabled = manager.getEnabledIds().contains(PACK_PROFILE_ID);
        if (!enabled && manager.enable(PACK_PROFILE_ID)) {
            client.options.refreshResourcePacks(manager);
            return;
        }

        if (forceReload) {
            client.reloadResources();
        }
    }

    private static void refreshFontOptions() {
        refreshFontOptions(discoverFontSources());
    }

    private static void refreshFontOptions(List<FontSource> sources) {
        List<FontOption> options = new ArrayList<>();
        options.add(FontOption.DEFAULT);
        for (FontSource source : sources) {
            options.add(new FontOption(
                    NAMESPACE + ":" + source.idPath,
                    source.label,
                    Identifier.of(NAMESPACE, source.idPath)
            ));
        }
        fontOptions = List.copyOf(options);
        sanitizeSelectedFonts();
    }

    private static List<FontOption> customFontOptions() {
        List<FontOption> options = getFontOptions();
        if (options.size() <= 1) return List.of();
        return options.subList(1, options.size());
    }

    private static List<FontSource> discoverFontSources() {
        ensureFontsDir();
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(FONTS_DIR)) {
            stream.filter(Files::isRegularFile)
                    .filter(BetterUCFontManager::isFontFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(files::add);
        } catch (IOException e) {
            BetterUCMod.LOGGER.warn("Could not list betterUC font folder {}", FONTS_DIR, e);
        }

        List<FontSource> sources = new ArrayList<>();
        Set<String> usedIds = new LinkedHashSet<>();
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String extension = extension(fileName);
            String baseName = fileName.substring(0, fileName.length() - extension.length() - 1);
            String idPath = uniqueIdPath(sanitizePath(baseName), usedIds);
            sources.add(new FontSource(file, fileName, idPath, idPath + "." + extension));
        }
        return sources;
    }

    private static void sanitizeSelectedFonts() {
        String legacy = normalizedFontId(BetterUCConfig.INSTANCE.customHudFont);
        if (legacy.isEmpty()
                && BetterUCConfig.INSTANCE.cartoonHudFont != null
                && !BetterUCConfig.INSTANCE.cartoonHudFont.isBlank()) {
            legacy = normalizedFontId(BetterUCConfig.INSTANCE.cartoonHudFont);
        }

        legacy = sanitizeFontChoice(legacy, DEFAULT_FONT_ID);
        BetterUCConfig.INSTANCE.customHudFont = legacy;
        BetterUCConfig.INSTANCE.healthHudCustomFont = sanitizeModuleFont(BetterUCConfig.INSTANCE.healthHudCustomFont, legacy);
        BetterUCConfig.INSTANCE.toggleSprintHudCustomFont = sanitizeModuleFont(BetterUCConfig.INSTANCE.toggleSprintHudCustomFont, legacy);
        BetterUCConfig.INSTANCE.fpsHudCustomFont = sanitizeModuleFont(BetterUCConfig.INSTANCE.fpsHudCustomFont, legacy);
        BetterUCConfig.INSTANCE.paydayHudCustomFont = sanitizeModuleFont(BetterUCConfig.INSTANCE.paydayHudCustomFont, legacy);
        BetterUCConfig.INSTANCE.ammoHudCustomFont = sanitizeModuleFont(BetterUCConfig.INSTANCE.ammoHudCustomFont, legacy);
        BetterUCConfig.INSTANCE.bankHudCustomFont = sanitizeModuleFont(BetterUCConfig.INSTANCE.bankHudCustomFont, legacy);
        BetterUCConfig.INSTANCE.potionHudCustomFont = sanitizeModuleFont(BetterUCConfig.INSTANCE.potionHudCustomFont, legacy);
        BetterUCConfig.INSTANCE.hackTimerHudCustomFont = sanitizeModuleFont(BetterUCConfig.INSTANCE.hackTimerHudCustomFont, legacy);
        BetterUCConfig.INSTANCE.plantTimerHudCustomFont = sanitizeModuleFont(BetterUCConfig.INSTANCE.plantTimerHudCustomFont, legacy);
    }

    private static String sanitizeModuleFont(String value, String fallback) {
        String selected = normalizedFontId(value);
        if (selected.isEmpty()) {
            selected = normalizedFontId(fallback);
        }
        return sanitizeFontChoice(selected, DEFAULT_FONT_ID);
    }

    private static String sanitizeFontChoice(String value, String fallback) {
        String selected = normalizedFontId(value);
        if (selected.isEmpty()) {
            selected = normalizedFontId(fallback);
        }
        if (selected.isEmpty()) {
            return DEFAULT_FONT_ID;
        }

        for (FontOption option : fontOptions) {
            if (option.id.equals(selected)) {
                return selected;
            }
        }
        return DEFAULT_FONT_ID;
    }

    private static String normalizedFontId(String value) {
        return value == null ? DEFAULT_FONT_ID : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isFontFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".ttf") || name.endsWith(".otf");
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "ttf" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sanitizePath(String raw) {
        String folded = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return folded.isEmpty() ? "font" : folded;
    }

    private static String uniqueIdPath(String base, Set<String> usedIds) {
        String candidate = base;
        int suffix = 2;
        while (!usedIds.add(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static String packMcmeta() {
        int packVersion = SharedConstants.getGameVersion()
                .packVersion(ResourceType.CLIENT_RESOURCES)
                .major();
        int lastOldVersion = PackVersion.getLastOldPackVersion(ResourceType.CLIENT_RESOURCES);
        String supported = packVersion <= lastOldVersion
                ? ",\n    \"supported_formats\": [" + packVersion + ", " + packVersion + "]"
                : "";

        return """
                {
                  "pack": {
                    "description": "betterUC custom HUD fonts",
                    "pack_format": %d,
                    "min_format": %d,
                    "max_format": %d%s
                  }
                }
                """.formatted(packVersion, packVersion, packVersion, supported);
    }

    private static String fontJson(FontSource source) {
        return """
                {
                  "providers": [
                    {
                      "type": "ttf",
                      "file": "%s:custom/%s",
                      "size": 11.0,
                      "oversample": 2.0,
                      "shift": [0.0, 0.0]
                    },
                    {
                      "type": "reference",
                      "id": "minecraft:default"
                    }
                  ]
                }
                """.formatted(NAMESPACE, source.resourceFileName);
    }

    private static void ensureFontsDir() {
        try {
            Files.createDirectories(FONTS_DIR);
        } catch (IOException e) {
            BetterUCMod.LOGGER.error("Could not create betterUC font folder {}", FONTS_DIR, e);
        }
    }

    private static void deleteGeneratedPack() throws IOException {
        if (!Files.exists(PACK_DIR)) return;
        Files.walkFileTree(PACK_DIR, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public record FontOption(String id, String label, Identifier identifier) {
        private static final FontOption DEFAULT = new FontOption(DEFAULT_FONT_ID, "Minecraft", Identifier.ofVanilla("default"));
    }

    private record FontSource(Path sourcePath, String label, String idPath, String resourceFileName) {
    }
}
