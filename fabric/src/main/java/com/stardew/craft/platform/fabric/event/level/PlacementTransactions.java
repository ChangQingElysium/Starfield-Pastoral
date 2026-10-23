package com.stardew.craft.platform.fabric.event.level;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Scoped item-use transactions. Publication and restoration never join an enclosing capture. */
public final class PlacementTransactions {
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);

    private PlacementTransactions() {}

    /** Captures native block writes without an item event; close commits, rollback restores the entry states. */
    public static CaptureScope beginCapture(LevelAccessor level) {
        Frame frame = new Frame(java.util.Objects.requireNonNull(level, "level"));
        FRAMES.get().push(frame);
        return new CaptureScope(frame);
    }

    public static InteractionResult run(LevelAccessor level, Entity actor, Direction face, ItemStack held,
            Supplier<InteractionResult> action) {
        Deque<Frame> stack = FRAMES.get();
        Frame frame = new Frame(level);
        StackState original = StackState.capture(held);
        stack.push(frame);
        try {
            InteractionResult result = action.get();
            frame.phase = Phase.PUBLISH;
            if (result.consumesAction() && !frame.snapshots.isEmpty()) {
                StackState placed = StackState.capture(held);
                original.restore(held);
                BlockSnapshot first = frame.snapshots.getFirst();
                BlockState against = level.getBlockState(first.getPos().relative(face.getOpposite()));
                BlockEvent.EntityPlaceEvent event = frame.snapshots.size() == 1
                        ? new BlockEvent.EntityPlaceEvent(first, against, actor)
                        : new BlockEvent.EntityMultiPlaceEvent(frame.snapshots, against, actor);
                FabricEvents.GAME.post(event);
                if (event.isCanceled()) {
                    frame.restore(actor);
                    return InteractionResult.FAIL;
                }
                placed.restore(held);
            }
            // Neo's item hook only replays captured updates for a consuming result.
            if (result.consumesAction()) for (BlockSnapshot snapshot : frame.snapshots) snapshot.commit();
            return result;
        } catch (RuntimeException | Error failure) {
            try { if (!frame.restorationAttempted) frame.restore(actor); }
            catch (RuntimeException | Error restoreFailure) { failure.addSuppressed(restoreFailure); }
            finally { original.restore(held); }
            throw failure;
        } finally {
            stack.pop();
            if (stack.isEmpty()) FRAMES.remove();
        }
    }

    public static boolean isCapturing(LevelAccessor level) {
        Frame frame = current(level);
        return frame != null && frame.phase == Phase.CAPTURE;
    }

    public static boolean isRestoring(LevelAccessor level) {
        Frame frame = current(level);
        return frame != null && frame.phase == Phase.RESTORE;
    }

    public static BlockSnapshot beforeSet(LevelAccessor level, BlockPos pos, int flags, int recursionLeft) {
        Frame frame = current(level);
        if (frame == null || frame.phase != Phase.CAPTURE) return null;
        BlockSnapshot snapshot = BlockSnapshot.create(level, pos, flags, recursionLeft);
        // Capture entry order, including setBlock calls nested in onRemove.
        frame.snapshots.add(snapshot);
        return snapshot;
    }

    public static void afterSet(BlockSnapshot snapshot, boolean changed) {
        if (snapshot != null) {
            Frame frame = current(snapshot.getLevel());
            if (frame == null || frame.phase != Phase.CAPTURE) throw new IllegalStateException("Placement capture scope changed during setBlock");
            if (!changed) frame.snapshots.remove(snapshot);
        }
    }

    private static Frame current(LevelAccessor level) {
        for (Frame frame : FRAMES.get()) if (frame.level == level) return frame;
        return null;
    }

    public static final class CaptureScope implements AutoCloseable {
        private final Frame frame;
        private final Thread owner = Thread.currentThread();
        private boolean closed;

        private CaptureScope(Frame frame) { this.frame = frame; }

        public List<BlockSnapshot> snapshots() { return List.copyOf(frame.snapshots); }

        /** Commit notifications and onPlace callbacks for the final captured block states. */
        @Override public void close() { finish(false); }

        public void rollback() { finish(true); }

        private void finish(boolean rollback) {
            if (Thread.currentThread() != owner) throw new IllegalStateException("Capture scope belongs to another thread");
            if (closed) return;
            Deque<Frame> frames = FRAMES.get();
            if (frames.peek() != frame) throw new IllegalStateException("Capture scopes must close in reverse opening order");
            try {
                frame.phase = Phase.PUBLISH;
                if (rollback) frame.restore(null);
                else for (BlockSnapshot snapshot : frame.snapshots) snapshot.commit();
            } catch (RuntimeException | Error failure) {
                if (!frame.restorationAttempted) {
                    try { frame.restore(null); }
                    catch (RuntimeException | Error restoreFailure) { failure.addSuppressed(restoreFailure); }
                }
                throw failure;
            } finally {
                frames.pop();
                if (frames.isEmpty()) FRAMES.remove();
                closed = true;
            }
        }
    }

    private enum Phase { CAPTURE, PUBLISH, RESTORE }

    private static final class Frame {
        private final LevelAccessor level;
        private final List<BlockSnapshot> snapshots = new ArrayList<>();
        private Phase phase = Phase.CAPTURE;
        private boolean restorationAttempted;

        private Frame(LevelAccessor level) { this.level = level; }

        private void restore(Entity actor) {
            restorationAttempted = true;
            phase = Phase.RESTORE;
            List<BlockState> replaced = snapshots.stream().map(BlockSnapshot::getCurrentState).toList();
            for (int index = snapshots.size() - 1; index >= 0; index--) snapshots.get(index).restore();
            phase = Phase.PUBLISH;
            for (int index = 0; index < snapshots.size(); index++) {
                BlockSnapshot snapshot = snapshots.get(index);
                snapshot.notifyChange(replaced.get(index), snapshot.getCurrentState(), snapshot.getFlags() | Block.UPDATE_CLIENTS);
                if (actor instanceof ServerPlayer player && player.connection != null) {
                    player.connection.send(new ClientboundBlockUpdatePacket(snapshot.getPos(), snapshot.getCurrentState()));
                    BlockEntity blockEntity = level.getBlockEntity(snapshot.getPos());
                    if (blockEntity != null && blockEntity.getUpdatePacket() != null) player.connection.send(blockEntity.getUpdatePacket());
                }
            }
        }
    }

    private record StackState(int count, DataComponentMap components) {
        private static StackState capture(ItemStack stack) {
            int count = stack.getCount();
            // Native getComponents hides the stored map once the last item was consumed.
            if (stack != ItemStack.EMPTY && count <= 0) stack.setCount(1);
            try { return new StackState(count, DataComponentMap.builder().addAll(stack.getComponents()).build()); }
            finally { stack.setCount(count); }
        }

        private void restore(ItemStack stack) {
            if (stack == ItemStack.EMPTY) return;
            stack.setCount(Math.max(1, count));
            try {
                for (var type : new ArrayList<>(stack.getComponents().keySet())) {
                    if (!components.has(type)) stack.remove(type);
                }
                stack.applyComponents(components);
            } finally { stack.setCount(count); }
        }
    }
}
