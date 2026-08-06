package com.betteruc.gui;

import com.betteruc.client.ClientCompat;
import com.betteruc.client.SecondChatManager;
import com.betteruc.config.SecondChatFilterConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SecondChatPresetScreen extends Screen {

    private final Screen parent;
    private final String tabId;
    private int panelX;
    private int panelWidth;
    private int leftColumnX;
    private int rightColumnX;
    private int columnWidth;
    private boolean twoColumns;

    public SecondChatPresetScreen(Screen parent, String tabId) {
        super(Component.literal("Filtervorlagen"));
        this.parent = parent;
        this.tabId = tabId;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(680, width - 32);
        panelX = (width - panelWidth) / 2;
        twoColumns = panelWidth >= 560;
        int gap = twoColumns ? 18 : 0;
        columnWidth = twoColumns ? (panelWidth - gap - 20) / 2 : panelWidth - 20;
        leftColumnX = panelX + 10;
        rightColumnX = twoColumns ? leftColumnX + columnWidth + gap : leftColumnX;

        int leftY = 98;
        leftY = addPreset(leftColumnX, leftY, columnWidth, "WPS / HQ", "hq");
        addPreset(leftColumnX, leftY, columnWidth, "Reinforcements", "reinf");

        int rightY = twoColumns ? 98 : leftY + 27;
        rightY = addPreset(rightColumnX, rightY, columnWidth, "PayDay", "payday");
        addPreset(rightColumnX, rightY, columnWidth, "Werbung", "advertising");
        addRenderableWidget(Button.builder(Component.literal("Zurück"), button -> onClose())
                .bounds(16, height - 29, 90, 20)
                .build());
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor context, int mouseX, int mouseY, float delta
    ) {
        SecondChatSettingsUi.renderPage(
                context,
                font,
                width,
                height,
                "Filtervorlagen",
                "Eine passende Grundlage wählen und anschließend verfeinern");
        SecondChatSettingsUi.renderPanel(
                context,
                panelX,
                64,
                panelWidth,
                Math.max(1, height - 108));
        SecondChatSettingsUi.renderSection(
                context,
                font,
                new SecondChatSettingsUi.Section(
                        "Kommunikation", leftColumnX, 74, columnWidth, 0xFF38BDF8),
                0,
                52,
                height - 39);
        SecondChatSettingsUi.renderSection(
                context,
                font,
                new SecondChatSettingsUi.Section(
                        "Alltag & System",
                        rightColumnX,
                        twoColumns ? 74 : 74 + 2 * 27 + 33,
                        columnWidth,
                        0xFF4ADE80),
                0,
                52,
                height - 39);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int addPreset(int x, int y, int width, String label, String preset) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
                    SecondChatFilterConfig filter = SecondChatManager.addPreset(tabId, preset);
                    if (filter != null && minecraft != null) {
                        ClientCompat.setScreen(minecraft,
                                new SecondChatFilterEditScreen(parent, tabId, filter.id, false));
                    }
                })
                .bounds(x, y, width, 20)
                .build());
        return y + 27;
    }
}
