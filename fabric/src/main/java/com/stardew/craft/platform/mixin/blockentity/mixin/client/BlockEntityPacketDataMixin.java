package com.stardew.craft.platform.mixin.blockentity.mixin.client;


import com.stardew.craft.platform.fabric.blockentity.BlockEntityDataProvider;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class BlockEntityPacketDataMixin {
    @Dynamic("Native 1.21.1 handleBlockEntityData consumer; bytecode contract checked")
    @Inject(method = "method_38542", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$updatePacket(ClientboundBlockEntityDataPacket packet, BlockEntity entity, CallbackInfo ci) {
        // Providers are this mod's BE classes. All native BEs, including the
        // command-block edit-screen refresh, continue through the original body.
        if (entity instanceof BlockEntityDataProvider provider) {
            var listener = (ClientPacketListener) (Object) this;
            provider.onDataPacket(listener.getConnection(), packet, listener.registryAccess());
            ci.cancel();
        }
    }
}
