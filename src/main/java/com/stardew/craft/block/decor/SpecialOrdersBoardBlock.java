package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class SpecialOrdersBoardBlock extends MapDecorStaticBlock {

    public SpecialOrdersBoardBlock(Properties properties, String modelId) {
        super(properties, modelId, -15, 0, 5, 31, 32, 13);
    }

    protected SpecialOrdersBoardBlock(Properties properties, String modelId,
                                      double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ) {
        super(properties, modelId, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state,
                                               @Nonnull Level level,
                                               @Nonnull BlockPos pos,
                                               @Nonnull Player player,
                                               @Nonnull BlockHitResult hit) {
        BlockPos mainPos = findMainPos(level, pos, state);
        if (mainPos == null) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer
            && !com.stardew.craft.specialorder.SpecialOrderManager.isUnlockedFor(serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.stardew.craft.specialorder.SpecialOrderManager.openBoard(serverPlayer);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        if (!isUnlockedForContext(context)) {
            return Shapes.empty();
        }
        return super.getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        if (!isUnlockedForContext(context)) {
            return Shapes.empty();
        }
        return super.getCollisionShape(state, level, pos, context);
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        if (FMLEnvironment.dist == Dist.CLIENT
            && !com.stardew.craft.client.specialorder.ClientSpecialOrderUnlockState.isUnlocked()) {
            return RenderShape.INVISIBLE;
        }
        return RenderShape.MODEL;
    }

    private static boolean isUnlockedForContext(CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext) {
            Entity entity = entityContext.getEntity();
            if (entity instanceof ServerPlayer serverPlayer) {
                return com.stardew.craft.specialorder.SpecialOrderManager.isUnlockedFor(serverPlayer);
            }
            if (entity instanceof Player) {
                return FMLEnvironment.dist == Dist.CLIENT
                    && com.stardew.craft.client.specialorder.ClientSpecialOrderUnlockState.isUnlocked();
            }
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return com.stardew.craft.client.specialorder.ClientSpecialOrderUnlockState.isUnlocked();
        }
        return com.stardew.craft.specialorder.SpecialOrderManager.isUnlocked();
    }
}
