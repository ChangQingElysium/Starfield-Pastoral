package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class BoneFractureTracker {

    private static final String TAG_END_TICK = "stardewcraft_bone_fracture_until";
    private static final String TAG_LAST_PARTICLE = "stardewcraft_bone_fracture_particle";
    private static final int PARTICLE_INTERVAL = 6;

    private BoneFractureTracker() {}

    public static void apply(ServerLevel level, LivingEntity target, long nowTick, int durationTicks) {
        CompoundTag tag = target.getPersistentData();
        tag.putLong(TAG_END_TICK, nowTick + durationTicks);
        tag.putLong(TAG_LAST_PARTICLE, nowTick - PARTICLE_INTERVAL);
        spawnTrace(target);
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
        if (nowTick >= endTick) {
            tag.remove(TAG_END_TICK);
            tag.remove(TAG_LAST_PARTICLE);
            return;
        }

        long last = tag.getLong(TAG_LAST_PARTICLE);
        if (nowTick - last < PARTICLE_INTERVAL) {
            return;
        }
        tag.putLong(TAG_LAST_PARTICLE, nowTick);

        if (entity.level() instanceof ServerLevel serverLevel) {
            spawnTrace(entity);
        }
    }

    private static void spawnTrace(LivingEntity target) {
        long end = target.getPersistentData().getLong(TAG_END_TICK);
        com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
                new com.stardew.craft.combat.network.BoneFractureTracePayload(target.getId(), end,
                        (int)Math.max(0, end - target.level().getGameTime())));
    }
}
