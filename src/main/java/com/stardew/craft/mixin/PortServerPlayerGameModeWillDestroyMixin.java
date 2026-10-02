package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.PortInheritance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): NeoForge 1.21.1 {@code ServerPlayerGameMode#destroyBlock} calls {@code block.playerWillDestroy} right
 * after the game-master/{@code blockActionRestricted} checks, before the creative branch, {@code canHarvestBlock} and
 * {@code mineBlock}, and then hands the state it captured before that call to {@code removeBlock}
 * ({@code onDestroyedByPlayer}). Forge 1.20.1 calls it from inside the default {@code onDestroyedByPlayer} and
 * {@code removeBlock} re-reads the world. For StardewCraft blocks the call is made here, at the 1.21.1 position, and
 * the default no longer makes it ({@code PortBlockDestroyedByPlayerMixin}); other blocks are unchanged.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class PortServerPlayerGameModeWillDestroyMixin {
    @Shadow
    protected ServerLevel level;

    @Shadow
    @Final
    protected ServerPlayer player;

    @Unique
    private BlockPos stardewcraft$willDestroyPos;

    @Unique
    private BlockState stardewcraft$willDestroyState;

    @Inject(method = "destroyBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayerGameMode;isCreative()Z"))
    private void stardewcraft$playerWillDestroyAt121Position(BlockPos pos, CallbackInfoReturnable<Boolean> cir,
            @Local BlockState state) {
        if (!PortInheritance.isModBlock(state.getBlock())) return;
        state.getBlock().playerWillDestroy(this.level, pos, state, this.player);
        this.stardewcraft$willDestroyPos = pos.immutable();
        this.stardewcraft$willDestroyState = state;
    }

    @ModifyExpressionValue(method = "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z", remap = false,
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState stardewcraft$removeCapturedState(BlockState current, @Local(argsOnly = true) BlockPos pos) {
        return this.stardewcraft$willDestroyState != null && pos.equals(this.stardewcraft$willDestroyPos)
                ? this.stardewcraft$willDestroyState : current;
    }

    @WrapMethod(method = "destroyBlock")
    private boolean stardewcraft$restoreCapturedState(BlockPos pos, Operation<Boolean> original) {
        BlockPos previousPos = this.stardewcraft$willDestroyPos;
        BlockState previousState = this.stardewcraft$willDestroyState;
        this.stardewcraft$willDestroyPos = null;
        this.stardewcraft$willDestroyState = null;
        try {
            return original.call(pos);
        } finally {
            this.stardewcraft$willDestroyPos = previousPos;
            this.stardewcraft$willDestroyState = previousState;
        }
    }
}
