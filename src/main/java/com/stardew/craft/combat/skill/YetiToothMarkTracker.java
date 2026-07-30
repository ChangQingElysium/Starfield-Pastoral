package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.YetiToothMarkPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class YetiToothMarkTracker {
    public static final String SKILL_ID = "yeti_tooth_mark";
    public static final int MARK_DURATION_TICKS = 60;
    public static final int SLOW_DURATION_TICKS = 60;
    public static final int SLOW_AMPLIFIER = 0;
    public static final int FREEZE_DURATION_TICKS = 40;

    private static final String TAG_END_TICK = "stardewcraft_yeti_tooth_mark_until";
    private static final String TAG_OWNER = "stardewcraft_yeti_tooth_mark_owner";
    private static final String TAG_USED = "stardewcraft_yeti_tooth_mark_used";

    private YetiToothMarkTracker() {}

    @SuppressWarnings("null")
    public static void apply(LivingEntity target, ServerPlayer owner, long nowTick, int durationTicks) {
        if (target == null || owner == null) {
            return;
        }
        CompoundTag tag = target.getPersistentData();
        tag.putLong(TAG_END_TICK, nowTick + durationTicks);
        tag.putUUID(TAG_OWNER, owner.getUUID());
        tag.putBoolean(TAG_USED, false);

        if (!target.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                new YetiToothMarkPayload(target.getId(), durationTicks)
            );

            if (target.level() instanceof ServerLevel serverLevel) {
                double x = target.getX();
                double y = target.getY() + target.getBbHeight() * 0.6;
                double z = target.getZ();
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                    x, y, z,
                    10, 0.35, 0.2, 0.35, 0.02);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    x, y, z,
                    6, 0.25, 0.15, 0.25, 0.02);
                serverLevel.playSound(null, target.blockPosition(),
                    net.minecraft.sounds.SoundEvents.GLASS_PLACE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
            }
        }
    }

    public static boolean isMarked(LivingEntity target, long nowTick) {
        CompoundTag tag = target.getPersistentData();
        if (!tag.contains(TAG_END_TICK)) {
            return false;
        }
        long endTick = tag.getLong(TAG_END_TICK);
        if (!isWithinMarkWindow(nowTick, endTick)) {
            clear(tag);
            return false;
        }
        return true;
    }

    public static boolean isEligibleFollowupSkill(String skillId) {
        return !SKILL_ID.equals(skillId);
    }

    static boolean isWithinMarkWindow(long nowTick, long endTick) {
        return nowTick < endTick;
    }

    public static boolean isMarkedBy(LivingEntity target, ServerPlayer owner, long nowTick) {
        if (!isMarked(target, nowTick)) {
            return false;
        }
        CompoundTag tag = target.getPersistentData();
        if (!tag.hasUUID(TAG_OWNER)) {
            return false;
        }
        UUID ownerId = tag.getUUID(TAG_OWNER);
        return ownerId.equals(owner.getUUID());
    }

    public static boolean consumeIfEligible(LivingEntity target, ServerPlayer owner, long nowTick) {
        if (!isMarkedBy(target, owner, nowTick)) {
            return false;
        }
        CompoundTag tag = target.getPersistentData();
        if (tag.getBoolean(TAG_USED)) {
            return false;
        }
        tag.putBoolean(TAG_USED, true);
        clear(tag);

        if (!target.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                new YetiToothMarkPayload(target.getId(), 0)
            );
        }
        return true;
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (entity.level().isClientSide) {
            return;
        }
        CompoundTag tag = entity.getPersistentData();
        if (!tag.contains(TAG_END_TICK)) {
            return;
        }
        long nowTick = entity.level().getGameTime();
        long endTick = tag.getLong(TAG_END_TICK);
        if (!isWithinMarkWindow(nowTick, endTick)) {
            clear(tag);
        }
    }

    private static void clear(CompoundTag tag) {
        tag.remove(TAG_END_TICK);
        tag.remove(TAG_OWNER);
        tag.remove(TAG_USED);
    }
}
