package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.WeaponCombatIdentity;
import com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection;
import com.stardew.craft.combat.network.WickedKrisPoisonStatusPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Persistent, owner-isolated Wicked Kris poison state.
 *
 * <p>The poisoned entity's NBT list is the only gameplay authority. Each
 * target/owner pair owns one poison pool and an optional independent Nest
 * fuse, so multiplayer applications, chunk unloads, restarts and dimension
 * moves cannot cross-wire owners or release weapon snapshots.</p>
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class WickedKrisPoisonTracker {
    private static final String TAG_POISONS_V2 =
            "stardewcraft_wicked_kris_poisons_v2";
    private static final String ENTRY_OWNER = "owner";
    private static final String ENTRY_STACKS = "stacks";
    private static final String ENTRY_END_TICK = "end_tick";
    private static final String ENTRY_NEXT_DOT_TICK = "next_dot_tick";
    private static final String ENTRY_POISON_TOTAL_TICKS =
            "poison_total_ticks";
    private static final String ENTRY_DOT_WEAPON_ID = "dot_weapon_id";
    private static final String ENTRY_DOT_WEAPON = "dot_weapon";
    private static final String ENTRY_DETONATE_TICK = "detonate_tick";
    private static final String ENTRY_DETONATE_TOTAL_TICKS =
            "detonate_total_ticks";
    private static final String ENTRY_DETONATION_WEAPON_ID =
            "detonation_weapon_id";
    private static final String ENTRY_DETONATION_WEAPON =
            "detonation_weapon";

    // Legacy scalar keys retained only for one-time migration.
    private static final String LEGACY_END_TICK =
            "stardewcraft_wicked_kris_poison_until";
    private static final String LEGACY_OWNER =
            "stardewcraft_wicked_kris_poison_owner";
    private static final String LEGACY_STACKS =
            "stardewcraft_wicked_kris_poison_stacks";
    private static final String LEGACY_NEXT_TICK =
            "stardewcraft_wicked_kris_poison_next_tick";
    private static final String LEGACY_DETONATE_TICK =
            "stardewcraft_wicked_kris_poison_detonate";
    private static final String LEGACY_LAST_X =
            "stardewcraft_wicked_kris_poison_last_x";
    private static final String LEGACY_LAST_Y =
            "stardewcraft_wicked_kris_poison_last_y";
    private static final String LEGACY_LAST_Z =
            "stardewcraft_wicked_kris_poison_last_z";
    private static final String LEGACY_WEAPON_ID =
            "stardewcraft_wicked_kris_poison_weapon_id";
    private static final String LEGACY_WEAPON =
            "stardewcraft_wicked_kris_poison_weapon";

    public static final int MAX_STACKS = 5;
    public static final long DOT_INTERVAL_TICKS = 20L;
    public static final float STACK_DAMAGE_RATIO = 0.10F;
    public static final int DETONATE_DELAY_TICKS = 60;
    public static final float DETONATE_MULTIPLIER = 1.5F;
    public static final float DETONATE_RADIUS = 3.5F;

    private WickedKrisPoisonTracker() {
    }

    /**
     * Legacy overload cannot guarantee a delayed release snapshot and therefore
     * intentionally refuses to create poison state.
     */
    @Deprecated
    public static void applyPoison(
            LivingEntity target,
            ServerPlayer owner,
            long nowTick,
            int durationTicks,
            int stacks,
            boolean scheduleDetonation
    ) {
        // Delayed damage must never reconstruct a weapon from the current hand.
    }

    public static void applyPoison(
            LivingEntity target,
            ServerPlayer owner,
            long nowTick,
            int durationTicks,
            int stacks,
            boolean scheduleDetonation,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        applyPoisonInternal(
                target,
                owner,
                nowTick,
                durationTicks,
                stacks,
                scheduleDetonation,
                Objects.requireNonNull(weaponSnapshot, "weaponSnapshot")
        );
    }

    private static void applyPoisonInternal(
            LivingEntity target,
            ServerPlayer owner,
            long nowTick,
            int durationTicks,
            int stacks,
            boolean scheduleDetonation,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (target == null
                || owner == null
                || durationTicks <= 0
                || !isValidSnapshot(weaponSnapshot)) {
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
        int clampedStacks = clampStacks(stacks);
        List<PoisonEntry> entries = readEntries(target);
        PoisonEntry previous = removeOwner(entries, owner.getUUID());
        PoisonEntry replacement = new PoisonEntry(
                owner.getUUID(),
                clampedStacks,
                nowTick + appliedDuration,
                nowTick + DOT_INTERVAL_TICKS,
                appliedDuration,
                weaponSnapshot,
                previous == null ? null : previous.detonateTick,
                previous == null ? 0 : previous.detonateTotalTicks,
                previous == null ? null : previous.detonationSnapshot
        );
        if (scheduleDetonation) {
            int delay = protection.adjustRelatedDurationTicks(
                    DETONATE_DELAY_TICKS
            );
            replacement.detonateTick = nowTick + delay;
            replacement.detonateTotalTicks = delay;
            replacement.detonationSnapshot = weaponSnapshot;
        }
        entries.add(replacement);
        writeEntries(target, entries);
        sendUpsert(owner, target.getUUID(), replacement, nowTick);
        emitPoisonApplied(target);
    }

    public static boolean isPoisoned(LivingEntity target, long nowTick) {
        if (target == null) {
            return false;
        }
        List<PoisonEntry> entries = readEntries(target);
        boolean changed = removeExpired(target, entries, nowTick);
        if (changed) {
            writeEntries(target, entries);
        }
        return !entries.isEmpty();
    }

    public static boolean isPoisonedBy(
            LivingEntity target,
            Player player,
            long nowTick
    ) {
        if (target == null || player == null) {
            return false;
        }
        List<PoisonEntry> entries = readEntries(target);
        boolean changed = removeExpired(target, entries, nowTick);
        if (changed) {
            writeEntries(target, entries);
        }
        UUID playerId = player.getUUID();
        return entries.stream().anyMatch(entry ->
                entry.ownerId.equals(playerId)
        );
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || target.level().isClientSide) {
            return;
        }
        List<PoisonEntry> entries = readEntries(target);
        if (entries.isEmpty()) {
            return;
        }
        long nowTick = target.level().getGameTime();
        if (!target.isAlive()) {
            entries.forEach(entry -> sendRemove(target, entry.ownerId));
            writeEntries(target, List.of());
            return;
        }

        List<PoisonEntry> survivors = new ArrayList<>();
        for (PoisonEntry entry : entries) {
            ServerPlayer owner = resolveOwner(target, entry.ownerId);
            if (nowTick > entry.endTick) {
                sendRemove(target, entry.ownerId);
                continue;
            }

            if (nowTick >= entry.nextDotTick) {
                if (owner != null) {
                    applyDotTick(target, owner, entry, nowTick);
                }
                entry.nextDotTick = nowTick + DOT_INTERVAL_TICKS;
            }

            if (entry.detonateTick != null
                    && nowTick >= entry.detonateTick
                    && owner != null) {
                detonate(target, owner, entry, nowTick);
                sendRemove(target, entry.ownerId);
                continue;
            }

            if (nowTick >= entry.endTick) {
                sendRemove(target, entry.ownerId);
                continue;
            }
            survivors.add(entry);
            if (owner != null && nowTick % DOT_INTERVAL_TICKS == 0L) {
                sendUpsert(owner, target.getUUID(), entry, nowTick);
            }
        }
        writeEntries(target, survivors);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(
                    player,
                    WickedKrisPoisonStatusPayload.clearAll()
            );
        }
    }

    private static void applyDotTick(
            LivingEntity target,
            ServerPlayer owner,
            PoisonEntry entry,
            long nowTick
    ) {
        if (!isValidSnapshot(entry.dotSnapshot)) {
            return;
        }
        SkillContext context = SkillContext.builder()
                .skillId("wicked_kris_poison_dot")
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(dotDamageMultiplier(entry.stacks))
                .build();
        WeaponSkillDamage.apply(
                owner,
                target,
                context,
                entry.dotSnapshot,
                nowTick + 5,
                WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                WeaponSkillDamage.HitCooldownPolicy
                        .BYPASS_FOR_AUTHORED_SEQUENCE
        );
        emitDotTick(target);
    }

    private static void detonate(
            LivingEntity poisonedTarget,
            ServerPlayer owner,
            PoisonEntry entry,
            long nowTick
    ) {
        if (!(poisonedTarget.level() instanceof ServerLevel level)
                || !isValidSnapshot(entry.detonationSnapshot)) {
            return;
        }
        Vec3 center = poisonedTarget.position();
        float damageMultiplier = scheduledDotApplications(
                entry.endTick,
                entry.nextDotTick
        ) * entry.stacks * STACK_DAMAGE_RATIO * DETONATE_MULTIPLIER;
        AABB box = new AABB(
                center.x - DETONATE_RADIUS,
                center.y - DETONATE_RADIUS,
                center.z - DETONATE_RADIUS,
                center.x + DETONATE_RADIUS,
                center.y + DETONATE_RADIUS,
                center.z + DETONATE_RADIUS
        );
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> entity.isPickable()
                        && entity.isAlive()
                        && entity != owner
        );
        if (damageMultiplier > 0.0F) {
            SkillContext context = SkillContext.builder()
                    .skillId("wicked_kris_poison_burst")
                    .tier(SkillContext.SkillTier.MAJOR)
                    .damageMultiplier(damageMultiplier)
                    .build();
            for (LivingEntity target : targets) {
                WeaponSkillDamage.apply(
                        owner,
                        target,
                        context,
                        entry.detonationSnapshot,
                        nowTick + 5,
                        WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE,
                        WeaponSkillDamage.HitCooldownPolicy
                                .BYPASS_FOR_AUTHORED_SEQUENCE
                );
            }
        }
        emitDetonation(level, center);
    }

    private static ServerPlayer resolveOwner(
            LivingEntity target,
            UUID ownerId
    ) {
        if (!(target.level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer owner = level.getServer()
                .getPlayerList()
                .getPlayer(ownerId);
        return owner != null && owner.level() == level ? owner : null;
    }

    private static boolean removeExpired(
            LivingEntity target,
            List<PoisonEntry> entries,
            long nowTick
    ) {
        boolean[] changed = {false};
        entries.removeIf(entry -> {
            if (nowTick <= entry.endTick && target.isAlive()) {
                return false;
            }
            changed[0] = true;
            sendRemove(target, entry.ownerId);
            return true;
        });
        return changed[0];
    }

    private static PoisonEntry removeOwner(
            List<PoisonEntry> entries,
            UUID ownerId
    ) {
        for (int index = 0; index < entries.size(); index++) {
            PoisonEntry entry = entries.get(index);
            if (entry.ownerId.equals(ownerId)) {
                entries.remove(index);
                return entry;
            }
        }
        return null;
    }

    private static List<PoisonEntry> readEntries(LivingEntity target) {
        CompoundTag root = target.getPersistentData();
        migrateLegacy(target, root);
        ListTag serialized = root.getList(TAG_POISONS_V2, Tag.TAG_COMPOUND);
        List<PoisonEntry> entries = new ArrayList<>();
        for (int index = 0; index < serialized.size(); index++) {
            PoisonEntry entry = decodeEntry(
                    target,
                    serialized.getCompound(index)
            );
            if (entry != null) {
                // The list is authoritative per target/owner pair. Keep the
                // newest serialized entry if legacy or corrupted data contains
                // duplicates, so one owner can never tick or detonate twice.
                removeOwner(entries, entry.ownerId);
                entries.add(entry);
            }
        }
        return entries;
    }

    private static void writeEntries(
            LivingEntity target,
            List<PoisonEntry> entries
    ) {
        CompoundTag root = target.getPersistentData();
        if (entries.isEmpty()) {
            root.remove(TAG_POISONS_V2);
            return;
        }
        ListTag serialized = new ListTag();
        entries.forEach(entry -> serialized.add(encodeEntry(target, entry)));
        root.put(TAG_POISONS_V2, serialized);
    }

    private static CompoundTag encodeEntry(
            LivingEntity target,
            PoisonEntry entry
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ENTRY_OWNER, entry.ownerId);
        tag.putInt(ENTRY_STACKS, entry.stacks);
        tag.putLong(ENTRY_END_TICK, entry.endTick);
        tag.putLong(ENTRY_NEXT_DOT_TICK, entry.nextDotTick);
        tag.putInt(ENTRY_POISON_TOTAL_TICKS, entry.poisonTotalTicks);
        writeSnapshot(
                target,
                tag,
                ENTRY_DOT_WEAPON_ID,
                ENTRY_DOT_WEAPON,
                entry.dotSnapshot
        );
        if (entry.detonateTick != null
                && isValidSnapshot(entry.detonationSnapshot)) {
            tag.putLong(ENTRY_DETONATE_TICK, entry.detonateTick);
            tag.putInt(
                    ENTRY_DETONATE_TOTAL_TICKS,
                    entry.detonateTotalTicks
            );
            writeSnapshot(
                    target,
                    tag,
                    ENTRY_DETONATION_WEAPON_ID,
                    ENTRY_DETONATION_WEAPON,
                    entry.detonationSnapshot
            );
        }
        return tag;
    }

    private static PoisonEntry decodeEntry(
            LivingEntity target,
            CompoundTag tag
    ) {
        if (!tag.hasUUID(ENTRY_OWNER)) {
            return null;
        }
        WeaponDamageSnapshot dotSnapshot = readSnapshot(
                target,
                tag,
                ENTRY_DOT_WEAPON_ID,
                ENTRY_DOT_WEAPON
        );
        if (!isValidSnapshot(dotSnapshot)) {
            return null;
        }
        Long detonateTick = tag.contains(ENTRY_DETONATE_TICK, Tag.TAG_LONG)
                ? tag.getLong(ENTRY_DETONATE_TICK)
                : null;
        WeaponDamageSnapshot detonationSnapshot = detonateTick == null
                ? null
                : readSnapshot(
                        target,
                        tag,
                        ENTRY_DETONATION_WEAPON_ID,
                        ENTRY_DETONATION_WEAPON
                );
        if (detonateTick != null
                && !isValidSnapshot(detonationSnapshot)) {
            detonateTick = null;
            detonationSnapshot = null;
        }
        return new PoisonEntry(
                tag.getUUID(ENTRY_OWNER),
                clampStacks(tag.getInt(ENTRY_STACKS)),
                tag.getLong(ENTRY_END_TICK),
                tag.getLong(ENTRY_NEXT_DOT_TICK),
                Math.max(1, tag.getInt(ENTRY_POISON_TOTAL_TICKS)),
                dotSnapshot,
                detonateTick,
                Math.max(0, tag.getInt(ENTRY_DETONATE_TOTAL_TICKS)),
                detonationSnapshot
        );
    }

    private static void migrateLegacy(
            LivingEntity target,
            CompoundTag root
    ) {
        if (root.contains(TAG_POISONS_V2, Tag.TAG_LIST)
                || !root.contains(LEGACY_END_TICK, Tag.TAG_LONG)) {
            return;
        }
        WeaponDamageSnapshot snapshot = readSnapshot(
                target,
                root,
                LEGACY_WEAPON_ID,
                LEGACY_WEAPON
        );
        if (root.hasUUID(LEGACY_OWNER) && isValidSnapshot(snapshot)) {
            long nowTick = target.level().getGameTime();
            long endTick = root.getLong(LEGACY_END_TICK);
            Long detonateTick = root.contains(
                    LEGACY_DETONATE_TICK,
                    Tag.TAG_LONG
            ) ? root.getLong(LEGACY_DETONATE_TICK) : null;
            PoisonEntry entry = new PoisonEntry(
                    root.getUUID(LEGACY_OWNER),
                    clampStacks(root.getInt(LEGACY_STACKS)),
                    endTick,
                    Math.max(
                            nowTick + DOT_INTERVAL_TICKS,
                            root.getLong(LEGACY_NEXT_TICK)
                    ),
                    (int) Math.max(1L, endTick - nowTick),
                    snapshot,
                    detonateTick,
                    detonateTick == null ? 0 : DETONATE_DELAY_TICKS,
                    detonateTick == null ? null : snapshot
            );
            writeEntries(target, List.of(entry));
        }
        clearLegacy(root);
    }

    private static void clearLegacy(CompoundTag tag) {
        tag.remove(LEGACY_END_TICK);
        tag.remove(LEGACY_OWNER);
        tag.remove(LEGACY_STACKS);
        tag.remove(LEGACY_NEXT_TICK);
        tag.remove(LEGACY_DETONATE_TICK);
        tag.remove(LEGACY_LAST_X);
        tag.remove(LEGACY_LAST_Y);
        tag.remove(LEGACY_LAST_Z);
        tag.remove(LEGACY_WEAPON_ID);
        tag.remove(LEGACY_WEAPON);
    }

    private static void writeSnapshot(
            LivingEntity target,
            CompoundTag tag,
            String idKey,
            String weaponKey,
            WeaponDamageSnapshot snapshot
    ) {
        tag.putString(idKey, snapshot.weaponId().toString());
        tag.put(
                weaponKey,
                snapshot.weapon().saveOptional(
                        target.level().registryAccess()
                )
        );
    }

    private static WeaponDamageSnapshot readSnapshot(
            LivingEntity target,
            CompoundTag tag,
            String idKey,
            String weaponKey
    ) {
        if (!tag.contains(idKey, Tag.TAG_STRING)
                || !tag.contains(weaponKey, Tag.TAG_COMPOUND)) {
            return null;
        }
        ResourceLocation weaponId = ResourceLocation.tryParse(
                tag.getString(idKey)
        );
        if (weaponId == null) {
            return null;
        }
        ItemStack weapon = ItemStack.parse(
                target.level().registryAccess(),
                tag.getCompound(weaponKey)
        ).orElse(ItemStack.EMPTY);
        WeaponDamageSnapshot snapshot = WeaponDamageSnapshot.capture(
                weaponId,
                weapon
        );
        return isValidSnapshot(snapshot) ? snapshot : null;
    }

    private static boolean isValidSnapshot(WeaponDamageSnapshot snapshot) {
        return snapshot != null
                && WeaponCombatIdentity.isWeapon(snapshot.weapon());
    }

    private static void sendUpsert(
            ServerPlayer owner,
            UUID targetId,
            PoisonEntry entry,
            long nowTick
    ) {
        int detonateRemaining = entry.detonateTick == null
                ? 0
                : (int) Math.max(0L, entry.detonateTick - nowTick);
        PacketDistributor.sendToPlayer(
                owner,
                WickedKrisPoisonStatusPayload.upsert(
                        targetId,
                        entry.stacks,
                        (int) Math.max(0L, entry.endTick - nowTick),
                        entry.poisonTotalTicks,
                        detonateRemaining,
                        entry.detonateTotalTicks
                )
        );
    }

    private static void sendRemove(LivingEntity target, UUID ownerId) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer owner = level.getServer()
                .getPlayerList()
                .getPlayer(ownerId);
        if (owner != null) {
            PacketDistributor.sendToPlayer(
                    owner,
                    WickedKrisPoisonStatusPayload.remove(target.getUUID())
            );
        }
    }

    private static void emitPoisonApplied(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.6D;
        double z = target.getZ();
        level.sendParticles(
                ParticleTypes.WITCH,
                x,
                y,
                z,
                12,
                0.35D,
                0.25D,
                0.35D,
                0.02D
        );
        level.sendParticles(
                ParticleTypes.SPORE_BLOSSOM_AIR,
                x,
                y,
                z,
                8,
                0.35D,
                0.25D,
                0.35D,
                0.01D
        );
        level.playSound(
                null,
                target.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS,
                0.6F,
                1.25F
        );
    }

    private static void emitDotTick(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.55D;
        double z = target.getZ();
        level.sendParticles(
                ParticleTypes.WITCH,
                x,
                y,
                z,
                6,
                0.25D,
                0.2D,
                0.25D,
                0.01D
        );
        level.sendParticles(
                ParticleTypes.SPORE_BLOSSOM_AIR,
                x,
                y,
                z,
                4,
                0.2D,
                0.15D,
                0.2D,
                0.01D
        );
        level.playSound(
                null,
                target.blockPosition(),
                SoundEvents.SPIDER_AMBIENT,
                SoundSource.PLAYERS,
                0.45F,
                1.35F
        );
    }

    private static void emitDetonation(ServerLevel level, Vec3 center) {
        level.sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                center.x,
                center.y + 0.2D,
                center.z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D
        );
        level.sendParticles(
                ParticleTypes.WITCH,
                center.x,
                center.y + 0.35D,
                center.z,
                28,
                0.6D,
                0.3D,
                0.6D,
                0.02D
        );
        level.sendParticles(
                ParticleTypes.SPORE_BLOSSOM_AIR,
                center.x,
                center.y + 0.35D,
                center.z,
                20,
                0.55D,
                0.3D,
                0.55D,
                0.01D
        );
        level.sendParticles(
                ParticleTypes.CRIT,
                center.x,
                center.y + 0.3D,
                center.z,
                16,
                0.45D,
                0.2D,
                0.45D,
                0.08D
        );
        level.playSound(
                null,
                net.minecraft.core.BlockPos.containing(center),
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS,
                0.9F,
                1.05F
        );
        level.playSound(
                null,
                net.minecraft.core.BlockPos.containing(center),
                SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS,
                0.7F,
                0.9F
        );
    }

    static int clampStacks(int stacks) {
        return Mth.clamp(stacks, 1, MAX_STACKS);
    }

    static float dotDamageMultiplier(int stacks) {
        return Math.max(0, stacks) * STACK_DAMAGE_RATIO;
    }

    static int remainingDotApplications(
            long poisonEndTick,
            long nowTick
    ) {
        long remainingTicks = Math.max(0L, poisonEndTick - nowTick);
        return (int) Math.max(
                0L,
                (remainingTicks + DOT_INTERVAL_TICKS - 1L)
                        / DOT_INTERVAL_TICKS
        );
    }

    static int scheduledDotApplications(
            long poisonEndTick,
            long nextDotTick
    ) {
        if (nextDotTick > poisonEndTick) {
            return 0;
        }
        return (int) ((poisonEndTick - nextDotTick)
                / DOT_INTERVAL_TICKS) + 1;
    }

    static float detonationDamageMultiplier(
            long poisonEndTick,
            long nowTick,
            int stacks
    ) {
        return remainingDotApplications(poisonEndTick, nowTick)
                * Math.max(0, stacks)
                * STACK_DAMAGE_RATIO
                * DETONATE_MULTIPLIER;
    }

    /** Persistent poison is intentionally not deleted when its owner logs out. */
    public static void removePlayer(UUID playerId) {
        // Entity NBT remains authoritative until natural expiry or detonation.
    }

    private static final class PoisonEntry {
        private final UUID ownerId;
        private final int stacks;
        private final long endTick;
        private long nextDotTick;
        private final int poisonTotalTicks;
        private final WeaponDamageSnapshot dotSnapshot;
        private Long detonateTick;
        private int detonateTotalTicks;
        private WeaponDamageSnapshot detonationSnapshot;

        private PoisonEntry(
                UUID ownerId,
                int stacks,
                long endTick,
                long nextDotTick,
                int poisonTotalTicks,
                WeaponDamageSnapshot dotSnapshot,
                Long detonateTick,
                int detonateTotalTicks,
                WeaponDamageSnapshot detonationSnapshot
        ) {
            this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
            this.stacks = clampStacks(stacks);
            this.endTick = endTick;
            this.nextDotTick = nextDotTick;
            this.poisonTotalTicks = Math.max(1, poisonTotalTicks);
            this.dotSnapshot = Objects.requireNonNull(
                    dotSnapshot,
                    "dotSnapshot"
            );
            this.detonateTick = detonateTick;
            this.detonateTotalTicks = Math.max(0, detonateTotalTicks);
            this.detonationSnapshot = detonationSnapshot;
        }
    }
}
