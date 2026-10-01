package com.stardew.craft.entity.monster;
import com.stardew.craft.monster.*;
import com.stardew.craft.mining.OrdinaryMineRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.entity.ModEntities;
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
import net.minecraft.world.phys.*;
/** SquidKid's grounded-glider trajectory, random expressions, one-shot firing and bobbing. */
@SuppressWarnings("null")
public final class MineSquidKidEntity extends StardewMonsterEntity {
    public static final float WIDTH=.94F,HEIGHT=.9F;
    private static final EntityDataAccessor<Integer> EXPRESSION=SynchedEntityData.defineId(MineSquidKidEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineSquidKidEntity.class,EntityDataSerializers.LONG),FIRE=SynchedEntityData.defineId(MineSquidKidEntity.class,EntityDataSerializers.LONG);
    private final SquidKidBehavior behavior=new SquidKidBehavior();private double groundY;private int stunMilliseconds;
    public MineSquidKidEntity(EntityType<? extends MineSquidKidEntity> type,Level level){super(type,level);setNoGravity(true);addTag("sd_mob_squid_kid");}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,1).add(Attributes.ATTACK_DAMAGE,18).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,64).add(Attributes.STEP_HEIGHT,0);}
    @Override protected void registerGoals(){}
    @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft:squid_kid");}
    @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){var r=MonsterStatResolver.base(d,c,random);setInitialHealth(r.initialHealth());replaceCombatStats(r.combat());groundY=getY();setPos(getX(),groundY+SquidKidBehavior.lift(level().getGameTime(),0),getZ());}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(EXPRESSION,0);b.define(HIT,-100L);b.define(FIRE,-100L);}
    public int expression(){return entityData.get(EXPRESSION);}public double groundY(){return groundY;}
    public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}public double fireTime(float p){return (level().getGameTime()-entityData.get(FIRE)+p)/20.;}
    public void stunFor(int milliseconds){stunMilliseconds=Math.max(stunMilliseconds,milliseconds);}
    private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS)&&(monsterState().context().generation()==null||OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
    @Override protected void customServerAiStep(){
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));
        var target=level().getNearestPlayer(getX(),groundY,getZ(),64,e->e instanceof Player p&&valid(p));setTarget(target);
        for(int i=0;i<3;i++){
            if(stunMilliseconds<=0){
                moveTrajectory();
                if(target!=null){
                    double dx=target.getX()-getX(),dz=target.getZ()-getZ();int dir=Math.abs(dx)>Math.abs(dz)?(dx>0?1:3):(dz>0?2:0);setYRot(dir*90-180);yBodyRot=getYRot();setYHeadRot(getYRot());
                    boolean near=MineMonsterSight.sees(this,target,6);
                    if(behavior.step(near,dx,dz,random)==SquidKidBehavior.Action.FIRE){var ball=ModEntities.SQUID_FIREBALL.get().create(level());ball.launch(this,target);level().addFreshEntity(ball);entityData.set(FIRE,level().getGameTime());playSound(ModSounds.FIREBALL.get(),1,(float)Math.pow(2,-.188+random.nextDouble()*.588));}
                }
                var contactBox=getBoundingBox();for(var p:((ServerLevel)level()).players())if(valid(p)&&contactBox.intersects(p.getBoundingBox())){var attack=MonsterDamageSource.contact(this);p.hurt(attack,attack.baseDamage());}
            }else stunMilliseconds=Math.max(0,stunMilliseconds-16);
            behavior.animate(random);setPos(getX(),groundY+SquidKidBehavior.lift(level().getGameTime(),i),getZ());
        }
        entityData.set(EXPRESSION,behavior.expression());
    }
    private boolean blocked(AABB b){return !level().noCollision(this,b)||level().containsAnyLiquid(b);}
    private void moveTrajectory(){
        double x=behavior.x(),z=behavior.z();if(x==0&&z==0)return;
        var base=getBoundingBox();int steps=Math.max(1,(int)Math.ceil(Math.max(Math.abs((int)x)/64/base.getXsize(),Math.abs((int)z)/64/base.getZsize())));AABB next=base;boolean collision=false;
        for(int i=1;i<=steps;i++){next=base.move((int)x/64.*i/steps,0,(int)z/64.*i/steps);if(blocked(next)){collision=true;break;}}
        if(!collision)setPos(getX()+x/64,getY(),getZ()+z/64);
        else{
            double cx=next.getCenter().x,cz=next.getCenter().z;
            boolean hz=blocked(new AABB(cx,next.minY,next.minZ,cx+1./64,next.maxY,next.maxZ));boolean hx=blocked(new AABB(next.minX,next.minY,cz,next.maxX,next.maxY,cz+1./64));
            behavior.reflect(hx,hz);if(hx){setPos(getX()+Math.signum(behavior.x())/64,getY(),getZ());random.nextInt(-10,11);}if(hz){setPos(getX(),getY(),getZ()+Math.signum(behavior.z())/64);random.nextInt(-10,11);}
        }
        behavior.decay(collision);
    }
    @Override public boolean hurt(DamageSource s,float amount){float hp=getHealth();boolean hit=super.hurt(s,amount);if(!level().isClientSide&&getHealth()<hp){behavior.hit();entityData.set(EXPRESSION,3);entityData.set(HIT,level().getGameTime());}return hit;}
    @Override public void knockback(double strength,double x,double z){super.knockback(strength,x,z);var v=getDeltaMovement();behavior.knockback(v.x*64,v.z*64);setDeltaMovement(Vec3.ZERO);}
    @Override protected SoundEvent getHurtSound(DamageSource s){return ModSounds.MONSTER_BAT_HIT.get();}@Override protected SoundEvent getDeathSound(){return null;}@Override public float getVoicePitch(){return 1;}
    @Override protected void onFinalDeath(DamageSource s){playSound(ModSounds.FIREBALL.get(),1,(float)Math.pow(2,-.188+random.nextDouble()*.588));}
    @Override public void tick(){super.tick();if(!level().isClientSide&&deathTime>=2&&deathTime<=8&&deathTime%2==0)((ServerLevel)level()).sendParticles(com.stardew.craft.weather.ModParticles.SQUID_DEATH_SPARK.get(),getX()+(-16+random.nextInt(64))/64.,getY()+random.nextInt(64)/64.-.5,getZ(),1,0,0,0,0);}
    @Override public void travel(Vec3 v){setDeltaMovement(Vec3.ZERO);}@Override public boolean isPushable(){return false;}@Override public void push(Entity e){}@Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("SquidBehavior",behavior.save());t.putDouble("GroundY",groundY);t.putInt("SquidStun",stunMilliseconds);t.putLong("FireTick",entityData.get(FIRE));}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);behavior.load(t.getCompound("SquidBehavior"));groundY=t.getDouble("GroundY");stunMilliseconds=t.getInt("SquidStun");entityData.set(FIRE,t.getLong("FireTick"));entityData.set(EXPRESSION,behavior.expression());setNoGravity(true);}
}
