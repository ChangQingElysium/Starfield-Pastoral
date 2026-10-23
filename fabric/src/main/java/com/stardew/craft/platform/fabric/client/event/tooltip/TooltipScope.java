package com.stardew.craft.platform.fabric.client.event.tooltip;

import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;

/** Per-GuiGraphics context, restored when nested tooltips or a renderer throws. */
public final class TooltipScope {
    public interface Access { TooltipScope stardewcraft$tooltipScope(); }
    private ItemStack stack = ItemStack.EMPTY;
    public ItemStack stack() { return stack; }
    public <T> T with(ItemStack stack, Supplier<T> body) {
        ItemStack previous = this.stack;
        this.stack = stack;
        try { return body.get(); }
        finally { this.stack = previous; }
    }
}
