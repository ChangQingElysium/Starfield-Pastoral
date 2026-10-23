package com.stardew.craft.platform.mixin.blockextension.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.blockextension.BlockBehaviors;
import com.stardew.craft.platform.fabric.blockextension.StepSoundContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
abstract class EntitySoundMixin {
    @WrapOperation(method = "playStepSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"), require = 1)
    private SoundType stardewcraft$stepMaterial(BlockState queried, Operation<SoundType> original, BlockPos pos, BlockState state) {
        var entity = (Entity) (Object) this;
        return BlockBehaviors.resolveSound(queried, entity.level(), pos, entity, original.call(queried));
    }

    @WrapOperation(method = "playCombinationStepSounds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"), require = 1)
    private SoundType stardewcraft$primaryMaterial(BlockState queried, Operation<SoundType> original) {
        var entity = (Entity) (Object) this; var pos = StepSoundContext.primary(entity);
        return pos == null ? original.call(queried) : BlockBehaviors.resolveSound(queried, entity.level(), pos, entity, original.call(queried));
    }

    @WrapOperation(method = "playMuffledStepSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"), require = 1)
    private SoundType stardewcraft$secondaryMaterial(BlockState queried, Operation<SoundType> original) {
        var entity = (Entity) (Object) this; var pos = StepSoundContext.secondary(entity);
        return pos == null ? original.call(queried) : BlockBehaviors.resolveSound(queried, entity.level(), pos, entity, original.call(queried));
    }
}
