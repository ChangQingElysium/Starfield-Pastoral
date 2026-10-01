package com.stardew.craft.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** PORT(1.20.1): lets {@code PortScreens} call the 1.21-visible container rendering steps. */
@Mixin(AbstractContainerScreen.class)
public interface PortContainerScreenAccessor {
    @Invoker("renderBg")
    void port$renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY);

    /** 1.20.1 renderSlot is private; 1.21 made it protected. */
    @Invoker("renderSlot")
    void port$renderSlot(GuiGraphics graphics, Slot slot);
}
