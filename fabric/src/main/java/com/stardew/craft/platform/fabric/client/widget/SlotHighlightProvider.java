package com.stardew.craft.platform.fabric.client.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

/** Explicit replacement for Neo's protected virtual container-screen highlight hook. */
public interface SlotHighlightProvider {
    void stardewcraft$renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick);
}
