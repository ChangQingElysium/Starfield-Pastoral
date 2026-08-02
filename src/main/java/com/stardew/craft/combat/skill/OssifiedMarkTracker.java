package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection;
import com.stardew.craft.combat.network.OssifiedMarkPayload;
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
public final class OssifiedMarkTracker {
    public static final float CRIT_CHANCE_BONUS = 0.10F;
    public static final float BONUS_DAMAGE_MULTIPLIER = 1.0F;
    public static final int UNTRIGGERED_COOLDOWN_TOTAL_TICKS = 80;

    private static final String TAG_END_TICK = "stardewcraft_ossified_mark_until";
    private static final String TAG_OWNER = "stardewcraft_ossified_mark_owner";
    private static final String TAG_BONUS_USED = "stardewcraft_ossified_mark_bonus_used";
    private static final String TAG_START_TICK = "stardewcraft_ossified_mark_start";
    private static final String TAG_UNTRIGGERED_COOLDOWN_END =
            "stardewcraft_ossified_mark_cooldown_end";
    private static final String WEAPON_ID = "ossified_blade";
    private static final String SKILL_ID = "ossified_mark";

    private OssifiedMarkTracker() {}

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
        tag.putBoolean(TAG_BONUS_USED, false);
        tag.putLong(TAG_START_TICK, nowTick);
        tag.putLong(
                TAG_UNTRIGGERED_COOLDOWN_END,
                nowTick + WeaponSkillCooldowns.adjustedDuration(
                        owner,
                        UNTRIGGERED_COOLDOWN_TOTAL_TICKS
                )
        );

        if (!target.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                new OssifiedMarkPayload(target.getId(), appliedDuration)
            );

            if (target.level() instanceof ServerLevel serverLevel) {
                double x = target.getX();
                double y = target.getY() + target.getBbHeight() * 0.6;
                double z = target.getZ();
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH,
                    x, y, z,
                    10, 0.35, 0.2, 0.35, 0.02);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                    x, y, z,
                    6, 0.25, 0.18, 0.25, 0.01);
                serverLevel.playSound(null, target.blockPosition(),
                    SoundEvents.BONE_BLOCK_PLACE,
                    SoundSource.PLAYERS, 0.9f, 1.05f);
                serverLevel.playSound(null, target.blockPosition(),
                    SoundEvents.SOUL_ESCAPE.value(),
                    SoundSource.PLAYERS, 0.8f, 0.9f);
            }
        }
    }

    public static boolean isMarked(LivingEntity target, long nowTick) {
        CompoundTag tag = target.getPersistentData();
        if (!tag.contains(TAG_END_TICK)) {
            return false;
        }
        long endTick = tag.getLong(TAG_END_TICK);
        if (isExpired(nowTick, endTick)) {
            expireMark(target, tag, nowTick);
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

    public static boolean consumeBonusIfEligible(LivingEntity target, Player player, long nowTick) {
        if (!isMarkedBy(target, player, nowTick)) {
            return false;
        }
        CompoundTag tag = target.getPersistentData();
        if (tag.getBoolean(TAG_BONUS_USED)) {
            return false;
        }
        tag.putBoolean(TAG_BONUS_USED, true);
        return true;
    }

    public static long getStartTick(LivingEntity target) {
        CompoundTag tag = target.getPersistentData();
        if (!tag.contains(TAG_START_TICK)) {
            return -1L;
        }
        return tag.getLong(TAG_START_TICK);
    }

    public static float getCritChanceBonus(LivingEntity target, Player player, long nowTick) {
        return isMarkedBy(target, player, nowTick) ? CRIT_CHANCE_BONUS : 0.0f;
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
            expireMark(entity, tag, nowTick);
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
                new OssifiedMarkPayload(
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

    static boolean isExpired(long nowTick, long endTick) {
        return nowTick >= endTick;
    }

    static int untriggeredCooldownRemaining(long startTick, long nowTick) {
        long desiredEnd = startTick + UNTRIGGERED_COOLDOWN_TOTAL_TICKS;
        return (int) Math.max(0L, desiredEnd - nowTick);
    }

    private static void expireMark(
            LivingEntity entity,
            CompoundTag tag,
            long nowTick
    ) {
        handleExpire(entity, tag, nowTick);
        clearMark(tag);
    }

    @SuppressWarnings("null")
    private static void handleExpire(LivingEntity entity, CompoundTag tag, long nowTick) {
        if (tag.getBoolean(TAG_BONUS_USED)) {
            return;
        }
        if (!tag.hasUUID(TAG_OWNER)) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerPlayer owner = serverLevel.getServer()
            .getPlayerList()
            .getPlayer(tag.getUUID(TAG_OWNER));
        if (owner == null) {
            return;
        }
        long cooldownEndTick = tag.contains(
                TAG_UNTRIGGERED_COOLDOWN_END
        )
                ? tag.getLong(TAG_UNTRIGGERED_COOLDOWN_END)
                : nowTick + WeaponSkillCooldowns.adjustedDuration(
                        owner,
                        untriggeredCooldownRemaining(
                                tag.getLong(TAG_START_TICK),
                                nowTick
                        )
                );
        WeaponSkillCooldowns.setCooldownUntil(
                owner,
                WEAPON_ID,
                SKILL_ID,
                nowTick,
                cooldownEndTick
        );
    }

    private static void clearMark(CompoundTag tag) {
        tag.remove(TAG_END_TICK);
        tag.remove(TAG_OWNER);
        tag.remove(TAG_BONUS_USED);
        tag.remove(TAG_START_TICK);
        tag.remove(TAG_UNTRIGGERED_COOLDOWN_END);
    }
}
