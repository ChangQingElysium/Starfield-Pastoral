package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.UncertaintyStatueBlockEntity;
import com.stardew.craft.statue.UncertaintyStatueService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("null")
public class UncertaintyStatueBlock extends MapDecorStaticBlock implements EntityBlock {
    public UncertaintyStatueBlock(Properties properties) {
        super(properties, "stardewcraft:block/decor/uncertainty_statue");
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape canonicalShape() {
        // Match the blockstate's clockwise quarter-turn, including its extension footprint.
        return com.stardew.craft.block.shape.ModelVoxelShapeCache.rotateY(super.canonicalShape(), 1);
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean isPathfindable(@Nonnull BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, @Nonnull PathComputationType type) {
        return false;
    }

    @Override
    public InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                                               @Nonnull Player player, @Nonnull BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            UncertaintyStatueService.open(serverPlayer, level, pos, state);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemInteractionResult useItemOn(@Nonnull ItemStack stack, @Nonnull BlockState state, @Nonnull Level level,
                                             @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull InteractionHand hand,
                                             @Nonnull BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            UncertaintyStatueService.open(serverPlayer, level, pos, state);
        }
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        if (state.getValue(PART) != Part.MAIN) {
            return null;
        }
        return new UncertaintyStatueBlockEntity(pos, state);
    }

    @Override
    public List<ItemStack> getDrops(@Nonnull BlockState state, @Nonnull LootParams.Builder params) {
        return List.of();
    }
}
