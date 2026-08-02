package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection;
import com.stardew.craft.combat.network.LavaKatanaMarkPayload;
import com.stardew.craft.combat.skill.handler.LavaKatanaReverbSkillHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class LavaKatanaMarkTracker {

    private static final String TAG_END_TICK = "stardewcraft_lava_katana_mark_until";
    private static final String TAG_OWNER = "stardewcraft_lava_katana_mark_owner";
    private static final String TAG_OWNER_SESSION = "stardewcraft_lava_katana_mark_owner_session";
    private static final String TAG_DIMENSION = "stardewcraft_lava_katana_mark_dimension";
    private static final String TAG_HEAT = "stardewcraft_lava_katana_mark_heat";
    private static final String TAG_NEXT_TICK = "stardewcraft_lava_katana_mark_next_tick";
    private static final String TAG_RELEASE_WEAPON_ID =
        "stardewcraft_lava_katana_mark_weapon_id";
    private static final String TAG_RELEASE_WEAPON =
        "stardewcraft_lava_katana_mark_weapon";

    public static final String BURN_SKILL_ID = "lava_katana_burn";
    public static final int MARK_DURATION_TICKS = 120;
    public static final int HEAT_CAP = 5;
    public static final long BURN_INTERVAL_TICKS = 10L;
    public static final float BASE_BURN_RATIO = 0.15f;
    public static final float HEAT_BONUS_RATIO = 0.04f;
    public static final float HEAT_BONUS_REVERB_RATIO = 0.08f;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;

    private static final Map<UUID, Set<UUID>> MARKED_BY_OWNER = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> OWNER_SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, PreparedRelease> PREPARED_RELEASES =
        new ConcurrentHashMap<>();

    private record PreparedRelease(
        UUID ownerId,
        WeaponDamageSnapshot weaponSnapshot
    ) {}

    private LavaKatanaMarkTracker() {}

    @SuppressWarnings("null")
    public static void apply(LivingEntity target, ServerPlayer owner, long nowTick, int durationTicks) {
        WeaponDamageSnapshot weaponSnapshot =
            consumePreparedRelease(target, owner);
        applyInternal(
            target,
            owner,
            nowTick,
            durationTicks,
            weaponSnapshot
        );
    }

    @SuppressWarnings("null")
    public static void apply(
        LivingEntity target,
        ServerPlayer owner,
        long nowTick,
        int durationTicks,
        WeaponDamageSnapshot weaponSnapshot
    ) {
        discardPreparedRelease(target, owner);
        applyInternal(
            target,
            owner,
            nowTick,
            durationTicks,
            Objects.requireNonNull(
                weaponSnapshot,
                "weaponSnapshot"
            )
        );
    }

    private static void applyInternal(
        LivingEntity target,
        ServerPlayer owner,
        long nowTick,
        int durationTicks,
        WeaponDamageSnapshot weaponSnapshot
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
        detachPreviousOwner(target, tag);
        UUID ownerId = owner.getUUID();
        UUID ownerSession = OWNER_SESSIONS.computeIfAbsent(
            ownerId,
            ignored -> UUID.randomUUID()
        );
        tag.putLong(TAG_END_TICK, nowTick + appliedDuration);
        tag.putUUID(TAG_OWNER, ownerId);
        tag.putUUID(TAG_OWNER_SESSION, ownerSession);
        tag.putString(
            TAG_DIMENSION,
            target.level().dimension().location().toString()
        );
        tag.putInt(TAG_HEAT, 0);
        tag.putLong(TAG_NEXT_TICK, nowTick + BURN_INTERVAL_TICKS);
        writeWeaponSnapshot(target, tag, weaponSnapshot);

        MARKED_BY_OWNER
            .computeIfAbsent(ownerId, id -> ConcurrentHashMap.newKeySet())
            .add(target.getUUID());

        sendMarkSync(target, nowTick);

        if (target.level() instanceof ServerLevel serverLevel) {
            double x = target.getX();
            double y = target.getY() + target.getBbHeight() * 0.6;
            double z = target.getZ();
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                x, y, z,
                8, 0.25, 0.2, 0.25, 0.02);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                x, y, z,
                10, 0.35, 0.2, 0.35, 0.02);
            serverLevel.playSound(null, target.blockPosition(),
                SoundEvents.LAVA_POP,
                SoundSource.PLAYERS, 0.7f, 1.05f);
            serverLevel.playSound(null, target.blockPosition(),
                SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 0.6f, 1.1f);
        }
    }

    public static void prepareRelease(
        LivingEntity target,
        ServerPlayer owner,
        WeaponDamageSnapshot weaponSnapshot
    ) {
        if (target == null || owner == null) {
            return;
        }
        PREPARED_RELEASES.put(
            target.getUUID(),
            new PreparedRelease(
                owner.getUUID(),
                Objects.requireNonNull(
                    weaponSnapshot,
                    "weaponSnapshot"
                )
            )
        );
    }

    public static void discardPreparedRelease(
        LivingEntity target,
        ServerPlayer owner
    ) {
        if (target == null || owner == null) {
            return;
        }
        PREPARED_RELEASES.computeIfPresent(
            target.getUUID(),
            (targetId, prepared) ->
                prepared.ownerId().equals(owner.getUUID())
                    ? null
                    : prepared
        );
    }

    public static boolean isMarked(LivingEntity target, long nowTick) {
        CompoundTag tag = target.getPersistentData();
        if (!tag.contains(TAG_END_TICK)) {
            return false;
        }
        long endTick = tag.getLong(TAG_END_TICK);
        if (!isWithinMarkWindow(nowTick, endTick)
            || !hasActiveOwnership(tag)
            || !matchesDimension(
                tag.getString(TAG_DIMENSION),
                target.level().dimension(),
                target.level().dimension()
            )) {
            clearMark(target, tag);
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
        return matchesOwner(ownerId, player.getUUID())
            && tag.hasUUID(TAG_OWNER_SESSION)
            && matchesSession(
                tag.getUUID(TAG_OWNER_SESSION),
                OWNER_SESSIONS.get(ownerId)
            )
            && matchesDimension(
                tag.getString(TAG_DIMENSION),
                target.level().dimension(),
                player.level().dimension()
            );
    }

    public static int getHeat(LivingEntity target) {
        CompoundTag tag = target.getPersistentData();
        return Math.max(0, tag.getInt(TAG_HEAT));
    }

    public static int addHeatIfEligible(LivingEntity target, ServerPlayer owner, long nowTick, int amount) {
        if (!isMarkedBy(target, owner, nowTick)) {
            return 0;
        }
        CompoundTag tag = target.getPersistentData();
        int current = Math.max(0, tag.getInt(TAG_HEAT));
        boolean ignoreCap = LavaKatanaReverbSkillHandler.isActive(
            owner,
            nowTick
        );
        int maxHeat = ignoreCap ? Integer.MAX_VALUE : HEAT_CAP;
        long nextHeat = (long) current + Math.max(0, amount);
        int clamped = (int) Math.min(maxHeat, Math.min(Integer.MAX_VALUE, nextHeat));
        if (clamped != current) {
            tag.putInt(TAG_HEAT, clamped);
            sendMarkSync(target, nowTick);
        }
        return clamped;
    }

    public static void ensureHeatAtLeast(LivingEntity target, ServerPlayer owner, long nowTick, int minHeat) {
        if (!isMarkedBy(target, owner, nowTick)) {
            return;
        }
        CompoundTag tag = target.getPersistentData();
        int current = Math.max(0, tag.getInt(TAG_HEAT));
        int next = Math.max(current, Math.max(0, minHeat));
        if (next != current) {
            tag.putInt(TAG_HEAT, next);
            sendMarkSync(target, nowTick);
        }
    }

    public static int getRemainingTicks(LivingEntity target, long nowTick) {
        CompoundTag tag = target.getPersistentData();
        if (!tag.contains(TAG_END_TICK)) {
            return 0;
        }
        return (int) Math.max(0, tag.getLong(TAG_END_TICK) - nowTick);
    }

    public static Set<UUID> getMarkedTargets(UUID ownerId) {
        Set<UUID> targets = MARKED_BY_OWNER.get(ownerId);
        if (targets == null || targets.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(targets);
    }

    public static void clearMark(LivingEntity target) {
        CompoundTag tag = target.getPersistentData();
        clearMark(target, tag);
    }

    private static void clearMark(LivingEntity target, CompoundTag tag) {
        UUID ownerId = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        UUID targetId = target.getUUID();
        tag.remove(TAG_END_TICK);
        tag.remove(TAG_OWNER);
        tag.remove(TAG_OWNER_SESSION);
        tag.remove(TAG_DIMENSION);
        tag.remove(TAG_HEAT);
        tag.remove(TAG_NEXT_TICK);
        tag.remove(TAG_RELEASE_WEAPON_ID);
        tag.remove(TAG_RELEASE_WEAPON);
        PREPARED_RELEASES.remove(targetId);
        if (ownerId != null) {
            removeMarkedTarget(ownerId, targetId);
        }
        if (!target.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                new LavaKatanaMarkPayload(target.getId(), 0, 0)
            );
        }
    }

    private static void removeMarkedTarget(UUID ownerId, UUID targetId) {
        Set<UUID> targets = MARKED_BY_OWNER.get(ownerId);
        if (targets == null) {
            return;
        }
        targets.remove(targetId);
        if (targets.isEmpty()) {
            MARKED_BY_OWNER.remove(ownerId);
        }
    }

    private static void sendMarkSync(LivingEntity target, long nowTick) {
        if (target.level().isClientSide) {
            return;
        }
        int remaining = getRemainingTicks(target, nowTick);
        int heat = getHeat(target);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
            target,
            new LavaKatanaMarkPayload(target.getId(), remaining, heat)
        );
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
        if (!entity.isAlive() || !isMarked(entity, nowTick)) {
            if (tag.contains(TAG_END_TICK)) {
                clearMark(entity, tag);
            }
            return;
        }
        ServerPlayer owner = resolveOwner(entity, tag);
        if (owner == null) {
            clearMark(entity, tag);
            return;
        }

        long nextTick = tag.getLong(TAG_NEXT_TICK);
        if (nowTick >= nextTick) {
            tag.putLong(TAG_NEXT_TICK, nowTick + BURN_INTERVAL_TICKS);
            applyBurnTick(entity, tag, owner, nowTick);
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
                new LavaKatanaMarkPayload(
                        target.getId(),
                        getRemainingTicks(target, nowTick),
                        getHeat(target)
                )
        );
    }

    @SuppressWarnings("null")
    private static void applyBurnTick(
        LivingEntity target,
        CompoundTag tag,
        ServerPlayer owner,
        long nowTick
    ) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int heat = Math.max(0, tag.getInt(TAG_HEAT));
        boolean reverbActive = LavaKatanaReverbSkillHandler.isActive(
            owner,
            nowTick
        );
        applyDamage(
            owner,
            target,
            createBurnContext(heat, reverbActive),
            readWeaponSnapshot(target, tag),
            nowTick + HIT_CONTEXT_LIFETIME_TICKS
        );

        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.55;
        double z = target.getZ();
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
            x, y, z,
            6, 0.25, 0.18, 0.25, 0.01);
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
            x, y, z,
            4, 0.2, 0.15, 0.2, 0.01);
        serverLevel.playSound(null, target.blockPosition(),
            SoundEvents.FIRE_AMBIENT,
            SoundSource.PLAYERS, 0.5f, 1.2f);
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        MARKED_BY_OWNER.remove(playerId);
        OWNER_SESSIONS.remove(playerId);
        PREPARED_RELEASES.entrySet().removeIf(
            entry -> playerId.equals(entry.getValue().ownerId())
        );
    }

    static boolean isWithinMarkWindow(long nowTick, long endTick) {
        return nowTick < endTick;
    }

    static boolean matchesOwner(UUID markOwner, UUID playerId) {
        return markOwner.equals(playerId);
    }

    static boolean matchesSession(
        UUID markSession,
        UUID activeOwnerSession
    ) {
        return markSession != null && markSession.equals(activeOwnerSession);
    }

    static boolean matchesDimension(
        String storedDimension,
        ResourceKey<Level> targetDimension,
        ResourceKey<Level> ownerDimension
    ) {
        String target = targetDimension.location().toString();
        return target.equals(storedDimension)
            && targetDimension.equals(ownerDimension);
    }

    static int scheduledBurnTicks(int durationTicks) {
        if (durationTicks <= 0) {
            return 0;
        }
        return (int) ((durationTicks - 1L) / BURN_INTERVAL_TICKS);
    }

    static float burnDamageMultiplier(int heat, boolean reverbActive) {
        int nonNegativeHeat = Math.max(0, heat);
        int effectiveHeat = reverbActive
            ? nonNegativeHeat
            : Math.min(nonNegativeHeat, HEAT_CAP);
        float bonus = reverbActive
            ? HEAT_BONUS_REVERB_RATIO
            : HEAT_BONUS_RATIO;
        return BASE_BURN_RATIO + effectiveHeat * bonus;
    }

    static SkillContext createBurnContext(
        int heat,
        boolean reverbActive
    ) {
        return SkillContext.builder()
            .skillId(BURN_SKILL_ID)
            .tier(SkillContext.SkillTier.MINOR)
            .damageMultiplier(
                burnDamageMultiplier(heat, reverbActive)
            )
            .build();
    }

    private static void applyDamage(
        ServerPlayer owner,
        LivingEntity target,
        SkillContext context,
        WeaponDamageSnapshot weaponSnapshot,
        long expireTick
    ) {
        if (weaponSnapshot == null) {
            WeaponSkillDamage.apply(
                owner,
                target,
                context,
                expireTick,
                WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                WeaponSkillDamage.HitCooldownPolicy
                        .BYPASS_FOR_AUTHORED_SEQUENCE
            );
            return;
        }
        WeaponSkillDamage.apply(
            owner,
            target,
            context,
            weaponSnapshot,
            expireTick,
            WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
            WeaponSkillDamage.HitCooldownPolicy
                    .BYPASS_FOR_AUTHORED_SEQUENCE
        );
    }

    private static void writeWeaponSnapshot(
        LivingEntity target,
        CompoundTag tag,
        WeaponDamageSnapshot weaponSnapshot
    ) {
        if (weaponSnapshot == null) {
            tag.remove(TAG_RELEASE_WEAPON_ID);
            tag.remove(TAG_RELEASE_WEAPON);
            return;
        }
        tag.putString(
            TAG_RELEASE_WEAPON_ID,
            weaponSnapshot.weaponId().toString()
        );
        tag.put(
            TAG_RELEASE_WEAPON,
            weaponSnapshot.weapon().saveOptional(
                target.level().registryAccess()
            )
        );
    }

    private static WeaponDamageSnapshot readWeaponSnapshot(
        LivingEntity target,
        CompoundTag tag
    ) {
        if (!tag.contains(TAG_RELEASE_WEAPON_ID, Tag.TAG_STRING)
            || !tag.contains(TAG_RELEASE_WEAPON, Tag.TAG_COMPOUND)) {
            return null;
        }
        ResourceLocation weaponId = ResourceLocation.tryParse(
            tag.getString(TAG_RELEASE_WEAPON_ID)
        );
        if (weaponId == null) {
            return null;
        }
        ItemStack weapon = ItemStack.parse(
            target.level().registryAccess(),
            tag.getCompound(TAG_RELEASE_WEAPON)
        ).orElse(ItemStack.EMPTY);
        return WeaponDamageSnapshot.capture(weaponId, weapon);
    }

    private static WeaponDamageSnapshot consumePreparedRelease(
        LivingEntity target,
        ServerPlayer owner
    ) {
        if (target == null || owner == null) {
            return null;
        }
        PreparedRelease prepared = PREPARED_RELEASES.remove(
            target.getUUID()
        );
        return prepared != null
            && prepared.ownerId().equals(owner.getUUID())
                ? prepared.weaponSnapshot()
                : null;
    }

    private static boolean hasActiveOwnership(CompoundTag tag) {
        if (!tag.hasUUID(TAG_OWNER) || !tag.hasUUID(TAG_OWNER_SESSION)) {
            return false;
        }
        UUID ownerId = tag.getUUID(TAG_OWNER);
        return matchesSession(
            tag.getUUID(TAG_OWNER_SESSION),
            OWNER_SESSIONS.get(ownerId)
        );
    }

    private static ServerPlayer resolveOwner(
        LivingEntity target,
        CompoundTag tag
    ) {
        if (!(target.level() instanceof ServerLevel serverLevel)
            || !tag.hasUUID(TAG_OWNER)
            || !tag.hasUUID(TAG_OWNER_SESSION)) {
            return null;
        }
        UUID ownerId = tag.getUUID(TAG_OWNER);
        ServerPlayer owner = serverLevel.getServer()
            .getPlayerList()
            .getPlayer(ownerId);
        if (owner == null
            || !matchesSession(
                tag.getUUID(TAG_OWNER_SESSION),
                OWNER_SESSIONS.get(ownerId)
            )
            || !matchesDimension(
                tag.getString(TAG_DIMENSION),
                target.level().dimension(),
                owner.level().dimension()
            )) {
            return null;
        }
        return owner;
    }

    private static void detachPreviousOwner(
        LivingEntity target,
        CompoundTag tag
    ) {
        if (tag.hasUUID(TAG_OWNER)) {
            removeMarkedTarget(
                tag.getUUID(TAG_OWNER),
                target.getUUID()
            );
        }
    }
}
