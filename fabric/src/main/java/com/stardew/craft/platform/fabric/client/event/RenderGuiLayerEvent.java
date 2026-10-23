package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public abstract class RenderGuiLayerEvent extends Event {
    private final GuiGraphics graphics;
    private final DeltaTracker partialTick;
    private final ResourceLocation name;
    private final LayeredDraw.Layer layer;

    protected RenderGuiLayerEvent(boolean cancelable, GuiGraphics graphics, DeltaTracker partialTick,
                                  ResourceLocation name, LayeredDraw.Layer layer) {
        super(cancelable);
        this.graphics = graphics;
        this.partialTick = partialTick;
        this.name = name;
        this.layer = layer;
    }

    public GuiGraphics getGuiGraphics() { return graphics; }
    public DeltaTracker getPartialTick() { return partialTick; }
    public ResourceLocation getName() { return name; }
    public LayeredDraw.Layer getLayer() { return layer; }

    public static class Pre extends RenderGuiLayerEvent {
        public Pre(GuiGraphics graphics, DeltaTracker partialTick, ResourceLocation name, LayeredDraw.Layer layer) {
            super(true, graphics, partialTick, name, layer);
        }
    }

    public static class Post extends RenderGuiLayerEvent {
        public Post(GuiGraphics graphics, DeltaTracker partialTick, ResourceLocation name, LayeredDraw.Layer layer) {
            super(false, graphics, partialTick, name, layer);
        }
    }
}
