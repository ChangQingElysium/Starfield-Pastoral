package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection;
import com.stardew.craft.combat.network.TideMarkPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class TideMarkTracker {
    public static final int MARK_DURATION_TICKS = 100;
    public static final String BONUS_SKILL_ID = "tide_mark_bonus";
    public static final float BONUS_DAMAGE_MULTIPLIER = 0.30F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    private static final String TAG_END_TICK = "stardewcraft_tide_mark_until";
    private static final String TAG_OWNER = "stardewcraft_tide_mark_owner";
    private static final String TAG_DIMENSION = "stardewcraft_tide_mark_dimension";

    private TideMarkTracker() {}

    @SuppressWarnings("null")
    public static void apply(
            LivingEntity target,
            ServerPlayer owner,
            long nowTick,
            int durationTicks
    ) {
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
        tag.putString(
                TAG_DIMENSION,
                target.level().dimension().location().toString()
        );

        sendMarkApplied(target, appliedDuration);
    }

    public static boolean isMarked(LivingEntity target, long nowTick) {
        if (target == null) {
            return false;
        }
        CompoundTag tag = target.getPersistentData();
        if (!tag.contains(TAG_END_TICK)) {
            return false;
        }
        long endTick = tag.getLong(TAG_END_TICK);
        if (isExpired(nowTick, endTick)) {
            clear(tag);
            return false;
        }
        return true;
    }

    public static boolean isMarkedBy(
            LivingEntity target,
            Player owner,
            long nowTick
    ) {
        if (owner == null || !isMarked(target, nowTick)) {
            return false;
        }
        CompoundTag tag = target.getPersistentData();
        if (!tag.hasUUID(TAG_OWNER)) {
            return false;
        }
        return matchesOwner(
                tag.getUUID(TAG_OWNER),
                owner.getUUID()
        );
    }

    public static SkillContext createBonusContext() {
        return SkillContext.builder()
            .skillId(BONUS_SKILL_ID)
            .tier(SkillContext.SkillTier.MINOR)
            .damageMultiplier(BONUS_DAMAGE_MULTIPLIER)
            .build();
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
        if (isExpired(nowTick, endTick)) {
            clear(tag);
            return;
        }
        if (shouldResyncDimension(
                tag.getString(TAG_DIMENSION),
                entity.level().dimension()
        )) {
            tag.putString(
                    TAG_DIMENSION,
                    entity.level().dimension().location().toString()
            );
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                entity,
                new TideMarkPayload(
                        entity.getId(),
                        remainingDurationTicks(nowTick, endTick)
                )
            );
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
        long endTick = target.getPersistentData().getLong(TAG_END_TICK);
        PacketDistributor.sendToPlayer(
                observer,
                new TideMarkPayload(
                        target.getId(),
                        remainingDurationTicks(nowTick, endTick)
                )
        );
    }

    static boolean isExpired(long nowTick, long endTick) {
        return nowTick >= endTick;
    }

    static int remainingDurationTicks(long nowTick, long endTick) {
        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(0L, endTick - nowTick)
        );
    }

    static boolean matchesOwner(UUID markOwner, UUID playerId) {
        return markOwner.equals(playerId);
    }

    static boolean shouldResyncDimension(
            String storedDimension,
            ResourceKey<Level> actualDimension
    ) {
        return !actualDimension.location().toString().equals(storedDimension);
    }

    private static void sendMarkApplied(
            LivingEntity target,
            int durationTicks
    ) {
        if (!target.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                new TideMarkPayload(target.getId(), durationTicks)
            );

            if (target.level() instanceof ServerLevel serverLevel) {
                double x = target.getX();
                double y = target.getY() + target.getBbHeight() * 0.6;
                double z = target.getZ();
                serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.BUBBLE,
                    x,
                    y,
                    z,
                    12,
                    0.35,
                    0.25,
                    0.35,
                    0.02
                );
                serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    x,
                    y,
                    z,
                    8,
                    0.3,
                    0.2,
                    0.3,
                    0.02
                );
                serverLevel.playSound(
                    null,
                    target.blockPosition(),
                    net.minecraft.sounds.SoundEvents.TRIDENT_THROW.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    0.5F,
                    1.2F
                );
                serverLevel.playSound(
                    null,
                    target.blockPosition(),
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    0.4F,
                    1.6F
                );
            }
        }
    }

    private static void clear(CompoundTag tag) {
        tag.remove(TAG_END_TICK);
        tag.remove(TAG_OWNER);
        tag.remove(TAG_DIMENSION);
    }
}
