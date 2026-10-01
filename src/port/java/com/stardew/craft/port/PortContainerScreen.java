package com.stardew.craft.port;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * PORT(1.20.1): 1.21.1 {@code AbstractContainerScreen} slot hooks. On 1.20.1 {@code renderSlot} is private and the
 * per-slot {@code renderSlotHighlight(GuiGraphics, Slot, int, int, float)} does not exist, so a subclass "override"
 * is never called. {@code PortContainerScreenMixin} routes both calls in {@code AbstractContainerScreen#render}
 * through these methods. A screen that overrides 1.21's {@code renderSlot} keeps its method and bridges
 * {@link #port$renderSlot} to it; 1.21's {@code super.renderSlot(...)} becomes
 * {@code PortContainerScreen.super.port$renderSlot(...)}. The names differ from vanilla's on purpose so the
 * reobfuscation remapper can never confuse them with the private vanilla method.
 */
@OnlyIn(Dist.CLIENT)
public interface PortContainerScreen extends PortScreen {
    /** 1.21.1 {@code AbstractContainerScreen#renderSlot}: the vanilla slot renderer. */
    default void port$renderSlot(GuiGraphics graphics, Slot slot) {
        PortScreens.vanillaRenderSlot((AbstractContainerScreen<?>) this, graphics, slot);
    }

    /** 1.21.1 {@code AbstractContainerScreen#renderSlotHighlight(GuiGraphics, Slot, int, int, float)}. */
    default void port$renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        PortScreens.vanillaRenderSlotHighlight((AbstractContainerScreen<?>) this, graphics, slot);
    }
}
