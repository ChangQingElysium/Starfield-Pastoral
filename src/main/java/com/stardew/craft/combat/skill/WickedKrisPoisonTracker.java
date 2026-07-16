package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.WickedKrisPoisonStatusPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class WickedKrisPoisonTracker {

    private static final String TAG_END_TICK = "stardewcraft_wicked_kris_poison_until";
    private static final String TAG_OWNER = "stardewcraft_wicked_kris_poison_owner";
    private static final String TAG_STACKS = "stardewcraft_wicked_kris_poison_stacks";
    private static final String TAG_NEXT_TICK = "stardewcraft_wicked_kris_poison_next_tick";
    private static final String TAG_DETONATE_TICK = "stardewcraft_wicked_kris_poison_detonate";
    private static final String TAG_LAST_X = "stardewcraft_wicked_kris_poison_last_x";
    private static final String TAG_LAST_Y = "stardewcraft_wicked_kris_poison_last_y";
    private static final String TAG_LAST_Z = "stardewcraft_wicked_kris_poison_last_z";

    private static final int MAX_STACKS = 5;
    private static final long DOT_INTERVAL_TICKS = 20L;
    private static final float STACK_DAMAGE_RATIO = 0.10f;
    private static final int DETONATE_DELAY_TICKS = 60;
    private static final float DETONATE_MULTIPLIER = 1.5f;
    private static final float DETONATE_RADIUS = 3.5f;

    private static final Map<UUID, DetonationState> DETONATIONS = new ConcurrentHashMap<>();

    private WickedKrisPoisonTracker() {}

    private static final class DetonationState {
        private final UUID targetId;
        private UUID ownerId;
        private ResourceKey<Level> dimension;
        private Vec3 lastPos;
        private long detonateTick;
        private long poisonEndTick;
        private int stacks;

        private DetonationState(UUID targetId) {
            this.targetId = targetId;
        }
    }

    public static void applyPoison(LivingEntity target, ServerPlayer owner, long nowTick,
                                   int durationTicks, int stacks, boolean scheduleDetonation) {
        if (target == null || owner == null || durationTicks <= 0) {
            return;
        }
        int clampedStacks = Mth.clamp(stacks, 1, MAX_STACKS);
        CompoundTag tag = target.getPersistentData();
        tag.putLong(TAG_END_TICK, nowTick + durationTicks);
        tag.putUUID(TAG_OWNER, owner.getUUID());
        tag.putInt(TAG_STACKS, clampedStacks);
        tag.putLong(TAG_NEXT_TICK, nowTick + DOT_INTERVAL_TICKS);
        updateLastPos(tag, target);

        if (scheduleDetonation) {
            long detonateTick = nowTick + DETONATE_DELAY_TICKS;
            tag.putLong(TAG_DETONATE_TICK, detonateTick);
            registerDetonation(target, owner, detonateTick, nowTick + durationTicks, clampedStacks);
            sendStatus(owner, clampedStacks, durationTicks, (int) (detonateTick - nowTick), DETONATE_DELAY_TICKS);
        } else {
            sendStatus(owner, clampedStacks, durationTicks, -1, 0);
        }

        if (target.level() instanceof ServerLevel serverLevel) {
            double x = target.getX();
            double y = target.getY() + target.getBbHeight() * 0.6;
            double z = target.getZ();
            serverLevel.sendParticles(ParticleTypes.WITCH,
                x, y, z,
                12, 0.35, 0.25, 0.35, 0.02);
            serverLevel.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                x, y, z,
                8, 0.35, 0.25, 0.35, 0.01);
            serverLevel.playSound(null, target.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.6f, 1.25f);
        }
    }

    public static boolean isPoisoned(LivingEntity target, long nowTick) {
        CompoundTag tag = target.getPersistentData();
        if (!tag.contains(TAG_END_TICK)) {
            return false;
        }
        long endTick = tag.getLong(TAG_END_TICK);
        if (nowTick >= endTick) {
            clearPoison(target, tag);
            return false;
        }
        return true;
    }

    public static boolean isPoisonedBy(LivingEntity target, Player player, long nowTick) {
        if (!isPoisoned(target, nowTick)) {
            return false;
        }
        CompoundTag tag = target.getPersistentData();
        if (!tag.hasUUID(TAG_OWNER)) {
            return false;
        }
        UUID ownerId = tag.getUUID(TAG_OWNER);
        return ownerId.equals(player.getUUID());
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
            clearPoison(entity, tag);
            return;
        }

        updateLastPos(tag, entity);
        refreshDetonationState(entity, tag);

        long nextTick = tag.getLong(TAG_NEXT_TICK);
        if (nowTick >= nextTick) {
            tag.putLong(TAG_NEXT_TICK, nowTick + DOT_INTERVAL_TICKS);
            applyDotTick(entity, tag, nowTick);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (DETONATIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        DETONATIONS.values().removeIf(state -> detonateIfReady(server, state));
    }

    private static boolean detonateIfReady(MinecraftServer server, DetonationState state) {
        ServerLevel level = state.dimension != null ? server.getLevel(state.dimension) : null;
        if (level == null) {
            return true;
        }
        if (com.stardew.craft.time.StardewTimePauseService.shouldPauseLevel(level)) {
            return false;
        }
        long nowTick = level.getGameTime();
        if (nowTick < state.detonateTick) {
            return false;
        }
        if (state.ownerId == null) {
            return true;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(state.ownerId);
        if (owner == null) {
            return true;
        }

        Vec3 center = state.lastPos;
        LivingEntity targetEntity = null;
        if (state.targetId != null) {
            var entity = level.getEntity(state.targetId);
            if (entity instanceof LivingEntity living) {
                targetEntity = living;
                center = living.position();
            }
        }
        if (center == null) {
            return true;
        }

        long remainingTicks = Math.max(0L, state.poisonEndTick - nowTick);
        int remainingJumps = (int) Math.max(0L, (remainingTicks + DOT_INTERVAL_TICKS - 1L) / DOT_INTERVAL_TICKS);
        float damageMultiplier = remainingJumps * state.stacks * STACK_DAMAGE_RATIO * DETONATE_MULTIPLIER;

        AABB box = new AABB(
            center.x - DETONATE_RADIUS, center.y - DETONATE_RADIUS, center.z - DETONATE_RADIUS,
            center.x + DETONATE_RADIUS, center.y + DETONATE_RADIUS, center.z + DETONATE_RADIUS
        );
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
            entity -> entity.isPickable() && entity.isAlive() && entity != owner);

        if (damageMultiplier > 0.0f) {
            for (LivingEntity target : targets) {
                SkillContext context = SkillContext.builder()
                    .skillId("wicked_kris_poison_burst")
                    .tier(SkillContext.SkillTier.MAJOR)
                    .damageMultiplier(damageMultiplier)
                    .build();
                WeaponSkillContextStore.setPending(owner, context, nowTick + 5);

                target.invulnerableTime = 0;
                target.hurtTime = 0;
                target.hurt(owner.damageSources().playerAttack(owner), 1.0F);
            }
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            center.x, center.y + 0.2, center.z,
            1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.WITCH,
            center.x, center.y + 0.35, center.z,
            28, 0.6, 0.3, 0.6, 0.02);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
            center.x, center.y + 0.35, center.z,
            20, 0.55, 0.3, 0.55, 0.01);
        level.sendParticles(ParticleTypes.CRIT,
            center.x, center.y + 0.3, center.z,
            16, 0.45, 0.2, 0.45, 0.08);
        level.playSound(null, net.minecraft.core.BlockPos.containing(center),
            SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9f, 1.05f);
        level.playSound(null, net.minecraft.core.BlockPos.containing(center),
            SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.7f, 0.9f);

        if (targetEntity != null) {
            clearPoison(targetEntity, targetEntity.getPersistentData());
        } else {
            sendStatus(owner, 0, 0, 0, 0);
        }

        return true;
    }

    private static void applyDotTick(LivingEntity target, CompoundTag tag, long nowTick) {
        if (!tag.hasUUID(TAG_OWNER)) {
            clearPoison(target, tag);
            return;
        }
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player ownerPlayer = serverLevel.getPlayerByUUID(tag.getUUID(TAG_OWNER));
        if (!(ownerPlayer instanceof ServerPlayer owner)) {
            clearPoison(target, tag);
            return;
        }

        int stacks = Math.max(0, tag.getInt(TAG_STACKS));
        if (stacks <= 0) {
            clearPoison(target, tag);
            return;
        }

        float damageMultiplier = stacks * STACK_DAMAGE_RATIO;
        SkillContext context = SkillContext.builder()
            .skillId("wicked_kris_poison_dot")
            .tier(SkillContext.SkillTier.MINOR)
            .damageMultiplier(damageMultiplier)
            .build();
        WeaponSkillContextStore.setPending(owner, context, nowTick + 5);

        target.invulnerableTime = 0;
        target.hurtTime = 0;
        target.hurt(owner.damageSources().playerAttack(owner), 1.0F);

        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.55;
        double z = target.getZ();
        serverLevel.sendParticles(ParticleTypes.WITCH,
            x, y, z,
            6, 0.25, 0.2, 0.25, 0.01);
        serverLevel.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
            x, y, z,
            4, 0.2, 0.15, 0.2, 0.01);
        serverLevel.playSound(null, target.blockPosition(),
            SoundEvents.SPIDER_AMBIENT,
            SoundSource.PLAYERS, 0.45f, 1.35f);
    }

    private static void updateLastPos(CompoundTag tag, LivingEntity target) {
        tag.putDouble(TAG_LAST_X, target.getX());
        tag.putDouble(TAG_LAST_Y, target.getY());
        tag.putDouble(TAG_LAST_Z, target.getZ());
    }

    private static void refreshDetonationState(LivingEntity target, CompoundTag tag) {
        if (!tag.contains(TAG_DETONATE_TICK)) {
            return;
        }
        DetonationState state = DETONATIONS.get(target.getUUID());
        if (state == null) {
            return;
        }
        state.lastPos = target.position();
        state.poisonEndTick = tag.getLong(TAG_END_TICK);
        state.stacks = Math.max(0, tag.getInt(TAG_STACKS));
    }

    private static void registerDetonation(LivingEntity target, ServerPlayer owner,
                                           long detonateTick, long poisonEndTick, int stacks) {
        DetonationState state = DETONATIONS.computeIfAbsent(target.getUUID(), DetonationState::new);
        state.ownerId = owner.getUUID();
        state.dimension = target.level().dimension();
        state.lastPos = target.position();
        state.detonateTick = detonateTick;
        state.poisonEndTick = poisonEndTick;
        state.stacks = stacks;
    }

    private static void clearPoison(LivingEntity target, CompoundTag tag) {
        UUID ownerId = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        tag.remove(TAG_END_TICK);
        tag.remove(TAG_OWNER);
        tag.remove(TAG_STACKS);
        tag.remove(TAG_NEXT_TICK);
        tag.remove(TAG_DETONATE_TICK);
        tag.remove(TAG_LAST_X);
        tag.remove(TAG_LAST_Y);
        tag.remove(TAG_LAST_Z);
        DETONATIONS.remove(target.getUUID());

        if (ownerId != null && target.level() instanceof ServerLevel serverLevel) {
            Player owner = serverLevel.getPlayerByUUID(ownerId);
            if (owner instanceof ServerPlayer serverPlayer) {
                sendStatus(serverPlayer, 0, 0, 0, 0);
            }
        }
    }

    private static void sendStatus(ServerPlayer owner, int stacks, int durationTicks,
                                   int detonateRemainingTicks, int detonateTotalTicks) {
        PacketDistributor.sendToPlayer(owner,
            new WickedKrisPoisonStatusPayload(stacks, durationTicks, detonateRemainingTicks, detonateTotalTicks)
        );
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        DETONATIONS.remove(playerId);
    }
}
