package com.stardew.craft.combat.skill.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * One server-authoritative execution of an original StardewCraft weapon skill.
 */
public final class SkillInstance {
    private final UUID instanceId;
    private final UUID casterId;
    private final int casterEntityId;
    private final ResourceLocation weaponId;
    private final ResourceLocation skillId;
    private final long startGameTick;
    private final Vec3 origin;
    private final Vec3 direction;
    private final long seed;
    private final List<Integer> targetEntityIds = new ArrayList<>();
    private Phase phase = Phase.CREATED;
    private EndReason endReason;

    public SkillInstance(
            UUID instanceId,
            UUID casterId,
            int casterEntityId,
            ResourceLocation weaponId,
            ResourceLocation skillId,
            long startGameTick,
            Vec3 origin,
            Vec3 direction,
            long seed
    ) {
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.casterId = Objects.requireNonNull(casterId, "casterId");
        this.casterEntityId = casterEntityId;
        this.weaponId = Objects.requireNonNull(weaponId, "weaponId");
        this.skillId = Objects.requireNonNull(skillId, "skillId");
        this.startGameTick = startGameTick;
        this.origin = Objects.requireNonNull(origin, "origin");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.seed = seed;
    }

    public void activate() {
        requirePhase(Phase.CREATED);
        phase = Phase.ACTIVE;
    }

    public void beginRecovery() {
        requirePhase(Phase.ACTIVE);
        phase = Phase.RECOVERY;
    }

    public void finish(EndReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (isTerminal()) {
            throw new IllegalStateException("Skill instance is already terminal: " + instanceId);
        }
        phase = reason == EndReason.COMPLETED ? Phase.ENDED : Phase.CANCELLED;
        endReason = reason;
    }

    public void setTargetEntityIds(List<Integer> entityIds) {
        if (isTerminal()) {
            throw new IllegalStateException("Cannot change targets on a terminal skill instance");
        }
        targetEntityIds.clear();
        targetEntityIds.addAll(List.copyOf(entityIds));
    }

    public boolean isTerminal() {
        return phase == Phase.ENDED || phase == Phase.CANCELLED;
    }

    private void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException(
                    "Expected skill phase " + expected + " but was " + phase);
        }
    }

    public UUID instanceId() {
        return instanceId;
    }

    public UUID casterId() {
        return casterId;
    }

    public int casterEntityId() {
        return casterEntityId;
    }

    public ResourceLocation weaponId() {
        return weaponId;
    }

    public ResourceLocation skillId() {
        return skillId;
    }

    public long startGameTick() {
        return startGameTick;
    }

    public Vec3 origin() {
        return origin;
    }

    public Vec3 direction() {
        return direction;
    }

    public long seed() {
        return seed;
    }

    public List<Integer> targetEntityIds() {
        return List.copyOf(targetEntityIds);
    }

    public Phase phase() {
        return phase;
    }

    public EndReason endReason() {
        return endReason;
    }

    public enum Phase {
        CREATED,
        ACTIVE,
        RECOVERY,
        ENDED,
        CANCELLED
    }

    public enum EndReason {
        COMPLETED,
        INTERRUPTED,
        INVALIDATED,
        CASTER_UNAVAILABLE
    }
}
