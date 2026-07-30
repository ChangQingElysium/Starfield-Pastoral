package com.stardew.craft.combat.skill.runtime;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.equipment.StardewWeaponSkillContext;
import com.stardew.craft.api.v1.equipment.StardewWeaponSkillHandlers;
import com.stardew.craft.combat.network.SkillFailFeedbackPayload;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillContextStore;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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

    public static synchronized boolean hasActive(UUID casterId, ResourceLocation skillId) {
        return ACTIVE.values().stream().anyMatch(execution ->
                execution.instance().casterId().equals(casterId)
                        && execution.instance().skillId().equals(skillId));
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
            handler.begin(context, instance);
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
            return InteractionResultHolder.sidedSuccess(stack, false);
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
                    "Runtime weapon skill {} failed for player {}",
                    publicContext.skillId(),
                    player.getGameProfile().getName(),
                    exception
            );
            sendFailure(player, publicContext.hand() == net.minecraft.world.InteractionHand.MAIN_HAND);
            return InteractionResultHolder.fail(stack);
        }
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
            execution.handler().finish(context, instance, reason);
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
