package com.stardew.craft.platform.fabric.event.entity.living.spawn;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.entity.EntityPresence;
import com.stardew.craft.platform.fabric.event.Event;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.EntityDispatch;
import com.stardew.craft.platform.fabric.event.entity.living.FinalizeSpawnEvent;
import com.stardew.craft.platform.fabric.event.entity.living.MobSpawnEvent;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MobSpawnHooksTest {
    private final List<Object> listeners = new ArrayList<>();
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @AfterEach void cleanup() {
        listeners.forEach(FabricEvents.GAME::unregister);
        FabricEvents.GAME.unregister(MobSpawnHooks.class);
    }
    private <E extends Event> void listen(Class<E> type, Consumer<E> listener) {
        listeners.add(listener); FabricEvents.GAME.addListener(type, listener);
    }
    private static DifficultyInstance difficulty() { return new DifficultyInstance(Difficulty.NORMAL, 300, 20, 0); }
    private static ServerLevelAccessor level() {
        return (ServerLevelAccessor) Proxy.newProxyInstance(MobSpawnHooksTest.class.getClassLoader(),
                new Class<?>[] {ServerLevelAccessor.class}, (proxy, method, args) -> {
                    throw new UnsupportedOperationException("Spawn hook must not query this test world: " + method.getName());
                });
    }

    @Test void placementUsesTheOriginalNativeResultAndAllThreeExplicitOutcomes() {
        var world = level(); var pos = new BlockPos(1, 2, 3);
        var random = new net.minecraft.world.level.levelgen.LegacyRandomSource(4);
        for (boolean vanilla : new boolean[] {false, true}) {
            var event = new MobSpawnEvent.SpawnPlacementCheck(EntityType.ZOMBIE, world, MobSpawnType.NATURAL, pos, random, vanilla);
            assertSame(world, event.getLevel()); assertSame(random, event.getRandom()); assertSame(pos, event.getPos());
            assertEquals(vanilla, event.getPlacementCheckResult());
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED); assertTrue(event.getPlacementCheckResult());
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL); assertFalse(event.getPlacementCheckResult());
            assertThrows(IllegalStateException.class, () -> event.setCanceled(true));
        }
        listen(MobSpawnEvent.SpawnPlacementCheck.class, event -> {
            assertTrue(event.getDefaultResult()); assertSame(EntityType.ZOMBIE, event.getEntityType());
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        });
        assertFalse(MobSpawnHooks.placement(EntityType.ZOMBIE, world, MobSpawnType.NATURAL, pos, random, true));
    }

    @Test void cancellingFinalizeSkipsTheWholeSubclassAndReturnsNullWithoutImplicitSpawnCancellation() throws Exception {
        var mob = TestMob.create(); var world = level();
        listen(FinalizeSpawnEvent.class, event -> {
            assertSame(mob, event.getEntity()); assertSame(world, event.getLevel());
            assertEquals(7, event.getX()); assertEquals(8, event.getY()); assertEquals(9, event.getZ());
            event.setCanceled(true);
        });
        assertNull(MobSpawnHooks.finalizeMobSpawn(mob, world, difficulty(), MobSpawnType.NATURAL, new SpawnGroupData() {}));
        assertEquals(0, mob.before + mob.after); assertFalse(SpawnCancellation.isCancelled(mob));
    }

    @Test void changedDifficultyAndGroupDataReachTheOriginalOutermostOverride() throws Exception {
        var mob = TestMob.create(); var world = level();
        var changedDifficulty = new DifficultyInstance(Difficulty.HARD, 1, 1, 1);
        SpawnGroupData changed = new SpawnGroupData() {};
        listen(FinalizeSpawnEvent.class, event -> { event.setDifficulty(changedDifficulty); event.setSpawnData(changed); });
        var result = MobSpawnHooks.finalizeMobSpawn(mob, world, difficulty(), MobSpawnType.COMMAND, null,
                args -> ((Mob) args[0]).finalizeSpawn((ServerLevelAccessor) args[1], (DifficultyInstance) args[2],
                        (MobSpawnType) args[3], (SpawnGroupData) args[4]));
        assertSame(mob.returned, result); assertSame(changed, mob.group); assertSame(changedDifficulty, mob.difficulty);
        assertEquals(1, mob.before); assertEquals(1, mob.after); assertEquals(MobSpawnType.COMMAND, mob.reason);
    }

    @Test void spawnCancellationAloneKeepsInitializationButBlocksTheRealEntityManagerInsert() throws Exception {
        var mob = TestMob.create();
        listen(FinalizeSpawnEvent.class, event -> event.setSpawnCancelled(true));
        MobSpawnHooks.finalizeMobSpawn(mob, level(), difficulty(), MobSpawnType.EVENT, null);
        assertEquals(1, mob.before); assertEquals(1, mob.after);
        MobSpawnHooks.initialize(); MobSpawnHooks.initialize();
        int[] inserts = {0};
        assertFalse(EntityDispatch.join(mob, null, false, () -> { inserts[0]++; return true; }));
        assertEquals(0, inserts[0]); assertFalse(EntityPresence.isAdded(mob));
    }

    @Test void nbtSpawnerStillPublishesTheRealOwnerAndCanRejectTheSpawnWithoutRunningFinalize() throws Exception {
        var mob = TestMob.create();
        var block = new SpawnerBlockEntity(BlockPos.ZERO, Blocks.SPAWNER.defaultBlockState());
        OwnedSpawner owner = () -> com.mojang.datafixers.util.Either.left(block);
        int[] events = {0};
        listen(FinalizeSpawnEvent.class, event -> {
            events[0]++; assertSame(block, event.getSpawner().left().orElseThrow());
            assertEquals(MobSpawnType.SPAWNER, event.getSpawnType()); event.setSpawnCancelled(true);
        });
        var event = MobSpawnHooks.finalizeSpawner(mob, level(), difficulty(), MobSpawnType.SPAWNER, null, owner, false, null);
        assertTrue(event.isSpawnCancelled()); assertEquals(1, events[0]); assertEquals(0, mob.before + mob.after);
    }

    @Test void cancellationOfASpawnerEventSkipsNativeInitializationAndPreservesOwnerData() throws Exception {
        var mob = TestMob.create();
        OwnedSpawner owner = () -> com.mojang.datafixers.util.Either.right(mob);
        listen(FinalizeSpawnEvent.class, event -> { assertSame(mob, event.getSpawner().right().orElseThrow()); event.setCanceled(true); });
        assertNull(MobSpawnHooks.finalizeMobSpawn(mob, level(), difficulty(), MobSpawnType.TRIAL_SPAWNER, null, owner,
                args -> { throw new AssertionError("Cancelled subclass initialization must not run"); }));
        assertEquals(0, mob.before + mob.after);
    }

    @Test void commandsEggsAndPersistedEntriesHaveNoExtraAmbientFilter() throws Exception {
        MobSpawnHooks.initialize();
        int[] finalized = {0};
        listen(FinalizeSpawnEvent.class, event -> finalized[0]++);
        for (var reason : List.of(MobSpawnType.COMMAND, MobSpawnType.SPAWN_EGG)) {
            var mob = TestMob.create();
            assertNotNull(MobSpawnHooks.finalizeMobSpawn(mob, level(), difficulty(), reason, null));
            assertTrue(EntityDispatch.join(mob, null, false, () -> true));
        }
        var saved = TestMob.create();
        assertTrue(EntityDispatch.join(saved, null, true, () -> true));
        assertEquals(2, finalized[0]); assertEquals(0, saved.before + saved.after);
    }

    @Test void lateCancellationAndUncancellationUseTheNativePresenceContract() throws Exception {
        var mob = TestMob.create();
        SpawnCancellation.set(mob, true); assertTrue(SpawnCancellation.isCancelled(mob));
        SpawnCancellation.set(mob, false); assertFalse(SpawnCancellation.isCancelled(mob));
        EntityPresence.set(mob, true);
        assertThrows(UnsupportedOperationException.class, () -> SpawnCancellation.set(mob, true));
        assertThrows(UnsupportedOperationException.class, () -> SpawnCancellation.set(mob, false));
        assertFalse(SpawnCancellation.isCancelled(mob));
    }

    /** Real Mob virtual dispatch with observable subclass work; world construction belongs to the headless gate. */
    private static final class TestMob extends Mob implements SpawnCancellation.State, EntityPresence.State {
        private int before, after;
        private boolean cancelled, added;
        private DifficultyInstance difficulty;
        private SpawnGroupData group, returned;
        private MobSpawnType reason;
        private TestMob() { super(EntityType.ZOMBIE, null); }
        @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData group) {
            before++; this.difficulty = difficulty; this.reason = reason; this.group = group; after++; return returned;
        }
        @Override public boolean stardewcraft$spawnCancelled() { return cancelled; }
        @Override public void stardewcraft$spawnCancelled(boolean value) { cancelled = value; }
        @Override public boolean stardewcraft$isAddedToLevel() { return added; }
        @Override public void stardewcraft$setAddedToLevel(boolean value) { added = value; }
        private static TestMob create() throws Exception {
            Class<?> type = Class.forName("sun.misc.Unsafe"); var field = type.getDeclaredField("theUnsafe");
            if (!field.trySetAccessible()) throw new IllegalStateException("Native mob fixture allocator unavailable");
            var mob = (TestMob) type.getMethod("allocateInstance", Class.class).invoke(field.get(null), TestMob.class);
            var position = Entity.class.getDeclaredField("position");
            if (!position.trySetAccessible()) throw new IllegalStateException("Native position fixture unavailable");
            position.set(mob, new Vec3(7, 8, 9)); mob.returned = new SpawnGroupData() {};
            return mob;
        }
    }
}
