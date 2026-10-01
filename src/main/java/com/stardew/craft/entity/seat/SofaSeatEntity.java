package com.stardew.craft.entity.seat;

import com.stardew.craft.block.utility.SofaBlock;
import com.stardew.craft.block.utility.ChairBlock;
import com.stardew.craft.block.utility.CushionBlock;
import com.stardew.craft.block.utility.DyeableChairBlock;
import com.stardew.craft.block.utility.OfficeChair2Block;
import com.stardew.craft.block.utility.OfficeStoolBlock;
import com.stardew.craft.blockentity.OfficeStoolBlockEntity;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("null")
public class SofaSeatEntity extends Entity {
    private static final double DEFAULT_SEAT_Y_OFFSET = 6.0D / 16.0D;
    private static final String TAG_X = "SofaX";
    private static final String TAG_Y = "SofaY";
    private static final String TAG_Z = "SofaZ";
    private static final String TAG_OFFSET = "SeatOffset";

    private BlockPos sofaPos = BlockPos.ZERO;
    private double seatYOffset = DEFAULT_SEAT_Y_OFFSET;

    public SofaSeatEntity(EntityType<? extends SofaSeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    public SofaSeatEntity(Level level, BlockPos sofaPos) {
        this(level, sofaPos, DEFAULT_SEAT_Y_OFFSET);
    }

    public SofaSeatEntity(Level level, BlockPos sofaPos, double seatYOffset) {
        this(ModEntities.SOFA_SEAT.get(), level);
        setSofaPos(sofaPos, seatYOffset);
    }

    public void setSofaPos(BlockPos pos) {
        setSofaPos(pos, seatYOffset);
    }

    public void setSofaPos(BlockPos pos, double offset) {
        this.sofaPos = pos;
        this.seatYOffset = offset;
        this.setPos(pos.getX() + 0.5D, pos.getY() + seatYOffset, pos.getZ() + 0.5D);
    }

    public BlockPos getSofaPos() {
        return sofaPos;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }

        BlockState seatBlockState = level().getBlockState(sofaPos);
        if (!isSupportedSeatBlock(seatBlockState.getBlock())) {
            discard();
            return;
        }

        if (isVehicle()
            && (seatBlockState.getBlock() instanceof OfficeStoolBlock || seatBlockState.getBlock() instanceof OfficeChair2Block)
            && !getPassengers().isEmpty()) {
            if (level().getBlockEntity(sofaPos) instanceof OfficeStoolBlockEntity stoolBe) {
                stoolBe.setTopYawDegrees(getFirstPassenger().getYRot() + 180.0F);
            }
        }

        this.setPos(sofaPos.getX() + 0.5D, sofaPos.getY() + seatYOffset, sofaPos.getZ() + 0.5D);
        if (this.tickCount > 1 && !isVehicle()) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        int x = tag.getInt(TAG_X);
        int y = tag.getInt(TAG_Y);
        int z = tag.getInt(TAG_Z);
        this.seatYOffset = tag.contains(TAG_OFFSET) ? tag.getDouble(TAG_OFFSET) : DEFAULT_SEAT_Y_OFFSET;
        this.sofaPos = new BlockPos(x, y, z);
        this.setPos(x + 0.5D, y + seatYOffset, z + 0.5D);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt(TAG_X, sofaPos.getX());
        tag.putInt(TAG_Y, sofaPos.getY());
        tag.putInt(TAG_Z, sofaPos.getZ());
        tag.putDouble(TAG_OFFSET, seatYOffset);
    }

    // PORT(1.20.1): 1.20.1 has no getPassengerRidingPosition; replay the 1.21 positionRider with it.
    @Override protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (hasPassenger(passenger)) com.stardew.craft.port.PortEntities.positionRider(passenger, getPassengerRidingPosition(passenger), callback);
    }
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return new Vec3(getX(), getY(), getZ());
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !this.isVehicle();
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Nullable
    public static SofaSeatEntity getOrCreate(ServerLevel level, BlockPos sofaPos) {
        return getOrCreate(level, sofaPos, DEFAULT_SEAT_Y_OFFSET);
    }

    @Nullable
    public static SofaSeatEntity getOrCreate(ServerLevel level, BlockPos sofaPos, double seatYOffset) {
        AABB query = new AABB(sofaPos).inflate(0.3D);
        List<SofaSeatEntity> seats = level.getEntitiesOfClass(SofaSeatEntity.class, query, seat -> seat.getSofaPos().equals(sofaPos));
        SofaSeatEntity seat = seats.isEmpty() ? null : seats.get(0);
        if (seat == null || !seat.isAlive()) {
            seat = new SofaSeatEntity(level, sofaPos, seatYOffset);
            if (!level.addFreshEntity(seat)) {
                return null;
            }
        } else {
            seat.setSofaPos(sofaPos, seatYOffset);
        }
        return seat;
    }

    private static boolean isSupportedSeatBlock(net.minecraft.world.level.block.Block block) {
        return block instanceof SofaBlock
            || block instanceof com.stardew.craft.block.decor.ParkBenchBlock
            || block instanceof ChairBlock
            || block instanceof CushionBlock
            || block instanceof OfficeStoolBlock
            || block instanceof OfficeChair2Block
            || block instanceof DyeableChairBlock;
    }

    public static void removeForPos(ServerLevel level, BlockPos sofaPos) {
        AABB query = new AABB(sofaPos).inflate(0.4D);
        for (SofaSeatEntity seat : level.getEntitiesOfClass(SofaSeatEntity.class, query, e -> e.getSofaPos().equals(sofaPos))) {
            seat.discard();
        }
    }
}
