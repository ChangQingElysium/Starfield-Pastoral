package com.stardew.craft.entity.projectile;

import com.stardew.craft.entity.monster.MineSkeletonEntity;
import com.stardew.craft.monster.MonsterDamageSource;
import com.stardew.craft.mining.*;
import com.stardew.craft.monster.MonsterSpace;
import com.stardew.craft.monster.MonsterProjectileMovement;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

/** BasicProjectile index 4: 8 source px/frame, no gravity, 100ms grace, melee-breakable. */
@SuppressWarnings("null")
public final class SkeletonBoneEntity extends Projectile {
    public static final float WIDTH=21F/64;
    private int sourceFrames,floor;private float damage=10;private boolean owned;private java.util.UUID generation;
    public SkeletonBoneEntity(EntityType<? extends SkeletonBoneEntity> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(){}
    public void launch(MineSkeletonEntity owner,Player player){
        setOwner(owner);damage=owner.monsterState().stats().getDamage();var context=owner.monsterState().context();owned=context.generation()!=null;generation=context.generation();floor=context.floor();
        // Aim once from the current throwing hand to the actual body; no homing or gravity.
        setPos(owner.getX(),owner.getY()+1.1,owner.getZ());var delta=MonsterSpace.aim(position(),player.getBoundingBox(),1);setDeltaMovement(delta.scale(.375));setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
    }
    public int sourceFrames(){return sourceFrames;}
    public boolean collisionReady(){return sourceFrames*16>100;}
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){setPos(position().add(getDeltaMovement()));return;}
        if(owned){var data=MineFloorDataManager.get((ServerLevel)level()).getFloorData(floor);if(data==null||!java.util.Objects.equals(generation,data.generationId())){discard();return;}}
        for(int i=0;i<3&&!isRemoved();i++){
            sourceFrames++;
            var wall=MonsterProjectileMovement.step(this,getDeltaMovement().scale(1./3),collisionReady(),owned,floor,player->{
                boolean vulnerable=player.invulnerableTime<=0&&!com.stardew.craft.combat.equipment.YobaProtectionState.isActive(player,level().getGameTime())&&!(player.isUsingItem()&&player.getUseItem().getUseAnimation()==net.minecraft.world.item.UseAnim.EAT);
                var hit=new MonsterDamageSource(damageSources().mobProjectile(this,null),MonsterDamageSource.Kind.PROJECTILE,damage);
                if(vulnerable)player.hurt(hit,damage);shatterVisual();if(vulnerable)discard();return vulnerable;
            });
            if(wall!=null)breakByWeapon();
        }
        // The ordinary source removes off-map shots. Free-world command testing has no map boundary.
        if(!owned&&(tickCount>1200||!level().hasChunkAt(blockPosition())))discard();
    }
    public void breakByWeapon(){if(!level().isClientSide&&!isRemoved()){shatterVisual();discard();}}
    private void shatterVisual(){((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(.81F,.69F,.46F),.4F),getX(),getY(),getZ(),6,.08,.08,.08,.045);playSound(ModSounds.SKELETON_HIT.get(),1,1);}
    @Override public boolean isPickable(){return !isRemoved();}
    @Override public boolean hurt(DamageSource source,float amount){if(source.getEntity() instanceof Player player&&(com.stardew.craft.combat.WeaponCombatIdentity.isWeapon(player.getMainHandItem())||player.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem)){breakByWeapon();return true;}return false;}
    @Override protected void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putInt("SourceFrames",sourceFrames);t.putFloat("MonsterDamage",damage);t.putInt("MineFloor",floor);t.putBoolean("MineOwned",owned);if(generation!=null)t.putUUID("MineGeneration",generation);}
    @Override protected void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);sourceFrames=t.getInt("SourceFrames");damage=t.getFloat("MonsterDamage");floor=t.getInt("MineFloor");owned=t.getBoolean("MineOwned");generation=t.hasUUID("MineGeneration")?t.getUUID("MineGeneration"):null;setNoGravity(true);}
}
