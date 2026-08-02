package com.stardew.craft.combat.skill.runtime;

import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * One release-bound cooldown transaction that may outlive its skill instance.
 *
 * <p>The adjusted duration is frozen when the skill is released, so delayed
 * settlement cannot accidentally read a later main-hand item or profession.</p>
 */
public final class DeferredSkillCooldown {
    private final SkillInstance instance;
    private final UUID casterId;
    private final String weaponId;
    private final String skillId;
    private final int appliedDurationTicks;
    private boolean committed;
    private boolean abandoned;

    DeferredSkillCooldown(
            SkillInstance instance,
            UUID casterId,
            String weaponId,
            String skillId,
            int appliedDurationTicks
    ) {
        this.instance = instance;
        this.casterId = Objects.requireNonNull(casterId, "casterId");
        this.weaponId = Objects.requireNonNull(weaponId, "weaponId");
        this.skillId = Objects.requireNonNull(skillId, "skillId");
        this.appliedDurationTicks = Math.max(0, appliedDurationTicks);
    }

    synchronized boolean commit(ServerPlayer player, long nowTick) {
        Objects.requireNonNull(player, "player");
        if (!casterId.equals(player.getUUID())) {
            throw new IllegalArgumentException(
                    "Deferred cooldown caster does not match player"
            );
        }
        if (committed || abandoned) {
            return false;
        }
        WeaponSkillCooldowns.setCooldownUntil(
                player,
                weaponId,
                skillId,
                nowTick,
                nowTick + appliedDurationTicks
        );
        if (instance != null) {
            instance.completeDeferredCooldown();
        }
        committed = true;
        return true;
    }

    synchronized boolean abandon() {
        if (committed || abandoned) {
            return false;
        }
        if (instance != null) {
            instance.abandonDeferredCooldown();
        }
        abandoned = true;
        return true;
    }

    public String weaponId() {
        return weaponId;
    }

    public String skillId() {
        return skillId;
    }

    public int appliedDurationTicks() {
        return appliedDurationTicks;
    }

    public synchronized boolean committed() {
        return committed;
    }

    public synchronized boolean abandoned() {
        return abandoned;
    }
}
