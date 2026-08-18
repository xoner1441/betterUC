package com.betteruc.mixin;

import com.betteruc.ServerGate;
import com.betteruc.client.TrustedChatCommands;
import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ChatCommandConfirmationMixin {

    @Inject(
            method = "defaultHandleGameClickEvent(Lnet/minecraft/network/chat/ClickEvent;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void betteruc$handleRunCommandWithoutConfirmation(
            ClickEvent event,
            Minecraft client,
            Screen screen,
            CallbackInfo ci
    ) {
        if (BetterUCConfig.INSTANCE.chatCommandConfirmationEnabled
                || !(event instanceof ClickEvent.RunCommand runCommand)
                || client == null
                || client.player == null
                || !ServerGate.isAllowedServer(client)) {
            return;
        }

        String command = Commands.trimOptionalPrefix(runCommand.command()).trim();
        if (command.isEmpty() || !TrustedChatCommands.isTrusted(command)) {
            return;
        }

        client.player.connection.sendCommand(command);
        client.gui.setScreen(screen);
        ci.cancel();
    }
}
