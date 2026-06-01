package com.betteruc.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ChangelogScreen extends Screen {

    private static final int BUTTON_W = 170;
    private static final int BUTTON_H = 20;

    private static final Page[] PAGES = new Page[]{
            new Page(
                    "Highlights",
                    "betterUC 1.1.0",
                    new String[]{
                            "Potion-HUD im Inventar-Stil mit Icon, Name und Dauer",
                            "CookDrug-Timer entfernt, weil der Server nun einen eigenen Timer hat",
                            "Plant-Timer fuer eigene Plantagen mit Wasser- und Duengerzeiten",
                            "Payday-, Bank-, Ammo-, FPS-, Herz- und ToggleSprint-HUDs",
                            "HUD-Positionen koennen im Settings-Menue angepasst werden",
                            "Zoom und Auto-Stats Join direkt im Settings-HUD schaltbar",
                            "Build kopiert die Mod-JAR automatisch in den Minecraft-Mods-Ordner"
                    }
            ),
            new Page(
                    "HUDs & Komfort",
                    "Spieler-Features",
                    new String[]{
                            "FPS-HUD fuer schnelle Performance-Uebersicht",
                            "Payday-HUD mit AFK-Erkennung",
                            "Bank-HUD erkennt neue Kontostaende aus dem Chat",
                            "Ammo-HUD fuer Waffenanzeige",
                            "Potion-HUD zeigt aktive Effekte wie im Inventar",
                            "Health-HUD fuer kompakte Lebensanzeige",
                            "ToggleSprint-HUD und integrierter ToggleSprint",
                            "Frei verschiebbare HUD-Elemente per X/Y-Slider",
                            "Farbwahl fuer mehrere HUD-Anzeigen"
                    }
            ),
            new Page(
                    "Tools & Commands",
                    "Alltag im Spiel",
                    new String[]{
                            "/seinzahlen zahlt Schwarzgeld automatisch in die S-Kasse ein",
                            "Hotkey Commands: eigene Befehle auf Tasten legen",
                            "Zoom-Taste frei waehlbar, Zoom reagiert sofort",
                            "/car find Koordinaten werden automatisch ins Navi uebernommen",
                            "Chat-Zeitstempel und groessere Chat-Historie"
                    }
            ),
            new Page(
                    "Fraktions-Tools",
                    "Optional fuer Orga-Spieler",
                    new String[]{
                            "Blacklist-Gruende koennen im Menue gepflegt werden",
                            "/blset und /setbl setzen Blacklist-Eintraege mit Vorlagen",
                            "/modbl erweitert bestehende Blacklist-Eintraege",
                            "/blinfo zeigt gespeicherte Infos zu einem Blacklist-Spieler",
                            "/setrp setzt RP-Stufen fuer Blacklist-Eintraege",
                            "/scall sendet einen schnellen Callout an einen Spieler",
                            "Normale /blacklist-Ausgabe bleibt serverseitig sichtbar"
                    }
            )
    };

    private final Screen parent;
    private int pageIndex = 0;

    public ChangelogScreen(Screen parent) {
        super(Text.literal("betterUC Features"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int navY = height - 54;

        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            if (pageIndex > 0) {
                pageIndex--;
                refreshWidgets();
            }
        }).dimensions(centerX - BUTTON_W / 2 - 28, navY, 24, BUTTON_H).build()).active = pageIndex > 0;

        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            if (pageIndex < PAGES.length - 1) {
                pageIndex++;
                refreshWidgets();
            }
        }).dimensions(centerX + BUTTON_W / 2 + 4, navY, 24, BUTTON_H).build()).active = pageIndex < PAGES.length - 1;

        addDrawableChild(ButtonWidget.builder(Text.literal("Zurueck"), b -> {
            if (client != null) {
                client.setScreen(parent);
            }
        }).dimensions(centerX - BUTTON_W / 2, height - 30, BUTTON_W, BUTTON_H).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xD0000000);
        super.render(context, mouseX, mouseY, delta);

        int panelWidth = Math.min(560, width - 40);
        int panelHeight = Math.min(270, height - 92);
        int panelX = width / 2 - panelWidth / 2;
        int panelY = 28;

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0101010);
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, 0xFFAAAAAA);

        Page page = PAGES[pageIndex];
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("betterUC Changelog & Features"), width / 2, panelY + 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(page.title), width / 2, panelY + 28, 0xFFFFD866);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(page.subtitle), width / 2, panelY + 42, 0xFFBBBBBB);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("Seite " + (pageIndex + 1) + "/" + PAGES.length),
                width / 2,
                height - 49,
                0xFFAAAAAA
        );

        int x = panelX + 22;
        int y = panelY + 66;
        int maxLineWidth = panelWidth - 44;
        int bottomY = panelY + panelHeight - 14;
        for (String line : page.lines) {
            if (y > bottomY) break;
            y = drawWrappedBullet(context, line, x, y, maxLineWidth);
        }
    }

    private int drawWrappedBullet(DrawContext context, String line, int x, int y, int maxWidth) {
        String bullet = "- ";
        String remaining = line;
        boolean firstLine = true;
        int currentY = y;
        while (!remaining.isEmpty()) {
            String prefix = firstLine ? bullet : "  ";
            int availableWidth = maxWidth - textRenderer.getWidth(prefix);
            String part = takeFittingText(remaining, availableWidth);
            context.drawTextWithShadow(textRenderer, Text.literal(prefix + part), x, currentY, 0xFFE6E6E6);
            remaining = remaining.substring(part.length()).trim();
            currentY += 12;
            firstLine = false;
        }
        return currentY + 2;
    }

    private String takeFittingText(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) return text;

        int lastSpace = -1;
        for (int i = 1; i <= text.length(); i++) {
            char c = text.charAt(i - 1);
            if (Character.isWhitespace(c)) {
                lastSpace = i - 1;
            }
            String candidate = text.substring(0, i).trim();
            if (textRenderer.getWidth(candidate) > maxWidth) {
                if (lastSpace > 0) {
                    return text.substring(0, lastSpace).trim();
                }
                return text.substring(0, Math.max(1, i - 1)).trim();
            }
        }
        return text;
    }

    private void refreshWidgets() {
        clearChildren();
        init();
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private record Page(String title, String subtitle, String[] lines) {
    }
}
