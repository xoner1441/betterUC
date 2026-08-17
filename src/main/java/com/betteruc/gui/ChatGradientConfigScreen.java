package com.betteruc.gui;

import com.betteruc.client.ChatCustomizationFormatter;
import com.betteruc.client.ClientCompat;
import com.betteruc.config.BetterUCConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class ChatGradientConfigScreen extends Screen {

    private static final int ROWS_TOP = 169;
    private static final int ROW_HEIGHT = 24;
    private static final int TEXT_PRIMARY = 0xFFF8FAFC;
    private static final int TEXT_MUTED = 0xFF94A3B8;

    private final Screen parent;
    private final List<ColorRow> rows = new ArrayList<>();
    private final List<RowWidget> rowWidgets = new ArrayList<>();
    private Profile profile = Profile.HQ;
    private int panelX;
    private int panelWidth;
    private int scrollOffset;

    public ChatGradientConfigScreen(Screen parent) {
        super(Component.literal("Chat-Farbverläufe"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        rowWidgets.clear();
        panelWidth = Math.min(540, width - 32);
        panelX = (width - panelWidth) / 2;

        int tabWidth = Math.min(150, (panelWidth - 8) / 2);
        int tabX = width / 2 - tabWidth - 2;
        addRenderableWidget(Button.builder(tabLabel("HQ", profile == Profile.HQ), button -> switchProfile(Profile.HQ))
                .bounds(tabX, 58, tabWidth, 20)
                .build());
        addRenderableWidget(Button.builder(tabLabel("PAY", profile == Profile.PAY), button -> switchProfile(Profile.PAY))
                .bounds(width / 2 + 2, 58, tabWidth, 20)
                .build());

        if (profile == Profile.HQ) {
            addHqRows();
        } else {
            addPayRows();
        }

        int labelWidth = Math.min(160, Math.max(100, panelWidth / 3));
        int gap = 5;
        int buttonWidth = Math.max(70, (panelWidth - labelWidth - gap * 2) / 2);
        for (int index = 0; index < rows.size(); index++) {
            ColorRow row = rows.get(index);
            int logicalY = ROWS_TOP + index * ROW_HEIGHT;
            int startX = panelX + labelWidth + gap;
            Button start = Button.builder(colorLabel("Start", row.start().getAsInt()),
                            button -> openColorPicker(row.label() + " – Start", row.start(), row.startSetter()))
                    .bounds(startX, logicalY, buttonWidth, 20)
                    .build();
            Button end = Button.builder(colorLabel("Ende", row.end().getAsInt()),
                            button -> openColorPicker(row.label() + " – Ende", row.end(), row.endSetter()))
                    .bounds(startX + buttonWidth + gap, logicalY, buttonWidth, 20)
                    .build();
            addRenderableWidget(start);
            addRenderableWidget(end);
            rowWidgets.add(new RowWidget(start, logicalY));
            rowWidgets.add(new RowWidget(end, logicalY));
        }

        addRenderableWidget(Button.builder(Component.literal("Profil zurücksetzen"), button -> resetCurrentProfile())
                .bounds(16, height - 29, 150, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Speichern & Zurück"), button -> onClose())
                .bounds(width - 166, height - 29, 150, 20)
                .build());
        updateRows();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        SecondChatSettingsUi.renderPage(
                context,
                font,
                width,
                height,
                "Chat-Farbverläufe",
                "HQ- und PAY-Nachrichten unabhängig gestalten"
        );
        SecondChatSettingsUi.renderPanel(context, panelX - 10, 85, panelWidth + 20, 83);
        context.text(font, Component.literal("Live-Vorschau"), panelX, 91, TEXT_MUTED);
        List<Component> preview = profile == Profile.HQ
                ? ChatCustomizationFormatter.hqGradientPreview()
                : ChatCustomizationFormatter.payGradientPreview();
        int previewY = 104;
        for (int index = 0; index < preview.size(); index++) {
            context.text(font, preview.get(index), panelX, previewY + index * 9, TEXT_PRIMARY);
        }

        int viewportBottom = height - 39;
        SecondChatSettingsUi.renderPanel(
                context,
                panelX - 10,
                ROWS_TOP - 18,
                panelWidth + 20,
                Math.max(1, viewportBottom - ROWS_TOP + 18)
        );
        context.text(font, Component.literal("Bereich"), panelX, ROWS_TOP - 13, TEXT_MUTED);
        for (int index = 0; index < rows.size(); index++) {
            int y = ROWS_TOP + index * ROW_HEIGHT - scrollOffset;
            if (y >= ROWS_TOP - 1 && y + 10 < viewportBottom) {
                context.text(font, Component.literal(rows.get(index).label()), panelX, y + 6, TEXT_PRIMARY);
            }
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
        SecondChatSettingsUi.renderScrollbar(
                context,
                width - 7,
                ROWS_TOP,
                viewportBottom,
                rows.size() * ROW_HEIGHT,
                scrollOffset,
                maxScroll()
        );
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= ROWS_TOP && mouseY < height - 39 && maxScroll() > 0) {
            scrollOffset = clamp(scrollOffset - (int) Math.round(verticalAmount * ROW_HEIGHT), 0, maxScroll());
            updateRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void removed() {
        BetterUCConfig.save();
        super.removed();
    }

    @Override
    public void onClose() {
        BetterUCConfig.save();
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addHqRows() {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        rows.add(row("Aktion", () -> config.chatHqActionGradientStart, value -> config.chatHqActionGradientStart = value,
                () -> config.chatHqActionGradientEnd, value -> config.chatHqActionGradientEnd = value));
        rows.add(row("Officer", () -> config.chatHqActorGradientStart, value -> config.chatHqActorGradientStart = value,
                () -> config.chatHqActorGradientEnd, value -> config.chatHqActorGradientEnd = value));
        rows.add(row("Zielspieler", () -> config.chatHqTargetGradientStart, value -> config.chatHqTargetGradientStart = value,
                () -> config.chatHqTargetGradientEnd, value -> config.chatHqTargetGradientEnd = value));
        rows.add(row("Details", () -> config.chatHqDetailGradientStart, value -> config.chatHqDetailGradientStart = value,
                () -> config.chatHqDetailGradientEnd, value -> config.chatHqDetailGradientEnd = value));
        rows.add(row("Positive Aktion", () -> config.chatHqPositiveGradientStart, value -> config.chatHqPositiveGradientStart = value,
                () -> config.chatHqPositiveGradientEnd, value -> config.chatHqPositiveGradientEnd = value));
        rows.add(row("Ticket-Aktion", () -> config.chatHqTicketActionGradientStart, value -> config.chatHqTicketActionGradientStart = value,
                () -> config.chatHqTicketActionGradientEnd, value -> config.chatHqTicketActionGradientEnd = value));
        rows.add(row("Ticket-Details", () -> config.chatHqTicketDetailGradientStart, value -> config.chatHqTicketDetailGradientStart = value,
                () -> config.chatHqTicketDetailGradientEnd, value -> config.chatHqTicketDetailGradientEnd = value));
        rows.add(row("Plantage-Aktion", () -> config.chatHqPlantageActionGradientStart, value -> config.chatHqPlantageActionGradientStart = value,
                () -> config.chatHqPlantageActionGradientEnd, value -> config.chatHqPlantageActionGradientEnd = value));
        rows.add(row("Plantage-Details", () -> config.chatHqPlantageDetailGradientStart, value -> config.chatHqPlantageDetailGradientStart = value,
                () -> config.chatHqPlantageDetailGradientEnd, value -> config.chatHqPlantageDetailGradientEnd = value));
    }

    private void addPayRows() {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        rows.add(row("Aktion", () -> config.chatPayActionGradientStart, value -> config.chatPayActionGradientStart = value,
                () -> config.chatPayActionGradientEnd, value -> config.chatPayActionGradientEnd = value));
        rows.add(row("Absender", () -> config.chatPayActorGradientStart, value -> config.chatPayActorGradientStart = value,
                () -> config.chatPayActorGradientEnd, value -> config.chatPayActorGradientEnd = value));
        rows.add(row("Empfänger", () -> config.chatPayTargetGradientStart, value -> config.chatPayTargetGradientStart = value,
                () -> config.chatPayTargetGradientEnd, value -> config.chatPayTargetGradientEnd = value));
        rows.add(row("Abbuchung", () -> config.chatPayOutgoingGradientStart, value -> config.chatPayOutgoingGradientStart = value,
                () -> config.chatPayOutgoingGradientEnd, value -> config.chatPayOutgoingGradientEnd = value));
        rows.add(row("Eingang", () -> config.chatPayIncomingGradientStart, value -> config.chatPayIncomingGradientStart = value,
                () -> config.chatPayIncomingGradientEnd, value -> config.chatPayIncomingGradientEnd = value));
    }

    private void switchProfile(Profile next) {
        if (profile == next) {
            return;
        }
        profile = next;
        scrollOffset = 0;
        clearWidgets();
        init();
    }

    private void openColorPicker(String label, IntSupplier getter, IntConsumer setter) {
        BetterUCConfig.save();
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, new ColorPickerScreen(
                    this,
                    label,
                    label + " wählen",
                    getter.getAsInt(),
                    setter::accept
            ));
        }
    }

    private void resetCurrentProfile() {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        if (profile == Profile.HQ) {
            config.chatHqActionGradientStart = BetterUCConfig.DEFAULT_CHAT_HQ_ACTION_GRADIENT_START;
            config.chatHqActionGradientEnd = BetterUCConfig.DEFAULT_CHAT_HQ_ACTION_GRADIENT_END;
            config.chatHqActorGradientStart = BetterUCConfig.DEFAULT_CHAT_HQ_ACTOR_GRADIENT_START;
            config.chatHqActorGradientEnd = BetterUCConfig.DEFAULT_CHAT_HQ_ACTOR_GRADIENT_END;
            config.chatHqTargetGradientStart = BetterUCConfig.DEFAULT_CHAT_HQ_TARGET_GRADIENT_START;
            config.chatHqTargetGradientEnd = BetterUCConfig.DEFAULT_CHAT_HQ_TARGET_GRADIENT_END;
            config.chatHqDetailGradientStart = BetterUCConfig.DEFAULT_CHAT_HQ_DETAIL_GRADIENT_START;
            config.chatHqDetailGradientEnd = BetterUCConfig.DEFAULT_CHAT_HQ_DETAIL_GRADIENT_END;
            config.chatHqPositiveGradientStart = BetterUCConfig.DEFAULT_CHAT_HQ_POSITIVE_GRADIENT_START;
            config.chatHqPositiveGradientEnd = BetterUCConfig.DEFAULT_CHAT_HQ_POSITIVE_GRADIENT_END;
            config.chatHqTicketActionGradientStart = BetterUCConfig.DEFAULT_CHAT_HQ_TICKET_ACTION_GRADIENT_START;
            config.chatHqTicketActionGradientEnd = BetterUCConfig.DEFAULT_CHAT_HQ_TICKET_ACTION_GRADIENT_END;
            config.chatHqTicketDetailGradientStart = BetterUCConfig.DEFAULT_CHAT_HQ_TICKET_DETAIL_GRADIENT_START;
            config.chatHqTicketDetailGradientEnd = BetterUCConfig.DEFAULT_CHAT_HQ_TICKET_DETAIL_GRADIENT_END;
            config.chatHqPlantageActionGradientStart = BetterUCConfig.DEFAULT_CHAT_HQ_PLANTAGE_ACTION_GRADIENT_START;
            config.chatHqPlantageActionGradientEnd = BetterUCConfig.DEFAULT_CHAT_HQ_PLANTAGE_ACTION_GRADIENT_END;
            config.chatHqPlantageDetailGradientStart = BetterUCConfig.DEFAULT_CHAT_HQ_PLANTAGE_DETAIL_GRADIENT_START;
            config.chatHqPlantageDetailGradientEnd = BetterUCConfig.DEFAULT_CHAT_HQ_PLANTAGE_DETAIL_GRADIENT_END;
        } else {
            config.chatPayActionGradientStart = BetterUCConfig.DEFAULT_CHAT_PAY_ACTION_GRADIENT_START;
            config.chatPayActionGradientEnd = BetterUCConfig.DEFAULT_CHAT_PAY_ACTION_GRADIENT_END;
            config.chatPayActorGradientStart = BetterUCConfig.DEFAULT_CHAT_PAY_ACTOR_GRADIENT_START;
            config.chatPayActorGradientEnd = BetterUCConfig.DEFAULT_CHAT_PAY_ACTOR_GRADIENT_END;
            config.chatPayTargetGradientStart = BetterUCConfig.DEFAULT_CHAT_PAY_TARGET_GRADIENT_START;
            config.chatPayTargetGradientEnd = BetterUCConfig.DEFAULT_CHAT_PAY_TARGET_GRADIENT_END;
            config.chatPayOutgoingGradientStart = BetterUCConfig.DEFAULT_CHAT_PAY_OUTGOING_GRADIENT_START;
            config.chatPayOutgoingGradientEnd = BetterUCConfig.DEFAULT_CHAT_PAY_OUTGOING_GRADIENT_END;
            config.chatPayIncomingGradientStart = BetterUCConfig.DEFAULT_CHAT_PAY_INCOMING_GRADIENT_START;
            config.chatPayIncomingGradientEnd = BetterUCConfig.DEFAULT_CHAT_PAY_INCOMING_GRADIENT_END;
        }
        BetterUCConfig.save();
        clearWidgets();
        init();
    }

    private void updateRows() {
        int bottom = height - 39;
        for (RowWidget row : rowWidgets) {
            int y = row.logicalY() - scrollOffset;
            row.widget().setY(y);
            row.widget().visible = y + row.widget().getHeight() > ROWS_TOP - 1 && y < bottom;
            row.widget().active = row.widget().visible;
        }
    }

    private int maxScroll() {
        int viewportHeight = Math.max(1, height - 39 - ROWS_TOP);
        return Math.max(0, rows.size() * ROW_HEIGHT - viewportHeight);
    }

    private static ColorRow row(
            String label,
            IntSupplier start,
            IntConsumer startSetter,
            IntSupplier end,
            IntConsumer endSetter
    ) {
        return new ColorRow(label, start, startSetter, end, endSetter);
    }

    private static Component tabLabel(String label, boolean selected) {
        return Component.literal((selected ? "● " : "") + label);
    }

    private static Component colorLabel(String label, int color) {
        MutableComponent component = Component.literal(label + " " + String.format("#%06X", color & 0xFFFFFF) + "  ");
        component.append(Component.literal("■").setStyle(Style.EMPTY.withColor(color & 0xFFFFFF)));
        return component;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Profile {
        HQ,
        PAY
    }

    private record ColorRow(
            String label,
            IntSupplier start,
            IntConsumer startSetter,
            IntSupplier end,
            IntConsumer endSetter
    ) {
    }

    private record RowWidget(AbstractWidget widget, int logicalY) {
    }
}
