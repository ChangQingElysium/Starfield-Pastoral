package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** One authoritative Light Counter parry window. */
final class LightCounterExecutionState
        implements SkillInstance.ExecutionState {
    private final ResourceKey<Level> dimension;
    private final long endTick;
    private final String weaponId;
    private final WeaponDamageSnapshot weaponSnapshot;
    private boolean consumed;
    private boolean cancelled;

    LightCounterExecutionState(
            ResourceKey<Level> dimension,
            long nowTick,
            int durationTicks,
            String weaponId,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Light Counter duration must be positive"
            );
        }
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.endTick = nowTick + durationTicks;
        this.weaponId = Objects.requireNonNull(weaponId, "weaponId");
        this.weaponSnapshot = weaponSnapshot;
    }

    boolean isActive(
            long nowTick,
            ResourceKey<Level> currentDimension
    ) {
        return !consumed
                && !cancelled
                && dimension.equals(currentDimension)
                && nowTick <= endTick;
    }

    Optional<LightCounterSkillHandler.CounterActivation> consume(
            long nowTick,
            ResourceKey<Level> currentDimension
    ) {
        if (!isActive(nowTick, currentDimension)) {
            return Optional.empty();
        }
        consumed = true;
        return Optional.of(
                new LightCounterSkillHandler.CounterActivation(
                        weaponId,
                        weaponSnapshot
                )
        );
    }

    SkillTickResult advance(
            long nowTick,
            ResourceKey<Level> currentDimension
    ) {
        if (consumed) {
            return SkillTickResult.COMPLETE;
        }
        if (cancelled || !dimension.equals(currentDimension)) {
            return SkillTickResult.CANCEL;
        }
        return nowTick <= endTick
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    void cancel() {
        cancelled = true;
    }
}
