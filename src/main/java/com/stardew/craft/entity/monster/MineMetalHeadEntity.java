package com.stardew.craft.entity.monster;
import com.stardew.craft.monster.*;
import com.stardew.craft.mining.OrdinaryMineRuntime;
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
/** MetalHead's ordinary constructor and base Monster cardinal movement; no HotHead bomb behavior. */
@SuppressWarnings("null")
public final class MineMetalHeadEntity extends StardewMonsterEntity {
    public static final float WIDTH=.74F,HEIGHT=.94F;
    private static final EntityDataAccessor<Boolean> MOVING=SynchedEntityData.defineId(MineMetalHeadEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> COLOR=SynchedEntityData.defineId(MineMetalHeadEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineMetalHeadEntity.class,EntityDataSerializers.LONG);
    private final SourceGroundMovement movement=new SourceGroundMovement(this,2,2);private double fallSpeed;private int stunMilliseconds;
    public MineMetalHeadEntity(EntityType<? extends MineMetalHeadEntity> type,Level level){super(type,level);addTag("sd_mob_metal_head");}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,40).add(Attributes.ATTACK_DAMAGE,15).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,64).add(Attributes.STEP_HEIGHT,0);}
    @Override protected void registerGoals(){}
    @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft:metal_head");}
    @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){var r=MonsterStatResolver.base(d,c,random);int area=c.floor()>120?121:c.floor()>=80?80:c.floor()>=40?40:0;setInitialHealth(r.initialHealth()*(area==80?3:area==40?2:1));replaceCombatStats(r.combat());entityData.set(COLOR,area==40?0x40e0d0:0xffffff);movement.face(2);}
    @Override protected void defineSynchedData(){super.defineSynchedData();this.entityData.define(MOVING,false);this.entityData.define(COLOR,0xffffff);this.entityData.define(HIT,-100L);}
    public boolean moving(){return entityData.get(MOVING);}
    public int color(){return entityData.get(COLOR);}
    public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}
    public void stunFor(int milliseconds){stunMilliseconds=Math.max(stunMilliseconds,milliseconds);}
    private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS.get())&&(monsterState().context().generation()==null||OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
    @Override protected void customServerAiStep(){
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));var target=level().getNearestPlayer(getX(),getY(),getZ(),64,e->e instanceof Player p&&valid(p));setTarget(target);double x=getX(),z=getZ();
        for(int i=0;i<3;i++){
            if(stunMilliseconds<=0)movement.tick(target,true,8,false,0,()->{});else stunMilliseconds=Math.max(0,stunMilliseconds-16);
            for(var p:((ServerLevel)level()).players())if(valid(p)&&getBoundingBox().intersects(p.getBoundingBox())){var attack=MonsterDamageSource.contact(this);p.hurt(attack,attack.baseDamage());}
        }
        entityData.set(MOVING,x!=getX()||z!=getZ());
    }
    @Override public boolean hurt(DamageSource s,float amount){float hp=getHealth();boolean hit=super.hurt(s,amount);if(!level().isClientSide&&getHealth()<hp)entityData.set(HIT,level().getGameTime());return hit;}
    @Override public void knockback(double strength,double x,double z){super.knockback(strength,x,z);var v=getDeltaMovement();movement.knockback(v.x*64/3,v.z*64/3);setDeltaMovement(Vec3.ZERO);}
    @Override protected SoundEvent getHurtSound(DamageSource s){return ModSounds.CLANK.get();}
    @Override protected SoundEvent getDeathSound(){return ModSounds.MONSTER_CRAB_DEATH.get();}
    @Override public void tick(){super.tick();if(!level().isClientSide&&(deathTime==1||deathTime==7||deathTime==13)){float x=deathTime==7?-.5F:deathTime==13?.5F:0;((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(.30F,.32F,.34F),.8F),getX()+x,getY()+.3,getZ(),10,.18,.2,.18,.04);}}
    @Override protected void onFinalDeath(DamageSource s){((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(.53F,.39F,.69F),.65F),getX(),getY()+.2,getZ(),10,.25,.1,.25,.03);}
    @Override public void travel(Vec3 v){fallSpeed=isNoGravity()?0:onGround()?-.08:Math.max(-3.9,(fallSpeed-.08)*.98);move(MoverType.SELF,new Vec3(0,fallSpeed,0));setDeltaMovement(Vec3.ZERO);}
    @Override public boolean isPushable(){return false;}
    @Override public void push(Entity e){}
    @Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("GroundMovement",movement.save());t.putInt("MetalColor",color());t.putInt("MetalStun",stunMilliseconds);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);movement.load(t.getCompound("GroundMovement"));stunMilliseconds=t.getInt("MetalStun");entityData.set(COLOR,t.contains("MetalColor")?t.getInt("MetalColor"):0xffffff);}
}
