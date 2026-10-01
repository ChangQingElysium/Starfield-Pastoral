package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.event.PortEventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): MinecraftForge 21.1 drops block XP from {@code BlockDropsEvent}; Forge 1.20.1 pops the {@code BreakEvent}
 * XP after {@code playerDestroy}. The BreakEvent XP seeds the drop event and Forge's own pop is skipped when the drop
 * event consumed it.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class PortServerPlayerGameModeXpMixin {
    @Shadow
    protected ServerLevel level;

    @ModifyExpressionValue(method = "destroyBlock", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraftforge/common/ForgeHooks;onBlockBreakEvent(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/GameType;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;)I"))
    private int stardewcraft$rememberBreakXp(int experience, @Local(argsOnly = true) BlockPos pos) {
        return PortEventHooks.beginPlayerBreak(this.level, pos, experience);
    }

    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;popExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;I)V"))
    private void stardewcraft$skipHandledXp(Block block, ServerLevel level, BlockPos pos, int experience, Operation<Void> original) {
        if (!PortEventHooks.playerBreakXpHandled()) original.call(block, level, pos, experience);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void stardewcraft$endBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        PortEventHooks.endPlayerBreak();
    }
}
