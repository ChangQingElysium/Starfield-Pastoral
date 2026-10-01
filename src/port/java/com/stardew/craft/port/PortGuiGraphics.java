package com.stardew.craft.port;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * PORT(1.20.1): 1.20.2+ {@code GuiGraphics#containsPointInScissor}, added to {@link GuiGraphics} by
 * {@code PortGuiGraphicsScissorMixin}. Use {@link #containsPointInScissor(GuiGraphics, int, int)}.
 */
@OnlyIn(Dist.CLIENT)
public interface PortGuiGraphics {
    boolean port$containsPointInScissor(int x, int y);

    static boolean containsPointInScissor(GuiGraphics graphics, int x, int y) {
        return ((PortGuiGraphics) graphics).port$containsPointInScissor(x, y);
    }
}
