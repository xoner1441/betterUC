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
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class PotionEffectsHud {

    private static final Identifier EFFECT_BACKGROUND_LARGE_TEXTURE =
            Identifier.ofVanilla("container/inventory/effect_background_large");
    private static final int EFFECT_WIDTH = 120;
    private static final int EFFECT_HEIGHT = 32;
    private static final int EFFECT_ICON_SIZE = 18;
    private static final int EFFECT_BASE_SPACING = 33;
    private static final int EFFECT_MAX_SPAN = 132;
    private static final int DURATION_COLOR = -8421505;
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
        float tickRate = client.world == null ? 20.0F : client.world.getTickManager().getTickRate();

        for (StatusEffectInstance effect : ACTIVE_EFFECTS) {
            RegistryEntry<StatusEffect> entry = effect.getEffectType();
            Identifier effectIcon = InGameHud.getEffectTexture(entry);

            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, EFFECT_BACKGROUND_LARGE_TEXTURE, x, y, EFFECT_WIDTH, EFFECT_HEIGHT);
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, effectIcon, x + 6, y + 7, EFFECT_ICON_SIZE, EFFECT_ICON_SIZE);

            Text effectName = buildEffectName(effect);
            context.drawTextWithShadow(client.textRenderer, effectName, x + 28, y + 6, Colors.WHITE);

            Text durationText = StatusEffectUtil.getDurationText(effect, 1.0F, tickRate);
            context.drawTextWithShadow(client.textRenderer, durationText, x + 28, y + 16, DURATION_COLOR);

            y += spacing;
        }
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
