package com.stardew.craft.entity.seat;

import com.stardew.craft.port.PortEntities;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.PlaygroundBlock;
import com.stardew.craft.block.decor.DoubleSwingMotion;
import com.stardew.craft.block.decor.BirdSpringRiderMotion;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Transient, independently occupied seat; motion is deterministic on both sides. */
public final class BirdSpringRiderSeatEntity extends Entity implements AnimatedDecorSeat {
    private static final EntityDataAccessor<BlockPos> MAIN = SynchedEntityData.defineId(BirdSpringRiderSeatEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Direction> FACING = SynchedEntityData.defineId(BirdSpringRiderSeatEntity.class, EntityDataSerializers.DIRECTION);

    public BirdSpringRiderSeatEntity(EntityType<? extends BirdSpringRiderSeatEntity> type, Level level) {
        super(type, level); noPhysics = true; blocksBuilding = false;
    }
    @Override protected void defineSynchedData() {
        this.entityData.define(MAIN, BlockPos.ZERO); this.entityData.define(FACING, Direction.NORTH);
    }
    public BlockPos mainPos() { return entityData.get(MAIN); }
    @Override public Direction facing() { return entityData.get(FACING); }
    @Override public Vec3 riderFeet(float partialTick) {
        return BirdSpringRiderMotion.surface(mainPos(), facing(), level().getGameTime(), partialTick).add(0, CONTACT_CLEARANCE - HIPS_FROM_FEET, 0);
    }
    @Override public double riderAngle(float partialTick) {
        return BirdSpringRiderMotion.angle((level().getGameTime() + partialTick) / 20.0);
    }
    private void updatePosition() {
        setPos(riderFeet(0).add(0, PortEntities.PLAYER_VEHICLE_ATTACHMENT.y, 0));
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide) {
            var state = level().getBlockState(mainPos());
            if (!state.is(ModBlocks.BIRD_SPRING_RIDER.get()) || state.getValue(PlaygroundBlock.PART) != PlaygroundBlock.Part.MAIN
                    || state.getValue(PlaygroundBlock.FACING) != entityData.get(FACING) || tickCount > 1 && !isVehicle()) {
                discard(); return;
            }
        }
        updatePosition();
    }
    // PORT(1.20.1): 1.20.1 has no getPassengerRidingPosition; replay the 1.21 positionRider with it.
    @Override protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (hasPassenger(passenger)) com.stardew.craft.port.PortEntities.positionRider(passenger, getPassengerRidingPosition(passenger), callback);
    }
    public Vec3 getPassengerRidingPosition(Entity passenger) { return position(); }
    @Override protected boolean canAddPassenger(Entity passenger) { return !isVehicle(); }
    @Override public boolean shouldRiderSit() { return true; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        for (int sign : new int[]{1, -1}) {
            Vec3 offset = DoubleSwingMotion.rotate(new Vec3(0, 0, sign * 2), entityData.get(FACING));
            Vec3 target = Vec3.atBottomCenterOf(mainPos()).add(offset);
            BlockPos floor = BlockPos.containing(target).below();
            if (level().getBlockState(floor).isFaceSturdy(level(), floor, Direction.UP)
                    && level().noCollision(passenger, passenger.getBoundingBox().move(target.subtract(passenger.position())))) return target;
        }
        return super.getDismountLocationForPassenger(passenger);
    }
    public static BirdSpringRiderSeatEntity getOrCreate(ServerLevel level, BlockPos main, Direction facing) {
        for (var existing : level.getEntitiesOfClass(BirdSpringRiderSeatEntity.class, new AABB(main).inflate(4),
                e -> e.mainPos().equals(main))) return existing;
        var seat = new BirdSpringRiderSeatEntity(ModEntities.BIRD_SPRING_RIDER_SEAT.get(), level);
        seat.entityData.set(MAIN, main.immutable()); seat.entityData.set(FACING, facing);
        seat.updatePosition();
        return level.addFreshEntity(seat) ? seat : null;
    }
    public static void removeForPos(ServerLevel level, BlockPos main) {
        for (var seat : level.getEntitiesOfClass(BirdSpringRiderSeatEntity.class, new AABB(main).inflate(4), e -> e.mainPos().equals(main))) seat.discard();
    }
}
