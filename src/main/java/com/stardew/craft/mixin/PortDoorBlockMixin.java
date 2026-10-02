package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.port.PortBlockInteraction;
import com.stardew.craft.port.PortInheritance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): 1.21.1 {@code DoorBlock} differs from 1.20.1 in two inherited methods; StardewCraft doors
 * ({@code PaintedDoorBlock}, {@code ShopGlassDoorBlock}, {@code FriendshipDoorBlock}, {@code VanillaTemplateDoorBlock})
 * get the 1.21.1 bodies, vanilla and other mods' doors are unchanged.
 * <ul>
 * <li>{@code updateShape}, other half above/below: 1.21 keeps the half when the neighbour is any {@code DoorBlock}
 * of the other half and becomes that neighbour's state with this half ({@code neighbour.setValue(HALF, half)});
 * 1.20.1 requires the same block and copies FACING/OPEN/HINGE/POWERED onto its own state.</li>
 * <li>{@code playerWillDestroy}: 1.21 also clears the lower half without drops when a survival player lacks the
 * correct tool ({@code preventDropFromBottomPart}); 1.20.1 only does so in creative.</li>
 * </ul>
 */
@Mixin(DoorBlock.class)
public abstract class PortDoorBlockMixin {
    /** Inherited DoorBlock interaction moved to useWithoutItem in 1.21, including its main-hand-only gate. */
    @WrapMethod(method = "use")
    private InteractionResult stardewcraft$defaultDoorUse(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {
        if (!PortInheritance.isModBlock((Block) (Object) this)) {
            return original.call(state, level, pos, player, hand, hit);
        }
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        InteractionResult result = original.call(state, level, pos, player, hand, hit);
        if (result.consumesAction()) PortBlockInteraction.DEFAULT_BLOCK_USE.set(Boolean.TRUE);
        return result;
    }

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$updateShape121(BlockState state, Direction facing, BlockState facingState, LevelAccessor level,
                                             BlockPos currentPos, BlockPos facingPos, CallbackInfoReturnable<BlockState> cir) {
        if (!PortInheritance.isModBlock((Block) (Object) this)) return;
        DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
        if (facing.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (facing == Direction.UP)) {
            cir.setReturnValue(facingState.getBlock() instanceof DoorBlock && facingState.getValue(DoorBlock.HALF) != half
                    ? facingState.setValue(DoorBlock.HALF, half)
                    : Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(method = "playerWillDestroy", at = @At("HEAD"))
    private void stardewcraft$preventDropWithoutTool(Level level, BlockPos pos, BlockState state, Player player, CallbackInfo ci) {
        if (level.isClientSide || player.isCreative() || !PortInheritance.isModBlock((Block) (Object) this)) return;
        if (player.hasCorrectToolForDrops(state)) return;
        // 1.21 DoublePlantBlock#preventDropFromBottomPart (same body as 1.20.1 preventCreativeDropFromBottomPart).
        if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER) return;
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.is(state.getBlock()) && belowState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockState replacement = belowState.getFluidState().is(Fluids.WATER)
                    ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            level.setBlock(below, replacement, 35);
            level.levelEvent(player, 2001, below, Block.getId(belowState));
        }
    }
}
