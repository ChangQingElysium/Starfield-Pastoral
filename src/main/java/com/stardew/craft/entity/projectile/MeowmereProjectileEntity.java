package com.stardew.craft.entity.projectile;

import com.stardew.craft.combat.skill.HitCooldownDamageSource;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillContextStore;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.sound.ModSounds;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class MeowmereProjectileEntity extends ThrowableProjectile {

    @SuppressWarnings("null")
    private static final EntityDataAccessor<Integer> BOUNCES = SynchedEntityData.defineId(MeowmereProjectileEntity.class, EntityDataSerializers.INT);
    public static final int MAX_BOUNCES = 4;
    public static final int MAX_LIFETIME_TICKS = 200;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final double GRAVITY_FACTOR = 0.03;
    public static final double BOUNCE_VELOCITY_RETENTION = 0.85;
    public static final int TRAIL_MAX_AGE = 5;
    public static final int TRAIL_MAX_POINTS = 5;
    private float damage = 10.0f;
    private int pierceCount = 0; // 穿透次数
    private String skillId = null;
    private SkillContext.SkillTier skillTier = SkillContext.SkillTier.MINOR;
    private float damageMultiplier = 1.0F;
    private WeaponDamageSnapshot releaseWeaponSnapshot;
    private static final float TRAIL_MIN_SPEED = 0.001f;
    private static final int TRAIL_UPDATE_FREQUENCY = 1;
    private static final double TRAIL_MOTION_SHIFT = 0.0;
    private static final Vec3 TRAIL_POSITION_OFFSET = new Vec3(0.0, 0.0, 0.0);
    private final Deque<TrailPoint> trailPoints = new ArrayDeque<>();

    public MeowmereProjectileEntity(EntityType<? extends ThrowableProjectile> type, Level level) {
        super(type, level);
    }

    public MeowmereProjectileEntity(Level level, LivingEntity owner, float damage, int pierceCount, String skillId) {
        this(
                level,
                owner,
                damage,
                pierceCount,
                skillId,
                defaultTier(skillId),
                defaultDamageMultiplier(skillId)
        );
    }

    public MeowmereProjectileEntity(
            Level level,
            LivingEntity owner,
            float damage,
            int pierceCount,
            String skillId,
            SkillContext.SkillTier skillTier,
            float damageMultiplier
    ) {
        this(
                level,
                owner,
                damage,
                pierceCount,
                skillId,
                skillTier,
                damageMultiplier,
                null
        );
    }

    public MeowmereProjectileEntity(
            Level level,
            LivingEntity owner,
            float damage,
            int pierceCount,
            String skillId,
            SkillContext.SkillTier skillTier,
            float damageMultiplier,
            WeaponDamageSnapshot releaseWeaponSnapshot
    ) {
        super(ModEntities.MEOWMERE_PROJECTILE.get(), owner, level);
        this.damage = damage;
        this.pierceCount = pierceCount;
        this.skillId = skillId;
        this.skillTier = skillTier;
        this.damageMultiplier = damageMultiplier;
        this.releaseWeaponSnapshot = releaseWeaponSnapshot;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
        this.skillTier = defaultTier(skillId);
        this.damageMultiplier = defaultDamageMultiplier(skillId);
    }

    @SuppressWarnings("null")
    @Override
    protected void defineSynchedData(@SuppressWarnings("null") SynchedEntityData.Builder builder) {
        builder.define(BOUNCES, 0);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public void setPierceCount(int count) {
        this.pierceCount = count;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            recordTrailPoint();
        }

        if (!this.level().isClientSide) {
            // 简单的低重力控制
            Vec3 deltaMovement = this.getDeltaMovement();
            if (!this.isNoGravity()) {
                this.setDeltaMovement(deltaMovement.x, deltaMovement.y * 0.99 - GRAVITY_FACTOR, deltaMovement.z);
            }

            if (isExpired(this.tickCount)) {
                this.discard();
            }
        }
    }

    @SuppressWarnings("null")
    private void recordTrailPoint() {
        if (this.tickCount % TRAIL_UPDATE_FREQUENCY != 0) {
            return;
        }

        Vec3 pos = this.tickCount > 1 ? this.getPosition(1.0f) : this.position();
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() < (double) (TRAIL_MIN_SPEED * TRAIL_MIN_SPEED)) {
            return;
        }
        if (motion.lengthSqr() > 1.0E-6) {
            pos = pos.add(motion.normalize().scale(-TRAIL_MOTION_SHIFT));
        }
        pos = pos.add(TRAIL_POSITION_OFFSET);
        addTrailPoint(pos);
    }

    @SuppressWarnings("null")
    private void addTrailPoint(Vec3 pos) {
        TrailPoint last = trailPoints.peekLast();
        float tex = last == null ? 0.0f : last.texcoord + (float) last.position.distanceTo(pos);
        trailPoints.addLast(new TrailPoint(pos, 0.0f, tex));
        trimTrail();
    }

    @SuppressWarnings("null")
    private void trimTrail() {
        for (TrailPoint p : trailPoints) {
            p.age += 1.0f;
        }
        while (!trailPoints.isEmpty() && trailPoints.peekFirst().age > TRAIL_MAX_AGE) {
            trailPoints.removeFirst();
        }
        while (trailPoints.size() > TRAIL_MAX_POINTS) {
            trailPoints.removeFirst();
        }
    }

    public Deque<TrailPoint> getTrailPoints() {
        return trailPoints;
    }

    public static int getTrailMaxAge() {
        return TRAIL_MAX_AGE;
    }

    public static final class TrailPoint {
        public final Vec3 position;
        public float age;
        public final float texcoord;

        public TrailPoint(Vec3 position, float age, float texcoord) {
            this.position = position;
            this.age = age;
            this.texcoord = texcoord;
        }
    }

    @SuppressWarnings("null")
    @Override
    protected void onHitEntity(@SuppressWarnings("null") EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        Entity owner = this.getOwner();

        if (target == owner) return; // 不伤害自己
        if (!(target instanceof LivingEntity livingTarget)
                || !(owner instanceof net.minecraft.world.entity.player.Player
                skillPlayer)
                || skillId == null
                || releaseWeaponSnapshot == null) {
            this.discard();
            return;
        }

        DamageSource source = HitCooldownDamageSource.bypassVanillaCooldown(
                this.damageSources().mobProjectile(
                        this,
                        skillPlayer
                )
        );
        long nowTick = this.level().getGameTime();
        SkillContext hitContext = createHitContext(
                skillId,
                skillTier,
                damageMultiplier
        );
        WeaponSkillContextStore.setPending(
                skillPlayer,
                hitContext,
                releaseWeaponSnapshot,
                nowTick + HIT_CONTEXT_LIFETIME_TICKS
        );
        try {
            livingTarget.hurt(source, this.damage);
        } finally {
            if (WeaponSkillContextStore.hasPending(
                    skillPlayer,
                    nowTick
            )) {
                WeaponSkillContextStore.consume(skillPlayer, nowTick);
            }
        }

        // 命中音效
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.MEOW.get(), SoundSource.PLAYERS, 0.6f, 1.0f + (this.random.nextFloat() - 0.5f) * 0.2f);
        }

        // 穿透逻辑
        if (!discardsAfterEntityHit(pierceCount)) {
            if (pierceCount > 0) {
                pierceCount--;
            }
            return;
        }
        this.discard();
    }

    @SuppressWarnings("null")
    @Override
    protected void onHitBlock(@SuppressWarnings("null") BlockHitResult result) {
        // 反弹逻辑
        @SuppressWarnings("null")
        int bounces = this.entityData.get(BOUNCES);
        if (!canBounce(bounces)) {
            this.discard();
            return;
        }

        this.entityData.set(BOUNCES, bounces + 1);

        // 播放反弹音效
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.MEOW.get(), SoundSource.NEUTRAL, 0.5f, 1.0f + (this.random.nextFloat() - 0.5f) * 0.2f);

        // 计算反弹向量
        Vec3 velocity = this.getDeltaMovement();
        @SuppressWarnings("null")
        Vec3 normal = Vec3.atLowerCornerOf(result.getDirection().getNormal());

        // V_new = V_old - 2 * (V_old · N) * N
        // 简单的完全弹性碰撞公式，加上一点摩擦力
        @SuppressWarnings("null")
        double dot = velocity.dot(normal);
        @SuppressWarnings("null")
        Vec3 reflection = velocity.subtract(normal.scale(2 * dot))
                .scale(BOUNCE_VELOCITY_RETENTION);

        this.setDeltaMovement(reflection);

        // 稍微推开一点，防止卡在墙里
        this.setPos(this.position().add(reflection.normalize().scale(0.1)));
    }

    @SuppressWarnings("null")
    @Override
    public void addAdditionalSaveData(@SuppressWarnings("null") CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Bounces", this.entityData.get(BOUNCES));
        tag.putFloat("Damage", this.damage);
        tag.putInt("PierceCount", this.pierceCount);
        tag.putString("SkillId", this.skillId == null ? "" : this.skillId);
        tag.putString("SkillTier", this.skillTier.name());
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
        if(tag.contains("Bounces")) this.entityData.set(BOUNCES, tag.getInt("Bounces"));
        if(tag.contains("Damage")) this.damage = tag.getFloat("Damage");
        if(tag.contains("PierceCount")) this.pierceCount = tag.getInt("PierceCount");
        if (tag.contains("SkillId")) {
            String id = tag.getString("SkillId");
            this.skillId = id == null || id.isEmpty() ? null : id;
        }
        if (tag.contains("SkillTier")) {
            this.skillTier = parseTier(
                    tag.getString("SkillTier"),
                    defaultTier(this.skillId)
            );
        } else {
            this.skillTier = defaultTier(this.skillId);
        }
        if (tag.contains("DamageMultiplier")) {
            this.damageMultiplier = tag.getFloat("DamageMultiplier");
        } else {
            this.damageMultiplier = defaultDamageMultiplier(this.skillId);
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

    static SkillContext createHitContext(
            String skillId,
            SkillContext.SkillTier skillTier,
            float damageMultiplier
    ) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(skillTier)
                .damageMultiplier(damageMultiplier)
                .build();
    }

    static SkillContext.SkillTier defaultTier(String skillId) {
        return "meowmere_symphony".equals(skillId)
                ? SkillContext.SkillTier.MAJOR
                : SkillContext.SkillTier.MINOR;
    }

    static float defaultDamageMultiplier(String skillId) {
        return "meowmere_symphony".equals(skillId) ? 0.8F : 1.0F;
    }

    static boolean isExpired(int tickCount) {
        return tickCount > MAX_LIFETIME_TICKS;
    }

    static boolean canBounce(int completedBounces) {
        return completedBounces < MAX_BOUNCES;
    }

    static boolean discardsAfterEntityHit(int remainingPierces) {
        return remainingPierces == 0;
    }

    private static SkillContext.SkillTier parseTier(
            String name,
            SkillContext.SkillTier fallback
    ) {
        try {
            return SkillContext.SkillTier.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    // 渲染需要这些
    @SuppressWarnings("null")
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(@SuppressWarnings("null") net.minecraft.server.level.ServerEntity serverEntity) {
        return super.getAddEntityPacket(serverEntity); // ThrowableProjectile处理了基础的同步
    }
}
