package com.stardew.craft.entity.monster;

import com.stardew.craft.monster.*;
import com.stardew.craft.mining.*;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;
import java.util.*;

/** Ordinary Skeleton.cs; the dangerous mage is a different, disabled variant. */
@SuppressWarnings("null")
public final class MineSkeletonEntity extends StardewMonsterEntity {
    public static final float WIDTH=.66F,HEIGHT=1.79F;
    private static final EntityDataAccessor<Boolean> MOVING=SynchedEntityData.defineId(MineSkeletonEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> THROW_PROGRESS=SynchedEntityData.defineId(MineSkeletonEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineSkeletonEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> RELEASE=SynchedEntityData.defineId(MineSkeletonEntity.class,EntityDataSerializers.LONG);
    private final SkeletonThrowClock clock=new SkeletonThrowClock();
    private final SourceGroundMovement movement=new SourceGroundMovement(this,2,3);
    private int attemptTimer,stunMilliseconds,puffTicks=-1;
    private double fallSpeed;
    private boolean spotted;
    public MineSkeletonEntity(EntityType<? extends MineSkeletonEntity> type,Level level){super(type,level);addTag("sd_mob_skeleton");}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,140).add(Attributes.ATTACK_DAMAGE,10).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,64).add(com.stardew.craft.port.PortAttributes.STEP_HEIGHT.get(),0);}
    @Override protected void registerGoals(){}
    @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft:skeleton");}
    @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){var r=MonsterStatResolver.base(d,c,random);setInitialHealth(r.initialHealth());replaceCombatStats(r.combat());movement.face(random.nextInt(4));}
    @Override protected void defineSynchedData(){super.defineSynchedData();this.entityData.define(MOVING,false);this.entityData.define(THROW_PROGRESS,0F);this.entityData.define(HIT,-100L);this.entityData.define(RELEASE,-100L);}
    public boolean moving(){return entityData.get(MOVING);}
    public double throwProgress(float partial){return Math.min(1,entityData.get(THROW_PROGRESS)+(phase()==1?partial/12.:0));}
    public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}
    public double releaseTime(float p){return (level().getGameTime()-entityData.get(RELEASE)+p)/20.;}
    public boolean spotted(){return spotted;}
    public SkeletonThrowClock throwClock(){return clock;}
    public void stunFor(int milliseconds){stunMilliseconds=Math.max(stunMilliseconds,milliseconds);}
    private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS.get())&&(monsterState().context().generation()==null||OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
    @Override protected void customServerAiStep(){
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));
        var target=level().getNearestPlayer(getX(),getY(),getZ(),64,e->e instanceof Player p&&valid(p));setTarget(target);double bx=getX(),bz=getZ();
        for(int i=0;i<3;i++){
            boolean wasThrowing=clock.throwing();
            if(!wasThrowing){
                if(stunMilliseconds<=0){movement.tick(target,spotted,8,false,0,clock::walk);}
            }
            if(wasThrowing||stunMilliseconds<=0){
                if(target!=null){
                    if(!spotted&&MineMonsterSight.sees(this,target,8)){movement.pathTo(target,200);spotted=true;if(!movement.hasPath()){movement.halt();movement.faceTarget(target);}playSound(ModSounds.SKELETON_STEP.get(),1,1);}
                    else if(clock.throwing()){faceThrowTarget(target);if(clock.step()){releaseBone(target);entityData.set(RELEASE,level().getGameTime());}}
                    else if(spotted&&!movement.hasPath()&&random.nextDouble()<.003&&MineMonsterSight.sees(this,target,8)){clock.begin();movement.halt();faceThrowTarget(target);startAction(1,true);}
                    else if(movement.near(target,2))movement.clearPath();
                    else if(spotted&&!movement.hasPath()&&attemptTimer<=0){movement.pathTo(target,200);attemptTimer=1000;if(!movement.hasPath())movement.halt();}
                    attemptTimer-=16;
                }
            }else stunMilliseconds=Math.max(0,stunMilliseconds-16);
            // Skeleton's throwing branch bypasses Monster.update, including its near-player controller cancellation.
            if(!wasThrowing&&target!=null&&movement.near(target,3))movement.clearPath();
            startAction(clock.throwing()?1:0,false);entityData.set(THROW_PROGRESS,(float)clock.progress());
            for(var p:((ServerLevel)level()).players())if(valid(p)&&getBoundingBox().intersects(p.getBoundingBox())){var attack=MonsterDamageSource.contact(this);p.hurt(attack,attack.baseDamage());}
        }
        // Movement may resume in the remaining source substeps after release; keep the visual follow-through aimed.
        if(target!=null&&(clock.throwing()||releaseTime(0)<.22))faceThrowTarget(target);
        entityData.set(MOVING,getX()!=bx||getZ()!=bz);
    }
    // The 2D source uses one south-facing throw sprite; the 3D arm must aim along the actual shot.
    private void faceThrowTarget(Player target){
        movement.faceTarget(target);
        float yaw=(float)Math.toDegrees(Math.atan2(getX()-target.getX(),target.getZ()-getZ()));
        setYRot(yaw);yBodyRot=yaw;setYHeadRot(yaw);
    }
    private void releaseBone(Player p){var bone=ModEntities.SKELETON_BONE.get().create(level());if(bone!=null){bone.launch(this,p);level().addFreshEntity(bone);}}
    @Override public void knockback(double strength,double x,double z){super.knockback(strength,x,z);var v=getDeltaMovement();movement.knockback(v.x*64/3,v.z*64/3);setDeltaMovement(Vec3.ZERO);}
    @Override public boolean hurt(DamageSource s,float amount){
        if(!level().isClientSide){playSound(ModSounds.SKELETON_HIT.get(),1,1);if(clock.throwing()){clock.interrupt();movement.halt();startAction(0,false);}if(getHealth()-amount<=0){puff(0);puffTicks=0;}}
        float hp=getHealth();boolean result=super.hurt(s,amount);if(!level().isClientSide&&getHealth()<hp)entityData.set(HIT,level().getGameTime());return result;
    }
    @Override public void tick(){super.tick();if(!level().isClientSide&&puffTicks>=0){if(++puffTicks==2)puff(-.25);else if(puffTicks==4){puff(.25);puffTicks=-1;}}}
    private void puff(double x){((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(.9F,.85F,.72F),.7F),getX()+x,getY()+.6,getZ(),8,.2,.3,.2,.025);}
    @Override protected SoundEvent getHurtSound(DamageSource s){return null;}
    @Override protected SoundEvent getDeathSound(){return ModSounds.SKELETON_DIE.get();}
    @Override protected void onFinalDeath(DamageSource s){((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(.84F,.71F,.5F),.5F),getX(),getY()+.7,getZ(),20,.3,.65,.3,.07);}
    @Override public void travel(Vec3 v){fallSpeed=isNoGravity()?0:onGround()?-.08:Math.max(-3.9,(fallSpeed-.08)*.98);move(MoverType.SELF,new Vec3(0,fallSpeed,0));setDeltaMovement(Vec3.ZERO);}
    @Override public boolean isPushable(){return false;}
    @Override public void push(Entity e){}
    @Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("SkeletonClock",clock.save());t.put("GroundMovement",movement.save());t.putBoolean("Spotted",spotted);t.putInt("AttemptTimer",attemptTimer);t.putInt("Stun",stunMilliseconds);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);clock.load(t.getCompound("SkeletonClock"));movement.load(t.getCompound("GroundMovement"));spotted=t.getBoolean("Spotted");attemptTimer=t.getInt("AttemptTimer");stunMilliseconds=t.getInt("Stun");entityData.set(THROW_PROGRESS,(float)clock.progress());}
}
