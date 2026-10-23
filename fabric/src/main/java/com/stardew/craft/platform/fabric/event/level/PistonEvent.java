package com.stardew.craft.platform.fabric.event.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;

/** Facing is the piston facing, including retraction; the resolver supplies actual movement. */
public abstract class PistonEvent extends BlockEvent {
    public enum PistonMoveType {
        EXTEND(true), RETRACT(false);
        public final boolean isExtend;
        PistonMoveType(boolean isExtend) { this.isExtend = isExtend; }
    }
    private final Direction direction;
    private final PistonMoveType moveType;
    protected PistonEvent(Level level, BlockPos pos, Direction direction, PistonMoveType moveType, boolean cancelable) {
        super(level, pos, level.getBlockState(pos), cancelable);
        this.direction = direction; this.moveType = moveType;
    }
    public Direction getDirection() { return direction; }
    public BlockPos getFaceOffsetPos() { return getPos().relative(direction); }
    public PistonMoveType getPistonMoveType() { return moveType; }
    public PistonStructureResolver getStructureHelper() { return new PistonStructureResolver((Level) getLevel(), getPos(), direction, moveType.isExtend); }
    public static final class Pre extends PistonEvent {
        public Pre(Level level, BlockPos pos, Direction direction, PistonMoveType moveType) { super(level, pos, direction, moveType, true); }
    }
}
