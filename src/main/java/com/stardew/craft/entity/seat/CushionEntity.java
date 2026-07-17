package com.stardew.craft.entity.seat;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.CushionBlock;
import com.stardew.craft.block.utility.MapUtilityStaticBlock;
import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.event.FarmAreaProtectionEvents;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.OpenSofaColorScreenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Entity-backed cushion following the vanilla 26.3 placement and support rules.
 * Its palette and rendered model intentionally remain StardewCraft-specific.
 */
@SuppressWarnings("null")
public final class CushionEntity extends Entity {
    private static final String TAG_COLOR = "Color";
    private static final int SUPPORT_CHECK_INTERVAL = 100;
    private static final double SUPPORT_SLICE_HEIGHT = 1.0D / 64.0D;
    private static final double SUPPORT_SEARCH_DEPTH = 1.0D / 8.0D;

    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(CushionEntity.class, EntityDataSerializers.INT);

    private int ticksSinceSupportCheck;

    public CushionEntity(EntityType<? extends CushionEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR, WoodenChestColorPalette.defaultColorIndex());
    }

    public int getColor() {
        return entityData.get(DATA_COLOR);
    }

    public void setColor(int color) {
        int clamped = WoodenChestColorPalette.clampIndex(color);
        entityData.set(DATA_COLOR, clamped < 0 ? WoodenChestColorPalette.defaultColorIndex() : clamped);
    }

    public Direction getFacing() {
        return Direction.fromYRot(getYRot());
    }

    public BlockState getRenderState() {
        return ModBlocks.CUSHION.get().defaultBlockState()
                .setValue(MapUtilityStaticBlock.PART, MapUtilityStaticBlock.Part.MAIN)
                .setValue(MapUtilityStaticBlock.FACING, getFacing())
                .setValue(CushionBlock.COLOR, getColor());
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        checkBelowWorld();
        if (isRemoved()) {
            return;
        }

        if (ticksSinceSupportCheck++ >= SUPPORT_CHECK_INTERVAL) {
            ticksSinceSupportCheck = 0;
            destroyIfInFire(serverLevel);
            if (!isRemoved() && !survives()) {
                breakAndDrop(null);
            }
        }
    }

    @Override
    public boolean dampensVibrations() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.DESTROY;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !isVehicle();
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return new Vec3(getX(), getY() + getBbHeight(), getZ());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).is(ModItems.PAINTBRUSH.get())) {
            if (level().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer,
                        new OpenSofaColorScreenPayload(blockPosition(), getColor(), getId()));
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }
        return trySit(player);
    }

    public InteractionResult trySit(Player player) {
        if (player.isSecondaryUseActive() || isVehicle()) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!player.startRiding(this)) {
            return InteractionResult.PASS;
        }
        playSound(SoundEvents.WOOL_STEP, 1.0F, 1.0F);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && getRemovalReason() == null) {
            playSound(SoundEvents.WOOL_STEP, 1.0F, 1.0F);
        }
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (!(attacker instanceof Player player)) {
            return false;
        }
        if (!level().isClientSide && canPlayerBreak(player)) {
            breakAndDrop(player);
        }
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) {
            return false;
        }
        if (!level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                && source.getEntity() instanceof Mob) {
            return false;
        }
        if (!level().isClientSide && !isRemoved()) {
            breakAndDrop(source.getEntity());
        }
        return true;
    }

    @Override
    public void move(MoverType moverType, Vec3 delta) {
        if (!level().isClientSide && !isRemoved() && delta.lengthSqr() > 0.0D) {
            breakAndDrop(null);
        }
    }

    @Override
    public void push(double x, double y, double z) {
        if (!level().isClientSide && !isRemoved() && x * x + y * y + z * z > 0.0D) {
            breakAndDrop(null);
        }
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        if (!isRemoved()) {
            breakAndDrop(lightning);
        }
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        Entity directSource = explosion.getDirectSourceEntity();
        if (directSource != null && directSource.isInWater()) {
            return true;
        }
        return !explosion.interactsWithBlocks() || super.ignoreExplosion(explosion);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.CUSHION.get());
    }

    @Override
    public Component getName() {
        return hasCustomName() ? getCustomName() : ModItems.CUSHION.get().getDescription();
    }

    public boolean survives() {
        AABB box = getBoundingBox();
        if (!wouldSurviveAt(level(), box)) {
            return false;
        }
        for (BlockPos pos : positionsIn(box.deflate(1.0E-7D))) {
            if (!level().getBlockState(pos).isCollisionShapeFullBlock(level(), pos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean wouldSurviveAt(Level level, AABB boundingBox) {
        AABB anchorBox = new AABB(
                boundingBox.minX,
                boundingBox.minY - SUPPORT_SLICE_HEIGHT,
                boundingBox.minZ,
                Math.nextDown(boundingBox.maxX),
                boundingBox.minY,
                Math.nextDown(boundingBox.maxZ));
        AABB searchBox = new AABB(
                anchorBox.minX,
                anchorBox.minY - SUPPORT_SEARCH_DEPTH,
                anchorBox.minZ,
                anchorBox.maxX,
                anchorBox.maxY,
                anchorBox.maxZ);

        for (BlockPos pos : positionsIn(searchBox)) {
            VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
            if (!shape.isEmpty() && shape.bounds().move(pos).intersects(anchorBox)) {
                return true;
            }
        }
        return false;
    }

    public void destroyIfInFire(ServerLevel level) {
        if (isRemoved()) {
            return;
        }
        for (BlockPos pos : positionsIn(getBoundingBox().deflate(1.0E-7D))) {
            if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE)) {
                breakAndDrop(null);
                return;
            }
        }
    }

    @Nullable
    public static CushionEntity findAt(Level level, BlockPos targetPos) {
        AABB query = new AABB(targetPos).inflate(0.01D);
        return level.getEntitiesOfClass(CushionEntity.class, query,
                        cushion -> cushion.blockPosition().equals(targetPos))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static Iterable<BlockPos> positionsIn(AABB box) {
        return BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(Math.nextDown(box.maxX), Math.nextDown(box.maxY), Math.nextDown(box.maxZ)));
    }

    @Nullable
    public static CushionEntity migrateLegacyBlock(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        if (!canPlayerModifyAt(player, level, pos)) {
            return null;
        }
        CushionEntity existing = findAt(level, pos);
        if (existing != null) {
            level.removeBlock(pos, false);
            return existing;
        }

        CushionEntity cushion = ModEntities.CUSHION.get().create(level);
        if (cushion == null) {
            return null;
        }
        cushion.setColor(state.hasProperty(CushionBlock.COLOR)
                ? state.getValue(CushionBlock.COLOR)
                : WoodenChestColorPalette.defaultColorIndex());
        Direction facing = state.hasProperty(MapUtilityStaticBlock.FACING)
                ? state.getValue(MapUtilityStaticBlock.FACING)
                : Direction.NORTH;
        cushion.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, facing.toYRot(), 0.0F);

        level.removeBlock(pos, false);
        if (!level.addFreshEntity(cushion)) {
            level.setBlock(pos, state, 3);
            return null;
        }
        cushion.destroyIfInFire(level);
        if (!cushion.isRemoved() && !cushion.survives()) {
            cushion.breakAndDrop(null);
            return null;
        }
        return cushion;
    }

    private boolean canPlayerBreak(Player player) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        return canPlayerModifyAt(player, serverLevel, blockPosition());
    }

    private static boolean canPlayerModifyAt(Player player, ServerLevel level, BlockPos pos) {
        if (!player.getAbilities().mayBuild || !level.mayInteract(player, pos)) {
            return false;
        }
        return !(player instanceof ServerPlayer serverPlayer)
                || FarmAreaProtectionEvents.canModifyDecorationAt(serverPlayer, level, pos);
    }

    private void breakAndDrop(@Nullable Entity causedBy) {
        if (isRemoved() || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        playSound(SoundEvents.WOOL_BREAK, 1.0F, 1.0F);
        serverLevel.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, getRenderState()),
                getX(), getY(2.0D / 3.0D), getZ(),
                10,
                getBbWidth() / 4.0D,
                getBbHeight() / 4.0D,
                getBbWidth() / 4.0D,
                0.05D);

        if (serverLevel.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)
                && (!(causedBy instanceof Player player) || !player.getAbilities().instabuild)) {
            ItemStack drop = new ItemStack(ModItems.CUSHION.get());
            Component customName = getCustomName();
            if (customName != null) {
                drop.set(DataComponents.CUSTOM_NAME, customName);
            }
            spawnAtLocation(drop);
        }

        gameEvent(GameEvent.ENTITY_DIE, causedBy);
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setColor(tag.contains(TAG_COLOR) ? tag.getInt(TAG_COLOR) : WoodenChestColorPalette.defaultColorIndex());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt(TAG_COLOR, getColor());
    }
}
