package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.entity.projectile.MeowmereProjectileEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Owns the single projectile created by one Rainbow Bolt runtime instance.
 */
public final class MeowmereShotTracker {
    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private MeowmereShotTracker() {}

    public static void start(
            UUID instanceId,
            UUID casterId,
            ResourceKey<Level> dimension,
            MeowmereProjectileEntity projectile,
            long endTick
    ) {
        ACTIVE.put(
                instanceId,
                new State(casterId, dimension, projectile, endTick)
        );
    }

    public static SkillTickResult tick(
            UUID instanceId,
            ResourceKey<Level> casterDimension,
            long nowTick
    ) {
        State state = ACTIVE.get(instanceId);
        if (state == null) {
            return SkillTickResult.CANCEL;
        }
        if (!isSameDimension(state.dimension, casterDimension)
                || !isSameDimension(
                        state.dimension,
                        state.projectile.level().dimension()
                )) {
            return SkillTickResult.CANCEL;
        }
        if (state.projectile.isRemoved()
                || hasTimedOut(nowTick, state.endTick)) {
            return SkillTickResult.COMPLETE;
        }
        return SkillTickResult.CONTINUE;
    }

    public static void stop(UUID instanceId) {
        State state = ACTIVE.remove(instanceId);
        discard(state);
    }

    public static void removeCaster(UUID casterId) {
        ACTIVE.entrySet().removeIf(entry -> {
            State state = entry.getValue();
            if (!state.casterId.equals(casterId)) {
                return false;
            }
            discard(state);
            return true;
        });
    }

    static boolean hasTimedOut(long nowTick, long endTick) {
        return nowTick >= endTick;
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    private static void discard(State state) {
        if (state != null && !state.projectile.isRemoved()) {
            state.projectile.discard();
        }
    }

    private static final class State {
        private final UUID casterId;
        private final ResourceKey<Level> dimension;
        private final MeowmereProjectileEntity projectile;
        private final long endTick;

        private State(
                UUID casterId,
                ResourceKey<Level> dimension,
                MeowmereProjectileEntity projectile,
                long endTick
        ) {
            this.casterId = casterId;
            this.dimension = dimension;
            this.projectile = projectile;
            this.endTick = endTick;
        }
    }
}
