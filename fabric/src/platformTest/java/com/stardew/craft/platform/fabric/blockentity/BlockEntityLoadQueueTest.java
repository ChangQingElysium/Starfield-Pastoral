package com.stardew.craft.platform.fabric.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockEntityLoadQueueTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void registrationIsDeferredUntilDataHasLoadedAndRunsOnce() throws ReflectiveOperationException {
        Level owner = levelIdentity();
        RecordingEntity entity = new RecordingEntity(BlockPos.ZERO);
        entity.setLevel(owner);
        Map<BlockPos, BlockEntity> residents = residents(entity);
        BlockEntityLoadQueue queue = new BlockEntityLoadQueue();
        queue.enqueue(entity, residents);
        assertEquals(0, entity.loads);
        entity.savedValue = 42;
        queue.drain(owner);
        assertEquals(1, entity.loads);
        assertEquals(42, entity.observedValue);
        queue.drain(owner);
        assertEquals(1, entity.loads);
    }

    @Test
    void duplicatePendingRegistrationIsCoalescedByEntityIdentity() throws ReflectiveOperationException {
        Level owner = levelIdentity();
        RecordingEntity entity = new RecordingEntity(BlockPos.ZERO);
        entity.setLevel(owner);
        Map<BlockPos, BlockEntity> residents = residents(entity);
        BlockEntityLoadQueue queue = new BlockEntityLoadQueue();
        queue.enqueue(entity, residents);
        queue.enqueue(entity, residents);
        queue.drain(owner);
        assertEquals(1, entity.loads);
    }

    @Test
    void callbacksKeepRegistrationOrder() throws ReflectiveOperationException {
        Level owner = levelIdentity();
        RecordingEntity first = new RecordingEntity(BlockPos.ZERO);
        RecordingEntity second = new RecordingEntity(new BlockPos(1, 0, 0));
        first.setLevel(owner);
        second.setLevel(owner);
        Map<BlockPos, BlockEntity> residents = residents(first, second);
        List<String> calls = new ArrayList<>();
        first.callback = () -> calls.add("first");
        second.callback = () -> calls.add("second");
        BlockEntityLoadQueue queue = new BlockEntityLoadQueue();
        queue.enqueue(first, residents);
        queue.enqueue(second, residents);
        queue.drain(owner);
        assertEquals(List.of("first", "second"), calls);
    }

    @Test
    void removedReplacedAndUnloadedResidentsAreNotInitialized() throws ReflectiveOperationException {
        Level owner = levelIdentity();
        RecordingEntity removed = new RecordingEntity(BlockPos.ZERO);
        RecordingEntity replaced = new RecordingEntity(new BlockPos(1, 0, 0));
        RecordingEntity unloaded = new RecordingEntity(new BlockPos(2, 0, 0));
        RecordingEntity replacement = new RecordingEntity(replaced.getBlockPos());
        for (RecordingEntity entity : List.of(removed, replaced, unloaded, replacement)) entity.setLevel(owner);
        Map<BlockPos, BlockEntity> residents = residents(removed, replaced, unloaded);
        BlockEntityLoadQueue queue = new BlockEntityLoadQueue();
        queue.enqueue(removed, residents);
        queue.enqueue(replaced, residents);
        queue.enqueue(unloaded, residents);
        removed.setRemoved();
        residents.put(replaced.getBlockPos(), replacement);
        residents.remove(unloaded.getBlockPos());
        queue.enqueue(replacement, residents);
        queue.drain(owner);
        assertEquals(0, removed.loads);
        assertEquals(0, replaced.loads);
        assertEquals(0, unloaded.loads);
        assertEquals(1, replacement.loads);
    }

    @Test
    void missingOrDifferentLevelCannotReceiveTheCallback() throws ReflectiveOperationException {
        Level owner = levelIdentity();
        RecordingEntity detached = new RecordingEntity(BlockPos.ZERO);
        RecordingEntity moved = new RecordingEntity(new BlockPos(1, 0, 0));
        moved.setLevel(levelIdentity());
        Map<BlockPos, BlockEntity> residents = residents(detached, moved);
        BlockEntityLoadQueue queue = new BlockEntityLoadQueue();
        queue.enqueue(detached, residents);
        queue.enqueue(moved, residents);
        queue.drain(owner);
        assertEquals(0, detached.loads);
        assertEquals(0, moved.loads);
    }

    @Test
    void registrationsDuringOnLoadWaitForTheNextPass() throws ReflectiveOperationException {
        Level owner = levelIdentity();
        RecordingEntity first = new RecordingEntity(BlockPos.ZERO);
        RecordingEntity second = new RecordingEntity(new BlockPos(1, 0, 0));
        first.setLevel(owner);
        second.setLevel(owner);
        Map<BlockPos, BlockEntity> residents = residents(first, second);
        BlockEntityLoadQueue queue = new BlockEntityLoadQueue();
        first.callback = () -> queue.enqueue(second, residents);
        queue.enqueue(first, residents);
        queue.drain(owner);
        assertEquals(1, first.loads);
        assertEquals(0, second.loads);
        queue.drain(owner);
        assertEquals(1, second.loads);
        queue.drain(owner);
        assertEquals(1, second.loads);
    }

    @Test
    void theSameObjectCanBeRegisteredAgainAfterRemovalAndReload() throws ReflectiveOperationException {
        Level owner = levelIdentity();
        RecordingEntity entity = new RecordingEntity(BlockPos.ZERO);
        entity.setLevel(owner);
        Map<BlockPos, BlockEntity> oldResidents = residents(entity);
        BlockEntityLoadQueue queue = new BlockEntityLoadQueue();
        queue.enqueue(entity, oldResidents);
        queue.drain(owner);
        entity.setRemoved();
        oldResidents.clear();
        entity.clearRemoved();
        Map<BlockPos, BlockEntity> newResidents = residents(entity);
        queue.enqueue(entity, newResidents);
        queue.drain(owner);
        assertEquals(2, entity.loads);
    }

    @Test
    void callbacksAreIsolatedPerLevelWithoutAStaticWorldCache() throws ReflectiveOperationException {
        Level firstLevel = levelIdentity();
        Level secondLevel = levelIdentity();
        RecordingEntity first = new RecordingEntity(BlockPos.ZERO);
        RecordingEntity second = new RecordingEntity(BlockPos.ZERO);
        first.setLevel(firstLevel);
        second.setLevel(secondLevel);
        BlockEntityLoadQueue firstQueue = new BlockEntityLoadQueue();
        BlockEntityLoadQueue secondQueue = new BlockEntityLoadQueue();
        firstQueue.enqueue(first, residents(first));
        secondQueue.enqueue(second, residents(second));
        firstQueue.drain(firstLevel);
        assertEquals(1, first.loads);
        assertEquals(0, second.loads);
        secondQueue.drain(secondLevel);
        assertEquals(1, second.loads);
    }

    @Test
    void inheritedCallbacksKeepTheirSuperChain() throws ReflectiveOperationException {
        final class ChildEntity extends RecordingEntity {
            private int childLoads;

            ChildEntity() {
                super(BlockPos.ZERO);
            }

            @Override
            public void onLoad() {
                super.onLoad();
                childLoads++;
            }
        }
        Level owner = levelIdentity();
        ChildEntity entity = new ChildEntity();
        entity.setLevel(owner);
        BlockEntityLoadQueue queue = new BlockEntityLoadQueue();
        queue.enqueue(entity, residents(entity));
        queue.drain(owner);
        assertEquals(1, ((RecordingEntity) entity).loads);
        assertEquals(1, entity.childLoads);
    }

    @Test
    void callbackFailuresRemainVisible() throws ReflectiveOperationException {
        Level owner = levelIdentity();
        RecordingEntity entity = new RecordingEntity(BlockPos.ZERO);
        entity.setLevel(owner);
        IllegalStateException failure = new IllegalStateException("Load fixture failure");
        entity.callback = () -> { throw failure; };
        BlockEntityLoadQueue queue = new BlockEntityLoadQueue();
        queue.enqueue(entity, residents(entity));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> queue.drain(owner)));
    }

    private static class RecordingEntity extends FabricBlockEntity {
        private int loads;
        private int savedValue;
        private int observedValue;
        private Runnable callback = () -> {};

        private RecordingEntity(BlockPos position) {
            super(BlockEntityType.CHEST, position, Blocks.CHEST.defaultBlockState());
        }

        @Override
        public void onLoad() {
            super.onLoad();
            loads++;
            observedValue = savedValue;
            callback.run();
        }
    }

    private static Map<BlockPos, BlockEntity> residents(BlockEntity... entities) {
        Map<BlockPos, BlockEntity> residents = new HashMap<>();
        for (BlockEntity entity : entities) residents.put(entity.getBlockPos(), entity);
        return residents;
    }

    /** Identity-only level fixture; no server constructor, chunks or gameplay run. */
    private static Level levelIdentity() throws ReflectiveOperationException {
        Class<?> allocatorType = Class.forName("sun.misc.Unsafe");
        var field = allocatorType.getDeclaredField("theUnsafe");
        if (!field.trySetAccessible()) throw new IllegalStateException("Test fixture allocator is unavailable");
        Object allocator = field.get(null);
        return (Level) allocatorType.getMethod("allocateInstance", Class.class).invoke(allocator, ServerLevel.class);
    }
}
