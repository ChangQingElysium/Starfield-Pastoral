package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.entity.projectile.MeowmereProjectileEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Owns the five projectiles created by one Meowmere Symphony cast.
 */
public final class MeowmereSymphonyTracker {
    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private MeowmereSymphonyTracker() {}

    public static void start(
            UUID instanceId,
            UUID casterId,
            ResourceKey<Level> dimension,
            List<MeowmereProjectileEntity> projectiles,
            long endTick
    ) {
        ACTIVE.put(
                instanceId,
                new State(
                        casterId,
                        dimension,
                        List.copyOf(projectiles),
                        endTick
                )
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
        boolean projectilesInCastDimension =
                state.projectiles.stream().allMatch(projectile ->
                        isSameDimension(
                                state.dimension,
                                projectile.level().dimension()
                        )
                );
        boolean allProjectilesRemoved =
                state.projectiles.stream().allMatch(
                        MeowmereProjectileEntity::isRemoved
                );
        return status(
                isSameDimension(state.dimension, casterDimension),
                projectilesInCastDimension,
                allProjectilesRemoved,
                nowTick,
                state.endTick
        );
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

    static SkillTickResult status(
            boolean casterInCastDimension,
            boolean projectilesInCastDimension,
            boolean allProjectilesRemoved,
            long nowTick,
            long endTick
    ) {
        if (!casterInCastDimension || !projectilesInCastDimension) {
            return SkillTickResult.CANCEL;
        }
        if (allProjectilesRemoved || nowTick >= endTick) {
            return SkillTickResult.COMPLETE;
        }
        return SkillTickResult.CONTINUE;
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    private static void discard(State state) {
        if (state == null) {
            return;
        }
        state.projectiles.stream()
                .filter(projectile -> !projectile.isRemoved())
                .forEach(MeowmereProjectileEntity::discard);
    }

    private static final class State {
        private final UUID casterId;
        private final ResourceKey<Level> dimension;
        private final List<MeowmereProjectileEntity> projectiles;
        private final long endTick;

        private State(
                UUID casterId,
                ResourceKey<Level> dimension,
                List<MeowmereProjectileEntity> projectiles,
                long endTick
        ) {
            this.casterId = casterId;
            this.dimension = dimension;
            this.projectiles = projectiles;
            this.endTick = endTick;
        }
    }
}
