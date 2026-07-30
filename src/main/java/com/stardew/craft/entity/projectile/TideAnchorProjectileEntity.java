package com.stardew.craft.entity.projectile;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.TideMarkTracker;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.network.WaterRingEffectPayload;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class TideAnchorProjectileEntity extends ThrowableProjectile {

    public static final double GRAVITY = 0.03;
    public static final double AOE_RADIUS = 4.5;
    public static final double MARK_TELEPORT_RADIUS = 24.0;
    public static final int MAX_LIFETIME_TICKS = 80;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int WATER_RING_DURATION_TICKS = 20;
    public static final int ROOT_DURATION_TICKS = 100;
    public static final int ROOT_SLOW_AMPLIFIER = 4;
    public static final int ROOT_JUMP_AMPLIFIER = 128;

    private String skillId = "tide_anchor";
    private float damageMultiplier = 1.5F;
    private WeaponDamageSnapshot releaseWeaponSnapshot;

    public TideAnchorProjectileEntity(EntityType<? extends ThrowableProjectile> type, Level level) {
        super(type, level);
    }

    public TideAnchorProjectileEntity(
            Level level,
            LivingEntity owner,
            String skillId,
            float damageMultiplier
    ) {
        this(
                level,
                owner,
                skillId,
                damageMultiplier,
                null
        );
    }

    public TideAnchorProjectileEntity(
            Level level,
            LivingEntity owner,
            String skillId,
            float damageMultiplier,
            WeaponDamageSnapshot releaseWeaponSnapshot
    ) {
        super(ModEntities.TIDE_ANCHOR_PROJECTILE.get(), owner, level);
        if (skillId != null) {
            this.skillId = skillId;
        }
        this.damageMultiplier = damageMultiplier;
        this.releaseWeaponSnapshot = releaseWeaponSnapshot;
    }

    @Override
    protected void defineSynchedData(@SuppressWarnings("null") net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isNoGravity()) {
            Vec3 delta = this.getDeltaMovement();
            this.setDeltaMovement(delta.x, delta.y * 0.99 - GRAVITY, delta.z);
        }
        if (isExpired(this.tickCount)) {
            this.discard();
        }
    }

    @SuppressWarnings("null")
    @Override
    protected void onHitEntity(@SuppressWarnings("null") EntityHitResult result) {
        super.onHitEntity(result);
        if (!this.level().isClientSide) {
            handleImpact(result.getLocation());
        }
        this.discard();
    }

    @SuppressWarnings("null")
    @Override
    protected void onHitBlock(@SuppressWarnings("null") BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            handleImpact(result.getLocation());
        }
        this.discard();
    }

    @SuppressWarnings("null")
    private void handleImpact(Vec3 hitPos) {
        if (!(this.getOwner() instanceof Player player)) {
            return;
        }
        long nowTick = this.level().getGameTime();
        ServerLevel serverLevel = this.level() instanceof ServerLevel ? (ServerLevel) this.level() : null;

        // 水环特效（客户端表现）
        if (serverLevel != null) {
            PacketDistributor.sendToPlayersInDimension(serverLevel,
                new WaterRingEffectPayload(
                    (float) hitPos.x,
                    (float) hitPos.y,
                    (float) hitPos.z,
                    (float) AOE_RADIUS,
                    WATER_RING_DURATION_TICKS
                ));
        }

        // 主要冲击音效与粒子
        if (serverLevel != null) {
            serverLevel.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.9f, 0.95f);
            serverLevel.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.8f, 1.1f);
            serverLevel.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7f, 0.8f);

            serverLevel.sendParticles(ParticleTypes.SPLASH,
                hitPos.x, hitPos.y + 0.25, hitPos.z,
                26, 0.9, 0.25, 0.9, 0.04);
            serverLevel.sendParticles(ParticleTypes.BUBBLE,
                hitPos.x, hitPos.y + 0.15, hitPos.z,
                18, 0.7, 0.2, 0.7, 0.02);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                hitPos.x, hitPos.y + 0.2, hitPos.z,
                10, 0.6, 0.1, 0.6, 0.02);
            serverLevel.sendParticles(ParticleTypes.CRIT,
                hitPos.x, hitPos.y + 0.3, hitPos.z,
                8, 0.5, 0.2, 0.5, 0.06);
        }

        // AOE 伤害
        AABB box = new AABB(
            hitPos.x - AOE_RADIUS, hitPos.y - 1.0, hitPos.z - AOE_RADIUS,
            hitPos.x + AOE_RADIUS, hitPos.y + 2.0, hitPos.z + AOE_RADIUS
        );
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, box,
            entity -> entity.isPickable() && entity != player
        );

        for (LivingEntity target : targets) {
            SkillContext hitContext =
                    createHitContext(skillId, damageMultiplier);
            if (releaseWeaponSnapshot != null) {
                WeaponSkillDamage.apply(
                    player,
                    target,
                    hitContext,
                    releaseWeaponSnapshot,
                    nowTick + HIT_CONTEXT_LIFETIME_TICKS
                );
            } else {
                WeaponSkillDamage.apply(
                    player,
                    target,
                    hitContext,
                    nowTick + HIT_CONTEXT_LIFETIME_TICKS
                );
            }
        }

        // 传送带印记的目标到锚点并禁锢
        LivingEntity marked = findNearestMarkedTarget(player, hitPos);
        if (marked != null) {
            Vec3 oldPos = marked.position();
            marked.teleportTo(hitPos.x, hitPos.y, hitPos.z);
            marked.setDeltaMovement(0, marked.getDeltaMovement().y, 0);
            marked.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                ROOT_DURATION_TICKS,
                ROOT_SLOW_AMPLIFIER,
                false,
                true,
                true
            ));
            marked.addEffect(new MobEffectInstance(
                MobEffects.JUMP,
                ROOT_DURATION_TICKS,
                ROOT_JUMP_AMPLIFIER,
                false,
                false,
                false
            ));

            if (serverLevel != null) {
                serverLevel.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                    SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
                serverLevel.sendParticles(ParticleTypes.SPLASH,
                    oldPos.x, oldPos.y + marked.getBbHeight() * 0.6, oldPos.z,
                    12, 0.5, 0.25, 0.5, 0.03);
                serverLevel.sendParticles(ParticleTypes.SPLASH,
                    hitPos.x, hitPos.y + marked.getBbHeight() * 0.6, hitPos.z,
                    14, 0.6, 0.25, 0.6, 0.03);
                serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    hitPos.x, hitPos.y + marked.getBbHeight() * 0.6, hitPos.z,
                    10, 0.4, 0.3, 0.4, 0.02);
            }
        }
    }

    private LivingEntity findNearestMarkedTarget(Player player, Vec3 anchorPos) {
        AABB box = new AABB(
            anchorPos.x - MARK_TELEPORT_RADIUS, anchorPos.y - 6.0, anchorPos.z - MARK_TELEPORT_RADIUS,
            anchorPos.x + MARK_TELEPORT_RADIUS, anchorPos.y + 6.0, anchorPos.z + MARK_TELEPORT_RADIUS
        );
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, box,
            entity -> entity.isPickable() && entity != player
        );

        LivingEntity closest = null;
        double best = Double.MAX_VALUE;
        long nowTick = this.level().getGameTime();
        for (LivingEntity target : targets) {
            if (!TideMarkTracker.isMarkedBy(target, player, nowTick)) {
                continue;
            }
            double dist = target.distanceToSqr(anchorPos.x, anchorPos.y, anchorPos.z);
            if (dist < best) {
                best = dist;
                closest = target;
            }
        }
        return closest;
    }

    static SkillContext createHitContext(
            String skillId,
            float damageMultiplier
    ) {
        return SkillContext.builder()
            .skillId(skillId)
            .tier(SkillContext.SkillTier.MAJOR)
            .damageMultiplier(damageMultiplier)
            .build();
    }

    static boolean isExpired(int tickCount) {
        return tickCount > MAX_LIFETIME_TICKS;
    }

    @SuppressWarnings("null")
    @Override
    public void addAdditionalSaveData(@SuppressWarnings("null") CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkillId", this.skillId == null ? "" : this.skillId);
        tag.putFloat("DamageMultiplier", this.damageMultiplier);
        writeReleaseWeaponSnapshot(
                tag,
                this.releaseWeaponSnapshot,
                this.level().registryAccess()
        );
    }

    @SuppressWarnings("null")
    @Override
    public void readAdditionalSaveData(@SuppressWarnings("null") CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String id = tag.getString("SkillId");
        this.skillId = id == null || id.isEmpty() ? "tide_anchor" : id;
        if (tag.contains("DamageMultiplier")) {
            this.damageMultiplier = tag.getFloat("DamageMultiplier");
        }
        this.releaseWeaponSnapshot = readReleaseWeaponSnapshot(
                tag,
                this.level().registryAccess()
        );
    }

    static void writeReleaseWeaponSnapshot(
            CompoundTag tag,
            WeaponDamageSnapshot snapshot,
            HolderLookup.Provider registries
    ) {
        if (snapshot == null) {
            return;
        }
        ItemStack weapon = snapshot.weapon();
        if (weapon.isEmpty()) {
            return;
        }
        tag.putString("ReleaseWeaponId", snapshot.weaponId().toString());
        tag.put("ReleaseWeapon", weapon.saveOptional(registries));
    }

    static WeaponDamageSnapshot readReleaseWeaponSnapshot(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        if (!tag.contains("ReleaseWeapon", Tag.TAG_COMPOUND)) {
            return null;
        }
        ResourceLocation weaponId =
                ResourceLocation.tryParse(tag.getString("ReleaseWeaponId"));
        if (weaponId == null) {
            return null;
        }
        ItemStack weapon = ItemStack.parseOptional(
                registries,
                tag.getCompound("ReleaseWeapon")
        );
        return weapon.isEmpty()
                ? null
                : WeaponDamageSnapshot.capture(weaponId, weapon);
    }
}
