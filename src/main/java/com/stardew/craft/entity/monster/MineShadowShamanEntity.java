package com.stardew.craft.entity.monster;
import com.stardew.craft.monster.*;
import com.stardew.craft.mining.OrdinaryMineRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.combat.MonsterStats;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
/** ShadowShaman: source movement precedes its 60Hz vision/cast/flee branches. */
@SuppressWarnings("null")
public final class MineShadowShamanEntity extends StardewMonsterEntity {
    public static final float WIDTH=.88F,HEIGHT=1.67F;
    private static final EntityDataAccessor<Boolean> MOVING=SynchedEntityData.defineId(MineShadowShamanEntity.class,EntityDataSerializers.BOOLEAN),CASTING=SynchedEntityData.defineId(MineShadowShamanEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineShadowShamanEntity.class,EntityDataSerializers.LONG),CAST=SynchedEntityData.defineId(MineShadowShamanEntity.class,EntityDataSerializers.LONG),RELEASE=SynchedEntityData.defineId(MineShadowShamanEntity.class,EntityDataSerializers.LONG);
    private final SourceGroundMovement movement=new SourceGroundMovement(this,3,2);
    private final ShamanSpellClock spell=new ShamanSpellClock();
    private double fallSpeed;private int stunMilliseconds;private long suppressKnockbackTick=-1;private boolean decidingSuppression;
    public MineShadowShamanEntity(EntityType<? extends MineShadowShamanEntity> type,Level level){super(type,level);addTag("sd_mob_shadow_shaman");}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,80).add(Attributes.ATTACK_DAMAGE,17).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,64).add(Attributes.STEP_HEIGHT,0);}
    @Override protected void registerGoals(){}
    @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft:shadow_shaman");}
    @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){
        var r=MonsterStatResolver.base(d,c,random);setInitialHealth(r.initialHealth());var stats=r.combat();
        var server=level().getServer();var friendships=com.stardew.craft.npc.runtime.NpcFriendshipDataManager.get((ServerLevel)level());var host=server.getSingleplayerProfile();
        int points=host!=null?friendships.getPointsForNpc(host.getId(),"???"):friendships.getMaxPointsForNpc("???");
        if(points>=1250)stats=MonsterStats.builder().damage(0).resilience(stats.getResilience()).missChance(stats.getMissChance()).experience(stats.getExperience()).build();
        replaceCombatStats(stats);movement.face(2);
    }
    @Override protected void defineSynchedData(){super.defineSynchedData();this.entityData.define(MOVING,false);this.entityData.define(CASTING,false);this.entityData.define(HIT,-100L);this.entityData.define(CAST,-100L);this.entityData.define(RELEASE,-100L);}
    public boolean moving(){return entityData.get(MOVING);}public boolean casting(){return entityData.get(CASTING);}
    private double time(EntityDataAccessor<Long> key,float p){return (level().getGameTime()-entityData.get(key)+p)/20.;}
    public double hitTime(float p){return time(HIT,p);}public double castTime(float p){return time(CAST,p);}public double releaseTime(float p){return time(RELEASE,p);}
    public void stunFor(int milliseconds){stunMilliseconds=Math.max(stunMilliseconds,milliseconds);}
    private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS.get())&&(monsterState().context().generation()==null||OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
    private boolean peripheral(Player p){return switch(movement.facing()){case 0->p.getZ()<getZ()+.5;case 1->p.getX()>getX()-.5;case 2->p.getZ()>getZ()-.5;default->p.getX()<getX()+.5;};}
    @Override protected void customServerAiStep(){
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));
        var target=level().getNearestPlayer(getX(),getY(),getZ(),64,e->e instanceof Player p&&valid(p));setTarget(target);double x=getX(),z=getZ();
        for(int i=0;i<3;i++){
            if(stunMilliseconds>0){stunMilliseconds=Math.max(0,stunMilliseconds-16);continue;}
            movement.tick(target,spell.walking(),8,false,.01,()->{});
            if(target!=null){
                boolean sight=MineMonsterSight.sees(this,target,8),inRange=movement.near(target,8),low=getHealth()<30;
                double roll=spell.spotted()&&!spell.casting()&&inRange&&!low&&(movement.hasPath()||sight)&&spell.cooldown()<=0?random.nextDouble():1;
                switch(spell.step(peripheral(target)&&sight,inRange,low,movement.hasPath(),sight,roll)){
                    case SPOTTED->{movement.clearPath();movement.halt();movement.faceTarget(target);if(random.nextDouble()<.3)playSound(ModSounds.SHADOW_PEEP.get(),1,pitch(-.22,.335));}
                    case FLEE->movement.direction(Math.abs(target.getZ()-getZ())>3?(target.getX()>getX()?3:1):(target.getZ()>getZ()?0:2));
                    case FIND_PATH->{movement.pathTo(target,300);if(!movement.hasPath()){spell.pathFailed();movement.halt();}}
                    case START_CAST->{movement.clearPath();movement.halt();entityData.set(CAST,level().getGameTime());}
                    case RELEASE->{releaseSpell(target);entityData.set(RELEASE,level().getGameTime());}
                    case FORGET->movement.clearPath();case WANDER->movement.randomize(.01);default->{}
                }
            }
            for(var p:((ServerLevel)level()).players())if(valid(p)&&getBoundingBox().intersects(p.getBoundingBox())){var attack=MonsterDamageSource.contact(this);if(attack.baseDamage()>0)p.hurt(attack,attack.baseDamage());}
        }
        entityData.set(MOVING,x!=getX()||z!=getZ());entityData.set(CASTING,spell.casting());
    }
    private void releaseSpell(Player target){
        int attack=target instanceof ServerPlayer p?com.stardew.craft.combat.equipment.EquipmentResolver.getMergedStats(p).getAttack()+com.stardew.craft.player.PlayerDataManager.getPlayerData(p).getTempAttackBonus():0;
        if(attack>=0&&random.nextDouble()<.6){var projectile=ModEntities.SHAMAN_CURSE.get().create(level());projectile.launch(this,target);level().addFreshEntity(projectile);playSound(ModSounds.DEBUFF_SPELL.get(),1,pitch(-.1,.253));}else healLowest();
    }
    /** Source iteration uses the last tied minimum, including the caster and full-health creatures. */
    public StardewMonsterEntity healLowest(){
        StardewMonsterEntity selected=null;double minimum=1;
        for(var e:((ServerLevel)level()).getAllEntities())if(e instanceof StardewMonsterEntity m&&m.isAlive()&&m.initialized()&&java.util.Objects.equals(m.monsterState().context().generation(),monsterState().context().generation())){
            var p=level().getNearestPlayer(m.getX(),m.getY(),m.getZ(),64,e2->e2 instanceof Player q&&valid(q));
            if(p==null||Math.abs(m.getBlockX()-p.getBlockX())>6||Math.abs(m.getBlockZ()-p.getBlockZ())>6)continue;
            double ratio=m.getHealth()/m.monsterState().sourceMaxHealth();if(ratio<=minimum){minimum=ratio;selected=m;}
        }
        if(selected!=null){selected.setHealth(Math.min(selected.monsterState().sourceMaxHealth(),selected.getHealth()+60));playSound(ModSounds.MONSTER_HEAL.get(),1,pitch(-.1,.171));
            ((ServerLevel)level()).sendParticles(com.stardew.craft.weather.ModParticles.MONSTER_HEAL.get(),selected.getX(),selected.getY()+.7,selected.getZ(),1,0,0,0,0);
            com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(selected,new com.stardew.craft.combat.network.DamageNumberPayload((float)selected.getX(),(float)(selected.getY()+.9),(float)selected.getZ(),60,false,"monster_heal"));
        }
        return selected;
    }
    @Override public boolean hurt(DamageSource s,float amount){
        float hp=getHealth();decidingSuppression=!level().isClientSide&&spell.casting()&&random.nextBoolean();boolean delayed=decidingSuppression;boolean hit;
        try{hit=super.hurt(s,amount);}finally{decidingSuppression=false;}
        if(!level().isClientSide&&getHealth()<hp){entityData.set(HIT,level().getGameTime());if(delayed){spell.delayFromHit();suppressKnockbackTick=level().getGameTime();}else{ suppressKnockbackTick=-1;playSound(ModSounds.SHADOW_HIT.get(),1,pitch(-.22,.318));}}
        return hit;
    }
    @Override public void knockback(double strength,double x,double z){if(decidingSuppression||suppressKnockbackTick==level().getGameTime())return;super.knockback(strength,x,z);var v=getDeltaMovement();movement.knockback(v.x*64,v.z*64);setDeltaMovement(Vec3.ZERO);}
    @Override protected SoundEvent getHurtSound(DamageSource s){return null;}@Override protected SoundEvent getDeathSound(){return null;}
    private float pitch(double a,double b){return (float)Math.pow(2,a+random.nextDouble()*(b-a));}
    @Override public void tick(){super.tick();if(!level().isClientSide&&(deathTime==3||deathTime==6)){int r=deathTime/3;for(int x:new int[]{-r,r})for(int z:new int[]{-r,r})((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(.38F,.4F,.42F),.75F),getX()+x,getY()+.2,getZ()+z,10,.12,.14,.12,.025);}}
    @Override protected void onFinalDeath(DamageSource s){playSound(ModSounds.SHADOW_DIE.get(),1,pitch(-.22,.318));((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(.65F,.39F,.21F),.8F),getX(),getY()+.8,getZ(),20,.3,.7,.3,.07);}
    @Override public void travel(Vec3 v){fallSpeed=isNoGravity()?0:onGround()?-.08:Math.max(-3.9,(fallSpeed-.08)*.98);move(MoverType.SELF,new Vec3(0,fallSpeed,0));setDeltaMovement(Vec3.ZERO);}
    @Override public boolean isPushable(){return false;}@Override public void push(Entity e){}@Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("GroundMovement",movement.save());t.put("ShamanSpell",spell.save());t.putInt("ShadowStun",stunMilliseconds);t.putLong("CastTick",entityData.get(CAST));t.putLong("ReleaseTick",entityData.get(RELEASE));}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);movement.load(t.getCompound("GroundMovement"));spell.load(t.getCompound("ShamanSpell"));stunMilliseconds=t.getInt("ShadowStun");entityData.set(CASTING,spell.casting());entityData.set(CAST,t.getLong("CastTick"));entityData.set(RELEASE,t.getLong("ReleaseTick"));}
}
