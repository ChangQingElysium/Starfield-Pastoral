package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection;
import com.stardew.craft.combat.network.GalaxyDaggerMarkPayload;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class GalaxyDaggerMarkTracker {

    private static final String TAG_END_TICK = "stardewcraft_galaxy_dagger_mark_until";
    private static final String TAG_OWNER = "stardewcraft_galaxy_dagger_mark_owner";

    private GalaxyDaggerMarkTracker() {}

    @SuppressWarnings("null")
    public static void apply(LivingEntity target, ServerPlayer owner, long nowTick, int durationTicks) {
        if (target == null || owner == null || durationTicks <= 0) {
            return;
        }
        EquipmentNegativeStatusProtection.Decision protection =
                EquipmentNegativeStatusProtection.decide(
                        target,
                        durationTicks
                );
        if (protection.resisted()) {
            return;
        }
        int appliedDuration = protection.durationTicks();
        CompoundTag tag = target.getPersistentData();
        tag.putLong(TAG_END_TICK, nowTick + appliedDuration);
        tag.putUUID(TAG_OWNER, owner.getUUID());

        if (!target.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                new GalaxyDaggerMarkPayload(target.getId(), appliedDuration)
            );

            if (target.level() instanceof ServerLevel serverLevel) {
                double x = target.getX();
                double y = target.getY() + target.getBbHeight() * 0.6;
                double z = target.getZ();
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    x, y, z,
                    10, 0.35, 0.25, 0.35, 0.02);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    x, y, z,
                    8, 0.3, 0.2, 0.3, 0.03);
                serverLevel.playSound(null, target.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.55f, 1.6f);
                serverLevel.playSound(null, target.blockPosition(),
                    SoundEvents.ENCHANTMENT_TABLE_USE,
                    SoundSource.PLAYERS, 0.35f, 1.2f);
            }
        }
    }

    public static boolean isMarkedBy(LivingEntity target, Player player, long nowTick) {
        if (!isMarked(target, nowTick)) {
            return false;
        }
        CompoundTag tag = target.getPersistentData();
        if (!tag.hasUUID(TAG_OWNER)) {
            return false;
        }
        UUID ownerId = tag.getUUID(TAG_OWNER);
        return ownerId.equals(player.getUUID());
    }

    public static boolean consumeIfEligible(LivingEntity target, Player player, long nowTick) {
        if (!isMarkedBy(target, player, nowTick)) {
            return false;
        }
        clearMark(target.getPersistentData());
        if (!target.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                new GalaxyDaggerMarkPayload(target.getId(), 0)
            );
        }
        return true;
    }

    public static boolean consumeDuringBegin(
            SkillInstance instance,
            LivingEntity target,
            Player player,
            long nowTick
    ) {
        if (!isMarkedBy(target, player, nowTick)) {
            return false;
        }
        CompoundTag tag = target.getPersistentData();
        long endTick = tag.getLong(TAG_END_TICK);
        UUID ownerId = tag.getUUID(TAG_OWNER);
        instance.registerBeginFailureCleanup(() -> {
            if (tag.contains(TAG_END_TICK)) {
                return;
            }
            tag.putLong(TAG_END_TICK, endTick);
            tag.putUUID(TAG_OWNER, ownerId);
            if (!target.level().isClientSide) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                        target,
                        new GalaxyDaggerMarkPayload(
                                target.getId(),
                                remainingDurationTicks(
                                        target.level().getGameTime(),
                                        endTick
                                )
                        )
                );
            }
        });
        return consumeIfEligible(target, player, nowTick);
    }

    private static boolean isMarked(LivingEntity target, long nowTick) {
        CompoundTag tag = target.getPersistentData();
        if (!tag.contains(TAG_END_TICK)) {
            return false;
        }
        long endTick = tag.getLong(TAG_END_TICK);
        if (nowTick >= endTick) {
            clearMark(tag);
            return false;
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
        if (nowTick >= endTick || !entity.isAlive()) {
            clearMark(tag);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)
                || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        long nowTick = target.level().getGameTime();
        if (!isMarked(target, nowTick)) {
            return;
        }
        PacketDistributor.sendToPlayer(
                observer,
                new GalaxyDaggerMarkPayload(
                        target.getId(),
                        remainingDurationTicks(
                                nowTick,
                                target.getPersistentData().getLong(TAG_END_TICK)
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

    private static void clearMark(CompoundTag tag) {
        tag.remove(TAG_END_TICK);
        tag.remove(TAG_OWNER);
    }
}
