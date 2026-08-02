package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection;
import com.stardew.craft.combat.network.ElfBladeMarkPayload;
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
public final class ElfBladeMarkTracker {

    private static final String TAG_END_TICK = "stardewcraft_elf_blade_mark_until";
    private static final String TAG_OWNER = "stardewcraft_elf_blade_mark_owner";
    private static final String TAG_STACKS = "stardewcraft_elf_blade_mark_stacks";
    private static final int MAX_STACKS = 10;

    private ElfBladeMarkTracker() {}

    @SuppressWarnings("null")
    public static void apply(LivingEntity target, ServerPlayer owner, long nowTick, int durationTicks, int stacksAdded) {
        if (target == null || owner == null || durationTicks <= 0 || stacksAdded <= 0) {
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
        if (!tag.hasUUID(TAG_OWNER) || !owner.getUUID().equals(tag.getUUID(TAG_OWNER))) {
            tag.putInt(TAG_STACKS, 0);
        }

        int stacks = Math.max(0, tag.getInt(TAG_STACKS));
        stacks = Math.min(MAX_STACKS, stacks + stacksAdded);
        tag.putInt(TAG_STACKS, stacks);
        tag.putLong(TAG_END_TICK, nowTick + appliedDuration);
        tag.putUUID(TAG_OWNER, owner.getUUID());
        target.setGlowingTag(true);

        if (!target.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                new ElfBladeMarkPayload(target.getId(), appliedDuration, stacks)
            );

            if (target.level() instanceof ServerLevel serverLevel) {
                double x = target.getX();
                double y = target.getY() + target.getBbHeight() * 0.6;
                double z = target.getZ();
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    x, y, z,
                    10, 0.35, 0.2, 0.35, 0.02);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW,
                    x, y, z,
                    6, 0.25, 0.15, 0.25, 0.01);
                serverLevel.playSound(null, target.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.6f, 1.4f);
            }
        }
    }

    public static boolean isMarked(LivingEntity target, long nowTick) {
        CompoundTag tag = target.getPersistentData();
        if (!tag.contains(TAG_END_TICK)) {
            return false;
        }
        long endTick = tag.getLong(TAG_END_TICK);
        if (nowTick >= endTick) {
            clear(target);
            return false;
        }
        return true;
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

    public static float getCritChanceBonus(LivingEntity target, Player player, long nowTick) {
        if (!isMarkedBy(target, player, nowTick)) {
            return 0.0f;
        }
        CompoundTag tag = target.getPersistentData();
        int stacks = Math.max(0, tag.getInt(TAG_STACKS));
        return stacks * 0.05f;
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
            clear(entity);
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
        CompoundTag tag = target.getPersistentData();
        PacketDistributor.sendToPlayer(
                observer,
                new ElfBladeMarkPayload(
                        target.getId(),
                        remainingDurationTicks(nowTick, tag.getLong(TAG_END_TICK)),
                        Math.max(0, tag.getInt(TAG_STACKS))
                )
        );
    }

    static int remainingDurationTicks(long nowTick, long endTick) {
        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(0L, endTick - nowTick)
        );
    }

    private static void clear(LivingEntity entity) {
        CompoundTag tag = entity.getPersistentData();
        tag.remove(TAG_END_TICK);
        tag.remove(TAG_OWNER);
        tag.remove(TAG_STACKS);
        entity.setGlowingTag(false);
    }
}
