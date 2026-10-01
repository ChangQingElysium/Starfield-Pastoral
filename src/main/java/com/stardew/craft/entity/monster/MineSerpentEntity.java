package com.stardew.craft.entity.monster;
import com.stardew.craft.monster.*;
import com.stardew.craft.mining.*;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.sound.ModSounds;
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
/** Ordinary Serpent: source combat/inertia clocks with physical, three-dimensional flight. */
@SuppressWarnings("null")
public final class MineSerpentEntity extends StardewMonsterEntity {
 public static final float WIDTH=1.5F,HEIGHT=.8F;public static final double LIFT=.75;public static final float MAX_FLIGHT_PITCH=12;
 private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineSerpentEntity.class,EntityDataSerializers.LONG);
 private static final EntityDataAccessor<Integer> DEATH_SPIN=SynchedEntityData.defineId(MineSerpentEntity.class,EntityDataSerializers.INT);
 private final SerpentFlightMotion steering=new SerpentFlightMotion();
 private Vec3 idleAnchor,idleTarget;private int idleWait,idleTravel;private double deathX,deathZ,deathOriginX,deathOriginZ;private int stunMilliseconds;private boolean deathEffects;
 public MineSerpentEntity(EntityType<? extends MineSerpentEntity> t,Level l){super(t,l);setNoGravity(true);noPhysics=false;addTag("sd_mob_serpent");}
 public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,150).add(Attributes.ATTACK_DAMAGE,23).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,128).add(Attributes.STEP_HEIGHT,0);}
 @Override protected void registerGoals(){}
 @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level){return new MonsterFlightRoute(this,level);}
 private MonsterFlightRoute flightRoute(){return (MonsterFlightRoute)getNavigation();}
 @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft:serpent");}
 @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){var r=MonsterStatResolver.base(d,c,random);setInitialHealth(r.initialHealth());replaceCombatStats(r.combat());steering.initialize(random);setPos(getX(),getY()+LIFT,getZ());MonsterFlightPlacement.ensureSpawnAir(this);idleAnchor=position();idleWait=20;}
 @Override protected void defineSynchedData(){super.defineSynchedData();this.entityData.define(HIT,-100L);this.entityData.define(DEATH_SPIN,3);}
 public int deathSpin(){return entityData.get(DEATH_SPIN);}
 public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}public SerpentFlightMotion steering(){return steering;}public void stunFor(int ms){stunMilliseconds=Math.max(stunMilliseconds,ms);}
 private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS.get())&&(monsterState().context().generation()==null||OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
 @Override protected void customServerAiStep(){
  if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));
  if(isRemoved())return;
  var previous=getTarget();
  int detection=getTags().contains("sd_focused_on_farmers")?128:13;
  var target=previous instanceof Player p&&valid(p)&&distanceToSqr(p)<(double)Math.max(24,detection)*Math.max(24,detection)?p:null;
  if(target==null)target=level().getNearestPlayer(getX(),getY(),getZ(),detection,e->e instanceof Player p&&valid(p)&&(detection>13||MineMonsterSight.sees(this,p,13)));
  setTarget(target);
  if(previous!=target){flightRoute().invalidate();idleTarget=null;idleAnchor=position();idleWait=20;}
  Vec3 destination=target==null?idleDestination():target.getBoundingBox().getCenter().add(0,-getBbHeight()/2.,0);
  Vec3 waypoint=flightRoute().waypoint(destination);
  for(int i=0;i<3;i++){
   int elapsed=i==2?18:16;
   steering.elapsed(elapsed);
   if(stunMilliseconds>0){stunMilliseconds=Math.max(0,stunMilliseconds-elapsed);continue;}
   steering.steer(waypoint==null?null:waypoint.subtract(position()),target!=null,!sourceHitRecoveryActive());
   Vec3 delta=steering.velocity().scale(1./64);
   var wall=MonsterSpace.blocks(this,getBoundingBox().inflate(.025),delta);
   Vec3 before=position();
   move(MoverType.SELF,delta.scale(wall==null?1:Math.max(0,wall.fraction()-1e-5)));
   if(wall!=null){steering.blocked(wall.normal());flightRoute().blocked(wall.normal());}
   Vec3 actual=position().subtract(before);
   if(actual.lengthSqr()>1e-7){
    float yaw=(float)Math.toDegrees(Math.atan2(-actual.x,actual.z));
    setYRot(net.minecraft.util.Mth.rotLerp(.22F,getYRot(),yaw));
    float pitch=(float)Math.clamp(-Math.toDegrees(Math.atan2(actual.y,actual.horizontalDistance())),-MAX_FLIGHT_PITCH,MAX_FLIGHT_PITCH);
    setXRot(net.minecraft.util.Mth.lerp(.18F,getXRot(),pitch));
   }else setXRot(getXRot()*.9F);
   yBodyRot=getYRot();setYHeadRot(getYRot());
   for(var p:((ServerLevel)level()).players())if(valid(p)&&getBoundingBox().intersects(p.getBoundingBox())){var a=MonsterDamageSource.contact(this);p.hurt(a,a.baseDamage());}
  }
  if(monsterState().context().generation()!=null){var layout=OrdinaryMineLayout.load((ServerLevel)level(),monsterState().context().floor());var origin=layout.origin(monsterState().context().floor()).offset(layout.tileX,0,layout.tileZ);double x=getX()-origin.getX(),z=getZ()-origin.getZ();if(x<-10||z<-10||x>layout.width+10||z>layout.depth+10)MonsterFactory.cleanup(this);}
 }
 private Vec3 idleDestination(){
  if(idleAnchor==null||idleAnchor.distanceToSqr(position())>144)idleAnchor=position();
  if(idleWait>0){idleWait--;return null;}
  if(idleTarget!=null){
   if(position().distanceToSqr(idleTarget)<.36||++idleTravel>160){idleTarget=null;idleWait=20+random.nextInt(41);return null;}
   return idleTarget;
  }
  for(int attempt=0;attempt<12;attempt++){
   Vec3 point=idleAnchor.add(random.nextDouble()*10-5,random.nextDouble()*4-2,random.nextDouble()*10-5);
   var box=getBoundingBox().move(point.subtract(position())).inflate(.025);
   if(MonsterSpace.loaded(this,box)&&level().noCollision(this,box)&&!level().containsAnyLiquid(box)){
    idleTarget=point;idleTravel=0;return point;
   }
  }
  idleWait=40;return null;
 }
 @Override public boolean hurt(DamageSource s,float amount){float hp=getHealth();boolean accepted=super.hurt(s,amount);if(!level().isClientSide){if(getHealth()<hp){steering.hit();entityData.set(HIT,level().getGameTime());playSound(ModSounds.SERPENT_HIT.get(),1,(float)Math.pow(2,-.269+random.nextDouble()*.653));}random.nextInt(-1,1);}return accepted;}
 @Override public void knockback(double strength,double x,double z){super.knockback(strength,x,z);var v=getDeltaMovement();steering.knockback(v.x*64/3,v.z*64/3);setDeltaMovement(Vec3.ZERO);}
 @Override protected SoundEvent getHurtSound(DamageSource s){return null;}@Override protected SoundEvent getDeathSound(){return null;}
 @Override protected void onFinalDeath(DamageSource s){if(!(s.getEntity() instanceof Player p))return;deathEffects=true;deathOriginX=getX();deathOriginZ=getZ();entityData.set(DEATH_SPIN,random.nextInt(3,5));double dx=p.getX()-getX(),dz=p.getZ()-getZ(),len=Math.max(.001,Math.sqrt(dx*dx+dz*dz));deathX=-(int)(dx/len*4)/64.;deathZ=-(int)(dz/len*4)/64.;playSound(ModSounds.SERPENT_DIE.get(),1,(float)Math.pow(2,-.612+random.nextDouble()*.685));}
 @Override public void tick(){super.tick();if(!level().isClientSide&&deathEffects&&deathTime>0&&deathTime<16)setPos(getX()+deathX*3,getY(),getZ()+deathZ*3);if(!level().isClientSide&&deathEffects&&deathTime>=1&&deathTime<=5){int i=deathTime-1;double[][] offsets={{-.5,0},{.5,0},{0,-.5},{0,0},{0,.5}};var type=i==2||i==4?com.stardew.craft.weather.ModParticles.SERPENT_PUFF_SLOW.get():com.stardew.craft.weather.ModParticles.SERPENT_PUFF.get();int alpha=(int)(255*(.9-i*.1));int color=alpha<<24|((int)(144*(.9-i*.1)))<<16|((int)(238*(.9-i*.1)))<<8|(int)(144*(.9-i*.1));((ServerLevel)level()).sendParticles(com.stardew.craft.port.net.minecraft.core.particles.ColorParticleOption.create(type,color),deathOriginX+offsets[i][0],getY()+.375,deathOriginZ+offsets[i][1],0,deathX*3*(1-i*.2),0,deathZ*3*(1-i*.2),1);playSound(ModSounds.COWBOY_MONSTERHIT.get(),1,(float)Math.pow(2,-.237+random.nextDouble()*.506));}}
 @Override public void travel(Vec3 v){setDeltaMovement(Vec3.ZERO);}@Override public boolean isPushable(){return false;}@Override public void push(Entity e){}@Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
 @Override public AABB getBoundingBoxForCulling(){return super.getBoundingBoxForCulling().inflate(.7);}
 @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("SerpentFlight",steering.save());if(idleAnchor!=null){t.putDouble("IdleX",idleAnchor.x);t.putDouble("IdleY",idleAnchor.y);t.putDouble("IdleZ",idleAnchor.z);}t.putInt("SerpentStun",stunMilliseconds);}
 @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);steering.load(t.getCompound("SerpentFlight"));idleAnchor=t.contains("IdleX")?new Vec3(t.getDouble("IdleX"),t.getDouble("IdleY"),t.getDouble("IdleZ")):position();idleTarget=null;idleWait=20;flightRoute().invalidate();stunMilliseconds=t.getInt("SerpentStun");setNoGravity(true);noPhysics=false;}
}
