package com.stardew.craft.platform.mixin.client.event.registration.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.stardew.craft.platform.fabric.client.event.registration.FabricRenderRegistrations;
import java.util.SequencedMap;
import java.util.function.Consumer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderBuffers.class)
public abstract class RenderBuffersRegistrationMixin {
    @SuppressWarnings("unchecked")
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;make(Ljava/lang/Object;Ljava/util/function/Consumer;)Ljava/lang/Object;"))
    private static Object stardewcraft$buffers(Object map, Consumer<Object> defaults, Operation<Object> original) {
        Object result = original.call(map, defaults);
        FabricRenderRegistrations.buffers((SequencedMap<RenderType, ByteBufferBuilder>) result); return result;
    }
}
