package com.stardew.craft.entity.monster;

import com.stardew.craft.monster.*;
import com.stardew.craft.effect.ModMobEffects;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Ordinary Fly.cs, including the emergence timer and independent hit steering lock. */
@SuppressWarnings("null")
public final class MineFlyEntity extends StardewMonsterEntity {
    public static final float WIDTH=1.2F,HEIGHT=.75F;
    public static final double FLIGHT_LIFT=.8;
    private static final EntityDataAccessor<Long> BIRTH=SynchedEntityData.defineId(MineFlyEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineFlyEntity.class,EntityDataSerializers.LONG);
    private final FlySteering steering=new FlySteering();
    private final MonsterFlightWander wander=new MonsterFlightWander();
    private double flightLift;
    public MineFlyEntity(EntityType<? extends MineFlyEntity> type,Level level){super(type,level);setNoGravity(true);noPhysics=false;addTag("sd_mob_fly");}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,22)
            .add(Attributes.ATTACK_DAMAGE,6).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,64).add(com.stardew.craft.port.PortAttributes.STEP_HEIGHT.get(),0);}
    @Override protected void registerGoals(){}
    @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level){return new MonsterFlightRoute(this,level);}
    private MonsterFlightRoute route(){return (MonsterFlightRoute)getNavigation();}
    @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft:fly");}
    @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){var r=MonsterStatResolver.base(d,c,random);setInitialHealth(r.initialHealth());replaceCombatStats(r.combat());
        steering.initialize(random);entityData.set(BIRTH,level().getGameTime());
        flightLift=.04;setPos(getX(),getY()+flightLift,getZ());MonsterFlightPlacement.ensureSpawnAir(this);}
    @Override protected void defineSynchedData(){super.defineSynchedData();this.entityData.define(BIRTH,0L);this.entityData.define(HIT,-100L);}
    public double spawnTime(float p){return (level().getGameTime()-entityData.get(BIRTH)+p)/20.;}
    public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}
    public FlySteering steering(){return steering;}
    private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS.get())
            &&(monsterState().context().generation()==null||com.stardew.craft.mining.OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
    @Override protected void customServerAiStep(){
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));
        if(isRemoved())return;
        var previous=getTarget();
        Player nearest=previous instanceof Player p&&valid(p)&&distanceToSqr(p)<64*64?p:null;
        if(nearest==null)nearest=level().getNearestPlayer(getX(),getY(),getZ(),64,e->e instanceof Player p&&valid(p)
                &&(getTags().contains("sd_focused_on_farmers")||MineMonsterSight.sees(this,p,20)));
        setTarget(nearest);var target=getTarget();
        if(previous!=target){wander.reset();route().invalidate();}
        Vec3 destination=target==null?wander.next(this,3,2):target.getBoundingBox().getCenter().add(0,.3-getBbHeight()/2.,0);
        Vec3 waypoint=route().waypoint(destination);
        for(int i=0;i<3;i++){
            boolean emerging=steering.spawnRemaining()>=0;
            steering.advance(i==2?18:16,waypoint==null?null:waypoint.subtract(position()),target!=null,!sourceHitRecoveryActive());
            if(emerging){
                // Newly emerged flies rise continuously out of the cocoon, stopping below low ceilings.
                double lift=.04+(FLIGHT_LIFT-.04)*com.stardew.craft.port.PortJava.clamp(1-steering.spawnRemaining()/1000.,0,1);
                Vec3 delta=new Vec3(0,Math.max(0,lift-flightLift),0);
                var wall=MonsterSpace.blocks(this,getBoundingBox().inflate(.025),delta);
                move(MoverType.SELF,delta.scale(wall==null?1:Math.max(0,wall.fraction()-1e-5)));flightLift=lift;
            }
            Vec3 actual=route().move(steering);
            if(actual.horizontalDistanceSqr()>1e-7){
                float yaw=(float)Math.toDegrees(Math.atan2(-actual.x,actual.z));
                setYRot(net.minecraft.util.Mth.rotLerp(.3F,getYRot(),yaw));yBodyRot=getYRot();setYHeadRot(getYRot());
            }
            if(outsideSourceMap()){MonsterFactory.cleanup(this);return;}
            for(var p:((ServerLevel)level()).players())if(valid(p)&&getBoundingBox().intersects(p.getBoundingBox())){var attack=MonsterDamageSource.contact(this);p.hurt(attack,attack.baseDamage());}
        }
    }
    private boolean outsideSourceMap(){
        if(monsterState().context().generation()==null)return false;
        int floor=monsterState().context().floor();
        var layout=com.stardew.craft.mining.OrdinaryMineLayout.load((ServerLevel)level(),floor);
        var origin=layout.origin(floor);
        double x=getX()-origin.getX()-layout.tileX,z=getZ()-origin.getZ()-layout.tileZ;
        return x<=-10||z<=-10||x>=layout.width+10||z>=layout.depth+10;
    }
    @Override public void travel(Vec3 input){setDeltaMovement(Vec3.ZERO);}
    @Override public void knockback(double strength,double x,double z){super.knockback(strength,x,z);var v=getDeltaMovement();
        steering.knockback(v.x*64/9,v.z*64/9);setDeltaMovement(Vec3.ZERO);}
    @Override public boolean hurt(DamageSource source,float amount){float hp=getHealth();boolean result=super.hurt(source,amount);if(!level().isClientSide&&getHealth()<hp){entityData.set(HIT,level().getGameTime());steering.hit();}return result;}
    @Override public boolean isPushable(){return false;}
    @Override public void push(Entity other){}
    @Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(.7);}
    @Override protected SoundEvent getHurtSound(DamageSource source){return ModSounds.MONSTER_BAT_HIT.get();}
    @Override protected SoundEvent getDeathSound(){return ModSounds.MONSTER_CRAB_DEATH.get();}
    @Override protected void onFinalDeath(DamageSource source){steering.stop();((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(1,.41F,.71F),.65F),getX(),getY()+.4,getZ(),18,.22,.16,.22,.03);}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("FlyFlight",steering.save());t.put("FlyWander",wander.save());t.putDouble("FlyLift",flightLift);t.putLong("FlyAge",level().getGameTime()-entityData.get(BIRTH));}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);if(t.contains("FlyFlight"))steering.load(t.getCompound("FlyFlight"));wander.load(t.getCompound("FlyWander"));route().invalidate();flightLift=t.getDouble("FlyLift");entityData.set(BIRTH,level().getGameTime()-t.getLong("FlyAge"));setNoGravity(true);noPhysics=false;}
}
