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
/** ShadowBrute's base Monster pursuit and contact damage; source shadow hit and cross-shaped death bursts. */
@SuppressWarnings("null")
public final class MineShadowBruteEntity extends StardewMonsterEntity {
    public static final float WIDTH=.86F,HEIGHT=1.86F;
    private static final EntityDataAccessor<Boolean> MOVING=SynchedEntityData.defineId(MineShadowBruteEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineShadowBruteEntity.class,EntityDataSerializers.LONG);
    private final SourceGroundMovement movement=new SourceGroundMovement(this,3,2);private double fallSpeed;private int stunMilliseconds;
    public MineShadowBruteEntity(EntityType<? extends MineShadowBruteEntity> type,Level level){super(type,level);addTag("sd_mob_shadow_brute");}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,160).add(Attributes.ATTACK_DAMAGE,18).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,64).add(Attributes.STEP_HEIGHT,0);}
    @Override protected void registerGoals(){}
    @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft:shadow_brute");}
    @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){var r=MonsterStatResolver.base(d,c,random);setInitialHealth(r.initialHealth());replaceCombatStats(r.combat());movement.face(2);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(MOVING,false);b.define(HIT,-100L);}
    public boolean moving(){return entityData.get(MOVING);}
    public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}
    public void stunFor(int milliseconds){stunMilliseconds=Math.max(stunMilliseconds,milliseconds);}
    private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS)&&(monsterState().context().generation()==null||OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
    @Override protected void customServerAiStep(){
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));var target=level().getNearestPlayer(getX(),getY(),getZ(),64,e->e instanceof Player p&&valid(p));setTarget(target);double x=getX(),z=getZ();
        for(int i=0;i<3;i++){
            if(stunMilliseconds<=0)movement.tick(target,true,8,false,0,()->{});else stunMilliseconds=Math.max(0,stunMilliseconds-16);
            for(var p:((ServerLevel)level()).players())if(valid(p)&&getBoundingBox().intersects(p.getBoundingBox())){var attack=MonsterDamageSource.contact(this);p.hurt(attack,attack.baseDamage());}
        }
        entityData.set(MOVING,x!=getX()||z!=getZ());
    }
    @Override public boolean hurt(DamageSource s,float amount){if(!level().isClientSide)playSound(ModSounds.SHADOW_HIT.get(),1,shadowPitch());float hp=getHealth();boolean hit=super.hurt(s,amount);if(!level().isClientSide&&getHealth()<hp)entityData.set(HIT,level().getGameTime());return hit;}
    @Override public void knockback(double strength,double x,double z){super.knockback(strength,x,z);var v=getDeltaMovement();movement.knockback(v.x*64/3,v.z*64/3);setDeltaMovement(Vec3.ZERO);}
    @Override protected SoundEvent getHurtSound(DamageSource s){return ModSounds.MONSTER_BAT_HIT.get();}
    @Override protected SoundEvent getDeathSound(){return null;}
    private float shadowPitch(){return (float)Math.pow(2,-.22+random.nextDouble()*.538);}
    @Override public float getVoicePitch(){return 1;}
    @Override public void tick(){super.tick();if(!level().isClientSide&&(deathTime==3||deathTime==6)){int r=deathTime/3;for(int[] d:new int[][]{{r,0},{-r,0},{0,r},{0,-r}})((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(.38F,.4F,.42F),.75F),getX()+d[0],getY()+.2,getZ()+d[1],10,.12,.14,.12,.025);}}
    @Override protected void onFinalDeath(DamageSource s){playSound(ModSounds.SHADOW_DIE.get(),1,shadowPitch());((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(.12F,.19F,.22F),.9F),getX(),getY()+.8,getZ(),10,.3,.7,.3,.07);}
    @Override public void travel(Vec3 v){fallSpeed=isNoGravity()?0:onGround()?-.08:Math.max(-3.9,(fallSpeed-.08)*.98);move(MoverType.SELF,new Vec3(0,fallSpeed,0));setDeltaMovement(Vec3.ZERO);}
    @Override public boolean isPushable(){return false;}
    @Override public void push(Entity e){}
    @Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("GroundMovement",movement.save());t.putInt("ShadowStun",stunMilliseconds);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);movement.load(t.getCompound("GroundMovement"));stunMilliseconds=t.getInt("ShadowStun");}
}
