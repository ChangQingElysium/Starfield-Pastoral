package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.ElfBladePayload;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.entity.projectile.ElfBladeLeafEntity;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

/** One runtime leaf window with exact ownership of its spawned entities. */
final class ElfBladeLeafExecutionState
        implements SkillInstance.ExecutionState {
    private final ResourceKey<Level> dimension;
    private final long endTick;
    private final DeferredSkillCooldown cooldown;
    private final Set<UUID> leafIds = new LinkedHashSet<>();
    private boolean settled;

    ElfBladeLeafExecutionState(
            ResourceKey<Level> dimension,
            long nowTick,
            int durationTicks,
            DeferredSkillCooldown cooldown
    ) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Moonlit Leaf Blades duration must be positive"
            );
        }
        this.dimension = Objects.requireNonNull(
                dimension,
                "dimension"
        );
        this.endTick = nowTick + durationTicks;
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    void start(
            SkillExecutionContext context,
            float damageMultiplier,
            String skillId
    ) {
        ServerPlayer player = context.player();
        PacketDistributor.sendToPlayer(
                player,
                new ElfBladePayload(
                        true,
                        ElfBladeLeafSkillHandler.ACTIVE_DURATION_TICKS
                )
        );

        ServerLevel level = player.serverLevel();
        WeaponDamageSnapshot weaponSnapshot = context.weaponSnapshot();
        for (int index = 0;
                index < ElfBladeLeafSkillHandler.LEAF_COUNT;
                index++) {
            ElfBladeLeafEntity leaf = new ElfBladeLeafEntity(
                    level,
                    player,
                    damageMultiplier,
                    skillId,
                    index,
                    endTick,
                    weaponSnapshot
            );
            leaf.setPos(
                    player.getX(),
                    player.getY() + player.getBbHeight() * 0.6D,
                    player.getZ()
            );
            if (!level.addFreshEntity(leaf)) {
                throw new IllegalStateException(
                        "Failed to add every Moonlit Leaf Blade"
                );
            }

            UUID leafId = leaf.getUUID();
            leafIds.add(leafId);
        }
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (settled) {
            return SkillTickResult.COMPLETE;
        }
        if (!dimension.equals(context.player().level().dimension())) {
            return SkillTickResult.CANCEL;
        }
        if (isWithinActiveWindow(context.nowTick(), endTick)) {
            return SkillTickResult.CONTINUE;
        }
        completeNaturally(context.player(), context.nowTick());
        return SkillTickResult.COMPLETE;
    }

    void fireLeafAtTarget(
            ServerPlayer player,
            LivingEntity target,
            long nowTick
    ) {
        if (settled
                || !dimension.equals(player.level().dimension())) {
            return;
        }
        if (!isWithinActiveWindow(nowTick, endTick)) {
            completeNaturally(player, nowTick);
            return;
        }
        ElfBladeLeafEntity leaf = findOrbitingLeaf(player);
        if (leaf != null) {
            leaf.launchToTarget(target);
        }
    }

    void cancel(ServerPlayer player, long nowTick) {
        if (settled) {
            return;
        }
        clearLeaves(player);
        settle(player, nowTick);
    }

    private void completeNaturally(
            ServerPlayer player,
            long nowTick
    ) {
        if (settled) {
            return;
        }
        settle(player, nowTick);
    }

    private void settle(ServerPlayer player, long nowTick) {
        WeaponSkillRuntime.commitDeferredCooldown(
                player,
                cooldown,
                nowTick
        );
        PacketDistributor.sendToPlayer(
                player,
                new ElfBladePayload(false, 0)
        );
        settled = true;
    }

    private ElfBladeLeafEntity findOrbitingLeaf(ServerPlayer player) {
        ServerLevel level = player.server.getLevel(dimension);
        if (level == null) {
            return null;
        }
        AABB bounds = player.getBoundingBox().inflate(6.0D, 4.0D, 6.0D);
        ElfBladeLeafEntity best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (UUID leafId : Set.copyOf(leafIds)) {
            Entity entity = level.getEntity(leafId);
            if (!(entity instanceof ElfBladeLeafEntity leaf)
                    || leaf.isRemoved()) {
                leafIds.remove(leafId);
                continue;
            }
            if (!leaf.isOrbiting()
                    || leaf.getOwner() != player
                    || !bounds.intersects(leaf.getBoundingBox())) {
                continue;
            }
            int orbitIndex = leaf.getOrbitIndex();
            if (best == null || orbitIndex < bestIndex) {
                best = leaf;
                bestIndex = orbitIndex;
            }
        }
        return best;
    }

    private void clearLeaves(ServerPlayer player) {
        RuntimeException cleanupFailure = null;
        for (UUID leafId : Set.copyOf(leafIds)) {
            boolean found = false;
            for (ServerLevel level : player.server.getAllLevels()) {
                if (level.getEntity(leafId)
                        instanceof ElfBladeLeafEntity leaf) {
                    found = true;
                    try {
                        leaf.discard();
                        leafIds.remove(leafId);
                    } catch (RuntimeException exception) {
                        if (cleanupFailure == null) {
                            cleanupFailure = exception;
                        } else if (cleanupFailure != exception) {
                            cleanupFailure.addSuppressed(exception);
                        }
                    }
                    break;
                }
            }
            if (!found) {
                leafIds.remove(leafId);
            }
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    static boolean isWithinActiveWindow(
            long nowTick,
            long endTick
    ) {
        return nowTick < endTick;
    }
}
