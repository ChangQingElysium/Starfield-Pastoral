package com.stardew.craft.entity.projectile;
import com.stardew.craft.entity.monster.MineSquidKidEntity;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.combat.equipment.*;
import com.stardew.craft.item.trinket.TrinketEffectHandler;
import com.stardew.craft.mining.*;
import com.stardew.craft.monster.MonsterSpace;
import com.stardew.craft.monster.MonsterProjectileMovement;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;
/** Ordinary SquidKid BasicProjectile: 15 damage, sprite 10, three bounces, 8px/frame, four tails. */
@SuppressWarnings("null")
public final class SquidFireballEntity extends Projectile {
    private int frames,bounces=3,tailCounter=50,floor;private boolean owned;private java.util.UUID generation;
    private final java.util.ArrayDeque<Vec3> tail=new java.util.ArrayDeque<>();
    public SquidFireballEntity(EntityType<? extends SquidFireballEntity> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(){}
    public void launch(MineSquidKidEntity owner,Player player){setOwner(owner);var c=owner.monsterState().context();owned=c.generation()!=null;generation=c.generation();floor=c.floor();setPos(owner.getX(),owner.getY()+owner.getBbHeight()*.5,owner.getZ());setDeltaMovement(MonsterSpace.aim(position(),player.getBoundingBox(),24./64));}
    public int bouncesLeft(){return bounces;}public int sourceFrames(){return frames;}public java.util.List<Vec3> tail(){return java.util.List.copyOf(tail);}
    @Override public void tick(){
        super.tick();if(!level().isClientSide&&owned){var d=MineFloorDataManager.get((ServerLevel)level()).getFloorData(floor);if(d==null||!java.util.Objects.equals(generation,d.generationId())){discard();return;}}
        for(int i=0;i<3&&!isRemoved();i++){
            var step=getDeltaMovement().scale(1./3);frames++;
            if(level().isClientSide)setPos(position().add(step));
            else {
                var wall=MonsterProjectileMovement.step(this,step,frames*16>100,owned,floor,this::hitPlayer);
                if(wall!=null){
                    if(bounces<=0){breakByWeapon();break;}
                    bounces--;setDeltaMovement(MonsterSpace.reflect(getDeltaMovement(),wall.normal()));hurtMarked=true;
                }
            }
            tailCounter-=16;if(tailCounter<=0){tailCounter=50;tail.addFirst(position());while(tail.size()>4)tail.removeLast();}
        }
        if(!level().isClientSide&&!owned&&(tickCount>1200||!level().hasChunkAt(blockPosition())))discard();
    }
    /** BasicProjectile has no contact damager: no thorns; vulnerable hits consume the projectile. */
    public boolean hitPlayer(ServerPlayer player){
        if(level().isClientSide||isRemoved())return false;
        boolean vulnerable=player.invulnerableTime<=0&&!YobaProtectionState.isActive(player,level().getGameTime())&&!(player.isUsingItem()&&player.getUseItem().getUseAnimation()==net.minecraft.world.item.UseAnim.EAT);
        if(vulnerable){var hit=new com.stardew.craft.monster.MonsterDamageSource(damageSources().mobProjectile(this,null),com.stardew.craft.monster.MonsterDamageSource.Kind.PROJECTILE,15);player.hurt(hit,15);}
        explode();if(vulnerable)discard();return vulnerable;
    }
    public void breakByWeapon(){if(!level().isClientSide&&!isRemoved()){explode();discard();}}
    private void explode(){((ServerLevel)level()).sendParticles(com.stardew.craft.weather.ModParticles.SQUID_FIREBALL_IMPACT.get(),getX(),getY(),getZ(),1,0,0,0,0);}
    @Override public boolean isPickable(){return !isRemoved();}
    @Override public boolean hurt(DamageSource source,float amount){if(source.getEntity() instanceof Player p&&(com.stardew.craft.combat.WeaponCombatIdentity.isWeapon(p.getMainHandItem())||p.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem)){breakByWeapon();return true;}return false;}
    @Override protected void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putInt("SourceFrames",frames);t.putInt("Bounces",bounces);t.putInt("TailCounter",tailCounter);t.putInt("MineFloor",floor);t.putBoolean("MineOwned",owned);if(generation!=null)t.putUUID("MineGeneration",generation);}
    @Override protected void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);frames=t.getInt("SourceFrames");bounces=t.contains("Bounces")?t.getInt("Bounces"):3;tailCounter=t.getInt("TailCounter");floor=t.getInt("MineFloor");owned=t.getBoolean("MineOwned");generation=t.hasUUID("MineGeneration")?t.getUUID("MineGeneration"):null;setNoGravity(true);}
}
