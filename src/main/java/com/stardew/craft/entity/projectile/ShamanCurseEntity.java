package com.stardew.craft.entity.projectile;
import com.stardew.craft.entity.monster.MineShadowShamanEntity;
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
/** Source DebuffingProjectile 14/7: wavy 15px/frame, four wall bounces, status only. */
@SuppressWarnings("null")
public final class ShamanCurseEntity extends Projectile {
    private int frames,bounces=4,tailCounter=50,floor;private boolean owned;private java.util.UUID generation;
    private final java.util.ArrayDeque<Vec3> tail=new java.util.ArrayDeque<>();
    public ShamanCurseEntity(EntityType<? extends ShamanCurseEntity> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(){}
    public void launch(MineShadowShamanEntity owner,Player player){setOwner(owner);var c=owner.monsterState().context();owned=c.generation()!=null;generation=c.generation();floor=c.floor();setPos(owner.getX(),owner.getY()+.85,owner.getZ());setDeltaMovement(MonsterSpace.aim(position(),player.getBoundingBox(),45./64));}
    public int bouncesLeft(){return bounces;}public int sourceFrames(){return frames;}public java.util.List<Vec3> tail(){return java.util.List.copyOf(tail);}
    public static Vec3 sourceStep(Vec3 velocity,long tick,int substep){double phase=((int)(Math.floorMod(tick,20)*50+substep*1000./60)%1000)*Math.PI/128;return velocity.scale(1./3).add(Math.sin(phase)/8,0,Math.cos(phase)/8);}
    /** Rotate the source wave with the shot, including elevation, rather than fixing it to world XZ. */
    public static Vec3 spatialStep(Vec3 velocity,long tick,int substep){
        Vec3 wave=sourceStep(Vec3.ZERO,tick,substep),forward=velocity.normalize();
        Vec3 right=forward.cross(new Vec3(0,1,0)).normalize();
        if(right.lengthSqr()<1e-8)right=new Vec3(1,0,0);
        return velocity.scale(1./3).add(right.scale(wave.x)).add(forward.scale(wave.z));
    }
    @Override public void tick(){
        super.tick();if(!level().isClientSide&&owned){var d=MineFloorDataManager.get((ServerLevel)level()).getFloorData(floor);if(d==null||!java.util.Objects.equals(generation,d.generationId())){discard();return;}}
        for(int i=0;i<3&&!isRemoved();i++){
            var step=spatialStep(getDeltaMovement(),level().getGameTime(),i);frames++;
            if(level().isClientSide)setPos(position().add(step));
            else {
                var wall=MonsterProjectileMovement.step(this,step,frames*16>100,owned,floor,this::hitPlayer);
                if(wall!=null){
                    if(bounces<=0){breakByWeapon();break;}
                    bounces--;var velocity=getDeltaMovement();if(velocity.dot(wall.normal())<0)setDeltaMovement(MonsterSpace.reflect(velocity,wall.normal()));hurtMarked=true;
                }
            }
            tailCounter-=16;if(tailCounter<=0){tailCounter=50;tail.addFirst(position());while(tail.size()>4)tail.removeLast();}
        }
        if(!level().isClientSide&&!owned&&(tickCount>1200||!level().hasChunkAt(blockPosition())))discard();
    }
    /** Immunity consumes neither HP nor the projectile; no contact-only Yoba/eating/i-frame gate. */
    public boolean hitPlayer(ServerPlayer player){
        if(level().isClientSide||isRemoved())return false;
        var protection=EquipmentNegativeStatusProtection.decideMilliseconds(player,8000);
        if(protection.resisted()||player.hasEffect(ModMobEffects.SQUID_INK_RAVIOLI.get())||TrinketEffectHandler.blocksNegativeEffects(player))return false;
        EquipmentMobEffectHandler.addPreAdjustedEffect(player,new MobEffectInstance(ModMobEffects.JINXED.get(),protection.durationTicks(),0));
        explode();playSound(ModSounds.DEBUFF_HIT.get(),1,(float)Math.pow(2,-.1+random.nextDouble()*.353));discard();return true;
    }
    public void breakByWeapon(){if(!level().isClientSide&&!isRemoved()){explode();discard();}}
    private void explode(){((ServerLevel)level()).sendParticles(com.stardew.craft.weather.ModParticles.SHAMAN_CURSE_IMPACT.get(),getX(),getY(),getZ(),1,0,0,0,0);}
    @Override public boolean isPickable(){return !isRemoved();}
    @Override public boolean hurt(DamageSource source,float amount){if(source.getEntity() instanceof Player p&&(com.stardew.craft.combat.WeaponCombatIdentity.isWeapon(p.getMainHandItem())||p.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem)){breakByWeapon();return true;}return false;}
    @Override protected void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putInt("SourceFrames",frames);t.putInt("Bounces",bounces);t.putInt("TailCounter",tailCounter);t.putInt("MineFloor",floor);t.putBoolean("MineOwned",owned);if(generation!=null)t.putUUID("MineGeneration",generation);}
    @Override protected void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);frames=t.getInt("SourceFrames");bounces=t.contains("Bounces")?t.getInt("Bounces"):4;tailCounter=t.getInt("TailCounter");floor=t.getInt("MineFloor");owned=t.getBoolean("MineOwned");generation=t.hasUUID("MineGeneration")?t.getUUID("MineGeneration"):null;setNoGravity(true);}
}
