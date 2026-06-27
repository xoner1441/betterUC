package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import java.util.ArrayList;
import java.util.List;

public class PotionEffectsHud {

    private static final int EFFECT_WIDTH = 120;
    private static final int EFFECT_HEIGHT = 32;
    private static final int EFFECT_ICON_SIZE = 18;
    private static final int EFFECT_BASE_SPACING = 33;
    private static final int EFFECT_MAX_SPAN = 132;
    private static final List<MobEffectInstance> ACTIVE_EFFECTS = new ArrayList<>();

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "potion_effects"), (context, tickCounter) -> render(context));
    }

    private static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!BetterUCConfig.INSTANCE.showPotionEffectsHud) return;

        ACTIVE_EFFECTS.clear();
        ACTIVE_EFFECTS.addAll(client.player.getActiveEffects());
        if (ACTIVE_EFFECTS.isEmpty()) return;

        ACTIVE_EFFECTS.sort(null);

        int x = BetterUCConfig.INSTANCE.potionHudX;
        int y = BetterUCConfig.INSTANCE.potionHudY;
        int spacing = EFFECT_BASE_SPACING;
        if (ACTIVE_EFFECTS.size() > 5) {
            spacing = Math.max(1, EFFECT_MAX_SPAN / (ACTIVE_EFFECTS.size() - 1));
        }
        String style = BetterUCConfig.INSTANCE.potionHudStyle;
        float tickRate = client.level == null ? 20.0F : client.level.tickRateManager().tickrate();
        int effectSpacing = spacing;

        ModernHudRenderer.drawScaledWithGradient(
                context,
                x,
                y,
                BetterUCConfig.INSTANCE.potionHudScale,
                BetterUCConfig.INSTANCE.potionHudGradientEnabled,
                BetterUCConfig.INSTANCE.potionHudGradientColor,
                () -> {
            int currentY = 0;
            for (MobEffectInstance effect : ACTIVE_EFFECTS) {
                Holder<MobEffect> entry = effect.getEffect();
                Identifier effectIcon = effectIcon(entry);
                int accentColor = 0xFF000000 | entry.value().getColor();
                Component effectName = buildEffectName(effect);
                Component durationText = MobEffectUtil.formatDuration(effect, 1.0F, tickRate);

                if (BetterUCConfig.isStylizedHudStyle(style)) {
                    ModernHudRenderer.drawStyledText(context, client.font, style, BetterUCConfig.INSTANCE.potionHudCustomFont, effectName, 0, currentY, accentColor);
                    ModernHudRenderer.drawStyledText(context, client.font, style, BetterUCConfig.INSTANCE.potionHudCustomFont, durationText, 0, currentY + 11, ModernHudRenderer.TEXT_DIM);
                    currentY += Math.max(23, effectSpacing - 9);
                    continue;
                }

                if (!BetterUCConfig.isModernHudStyle(style)) {
                    ModernHudRenderer.drawHudTextWithShadow(context, client.font, effectName, 0, currentY, accentColor);
                    ModernHudRenderer.drawHudTextWithShadow(context, client.font, durationText, 0, currentY + 10, ModernHudRenderer.TEXT_DIM);
                    currentY += Math.max(21, effectSpacing - 11);
                    continue;
                }

                ModernHudRenderer.drawPanel(context, 0, currentY, EFFECT_WIDTH, EFFECT_HEIGHT, accentColor);
                if (ModernHudRenderer.isRightAligned(0, EFFECT_WIDTH)) {
                    int iconX = EFFECT_WIDTH - EFFECT_ICON_SIZE - 7;
                    int textRight = iconX - 6;
                    context.blitSprite(RenderPipelines.GUI_TEXTURED, effectIcon, iconX, currentY + 7, EFFECT_ICON_SIZE, EFFECT_ICON_SIZE);
                    context.text(
                            client.font,
                            effectName,
                            Math.max(8, textRight - client.font.width(effectName)),
                            currentY + 6,
                            ModernHudRenderer.TEXT_PRIMARY
                    );
                    context.text(
                            client.font,
                            durationText,
                            Math.max(8, textRight - client.font.width(durationText)),
                            currentY + 16,
                            ModernHudRenderer.TEXT_DIM
                    );
                } else {
                    context.blitSprite(RenderPipelines.GUI_TEXTURED, effectIcon, 6, currentY + 7, EFFECT_ICON_SIZE, EFFECT_ICON_SIZE);
                    context.text(client.font, effectName, 28, currentY + 6, ModernHudRenderer.TEXT_PRIMARY);
                    context.text(client.font, durationText, 28, currentY + 16, ModernHudRenderer.TEXT_DIM);
                }

                currentY += effectSpacing;
            }
        });
    }

    private static Component buildEffectName(MobEffectInstance effect) {
        MutableComponent name = effect.getEffect().value().getDisplayName().copy();
        int amplifier = effect.getAmplifier();
        if (amplifier >= 1 && amplifier <= 9) {
            name.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + (amplifier + 1)));
        }
        return name;
    }

    private static Identifier effectIcon(Holder<MobEffect> entry) {
        return entry.unwrapKey()
                .map(key -> key.identifier().withPrefix("mob_effect/"))
                .orElse(Identifier.withDefaultNamespace("mob_effect/speed"));
    }
}
