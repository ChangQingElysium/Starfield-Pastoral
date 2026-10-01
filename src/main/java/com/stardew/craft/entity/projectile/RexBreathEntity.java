package com.stardew.craft.entity.projectile;
import com.stardew.craft.entity.monster.MinePepperRexEntity;
import com.stardew.craft.monster.MonsterDamageSource;
import com.stardew.craft.mining.*;
import com.stardew.craft.monster.MonsterSpace;
import com.stardew.craft.monster.MonsterProjectileMovement;
import com.stardew.craft.combat.equipment.YobaProtectionState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
/** DinoMonster's pooled BreathProjectile. No ricochet, weapon interception, fire-setting or new debuff. */
@SuppressWarnings("null")
public final class RexBreathEntity extends Projectile {
 private static final EntityDataAccessor<Float> DISTANCE=SynchedEntityData.defineId(RexBreathEntity.class,EntityDataSerializers.FLOAT);
 private boolean owned;private int floor;private java.util.UUID generation;
 public RexBreathEntity(EntityType<? extends RexBreathEntity> type,Level level){super(type,level);setNoGravity(true);}
 @Override protected void defineSynchedData(){this.entityData.define(DISTANCE,0F);}
 public void launch(MinePepperRexEntity owner,Vec3 origin,double angle){setOwner(owner);var c=owner.monsterState().context();owned=c.generation()!=null;floor=c.floor();generation=c.generation();setPos(origin);var target=owner.getTarget();
  double pitch=target==null?0:Math.atan2(target.getBoundingBox().getCenter().y-origin.y,Math.max(.001,target.position().subtract(origin).horizontalDistance()));
  pitch=Math.clamp(pitch,-Math.PI/6,Math.PI/6);
  setDeltaMovement(Math.cos(angle)*Math.cos(pitch)*30/64.,Math.sin(pitch)*30/64.,-Math.sin(angle)*Math.cos(pitch)*30/64.);entityData.set(DISTANCE,0F);hurtMarked=true;}
 public float travelled(){return entityData.get(DISTANCE);}public float opacity(float partial){double dist=travelled()+partial*30;return (float)Math.clamp((256-dist)/128,0,1);}
 @Override public void tick(){
  super.tick();if(level().isClientSide)return;
  if(owned){var d=MineFloorDataManager.get((ServerLevel)level()).getFloorData(floor);if(d==null||!java.util.Objects.equals(generation,d.generationId())){discard();return;}}
  for(int i=0;i<3&&!isRemoved();i++){
   entityData.set(DISTANCE,travelled()+10);
   if(travelled()>256){discard();return;}
   var wall=MonsterProjectileMovement.step(this,getDeltaMovement().scale(1./3),true,owned,floor,player->{
    if(player.invulnerableTime>0||YobaProtectionState.isActive(player,level().getGameTime())||(player.isUsingItem()&&player.getUseItem().getUseAnimation()==net.minecraft.world.item.UseAnim.EAT))return false;
    var attack=new MonsterDamageSource(damageSources().mobProjectile(this,null),MonsterDamageSource.Kind.PROJECTILE,25);player.hurt(attack,25);explode();discard();return true;
   });
   if(wall!=null){explode();discard();return;}
  }
 }

 private void explode(){((ServerLevel)level()).sendParticles(com.stardew.craft.weather.ModParticles.REX_BREATH_FRAGMENT.get(),getX(),getY(),getZ(),6,.07,.07,.07,.03);}
 @Override public boolean isPickable(){return false;}@Override public boolean hurt(DamageSource s,float amount){return false;}
 @Override protected void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putFloat("Distance",travelled());t.putInt("MineFloor",floor);t.putBoolean("MineOwned",owned);if(generation!=null)t.putUUID("MineGeneration",generation);}
 @Override protected void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);entityData.set(DISTANCE,t.getFloat("Distance"));floor=t.getInt("MineFloor");owned=t.getBoolean("MineOwned");generation=t.hasUUID("MineGeneration")?t.getUUID("MineGeneration"):null;setNoGravity(true);}
}
