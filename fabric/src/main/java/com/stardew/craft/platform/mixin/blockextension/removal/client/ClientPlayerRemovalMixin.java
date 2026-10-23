package com.stardew.craft.platform.mixin.blockextension.removal.client;


import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.blockextension.removal.PlayerBlockRemoval;
import com.stardew.craft.platform.fabric.blockextension.removal.PlayerDestroyProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
abstract class ClientPlayerRemovalMixin {
    @Shadow @Final private Minecraft minecraft;
    @Inject(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;"), cancellable = true, require = 1)
    private void stardewcraft$predictOwnedRemoval(BlockPos pos, CallbackInfoReturnable<Boolean> result, @Local BlockState state) {
        if (state.getBlock() instanceof PlayerDestroyProvider)
            result.setReturnValue(PlayerBlockRemoval.destroyClient(state, minecraft.level, pos, minecraft.player));
    }
}
