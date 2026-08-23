package com.betteruc.gui;

import com.betteruc.client.ClientCompat;
import com.betteruc.client.VersionChecker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class UpdateRestartScreen extends Screen {
    private static final int ACCENT = 0xFF38BDF8;
    private static final int SUCCESS = 0xFF4ADE80;
    private static final int PANEL = 0xFA111820;
    private static final int INNER = 0xFA18212B;
    private static final int BORDER = 0xFF475569;
    private static final int TEXT = 0xFFF8FAFC;
    private static final int MUTED = 0xFF94A3B8;

    private final Screen parent;
    private final String version;
    private final boolean automaticRestart;

    public UpdateRestartScreen(Screen parent, String version, boolean automaticRestart) {
        super(Component.literal("betterUC Update"));
        this.parent = parent;
        this.version = version == null || version.isBlank() ? "neu" : version;
        this.automaticRestart = automaticRestart;
    }

    @Override
    protected void init() {
        int panelX = panelX();
        int panelY = panelY();
        int panelW = panelW();
        int buttonY = panelY + panelH() - 36;
        int confirmW = 176;
        int cancelW = 76;
        int gap = 8;
        int rowW = confirmW + gap + cancelW;
        int rowX = panelX + (panelW - rowW) / 2;

        String confirmLabel = automaticRestart
                ? "Installieren & neu starten"
                : "Installieren & schließen";
        addRenderableWidget(Button.builder(Component.literal(confirmLabel), button -> confirm())
                .bounds(rowX, buttonY, confirmW, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Später"), button -> closeToParent())
                .bounds(rowX + confirmW + gap, buttonY, cancelW, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (parent != null) {
            parent.extractRenderState(context, mouseX, mouseY, delta);
        }
        context.fill(0, 0, width, height, 0xB8000000);

        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();
        context.fill(x, y, x + w, y + h, PANEL);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, INNER);
        drawBorder(context, x, y, w, h, BORDER);
        context.fill(x + 2, y + 2, x + w - 2, y + 4, ACCENT);

        context.text(font, Component.literal("BETTERUC UPDATE"), x + 18, y + 17, ACCENT);
        context.text(font, Component.literal("Version " + version + " ist bereit"), x + 18, y + 36, TEXT);
        context.text(font, Component.literal("Die neue Mod-Datei wurde geprüft und heruntergeladen."),
                x + 18, y + 57, MUTED);

        context.text(font, Component.literal("Minecraft wird geschlossen und betterUC sicher aktualisiert."),
                x + 18, y + 72, MUTED);
        String restartText = automaticRestart
                ? "Danach startet dieselbe Minecraft-Installation automatisch neu."
                : "Starte deine Minecraft-Installation danach bitte erneut.";
        context.text(font, Component.literal(restartText), x + 18, y + 86, MUTED);
        context.text(font, Component.literal("Nicht gespeicherte Änderungen können verloren gehen."),
                x + 18, y + 105, 0xFFFACC15);
        context.text(font, Component.literal(automaticRestart ? "Automatischer Neustart verfügbar" : "Manueller Neustart erforderlich"),
                x + 18, y + 122, automaticRestart ? SUCCESS : 0xFFFACC15);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void confirm() {
        if (minecraft != null) {
            VersionChecker.confirmInstallAndRestart(minecraft);
        }
    }

    private void closeToParent() {
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, parent);
        }
    }

    private int panelW() {
        return Math.min(460, Math.max(330, width - 36));
    }

    private int panelH() {
        return 178;
    }

    private int panelX() {
        return width / 2 - panelW() / 2;
    }

    private int panelY() {
        return height / 2 - panelH() / 2;
    }

    private static void drawBorder(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
