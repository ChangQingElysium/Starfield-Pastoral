package com.stardew.craft.item;

import com.stardew.craft.core.ModTags;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.seat.CushionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Places StardewCraft cushions as 26.3-style entities while retaining the legacy block item identity. */
@SuppressWarnings("null")
public final class CushionItem extends StardewBlockItem {
    private static final double COLLISION_SHAPE_RAYCAST_EPSILON = 0.001D;

    public CushionItem(Block block, String itemTypeKey, int sellPrice, Item.Properties properties) {
        super(block, itemTypeKey, sellPrice, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        UseOnContext placementContext = recalculateContextForSpecialCollisionShapes(context);
        if (placementContext.getClickedFace() != Direction.UP) {
            return InteractionResult.FAIL;
        }

        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !player.getAbilities().mayBuild) {
            return InteractionResult.FAIL;
        }
        BlockPlaceContext blockPlaceContext = new BlockPlaceContext(placementContext);
        BlockPos placementCell = blockPlaceContext.getClickedPos();
        if (!level.getWorldBorder().isWithinBounds(placementCell)
                || !level.mayInteract(player, placementCell)) {
            return InteractionResult.FAIL;
        }
        Vec3 entityPos = new Vec3(
                placementCell.getX() + 0.5D,
                placementContext.getClickLocation().y,
                placementCell.getZ() + 0.5D);
        AABB spawnBox = ModEntities.CUSHION.get().getDimensions().makeBoundingBox(entityPos);
        if (!CushionEntity.wouldSurviveAt(level, spawnBox)) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        if (level instanceof ServerLevel serverLevel) {
            if (player instanceof ServerPlayer serverPlayer
                    && !com.stardew.craft.event.FarmAreaProtectionEvents.canModifyDecorationAt(
                            serverPlayer, serverLevel, placementCell)) {
                return InteractionResult.FAIL;
            }
            if (!serverLevel.getEntitiesOfClass(CushionEntity.class, spawnBox).isEmpty()) {
                return InteractionResult.FAIL;
            }

            CushionEntity cushion = ModEntities.CUSHION.get().create(serverLevel);
            if (cushion == null) {
                return InteractionResult.FAIL;
            }

            Direction facing = Direction.fromYRot(blockPlaceContext.getRotation());
            cushion.moveTo(entityPos.x, entityPos.y, entityPos.z, facing.toYRot(), 0.0F);
            Component customName = stack.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                cushion.setCustomName(customName);
            }

            if (!serverLevel.addFreshEntity(cushion)) {
                return InteractionResult.FAIL;
            }
            cushion.destroyIfInFire(serverLevel);
            level.playSound(null, cushion.getX(), cushion.getY(), cushion.getZ(),
                    SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.75F, 0.8F);
            cushion.gameEvent(GameEvent.ENTITY_PLACE);
            stack.consume(1, blockPlaceContext.getPlayer());
        }

        return InteractionResult.SUCCESS;
    }

    private static UseOnContext recalculateContextForSpecialCollisionShapes(UseOnContext context) {
        if (context.getPlayer() == null) {
            return context;
        }

        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        if (!clickedState.is(ModTags.Blocks.CUSHION_USES_COLLISION_SHAPE)) {
            return context;
        }

        Vec3 rayFrom = context.getPlayer().getEyePosition();
        Vec3 ray = context.getClickLocation().subtract(rayFrom);
        if (ray.lengthSqr() == 0.0D) {
            return context;
        }
        Vec3 rayTo = context.getClickLocation().add(ray.normalize().scale(COLLISION_SHAPE_RAYCAST_EPSILON));
        BlockHitResult collisionHit = clickedState.getCollisionShape(level, clickedPos).clip(rayFrom, rayTo, clickedPos);
        return collisionHit == null ? context : new UseOnContext(context.getPlayer(), context.getHand(), collisionHit);
    }
}
