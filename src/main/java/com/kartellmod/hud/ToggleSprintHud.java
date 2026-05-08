package com.kartellmod.hud;

import com.kartellmod.KartellModClient;
import com.kartellmod.config.KartellConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class ToggleSprintHud {

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!KartellConfig.INSTANCE.toggleSprintEnabled) return;

        boolean isOn = KartellModClient.isToggleSprintHudActive();
        String text = "ToggleSprint: " + (isOn ? "ON" : "OFF");
        int color = KartellConfig.INSTANCE.toggleSprintHudColor;

        int x = KartellConfig.INSTANCE.toggleSprintHudX;
        int y = KartellConfig.INSTANCE.toggleSprintHudY;
        context.drawTextWithShadow(client.textRenderer, Text.literal(text), x, y, color);
    }
}
