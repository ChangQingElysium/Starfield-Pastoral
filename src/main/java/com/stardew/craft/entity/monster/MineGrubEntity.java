package com.stardew.craft.entity.monster;

import com.stardew.craft.monster.*;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Ordinary Grub.cs: cardinal pursuit, wounded retreat, invulnerable pupa, non-death replacement. */
@SuppressWarnings("null")
public final class MineGrubEntity extends StardewMonsterEntity {
    public static final float WIDTH=1.1F,HEIGHT=.53F;
    private static final EntityDataAccessor<Boolean> MOVING=SynchedEntityData.defineId(MineGrubEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FORM_FRAME=SynchedEntityData.defineId(MineGrubEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineGrubEntity.class,EntityDataSerializers.LONG);
    private final GrubLifecycle lifecycle=new GrubLifecycle();
    private int direction=-1,facing=2;
    private double slideX,slideZ,fallSpeed,skipHorizontal,lastX,lastZ;
    public MineGrubEntity(EntityType<? extends MineGrubEntity> type,Level level){super(type,level);addTag("sd_mob_grub");}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,20)
            .add(Attributes.ATTACK_DAMAGE,4).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,64).add(Attributes.STEP_HEIGHT,.5);}
    @Override protected void registerGoals(){}
    @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft:grub");}
    @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){var r=MonsterStatResolver.base(d,c,random);setInitialHealth(r.initialHealth());replaceCombatStats(r.combat());facing=random.nextInt(4);face(facing);lastX=getX();lastZ=getZ();}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(MOVING,false);b.define(FORM_FRAME,16);b.define(HIT,-100L);}
    public boolean moving(){return entityData.get(MOVING);}
    public double formProgress(){return Math.clamp((entityData.get(FORM_FRAME)-16)/3.,0,1);}
    public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}
    public GrubLifecycle lifecycle(){return lifecycle;}
    private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS)
            &&(monsterState().context().generation()==null||com.stardew.craft.mining.OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
    @Override protected void customServerAiStep(){
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));
        var target=level().getNearestPlayer(getX(),getY(),getZ(),64,e->e instanceof Player p&&valid(p));setTarget(target);
        double bx=getX(),bz=getZ();
        for(int i=0;i<3&&!isRemoved();i++){
            boolean wounded=getHealth()<=monsterState().sourceMaxHealth()/2-2;
            boolean mobile=lifecycle.phase()!=GrubLifecycle.PUPA&&(!wounded||lifecycle.remaining()>1000./60);
            double beforeX=getX(),beforeZ=getZ();
            if(mobile){
                if(wounded&&target!=null){
                    double dx=target.getX()-getX(),dz=target.getZ()-getZ();
                    if(Math.abs(dz)>2)direction=dx>0?3:1;
                    else if(Math.abs(dx)>2)direction=dz>0?0:2;
                } else if(target!=null&&Math.abs(target.getX()-getX())<=3&&Math.abs(target.getZ()-getZ())<=3){
                    if(skipHorizontal<=0){
                        if(getX()==lastX&&getZ()==lastZ&&random.nextDouble()<.001){direction=facing%2==1?(random.nextBoolean()?0:2):(random.nextBoolean()?1:3);skipHorizontal=700;}
                        else choose(target,getX()==lastX);
                    }else skipHorizontal-=1000./60;
                }else if(direction!=-1){direction=-1;face(random.nextInt(4));}
                lastX=getX();lastZ=getZ();
                if(slideX!=0||slideZ!=0){move(MoverType.SELF,new Vec3(slideX/64,0,slideZ/64));slideX*=.75;slideZ*=.75;if(Math.abs(slideX)<=.05)slideX=0;if(Math.abs(slideZ)<=.05)slideZ=0;}
                if(direction>=0&&!tryDirection(direction))direction=-1;
            }
            lifecycle.step(1000./60,getHealth(),monsterState().sourceMaxHealth(),getX()!=beforeX||getZ()!=beforeZ,facing);
            startAction(lifecycle.phase(),false);entityData.set(FORM_FRAME,lifecycle.frame());
            if(lifecycle.ready())transform();
        }
        entityData.set(MOVING,getX()!=bx||getZ()!=bz);
        if(!isRemoved())for(var p:((ServerLevel)level()).players())if(valid(p)&&getBoundingBox().intersects(p.getBoundingBox())){var attack=MonsterDamageSource.contact(this);p.hurt(attack,attack.baseDamage());}
    }
    private void choose(Player target,boolean horizontal){
        double dx=target.getX()-getX(),dz=target.getZ()-getZ();
        int h=Math.abs(dx)>.25?(dx>0?1:3):-1,v=Math.abs(dz)>.25?(dz>0?2:0):-1;
        for(int d:new int[]{horizontal?h:v,horizontal?v:h})if(d>=0){direction=d;if(clear(d)){skipHorizontal=500;return;}}
        if(h<0&&v<0)direction=-1;
    }
    private Vec3 step(int d){double speed=getAttributeValue(Attributes.MOVEMENT_SPEED)/.25/64.;return new Vec3(d==1?speed:d==3?-speed:0,0,d==2?speed:d==0?-speed:0);}
    private boolean clear(int d){var delta=step(d);var floor=BlockPos.containing(getX()+delta.x,getY()-.05,getZ()+delta.z);
        return level().noCollision(this,getBoundingBox().move(delta))&&level().getFluidState(floor).isEmpty()&&level().getFluidState(floor.above()).isEmpty()
                &&!level().getBlockState(floor).getCollisionShape(level(),floor).isEmpty();}
    private boolean tryDirection(int d){if(!clear(d))return false;move(MoverType.SELF,step(d));face(d);return true;}
    private void face(int d){facing=d;setYRot(d*90-180);yBodyRot=getYRot();setYHeadRot(getYRot());}
    private void transform(){
        var child=ModEntities.FLY.get().create(level());if(child==null)return;
        child.moveTo(getX(),getY(),getZ(),getYRot(),0);child.initialize(monsterState().context().offspring());
        if(MonsterFactory.replace(this,child))((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(.39F,.67F,.48F),.55F),getX(),getY()+.3,getZ(),12,.2,.12,.2,.08);
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(source.is(DamageTypes.GENERIC_KILL)||source.is(DamageTypes.FELL_OUT_OF_WORLD))return super.hurt(source,amount);
        if(lifecycle.phase()==GrubLifecycle.PUPA){
            // Source feedback belongs to an actual attack on the invulnerable pupa. Minecraft's
            // suffocation probe is an environmental placement check and must not loop hit sounds.
            if(!source.is(DamageTypes.IN_WALL)&&!level().isClientSide){playSound(ModSounds.MONSTER_SLIME_HIT.get(),1,1);playSound(ModSounds.CRAFTING.get(),1,1);}
            return false;
        }
        float hp=getHealth();boolean result=super.hurt(source,amount);if(!level().isClientSide&&getHealth()<hp)entityData.set(HIT,level().getGameTime());return result;
    }
    @Override public void knockback(double strength,double x,double z){super.knockback(strength,x,z);var v=getDeltaMovement();double factor=lifecycle.phase()==GrubLifecycle.PUPA?.5:1;
        if(Math.abs(v.x*64/3*factor)>Math.abs(slideX))slideX=v.x*64/3*factor;if(Math.abs(v.z*64/3*factor)>Math.abs(slideZ))slideZ=v.z*64/3*factor;setDeltaMovement(Vec3.ZERO);}
    @Override public void travel(Vec3 input){fallSpeed=isNoGravity()?0:onGround()?-.08:Math.max(-3.9,(fallSpeed-.08)*.98);move(MoverType.SELF,new Vec3(0,fallSpeed,0));setDeltaMovement(Vec3.ZERO);}
    @Override public boolean isPushable(){return false;}
    @Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
    @Override protected SoundEvent getHurtSound(DamageSource s){return ModSounds.MONSTER_SLIME_HIT.get();}
    @Override protected SoundEvent getDeathSound(){return ModSounds.SLIMEDEAD.get();}
    @Override protected void onFinalDeath(DamageSource s){slideX=slideZ=0;((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(.93F,.57F,.22F),.6F),getX(),getY()+.3,getZ(),15,.2,.1,.2,.03);}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("GrubLifecycle",lifecycle.save());t.putInt("GrubDirection",direction);t.putInt("GrubFacing",facing);t.putDouble("GrubSlideX",slideX);t.putDouble("GrubSlideZ",slideZ);t.putDouble("GrubSkip",skipHorizontal);t.putDouble("GrubLastX",lastX);t.putDouble("GrubLastZ",lastZ);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);if(t.contains("GrubLifecycle"))lifecycle.load(t.getCompound("GrubLifecycle"));direction=t.contains("GrubDirection")?t.getInt("GrubDirection"):-1;face(t.getInt("GrubFacing"));slideX=t.getDouble("GrubSlideX");slideZ=t.getDouble("GrubSlideZ");skipHorizontal=t.getDouble("GrubSkip");lastX=t.getDouble("GrubLastX");lastZ=t.getDouble("GrubLastZ");entityData.set(FORM_FRAME,lifecycle.frame());}
}
