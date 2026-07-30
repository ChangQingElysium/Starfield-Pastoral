package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class YetiFreezeTracker {

    private static final String TAG_END_TICK = "stardewcraft_yeti_freeze_until";
    private static final String TAG_PREV_NO_AI = "stardewcraft_yeti_freeze_prev_no_ai";

    private YetiFreezeTracker() {}

    public static void apply(LivingEntity target, long nowTick, int durationTicks) {
        if (target == null) {
            return;
        }
        CompoundTag tag = target.getPersistentData();
        tag.putLong(TAG_END_TICK, nowTick + durationTicks);

        if (target instanceof Mob mob) {
            if (!tag.contains(TAG_PREV_NO_AI)) {
                tag.putBoolean(TAG_PREV_NO_AI, mob.isNoAi());
            }
            mob.setNoAi(true);
            mob.getNavigation().stop();
        }

        target.setDeltaMovement(0.0, 0.0, 0.0);
    }

    private static boolean isFrozen(LivingEntity target, long nowTick) {
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
        if (!isFrozen(entity, nowTick)) {
            return;
        }

        entity.setDeltaMovement(0.0, 0.0, 0.0);
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setNoAi(true);
        }
    }

    private static void clear(LivingEntity entity, CompoundTag tag) {
        if (entity instanceof Mob mob) {
            boolean prev = tag.getBoolean(TAG_PREV_NO_AI);
            mob.setNoAi(prev);
        }
        tag.remove(TAG_END_TICK);
        tag.remove(TAG_PREV_NO_AI);
    }
}
