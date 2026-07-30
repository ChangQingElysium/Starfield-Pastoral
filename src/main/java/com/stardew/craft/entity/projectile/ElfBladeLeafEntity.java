package com.stardew.craft.entity.projectile;

import com.stardew.craft.combat.skill.ElfBladeMarkTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.entity.ModEntities;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class ElfBladeLeafEntity extends ThrowableProjectile {

    @SuppressWarnings("null")
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(ElfBladeLeafEntity.class, EntityDataSerializers.INT);
    @SuppressWarnings("null")
    private static final EntityDataAccessor<Integer> ORBIT_INDEX = SynchedEntityData.defineId(ElfBladeLeafEntity.class, EntityDataSerializers.INT);
    private static final int STATE_ORBIT = 0;
    private static final int STATE_HOMING = 1;

    private static final double ORBIT_RADIUS = 0.85;
    private static final double ORBIT_SPEED = 0.22;
    private static final double ORBIT_BOB = 0.12;
    private static final double HOMING_SPEED = 1.15;
    private static final double TURN_RATE = 0.22;
    private static final int MAX_HOMING_TICKS = 60;
    private static final int MARK_DURATION_TICKS = 140;

    private static final double TRAIL_MIN_DIST = 0.006;
    private static final int TRAIL_MAX_AGE_ORBIT = 30;
    private static final int TRAIL_MAX_AGE_FIRED = 46;
    private static final int TRAIL_MAX_POINTS_ORBIT = 90;
    private static final int TRAIL_MAX_POINTS_FIRED = 150;
    private static final float TRAIL_MIN_SPEED = 0.03f;
    private static final int TRAIL_UPDATE_FREQUENCY = 1;
    private static final double TRAIL_MOTION_SHIFT = 0.18;
    private static final Vec3 TRAIL_POSITION_OFFSET = new Vec3(0.0, 0.02, 0.0);

    private float damageMultiplier = 0.50f;
    private String skillId = "elf_blade_leaf";
    private UUID targetId = null;
    private long expireTick = 0L;
    private int homingTicks = 0;
    private WeaponDamageSnapshot releaseWeaponSnapshot;

    private final Deque<TrailPoint> trailPoints = new ArrayDeque<>();
    private Vec3 lastEmitPos = null;
    private Vec3 prevPos = null;
    private Vec3 smoothVel = Vec3.ZERO;

    public ElfBladeLeafEntity(EntityType<? extends ThrowableProjectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @SuppressWarnings("null")
    public ElfBladeLeafEntity(Level level, LivingEntity owner, float damageMultiplier, String skillId, int orbitIndex, long expireTick) {
        this(
            level,
            owner,
            damageMultiplier,
            skillId,
            orbitIndex,
            expireTick,
            null
        );
    }

    @SuppressWarnings("null")
    public ElfBladeLeafEntity(
        Level level,
        LivingEntity owner,
        float damageMultiplier,
        String skillId,
        int orbitIndex,
        long expireTick,
        WeaponDamageSnapshot releaseWeaponSnapshot
    ) {
        super(ModEntities.ELF_BLADE_LEAF.get(), owner, level);
        this.damageMultiplier = damageMultiplier;
        if (skillId != null) {
            this.skillId = skillId;
        }
        this.expireTick = expireTick;
        this.releaseWeaponSnapshot = releaseWeaponSnapshot;
        this.setNoGravity(true);
        this.entityData.set(ORBIT_INDEX, orbitIndex);
        this.entityData.set(STATE, STATE_ORBIT);
    }

    @Override
    @SuppressWarnings("null")
    protected void defineSynchedData(@SuppressWarnings("null") SynchedEntityData.Builder builder) {
        builder.define(STATE, STATE_ORBIT);
        builder.define(ORBIT_INDEX, 0);
    }

    @SuppressWarnings("null")
    public int getOrbitIndex() {
        return this.entityData.get(ORBIT_INDEX);
    }

    @SuppressWarnings("null")
    public boolean isOrbiting() {
        return this.entityData.get(STATE) == STATE_ORBIT;
    }

    @SuppressWarnings("null")
    public void launchToTarget(LivingEntity target) {
        if (target == null) {
            return;
        }
        this.targetId = target.getUUID();
        this.entityData.set(STATE, STATE_HOMING);
        this.homingTicks = 0;
    }

    @SuppressWarnings("null")
    @Override
    public void tick() {
        super.tick();

        LivingEntity owner = (LivingEntity) this.getOwner();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }

        if (this.entityData.get(STATE) == STATE_ORBIT) {
            if (!this.level().isClientSide && this.level().getGameTime() >= expireTick) {
                this.discard();
                return;
            }

            Vec3 center = owner.position().add(0, owner.getBbHeight() * 0.6, 0);
            double angle = (this.level().getGameTime() * ORBIT_SPEED)
                + (this.getOrbitIndex() * (Math.PI * 2.0 / 3.0));
            double x = center.x + Math.cos(angle) * ORBIT_RADIUS;
            double z = center.z + Math.sin(angle) * ORBIT_RADIUS;
            double y = center.y + Math.sin(angle * 2.0) * ORBIT_BOB;
            this.setPos(x, y, z);
            this.setDeltaMovement(Vec3.ZERO);
            if (this.level().isClientSide) {
                ageTrailPoints();
                recordTrailPoint();
            }
            return;
        }

        if (!this.level().isClientSide) {
            homingTicks++;
            if (homingTicks > MAX_HOMING_TICKS) {
                this.discard();
                return;
            }

            LivingEntity target = getTarget();
            if (target != null) {
                Vec3 desired = target.getEyePosition().subtract(this.position()).normalize().scale(HOMING_SPEED);
                Vec3 current = this.getDeltaMovement();
                Vec3 newVel = current.add(desired.subtract(current).scale(TURN_RATE));
                double max = HOMING_SPEED * HOMING_SPEED;
                if (newVel.lengthSqr() > max) {
                    newVel = newVel.normalize().scale(HOMING_SPEED);
                }
                this.setDeltaMovement(newVel);
            } else {
                this.setDeltaMovement(this.getDeltaMovement().scale(0.96));
            }
        }

        if (this.level().isClientSide) {
            ageTrailPoints();
            recordTrailPoint();
        }
    }

    @Override
    @SuppressWarnings("null")
    protected boolean canHitEntity(@SuppressWarnings("null") Entity entity) {
        if (this.entityData.get(STATE) == STATE_ORBIT) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @SuppressWarnings("null")
    private LivingEntity getTarget() {
        if (targetId == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity entity = serverLevel.getEntity(targetId);
        if (entity instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        return null;
    }

    @SuppressWarnings("null")
    private void recordTrailPoint() {
        if (this.tickCount % TRAIL_UPDATE_FREQUENCY != 0) {
            return;
        }

        Vec3 pos = (this.tickCount > 1 ? this.getPosition(1.0f) : this.position()).add(TRAIL_POSITION_OFFSET);
        Vec3 motion = this.getDeltaMovement();
        if (this.entityData.get(STATE) != STATE_ORBIT
            && motion.lengthSqr() < (double) (TRAIL_MIN_SPEED * TRAIL_MIN_SPEED)) {
            return;
        }
        if (motion.lengthSqr() > 1.0E-6) {
            pos = pos.add(motion.normalize().scale(-TRAIL_MOTION_SHIFT));
        }
        if (prevPos == null) {
            prevPos = pos;
        }
        Vec3 vel = pos.subtract(prevPos);
        smoothVel = smoothVel.add(vel.subtract(smoothVel).scale(0.25));
        prevPos = pos;
        Vec3 smoothPos = pos.add(smoothVel.scale(0.4));

        if (lastEmitPos == null) {
            addTrailPoint(smoothPos);
            lastEmitPos = smoothPos;
            return;
        }

        double minDist = this.entityData.get(STATE) == STATE_ORBIT ? 0.008 : TRAIL_MIN_DIST;
        double dist = lastEmitPos.distanceTo(smoothPos);
        if (dist < minDist) {
            return;
        }

        int steps = Math.max(1, (int) Math.ceil(dist / minDist));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / (double) steps;
            Vec3 p = lastEmitPos.lerp(smoothPos, t);
            addTrailPoint(p);
        }
        lastEmitPos = smoothPos;
    }

    @SuppressWarnings("null")
    private void addTrailPoint(Vec3 pos) {
        TrailPoint last = trailPoints.peekLast();
        float tex = last == null ? 0.0f : last.texcoord + (float) last.position.distanceTo(pos);
        trailPoints.addLast(new TrailPoint(pos, 0.0f, tex));
        trimTrail();
    }

    private void ageTrailPoints() {
        for (TrailPoint p : trailPoints) {
            p.age += 1.0f;
        }
        while (!trailPoints.isEmpty() && trailPoints.peekFirst().age > getTrailMaxAgeValue()) {
            trailPoints.removeFirst();
        }
    }

    @SuppressWarnings("null")
    private void trimTrail() {
        while (!trailPoints.isEmpty() && trailPoints.peekFirst().age > getTrailMaxAgeValue()) {
            trailPoints.removeFirst();
        }
        while (trailPoints.size() > getTrailMaxPointsValue()) {
            trailPoints.removeFirst();
        }
    }

    public Deque<TrailPoint> getTrailPoints() {
        return trailPoints;
    }

    @SuppressWarnings("null")
    public int getTrailMaxAgeValue() {
        return this.entityData.get(STATE) == STATE_ORBIT ? TRAIL_MAX_AGE_ORBIT : TRAIL_MAX_AGE_FIRED;
    }

    @SuppressWarnings("null")
    public int getTrailMaxPointsValue() {
        return this.entityData.get(STATE) == STATE_ORBIT ? TRAIL_MAX_POINTS_ORBIT : TRAIL_MAX_POINTS_FIRED;
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
        if (this.level().isClientSide) {
            return;
        }

        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        if (!(owner instanceof Player player) || target == owner) {
            this.discard();
            return;
        }

        if (target instanceof LivingEntity livingTarget) {
            livingTarget.invulnerableTime = 0;
            livingTarget.hurtTime = 0;

            long nowTick = this.level().getGameTime();
            SkillContext context = SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(this.damageMultiplier)
                .build();
            if (releaseWeaponSnapshot == null) {
                WeaponSkillDamage.apply(
                    player,
                    livingTarget,
                    context,
                    nowTick + 5
                );
            } else {
                WeaponSkillDamage.apply(
                    player,
                    livingTarget,
                    context,
                    releaseWeaponSnapshot,
                    nowTick + 5
                );
            }

            if (player instanceof ServerPlayer serverPlayer) {
                ElfBladeMarkTracker.apply(livingTarget, serverPlayer, nowTick, MARK_DURATION_TICKS, 1);
            }

            if (this.level() instanceof ServerLevel serverLevel) {
                double x = this.getX();
                double y = this.getY() + 0.1;
                double z = this.getZ();
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    x, y, z,
                    8, 0.3, 0.15, 0.3, 0.02);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW,
                    x, y, z,
                    6, 0.2, 0.1, 0.2, 0.01);
                serverLevel.playSound(null, target.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.6f, 1.4f);
            }
        }

        this.setDeltaMovement(Vec3.ZERO);
        this.discard();
    }

    @SuppressWarnings("null")
    @Override
    protected void onHitBlock(@SuppressWarnings("null") BlockHitResult result) {
        if (this.entityData.get(STATE) == STATE_ORBIT) {
            return;
        }
        super.onHitBlock(result);
        this.setDeltaMovement(Vec3.ZERO);
        this.discard();
    }

    @SuppressWarnings("null")
    @Override
    public void addAdditionalSaveData(@SuppressWarnings("null") CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkillId", this.skillId == null ? "" : this.skillId);
        tag.putFloat("DamageMultiplier", this.damageMultiplier);
        tag.putInt("State", this.entityData.get(STATE));
        tag.putInt("OrbitIndex", this.entityData.get(ORBIT_INDEX));
        tag.putLong("ExpireTick", this.expireTick);
        tag.putInt("HomingTicks", this.homingTicks);
        if (this.targetId != null) {
            tag.putUUID("Target", this.targetId);
        }
        writeReleaseWeaponSnapshot(
            tag,
            releaseWeaponSnapshot,
            this.level().registryAccess()
        );
    }

    @SuppressWarnings("null")
    @Override
    public void readAdditionalSaveData(@SuppressWarnings("null") CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String id = tag.getString("SkillId");
        this.skillId = id == null || id.isEmpty() ? "elf_blade_leaf" : id;
        if (tag.contains("DamageMultiplier")) {
            this.damageMultiplier = tag.getFloat("DamageMultiplier");
        }
        if (tag.contains("State")) {
            this.entityData.set(STATE, tag.getInt("State"));
        }
        if (tag.contains("OrbitIndex")) {
            this.entityData.set(ORBIT_INDEX, tag.getInt("OrbitIndex"));
        }
        if (tag.contains("ExpireTick")) {
            this.expireTick = tag.getLong("ExpireTick");
        }
        if (tag.contains("HomingTicks")) {
            this.homingTicks = tag.getInt("HomingTicks");
        }
        if (tag.hasUUID("Target")) {
            this.targetId = tag.getUUID("Target");
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
