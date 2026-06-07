package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.gl.RenderPipelines;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class PotionEffectsHud {

    private static final int EFFECT_WIDTH = 120;
    private static final int EFFECT_HEIGHT = 32;
    private static final int EFFECT_ICON_SIZE = 18;
    private static final int EFFECT_BASE_SPACING = 33;
    private static final int EFFECT_MAX_SPAN = 132;
    private static final List<StatusEffectInstance> ACTIVE_EFFECTS = new ArrayList<>();

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!BetterUCConfig.INSTANCE.showPotionEffectsHud) return;

        ACTIVE_EFFECTS.clear();
        ACTIVE_EFFECTS.addAll(client.player.getStatusEffects());
        if (ACTIVE_EFFECTS.isEmpty()) return;

        ACTIVE_EFFECTS.sort(null);

        int x = BetterUCConfig.INSTANCE.potionHudX;
        int y = BetterUCConfig.INSTANCE.potionHudY;
        int spacing = EFFECT_BASE_SPACING;
        if (ACTIVE_EFFECTS.size() > 5) {
            spacing = Math.max(1, EFFECT_MAX_SPAN / (ACTIVE_EFFECTS.size() - 1));
        }
        String style = BetterUCConfig.INSTANCE.potionHudStyle;
        float tickRate = client.world == null ? 20.0F : client.world.getTickManager().getTickRate();
        int effectSpacing = spacing;

        ModernHudRenderer.drawScaled(context, x, y, BetterUCConfig.INSTANCE.potionHudScale, () -> {
            int currentY = 0;
            for (StatusEffectInstance effect : ACTIVE_EFFECTS) {
                RegistryEntry<StatusEffect> entry = effect.getEffectType();
                Identifier effectIcon = InGameHud.getEffectTexture(entry);
                int accentColor = 0xFF000000 | entry.value().getColor();
                Text effectName = buildEffectName(effect);
                Text durationText = StatusEffectUtil.getDurationText(effect, 1.0F, tickRate);

                if (BetterUCConfig.isStylizedHudStyle(style)) {
                    ModernHudRenderer.drawStyledText(context, client.textRenderer, style, BetterUCConfig.INSTANCE.potionHudCustomFont, effectName, 0, currentY, accentColor);
                    ModernHudRenderer.drawStyledText(context, client.textRenderer, style, BetterUCConfig.INSTANCE.potionHudCustomFont, durationText, 0, currentY + 11, ModernHudRenderer.TEXT_DIM);
                    currentY += Math.max(23, effectSpacing - 9);
                    continue;
                }

                if (!BetterUCConfig.isModernHudStyle(style)) {
                    context.drawTextWithShadow(client.textRenderer, effectName, 0, currentY, accentColor);
                    context.drawTextWithShadow(client.textRenderer, durationText, 0, currentY + 10, ModernHudRenderer.TEXT_DIM);
                    currentY += Math.max(21, effectSpacing - 11);
                    continue;
                }

                ModernHudRenderer.drawPanel(context, 0, currentY, EFFECT_WIDTH, EFFECT_HEIGHT, accentColor);
                if (ModernHudRenderer.isRightAligned(0, EFFECT_WIDTH)) {
                    int iconX = EFFECT_WIDTH - EFFECT_ICON_SIZE - 7;
                    int textRight = iconX - 6;
                    context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, effectIcon, iconX, currentY + 7, EFFECT_ICON_SIZE, EFFECT_ICON_SIZE);
                    context.drawTextWithShadow(
                            client.textRenderer,
                            effectName,
                            Math.max(8, textRight - client.textRenderer.getWidth(effectName)),
                            currentY + 6,
                            ModernHudRenderer.TEXT_PRIMARY
                    );
                    context.drawTextWithShadow(
                            client.textRenderer,
                            durationText,
                            Math.max(8, textRight - client.textRenderer.getWidth(durationText)),
                            currentY + 16,
                            ModernHudRenderer.TEXT_DIM
                    );
                } else {
                    context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, effectIcon, 6, currentY + 7, EFFECT_ICON_SIZE, EFFECT_ICON_SIZE);
                    context.drawTextWithShadow(client.textRenderer, effectName, 28, currentY + 6, ModernHudRenderer.TEXT_PRIMARY);
                    context.drawTextWithShadow(client.textRenderer, durationText, 28, currentY + 16, ModernHudRenderer.TEXT_DIM);
                }

                currentY += effectSpacing;
            }
        });
    }

    private static Text buildEffectName(StatusEffectInstance effect) {
        MutableText name = effect.getEffectType().value().getName().copy();
        int amplifier = effect.getAmplifier();
        if (amplifier >= 1 && amplifier <= 9) {
            name.append(ScreenTexts.SPACE).append(Text.translatable("enchantment.level." + (amplifier + 1)));
        }
        return name;
    }
}
