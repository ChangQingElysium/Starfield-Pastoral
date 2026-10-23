package com.stardew.craft.platform.fabric.event.entity.living;

import com.mojang.datafixers.util.Either;
import com.stardew.craft.platform.fabric.event.entity.living.spawn.SpawnCancellation;
import java.util.Objects;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Cancellation skips initialization; spawnCancelled independently prevents actual world insertion. */
public final class FinalizeSpawnEvent extends MobSpawnEvent {
    private final MobSpawnType spawnType;
    private final Either<BlockEntity, Entity> spawner;
    private DifficultyInstance difficulty;
    private SpawnGroupData spawnData;
    public FinalizeSpawnEvent(Mob mob, ServerLevelAccessor level, double x, double y, double z,
            DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData spawnData,
            Either<BlockEntity, Entity> spawner) {
        super(mob, level, x, y, z, true);
        this.difficulty = Objects.requireNonNull(difficulty); this.spawnType = Objects.requireNonNull(spawnType);
        this.spawnData = spawnData; this.spawner = spawner;
    }
    public MobSpawnType getSpawnType() { return spawnType; }
    public DifficultyInstance getDifficulty() { return difficulty; }
    public void setDifficulty(DifficultyInstance difficulty) { this.difficulty = Objects.requireNonNull(difficulty); }
    public SpawnGroupData getSpawnData() { return spawnData; }
    public void setSpawnData(SpawnGroupData data) { spawnData = data; }
    public Either<BlockEntity, Entity> getSpawner() { return spawner; }
    public boolean isSpawnCancelled() { return SpawnCancellation.isCancelled(getEntity()); }
    public void setSpawnCancelled(boolean cancel) { SpawnCancellation.set(getEntity(), cancel); }
}
