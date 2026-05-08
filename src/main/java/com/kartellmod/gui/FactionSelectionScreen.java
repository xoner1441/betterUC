package com.kartellmod.gui;

import com.kartellmod.KartellSuppressFlags;
import com.kartellmod.ServerGate;
import com.kartellmod.config.KartellConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public class FactionSelectionScreen extends Screen {

    private static final int BUTTON_W = 220;
    private static final int BUTTON_H = 20;
    private static final int START_Y = 56;
    private static final int ROW_STEP = 24;

    private final Screen parent;
    private boolean dropdownOpen = false;
    private String selectedFactionQuery = "kartell";

    public FactionSelectionScreen(Screen parent) {
        super(Text.literal("Fraktionsauswahl"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ensureSelectedFaction();

        int centerX = width / 2;
        int y = START_Y;

        addDrawableChild(ButtonWidget.builder(Text.literal(dropdownLabel()), b -> {
            dropdownOpen = !dropdownOpen;
            refreshWidgets();
        }).dimensions(centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H).build());

        y += ROW_STEP;
        addDrawableChild(ButtonWidget.builder(Text.literal(toggleLabel()), b -> {
            KartellConfig.toggleTrackedFaction(selectedFactionQuery);
            KartellConfig.rebuildRemoteFactionUnion();
            if (KartellConfig.isFactionTracked(selectedFactionQuery)) {
                requestSilentMemberRefresh(selectedFactionQuery);
            }
            refreshWidgets();
        }).dimensions(centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H).build());

        if (dropdownOpen) {
            y += ROW_STEP;
            for (KartellConfig.TrackableFaction faction : KartellConfig.getTrackableFactions()) {
                final String query = faction.query;
                addDrawableChild(ButtonWidget.builder(Text.literal(optionLabel(faction)), b -> {
                    selectedFactionQuery = KartellConfig.normalizeFactionQuery(query);
                    KartellConfig.setOnlyTrackedFaction(selectedFactionQuery);
                    KartellConfig.rebuildRemoteFactionUnion();
                    requestSilentMemberRefresh(selectedFactionQuery);
                    dropdownOpen = true;
                    refreshWidgets();
                }).dimensions(centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H).build());
                y += ROW_STEP;
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("\u2714 Speichern & Zurueck"), b -> {
            KartellConfig.save();
            if (client != null) {
                client.setScreen(parent);
            }
        }).dimensions(centerX - BUTTON_W / 2, height - 28, BUTTON_W, BUTTON_H).build());
    }

    private String dropdownLabel() {
        String label = KartellConfig.factionLabelForQuery(selectedFactionQuery);
        return "\u00A7bFraktion: \u00A7f" + label + (dropdownOpen ? " \u25B2" : " \u25BC");
    }

    private String toggleLabel() {
        boolean tracked = KartellConfig.isFactionTracked(selectedFactionQuery);
        return tracked ? "\u00A7aTracking: AN" : "\u00A77Tracking: AUS";
    }

    private String optionLabel(KartellConfig.TrackableFaction faction) {
        boolean tracked = KartellConfig.isFactionTracked(faction.query);
        return (tracked ? "\u00A7a[AN] " : "\u00A77[AUS] ") + faction.label;
    }

    private void ensureSelectedFaction() {
        List<String> tracked = KartellConfig.getTrackedFactionQueries();
        if (!tracked.isEmpty()) {
            selectedFactionQuery = tracked.get(0);
            return;
        }
        String normalizedCurrent = KartellConfig.normalizeFactionQuery(selectedFactionQuery);
        if (!normalizedCurrent.isEmpty()) {
            selectedFactionQuery = normalizedCurrent;
            return;
        }
        selectedFactionQuery = KartellConfig.normalizeFactionQuery("kartell");
    }

    private void requestSilentMemberRefresh(String factionQuery) {
        if (client == null || client.player == null || factionQuery == null || factionQuery.isBlank()) return;
        if (!ServerGate.isAllowedServer(client)) return;
        KartellSuppressFlags.markSilentMemberRequest();
        client.player.networkHandler.sendChatCommand("memberinfoall " + factionQuery);
    }

    private void refreshWidgets() {
        clearChildren();
        init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0000000);
        super.render(context, mouseX, mouseY, delta);

        int centerX = width / 2;
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("\u00A7lFraktionen fuer Nametag-Farbe"),
                centerX,
                14,
                0xFFFFFF
        );

        List<String> tracked = KartellConfig.getTrackedFactionQueries();
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("\u00A77Aktiv: \u00A7f" + tracked.stream().map(KartellConfig::factionLabelForQuery).reduce((a, b) -> a + ", " + b).orElse("keine")),
                centerX,
                34,
                0xBBBBBB
        );
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
