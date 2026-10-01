package com.stardew.craft.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** PORT(1.20.1): the action-bar message read by {@code StardewReadingOverlayMessageMixin}. */
@Mixin(Gui.class)
public interface PortGuiOverlayAccessor {
    @Accessor("overlayMessageString")
    Component port$overlayMessageString();
}
