package com.stardew.craft.entity.monster;
import com.stardew.craft.monster.*;
import com.stardew.craft.mining.*;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
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
/** Ghost.cs ordinary family: gliding through terrain, source inertia, contact relocation. */
@SuppressWarnings("null")
public final class MineGhostEntity extends StardewMonsterEntity {
    public static final float WIDTH=.76F,HEIGHT=1.13F;
    public static final double LIFT=.45;
    private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineGhostEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> SLOWED=SynchedEntityData.defineId(MineGhostEntity.class,EntityDataSerializers.BOOLEAN);
    private final GhostSteering steering=new GhostSteering();
    private final boolean carbon;
    private double groundY;
    private int stunMilliseconds;
    public MineGhostEntity(EntityType<? extends MineGhostEntity> type,Level level,boolean carbon){super(type,level);this.carbon=carbon;setNoGravity(true);noPhysics=true;addTag("sd_mob_ghost");if(carbon)addTag("sd_mob_carbon_ghost");}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,96).add(Attributes.ATTACK_DAMAGE,10).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,128).add(Attributes.STEP_HEIGHT,0);}
    @Override protected void registerGoals(){}
    public boolean carbon(){return carbon;}
    public boolean slowed(){return entityData.get(SLOWED);}
    @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft:"+(carbon?"carbon_ghost":"ghost"));}
    @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){var r=MonsterStatResolver.base(d,c,random);setInitialHealth(r.initialHealth());replaceCombatStats(r.combat());groundY=getY();setPos(getX(),groundY+LIFT,getZ());}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(HIT,-100L);b.define(SLOWED,false);}
    public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}
    public GhostSteering steering(){return steering;}
    public void stunFor(int ms){stunMilliseconds=Math.max(stunMilliseconds,ms);}
    private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS)&&(monsterState().context().generation()==null||OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
    @Override protected void customServerAiStep(){
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));
        var target=level().getNearestPlayer(getX(),groundY,getZ(),128,e->e instanceof Player p&&valid(p));setTarget(target);
        for(int i=0;i<3;i++){
            boolean stunned=stunMilliseconds>0;
            if(!stunned){setPos(getX()+steering.x()/64,getY(),getZ()-steering.y()/64);steering.decay();}
            else stunMilliseconds=Math.max(0,stunMilliseconds-17);
            if(target!=null){
                if(!stunned){
                    var contactBox=getBoundingBox();
                    if(contactBox.intersects(target.getBoundingBox())){
                        var attack=MonsterDamageSource.contact(this);target.hurt(attack,attack.baseDamage());
                        if(target.invulnerableTime>0)relocate(target);
                    }
                }
                // Ghost.updateAnimation still steers while stunned; MovePosition alone is paused.
                steering.animate((target.getX()-getX())*64,(target.getZ()-getZ())*64,random);
                double dx=target.getX()-getX(),dz=target.getZ()-getZ();if(dx*dx+dz*dz>.001){setYRot((float)Math.toDegrees(Math.atan2(-dx,dz)));yBodyRot=getYRot();setYHeadRot(getYRot());}
            }
        }
        int offset=(int)(Math.sin(level().getGameTime()/20.*Math.PI*2)*20);setPos(getX(),groundY+LIFT-offset/64.,getZ());
    }
    private void relocate(Player player){
        int f=monsterState().context().floor();OrdinaryMineLayout layout=monsterState().context().generation()==null?null:OrdinaryMineLayout.load((ServerLevel)level(),f);
        var o=layout==null?BlockPos.ZERO:layout.origin(f).offset(layout.tileX,0,layout.tileZ);
        int px=player.getBlockX()-o.getX(),pz=player.getBlockZ()-o.getZ();
        int x=px+random.nextInt(-12,12),z=pz+random.nextInt(-12,12);
        for(int attempt=0;attempt<3;attempt++){
            boolean passable;
            if(layout!=null){var cell=layout.cell(x,z);passable=x>=0&&z>=0&&x<layout.width&&z<layout.depth&&cell!=null&&cell.back()>=0&&!layout.blocksSight(x,z);}
            else{var at=BlockPos.containing(o.getX()+x+.5,groundY,o.getZ()+z);passable=level().getBlockState(at).isAir()&&!level().getBlockState(at.below()).getCollisionShape(level(),at.below()).isEmpty();}
            if(passable&&(x!=px||z!=pz)){setPos(o.getX()+x+.5,getY(),o.getZ()+z);return;}
            x=px+random.nextInt(-12,12);z=pz+random.nextInt(-12,12);
        }
    }
    @Override public boolean hurt(DamageSource source,float amount){
        float hp=getHealth();boolean result=super.hurt(source,amount);
        if(!level().isClientSide){entityData.set(SLOWED,true);((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(.6F,.8F,1),.5F),getX(),getY()+.6,getZ(),6,.2,.3,.2,.02);if(getHealth()<hp)entityData.set(HIT,level().getGameTime());}
        return result;
    }
    @Override public void knockback(double strength,double x,double z){super.knockback(strength,x,z);var v=getDeltaMovement();steering.knockback(v.x*64,-v.z*64);setDeltaMovement(Vec3.ZERO);}
    @Override public boolean isPushable(){return false;}
    @Override public void push(Entity e){}
    @Override public void travel(Vec3 input){setDeltaMovement(Vec3.ZERO);}
    @Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){if(position().distanceToSqr(new Vec3(x,y,z))>9){setPos(x,y,z);xo=x;yo=y;zo=z;}super.lerpTo(x,y,z,yaw,pitch,steps);}
    @Override protected SoundEvent getHurtSound(DamageSource s){return null;}
    @Override protected SoundEvent getDeathSound(){return ModSounds.GHOST.get();}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("GhostSteering",steering.save());t.putDouble("GhostGround",groundY);t.putBoolean("GhostSlowed",slowed());t.putInt("GhostStun",stunMilliseconds);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);if(t.contains("GhostSteering"))steering.load(t.getCompound("GhostSteering"));groundY=t.contains("GhostGround")?t.getDouble("GhostGround"):getY()-LIFT;entityData.set(SLOWED,t.getBoolean("GhostSlowed"));stunMilliseconds=t.getInt("GhostStun");setNoGravity(true);noPhysics=true;}
}
