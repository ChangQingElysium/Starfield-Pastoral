package com.stardew.craft.entity.monster;

import com.stardew.craft.monster.*;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Bug.cs ordinary and armored variants: cardinal patrol, terrain reversal, no pursuit or Spider AI. */
@SuppressWarnings("null")
public final class MineBugEntity extends StardewMonsterEntity {
    public static final float WIDTH=1.3F, HEIGHT=.85F;
    public static final double FLIGHT_LIFT=.65; // MC adaptation: body center about 1.15 blocks above the patrol floor.
    public static final double STEP=2.0/64; // Source speed 2, three 60 Hz updates per MC tick.
    private static final EntityDataAccessor<Integer> FACING=SynchedEntityData.defineId(MineBugEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> HIT_START=SynchedEntityData.defineId(MineBugEntity.class,EntityDataSerializers.LONG);
    private double slideX,slideZ,flightLift;
    private final boolean armored;
    private int stunMilliseconds, patrolRetryTicks;
    private boolean checkPatrolPlacement=true;

    public MineBugEntity(EntityType<? extends MineBugEntity> type,Level level) {
        this(type,level,false);
    }
    public MineBugEntity(EntityType<? extends MineBugEntity> type,Level level,boolean armored) {
        super(type,level);this.armored=armored;setNoGravity(true);addTag("sd_mob_bug");if(armored)addTag("sd_mob_armored_bug");
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,1).add(Attributes.ATTACK_DAMAGE,8)
                .add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.STEP_HEIGHT,0);
    }
    @Override protected void registerGoals() {}
    @Override protected ResourceLocation definitionId() { return new ResourceLocation("stardewcraft:"+variant()); }
    @Override protected void configureSpawn(MonsterDefinition definition,MonsterSpawnContext context) {
        var resolved=MonsterStatResolver.base(definition,context,random);
        setInitialHealth(armored?150:resolved.initialHealth());
        var stats=resolved.combat();
        if(armored)stats=com.stardew.craft.combat.MonsterStats.builder().damage(stats.getDamage()*2).resilience(stats.getResilience()).missChance(stats.getMissChance()).experience(stats.getExperience()).build();
        replaceCombatStats(stats);
        raiseFlight();
        setPatrolFacing(Math.floorMod(Math.round((getYRot()+180)/90),4));
    }
    private void raiseFlight() {
        setPos(getX(),getY()+FLIGHT_LIFT-flightLift,getZ());
        flightLift=FLIGHT_LIFT;
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();this.entityData.define(FACING,2);this.entityData.define(HIT_START,-100L);
    }
    public boolean armored() { return armored; }
    public String variant() { return armored?"armored_bug":"bug"; }
    public void stunFor(int ms) { stunMilliseconds=Math.max(stunMilliseconds,ms); }
    public int patrolFacing() { return entityData.get(FACING); }
    public void setPatrolFacing(int facing) {
        int dir=Math.floorMod(facing,4);entityData.set(FACING,dir);
        setYRot(dir*90-180);yBodyRot=getYRot();setYHeadRot(getYRot());
    }
    public double hitTime(float partial) { return (level().getGameTime()-entityData.get(HIT_START)+partial)/20.; }
    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity other) {}
    @Override public boolean canCollideWith(Entity other) { return false; }
    @Override public boolean causeFallDamage(float d,float m,DamageSource s) { return false; }
    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling() { return super.getBoundingBoxForCulling().inflate(.8); }
    @Override public void tick() {
        // Ordinary Bug has one HP: validate before LivingEntity applies suffocation,
        // and after the complete floor population (including adjacent stones) is installed.
        if (!level().isClientSide && initialized() && checkPatrolPlacement) {
            checkPatrolPlacement=false;
            if (isAlive() && !resolveInitialPatrolSpace()) { MonsterFactory.cleanup(this); return; }
        }
        super.tick();
    }
    @Override protected void customServerAiStep() {
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));
        setTarget(null);
        boolean retryPatrol=patrolRetryTicks==0;
        if (patrolRetryTicks>0) patrolRetryTicks--;
        for(int i=0;i<3;i++) {
            if(stunMilliseconds>0){stunMilliseconds=Math.max(0,stunMilliseconds-16);continue;}
            if(slideX!=0||slideZ!=0) {
                tryStep(slideX/64,slideZ/64);
                // Ordinary Bug inherits Monster.slipperiness=2; blocked slides decay identically.
                slideX*=.5;slideZ*=.5;
                if(Math.abs(slideX)<=.05)slideX=0;if(Math.abs(slideZ)<=.05)slideZ=0;
            }
            if (!retryPatrol) { contactPlayers(); continue; }
            int direction=patrolFacing();
            double dx=direction==1?STEP:direction==3?-STEP:0;
            double dz=direction==2?STEP:direction==0?-STEP:0;
            if(!tryStep(dx,dz)) {
                var next=getBoundingBox().move(dx,0,dz);
                // Source collision callback explicitly does not reverse into a farmer.
                boolean farmer=((ServerLevel)level()).players().stream().anyMatch(p->p.isAlive()&&next.intersects(p.getBoundingBox()));
                if(!farmer) {
                    // Both ends blocked: keep one heading instead of reversing three times per tick.
                    if (canStep(-dx*3,-dz*3)) setPatrolFacing(direction+2);
                    else { patrolRetryTicks=5; contactPlayers(); break; }
                }
            }
            contactPlayers();
        }
    }
    private void contactPlayers() {
        for(var player:((ServerLevel)level()).players()) {
            if(!player.isAlive()||player.isCreative()||player.isSpectator()||!getBoundingBox().intersects(player.getBoundingBox()))continue;
            if(monsterState().context().generation()!=null&&com.stardew.craft.mining.OrdinaryMineRuntime.floorAt(player.blockPosition())!=monsterState().context().floor())continue;
            var attack=MonsterDamageSource.contact(this);player.hurt(attack,attack.baseDamage());
        }
    }
    private net.minecraft.world.phys.AABB patrolBox() {
        // The source ground obstacles still matter; leave numerical clearance above the supporting floor.
        return getBoundingBox().expandTowards(0,-flightLift+1e-5,0);
    }
    private boolean validPatrolSpace(Vec3 point) {
        var box=patrolBox().move(point.subtract(position()));
        if(!MonsterSpace.loaded(this,box)||!level().noCollision(this,box)||level().containsAnyLiquid(box))return false;
        var floor=net.minecraft.core.BlockPos.containing(point.x,point.y-flightLift-.05,point.z);
        if(!level().getFluidState(floor).isEmpty()||!level().getFluidState(floor.above()).isEmpty())return false;
        return monsterState().context().generation()==null||com.stardew.craft.mining.OrdinaryMineRuntime.floorAt(net.minecraft.core.BlockPos.containing(point))==monsterState().context().floor();
    }
    private boolean resolveInitialPatrolSpace() {
        if(validPatrolSpace(position()))return true;
        // Population checks a one-block cell, but this body is 1.3 blocks wide. Adjust only
        // by at most 0.25 blocks per axis after population completes (also on reload).
        Vec3 origin=position(),best=null;double distance=Double.MAX_VALUE;
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
            var point=origin.add(x*.125,0,z*.125);double d=point.distanceToSqr(origin);
            if(d<distance&&validPatrolSpace(point)){best=point;distance=d;}
        }
        if(best==null)return false;
        setPos(best);return true;
    }
    private boolean canStep(double x,double z) {
        var delta=new Vec3(x,0,z);
        return validPatrolSpace(position().add(delta))&&MonsterSpace.blocks(this,patrolBox(),delta)==null;
    }
    private boolean tryStep(double x,double z) {
        if(!canStep(x,z))return false;
        var before=position();move(MoverType.SELF,new Vec3(x,0,z));
        return Math.abs(getX()-before.x-x)<1e-5&&Math.abs(getZ()-before.z-z)<1e-5;
    }
    @Override public void travel(Vec3 input) { setDeltaMovement(Vec3.ZERO); }
    @Override public void knockback(double strength,double dx,double dz) {
        if(armored||level().isClientSide||dx*dx+dz*dz<.000001)return;
        super.knockback(strength,dx,dz);
        var impulse=getDeltaMovement();
        // Existing combat impulse is blocks/tick. Convert to source pixels/substep, then Bug's /3.
        double x=impulse.x*64/3,z=impulse.z*64/3;
        if(Math.abs(x)>Math.abs(slideX))slideX=x;if(Math.abs(z)>Math.abs(slideZ))slideZ=z;
        setDeltaMovement(Vec3.ZERO);
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(armored&&!source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)&&!source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
            boolean valid=source.getEntity() instanceof net.minecraft.world.entity.player.Player player
                    &&!source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                    &&isMeleeWeapon(player.getMainHandItem())
                    &&com.stardew.craft.enchantment.StardewEnchantments.has(player.getMainHandItem(),com.stardew.craft.enchantment.StardewEnchantments.BUG_KILLER);
            if(!valid){if(!level().isClientSide)playSound(ModSounds.CRAFTING.get(),1,1);return false;}
        }
        float before=getHealth();boolean accepted=super.hurt(source,amount);
        if(!level().isClientSide&&getHealth()<before)entityData.set(HIT_START,level().getGameTime());
        return accepted;
    }
    private static boolean isMeleeWeapon(net.minecraft.world.item.ItemStack stack) {
        if(stack.getItem() instanceof net.minecraft.world.item.SwordItem)return true;
        var data=com.stardew.craft.api.v1.equipment.StardewEquipmentDataApi.get(stack);
        return data!=null&&data.weapon().isPresent();
    }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.MONSTER_BAT_HIT.get(); }
    @Override protected SoundEvent getDeathSound() { return ModSounds.SLIMEDEAD.get(); }
    @Override protected void onFinalDeath(DamageSource source) {
        slideX=slideZ=0;
        if(level() instanceof ServerLevel server)server.sendParticles(new DustParticleOptions(new Vector3f(.93F,.51F,.93F),.7F),getX(),getY()+.5,getZ(),18,.25,.15,.25,.03);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);tag.putInt("BugStun",stunMilliseconds);tag.putInt("BugFacing",patrolFacing());tag.putDouble("BugSlideX",slideX);tag.putDouble("BugSlideZ",slideZ);tag.putDouble("BugFlightLift",flightLift);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);setNoGravity(true);checkPatrolPlacement=true;patrolRetryTicks=0;stunMilliseconds=tag.getInt("BugStun");
        setPatrolFacing(tag.contains("BugFacing")?tag.getInt("BugFacing"):Math.round((getYRot()+180)/90));
        slideX=tag.getDouble("BugSlideX");slideZ=tag.getDouble("BugSlideZ");
        flightLift=tag.getDouble("BugFlightLift");raiseFlight();
    }
}
