package com.stardew.craft.platform.mixin.blockextension.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.blockextension.StepSoundContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class PlayerStepContextMixin {
    @WrapOperation(method = "playStepSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;playCombinationStepSounds(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V"), require = 1)
    private void stardewcraft$preserveBothStepPositions(Player player, BlockState primary, BlockState secondary,
            Operation<Void> original, BlockPos pos, BlockState state, @Local(ordinal = 1) BlockPos primaryPos) {
        StepSoundContext.run(player, primaryPos, pos, () -> original.call(player, primary, secondary));
    }

    @WrapOperation(method = "playStepSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;playMuffledStepSound(Lnet/minecraft/world/level/block/state/BlockState;)V"), require = 1)
    private void stardewcraft$preserveUnderwaterStepPosition(Player player, BlockState queried, Operation<Void> original,
            BlockPos pos, BlockState state) {
        StepSoundContext.run(player, pos, pos, () -> original.call(player, queried));
    }
}
