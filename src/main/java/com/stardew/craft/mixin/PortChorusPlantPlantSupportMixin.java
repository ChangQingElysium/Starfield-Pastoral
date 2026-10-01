package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.PortPlantSupport;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PORT(1.20.1): NeoForge 1.21.1 soil TriState hook in {@code ChorusPlantBlock} connections and survival. */
@Mixin(ChorusPlantBlock.class)
public abstract class PortChorusPlantPlantSupportMixin {
    /** 1.21.1 {@code getStateWithConnections}: DOWN also connects to a soil that answers TRUE. */
    @WrapOperation(method = "getStateForPlacement(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean stardewcraft$port121PlacementSoil(BlockState soil, TagKey<Block> tag, Operation<Boolean> original,
                                                      @Local(argsOnly = true) BlockGetter level, @Local(argsOnly = true) BlockPos pos) {
        return original.call(soil, tag) || PortPlantSupport.decision(soil, level, pos.below(), Direction.UP,
                ((Block) (Object) this).defaultBlockState()).isTrue();
    }

    /** 1.21.1 {@code updateShape}: a non-DEFAULT soil below sets the DOWN connection (NeoForge passes facingPos.below()). */
    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121UpdateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level,
                                                 BlockPos currentPos, BlockPos facingPos, CallbackInfoReturnable<BlockState> cir) {
        if (facing != Direction.DOWN) return;
        TriState decision = PortPlantSupport.decision(facingState, level, facingPos.relative(facing), facing.getOpposite(), state);
        if (!decision.isDefault() && state.canSurvive(level, currentPos))
            cir.setReturnValue(state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(facing), decision.isTrue()));
    }

    /** 1.21.1 {@code canSurvive}: neighbour checks unchanged, then a non-DEFAULT soil decides. */
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$port121CanSurvive(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState below = level.getBlockState(pos.below());
        TriState decision = PortPlantSupport.decision(below, level, pos.below(), Direction.UP, state);
        if (decision.isDefault()) return;
        boolean vertical = !level.getBlockState(pos.above()).isAir() && !below.isAir();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            if (level.getBlockState(side).is((Block) (Object) this)) {
                if (vertical) {
                    cir.setReturnValue(false);
                    return;
                }
                BlockState sideBelow = level.getBlockState(side.below());
                if (sideBelow.is((Block) (Object) this) || sideBelow.is(Blocks.END_STONE)
                        || sideBelow.is(Tags.Blocks.CHORUS_ADDITIONALLY_GROWS_ON)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
        cir.setReturnValue(decision.isTrue());
    }
}
