package com.stardew.craft.port.event;

import com.stardew.craft.port.net.minecraft.client.DeltaTracker;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * PORT(1.20.1): {@code TickEvent.RenderTickEvent} -> NeoForge {@code RenderFrameEvent}. The 1.20.1 frame timing is
 * exposed through the port {@link DeltaTracker}: the partial tick is Forge's {@code renderTickTime} (pause-aware, as
 * vanilla's), the tick deltas are {@code Minecraft#getDeltaFrameTime()}.
 */
final class PortRenderFrameBridge {
    private PortRenderFrameBridge() {}

    static void install(IEventBus forge) {
        forge.addListener(EventPriority.NORMAL, false, TickEvent.RenderTickEvent.class, event -> {
            DeltaTracker delta = new FrameDelta(event.renderTickTime);
            forge.post(event.phase == TickEvent.Phase.START ? new RenderFrameEvent.Pre(delta) : new RenderFrameEvent.Post(delta));
        });
    }

    private record FrameDelta(float gamePartialTick) implements DeltaTracker {
        @Override
        public float getGameTimeDeltaTicks() {
            return Minecraft.getInstance().getDeltaFrameTime();
        }

        @Override
        public float getGameTimeDeltaPartialTick(boolean runsNormally) {
            // 1.20.1 has no tick freezing, so both variants are the pause-aware partial tick.
            return this.gamePartialTick;
        }

        @Override
        public float getRealtimeDeltaTicks() {
            return Minecraft.getInstance().getDeltaFrameTime();
        }
    }
}
