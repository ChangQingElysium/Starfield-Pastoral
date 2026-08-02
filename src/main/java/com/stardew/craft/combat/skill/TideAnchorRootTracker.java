package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementArbiter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Authoritative movement-lock portion of Tide Anchor's root. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class TideAnchorRootTracker {
    private static final String TAG_END_TICK =
            "stardewcraft_tide_anchor_root_until";

    private TideAnchorRootTracker() {
    }

    public static void applyPreAdjusted(
            LivingEntity target,
            long nowTick,
            int durationTicks
    ) {
        if (target == null || durationTicks <= 0) {
            return;
        }
        target.getPersistentData().putLong(
                TAG_END_TICK,
                nowTick + durationTicks
        );
        if (target instanceof ServerPlayer player) {
            WeaponSkillMovementArbiter.revokeCurrent(player);
        }
    }

    public static boolean isMovementLocked(
            LivingEntity target,
            long nowTick
    ) {
        if (target == null) {
            return false;
        }
        CompoundTag tag = target.getPersistentData();
        if (!tag.contains(TAG_END_TICK)) {
            return false;
        }
        if (nowTick >= tag.getLong(TAG_END_TICK)) {
            tag.remove(TAG_END_TICK);
            return false;
        }
        return true;
    }

    public static void clear(LivingEntity target) {
        if (target != null) {
            target.getPersistentData().remove(TAG_END_TICK);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity
                && !entity.level().isClientSide) {
            isMovementLocked(entity, entity.level().getGameTime());
        }
    }
}
