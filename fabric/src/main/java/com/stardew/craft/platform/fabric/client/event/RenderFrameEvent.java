package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;

@Environment(EnvType.CLIENT)
public abstract class RenderFrameEvent extends Event {
    private final DeltaTracker partialTick;
    protected RenderFrameEvent(DeltaTracker partialTick) { this.partialTick = partialTick; }
    public DeltaTracker getPartialTick() { return partialTick; }
    public static final class Pre extends RenderFrameEvent { public Pre(DeltaTracker partialTick) { super(partialTick); } }
    public static final class Post extends RenderFrameEvent { public Post(DeltaTracker partialTick) { super(partialTick); } }
}
