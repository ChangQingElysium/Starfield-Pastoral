package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection;
import com.stardew.craft.combat.network.YetiFreezePayload;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementArbiter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class YetiFreezeTracker {

    public enum PresentationPolicy {
        SYNC_FREEZE_OVERLAY,
        SERVER_ONLY_STAGGER
    }

    private static final String TAG_END_TICK = "stardewcraft_yeti_freeze_until";
    private static final String TAG_PREV_NO_AI = "stardewcraft_yeti_freeze_prev_no_ai";
    private static final String TAG_SYNC_FREEZE_OVERLAY =
            "stardewcraft_yeti_freeze_sync_overlay";

    private YetiFreezeTracker() {}

    public static void apply(LivingEntity target, long nowTick, int durationTicks) {
        applyWithEquipmentProtection(target, nowTick, durationTicks);
    }

    /**
     * Applies the authored hard movement lock and returns its actual duration.
     * Player equipment gets one Stardew-style immunity roll for the whole
     * custom status; Sturdy shortens the same authoritative lock.
     */
    public static int applyWithEquipmentProtection(
            LivingEntity target,
            long nowTick,
            int durationTicks,
            PresentationPolicy presentationPolicy
    ) {
        if (target == null || durationTicks <= 0) {
            return 0;
        }

        EquipmentNegativeStatusProtection.Decision protection =
                EquipmentNegativeStatusProtection.decide(
                        target,
                        durationTicks
                );
        if (protection.resisted()) {
            return 0;
        }
        return applyPreAdjusted(
                target,
                nowTick,
                protection.durationTicks(),
                presentationPolicy
        );
    }

    /**
     * Applies a duration that already passed one shared equipment decision.
     * Composite authored statuses use this to avoid rolling immunity twice.
     */
    public static int applyPreAdjusted(
            LivingEntity target,
            long nowTick,
            int appliedDuration,
            PresentationPolicy presentationPolicy
    ) {
        if (target == null || appliedDuration <= 0) {
            return 0;
        }

        CompoundTag tag = target.getPersistentData();
        boolean existingSyncedOverlay = tag.getBoolean(
                TAG_SYNC_FREEZE_OVERLAY
        ) && isWithinFreezeWindow(
                nowTick,
                tag.getLong(TAG_END_TICK)
        );
        tag.putLong(TAG_END_TICK, nowTick + appliedDuration);
        boolean syncFreezeOverlay = existingSyncedOverlay
                || presentationPolicy
                == PresentationPolicy.SYNC_FREEZE_OVERLAY;
        tag.putBoolean(TAG_SYNC_FREEZE_OVERLAY, syncFreezeOverlay);

        if (target instanceof ServerPlayer player) {
            WeaponSkillMovementArbiter.revokeCurrent(player);
        }

        if (target instanceof Mob mob) {
            if (!tag.contains(TAG_PREV_NO_AI)) {
                tag.putBoolean(TAG_PREV_NO_AI, mob.isNoAi());
            }
            mob.setNoAi(true);
            mob.getNavigation().stop();
        }

        target.setDeltaMovement(0.0, 0.0, 0.0);
        if (syncFreezeOverlay && !target.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    target,
                    new YetiFreezePayload(target.getId(), appliedDuration)
            );
        }
        return appliedDuration;
    }

    public static int applyWithEquipmentProtection(
            LivingEntity target,
            long nowTick,
            int durationTicks
    ) {
        return applyWithEquipmentProtection(
                target,
                nowTick,
                durationTicks,
                PresentationPolicy.SERVER_ONLY_STAGGER
        );
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
        long endTick = tag.getLong(TAG_END_TICK);
        if (!isWithinFreezeWindow(nowTick, endTick)) {
            clear(target, tag);
            return false;
        }
        return true;
    }

    public static void clear(LivingEntity target) {
        if (target == null) {
            return;
        }
        CompoundTag tag = target.getPersistentData();
        if (!tag.contains(TAG_END_TICK)
                && !tag.contains(TAG_PREV_NO_AI)) {
            return;
        }
        clear(target, tag);
    }

    static boolean isWithinFreezeWindow(long nowTick, long endTick) {
        return nowTick < endTick;
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (entity.level().isClientSide) {
            return;
        }
        long nowTick = entity.level().getGameTime();
        if (!isMovementLocked(entity, nowTick)) {
            return;
        }

        entity.setDeltaMovement(0.0, 0.0, 0.0);
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setNoAi(true);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)
                || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        CompoundTag tag = target.getPersistentData();
        if (!tag.getBoolean(TAG_SYNC_FREEZE_OVERLAY)) {
            return;
        }
        long nowTick = target.level().getGameTime();
        if (!isMovementLocked(target, nowTick)) {
            return;
        }
        PacketDistributor.sendToPlayer(
                observer,
                new YetiFreezePayload(
                        target.getId(),
                        remainingDurationTicks(
                                nowTick,
                                tag.getLong(TAG_END_TICK)
                        )
                )
        );
    }

    static int remainingDurationTicks(long nowTick, long endTick) {
        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(0L, endTick - nowTick)
        );
    }

    private static void clear(LivingEntity entity, CompoundTag tag) {
        if (entity instanceof Mob mob) {
            if (tag.contains(TAG_PREV_NO_AI)) {
                mob.setNoAi(tag.getBoolean(TAG_PREV_NO_AI));
            }
        }
        tag.remove(TAG_END_TICK);
        tag.remove(TAG_PREV_NO_AI);
        tag.remove(TAG_SYNC_FREEZE_OVERLAY);
    }
}
