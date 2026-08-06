package com.betteruc.gui;

import com.betteruc.client.ClientCompat;
import com.betteruc.client.SecondChatManager;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.config.SecondChatWindowConfig;
import com.betteruc.hud.SecondChatHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class SecondChatEditorScreen extends Screen {

    private static final int TEXT_PRIMARY = 0xFFF8FAFC;
    private static final int TEXT_MUTED = 0xFF94A3B8;
    private static final int HANDLE_SIZE = 5;
    private static final int HANDLE_HIT_SIZE = 11;
    private static final int SNAP_DISTANCE = 8;
    private final Screen parent;
    private final String windowId;
    private boolean dragging;
    private boolean resizing;
    private int dragOffsetX;
    private int dragOffsetY;
    private int resizeStartWidth;
    private int resizeStartHeight;
    private double resizeStartMouseX;
    private double resizeStartMouseY;
    private Button lockButton;

    public SecondChatEditorScreen(Screen parent) {
        this(parent, SecondChatManager.PRIMARY_WINDOW_ID);
    }

    public SecondChatEditorScreen(Screen parent, String windowId) {
        super(Component.literal("Chatfenster Editor"));
        this.parent = parent;
        this.windowId = windowId == null ? SecondChatManager.PRIMARY_WINDOW_ID : windowId;
    }

    @Override
    protected void init() {
        int controlsY = height - 28;
        if (isPrimary()) {
            lockButton = null;
            addRenderableWidget(Button.builder(Component.literal("Vanilla-Größe"), b -> resetLayout())
                    .bounds(12, controlsY, 112, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Verlauf leeren"), b -> SecondChatManager.clear())
                    .bounds(130, controlsY, 112, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                    .bounds(width - 92, controlsY, 80, 20)
                    .build());
            return;
        }
        lockButton = addRenderableWidget(Button.builder(lockLabel(), b -> toggleLock())
                .bounds(12, controlsY, 92, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Zurücksetzen"), b -> resetLayout())
                .bounds(110, controlsY, 102, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Verlauf leeren"), b -> SecondChatManager.clear())
                .bounds(218, controlsY, 102, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(width - 92, controlsY, 80, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderEditorBackground(context);
        context.text(font, Component.literal("Chatfenster Editor"), 12, 12, TEXT_PRIMARY);
        context.text(font, Component.literal(isPrimary()
                ? "Die kleine Ecke oben rechts ändert Breite und Höhe. Vanilla-Größe setzt die Minecraft-Maße zurück."
                : isLocked()
                    ? "Fenster entsperren, um es zu verschieben oder zu skalieren."
                    : "Am Titel ziehen. Die kleine Ecke unten rechts skaliert."),
                12, 25, TEXT_MUTED);

        SecondChatHud.renderEditorPreview(context, windowId);
        drawEditorOutline(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0 || (!isPrimary() && isLocked())) return false;

        SecondChatHud.Bounds bounds = SecondChatHud.configuredBounds(windowId);
        if (resizeHandleContains(bounds, event.x(), event.y())) {
            resizing = true;
            resizeStartWidth = bounds.panelWidth();
            resizeStartHeight = bounds.panelHeight();
            resizeStartMouseX = event.x();
            resizeStartMouseY = event.y();
            return true;
        }
        if (!isPrimary()
                && event.x() >= bounds.x() && event.x() < bounds.x() + bounds.panelWidth()
                && event.y() >= bounds.y() && event.y() < bounds.y() + SecondChatHud.TITLE_HEIGHT) {
            dragging = true;
            dragOffsetX = (int) Math.round(event.x()) - bounds.x();
            dragOffsetY = (int) Math.round(event.y()) - bounds.y();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double offsetX, double offsetY) {
        if (event.button() != 0) return super.mouseDragged(event, offsetX, offsetY);
        if (dragging) {
            SecondChatHud.Bounds bounds = SecondChatHud.configuredBounds(windowId);
            int maxX = Math.max(0, width - bounds.panelWidth());
            int maxY = Math.max(0, height - bounds.panelHeight());
            int targetX = clamp((int) Math.round(event.x()) - dragOffsetX, 0, maxX);
            int targetY = clamp((int) Math.round(event.y()) - dragOffsetY, 0, maxY);
            int[] snapped = snapPosition(targetX, targetY, bounds.panelWidth(), bounds.panelHeight());
            setPosition(snapped[0], snapped[1]);
            return true;
        }
        if (resizing) {
            SecondChatHud.Bounds bounds = SecondChatHud.configuredBounds(windowId);
            int targetWidth = resizeStartWidth + (int) Math.round(event.x() - resizeStartMouseX);
            int targetHeight = resizeStartHeight + (int) Math.round(
                    (isPrimary() ? resizeStartMouseY - event.y() : event.y() - resizeStartMouseY));
            int clampedWidth = clamp(targetWidth, SecondChatHud.MIN_WIDTH,
                    Math.min(SecondChatHud.MAX_WIDTH,
                            Math.max(SecondChatHud.MIN_WIDTH, width - bounds.x())));
            int clampedHeight = clamp(targetHeight, SecondChatHud.MIN_HEIGHT,
                    isPrimary()
                            ? Math.min(SecondChatHud.MAX_HEIGHT, Math.max(SecondChatHud.MIN_HEIGHT, height - 40))
                            : Math.min(SecondChatHud.MAX_HEIGHT,
                                    Math.max(SecondChatHud.MIN_HEIGHT, height - bounds.y())));
            if (isPrimary()) {
                setSize(clampedWidth, clampedHeight);
                return true;
            }
            int[] snapped = snapSize(bounds, clampedWidth, clampedHeight);
            setSize(snapped[0], snapped[1]);
            return true;
        }
        return super.mouseDragged(event, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging || resizing) {
            dragging = false;
            resizing = false;
            BetterUCConfig.sanitizeSecondChat();
            BetterUCConfig.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void removed() {
        BetterUCConfig.sanitizeSecondChat();
        BetterUCConfig.save();
        super.removed();
    }

    @Override
    public void onClose() {
        BetterUCConfig.sanitizeSecondChat();
        BetterUCConfig.save();
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void toggleLock() {
        setLocked(!isLocked());
        dragging = false;
        resizing = false;
        lockButton.setMessage(lockLabel());
        BetterUCConfig.save();
    }

    private void resetLayout() {
        if (isPrimary()) {
            BetterUCConfig.INSTANCE.secondChatPrimaryCustomSize = false;
        } else {
            setPosition(Math.max(8, width - 338), 62);
            setSize(330, 120);
        }
        BetterUCConfig.sanitizeSecondChat();
        BetterUCConfig.save();
    }

    private Component lockLabel() {
        return Component.literal(isLocked() ? "Entsperren" : "Sperren");
    }

    private void drawEditorOutline(GuiGraphicsExtractor context) {
        SecondChatHud.Bounds bounds = SecondChatHud.configuredBounds(windowId);
        int color = isLocked() ? 0xAA94A3B8 : 0xFF38BDF8;
        context.fill(bounds.x(), bounds.y(), bounds.x() + bounds.panelWidth(), bounds.y() + 1, color);
        context.fill(bounds.x(), bounds.y() + bounds.panelHeight() - 1,
                bounds.x() + bounds.panelWidth(), bounds.y() + bounds.panelHeight(), color);
        context.fill(bounds.x(), bounds.y(), bounds.x() + 1, bounds.y() + bounds.panelHeight(), color);
        context.fill(bounds.x() + bounds.panelWidth() - 1, bounds.y(),
                bounds.x() + bounds.panelWidth(), bounds.y() + bounds.panelHeight(), color);

        if (isPrimary() || !isLocked()) {
            int handleX = bounds.x() + bounds.panelWidth() - HANDLE_SIZE;
            int handleY = isPrimary()
                    ? bounds.y()
                    : bounds.y() + bounds.panelHeight() - HANDLE_SIZE;
            context.fill(handleX, handleY, handleX + HANDLE_SIZE, handleY + HANDLE_SIZE, 0xFFF8FAFC);
            context.fill(handleX + 2, handleY + 2, handleX + HANDLE_SIZE, handleY + HANDLE_SIZE, color);
        }
    }

    private void renderEditorBackground(GuiGraphicsExtractor context) {
        context.fill(0, 0, width, height, 0x99000000);
        for (int x = 0; x < width; x += 20) {
            context.fill(x, 0, x + 1, height, 0x18FFFFFF);
        }
        for (int y = 0; y < height; y += 20) {
            context.fill(0, y, width, y + 1, 0x18FFFFFF);
        }
        context.fill(0, 0, width, 39, 0xD00D1117);
        context.fill(0, height - 38, width, height, 0xE00D1117);
    }

    private boolean resizeHandleContains(SecondChatHud.Bounds bounds, double mouseX, double mouseY) {
        int handleY = isPrimary()
                ? bounds.y()
                : bounds.y() + bounds.panelHeight() - HANDLE_HIT_SIZE;
        return mouseX >= bounds.x() + bounds.panelWidth() - HANDLE_HIT_SIZE
                && mouseX <= bounds.x() + bounds.panelWidth() + 3
                && mouseY >= handleY - (isPrimary() ? 3 : 0)
                && mouseY <= handleY + HANDLE_HIT_SIZE + 3;
    }

    private int[] snapPosition(int x, int y, int panelWidth, int panelHeight) {
        x = snap(x, 0);
        x = snap(x, width - panelWidth);
        y = snap(y, 0);
        y = snap(y, height - panelHeight);

        for (SecondChatWindowConfig other : SecondChatManager.windows()) {
            if (windowId.equals(other.id)) {
                continue;
            }
            SecondChatHud.Bounds bounds = SecondChatHud.configuredBounds(other.id);
            x = snap(x, bounds.x());
            x = snap(x, bounds.x() + bounds.panelWidth());
            x = snap(x, bounds.x() - panelWidth);
            x = snap(x, bounds.x() + bounds.panelWidth() - panelWidth);
            y = snap(y, bounds.y());
            y = snap(y, bounds.y() + bounds.panelHeight());
            y = snap(y, bounds.y() - panelHeight);
            y = snap(y, bounds.y() + bounds.panelHeight() - panelHeight);
        }
        return new int[]{
                clamp(x, 0, Math.max(0, width - panelWidth)),
                clamp(y, 0, Math.max(0, height - panelHeight))
        };
    }

    private int[] snapSize(SecondChatHud.Bounds current, int panelWidth, int panelHeight) {
        int right = snap(current.x() + panelWidth, width);
        int bottom = snap(current.y() + panelHeight, height);
        for (SecondChatWindowConfig other : SecondChatManager.windows()) {
            if (windowId.equals(other.id)) {
                continue;
            }
            SecondChatHud.Bounds bounds = SecondChatHud.configuredBounds(other.id);
            right = snap(right, bounds.x());
            right = snap(right, bounds.x() + bounds.panelWidth());
            bottom = snap(bottom, bounds.y());
            bottom = snap(bottom, bounds.y() + bounds.panelHeight());
        }
        return new int[]{
                clamp(right - current.x(), SecondChatHud.MIN_WIDTH,
                        Math.min(SecondChatHud.MAX_WIDTH, width - current.x())),
                clamp(bottom - current.y(), SecondChatHud.MIN_HEIGHT,
                        Math.min(SecondChatHud.MAX_HEIGHT, height - current.y()))
        };
    }

    private static int snap(int value, int target) {
        return Math.abs(value - target) <= SNAP_DISTANCE ? target : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isPrimary() {
        return SecondChatManager.PRIMARY_WINDOW_ID.equals(windowId);
    }

    private SecondChatWindowConfig windowConfig() {
        return SecondChatManager.findWindow(windowId);
    }

    private boolean isLocked() {
        SecondChatWindowConfig window = windowConfig();
        return isPrimary() ? BetterUCConfig.INSTANCE.secondChatLocked : window == null || window.locked;
    }

    private void setLocked(boolean locked) {
        SecondChatWindowConfig window = windowConfig();
        if (isPrimary()) {
            BetterUCConfig.INSTANCE.secondChatLocked = locked;
        } else if (window != null) {
            window.locked = locked;
        }
    }

    private void setPosition(int x, int y) {
        SecondChatWindowConfig window = windowConfig();
        if (isPrimary()) {
            BetterUCConfig.INSTANCE.secondChatX = x;
            BetterUCConfig.INSTANCE.secondChatY = y;
        } else if (window != null) {
            window.x = x;
            window.y = y;
        }
    }

    private void setSize(int width, int height) {
        SecondChatWindowConfig window = windowConfig();
        if (isPrimary()) {
            BetterUCConfig.INSTANCE.secondChatWidth = width;
            BetterUCConfig.INSTANCE.secondChatHeight = height;
            BetterUCConfig.INSTANCE.secondChatPrimaryCustomSize = true;
        } else if (window != null) {
            window.width = width;
            window.height = height;
        }
    }
}
