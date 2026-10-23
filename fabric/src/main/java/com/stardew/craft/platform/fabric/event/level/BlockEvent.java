package com.stardew.craft.platform.fabric.event.level;

import com.stardew.craft.platform.fabric.event.Event;
import com.stardew.craft.platform.fabric.extension.ItemAbility;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public abstract class BlockEvent extends Event {
    private final LevelAccessor level;
    private final BlockPos pos;
    private final BlockState state;

    protected BlockEvent(LevelAccessor level, BlockPos pos, BlockState state) {
        this(level, pos, state, true);
    }

    protected BlockEvent(LevelAccessor level, BlockPos pos, BlockState state, boolean cancelable) {
        super(cancelable);
        this.level = level;
        this.pos = pos.immutable();
        this.state = state;
    }

    public LevelAccessor getLevel() { return level; }
    public BlockPos getPos() { return pos; }
    public BlockState getState() { return state; }

    public static final class BreakEvent extends BlockEvent {
        private final Player player;

        public BreakEvent(Level level, BlockPos pos, BlockState state, Player player) {
            super(level, pos, state);
            this.player = player;
        }

        public Player getPlayer() { return player; }
    }

    public static class EntityPlaceEvent extends BlockEvent {
        private final BlockSnapshot snapshot;
        private final BlockState placedAgainst;
        private final Entity entity;

        public EntityPlaceEvent(BlockSnapshot snapshot, BlockState placedAgainst, Entity entity) {
            super(snapshot.getLevel(), snapshot.getPos(), entity instanceof Player ? snapshot.getCurrentState() : snapshot.getState());
            this.snapshot = snapshot;
            this.placedAgainst = placedAgainst;
            this.entity = entity;
        }

        public Entity getEntity() { return entity; }
        public BlockSnapshot getBlockSnapshot() { return snapshot; }
        public BlockState getPlacedBlock() { return getState(); }
        public BlockState getPlacedAgainst() { return placedAgainst; }
    }

    public static final class EntityMultiPlaceEvent extends EntityPlaceEvent {
        private final List<BlockSnapshot> snapshots;

        public EntityMultiPlaceEvent(List<BlockSnapshot> snapshots, BlockState placedAgainst, Entity entity) {
            super(snapshots.getFirst(), placedAgainst, entity);
            this.snapshots = List.copyOf(snapshots);
        }

        public List<BlockSnapshot> getReplacedBlockSnapshots() { return snapshots; }
    }

    /** The tool bridge supplies the original state before any default or custom block action. */
    public static final class BlockToolModificationEvent extends BlockEvent {
        private final UseOnContext context;
        private final ItemAbility ability;
        private final boolean simulated;
        private BlockState finalState;

        public BlockToolModificationEvent(BlockState originalState, UseOnContext context,
                ItemAbility ability, boolean simulated) {
            super(context.getLevel(), context.getClickedPos(), originalState);
            this.context = context;
            this.ability = ability;
            this.simulated = simulated;
            this.finalState = originalState;
        }

        public Player getPlayer() { return context.getPlayer(); }
        public ItemStack getHeldItemStack() { return context.getItemInHand(); }
        public ItemAbility getItemAbility() { return ability; }
        public boolean isSimulated() { return simulated; }
        public UseOnContext getContext() { return context; }
        public BlockState getFinalState() { return finalState; }
        public void setFinalState(BlockState state) { finalState = state; }
    }
    /** Posted after native trampling chance, entity size and mob-griefing checks. */
    public static final class FarmlandTrampleEvent extends BlockEvent {
        private final Entity entity;
        private final float fallDistance;
        public FarmlandTrampleEvent(Level level, BlockPos pos, BlockState replacement, float fallDistance, Entity entity) {
            super(level, pos, replacement);
            this.entity = entity;
            this.fallDistance = fallDistance;
        }
        public Entity getEntity() { return entity; }
        public float getFallDistance() { return fallDistance; }
    }

    /** Neo's fluid hook consumes newState only; cancellation alone has no effect. */
    public static final class FluidPlaceBlockEvent extends BlockEvent {
        private final BlockPos liquidPos;
        private final BlockState originalState;
        private BlockState newState;
        public FluidPlaceBlockEvent(LevelAccessor level, BlockPos pos, BlockPos liquidPos, BlockState newState) {
            super(level, pos, newState);
            this.liquidPos = liquidPos.immutable();
            this.originalState = level.getBlockState(pos);
            this.newState = newState;
        }
        public BlockPos getLiquidPos() { return liquidPos; }
        public BlockState getOriginalState() { return originalState; }
        public BlockState getNewState() { return newState; }
        public void setNewState(BlockState value) { newState = java.util.Objects.requireNonNull(value); }
    }

}
