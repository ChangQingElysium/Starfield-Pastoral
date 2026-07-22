package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.WizardBuildingCatalogBlockEntity;
import com.stardew.craft.shop.WizardBuildingService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** The per-player magic-building catalog revealed by the Magic Ink event. */
@SuppressWarnings("null")
public final class WizardBuildingCatalogBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 19.0D, 13.0D);

    public WizardBuildingCatalogBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
                                                BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            WizardBuildingService.open(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
                                              net.minecraft.world.level.Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            WizardBuildingService.open(serverPlayer);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WizardBuildingCatalogBlockEntity(pos, state);
    }
}
