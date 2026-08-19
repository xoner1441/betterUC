package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ArmorHud {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };
    private static final int SLOT_WIDTH = 26;
    private static final int MODERN_PADDING = 6;
    private static final int ITEM_SIZE = 16;

    private ArmorHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "armor"), (context, tickCounter) -> {
            if (ModernHudRenderer.shouldRenderGameplayHud()) {
                render(context);
            }
        });
    }

    private static void render(GuiGraphicsExtractor context) {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        Minecraft client = Minecraft.getInstance();
        if (!config.showArmorHud || client.player == null) return;

        ItemStack[] armor = new ItemStack[ARMOR_SLOTS.length];
        boolean hasArmor = false;
        for (int index = 0; index < ARMOR_SLOTS.length; index++) {
            ItemStack stack = client.player.getItemBySlot(ARMOR_SLOTS[index]);
            armor[index] = stack == null ? ItemStack.EMPTY : stack;
            hasArmor |= !armor[index].isEmpty();
        }
        if (!hasArmor) return;

        ModernHudRenderer.drawScaledWithGradient(
                context,
                config.armorHudX,
                config.armorHudY,
                config.armorHudScale,
                config.armorHudGradientEnabled,
                config.armorHudGradientColor,
                () -> drawArmor(
                        context,
                        client,
                        armor,
                        0,
                        0,
                        config.armorHudStyle,
                        config.armorHudCustomFont,
                        config.armorHudColor,
                        config.armorHudDurabilityEnabled
                )
        );
    }

    public static void drawPreview(
            GuiGraphicsExtractor context,
            Minecraft client,
            int x,
            int y,
            String style,
            String fontId,
            int accentColor,
            boolean showDurability
    ) {
        drawArmor(context, client, previewArmor(), x, y, style, fontId, accentColor, showDurability);
    }

    public static int previewWidth(String style) {
        return widthForSlotCount(style, ARMOR_SLOTS.length);
    }

    static int widthForSlotCount(String style, int slotCount) {
        return widthForSlotCount(BetterUCConfig.isModernHudStyle(style), slotCount);
    }

    static int widthForSlotCount(boolean modern, int slotCount) {
        int safeSlotCount = Math.max(1, Math.min(ARMOR_SLOTS.length, slotCount));
        int contentWidth = SLOT_WIDTH * safeSlotCount;
        return modern ? contentWidth + MODERN_PADDING * 2 : contentWidth;
    }

    public static int previewHeight(String style, boolean showDurability) {
        if (BetterUCConfig.isModernHudStyle(style)) {
            return showDurability ? 36 : 26;
        }
        if (!showDurability) return ITEM_SIZE;
        return BetterUCConfig.isStylizedHudStyle(style) ? 33 : 30;
    }

    static int durabilityPercent(int maxDamage, int damageValue) {
        if (maxDamage <= 0) return 100;
        int safeDamage = Math.max(0, Math.min(maxDamage, damageValue));
        return Math.max(0, Math.min(100, Math.round((maxDamage - safeDamage) * 100.0F / maxDamage)));
    }

    static int durabilityColor(int percent) {
        int safePercent = Math.max(0, Math.min(100, percent));
        if (safePercent <= 20) return 0xFFFF5555;
        if (safePercent <= 50) return 0xFFFFAA00;
        return 0xFF55FF55;
    }

    private static void drawArmor(
            GuiGraphicsExtractor context,
            Minecraft client,
            ItemStack[] armor,
            int x,
            int y,
            String style,
            String fontId,
            int accentColor,
            boolean showDurability
    ) {
        boolean modern = BetterUCConfig.isModernHudStyle(style);
        int occupiedSlots = 0;
        for (ItemStack stack : armor) {
            if (stack != null && !stack.isEmpty()) occupiedSlots++;
        }
        if (occupiedSlots == 0) return;

        int width = widthForSlotCount(style, occupiedSlots);
        int height = previewHeight(style, showDurability);
        if (modern) {
            ModernHudRenderer.drawPanel(context, x, y, width, height, accentColor);
        }

        int contentX = x + (modern ? MODERN_PADDING : 0);
        int itemY = y + (modern ? 4 : 0);
        int frameColor = withAlpha(ModernHudRenderer.hudTextColor(accentColor), modern ? 0x38 : 0x68);

        int renderedSlot = 0;
        for (int index = 0; index < armor.length; index++) {
            ItemStack stack = index < armor.length && armor[index] != null ? armor[index] : ItemStack.EMPTY;
            if (stack.isEmpty()) continue;

            int slotX = contentX + renderedSlot * SLOT_WIDTH;
            int itemX = slotX + (SLOT_WIDTH - ITEM_SIZE) / 2;
            context.outline(itemX - 1, itemY - 1, ITEM_SIZE + 2, ITEM_SIZE + 2, frameColor);
            context.item(stack, itemX, itemY);
            renderedSlot++;

            if (!showDurability) continue;
            int percent = durabilityPercent(stack.getMaxDamage(), stack.getDamageValue());
            int color = durabilityColor(percent);
            String text = percent + "%";
            int textX = slotX + (SLOT_WIDTH - client.font.width(text)) / 2;
            int textY = itemY + 17;
            if (BetterUCConfig.isStylizedHudStyle(style)) {
                ModernHudRenderer.drawStyledText(context, client, style, fontId, text, textX, textY, color);
            } else {
                ModernHudRenderer.drawHudTextWithShadow(context, client.font, text, textX, textY, color);
            }

            int barX = slotX + 2;
            int barY = itemY + (BetterUCConfig.isStylizedHudStyle(style) ? 30 : 27);
            int barWidth = SLOT_WIDTH - 4;
            context.fill(barX, barY, barX + barWidth, barY + 2, 0x66313A47);
            int filledWidth = Math.round(barWidth * (percent / 100.0F));
            if (filledWidth > 0) {
                context.fill(barX, barY, barX + filledWidth, barY + 2, color);
            }
        }
    }

    private static ItemStack[] previewArmor() {
        ItemStack[] armor = {
                new ItemStack(Items.DIAMOND_HELMET),
                new ItemStack(Items.DIAMOND_CHESTPLATE),
                new ItemStack(Items.DIAMOND_LEGGINGS),
                new ItemStack(Items.DIAMOND_BOOTS)
        };
        int[] remaining = {92, 68, 43, 17};
        for (int index = 0; index < armor.length; index++) {
            int maxDamage = armor[index].getMaxDamage();
            armor[index].setDamageValue(Math.round(maxDamage * (100 - remaining[index]) / 100.0F));
        }
        return armor;
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }
}
