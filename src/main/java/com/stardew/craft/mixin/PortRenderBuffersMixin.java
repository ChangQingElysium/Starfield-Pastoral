package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.stardew.craft.client.render.BubbleItemRenderer;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import java.util.Map;

/** Registers fixed bubble buffers in both the main view and independently allocated portal views. */
@Mixin(RenderBuffers.class)
public abstract class PortRenderBuffersMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource;immediateWithBuffers(Ljava/util/Map;Lcom/mojang/blaze3d/vertex/BufferBuilder;)Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;"), index = 0)
    private Map<RenderType, BufferBuilder> stardewcraft$fixedBubbles(Map<RenderType, BufferBuilder> buffers) {
        // RenderBuffers is also constructed before the mod bus can receive lifecycle events.
        // Invoke this facade's sole consumer directly, retaining the upstream registration API.
        BubbleItemRenderer.registerRenderBuffers(new RegisterRenderBuffersEvent(buffers));
        return buffers;
    }
}
