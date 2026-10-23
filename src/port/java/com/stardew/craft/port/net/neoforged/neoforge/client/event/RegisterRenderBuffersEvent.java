package com.stardew.craft.port.net.neoforged.neoforge.client.event;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;
import java.util.Map;

/** PORT(1.20.1): registration facade invoked for each RenderBuffers by PortRenderBuffersMixin. */
public final class RegisterRenderBuffersEvent extends Event implements IModBusEvent {
    private final Map<RenderType, BufferBuilder> buffers;

    public RegisterRenderBuffersEvent(Map<RenderType, BufferBuilder> buffers) {
        this.buffers = buffers;
    }

    public void registerRenderBuffer(RenderType type) {
        buffers.computeIfAbsent(type, key -> new BufferBuilder(key.bufferSize()));
    }
}
