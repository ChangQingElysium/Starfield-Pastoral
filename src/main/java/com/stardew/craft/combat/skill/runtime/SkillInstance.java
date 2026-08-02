package com.stardew.craft.combat.skill.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    private final List<Runnable> beginFailureCleanups = new ArrayList<>();
    private final List<Runnable> committedEffects = new ArrayList<>();
    private Phase phase = Phase.CREATED;
    private EndReason endReason;
    private CooldownState cooldownState = CooldownState.UNCLAIMED;
    private ExecutionState executionState;
    private boolean beginCleanupOpen;
    private boolean beginCommitted;

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
        beginCleanupOpen = true;
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

    /**
     * Attaches the handler-specific state for this execution. One active skill
     * instance has exactly one state owner; the runtime clears it after the
     * handler's finish callback for every terminal path.
     */
    public synchronized <T extends ExecutionState> void initializeExecutionState(
            T state
    ) {
        requirePhase(Phase.ACTIVE);
        if (executionState != null) {
            throw new IllegalStateException(
                    "Skill execution state is already initialized: " + instanceId
            );
        }
        executionState = Objects.requireNonNull(state, "state");
    }

    /**
     * Registers compensation for an external state created during
     * {@link RuntimeWeaponSkillHandler#begin}. The runtime keeps these
     * callbacks only until begin succeeds; a failed begin executes them in
     * reverse registration order.
     */
    public synchronized void registerBeginFailureCleanup(Runnable cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        if (phase != Phase.ACTIVE || !beginCleanupOpen) {
            throw new IllegalStateException(
                    "Skill begin cleanup window is closed: " + instanceId
            );
        }
        beginFailureCleanups.add(cleanup);
    }

    /**
     * Queues an irreversible effect captured while preparing the cast. The
     * runtime executes these callbacks only after {@link #commitBegin()} has
     * completed successfully.
     */
    public synchronized void registerCommittedEffect(Runnable effect) {
        Objects.requireNonNull(effect, "effect");
        if (phase != Phase.ACTIVE || !beginCleanupOpen) {
            throw new IllegalStateException(
                    "Skill committed-effect window is closed: " + instanceId
            );
        }
        committedEffects.add(effect);
    }

    synchronized void commitBegin() {
        if (phase != Phase.ACTIVE || !beginCleanupOpen) {
            throw new IllegalStateException(
                    "Skill begin cleanup window is closed: " + instanceId
            );
        }
        beginCleanupOpen = false;
        beginCommitted = true;
        beginFailureCleanups.clear();
    }

    void rollbackBeginFailure(RuntimeException beginFailure) {
        Objects.requireNonNull(beginFailure, "beginFailure");
        List<Runnable> cleanups;
        synchronized (this) {
            if (!beginCleanupOpen) {
                return;
            }
            beginCleanupOpen = false;
            cleanups = List.copyOf(beginFailureCleanups);
            beginFailureCleanups.clear();
            committedEffects.clear();
        }
        for (int index = cleanups.size() - 1; index >= 0; index--) {
            try {
                cleanups.get(index).run();
            } catch (RuntimeException cleanupFailure) {
                if (cleanupFailure != beginFailure) {
                    beginFailure.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    void runCommittedEffects() {
        List<Runnable> effects;
        synchronized (this) {
            if (!beginCommitted || beginCleanupOpen) {
                throw new IllegalStateException(
                        "Skill begin is not committed: " + instanceId
                );
            }
            effects = List.copyOf(committedEffects);
            committedEffects.clear();
        }
        for (Runnable effect : effects) {
            effect.run();
        }
    }

    public synchronized <T extends ExecutionState> Optional<T> executionState(
            Class<T> stateType
    ) {
        Objects.requireNonNull(stateType, "stateType");
        if (executionState == null) {
            return Optional.empty();
        }
        if (!stateType.isInstance(executionState)) {
            throw new IllegalStateException(
                    "Skill execution state is "
                            + executionState.getClass().getName()
                            + ", not "
                            + stateType.getName()
            );
        }
        return Optional.of(stateType.cast(executionState));
    }

    public synchronized <T extends ExecutionState> T requireExecutionState(
            Class<T> stateType
    ) {
        return executionState(stateType).orElseThrow(() ->
                new IllegalStateException(
                        "Skill execution state is not initialized: " + instanceId
                )
        );
    }

    synchronized void clearExecutionState() {
        executionState = null;
    }

    synchronized boolean tryCommitCooldown() {
        if (phase == Phase.CREATED || isTerminal()) {
            throw new IllegalStateException(
                    "Cannot commit cooldown during skill phase " + phase
            );
        }
        if (cooldownState != CooldownState.UNCLAIMED) {
            return false;
        }
        cooldownState = CooldownState.COMMITTED;
        return true;
    }

    synchronized boolean tryDeferCooldown() {
        if (phase == Phase.CREATED || isTerminal()) {
            throw new IllegalStateException(
                    "Cannot defer cooldown during skill phase " + phase
            );
        }
        if (cooldownState != CooldownState.UNCLAIMED) {
            return false;
        }
        cooldownState = CooldownState.DEFERRED;
        return true;
    }

    synchronized void completeDeferredCooldown() {
        if (cooldownState != CooldownState.DEFERRED) {
            throw new IllegalStateException(
                    "Skill cooldown is not deferred: " + instanceId
            );
        }
        cooldownState = CooldownState.COMMITTED;
    }

    synchronized boolean abandonDeferredCooldown() {
        if (cooldownState == CooldownState.ABANDONED) {
            return false;
        }
        if (cooldownState != CooldownState.DEFERRED) {
            throw new IllegalStateException(
                    "Skill cooldown is not deferred: " + instanceId
            );
        }
        cooldownState = CooldownState.ABANDONED;
        return true;
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

    public synchronized boolean cooldownCommitted() {
        return cooldownState == CooldownState.COMMITTED;
    }

    public synchronized boolean cooldownDeferred() {
        return cooldownState == CooldownState.DEFERRED;
    }

    public synchronized boolean cooldownAbandoned() {
        return cooldownState == CooldownState.ABANDONED;
    }

    synchronized boolean isBeginCleanupOpen() {
        return beginCleanupOpen;
    }

    public synchronized boolean beginCommitted() {
        return beginCommitted;
    }

    private enum CooldownState {
        UNCLAIMED,
        DEFERRED,
        COMMITTED,
        ABANDONED
    }

    /** Marker for state whose lifetime is exactly one runtime execution. */
    public interface ExecutionState {
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
