package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.stardew.craft.platform.fabric.event.Event;
import java.util.Objects;
import java.util.SequencedMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderType;

@Environment(EnvType.CLIENT)
public final class RegisterRenderBuffersEvent extends Event implements ModBusEvent {
    private final SequencedMap<RenderType, ByteBufferBuilder> buffers;
    public RegisterRenderBuffersEvent(SequencedMap<RenderType, ByteBufferBuilder> buffers) { this.buffers = Objects.requireNonNull(buffers); }
    public void registerRenderBuffer(RenderType type) {
        checkNew(type); registerRenderBuffer(type, new ByteBufferBuilder(type.bufferSize()));
    }
    public void registerRenderBuffer(RenderType type, ByteBufferBuilder buffer) { checkNew(type); buffers.put(type, Objects.requireNonNull(buffer)); }
    private void checkNew(RenderType type) {
        Objects.requireNonNull(type, "type");
        if (buffers.containsKey(type)) throw new IllegalStateException("Duplicate render buffer: " + type);
    }
}
