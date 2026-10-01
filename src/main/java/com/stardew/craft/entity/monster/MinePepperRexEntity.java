package com.stardew.craft.entity.monster;
import com.stardew.craft.monster.*;
import com.stardew.craft.mining.*;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.projectile.RexBreathEntity;
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
import net.minecraft.world.phys.Vec3;
/** Ordinary Pepper Rex. Existing breath projectiles survive their parent's accepted death. */
@SuppressWarnings("null")
public final class MinePepperRexEntity extends StardewMonsterEntity {
 public static final float WIDTH=1.5F,HEIGHT=1.75F;
 private static final EntityDataAccessor<Boolean> MOVING=SynchedEntityData.defineId(MinePepperRexEntity.class,EntityDataSerializers.BOOLEAN);
 private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MinePepperRexEntity.class,EntityDataSerializers.LONG);
 private final PepperRexBehavior behavior=new PepperRexBehavior();private final SourceGroundMovement movement=new SourceGroundMovement(this,2,2);private final java.util.UUID[] slots=new java.util.UUID[15];private int nextSlot,stunMilliseconds;private double fallSpeed;
 public MinePepperRexEntity(EntityType<? extends MinePepperRexEntity> t,Level l){super(t,l);addTag("sd_mob_dino");}
 public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,300).add(Attributes.ATTACK_DAMAGE,15).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,64).add(Attributes.STEP_HEIGHT,0);}
 @Override protected void registerGoals(){}
 @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft:pepper_rex");}
 @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){var r=MonsterStatResolver.base(d,c,random);setInitialHealth(r.initialHealth());replaceCombatStats(r.combat());behavior.initialize(random);movement.face(2);}
 @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(MOVING,false);b.define(HIT,-100L);}
 public boolean moving(){return entityData.get(MOVING);}public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}public PepperRexBehavior behavior(){return behavior;}public void stunFor(int ms){stunMilliseconds=Math.max(stunMilliseconds,ms);}
 private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS)&&(monsterState().context().generation()==null||OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
 private boolean near(Player p,int r){return p!=null&&MineMonsterSight.sees(this,p,r);}
 @Override protected void customServerAiStep(){
  if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));setTarget(level().getNearestPlayer(getX(),getY(),getZ(),64,e->e instanceof Player p&&valid(p)));var target=getTarget() instanceof Player p?p:null;double x=getX(),z=getZ();
  for(int i=0;i<3;i++){
   int ms=i==0?16:17;
   if(stunMilliseconds>0){stunMilliseconds=Math.max(0,stunMilliseconds-ms);continue;}
   // NPC base movement precedes DinoMonster.behaviorAtGameTick in the source.
   movement.tick(target,behavior.walking(),3,false,.01,()->{});
   if(behavior.phase()==0)behavior.facing(movement.facing());
   int facing=-1;if(target!=null){double dx=target.getX()-getX(),dz=target.getZ()-getZ();facing=Math.abs(dx)>Math.abs(dz)?(dx>0?1:3):(dz>0?2:0);}
   var event=behavior.tick(ms,near(target,3),near(target,2),facing,random);
   if(behavior.phase()!=0)movement.halt();
   else if(!near(target,3)&&behavior.wander()){movement.halt();movement.tryDirect(behavior.facing());}
   movement.face(behavior.facing());startAction(behavior.phase(),false);
   if(event==PepperRexBehavior.Event.PREPARE)playSound(ModSounds.CROAK.get(),1,1);
   if(event==PepperRexBehavior.Event.FIRST_SHOT||event==PepperRexBehavior.Event.SHOT){if(event==PepperRexBehavior.Event.FIRST_SHOT)playSound(ModSounds.FURNACE.get(),1,1);movement.breathRecoil(behavior.facing());fire();}
   for(var p:((ServerLevel)level()).players())if(valid(p)&&getBoundingBox().intersects(p.getBoundingBox())){var a=MonsterDamageSource.contact(this);p.hurt(a,a.baseDamage());}
  }
  entityData.set(MOVING,x!=getX()||z!=getZ());
 }
 private void fire(){
  var level=(ServerLevel)level();RexBreathEntity shot=slots[nextSlot]!=null&&level.getEntity(slots[nextSlot]) instanceof RexBreathEntity p&&!p.isRemoved()?p:null;boolean fresh=shot==null;if(fresh)shot=ModEntities.REX_BREATH.get().create(level);
  int f=behavior.facing();var origin=position().add(f==1?.95:f==3?-.95:0,.68,f==2?.95:f==0?-.95:0);shot.launch(this,origin,behavior.shotAngle());if(fresh)level.addFreshEntity(shot);slots[nextSlot]=shot.getUUID();nextSlot=(nextSlot+1)%slots.length;
 }
 @Override public boolean hurt(DamageSource s,float amount){float hp=getHealth();boolean result=super.hurt(s,amount);if(!level().isClientSide&&getHealth()<hp)entityData.set(HIT,level().getGameTime());return result;}
 @Override public void knockback(double strength,double x,double z){super.knockback(strength,x,z);var v=getDeltaMovement();movement.knockback(v.x*64/3,v.z*64/3);setDeltaMovement(Vec3.ZERO);}
 @Override protected SoundEvent getHurtSound(DamageSource s){return ModSounds.MONSTER_BAT_HIT.get();}@Override protected SoundEvent getDeathSound(){return null;}@Override public float getVoicePitch(){return 1;}
 @Override protected void onFinalDeath(DamageSource s){playSound(ModSounds.SKELETON_DIE.get(),1,1);playSound(ModSounds.MONSTER_REX_GRUNT.get(),1,(float)Math.pow(2,random.nextDouble()*.465));((ServerLevel)level()).sendParticles(com.stardew.craft.weather.ModParticles.REX_BONE_FRAGMENT.get(),getX(),getY()+.6,getZ(),16,.55,.35,.35,.12);pinkCloud(0,0);randomPinkCloud();}
 private void randomPinkCloud(){pinkCloud(random.nextInt(-48,49)/64.,random.nextInt(-32,33)/64.);}
 private void pinkCloud(double x,double z){((ServerLevel)level()).sendParticles(com.stardew.craft.weather.ModParticles.REX_DISSOLVE.get(),getX()+x,getY()+.65,getZ()+z,1,0,0,0,0);}
 @Override public void tick(){super.tick();if(!level().isClientSide&&deathTime>=2&&deathTime<=14&&deathTime%2==0)randomPinkCloud();}
 @Override public void travel(Vec3 v){fallSpeed=isNoGravity()?0:onGround()?-.08:Math.max(-3.9,(fallSpeed-.08)*.98);move(MoverType.SELF,new Vec3(0,fallSpeed,0));setDeltaMovement(Vec3.ZERO);}@Override public boolean isPushable(){return false;}@Override public void push(Entity e){}@Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
 @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(.65);}
 @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("RexBehavior",behavior.save());t.put("GroundMovement",movement.save());t.putInt("RexStun",stunMilliseconds);t.putInt("NextBreathSlot",nextSlot);for(int i=0;i<slots.length;i++)if(slots[i]!=null)t.putUUID("BreathSlot"+i,slots[i]);}
 @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);behavior.load(t.getCompound("RexBehavior"));movement.load(t.getCompound("GroundMovement"));stunMilliseconds=t.getInt("RexStun");nextSlot=t.getInt("NextBreathSlot");for(int i=0;i<slots.length;i++)slots[i]=t.hasUUID("BreathSlot"+i)?t.getUUID("BreathSlot"+i):null;}
}
