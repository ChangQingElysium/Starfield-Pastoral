package com.stardew.craft.platform.fabric.event.entity.living.spawn;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.platform.fabric.event.EventPriority;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.SubscribeEvent;
import com.stardew.craft.platform.fabric.event.entity.EntityJoinLevelEvent;
import com.stardew.craft.platform.fabric.event.entity.living.FinalizeSpawnEvent;
import com.stardew.craft.platform.fabric.event.entity.living.MobSpawnEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;

public final class MobSpawnHooks {
    private MobSpawnHooks() {}
    public static void initialize() { FabricEvents.GAME.register(MobSpawnHooks.class); }

    public static boolean placement(EntityType<?> type, ServerLevelAccessor level, MobSpawnType reason,
            BlockPos pos, RandomSource random, boolean vanillaResult) {
        return FabricEvents.GAME.post(new MobSpawnEvent.SpawnPlacementCheck(type, level, reason, pos, random, vanillaResult))
                .getPlacementCheckResult();
    }
    public static SpawnGroupData finalizeMobSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group, Operation<SpawnGroupData> original) {
        return finalizeMobSpawn(mob, level, difficulty, reason, group, null, original);
    }
    public static SpawnGroupData finalizeMobSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group, OwnedSpawner spawner, Operation<SpawnGroupData> original) {
        var event = post(mob, level, difficulty, reason, group, spawner);
        return event.isCanceled() ? null : original.call(mob, level, event.getDifficulty(), reason, event.getSpawnData());
    }
    /** Source-converted mod calls have the same outermost boundary as the audited native invocation hooks. */
    public static SpawnGroupData finalizeMobSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group) {
        var event = post(mob, level, difficulty, reason, group, null);
        return event.isCanceled() ? null : mob.finalizeSpawn(level, event.getDifficulty(), reason, event.getSpawnData());
    }
    public static FinalizeSpawnEvent finalizeSpawner(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group, OwnedSpawner spawner, boolean normallyFinalizes,
            Operation<SpawnGroupData> original) {
        var event = post(mob, level, difficulty, reason, group, spawner);
        if (normallyFinalizes && !event.isCanceled()) {
            if (original == null) throw new IllegalArgumentException("A normal spawner finalization requires its native operation");
            original.call(mob, level, event.getDifficulty(), reason, event.getSpawnData());
        }
        return event;
    }
    private static FinalizeSpawnEvent post(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group, OwnedSpawner spawner) {
        return FabricEvents.GAME.post(new FinalizeSpawnEvent(mob, level, mob.getX(), mob.getY(), mob.getZ(),
                difficulty, reason, group, spawner == null ? null : spawner.stardewcraft$spawnerOwner()));
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockCancelledSpawn(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && SpawnCancellation.isCancelled(mob)) event.setCanceled(true);
    }
}
