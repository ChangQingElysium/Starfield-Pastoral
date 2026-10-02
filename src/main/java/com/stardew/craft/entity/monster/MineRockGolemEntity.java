package com.stardew.craft.entity.monster;
import com.stardew.craft.monster.*;
import com.stardew.craft.mining.OrdinaryMineRuntime;
import com.stardew.craft.combat.MonsterStats;
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
/** Shared RockGolem movement; mine and farm constructors keep separate identities and source rules. */
@SuppressWarnings("null")
public final class MineRockGolemEntity extends StardewMonsterEntity {
    public static final float WIDTH=.9F,HEIGHT=1.48F,FARM_HEIGHT=1.6F;
    public enum Variant { STONE, WILDERNESS, IRIDIUM }
    private final Variant variant;
    private int farmCombatLevel=-1;
    private static final EntityDataAccessor<Boolean> MOVING=SynchedEntityData.defineId(MineRockGolemEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> RISE=SynchedEntityData.defineId(MineRockGolemEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineRockGolemEntity.class,EntityDataSerializers.LONG);
    private final SourceGroundMovement movement=new SourceGroundMovement(this,2,2);
    private final RockGolemAwakening waking=new RockGolemAwakening();private double fallSpeed;private int stunMilliseconds;
    public MineRockGolemEntity(EntityType<? extends MineRockGolemEntity> type,Level level){this(type,level,Variant.STONE);}
    public MineRockGolemEntity(EntityType<? extends MineRockGolemEntity> type,Level level,Variant variant){
        super(type,level);this.variant=variant;addTag("sd_mob_"+visualVariant());
        if(isFarmGolem())movement.slipperiness(3);refreshDimensions();
    }
    public boolean isFarmGolem(){return variant!=null&&variant!=Variant.STONE;}
    public boolean isIridium(){return variant==Variant.IRIDIUM;}
    public String visualVariant(){return variant==Variant.WILDERNESS?"wilderness_golem":variant==Variant.IRIDIUM?"iridium_golem":"rock_golem";}
    public void setFarmCombatLevel(int value){if(initialized())throw new IllegalStateException("Farm difficulty must precede initialization");farmCombatLevel=com.stardew.craft.port.PortJava.clamp(value,0,100);}
    public int farmCombatLevel(){return farmCombatLevel;}
    private void setSourceSpeed(int speed){getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.25*speed/2.);}

    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,45).add(Attributes.ATTACK_DAMAGE,5).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,64).add(com.stardew.craft.port.PortAttributes.STEP_HEIGHT.get(),0);}
    @Override protected void registerGoals(){}
    @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft",visualVariant());}
    @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){
        var r=MonsterStatResolver.base(d,c,random);var base=r.combat();
        if(isFarmGolem()){
            if(farmCombatLevel<0){
                var player=level().getNearestPlayer(this,64);
                farmCombatLevel=player instanceof net.minecraft.server.level.ServerPlayer p
                        ?com.stardew.craft.player.PlayerStardewDataAPI.getSkillLevel(p,com.stardew.craft.player.SkillType.COMBAT):0;
            }
            setInitialHealth(r.initialHealth()+2*farmCombatLevel*farmCombatLevel+(isIridium()?400:0));
            replaceCombatStats(MonsterStats.builder().damage(base.getDamage()+farmCombatLevel+(isIridium()?10:0))
                    .resilience(base.getResilience()).missChance(base.getMissChance())
                    .experience(base.getExperience()+farmCombatLevel+(isIridium()?10:0)).build());
            for(String drop:FarmGolemRules.constructorDrops(farmCombatLevel,isIridium(),random))monsterState().addBornDrop(new ResourceLocation("stardewcraft",drop));
            setSourceSpeed(isIridium()?4:2);movement.face(2);return;
        }
        double health=c.floor()>80?2.5:c.floor()>40?1.75:1,damage=c.floor()>80?2:c.floor()>40?1.5:1;
        setInitialHealth((int)(r.initialHealth()*health));replaceCombatStats(MonsterStats.builder().damage((int)(base.getDamage()*damage)).resilience(base.getResilience()).missChance(base.getMissChance()).experience(base.getExperience()).build());movement.face(2);
    }
    @Override protected void defineSynchedData(){super.defineSynchedData();this.entityData.define(MOVING,false);this.entityData.define(RISE,0F);this.entityData.define(HIT,-100L);}
    // PORT(1.20.1): 1.21 LivingEntity#getDimensions is sleeping ? SLEEPING_DIMENSIONS : getDefaultDimensions(pose)
    // .scale(getScale()); 1.20.1 only has getDimensions.
    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return pose == net.minecraft.world.entity.Pose.SLEEPING ? SLEEPING_DIMENSIONS : getDefaultDimensions(pose).scale(getScale());
    }
    public EntityDimensions getDefaultDimensions(Pose pose){return EntityDimensions.scalable(WIDTH,isFarmGolem()?FARM_HEIGHT:1.15F+(HEIGHT-1.15F)*entityData.get(RISE));}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(RISE.equals(key))refreshDimensions();}
    public boolean moving(){return entityData.get(MOVING);}
    public double riseProgress(float p){return Math.min(1,entityData.get(RISE)+(phase()==1?p/12.:0));}
    public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}
    public RockGolemAwakening awakening(){return waking;}
    public void stunFor(int milliseconds){stunMilliseconds=Math.max(stunMilliseconds,milliseconds);}
    private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS.get())&&(monsterState().context().generation()==null||OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
    @Override protected void customServerAiStep(){
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));var target=level().getNearestPlayer(getX(),getY(),getZ(),64,e->e instanceof Player p&&valid(p));setTarget(target);double x=getX(),z=getZ();
        for(int i=0;i<3;i++){
            if(stunMilliseconds<=0){
                movement.tick(target,waking.walking(),waking.awake()?16:3,waking.focused(),waking.awake()?(isIridium()?.02:.01):0,waking::walkFrame);
                if(target!=null){boolean seen=waking.seen(),unfolding=seen&&!waking.awake();if(waking.step(waking.focused()||movement.near(target,3)))playSound(ModSounds.ROCK_GOLEM_SPAWN.get(),1,sourcePitch());
                    if(seen&&!unfolding&&waking.walking()&&random.nextDouble()<.001&&distanceToSqr(target)<400)movement.pathTo(target,200);
                }
            }else stunMilliseconds=Math.max(0,stunMilliseconds-16);
            if(target!=null&&movement.near(target,3))movement.clearPath();startAction(waking.awake()?2:waking.seen()?1:0,false);entityData.set(RISE,(float)waking.progress());
            for(var p:((ServerLevel)level()).players())if(valid(p)&&getBoundingBox().intersects(p.getBoundingBox())){var attack=MonsterDamageSource.contact(this);p.hurt(attack,attack.baseDamage());}
        }
        entityData.set(MOVING,x!=getX()||z!=getZ());
    }
    @Override public boolean hurt(DamageSource s,float amount){if(!level().isClientSide)waking.struck();float hp=getHealth();boolean hit=super.hurt(s,amount);if(!level().isClientSide&&getHealth()<hp){entityData.set(HIT,level().getGameTime());if(isIridium())setSourceSpeed(2+random.nextInt(5));}return hit;}
    @Override public void knockback(double strength,double x,double z){super.knockback(strength,x,z);var v=getDeltaMovement();movement.knockback(v.x*64,v.z*64);setDeltaMovement(Vec3.ZERO);}
    private float sourcePitch(){return (float)Math.pow(2,-.1+random.nextDouble()*.467);}
    @Override public float getVoicePitch(){return sourcePitch();}
    @Override protected SoundEvent getHurtSound(DamageSource s){return ModSounds.ROCK_GOLEM_HIT.get();}
    @Override protected SoundEvent getDeathSound(){return ModSounds.ROCK_GOLEM_DIE.get();}
    @Override protected void onFinalDeath(DamageSource s){((ServerLevel)level()).sendParticles(new DustParticleOptions(isIridium()?new Vector3f(.47F,.25F,.58F):isFarmGolem()?new Vector3f(.24F,.4F,.3F):new Vector3f(.23F,.27F,.26F),.7F),getX(),getY()+.4,getZ(),4+random.nextInt(5),.35,.4,.35,.09);}
    @Override public void travel(Vec3 v){fallSpeed=isNoGravity()?0:onGround()?-.08:Math.max(-3.9,(fallSpeed-.08)*.98);move(MoverType.SELF,new Vec3(0,fallSpeed,0));setDeltaMovement(Vec3.ZERO);}
    @Override public boolean isPushable(){return false;}
    @Override public void push(Entity e){}
    @Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("GolemAwakening",waking.save());t.put("GroundMovement",movement.save());t.putInt("GolemStun",stunMilliseconds);t.putInt("FarmGolemCombatLevel",farmCombatLevel);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);farmCombatLevel=t.contains("FarmGolemCombatLevel")?t.getInt("FarmGolemCombatLevel"):-1;waking.load(t.getCompound("GolemAwakening"));movement.load(t.getCompound("GroundMovement"));stunMilliseconds=t.getInt("GolemStun");entityData.set(RISE,(float)waking.progress());}
}
