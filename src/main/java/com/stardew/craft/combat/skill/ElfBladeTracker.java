package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.ElfBladePayload;
import com.stardew.craft.entity.projectile.ElfBladeLeafEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ElfBladeTracker {

    public static final int LEAF_COUNT = 3;
    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private static final class State {
        private long endTick;
        private String weaponId;
        private String skillId;
        private int cooldownTicks;
        private boolean cooldownApplied;
        private final Set<UUID> leafIds = new LinkedHashSet<>();

        private State(long endTick, String weaponId, String skillId, int cooldownTicks) {
            this.endTick = endTick;
            this.weaponId = weaponId;
            this.skillId = skillId;
            this.cooldownTicks = cooldownTicks;
            this.cooldownApplied = false;
        }
    }

    private ElfBladeTracker() {}

    public static void start(ServerPlayer player, long nowTick, int durationTicks, float damageMultiplier,
                             String weaponId, String skillId, int cooldownTicks) {
        start(
                player,
                nowTick,
                durationTicks,
                damageMultiplier,
                weaponId,
                skillId,
                cooldownTicks,
                null
        );
    }

    public static void start(
            ServerPlayer player,
            long nowTick,
            int durationTicks,
            float damageMultiplier,
            String weaponId,
            String skillId,
            int cooldownTicks,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null || durationTicks <= 0 || weaponId == null || skillId == null) {
            return;
        }

        long endTick = nowTick + durationTicks;
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            state = new State(endTick, weaponId, skillId, cooldownTicks);
            ACTIVE.put(player.getUUID(), state);
        } else {
            clearLeaves(player, state);
            state.endTick = endTick;
            state.weaponId = weaponId;
            state.skillId = skillId;
            state.cooldownTicks = cooldownTicks;
            state.cooldownApplied = false;
            state.leafIds.clear();
        }

        PacketDistributor.sendToPlayer(player, new ElfBladePayload(true, durationTicks));
        spawnLeaves(
                player,
                state,
                endTick,
                damageMultiplier,
                skillId,
                weaponSnapshot
        );
    }

    public static void tick(ServerPlayer player, long nowTick) {
        if (player == null) {
            return;
        }
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }

        if (nowTick >= state.endTick) {
            finish(player, state, nowTick);
        }
    }

    public static boolean isActive(ServerPlayer player, long nowTick) {
        if (player == null) {
            return false;
        }
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return false;
        }
        if (nowTick >= state.endTick) {
            finish(player, state, nowTick);
            return false;
        }
        return true;
    }

    public static void fireLeafAtTarget(ServerPlayer player, LivingEntity target, long nowTick) {
        if (player == null || target == null) {
            return;
        }
        if (!isActive(player, nowTick)) {
            return;
        }
        ElfBladeLeafEntity leaf = findOrbitingLeaf(player);
        if (leaf != null) {
            leaf.launchToTarget(target);
        }
    }

    private static void spawnLeaves(
            ServerPlayer player,
            State state,
            long endTick,
            float damageMultiplier,
            String skillId,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        for (int i = 0; i < LEAF_COUNT; i++) {
            ElfBladeLeafEntity leaf = new ElfBladeLeafEntity(
                    level,
                    player,
                    damageMultiplier,
                    skillId,
                    i,
                    endTick,
                    weaponSnapshot
            );
            leaf.setPos(player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ());
            level.addFreshEntity(leaf);
            state.leafIds.add(leaf.getUUID());
        }
    }

    @SuppressWarnings("null")
    private static ElfBladeLeafEntity findOrbitingLeaf(ServerPlayer player) {
        @SuppressWarnings("null")
        net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(6.0, 4.0, 6.0);
        List<ElfBladeLeafEntity> leaves = player.level().getEntitiesOfClass(
            ElfBladeLeafEntity.class,
            box,
            leaf -> leaf.isOrbiting() && leaf.getOwner() == player
        );
        if (leaves.isEmpty()) {
            return null;
        }
        ElfBladeLeafEntity best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (ElfBladeLeafEntity leaf : leaves) {
            int idx = leaf.getOrbitIndex();
            if (best == null || idx < bestIndex) {
                best = leaf;
                bestIndex = idx;
            }
        }
        return best;
    }

    @SuppressWarnings("null")
    private static void clearLeaves(ServerPlayer player, State state) {
        for (ServerLevel level : player.server.getAllLevels()) {
            for (UUID leafId : state.leafIds) {
                if (level.getEntity(leafId) instanceof ElfBladeLeafEntity leaf) {
                    leaf.discard();
                }
            }
        }
    }

    private static void finish(ServerPlayer player, State state, long nowTick) {
        if (!state.cooldownApplied && state.cooldownTicks > 0) {
            WeaponSkillCooldowns.setCooldown(
                player,
                state.weaponId,
                state.skillId,
                nowTick,
                state.cooldownTicks
            );
            state.cooldownApplied = true;
        }
        PacketDistributor.sendToPlayer(player, new ElfBladePayload(false, 0));
        ACTIVE.remove(player.getUUID());
    }

    public static void cancel(ServerPlayer player, long nowTick) {
        if (player == null) {
            return;
        }
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        clearLeaves(player, state);
        finish(player, state, nowTick);
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
