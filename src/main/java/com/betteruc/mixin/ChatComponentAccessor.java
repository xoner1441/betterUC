package com.betteruc.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> betteruc$getTrimmedMessages();

    @Accessor("chatScrollbarPos")
    int betteruc$getChatScrollbarPos();

    @Invoker("getLineHeight")
    int betteruc$invokeGetLineHeight();

    @Invoker("getLinesPerPage")
    int betteruc$invokeGetLinesPerPage();
}
