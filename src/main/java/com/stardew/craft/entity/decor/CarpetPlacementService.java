package com.stardew.craft.entity.decor;

import com.stardew.craft.block.decor.CarpetDecorBlock;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

/** Shared placement and old-block migration for entity-backed carpets. */
@SuppressWarnings("null")
public final class CarpetPlacementService {
    private CarpetPlacementService() {}

    public static InteractionResult place(CarpetDecorBlock carpet, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (context.getClickedFace() != Direction.UP || player == null || !player.getAbilities().mayBuild) {
            return InteractionResult.FAIL;
        }

        BlockPos supportPos = context.getClickedPos();
        BlockState support = level.getBlockState(supportPos);
        if (!support.isFaceSturdy(level, supportPos, Direction.UP)) {
            return InteractionResult.FAIL;
        }

        BlockPos anchorPos = supportPos.above();
        if (!level.getWorldBorder().isWithinBounds(anchorPos)) {
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel
                && !com.stardew.craft.event.FarmAreaProtectionEvents.canModifyDecorationAt(
                        serverPlayer, serverLevel, anchorPos)) {
            return InteractionResult.FAIL;
        }
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockState carpetState = carpet.defaultBlockState()
                .setValue(CarpetDecorBlock.PART, CarpetDecorBlock.Part.MAIN)
                .setValue(CarpetDecorBlock.FACING, facing);

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        CarpetEntity entity = ModEntities.CARPET.get().create(level);
        if (entity == null) return InteractionResult.FAIL;
        entity.setPos(anchorPos.getX() + 0.5D, anchorPos.getY(), anchorPos.getZ() + 0.5D);
        entity.setCarpetState(carpetState);
        entity.refreshInteractionBounds();
        AABB overlapCheck = entity.getBoundingBox().deflate(0.02D, 0.0D, 0.02D);
        if (!level.getEntitiesOfClass(CarpetEntity.class, overlapCheck).isEmpty()) {
            return InteractionResult.FAIL;
        }

        if (!level.addFreshEntity(entity)) return InteractionResult.FAIL;
        var soundType = carpetState.getSoundType(level, anchorPos, player);
        level.playSound(null, anchorPos, soundType.getPlaceSound(), SoundSource.BLOCKS,
                (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, anchorPos);
        context.getItemInHand().consume(1, player);
        return InteractionResult.CONSUME;
    }

    public static void migratePlacedBlock(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide || !(state.getBlock() instanceof CarpetDecorBlock)) return;
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel
                && !com.stardew.craft.event.FarmAreaProtectionEvents.canModifyDecorationAt(
                        serverPlayer, serverLevel, pos)) {
            return;
        }
        CarpetEntity entity = ModEntities.CARPET.get().create(level);
        if (entity == null) return;
        entity.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        entity.setCarpetState(state);
        entity.refreshInteractionBounds();
        if (!level.getEntitiesOfClass(CarpetEntity.class,
                entity.getBoundingBox().deflate(0.02D, 0.0D, 0.02D)).isEmpty()) {
            return;
        }
        level.removeBlock(pos, false);
        level.addFreshEntity(entity);
    }
}
