package com.stardew.craft.port.net.neoforged.neoforge.client.event;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * PORT(1.20.1): NeoForge 21.1 per-layer HUD render event, bridged from Forge {@code RenderGuiOverlayEvent.Pre/Post}.
 * {@link #getName()} is the Forge overlay id, which is what {@code VanillaGuiLayers} constants hold.
 * Cancelling {@link Pre} cancels the Forge overlay (the layer is not rendered). Game bus, client only.
 */
public abstract class RenderGuiLayerEvent extends Event {
    private final GuiGraphics guiGraphics;
    // PORT(1.20.1): NeoForge's getPartialTick() returns the frame DeltaTracker; Forge supplies the frame partial tick.
    private final float partialTick;
    private final ResourceLocation name;

    protected RenderGuiLayerEvent(GuiGraphics guiGraphics, float partialTick, ResourceLocation name) {
        this.guiGraphics = guiGraphics;
        this.partialTick = partialTick;
        this.name = name;
    }

    public GuiGraphics getGuiGraphics() {
        return this.guiGraphics;
    }

    public ResourceLocation getName() {
        return this.name;
    }

    /** NeoForge 21.1: the frame's DeltaTracker (Forge's overlay partial tick is Minecraft#getFrameTime). */
    public com.stardew.craft.port.net.minecraft.client.DeltaTracker getPartialTick() {
        return com.stardew.craft.port.net.minecraft.client.DeltaTracker.of(this.partialTick);
    }

    @Cancelable
    public static class Pre extends RenderGuiLayerEvent {
        public Pre() {
            this(null, 0.0F, null);
        }

        public Pre(GuiGraphics guiGraphics, float partialTick, ResourceLocation name) {
            super(guiGraphics, partialTick, name);
        }
    }

    public static class Post extends RenderGuiLayerEvent {
        public Post() {
            this(null, 0.0F, null);
        }

        public Post(GuiGraphics guiGraphics, float partialTick, ResourceLocation name) {
            super(guiGraphics, partialTick, name);
        }
    }
}
