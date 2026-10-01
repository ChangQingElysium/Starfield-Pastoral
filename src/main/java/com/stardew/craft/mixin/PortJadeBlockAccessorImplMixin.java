package com.stardew.craft.mixin;

import com.stardew.craft.integration.jade.JadeBlockDataProviders;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** PORT(1.20.1): serve Jade 15-style block-keyed server data providers (see {@link JadeBlockDataProviders}). */
@Pseudo
@Mixin(targets = "snownee.jade.impl.BlockAccessorImpl", remap = false)
public abstract class PortJadeBlockAccessorImplMixin {
    @Inject(method = "handleRequest(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private static void stardewcraft$handleMergedRequest(FriendlyByteBuf buf, ServerPlayer player,
                                                       Consumer<Runnable> executor,
                                                       Consumer<CompoundTag> responseSender,
                                                       CallbackInfo callback) {
        JadeBlockDataProviders.handleRequest(buf, player, executor, responseSender);
        callback.cancel();
    }
}
