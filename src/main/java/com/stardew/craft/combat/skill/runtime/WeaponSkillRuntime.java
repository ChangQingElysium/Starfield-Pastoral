package com.stardew.craft.combat.skill.runtime;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.CombatHealing;
import com.stardew.craft.api.v1.equipment.StardewWeaponSkillContext;
import com.stardew.craft.api.v1.equipment.StardewWeaponSkillHandlers;
import com.stardew.craft.combat.network.SkillFailFeedbackPayload;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.WeaponSkillContextStore;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponSkillData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative lifecycle runtime for built-in original weapon skills.
 */
public final class WeaponSkillRuntime {
    private static final Map<ResourceLocation, RuntimeWeaponSkillHandler> HANDLERS =
            new LinkedHashMap<>();
    private static final Map<UUID, ActiveExecution> ACTIVE = new LinkedHashMap<>();
    private static final ThreadLocal<SkillInstance> PREPARING =
            new ThreadLocal<>();

    private WeaponSkillRuntime() {}

    public static synchronized void register(
            ResourceLocation id,
            RuntimeWeaponSkillHandler handler
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (HANDLERS.putIfAbsent(id, handler) != null) {
            throw new IllegalStateException("Runtime weapon skill already registered: " + id);
        }
        StardewWeaponSkillHandlers.register(id, WeaponSkillRuntime::execute);
    }

    public static synchronized Optional<RuntimeWeaponSkillHandler> get(ResourceLocation id) {
        return Optional.ofNullable(HANDLERS.get(id));
    }

    public static synchronized Set<ResourceLocation> registeredSkillIds() {
        return Set.copyOf(new LinkedHashSet<>(HANDLERS.keySet()));
    }

    public static synchronized Optional<SkillInstance> active(UUID instanceId) {
        ActiveExecution execution = ACTIVE.get(instanceId);
        return execution == null ? Optional.empty() : Optional.of(execution.instance());
    }

    /**
     * Defers shared presentation mutations invoked by a handler's begin hook.
     * Utilities use this boundary so notification and animation-lock failures
     * are treated as post-commit failures instead of rejected casts.
     */
    public static boolean deferIfPreparing(Runnable effect) {
        Objects.requireNonNull(effect, "effect");
        SkillInstance instance = PREPARING.get();
        if (instance == null) {
            return false;
        }
        instance.registerCommittedEffect(effect);
        return true;
    }

    /**
     * Pays a begin-time energy cost and automatically restores the exact
     * amount spent if the handler later fails. Creative mode and free-energy
     * effects remain zero-cost because the measured balance does not change.
     */
    public static boolean consumeEnergyDuringBegin(
            SkillExecutionContext context,
            SkillInstance instance,
            float amount
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(instance, "instance");
        if (amount <= 0.0F
                || context.player().getAbilities().instabuild) {
            return true;
        }
        float before = PlayerStardewDataAPI.getEnergy(context.player());
        boolean exhaustedBefore = PlayerStardewDataAPI.isExhausted(
                context.player()
        );
        if (!PlayerStardewDataAPI.consumeEnergy(context.player(), amount)) {
            return false;
        }
        float spent = Math.max(
                0.0F,
                before - PlayerStardewDataAPI.getEnergy(context.player())
        );
        if (spent > 0.0F) {
            instance.registerBeginFailureCleanup(() ->
                    PlayerStardewDataAPI.rollbackEnergyPayment(
                            context.player(),
                            spent,
                            exhaustedBefore
                    )
            );
        }
        return true;
    }

    /** Pays a nonlethal health cost with exact begin-failure compensation. */
    public static float spendHealthDuringBegin(
            SkillExecutionContext context,
            SkillInstance instance,
            float requested,
            float minimumRemaining
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(instance, "instance");
        float spent = CombatHealing.spendNonlethal(
                context.player(),
                requested,
                minimumRemaining
        );
        if (spent > 0.0F) {
            instance.registerBeginFailureCleanup(() ->
                    CombatHealing.rollbackHealthPayment(
                            context.player(),
                            spent
                    )
            );
        }
        return spent;
    }

    public static synchronized boolean hasActive(UUID casterId, ResourceLocation skillId) {
        return ACTIVE.values().stream().anyMatch(execution ->
                execution.instance().casterId().equals(casterId)
                        && execution.instance().skillId().equals(skillId));
    }

    /**
     * Returns typed state only for the caster's exact currently active skill.
     */
    public static synchronized <T extends SkillInstance.ExecutionState>
            Optional<T> activeExecutionState(
                    UUID casterId,
                    ResourceLocation skillId,
                    Class<T> stateType
            ) {
        return findActiveExecutionState(
                ACTIVE.values().stream()
                        .map(ActiveExecution::instance)
                        .toList(),
                casterId,
                skillId,
                stateType
        );
    }

    static <T extends SkillInstance.ExecutionState>
            Optional<T> findActiveExecutionState(
                    Iterable<SkillInstance> instances,
                    UUID casterId,
                    ResourceLocation skillId,
                    Class<T> stateType
            ) {
        Objects.requireNonNull(instances, "instances");
        Objects.requireNonNull(casterId, "casterId");
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(stateType, "stateType");
        for (SkillInstance instance : instances) {
            if (instance.phase() != SkillInstance.Phase.ACTIVE
                    || !instance.casterId().equals(casterId)
                    || !instance.skillId().equals(skillId)) {
                continue;
            }
            Optional<SkillInstance.ExecutionState> state =
                    instance.executionState(
                            SkillInstance.ExecutionState.class
                    );
            if (state.isPresent()
                    && stateType.isInstance(state.get())) {
                return Optional.of(stateType.cast(state.get()));
            }
        }
        return Optional.empty();
    }

    /**
     * Commits one cooldown for one accepted execution. Repeated calls for the
     * same instance are idempotent and do not emit another sync packet.
     */
    public static boolean commitCooldown(
            SkillExecutionContext context,
            SkillInstance instance,
            int durationTicks
    ) {
        validateCooldownContext(context, instance);
        if (!instance.tryCommitCooldown()) {
            return false;
        }
        long nowTick = context.player().level().getGameTime();
        int previousRemaining = WeaponSkillCooldowns.getRemainingTicks(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                nowTick
        );
        if (instance.isBeginCleanupOpen()) {
            instance.registerBeginFailureCleanup(() ->
                    WeaponSkillCooldowns.setCooldownUntil(
                            context.player(),
                            context.weaponId().getPath(),
                            context.skillData().getId(),
                            context.player().level().getGameTime(),
                            context.player().level().getGameTime()
                                    + previousRemaining
                    )
            );
        }
        WeaponSkillCooldowns.setCooldown(
                context.player(),
                context.weapon(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                nowTick,
                durationTicks
        );
        return true;
    }

    /**
     * Reserves this execution's single cooldown submission for later
     * settlement by a state object that outlives the execution.
     */
    public static DeferredSkillCooldown deferCooldown(
            SkillExecutionContext context,
            SkillInstance instance,
            int durationTicks
    ) {
        validateCooldownContext(context, instance);
        if (!instance.tryDeferCooldown()) {
            throw new IllegalStateException(
                    "Skill cooldown is already claimed: "
                            + instance.instanceId()
            );
        }
        int appliedDurationTicks =
                WeaponSkillCooldowns.adjustedDurationForRelease(
                        context.player(),
                        context.weapon(),
                        durationTicks
                );
        DeferredSkillCooldown cooldown = new DeferredSkillCooldown(
                instance,
                context.player().getUUID(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                appliedDurationTicks
        );
        if (instance.isBeginCleanupOpen()) {
            instance.registerBeginFailureCleanup(cooldown::abandon);
        }
        return cooldown;
    }

    public static DeferredSkillCooldown restoreDeferredCooldown(
            ServerPlayer player,
            String weaponId,
            String skillId,
            int appliedDurationTicks
    ) {
        Objects.requireNonNull(player, "player");
        return new DeferredSkillCooldown(
                null,
                player.getUUID(),
                weaponId,
                skillId,
                appliedDurationTicks
        );
    }

    public static DeferredSkillCooldown restoreLegacyDeferredCooldown(
            ServerPlayer player,
            String weaponId,
            String skillId,
            int baseDurationTicks
    ) {
        Objects.requireNonNull(player, "player");
        int appliedDurationTicks =
                WeaponSkillCooldowns.adjustedDurationForRelease(
                        player,
                        player.getMainHandItem(),
                        baseDurationTicks
                );
        return restoreDeferredCooldown(
                player,
                weaponId,
                skillId,
                appliedDurationTicks
        );
    }

    public static boolean commitDeferredCooldown(
            ServerPlayer player,
            DeferredSkillCooldown cooldown,
            long nowTick
    ) {
        Objects.requireNonNull(cooldown, "cooldown");
        return cooldown.commit(player, nowTick);
    }

    /**
     * Explicitly ends a deferred transaction without applying a cooldown.
     * Only authored cancellation paths may use this operation.
     */
    public static boolean abandonDeferredCooldown(
            DeferredSkillCooldown cooldown
    ) {
        Objects.requireNonNull(cooldown, "cooldown");
        return cooldown.abandon();
    }

    /**
     * Clears an already committed cooldown for an authored skill interaction.
     * This is an explicit override, not a second cooldown submission.
     */
    public static void clearCooldown(
            ServerPlayer player,
            String weaponId,
            String skillId,
            long nowTick
    ) {
        Objects.requireNonNull(player, "player");
        if (weaponId == null || weaponId.isBlank()
                || skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException(
                    "Cooldown override requires weapon and skill ids"
            );
        }
        WeaponSkillCooldowns.setCooldownUntil(
                player,
                weaponId,
                skillId,
                nowTick,
                nowTick
        );
    }

    private static void validateCooldownContext(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(instance, "instance");
        if (!instance.casterId().equals(context.player().getUUID())
                || !instance.weaponId().equals(context.weaponId())
                || !instance.skillId().equals(context.skillId())) {
            throw new IllegalArgumentException(
                    "Cooldown context does not match skill instance "
                            + instance.instanceId()
            );
        }
    }

    /**
     * Resolves the release weapon for a pending skill hit without exposing the
     * runtime's retained stack.
     *
     * <p>The caster and skill id must both match. Child hit ids that differ
     * from their parent execution must use the explicit snapshot overload on
     * {@code WeaponSkillContextStore}.</p>
     */
    public static synchronized Optional<WeaponDamageSnapshot> releaseWeaponSnapshot(
            UUID casterId,
            String skillId
    ) {
        Objects.requireNonNull(casterId, "casterId");
        String normalizedSkillId = skillId == null ? "" : skillId.trim();

        for (ActiveExecution execution : ACTIVE.values()) {
            if (!execution.instance().casterId().equals(casterId)) {
                continue;
            }
            if (matchesSkillId(execution.instance().skillId(), normalizedSkillId)) {
                return Optional.of(execution.weaponSnapshot());
            }
        }
        return Optional.empty();
    }

    public static void tickPlayer(ServerPlayer player, long nowTick) {
        WeaponSkillContextStore.clearExpired(player, nowTick);
        ActiveExecution[] executions;
        synchronized (WeaponSkillRuntime.class) {
            executions = ACTIVE.values().stream()
                    .filter(execution -> execution.instance().casterId().equals(player.getUUID()))
                    .toArray(ActiveExecution[]::new);
        }
        for (ActiveExecution execution : executions) {
            SkillExecutionContext currentContext = execution.context().withNowTick(nowTick);
            if (!player.isAlive() || player.isRemoved()) {
                logTerminationFailure(
                        execution,
                        SkillInstance.EndReason.CASTER_UNAVAILABLE,
                        endExecution(
                                execution,
                                currentContext,
                                SkillInstance.EndReason.CASTER_UNAVAILABLE
                        )
                );
                continue;
            }
            if (!execution.releaseDimension().equals(player.level().dimension())) {
                logTerminationFailure(
                        execution,
                        SkillInstance.EndReason.INVALIDATED,
                        endExecution(
                                execution,
                                currentContext,
                                SkillInstance.EndReason.INVALIDATED
                        )
                );
                continue;
            }
            SkillTickResult result;
            try {
                result = Objects.requireNonNull(
                        execution.handler().tick(currentContext, execution.instance()),
                        "Runtime weapon skill tick result"
                );
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Ticking runtime weapon skill {} failed for player {}",
                        execution.instance().skillId(),
                        player.getGameProfile().getName(),
                        exception
                );
                result = SkillTickResult.CANCEL;
            }
            if (result == SkillTickResult.COMPLETE) {
                logTerminationFailure(
                        execution,
                        SkillInstance.EndReason.COMPLETED,
                        endExecution(
                                execution,
                                currentContext,
                                SkillInstance.EndReason.COMPLETED
                        )
                );
            } else if (result == SkillTickResult.CANCEL) {
                logTerminationFailure(
                        execution,
                        SkillInstance.EndReason.INVALIDATED,
                        endExecution(
                                execution,
                                currentContext,
                                SkillInstance.EndReason.INVALIDATED
                        )
                );
            }
        }
        WeaponSkillStateRuntime.tickPlayer(player, nowTick);
    }

    /**
     * Advances the small subset of skills whose authoritative movement must
     * happen after vanilla connection position reconciliation.
     */
    public static void tickPostServer(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ActiveExecution[] executions;
        synchronized (WeaponSkillRuntime.class) {
            executions = ACTIVE.values().stream()
                    .filter(execution -> execution.handler()
                            instanceof PostServerRuntimeWeaponSkillHandler)
                    .toArray(ActiveExecution[]::new);
        }

        for (ActiveExecution execution : executions) {
            synchronized (WeaponSkillRuntime.class) {
                if (ACTIVE.get(execution.instance().instanceId())
                        != execution) {
                    continue;
                }
            }
            ServerPlayer player = server.getPlayerList().getPlayer(
                    execution.instance().casterId()
            );
            if (player == null) {
                logTerminationFailure(
                        execution,
                        SkillInstance.EndReason.CASTER_UNAVAILABLE,
                        endExecution(
                                execution,
                                execution.context(),
                                SkillInstance.EndReason.CASTER_UNAVAILABLE
                        )
                );
                continue;
            }
            if (execution.context().player() != player) {
                logTerminationFailure(
                        execution,
                        SkillInstance.EndReason.CASTER_UNAVAILABLE,
                        endExecution(
                                execution,
                                execution.context(),
                                SkillInstance.EndReason.CASTER_UNAVAILABLE
                        )
                );
                continue;
            }
            if (!player.isAlive() || player.isRemoved()) {
                logTerminationFailure(
                        execution,
                        SkillInstance.EndReason.CASTER_UNAVAILABLE,
                        endExecution(
                                execution,
                                execution.context(),
                                SkillInstance.EndReason.CASTER_UNAVAILABLE
                        )
                );
                continue;
            }

            SkillExecutionContext currentContext =
                    execution.context().withNowTick(
                            player.level().getGameTime()
                    );
            if (!execution.releaseDimension().equals(
                    player.level().dimension()
            )) {
                logTerminationFailure(
                        execution,
                        SkillInstance.EndReason.INVALIDATED,
                        endExecution(
                                execution,
                                currentContext,
                                SkillInstance.EndReason.INVALIDATED
                        )
                );
                continue;
            }
            try {
                SkillTickResult result = Objects.requireNonNull(
                        ((PostServerRuntimeWeaponSkillHandler)
                                execution.handler()).postServerTick(
                                        currentContext,
                                        execution.instance()
                                ),
                        "Post-server weapon skill tick result"
                );
                if (result == SkillTickResult.COMPLETE) {
                    logTerminationFailure(
                            execution,
                            SkillInstance.EndReason.COMPLETED,
                            endExecution(
                                    execution,
                                    currentContext,
                                    SkillInstance.EndReason.COMPLETED
                            )
                    );
                } else if (result == SkillTickResult.CANCEL) {
                    logTerminationFailure(
                            execution,
                            SkillInstance.EndReason.INVALIDATED,
                            endExecution(
                                    execution,
                                    currentContext,
                                    SkillInstance.EndReason.INVALIDATED
                            )
                    );
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Post-server ticking runtime weapon skill {} failed "
                                + "for player {}",
                        execution.instance().skillId(),
                        player.getGameProfile().getName(),
                        exception
                );
                logTerminationFailure(
                        execution,
                        SkillInstance.EndReason.INVALIDATED,
                        endExecution(
                                execution,
                                currentContext,
                                SkillInstance.EndReason.INVALIDATED
                        )
                );
            }
        }
    }

    public static void removePlayer(UUID playerId) {
        ActiveExecution[] executions;
        synchronized (WeaponSkillRuntime.class) {
            executions = ACTIVE.values().stream()
                    .filter(execution -> execution.instance().casterId().equals(playerId))
                    .toArray(ActiveExecution[]::new);
        }
        try {
            for (ActiveExecution execution : executions) {
                logTerminationFailure(
                        execution,
                        SkillInstance.EndReason.CASTER_UNAVAILABLE,
                        endExecution(
                                execution,
                                execution.context(),
                                SkillInstance.EndReason.CASTER_UNAVAILABLE
                        )
                );
            }
        } finally {
            WeaponSkillContextStore.removePlayer(playerId);
        }
    }

    private static InteractionResultHolder<ItemStack> execute(StardewWeaponSkillContext publicContext) {
        ItemStack stack = publicContext.weapon();
        if (!(publicContext.player() instanceof ServerPlayer player)
                || !(stack.getItem() instanceof IStardewWeapon weaponItem)) {
            return InteractionResultHolder.pass(stack);
        }

        RuntimeWeaponSkillHandler handler;
        synchronized (WeaponSkillRuntime.class) {
            handler = HANDLERS.get(publicContext.skillId());
        }
        if (handler == null) {
            return InteractionResultHolder.pass(stack);
        }

        WeaponData weaponData = weaponItem.getWeaponData();
        WeaponSkillData skillData = resolveSkillData(
                weaponData,
                publicContext.skillId(),
                publicContext.majorSkill()
        );
        if (skillData == null) {
            sendFailure(player, publicContext.hand() == net.minecraft.world.InteractionHand.MAIN_HAND);
            return InteractionResultHolder.fail(stack);
        }

        long nowTick = player.level().getGameTime();
        ResourceLocation weaponId = ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID,
                weaponItem.getWeaponId()
        );
        SkillExecutionContext context = new SkillExecutionContext(
                player,
                publicContext.hand(),
                stack,
                weaponId,
                publicContext.skillId(),
                skillData,
                publicContext.majorSkill(),
                nowTick
        );
        SkillValidation validation = handler.validate(context);
        if (!validation.accepted()) {
            sendFailure(player, publicContext.hand() == net.minecraft.world.InteractionHand.MAIN_HAND);
            return InteractionResultHolder.fail(stack);
        }

        SkillInstance instance = new SkillInstance(
                UUID.randomUUID(),
                player.getUUID(),
                player.getId(),
                weaponId,
                publicContext.skillId(),
                nowTick,
                player.position(),
                player.getLookAngle(),
                player.getRandom().nextLong()
        );
        ActiveExecution execution = new ActiveExecution(
                context,
                handler,
                instance,
                player.level().dimension(),
                context.weaponSnapshot()
        );
        synchronized (WeaponSkillRuntime.class) {
            ACTIVE.put(
                    instance.instanceId(),
                    execution
            );
        }

        try {
            instance.activate();
            if (PREPARING.get() != null) {
                throw new IllegalStateException(
                        "Nested runtime weapon skill preparation"
                );
            }
            PREPARING.set(instance);
            try {
                handler.begin(context, instance);
            } finally {
                PREPARING.remove();
            }
            instance.commitBegin();
        } catch (RuntimeException exception) {
            instance.rollbackBeginFailure(exception);
            RuntimeException cleanupFailure = endExecution(
                    execution,
                    context,
                    SkillInstance.EndReason.INVALIDATED,
                    false
            );
            if (cleanupFailure != null && cleanupFailure != exception) {
                exception.addSuppressed(cleanupFailure);
            }
            StardewCraft.LOGGER.error(
                    "Runtime weapon skill {} failed for player {}",
                    publicContext.skillId(),
                    player.getGameProfile().getName(),
                    exception
            );
            sendFailure(player, publicContext.hand() == net.minecraft.world.InteractionHand.MAIN_HAND);
            return InteractionResultHolder.fail(stack);
        }

        try {
            handler.applyCommittedEffects(context, instance);
            if (handler.completesImmediately()) {
                RuntimeException finishFailure = endExecution(
                        execution,
                        context,
                        SkillInstance.EndReason.COMPLETED
                );
                if (finishFailure != null) {
                    throw finishFailure;
                }
            }
        } catch (RuntimeException exception) {
            RuntimeException cleanupFailure = endExecution(
                    execution,
                    context,
                    SkillInstance.EndReason.INVALIDATED
            );
            if (cleanupFailure != null && cleanupFailure != exception) {
                exception.addSuppressed(cleanupFailure);
            }
            StardewCraft.LOGGER.error(
                    "Committed runtime weapon skill {} failed for player {}",
                    publicContext.skillId(),
                    player.getGameProfile().getName(),
                    exception
            );
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    private static WeaponSkillData resolveSkillData(
            WeaponData weaponData,
            ResourceLocation skillId,
            boolean majorSkill
    ) {
        if (weaponData == null) {
            return null;
        }
        WeaponSkillData skillData = weaponData.getSkill(majorSkill);
        if (skillData == null) {
            return null;
        }
        return skillData.matches(skillId) ? skillData : null;
    }

    private static void sendFailure(ServerPlayer player, boolean mainHand) {
        PacketDistributor.sendToPlayer(player, new SkillFailFeedbackPayload(mainHand));
    }

    static boolean matchesSkillId(ResourceLocation activeSkillId, String pendingSkillId) {
        if (activeSkillId == null || pendingSkillId == null || pendingSkillId.isBlank()) {
            return false;
        }
        return activeSkillId.toString().equals(pendingSkillId)
                || activeSkillId.getPath().equals(pendingSkillId);
    }

    private static RuntimeException endExecution(
            ActiveExecution execution,
            SkillExecutionContext context,
            SkillInstance.EndReason reason
    ) {
        return endExecution(execution, context, reason, true);
    }

    private static RuntimeException endExecution(
            ActiveExecution execution,
            SkillExecutionContext context,
            SkillInstance.EndReason reason,
            boolean invokeHandlerFinish
    ) {
        SkillInstance instance = execution.instance();
        synchronized (WeaponSkillRuntime.class) {
            if (!ACTIVE.containsKey(instance.instanceId())) {
                return null;
            }
        }
        RuntimeException failure = null;
        try {
            if (reason == SkillInstance.EndReason.COMPLETED
                    && instance.phase() == SkillInstance.Phase.ACTIVE) {
                instance.beginRecovery();
            }
            if (invokeHandlerFinish) {
                execution.handler().finish(context, instance, reason);
            }
        } catch (RuntimeException exception) {
            failure = exception;
        } finally {
            try {
                if (!instance.isTerminal()) {
                    instance.finish(reason);
                }
            } catch (RuntimeException terminalFailure) {
                if (failure == null) {
                    failure = terminalFailure;
                } else if (failure != terminalFailure) {
                    failure.addSuppressed(terminalFailure);
                }
            } finally {
                instance.clearExecutionState();
                synchronized (WeaponSkillRuntime.class) {
                    ACTIVE.remove(instance.instanceId());
                }
            }
        }
        return failure;
    }

    private static void logTerminationFailure(
            ActiveExecution execution,
            SkillInstance.EndReason reason,
            RuntimeException failure
    ) {
        if (failure == null) {
            return;
        }
        StardewCraft.LOGGER.error(
                "Ending runtime weapon skill {} for caster {} with reason {} failed",
                execution.instance().skillId(),
                execution.instance().casterId(),
                reason,
                failure
        );
    }

    private record ActiveExecution(
            SkillExecutionContext context,
            RuntimeWeaponSkillHandler handler,
            SkillInstance instance,
            ResourceKey<Level> releaseDimension,
            WeaponDamageSnapshot weaponSnapshot
    ) {}
}
