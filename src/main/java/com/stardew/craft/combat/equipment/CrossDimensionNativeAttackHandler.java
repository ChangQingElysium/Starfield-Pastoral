package com.stardew.craft.combat.equipment;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.DimensionDamageMapper;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.WeaponCombatIdentity;
import com.stardew.craft.combat.skill.WeaponSkillContextStore;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.trinket.TrinketEffectHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.ProfessionType;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * Projects Stardew combat attributes onto Minecraft-native melee attacks.
 * Stardew weapons are excluded because their authoritative damage pipeline
 * already resolves these values in every dimension.
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class CrossDimensionNativeAttackHandler {
    static final float MINECRAFT_CRITICAL_MULTIPLIER = 1.5F;
    private static final Map<UUID, PendingNativeAttack> PENDING_ATTACKS =
            new HashMap<>();
    private static final NativeHitFrameStore NATIVE_HITS =
            new NativeHitFrameStore();

    private CrossDimensionNativeAttackHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !DimensionDamageMapper.isInStardewDimension(player)) {
            clearCritical(player.getUUID());
            // Main-hand changes may occur after the previous player tick.
            EquipmentPlayerAttributes.sync(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCriticalHit(CriticalHitEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || DimensionDamageMapper.isInStardewDimension(player)) {
            return;
        }

        ItemStack weapon = player.getMainHandItem();
        if (WeaponCombatIdentity.isWeapon(weapon)) {
            return;
        }

        EquipmentStats equipment = EquipmentResolver.getMergedStats(player);
        WeaponStats weaponStats = WeaponStats.fromItemStack(weapon);
        PlayerStardewData playerData = PlayerDataManager.getPlayerData(player);
        if (!event.isCriticalHit()) {
            float luckLevel = playerData.getLuckLevel()
                    + equipment.getLuck();
            float chance = CrossDimensionAttributeRules.minecraftCriticalChance(
                    weaponStats.getCritChance(),
                    weaponStats.getBonusCritChance(),
                    equipment.getCritChance(),
                    player.hasEffect(ModMobEffects.STATUE_OF_BLESSINGS_5)
                            ? 0.10F
                            : 0.0F,
                    playerData.hasProfession(ProfessionType.SCOUT),
                    luckLevel
            );
            if (player.getRandom().nextFloat() < chance) {
                event.setCriticalHit(true);
                event.setDisableSweep(true);
                event.setDamageMultiplier(
                        MINECRAFT_CRITICAL_MULTIPLIER
                );
            }
        }

        if (event.isCriticalHit()) {
            event.setDamageMultiplier(
                    CrossDimensionAttributeRules.minecraftCriticalMultiplier(
                            event.getDamageMultiplier(),
                            weaponStats.getBonusCritPower(),
                            equipment.getCritPower(),
                            playerData.hasProfession(ProfessionType.DESPERADO)
                    )
            );
        }
        rememberAttack(
                player.getUUID(),
                combatTargetId(event.getTarget()),
                player.level().getGameTime(),
                event.isCriticalHit()
        );
    }

    /**
     * Freezes native-hit ownership before the Stardew weapon adapter consumes
     * a pending release snapshot. Post must not reconstruct this decision from
     * the attacker's mutable main hand.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long nowTick = player.level().getGameTime();
        if (!shouldBindNativeHit(
                source.is(DamageTypes.PLAYER_ATTACK),
                DimensionDamageMapper.isInStardewDimension(player),
                WeaponSkillContextStore.hasPending(player, nowTick),
                WeaponCombatIdentity.isWeapon(player.getMainHandItem())
        )) {
            return;
        }
        NATIVE_HITS.bind(
                player.getUUID(),
                event.getEntity().getUUID(),
                source,
                nowTick + 2L
        );
    }

    /**
     * Bridges Minecraft-native melee hits into the same exact Applied Post
     * trinket boundary used by Stardew weapons. The pending critical record
     * identifies only the primary target; native sweep targets are valid
     * non-critical hits and still contribute to Fairy Box combat damage.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        LivingEntity target = event.getEntity();
        long nowTick = player.level().getGameTime();
        if (!NATIVE_HITS.consume(
                player.getUUID(),
                target.getUUID(),
                event.getSource(),
                nowTick
        )) {
            return;
        }
        boolean critical = consumeCritical(
                player.getUUID(),
                target.getUUID(),
                nowTick
        );
        if (event.getNewDamage() <= 0.0F) {
            return;
        }
        TrinketEffectHandler.onDamageMonster(
                player,
                target,
                nativeTrinketDamage(event.getNewDamage()),
                critical
        );
    }

    static boolean shouldBindNativeHit(
            boolean playerAttackSource,
            boolean inStardewDimension,
            boolean pendingWeaponSkill,
            boolean stardewWeaponHeld
    ) {
        return playerAttackSource
                && !inStardewDimension
                && !pendingWeaponSkill
                && !stardewWeaponHeld;
    }

    static int nativeTrinketDamage(float finalDamage) {
        if (!Float.isFinite(finalDamage) || finalDamage <= 0.0F) {
            return 0;
        }
        return Math.max(1, Math.round(finalDamage));
    }

    private static UUID combatTargetId(Entity target) {
        if (target instanceof PartEntity<?> part) {
            return part.getParent().getUUID();
        }
        return target.getUUID();
    }

    static void rememberAttack(
            UUID playerId,
            UUID targetId,
            long gameTick,
            boolean critical
    ) {
        PENDING_ATTACKS.put(
                playerId,
                new PendingNativeAttack(targetId, gameTick, critical)
        );
    }

    static boolean consumeCritical(
            UUID playerId,
            UUID targetId,
            long gameTick
    ) {
        PendingNativeAttack pending = PENDING_ATTACKS.get(playerId);
        if (pending == null) {
            return false;
        }
        if (pending.gameTick() != gameTick) {
            PENDING_ATTACKS.remove(playerId);
            return false;
        }
        if (!pending.targetId().equals(targetId)) {
            return false;
        }
        PENDING_ATTACKS.remove(playerId);
        return pending.critical();
    }

    public static void clear(UUID playerId) {
        clearCritical(playerId);
        NATIVE_HITS.clear(playerId);
    }

    private static void clearCritical(UUID playerId) {
        PENDING_ATTACKS.remove(playerId);
    }

    private record PendingNativeAttack(
            UUID targetId,
            long gameTick,
            boolean critical
    ) {
    }

    /** Package-private pure state seam for exact identity and nesting tests. */
    static final class NativeHitFrameStore {
        private final Map<UUID, Deque<BoundNativeHit>> frames =
                new HashMap<>();

        synchronized void bind(
                UUID playerId,
                UUID targetId,
                Object source,
                long expireTick
        ) {
            frames.computeIfAbsent(
                    Objects.requireNonNull(playerId, "playerId"),
                    ignored -> new ArrayDeque<>()
            ).push(new BoundNativeHit(
                    Objects.requireNonNull(targetId, "targetId"),
                    Objects.requireNonNull(source, "source"),
                    expireTick
            ));
        }

        synchronized boolean consume(
                UUID playerId,
                UUID targetId,
                Object source,
                long nowTick
        ) {
            Deque<BoundNativeHit> playerFrames = frames.get(playerId);
            if (playerFrames == null) {
                return false;
            }
            while (!playerFrames.isEmpty()
                    && playerFrames.peek().expireTick() < nowTick) {
                playerFrames.pop();
            }
            if (playerFrames.isEmpty()) {
                frames.remove(playerId);
                return false;
            }
            BoundNativeHit frame = playerFrames.peek();
            if (!frame.matches(targetId, source)) {
                return false;
            }
            playerFrames.pop();
            if (playerFrames.isEmpty()) {
                frames.remove(playerId);
            }
            return true;
        }

        synchronized void clear(UUID playerId) {
            frames.remove(playerId);
        }

        synchronized int size(UUID playerId) {
            Deque<BoundNativeHit> playerFrames = frames.get(playerId);
            return playerFrames == null ? 0 : playerFrames.size();
        }
    }

    private record BoundNativeHit(
            UUID targetId,
            Object source,
            long expireTick
    ) {
        private boolean matches(UUID candidateTargetId, Object candidate) {
            return targetId.equals(candidateTargetId) && source == candidate;
        }
    }
}
