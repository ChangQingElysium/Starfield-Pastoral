package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

@Environment(EnvType.CLIENT)
public abstract class RenderGuiEvent extends Event {
    private final GuiGraphics graphics;
    private final DeltaTracker partialTick;

    private RenderGuiEvent(GuiGraphics graphics, DeltaTracker partialTick, boolean cancelable) {
        super(cancelable);
        this.graphics = graphics;
        this.partialTick = partialTick;
    }

    public GuiGraphics getGuiGraphics() { return graphics; }
    public DeltaTracker getPartialTick() { return partialTick; }

    public static final class Pre extends RenderGuiEvent {
        public Pre(GuiGraphics graphics, DeltaTracker partialTick) { super(graphics, partialTick, true); }
    }

    public static final class Post extends RenderGuiEvent {
        public Post(GuiGraphics graphics, DeltaTracker partialTick) { super(graphics, partialTick, false); }
    }
}
