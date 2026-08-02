package com.stardew.craft.item.trinket;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.CombatTargetRules;
import com.stardew.craft.combat.DimensionDamageMapper;
import com.stardew.craft.entity.effect.IceSpineEffectEntity;
import com.stardew.craft.entity.trinket.FairyCompanionEntity;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class TrinketEffectHandler {
    private static final String STATE_TAG = "StardewTrinketEffects";
    private static final String TAG_LAST_TYPE = "LastType";
    private static final String TAG_QUIVER_TIMER = "QuiverTimerMs";
    private static final String TAG_ICE_TIMER = "IceTimerMs";
    private static final String TAG_FAIRY_TIMER = "FairyTimerMs";
    private static final String TAG_FAIRY_COMBAT_DAMAGE =
            "FairyDamageSinceLastHeal";
    private static final String TAG_MAGIC_QUIVER_DAMAGE =
            "StardewMagicQuiverDamage";
    private static final String TAG_PARROT_UUID = "ParrotUuid";
    private static final String TAG_TRINKET_PARROT = "StardewTrinketParrot";
    private static final String TAG_FROG_NEXT_CHECK_TICK = "FrogNextCheckTick";
    private static final int TICK_MS = 50;
    private static final double MAGIC_QUIVER_RANGE = 500.0D / 64.0D;
    private static final double ICE_ROD_TARGET_RANGE = 600.0D / 64.0D;
    private static final double FROG_RANGE = 300.0D / 64.0D;
    private static final long FROG_INITIAL_DELAY_TICKS = 12000L / TICK_MS;
    private static final long FROG_CHECK_INTERVAL_TICKS = 2000L / TICK_MS;
    private static final long FROG_FULLNESS_TICKS = 12000L / TICK_MS;

    private TrinketEffectHandler() {
    }

    public static void tick(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        ItemStack stack = data.getEquippedTrinket();
        TrinketType type = StardewTrinketItem.getType(stack);
        CompoundTag state = getState(player);
        handleTypeTransition(player, state, type);

        if (type != TrinketType.PARROT_EGG) {
            removeParrot(player, state);
        }
        if (type != TrinketType.FAIRY_BOX) {
            removeFairies(player);
        }

        if (type == null) {
            return;
        }

        switch (type) {
            case BASILISK_PAW -> tickBasiliskPaw(player);
            case MAGIC_QUIVER -> tickMagicQuiver(player, stack, state);
            case ICE_ROD -> tickIceRod(player, stack, state);
            case FAIRY_BOX -> tickFairyBox(player, data, stack, state);
            case PARROT_EGG -> tickParrotEgg(player, stack, state);
            case FROG_EGG -> tickFrogEgg(player, state);
            default -> {
            }
        }
    }

    public static void onReceiveDamage(ServerPlayer player, int stardewDamage) {
        if (stardewDamage <= 0 || equippedType(player) != TrinketType.FAIRY_BOX) {
            return;
        }
        recordFairyCombatDamage(player, stardewDamage);
    }

    public static boolean cancelBasiliskDamage(ServerPlayer player, DamageSource source) {
        if (equippedType(player) != TrinketType.BASILISK_PAW) {
            return false;
        }
        tickBasiliskPaw(player);
        if (source.is(DamageTypes.ON_FIRE)
                || source.is(DamageTypes.FREEZE)
                || source.is(DamageTypes.WITHER)
                || source.is(Tags.DamageTypes.IS_POISON)) {
            player.clearFire();
            if (source.is(DamageTypes.FREEZE)) {
                player.setTicksFrozen(0);
            }
            return true;
        }
        return false;
    }

    public static void onDamageMonster(ServerPlayer player, LivingEntity target, int stardewDamage, boolean criticalHit) {
        TrinketType type = equippedType(player);
        if (type == null
                || target == null
                || target instanceof ServerPlayer
                || !CombatTargetRules.isCombatMonster(target)) {
            return;
        }

        if (type == TrinketType.FAIRY_BOX && stardewDamage > 0) {
            recordFairyCombatDamage(player, stardewDamage);
        }

        if (type == TrinketType.IRIDIUM_SPUR && criticalHit) {
            int seconds = Math.max(1, StardewTrinketItem.getGeneralStat(PlayerDataManager.getPlayerData(player).getEquippedTrinket(), player));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, seconds * 20, 0, false, true, true));
        }
    }

    public static void onPlayerLogout(ServerPlayer player) {
        CompoundTag state = getState(player);
        removeParrot(player, state);
        removeFairies(player);
        state.remove(TAG_LAST_TYPE);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMagicQuiverIncomingDamage(
            LivingIncomingDamageEvent event
    ) {
        if (!(event.getSource().getDirectEntity()
                instanceof AbstractArrow arrow)) {
            return;
        }
        CompoundTag projectileData = arrow.getPersistentData();
        if (!projectileData.contains(TAG_MAGIC_QUIVER_DAMAGE)) {
            return;
        }
        event.setAmount(projectileData.getFloat(TAG_MAGIC_QUIVER_DAMAGE));
    }

    @SubscribeEvent(
            priority = EventPriority.LOWEST,
            receiveCanceled = true
    )
    public static void onMobKilled(LivingDeathEvent event) {
        if (event.isCanceled()
                || !(event.getSource().getEntity()
                instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity killed = event.getEntity();
        if (!(killed instanceof Enemy) || killed instanceof ServerPlayer || equippedType(player) != TrinketType.PARROT_EGG) {
            return;
        }

        ItemStack stack = PlayerDataManager.getPlayerData(player).getEquippedTrinket();
        int generalStat = StardewTrinketItem.getGeneralStat(stack, player);
        double chance = (generalStat + 1) * 0.1D;
        int coins = 0;
        while (player.getRandom().nextDouble() <= chance) {
            coins++;
        }
        if (coins <= 0) {
            return;
        }

        com.stardew.craft.player.PlayerStardewDataAPI.addMoney(player, coins);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.COIN.get(), SoundSource.PLAYERS, 0.65F, 1.1F);
    }

    private static void tickBasiliskPaw(ServerPlayer player) {
        player.clearFire();
        List<Holder<MobEffect>> negativeEffects = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            Holder<MobEffect> holder = effect.getEffect();
            if (!holder.value().isBeneficial()) {
                negativeEffects.add(holder);
            }
        }
        for (Holder<MobEffect> effect : negativeEffects) {
            player.removeEffect(effect);
        }
    }

    private static void tickMagicQuiver(ServerPlayer player, ItemStack stack, CompoundTag state) {
        int delay = Math.max(1, StardewTrinketItem.getProjectileDelayMs(stack, player));
        int timer = state.getInt(TAG_QUIVER_TIMER) + TICK_MS;
        if (timer < delay) {
            state.putInt(TAG_QUIVER_TIMER, timer);
            return;
        }
        state.putInt(TAG_QUIVER_TIMER, 0);

        ServerLevel level = player.serverLevel();
        Optional<LivingEntity> target = findClosestMonster(player, MAGIC_QUIVER_RANGE);
        if (target.isEmpty()) {
            return;
        }

        int minDamage = StardewTrinketItem.getMinDamage(stack, player);
        int maxDamage = StardewTrinketItem.getMaxDamage(stack, player);
        int stardewDamage = player.getRandom().nextIntBetweenInclusive(minDamage, maxDamage);
        float actualDamage = DimensionDamageMapper.mapDamage(stardewDamage, DimensionDamageMapper.isInStardewDimension(player));
        Vec3 from = player.getEyePosition().subtract(0.0D, 0.25D, 0.0D);
        Vec3 to = target.get().getBoundingBox().getCenter().subtract(from);

        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
        arrow.setPos(from.x, from.y, from.z);
        // AbstractArrow multiplies base damage by its current velocity. Keep a
        // small positive trigger value here and restore the authored damage at
        // LivingIncomingDamageEvent, before armor and other reductions.
        arrow.setBaseDamage(1.0D);
        arrow.getPersistentData().putFloat(
                TAG_MAGIC_QUIVER_DAMAGE,
                actualDamage
        );
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setNoGravity(true);
        arrow.shoot(to.x, to.y, to.z, 2.0F, 0.0F);
        level.addFreshEntity(arrow);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.75F, 1.25F);
    }

    private static void tickIceRod(ServerPlayer player, ItemStack stack, CompoundTag state) {
        int delay = Math.max(1, StardewTrinketItem.getProjectileDelayMs(stack, player));
        int timer = state.getInt(TAG_ICE_TIMER) + TICK_MS;
        if (timer < delay) {
            state.putInt(TAG_ICE_TIMER, timer);
            return;
        }
        state.putInt(TAG_ICE_TIMER, 0);

        Optional<LivingEntity> target = findClosestMonster(player, ICE_ROD_TARGET_RANGE);
        if (target.isEmpty()) {
            return;
        }

        Vec3 start = player.getEyePosition().subtract(0.0D, 0.35D, 0.0D);
        Vec3 direction = target.get().getBoundingBox().getCenter().subtract(start).normalize();
        int freezeTicks = Math.max(1, StardewTrinketItem.getFreezeTimeMs(stack, player) / TICK_MS);
        player.serverLevel().addFreshEntity(new IceSpineEffectEntity(player.level(), player, start, direction, freezeTicks));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.FIREBALL.get(), SoundSource.PLAYERS, 0.75F, 1.05F);
    }

    private static void tickFairyBox(ServerPlayer player, PlayerStardewData data, ItemStack stack, CompoundTag state) {
        ensureFairy(player);

        int level = Math.max(1, StardewTrinketItem.getFairyLevel(stack, player));
        int delay = 5000 - level * 300;
        int timer = state.getInt(TAG_FAIRY_TIMER) + TICK_MS;
        if (timer < delay) {
            state.putInt(TAG_FAIRY_TIMER, timer);
            return;
        }
        state.putInt(TAG_FAIRY_TIMER, 0);

        int damageSinceLastHeal = state.getInt(TAG_FAIRY_COMBAT_DAMAGE);
        if (damageSinceLastHeal < 0) {
            return;
        }

        boolean inStardewDimension = DimensionDamageMapper.isInStardewDimension(player);
        int maxHealth = inStardewDimension ? data.getMaxHealth() : Math.max(1, Math.round(player.getMaxHealth() * DimensionDamageMapper.getHealthRatio()));
        int currentHealth = inStardewDimension ? data.getHealth() : Math.round(player.getHealth() * DimensionDamageMapper.getHealthRatio());
        if (currentHealth >= maxHealth) {
            return;
        }

        int healAmount = (int) Math.min(Math.pow(damageSinceLastHeal, 0.33000001311302185D), maxHealth / 10.0F);
        healAmount = (int) (healAmount * (0.7F + level * 0.1F));
        if (healAmount > 0) {
            int variance = Math.max(0, (int) (healAmount * 0.25F));
            healAmount += player.getRandom().nextIntBetweenInclusive(-variance, variance);
        }
        if (healAmount <= 0) {
            return;
        }

        if (inStardewDimension) {
            data.setHealth(Math.min(maxHealth, currentHealth + healAmount));
            PlayerDataEventHandler.syncPlayerVitals(player, data);
        } else {
            player.heal(Math.max(1.0F, healAmount / DimensionDamageMapper.getHealthRatio()));
        }
        state.putInt(TAG_FAIRY_COMBAT_DAMAGE, 0);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.FAIRY_HEAL.get(), SoundSource.PLAYERS, 0.75F, 1.0F);
    }

    private static void tickFrogEgg(ServerPlayer player, CompoundTag state) {
        long now = player.serverLevel().getGameTime();
        if (!state.contains(TAG_FROG_NEXT_CHECK_TICK)) {
            state.putLong(TAG_FROG_NEXT_CHECK_TICK, now + FROG_INITIAL_DELAY_TICKS);
            return;
        }
        if (now < state.getLong(TAG_FROG_NEXT_CHECK_TICK)) {
            return;
        }

        Optional<LivingEntity> target = findClosestMonster(player, FROG_RANGE);
        if (target.isEmpty() || !canFrogEat(target.get())) {
            state.putLong(TAG_FROG_NEXT_CHECK_TICK, now + FROG_CHECK_INTERVAL_TICKS);
            return;
        }

        LivingEntity eaten = target.get();
        // Stardew's frog consumes the monster without a kill, loot, XP, or
        // eradication credit. Do not route this through LivingDeathEvent.
        eaten.setHealth(0.0F);
        eaten.remove(Entity.RemovalReason.KILLED);
        state.putLong(TAG_FROG_NEXT_CHECK_TICK, now + FROG_FULLNESS_TICKS);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CROAK.get(), SoundSource.PLAYERS, 0.65F, 1.0F);
    }

    private static void tickParrotEgg(ServerPlayer player, ItemStack stack, CompoundTag state) {
        StardewTrinketItem.getGeneratedData(stack, player);
        Parrot parrot = getTrackedParrot(player, state);
        if (parrot == null) {
            parrot = EntityType.PARROT.create(player.serverLevel());
            if (parrot == null) {
                return;
            }
            parrot.setPos(player.getX(), player.getY() + 1.5D, player.getZ());
            configureParrot(player, parrot);
            if (!player.serverLevel().addFreshEntity(parrot) || parrot.isRemoved()) {
                parrot.discard();
                return;
            }
            state.putUUID(TAG_PARROT_UUID, parrot.getUUID());
        }

        configureParrot(player, parrot);
        Vec3 target = parrotTarget(player);
        if (parrot.level() != player.level() || parrot.distanceToSqr(player) > 144.0D) {
            parrot.teleportTo(target.x, target.y, target.z);
            return;
        }
        Vec3 toTarget = target.subtract(parrot.position());
        Vec3 motion = parrot.getDeltaMovement().scale(0.55D).add(toTarget.scale(0.18D));
        if (motion.lengthSqr() > 0.20D) {
            motion = motion.normalize().scale(0.45D);
        }
        parrot.setDeltaMovement(motion);
        parrot.move(net.minecraft.world.entity.MoverType.SELF, motion);
        parrot.fallDistance = 0.0F;
        parrot.hurtMarked = true;
    }

    private static void configureParrot(ServerPlayer player, Parrot parrot) {
        parrot.setTame(true, true);
        parrot.setOwnerUUID(player.getUUID());
        parrot.setOrderedToSit(false);
        parrot.setNoAi(true);
        parrot.setNoGravity(true);
        parrot.setInvulnerable(true);
        parrot.noPhysics = true;
        parrot.setPersistenceRequired();
        parrot.getPersistentData().putBoolean(TAG_TRINKET_PARROT, true);
    }

    private static Vec3 parrotTarget(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 0.001D) {
            horizontalLook = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        horizontalLook = horizontalLook.normalize();
        Vec3 right = new Vec3(-horizontalLook.z, 0.0D, horizontalLook.x);
        double bob = Math.sin((player.tickCount + player.getId()) * 0.16D) * 0.16D;
        return player.position()
                .add(right.scale(1.05D))
                .add(horizontalLook.scale(0.25D))
                .add(0.0D, 1.75D + bob, 0.0D);
    }

    private static Optional<LivingEntity> findClosestMonster(ServerPlayer player, double range) {
        AABB area = player.getBoundingBox().inflate(range);
        return player.level().getEntitiesOfClass(LivingEntity.class, area, target -> isTargetableMonster(player, target))
            .stream()
            .min(Comparator.comparingDouble(player::distanceToSqr));
    }

    private static boolean isTargetableMonster(ServerPlayer player, LivingEntity target) {
        return target != player
            && target.isAlive()
            && !(target instanceof ServerPlayer)
            && target instanceof Enemy;
    }

    private static boolean canFrogEat(LivingEntity target) {
        return target instanceof Enemy
            && !(target instanceof ServerPlayer)
            && !(target instanceof EnderDragon)
            && !(target instanceof WitherBoss);
    }

    private static void ensureFairy(ServerPlayer player) {
        if (!player.serverLevel().getEntitiesOfClass(FairyCompanionEntity.class,
                player.getBoundingBox().inflate(64.0D), fairy -> fairy.isOwnedBy(player.getUUID())).isEmpty()) {
            return;
        }
        player.serverLevel().addFreshEntity(new FairyCompanionEntity(player.level(), player));
    }

    private static void removeFairies(ServerPlayer player) {
        for (FairyCompanionEntity fairy : player.serverLevel().getEntitiesOfClass(FairyCompanionEntity.class,
                player.getBoundingBox().inflate(128.0D), fairy -> fairy.isOwnedBy(player.getUUID()))) {
            fairy.discard();
        }
    }

    private static Parrot getTrackedParrot(ServerPlayer player, CompoundTag state) {
        if (!state.hasUUID(TAG_PARROT_UUID)) {
            return null;
        }
        UUID uuid = state.getUUID(TAG_PARROT_UUID);
        Entity entity = player.serverLevel().getEntity(uuid);
        if (entity instanceof Parrot parrot && parrot.isAlive()) {
            return parrot;
        }
        for (ServerLevel level : player.server.getAllLevels()) {
            if (level == player.serverLevel()) {
                continue;
            }
            Entity other = level.getEntity(uuid);
            if (other instanceof Parrot parrot && parrot.getPersistentData().getBoolean(TAG_TRINKET_PARROT)) {
                parrot.discard();
                break;
            }
        }
        state.remove(TAG_PARROT_UUID);
        return null;
    }

    private static void removeParrot(ServerPlayer player, CompoundTag state) {
        Parrot parrot = getTrackedParrot(player, state);
        if (parrot != null) {
            parrot.discard();
        }
        state.remove(TAG_PARROT_UUID);
    }

    private static TrinketType equippedType(ServerPlayer player) {
        return StardewTrinketItem.getType(PlayerDataManager.getPlayerData(player).getEquippedTrinket());
    }

    private static void handleTypeTransition(ServerPlayer player, CompoundTag state, TrinketType type) {
        String current = type == null ? "" : type.registryName();
        String previous = state.getString(TAG_LAST_TYPE);
        if (previous.equals(current)) {
            return;
        }
        if (TrinketType.PARROT_EGG.registryName().equals(previous)) {
            removeParrot(player, state);
        }
        if (TrinketType.FAIRY_BOX.registryName().equals(previous)) {
            removeFairies(player);
        }
        state.putString(TAG_LAST_TYPE, current);
        state.putInt(TAG_QUIVER_TIMER, 0);
        state.putInt(TAG_ICE_TIMER, 0);
        state.putInt(TAG_FAIRY_TIMER, 0);
        state.putInt(TAG_FAIRY_COMBAT_DAMAGE, 0);
        state.remove(TAG_FROG_NEXT_CHECK_TICK);
    }

    private static void recordFairyCombatDamage(
            ServerPlayer player,
            int stardewDamage
    ) {
        CompoundTag state = getState(player);
        state.putInt(
                TAG_FAIRY_COMBAT_DAMAGE,
                accumulateFairyCombatDamage(
                        state.getInt(TAG_FAIRY_COMBAT_DAMAGE),
                        stardewDamage
                )
        );
    }

    static int accumulateFairyCombatDamage(
            int accumulatedDamage,
            int newDamage
    ) {
        if (newDamage <= 0) {
            return Math.max(0, accumulatedDamage);
        }
        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(0L, accumulatedDamage) + (long) newDamage
        );
    }

    private static CompoundTag getState(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(STATE_TAG)) {
            persistent.put(STATE_TAG, new CompoundTag());
        }
        return persistent.getCompound(STATE_TAG);
    }
}
