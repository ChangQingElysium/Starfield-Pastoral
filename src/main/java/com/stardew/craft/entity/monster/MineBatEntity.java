package com.stardew.craft.entity.monster;

import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.monster.*;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Ordinary SDV bat. No Phantom circling/diving, daylight burning, or vanilla Bat AI. */
@SuppressWarnings("null")
public final class MineBatEntity extends StardewMonsterEntity {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DEEP_RED =
            net.minecraft.network.syncher.SynchedEntityData.defineId(MineBatEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    public static final int ROOST = 0, AWAKE = 1, FLY = 2;
    // Stable body/head-hull AABB; the cutout wing membranes do not enlarge contact damage.
    public static final float WIDTH = 1.05F, HEIGHT = 1.12F;
    private final BatFlight flight = new BatFlight();
    private BlockPos ceiling;
    private final MonsterFlightWander wander = new MonsterFlightWander();
    private int flapCooldown;
    private int steeringPauseMillis;

    public MineBatEntity(EntityType<? extends MineBatEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = false;
        addTag("sd_mob_bat");
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24)
                .add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.MOVEMENT_SPEED, .25)
                .add(Attributes.FOLLOW_RANGE, 64).add(Attributes.STEP_HEIGHT, 0);
    }
    @Override protected void registerGoals() {}
    @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) { return new MonsterFlightRoute(this,level); }
    private MonsterFlightRoute route() { return (MonsterFlightRoute)getNavigation(); }
    public BatFlight flight() { return flight; }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DEEP_RED, false);
    }
    public boolean deepRed() { return entityData.get(DEEP_RED); }
    @Override public boolean isPushable() { return false; }
    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(1.2);
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide) return;
        if (flapCooldown > 0) flapCooldown--;
        int frame = (int) (animationTime(0) / .08) % 4;
        if (isAlive() && phase() != ROOST && !isSilent() && flapCooldown == 0 && frame % 3 == 0) {
            // Existing crow cue is the original batFlap sample (0.26 s), not a crow call.
            level().playLocalSound(getX(), getY(), getZ(), ModSounds.CROW_FLAP.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE, .6F, 1, false);
            flapCooldown = 6; // Do not overlap the 260 ms sample with itself.
        }
    }
    public String variant() { return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath(); }
    @Override protected ResourceLocation definitionId() {
        return new ResourceLocation("stardewcraft", variant());
    }
    @Override protected void configureSpawn(MonsterDefinition definition, MonsterSpawnContext context) {
        var resolved = MonsterStatResolver.base(definition, context, random);
        setInitialHealth(resolved.initialHealth());
        replaceCombatStats(resolved.combat());
        boolean deep=context.floor()>999;
        entityData.set(DEEP_RED,deep);
        flight.variant(variant().equals("iridium_bat"),deep);
        if(deep)setInitialHealth(resolved.initialHealth()*2);
        flight.initialize(random);
        setPos(getX(),getY()+.04,getZ());
        MonsterFlightPlacement.ensureSpawnAir(this);
        if(isRemoved())return;
        // Find a real underside in the 3D map; never place the head shell in terrain.
        for (int dy = 1; dy <= 8; dy++) {
            var candidate = blockPosition().above(dy);
            if(!level().hasChunkAt(candidate))break;
            var state = level().getBlockState(candidate);
            if (state.getCollisionShape(level(), candidate).isEmpty()) continue;
            double y = candidate.getY() - getBbHeight() - .04;
            if (state.isFaceSturdy(level(), candidate, Direction.DOWN)
                    && level().noCollision(this, getBoundingBox().move(0, y - getY(), 0))) {
                ceiling = candidate;
                setPos(getX(), y, getZ());
            }
            break;
        }
        // Open-air command spawns cannot hang from an absent ceiling.
        if (ceiling == null) {
            if (level().noCollision(this, getBoundingBox().move(0, .6, 0))) setPos(getX(), getY() + .6, getZ());
            startAction(FLY, true);
        }
    }
    public void startPursuit() {
        if (level().isClientSide) return;
        ceiling = null;route().invalidate();
        if (phase() == ROOST) startAction(AWAKE, true);
    }
    private boolean validTarget(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && !player.hasEffect(ModMobEffects.AVOID_MONSTERS)
                && (monsterState().context().generation() == null
                    || com.stardew.craft.mining.OrdinaryMineRuntime.floorAt(player.blockPosition()) == monsterState().context().floor());
    }
    @Override protected void customServerAiStep() {
        if (!initialized()) initialize(MonsterSpawnContext.capture((ServerLevel) level(), MonsterSpawnContext.Source.WORLD, 1));
        if(isRemoved())return;
        var previous=getTarget();
        Player nearest=previous instanceof Player p&&validTarget(p)&&distanceToSqr(p)<64*64?p:null;
        if(nearest==null)nearest=level().getNearestPlayer(getX(),getY(),getZ(),64,
                e->e instanceof Player p&&validTarget(p)&&(getTags().contains("sd_focused_on_farmers")||MineMonsterSight.sees(this,p,64)));
        setTarget(nearest);
        var target=getTarget(); // Equipment/target events remain authoritative.
        if(previous!=target){wander.reset();route().invalidate();}
        if(phase()==ROOST){
            boolean broken=ceiling==null||!level().hasChunkAt(ceiling)||!level().getBlockState(ceiling).isFaceSturdy(level(),ceiling,Direction.DOWN);
            if(broken||target!=null&&(getTags().contains("sd_focused_on_farmers")||getBoundingBox().getCenter().distanceToSqr(target.getBoundingBox().getCenter())<=36))startPursuit();
            else return;
        }
        Vec3 destination=target==null?wander.next(this,5,1.5):target.getBoundingBox().getCenter().add(0,.3-getBbHeight()/2.,0);
        Vec3 waypoint=route().waypoint(destination);
        for(int step=0;step<BatFlight.STEPS_PER_TICK;step++){
            int elapsed=step==2?18:16;flight.elapsed(elapsed);
            boolean paused=steeringPauseMillis>0;
            steeringPauseMillis=Math.max(0,steeringPauseMillis-elapsed);
            flight.steer(paused||waypoint==null?null:waypoint.subtract(position()),target!=null,!paused);
            Vec3 actual=route().move(flight);
            if(actual.horizontalDistanceSqr()>1e-7){
                float yaw=(float)Math.toDegrees(Math.atan2(-actual.x,actual.z));
                setYRot(net.minecraft.util.Mth.rotLerp(.3F,getYRot(),yaw));yBodyRot=getYRot();setYHeadRot(getYRot());
            }
            contactPlayers();
        }
    }
    private void contactPlayers() {
        for (var player : ((ServerLevel) level()).players()) {
            if (!validTarget(player) || !getBoundingBox().intersects(player.getBoundingBox())) continue;
            var attack = MonsterDamageSource.contact(this);
            player.hurt(attack, attack.baseDamage());
        }
    }
    @Override public void travel(Vec3 input) {
        // Motion is integrated at source cadence above; do not apply MC gravity/friction again.
        setDeltaMovement(Vec3.ZERO);
    }
    @Override public void knockback(double strength, double dx, double dz) {
        if (level().isClientSide || dx * dx + dz * dz < .000001) return;
        super.knockback(strength, dx, dz); // Retains the common equipment/event boundary.
        var impulse = getDeltaMovement();
        flight.knockback(impulse.x*64/BatFlight.STEPS_PER_TICK,impulse.z*64/BatFlight.STEPS_PER_TICK);
        setDeltaMovement(Vec3.ZERO);
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isAlive()) startPursuit();
        float before = getHealth();
        boolean accepted = super.hurt(source, amount);
        if (!level().isClientSide && getHealth() < before) {
            flight.hit();
            // GameLocation.damageMonster: 450 / 3 for daggers, 450 / 2 for other melee.
            boolean dagger = source.getEntity() instanceof Player player
                    && com.stardew.craft.combat.WeaponStats.fromItemStack(player.getMainHandItem()).getWeaponType()
                        == com.stardew.craft.combat.WeaponType.DAGGER;
            steeringPauseMillis = dagger ? 150 : 225;
        }
        return accepted;
    }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.MONSTER_BAT_HIT.get(); }
    @Override protected SoundEvent getDeathSound() { return ModSounds.BAT_SCREECH.get(); }
    @Override protected void onFinalDeath(DamageSource source) {
        flight.stop();
        if (level() instanceof ServerLevel server) server.sendParticles(
                new DustParticleOptions(new Vector3f(.55F, 0, .55F), .8F),
                getX(), getY() + .5, getZ(), 18, .25, .2, .25, .04);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("BatFlight",flight.save());tag.put("BatWander",wander.save());
        tag.putInt("BatSteeringPauseMillis",steeringPauseMillis);
        if (ceiling != null) tag.putLong("BatCeiling", ceiling.asLong());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setNoGravity(true);
        noPhysics=false;
        flight.variant(variant().equals("iridium_bat"),initialized()&&monsterState().context().floor()>999);
        if(tag.contains("BatFlight"))flight.load(tag.getCompound("BatFlight"));
        wander.load(tag.getCompound("BatWander"));route().invalidate();
        steeringPauseMillis=tag.getInt("BatSteeringPauseMillis");
        entityData.set(DEEP_RED, initialized() && monsterState().context().floor() > 999);
        ceiling = tag.contains("BatCeiling") ? BlockPos.of(tag.getLong("BatCeiling")) : null;
    }
}
