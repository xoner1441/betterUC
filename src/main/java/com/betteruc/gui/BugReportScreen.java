package com.betteruc.gui;

import com.betteruc.client.BugReportClient;
import com.betteruc.client.ClientCompat;
import com.betteruc.client.ScreenshotActionsClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;

public final class BugReportScreen extends Screen {
    private static final int PANEL_BG = 0xF0141A22;
    private static final int PANEL_BORDER = 0xFF475569;
    private static final int AMBER = 0xFFF59E0B;
    private static final int TEXT = 0xFFF8FAFC;
    private static final int MUTED = 0xFF94A3B8;

    private final Screen parent;
    private String titleDraft = "";
    private String descriptionDraft = "";
    private String stepsDraft = "";
    private Path latestScreenshot;
    private boolean screenshotEnabled;
    private boolean screenshotInitialized;
    private boolean attachLog;
    private boolean sending;
    private String status = "";
    private int statusColor = MUTED;
    private String resultUrl = "";

    private EditBox titleField;
    private MultiLineEditBox descriptionField;
    private MultiLineEditBox stepsField;
    private Button screenshotButton;
    private Button logButton;
    private Button sendButton;
    private Button openButton;

    public BugReportScreen(Screen parent) {
        super(Component.literal("Bug melden"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelW = Math.min(600, width - 30);
        int panelH = Math.min(430, height - 24);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        int x = panelX + 18;
        int fieldW = panelW - 36;
        int y = panelY + 60;

        if (!screenshotInitialized) {
            latestScreenshot = ScreenshotActionsClient.latestScreenshot();
            screenshotEnabled = latestScreenshot != null;
            screenshotInitialized = true;
        }

        titleField = new EditBox(font, x, y, fieldW, 20, Component.literal("Kurzer, eindeutiger Titel"));
        titleField.setMaxLength(100);
        titleField.setValue(titleDraft);
        titleField.setResponder(value -> {
            titleDraft = value;
            updateSendButton();
        });
        addRenderableWidget(titleField);
        y += 42;

        int descriptionH = Math.max(60, Math.min(90, panelH - 330));
        descriptionField = MultiLineEditBox.builder()
                .setX(x).setY(y)
                .setPlaceholder(Component.literal("Was ist passiert? Was hast du erwartet?"))
                .build(font, fieldW, descriptionH, Component.literal("Beschreibung"));
        descriptionField.setCharacterLimit(3000);
        descriptionField.setValue(descriptionDraft);
        descriptionField.setValueListener(value -> {
            descriptionDraft = value;
            updateSendButton();
        });
        addRenderableWidget(descriptionField);
        y += descriptionH + 22;

        int stepsH = Math.max(48, Math.min(72, panelY + panelH - y - 122));
        stepsField = MultiLineEditBox.builder()
                .setX(x).setY(y)
                .setPlaceholder(Component.literal("Optional: 1. ...  2. ...  3. ..."))
                .build(font, fieldW, stepsH, Component.literal("Schritte zum Nachstellen"));
        stepsField.setCharacterLimit(2000);
        stepsField.setValue(stepsDraft);
        stepsField.setValueListener(value -> stepsDraft = value);
        addRenderableWidget(stepsField);

        int optionY = panelY + panelH - 84;
        int gap = 8;
        int optionW = (fieldW - gap) / 2;
        screenshotButton = Button.builder(Component.empty(), button -> {
                    screenshotEnabled = latestScreenshot != null && !screenshotEnabled;
                    updateOptionLabels();
                })
                .bounds(x, optionY, optionW, 20)
                .build();
        addRenderableWidget(screenshotButton);
        logButton = Button.builder(Component.empty(), button -> {
                    attachLog = !attachLog;
                    updateOptionLabels();
                })
                .bounds(x + optionW + gap, optionY, optionW, 20)
                .build();
        addRenderableWidget(logButton);

        int footerY = panelY + panelH - 34;
        addRenderableWidget(Button.builder(Component.literal("Abbrechen"), button -> onClose())
                .bounds(x, footerY, 92, 20)
                .build());
        openButton = Button.builder(Component.literal("Discord öffnen"), button -> {
                    if (!resultUrl.isBlank()) Util.getPlatform().openUri(URI.create(resultUrl));
                })
                .bounds(x + 100, footerY, 112, 20)
                .build();
        openButton.visible = !resultUrl.isBlank();
        addRenderableWidget(openButton);
        sendButton = Button.builder(Component.literal("Bugmeldung senden"), button -> submit())
                .bounds(panelX + panelW - 166, footerY, 148, 20)
                .build();
        addRenderableWidget(sendButton);

        updateOptionLabels();
        updateSendButton();
    }

    private void updateOptionLabels() {
        String screenshotLabel = latestScreenshot == null
                ? "Screenshot: keiner gefunden"
                : "Screenshot: " + (screenshotEnabled ? "LETZTER AN" : "AUS");
        screenshotButton.setMessage(Component.literal(screenshotLabel));
        screenshotButton.active = latestScreenshot != null && !sending;
        logButton.setMessage(Component.literal("latest.log anhängen: " + (attachLog ? "AN" : "AUS")));
        logButton.active = !sending;
    }

    private void updateSendButton() {
        if (sendButton == null) return;
        sendButton.active = !sending
                && titleDraft.trim().length() >= 5
                && descriptionDraft.trim().length() >= 10;
        sendButton.setMessage(Component.literal(sending ? "Wird gesendet..." : "Bugmeldung senden"));
    }

    private void submit() {
        if (sending) return;
        sending = true;
        resultUrl = "";
        openButton.visible = false;
        status = screenshotEnabled ? "Screenshot wird hochgeladen..." : "Bugmeldung wird übermittelt...";
        statusColor = MUTED;
        titleField.active = false;
        descriptionField.active = false;
        stepsField.active = false;
        updateOptionLabels();
        updateSendButton();

        BugReportClient.submit(
                titleDraft,
                descriptionDraft,
                stepsDraft,
                screenshotEnabled ? latestScreenshot : null,
                attachLog
        ).whenComplete((result, throwable) -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                sending = false;
                titleField.active = true;
                descriptionField.active = true;
                stepsField.active = true;
                if (throwable == null) {
                    resultUrl = result.url();
                    minecraft.keyboardHandler.setClipboard(resultUrl);
                    status = "Gesendet – Discord-Link wurde kopiert.";
                    statusColor = 0xFF4ADE80;
                    openButton.visible = true;
                } else {
                    status = readableError(throwable);
                    statusColor = 0xFFFF5555;
                }
                updateOptionLabels();
                updateSendButton();
            });
        });
    }

    private static String readableError(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current.getCause() != null) && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) return "Bugmeldung konnte nicht gesendet werden.";
        return message.length() > 96 ? message.substring(0, 93) + "..." : message;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xAA000000);
        int panelW = Math.min(600, width - 30);
        int panelH = Math.min(430, height - 24);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        context.fill(panelX, panelY, panelX + panelW, panelY + 1, PANEL_BORDER);
        context.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, PANEL_BORDER);
        context.fill(panelX, panelY, panelX + 1, panelY + panelH, PANEL_BORDER);
        context.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, PANEL_BORDER);
        context.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 4, AMBER);

        int x = panelX + 18;
        context.text(font, Component.literal("Bug melden"), x, panelY + 15, TEXT);
        context.text(font, Component.literal("Die Meldung wird öffentlich im Discord-Forum erstellt."), x, panelY + 30, MUTED);
        context.text(font, Component.literal("Titel *"), x, panelY + 49, MUTED);
        context.text(font, Component.literal("Beschreibung *"), x, titleField.getY() + 28, MUTED);
        context.text(font, Component.literal("Schritte zum Nachstellen (optional)"), x, descriptionField.getY() + descriptionField.getHeight() + 7, MUTED);
        context.text(font, Component.literal("Anhänge werden nur nach deiner Auswahl gesendet. Sitzungsdaten werden aus dem Log entfernt."),
                x, screenshotButton.getY() - 12, 0xFFFBBF24);
        if (!status.isBlank()) {
            context.text(font, Component.literal(status), x, panelY + panelH - 55, statusColor);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null && !sending) ClientCompat.setScreen(minecraft, parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
