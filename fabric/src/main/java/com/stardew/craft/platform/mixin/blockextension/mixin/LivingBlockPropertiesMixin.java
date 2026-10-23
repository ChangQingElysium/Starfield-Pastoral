package com.stardew.craft.platform.mixin.blockextension.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.blockextension.BlockBehaviors;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class LivingBlockPropertiesMixin {
    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"), require = 1)
    private float stardewcraft$groundFriction(Block block, Operation<Float> original) {
        var entity = (LivingEntity) (Object) this;
        return BlockBehaviors.resolveFriction(block, entity, entity.getBlockPosBelowThatAffectsMyMovement(), original.call(block));
    }

    @WrapOperation(method = "onClimbable", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"), require = 1)
    private boolean stardewcraft$contextualClimbing(BlockState state, TagKey<Block> tag, Operation<Boolean> original) {
        var entity = (LivingEntity) (Object) this;
        return tag.equals(BlockTags.CLIMBABLE) && state.getBlock() instanceof com.stardew.craft.platform.fabric.blockextension.BlockBehaviorProvider
                ? BlockBehaviors.isLadder(state, entity.level(), entity.blockPosition(), entity) : original.call(state, tag);
    }

    @WrapOperation(method = "trapdoorUsableAsLadder", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"), require = 1)
    private boolean stardewcraft$ladderSubclassesBelowTrapdoor(BlockState state, Block queried, Operation<Boolean> original) {
        return queried == Blocks.LADDER ? state.getBlock() instanceof LadderBlock : original.call(state, queried);
    }

    @WrapOperation(method = "playBlockFallSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"), require = 1)
    private SoundType stardewcraft$materialFallSound(BlockState state, Operation<SoundType> original) {
        var entity = (LivingEntity) (Object) this;
        var pos = BlockPos.containing(entity.getX(), entity.getY() - 0.2F, entity.getZ());
        return BlockBehaviors.resolveSound(state, entity.level(), pos, entity, original.call(state));
    }
}
