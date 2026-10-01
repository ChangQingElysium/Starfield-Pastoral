package com.stardew.craft.entity.monster;

import com.stardew.craft.monster.*;
import com.stardew.craft.mining.*;
import com.stardew.craft.block.mine.MineStoneBlock;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
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
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;
import java.util.*;

/** Ordinary Dust Spirit: independent little hops, sight-triggered retreat and inertial charging. */
@SuppressWarnings("null")
public final class MineDustSpiritEntity extends StardewMonsterEntity {
    public static final float WIDTH=.73F,HEIGHT=.69F;
    private static final EntityDataAccessor<Float> OFFSET=SynchedEntityData.defineId(MineDustSpiritEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineDustSpiritEntity.class,EntityDataSerializers.LONG);
    private final DustSpiritMotion motion=new DustSpiritMotion();
    private List<SourceTilePath.Tile> path=List.of();
    private int pathIndex,soundTicks,stunMilliseconds;
    private double groundY,pathPaused,lastPathX,lastPathZ;
    private boolean anchored;
    public MineDustSpiritEntity(EntityType<? extends MineDustSpiritEntity> type,Level level){super(type,level);setNoGravity(true);addTag("sd_mob_dust_sprite");}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,40).add(Attributes.ATTACK_DAMAGE,6).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,64).add(Attributes.STEP_HEIGHT,0);}
    @Override protected void registerGoals(){}
    @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft:dust_sprite");}
    @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){var r=MonsterStatResolver.base(d,c,random);setInitialHealth(r.initialHealth());replaceCombatStats(r.combat());getAttribute(Attributes.SCALE).setBaseValue((75+random.nextInt(26))/100.);motion.voice(1+random.nextInt(23));groundY=getY();anchored=true;}
    @Override protected void defineSynchedData(){super.defineSynchedData();this.entityData.define(OFFSET,0F);this.entityData.define(HIT,-100L);}
    public double sourceOffset(){return entityData.get(OFFSET);}
    public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}
    public DustSpiritMotion motion(){return motion;}
    public void sourceChargingConstructor(boolean charging){motion.voice(0);if(charging){motion.see();motion.charge();}}
    public void stunFor(int milliseconds){stunMilliseconds=Math.max(stunMilliseconds,milliseconds);}
    private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS.get())&&(monsterState().context().generation()==null||OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
    @Override protected void customServerAiStep(){
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));
        if(!anchored){groundY=getY();anchored=true;}
        if(soundTicks>0)soundTicks--;
        var target=level().getNearestPlayer(getX(),groundY,getZ(),64,e->e instanceof Player p&&valid(p));setTarget(target);
        for(int i=0;i<3;i++){
            motion.jumpPhysics();setPos(getX(),groundY-motion.offset()/64.,getZ());
            boolean stunned=stunMilliseconds>0;if(stunned)stunMilliseconds=Math.max(0,stunMilliseconds-17);
            if(!stunned){
                if(motion.x()!=0||motion.y()!=0){boolean clear=step(motion.x()/64,-motion.y()/64);motion.decay(!clear);}
                followPath();
                if(motion.offset()==0){if(random.nextDouble()<.01)quake();motion.landingDrift(random);}
                if(target!=null){
                    if(motion.charging()){
                        motion.accelerate(target.getX()-getX(),target.getZ()-getZ(),Math.floorMod(Math.round((target.getYRot()+180)/90),4),random);
                        if(random.nextDouble()<.0001){makePath(target,false,300);motion.repath();}
                    }else if(!motion.seen()&&sourceSight(target))motion.see();
                    else if(motion.seen()&&path.isEmpty()&&!motion.running()){makePath(target,true,350);motion.flee();}
                    else if(path.isEmpty()&&motion.running())motion.charge();
                }
            }
            if(motion.offset()==0){
                motion.launch(random);
                if(random.nextDouble()<.1&&soundTicks==0&&target!=null&&distanceToSqr(target)<400){playSound(ModSounds.DUST_MEEP.get(),.65F,DustSpiritMotion.pitch(motion.voice()*100+random.nextInt(200)-100));soundTicks=12;}
            }
            if(target!=null&&Math.abs(target.getX()-getX())<=3&&Math.abs(target.getZ()-getZ())<=3)path=List.of();
            entityData.set(OFFSET,(float)motion.offset());startAction(motion.charging()?2:motion.running()?1:0,false);
            for(var p:((ServerLevel)level()).players())if(valid(p)&&getBoundingBox().intersects(p.getBoundingBox())){var attack=MonsterDamageSource.contact(this);p.hurt(attack,attack.baseDamage());}
        }
    }
    private boolean clearAt(double x,double z){
        var box=getBoundingBox().move(x-getX(),groundY-getY(),z-getZ());
        var floor=BlockPos.containing(x,groundY-.05,z);
        return level().noCollision(this,box)&&level().getFluidState(floor).isEmpty()&&level().getFluidState(floor.above()).isEmpty()&&!level().getBlockState(floor).getCollisionShape(level(),floor).isEmpty();
    }
    private boolean step(double dx,double dz){
        if(!clearAt(getX()+dx,getZ()+dz)||!level().noCollision(this,getBoundingBox().move(dx,0,dz)))return false;
        move(MoverType.SELF,new Vec3(dx,0,dz));
        if(dx*dx+dz*dz>.00001){setYRot((float)Math.toDegrees(Math.atan2(-dx,dz)));yBodyRot=getYRot();setYHeadRot(getYRot());}return true;
    }
    private boolean sourceSight(Player target){return MineMonsterSight.sees(this,target,8);}
    private void makePath(Player p,boolean escape,int limit){
        var start=new SourceTilePath.Tile(getBlockX(),getBlockZ());var end=escape?start:new SourceTilePath.Tile(p.getBlockX(),p.getBlockZ());
        // A dedicated server has no SDV viewport. Twenty blocks is the existing native-monster vicinity adaptation.
        path=SourceTilePath.find(start,end,limit,t->escape?Math.hypot(t.x()+.5-p.getX(),t.z()+.5-p.getZ())>20:t.equals(end),t->clearAt(t.x()+.5,t.z()+.5));
        pathIndex=0;pathPaused=0;lastPathX=getX();lastPathZ=getZ();
    }
    private void followPath(){
        if(path.isEmpty())return;
        if(lastPathX==getX()&&lastPathZ==getZ())pathPaused+=1000./60;else pathPaused=0;
        lastPathX=getX();lastPathZ=getZ();if(pathPaused>5000){path=List.of();return;}
        var t=path.get(pathIndex);double dx=t.x()+.5-getX(),dz=t.z()+.5-getZ();
        if(Math.abs(dx)<.1&&Math.abs(dz)<.1){if(++pathIndex==path.size())path=List.of();return;}
        double speed=(motion.running()?5:3)/64.;
        if(Math.abs(dx)>.1)step(Math.copySign(Math.min(speed,Math.abs(dx)),dx),0);else step(0,Math.copySign(Math.min(speed,Math.abs(dz)),dz));
    }
    private void quake(){
        var server=(ServerLevel)level();var at=BlockPos.containing(getX(),groundY,getZ());
        server.sendParticles(new DustParticleOptions(new Vector3f(.32F,.35F,.4F),.7F),getX(),groundY+.06,getZ(),12,.45,.015,.45,.02);
        for(var d:new net.minecraft.core.Direction[]{net.minecraft.core.Direction.WEST,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.NORTH}){var p=at.relative(d);var state=level().getBlockState(p);
            if(state.getBlock() instanceof MineStoneBlock&&!OrdinaryMineRuntime.isArchitecture(server,p)&&level().destroyBlock(p,false,this))MineStoneMining.finishDrops(server,null,p,state,false);
        }
    }
    @Override public void knockback(double strength,double x,double z){super.knockback(strength,x,z);var v=getDeltaMovement();motion.knockback(v.x*64/3,-v.z*64/3);setDeltaMovement(Vec3.ZERO);}
    @Override public boolean hurt(DamageSource s,float amount){float hp=getHealth();boolean result=super.hurt(s,amount);if(!level().isClientSide&&getHealth()<hp)entityData.set(HIT,level().getGameTime());return result;}
    @Override public boolean isPushable(){return false;}
    @Override public void push(Entity other){}
    @Override public void travel(Vec3 v){setDeltaMovement(Vec3.ZERO);}
    @Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
    @Override protected SoundEvent getHurtSound(DamageSource s){return ModSounds.MONSTER_BAT_HIT.get();}
    @Override protected SoundEvent getDeathSound(){return ModSounds.DUST_MEEP.get();}
    private void deathPuff(float size){((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(50/255F,50/255F,80/255F),size),getX()+(size<1?(random.nextInt(64)-32)/64.:0),getY()+.2,getZ()+(size<1?(random.nextInt(64)-32)/64.:0),8,.08,.08,.08,.035);}
    @Override protected void onFinalDeath(DamageSource s){deathPuff(1);}
    @Override protected void tickDeath(){super.tickDeath();if(!level().isClientSide&&(deathTime==3||deathTime==6||deathTime==9))deathPuff(.5F);}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("DustMotion",motion.save());t.putDouble("DustGround",groundY);t.putInt("DustPathIndex",pathIndex);t.putDouble("DustPathPaused",pathPaused);var a=new ListTag();for(var p:path){var n=new CompoundTag();n.putInt("X",p.x());n.putInt("Z",p.z());a.add(n);}t.put("DustPath",a);t.putInt("DustStun",stunMilliseconds);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);if(t.contains("DustMotion"))motion.load(t.getCompound("DustMotion"));groundY=t.contains("DustGround")?t.getDouble("DustGround"):getY();anchored=true;pathIndex=t.getInt("DustPathIndex");pathPaused=t.getDouble("DustPathPaused");var a=new ArrayList<SourceTilePath.Tile>();for(var e:t.getList("DustPath",Tag.TAG_COMPOUND)){var p=(CompoundTag)e;a.add(new SourceTilePath.Tile(p.getInt("X"),p.getInt("Z")));}path=pathIndex<a.size()?List.copyOf(a):List.of();stunMilliseconds=t.getInt("DustStun");entityData.set(OFFSET,(float)motion.offset());setNoGravity(true);}
}
