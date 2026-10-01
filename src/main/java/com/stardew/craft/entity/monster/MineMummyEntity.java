package com.stardew.craft.entity.monster;
import com.stardew.craft.monster.*;
import com.stardew.craft.combat.MonsterStats;
import com.stardew.craft.sound.ModSounds;
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
/** Mummy.cs: a collapse is never a death, and the ten-second wait excludes crumble frames. */
@SuppressWarnings("null")
public final class MineMummyEntity extends StardewMonsterEntity {
 public static final float WIDTH=1.02F,HEIGHT=1.84F;
 private static final EntityDataAccessor<Boolean> MOVING=SynchedEntityData.defineId(MineMummyEntity.class,EntityDataSerializers.BOOLEAN);
 private static final EntityDataAccessor<Integer> REMAINING=SynchedEntityData.defineId(MineMummyEntity.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineMummyEntity.class,EntityDataSerializers.LONG);
 private final MummyLifecycle lifecycle=new MummyLifecycle();private final SourceGroundMovement movement=new SourceGroundMovement(this,2,2);
 private DamageSource incoming;private boolean finishing,collapsedThisHit;private int contactDamage,stunMilliseconds;private double fallSpeed;
 public MineMummyEntity(EntityType<? extends MineMummyEntity> type,Level level){super(type,level);addTag("sd_mob_mummy");}
 public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,260).add(Attributes.ATTACK_DAMAGE,30).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,128).add(Attributes.STEP_HEIGHT,0);}
 @Override public void tick(){super.tick();setBoundingBox(MummyLifecycle.collisionBox(getX(),getY(),getZ(),getYRot(),getScale(),phase()));}
 @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(.5);}
 @Override protected void registerGoals(){}
 @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft:mummy");}
 @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){var base=MonsterStatResolver.base(d,c,random);setInitialHealth(base.initialHealth());replaceCombatStats(base.combat());contactDamage=Math.round(base.combat().getDamage());}
 @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(MOVING,false);b.define(REMAINING,0);b.define(HIT,-100L);}
 public boolean moving(){return entityData.get(MOVING);}public boolean collapsed(){return phase()==MummyLifecycle.CRUMBLE||phase()==MummyLifecycle.DOWNED;}
 public int reviveRemaining(){return entityData.get(REMAINING);}public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}
 public void stunFor(int milliseconds){stunMilliseconds=Math.max(stunMilliseconds,milliseconds);}
 private void damage(int value){var s=monsterState().stats();replaceCombatStats(MonsterStats.builder().damage(value).resilience(s.getResilience()).missChance(s.getMissChance()).experience(s.getExperience()).build());}
 private static boolean admin(DamageSource s){return s.is(DamageTypes.GENERIC_KILL)||s.is(DamageTypes.FELL_OUT_OF_WORLD);}
 private static boolean crusader(DamageSource s){if(!(s.getEntity() instanceof Player p)||s.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION))return false;var item=p.getMainHandItem();var data=com.stardew.craft.api.v1.equipment.StardewEquipmentDataApi.get(item);return (item.getItem() instanceof net.minecraft.world.item.SwordItem||data!=null&&data.weapon().isPresent())&&com.stardew.craft.enchantment.StardewEnchantments.has(item,com.stardew.craft.enchantment.StardewEnchantments.CRUSADER);}
 @Override public boolean hurt(DamageSource source,float amount){
  if(!level().isClientSide&&!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,121));
  boolean wasCollapsed=collapsed(),bomb=source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
  if(wasCollapsed&&!bomb&&!admin(source))return false;
  incoming=source;finishing=admin(source)||wasCollapsed&&bomb||crusader(source);collapsedThisHit=false;
  if(wasCollapsed&&finishing){monsterState().life(MonsterState.Life.ALIVE);invulnerableTime=0;}
  float before=getHealth();
  try {boolean accepted=super.hurt(source,wasCollapsed&&bomb?999:amount);
   if(!level().isClientSide&&(getHealth()<before||collapsedThisHit)){if(!collapsed())entityData.set(HIT,level().getGameTime());}
   if(wasCollapsed&&isAlive())monsterState().life(MonsterState.Life.DOWNED);
   return accepted;
  }finally{incoming=null;finishing=false;}
 }
 @Override public void setHealth(float value){
  // LivingEntity applies final resolved damage here before LivingDamage.Post. Restore
  // source MaxHealth now, so no death/kill reward can briefly escape during a collapse.
  if(incoming!=null&&value<getHealth()&&!level().isClientSide&&!collapsed()){playSound(ModSounds.SHADOW_HIT.get(),1,1);playSound(ModSounds.SKELETON_STEP.get(),1,1);}
  if(value<=0&&incoming!=null&&!finishing&&initialized()&&!level().isClientSide){
   super.setHealth(monsterState().sourceMaxHealth());lifecycle.crumble();startAction(MummyLifecycle.CRUMBLE,true);entityData.set(REMAINING,10000);monsterState().life(MonsterState.Life.DOWNED);damage(0);movement.halt();movement.clearPath();entityData.set(MOVING,false);collapsedThisHit=true;playSound(ModSounds.MONSTER_CRAB_DEATH.get(),1,1);return;
  }
  super.setHealth(value);
 }
 private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(com.stardew.craft.effect.ModMobEffects.AVOID_MONSTERS)&&(monsterState().context().generation()==null||com.stardew.craft.mining.OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
 @Override protected void customServerAiStep(){
  if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,121));
  if(lifecycle.tick(50)){startAction(lifecycle.phase(),true);if(lifecycle.phase()==MummyLifecycle.REVIVE){monsterState().life(MonsterState.Life.ALIVE);damage(contactDamage);playSound(ModSounds.SKELETON_DIE.get(),1,1);}refreshDimensions();}
  entityData.set(REMAINING,lifecycle.remaining());var target=level().getNearestPlayer(getX(),getY(),getZ(),128,e->e instanceof Player p&&valid(p));setTarget(target);double x=getX(),z=getZ();
  for(int i=0;i<3;i++){if(stunMilliseconds>0){stunMilliseconds=Math.max(0,stunMilliseconds-16);continue;}if(lifecycle.collapsed())movement.tick(null,false,-1,false,0,()->{});else movement.tick(target,true,8,lifecycle.phase()==MummyLifecycle.REVIVE,.01,()->{});}
  entityData.set(MOVING,!collapsed()&&(getX()!=x||getZ()!=z));
  if(!collapsed())for(var p:((ServerLevel)level()).players())if(valid(p)&&getBoundingBox().intersects(p.getBoundingBox())){var attack=MonsterDamageSource.contact(this);p.hurt(attack,attack.baseDamage());}
 }
 @Override public void knockback(double strength,double x,double z){super.knockback(strength,x,z);var v=getDeltaMovement();movement.knockback(v.x*64,v.z*64);setDeltaMovement(Vec3.ZERO);}
 @Override public void travel(Vec3 v){fallSpeed=isNoGravity()?0:onGround()?-.08:Math.max(-3.9,(fallSpeed-.08)*.98);move(MoverType.SELF,new Vec3(0,fallSpeed,0));setDeltaMovement(Vec3.ZERO);}
 @Override public boolean isPushable(){return false;}@Override public void push(Entity entity){}
 @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return false;}
 @Override protected SoundEvent getHurtSound(DamageSource source){return null;}
 @Override protected SoundEvent getDeathSound(){return null;}
 private final double[][] burstOffsets=new double[4][2];
 private void dissolve(double x,double z){if(level() instanceof ServerLevel server)server.sendParticles(com.stardew.craft.weather.ModParticles.MUMMY_DISSOLVE.get(),getX()+x,getY()+.625,getZ()+z,1,0,0,0,0);}
 @Override protected void onFinalDeath(DamageSource source){movement.halt();playSound(ModSounds.GHOST.get(),1,1);for(var offset:burstOffsets){offset[0]=random.nextInt(-32,33)/64.;offset[1]=random.nextInt(-32,33)/64.;}dissolve(0,0);dissolve(burstOffsets[0][0],burstOffsets[0][1]);}
 @Override protected void tickDeath(){super.tickDeath();if(deathTime==2||deathTime==4||deathTime==6){var offset=burstOffsets[deathTime/2];dissolve(offset[0],offset[1]);}}

 @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putInt("MummyPhase",lifecycle.phase());t.putInt("MummyElapsed",lifecycle.elapsed());t.putInt("MummyRemaining",lifecycle.remaining());t.putInt("MummyContactDamage",contactDamage);t.putInt("MummyStun",stunMilliseconds);t.put("MummyMovement",movement.save());}
 @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);lifecycle.load(t.getInt("MummyPhase"),t.getInt("MummyElapsed"),t.getInt("MummyRemaining"));entityData.set(REMAINING,lifecycle.remaining());contactDamage=t.getInt("MummyContactDamage");stunMilliseconds=t.getInt("MummyStun");movement.load(t.getCompound("MummyMovement"));}
}
