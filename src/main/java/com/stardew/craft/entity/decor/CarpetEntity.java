package com.stardew.craft.entity.decor;

import com.stardew.craft.block.decor.CarpetDecorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Entity-backed rug. Like the 26.3 cushion, it occupies no block cell, so furniture can
 * live in the same grid cells while the rug remains visible underneath.
 */
@SuppressWarnings("null")
public final class CarpetEntity extends Entity {
    private static final EntityDataAccessor<BlockState> DATA_CARPET_STATE =
            SynchedEntityData.defineId(CarpetEntity.class, EntityDataSerializers.BLOCK_STATE);

    public CarpetEntity(EntityType<? extends CarpetEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CARPET_STATE, Blocks.AIR.defaultBlockState());
    }

    public BlockState getCarpetState() {
        return entityData.get(DATA_CARPET_STATE);
    }

    public void setCarpetState(BlockState state) {
        if (!(state.getBlock() instanceof CarpetDecorBlock)) return;
        entityData.set(DATA_CARPET_STATE, state
                .setValue(CarpetDecorBlock.PART, CarpetDecorBlock.Part.MAIN));
        refreshInteractionBounds();
    }

    @Override
    public Component getName() {
        BlockState state = getCarpetState();
        return state.getBlock() instanceof CarpetDecorBlock
                ? state.getBlock().getName()
                : super.getName();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_CARPET_STATE.equals(key)) {
            refreshInteractionBounds();
        }
    }

    public void refreshInteractionBounds() {
        BlockState state = getCarpetState();
        if (!(state.getBlock() instanceof CarpetDecorBlock)) {
            setBoundingBox(AABB.ofSize(position(), 0.9D, 0.1D, 0.9D));
            return;
        }
        VoxelShape shape = state.getShape(level(), blockPosition(), CollisionContext.empty());
        if (shape.isEmpty()) {
            setBoundingBox(AABB.ofSize(position(), 0.9D, 0.1D, 0.9D));
            return;
        }
        AABB local = shape.bounds();
        // Entity origin is the anchor cell centre; block models use the cell's lower NW corner.
        setBoundingBox(new AABB(
                getX() - 0.5D + local.minX, getY() + local.minY, getZ() - 0.5D + local.minZ,
                getX() - 0.5D + local.maxX, getY() + Math.max(0.08D, local.maxY), getZ() - 0.5D + local.maxZ));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setCarpetState(NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK), tag.getCompound("CarpetState")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("CarpetState", NbtUtils.writeBlockState(getCarpetState()));
    }

    @Override
    public void tick() {
        if (!level().isClientSide && tickCount % 20 == 0) {
            BlockPos supportPos = BlockPos.containing(getX(), getY() - 0.01D, getZ());
            BlockState support = level().getBlockState(supportPos);
            if (support.isAir() || !support.isFaceSturdy(level(), supportPos, Direction.UP)) {
                breakAndDrop(null);
            }
        }
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }
    @Override public boolean isIgnoringBlockTriggers() { return true; }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (attacker instanceof Player player) {
            if (!level().isClientSide && player.getAbilities().mayBuild) {
                breakAndDrop(player);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) {
            breakAndDrop(source.getEntity() instanceof Player player ? player : null);
        }
        return true;
    }

    /**
     * A carpet is the ray-trace target, so forward block-item use to the floor cell under the
     * exact clicked point. This is what lets chairs, tables and every cell of a bed be placed
     * at normal floor height instead of one block above the rug.
     */
    @Override
    public InteractionResult interactAt(Player player, Vec3 localHit, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof BlockItem)) return InteractionResult.PASS;

        Vec3 worldHit = position().add(localHit);
        BlockPos supportPos = BlockPos.containing(worldHit.x, getY() - 0.01D, worldHit.z);
        BlockHitResult floorHit = new BlockHitResult(
                new Vec3(worldHit.x, getY(), worldHit.z), Direction.UP, supportPos, false);
        return held.useOn(new UseOnContext(player, hand, floorHit));
    }

    private void breakAndDrop(Player player) {
        if (isRemoved()) return;
        if (player instanceof ServerPlayer serverPlayer
                && level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                && !com.stardew.craft.event.FarmAreaProtectionEvents.canModifyDecorationAt(
                        serverPlayer, serverLevel, blockPosition())) {
            return;
        }
        if (player == null || !player.getAbilities().instabuild) {
            ItemStack drop = new ItemStack(getCarpetState().getBlock());
            if (!drop.isEmpty()) spawnAtLocation(drop);
        }
        gameEvent(GameEvent.ENTITY_DIE, player);
        discard();
    }
}
