package com.stardew.craft.platform.fabric.event.entity.living;

import com.stardew.craft.platform.fabric.event.Event;
import com.stardew.craft.platform.fabric.event.entity.EntityEvent;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

/** Events carry the real spawn world/type/mob at their native decision boundary. */
public abstract class MobSpawnEvent extends EntityEvent {
    private final ServerLevelAccessor level;
    private final double x, y, z;
    protected MobSpawnEvent(Mob mob, ServerLevelAccessor level, double x, double y, double z, boolean cancelable) {
        super(mob, cancelable);
        this.level = Objects.requireNonNull(level); this.x = x; this.y = y; this.z = z;
    }
    @Override public Mob getEntity() { return (Mob) super.getEntity(); }
    public ServerLevelAccessor getLevel() { return level; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    public static final class SpawnPlacementCheck extends Event {
        public enum Result { SUCCEED, DEFAULT, FAIL }
        private final EntityType<?> entityType;
        private final ServerLevelAccessor level;
        private final MobSpawnType spawnType;
        private final BlockPos pos;
        private final RandomSource random;
        private final boolean defaultResult;
        private Result result = Result.DEFAULT;
        public SpawnPlacementCheck(EntityType<?> entityType, ServerLevelAccessor level, MobSpawnType spawnType,
                BlockPos pos, RandomSource random, boolean defaultResult) {
            this.entityType = Objects.requireNonNull(entityType); this.level = Objects.requireNonNull(level);
            this.spawnType = Objects.requireNonNull(spawnType); this.pos = Objects.requireNonNull(pos);
            this.random = Objects.requireNonNull(random); this.defaultResult = defaultResult;
        }
        public EntityType<?> getEntityType() { return entityType; }
        public ServerLevelAccessor getLevel() { return level; }
        public MobSpawnType getSpawnType() { return spawnType; }
        public BlockPos getPos() { return pos; }
        public RandomSource getRandom() { return random; }
        public boolean getDefaultResult() { return defaultResult; }
        public Result getResult() { return result; }
        public void setResult(Result result) { this.result = Objects.requireNonNull(result); }
        public boolean getPlacementCheckResult() { return result == Result.SUCCEED || result == Result.DEFAULT && defaultResult; }
    }
}
