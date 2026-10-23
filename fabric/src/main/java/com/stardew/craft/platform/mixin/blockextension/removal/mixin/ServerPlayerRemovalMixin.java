package com.stardew.craft.platform.mixin.blockextension.removal.mixin;


import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.blockextension.removal.PlayerBlockRemoval;
import com.stardew.craft.platform.fabric.blockextension.removal.PlayerDestroyProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerRemovalMixin {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;
    @Shadow public abstract boolean isCreative();
    @Inject(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"), cancellable = true, require = 1)
    private void stardewcraft$ownedRemovalOrder(BlockPos pos, CallbackInfoReturnable<Boolean> result,
            @Local(ordinal = 0) BlockState returnedState, @Local Block originalBlock, @Local BlockEntity entity) {
        if (returnedState.getBlock() instanceof PlayerDestroyProvider)
            result.setReturnValue(PlayerBlockRemoval.finishServer(level, player, pos, returnedState, originalBlock, entity, isCreative()));
    }
}
