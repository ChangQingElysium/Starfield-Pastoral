package com.stardew.craft.mixin;

import com.mojang.serialization.Codec;
import com.stardew.craft.port.net.minecraft.core.component.DataComponentType;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.UnaryOperator;

@Mixin(DataComponents.class)
public abstract class DataComponentsMixin {

    @Shadow
    private static <T> DataComponentType<T> register(String name, UnaryOperator<DataComponentType.Builder<T>> builderOp) {
        throw new AssertionError();
    }

    @SuppressWarnings("null")
    @Redirect(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/component/DataComponents;register(Ljava/lang/String;Ljava/util/function/UnaryOperator;)Lnet/minecraft/core/component/DataComponentType;"
            )
    )
    private static <T> DataComponentType<T> redirectRegister(String name, UnaryOperator<DataComponentType.Builder<T>> builderOp) {
        if ("max_stack_size".equals(name)) {
            // Replace the builder operator with one that allows 999 stack size
            UnaryOperator<DataComponentType.Builder<T>> newOp = (builder) -> {
                // We know T is Integer for max_stack_size
                @SuppressWarnings("unchecked")
                DataComponentType.Builder<Integer> intBuilder = (DataComponentType.Builder<Integer>) builder;
                
                // Replicate original logic but with 999 range
                intBuilder.persistent(Codec.intRange(1, 999));
                // Use VAR_INT for network sync, allowing any integer value (effectively removing the 99 limit on network)
                intBuilder.networkSynchronized(ByteBufCodecs.VAR_INT);
                
                @SuppressWarnings("unchecked")
                DataComponentType.Builder<T> result = (DataComponentType.Builder<T>) intBuilder;
                return result;
            };
            return register(name, newOp);
        }
        // For all other components, use the original operator
        return register(name, builderOp);
    }
}
