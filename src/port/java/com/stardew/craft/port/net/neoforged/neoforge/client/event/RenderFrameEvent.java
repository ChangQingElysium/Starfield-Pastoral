package com.stardew.craft.port.net.neoforged.neoforge.client.event;

import com.stardew.craft.port.net.minecraft.client.DeltaTracker;
import net.minecraftforge.eventbus.api.Event;

/**
 * PORT(1.20.1): NeoForge 21.1 frame render event (game bus, client only), bridged from Forge
 * {@code TickEvent.RenderTickEvent} ({@code START} -> {@link Pre}, {@code END} -> {@link Post}) by
 * {@code com.stardew.craft.port.event.PortRenderFrameBridge}.
 */
public abstract class RenderFrameEvent extends Event {
    protected final DeltaTracker partialTick;

    protected RenderFrameEvent(DeltaTracker partialTick) {
        this.partialTick = partialTick;
    }

    public DeltaTracker getPartialTick() {
        return this.partialTick;
    }

    public static class Pre extends RenderFrameEvent {
        public Pre() {
            this(null);
        }

        public Pre(DeltaTracker partialTick) {
            super(partialTick);
        }
    }

    public static class Post extends RenderFrameEvent {
        public Post() {
            this(null);
        }

        public Post(DeltaTracker partialTick) {
            super(partialTick);
        }
    }
}
