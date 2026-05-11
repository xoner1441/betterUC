package com.betteruc.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ChangelogScreen extends Screen {

    private static final String[] LINES = new String[]{
            "Fraktionsmember werden direkt nach /memberinfoall live gefaerbt",
            "Intervall-Sync nutzt die ausgewaehlte Settings-Fraktion",
            "/blacklist und /bl refreshen die Nametag-Faerbung direkt",
            "Bank-HUD erkennt Neuer Bankkontostand",
            "Parser/Chat-Erkennung aufgeraeumt",
            "Build kopiert die JAR direkt in den Mods-Ordner",
            "Alte Mod-JARs werden beim Build entfernt"
    };

    private final Screen parent;

    public ChangelogScreen(Screen parent) {
        super(Text.literal("betterUC Update"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("\u2714 Zurueck"), b -> {
            if (client != null) {
                client.setScreen(parent);
            }
        }).dimensions(width / 2 - 85, height - 30, 170, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xD0000000);
        super.render(context, mouseX, mouseY, delta);

        int panelWidth = Math.min(420, width - 40);
        int panelHeight = 150;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = Math.max(24, height / 2 - 150);

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0101010);
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, 0xFFAAAAAA);

        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Update-Changelog"), width / 2, panelY + 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("betterUC 1.0.0"), width / 2, panelY + 28, 0xFFFFD866);

        int x = panelX + 22;
        int y = panelY + 52;
        for (String line : LINES) {
            context.drawTextWithShadow(textRenderer, Text.literal("- " + line), x, y, 0xFFE6E6E6);
            y += 14;
        }
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
}
