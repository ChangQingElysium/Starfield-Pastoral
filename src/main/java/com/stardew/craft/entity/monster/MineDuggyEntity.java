package com.stardew.craft.entity.monster;

import com.stardew.craft.block.mine.*;
import com.stardew.craft.combat.MonsterStats;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.mining.*;
import com.stardew.craft.monster.*;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Duggy.cs: hidden tile ambush, zero knockback and exact source frame gates. */
@SuppressWarnings("null")
public final class MineDuggyEntity extends StardewMonsterEntity {
    public static final float WIDTH=.75F,HEIGHT=.73F;
    private static final EntityDataAccessor<Float> CURSOR=SynchedEntityData.defineId(MineDuggyEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineDuggyEntity.class,EntityDataSerializers.LONG);
    private final DuggyLifecycle lifecycle=new DuggyLifecycle();
    private int stunMilliseconds;
    public MineDuggyEntity(EntityType<? extends MineDuggyEntity> type,Level level){super(type,level);setNoGravity(true);setInvisible(true);addTag("sd_mob_duggy");}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,40).add(Attributes.ATTACK_DAMAGE,0)
            .add(Attributes.MOVEMENT_SPEED,0).add(Attributes.FOLLOW_RANGE,64).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override protected void registerGoals(){}
    @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft:duggy");}
    @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){var r=MonsterStatResolver.base(d,c,random);setInitialHealth(r.initialHealth());replaceCombatStats(r.combat());syncDamage();}
    @Override protected void defineSynchedData(){super.defineSynchedData();this.entityData.define(CURSOR,0F);this.entityData.define(HIT,-100L);}
    public double cursor(float p){double c=entityData.get(CURSOR),edge=c<2?2:c<4?4:c<8?8:10;return Math.min(edge-.00001,c+p*.05/(c<4?.1:.22));}
    public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}
    public DuggyLifecycle lifecycle(){return lifecycle;}
    public void stunFor(int milliseconds){stunMilliseconds=Math.max(stunMilliseconds,milliseconds);}
    private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS.get())
            &&(monsterState().context().generation()==null||OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
    private boolean diggable(Player p){
        BlockPos floor=p.blockPosition().below();var state=level().getBlockState(floor);boolean soil=state.is(Blocks.DIRT)||state.is(Blocks.COARSE_DIRT)||state.is(Blocks.ROOTED_DIRT);
        for(var theme:MineBuildingTheme.values())if(state.is(theme.soil())||state.is(theme.looseSoil())){soil=true;break;}
        if(!soil||Math.abs(p.getY()-getY())>1.1)return false;
        if(monsterState().context().generation()==null)return true;
        int f=monsterState().context().floor();var layout=OrdinaryMineLayout.load((ServerLevel)level(),f);var o=layout.origin(f);
        var cell=layout.cell(floor.getX()-o.getX()-layout.tileX,floor.getZ()-o.getZ()-layout.tileZ);
        // Reachable floor metadata excludes the original map's blocked/NPC barrier cells.
        return cell!=null&&cell.reachable()&&(cell.diggable()||cell.back()==0);
    }
    private void syncDamage(){
        var stats=monsterState().stats();int damage=lifecycle.damage();if(stats.getDamage()==damage)return;
        replaceCombatStats(MonsterStats.builder().damage(damage).resilience(stats.getResilience()).missChance(stats.getMissChance()).experience(stats.getExperience()).isDangerous(stats.isDangerous()).build());
    }
    @Override protected void customServerAiStep(){
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));
        var target=level().getNearestPlayer(getX(),getY(),getZ(),64,e->e instanceof Player p&&valid(p));setTarget(target);
        if(stunMilliseconds>0){stunMilliseconds=Math.max(0,stunMilliseconds-50);return;}
        for(int i=0;i<3;i++){
            boolean wasHidden=lifecycle.hidden();
            boolean trigger=target!=null&&getBoundingBox().inflate(2,0,2).contains(target.getX(),getY()+.2,target.getZ())&&diggable(target);
            if(wasHidden&&trigger){
                var at=target.blockPosition();setPos(at.getX()+.5,at.getY(),at.getZ()+.5);playSound(ModSounds.DUGGY.get(),1,1);soilParticles();
            }
            boolean reset=lifecycle.step(1000./60,trigger);
            setInvisible(lifecycle.hidden());entityData.set(CURSOR,(float)lifecycle.cursor());syncDamage();
            startAction(lifecycle.hidden()?0:lifecycle.frame()<2?1:lifecycle.frame()<4?2:lifecycle.frame()<8?3:4,false);
            if(reset)clearSourceTile();
            if(lifecycle.damage()>0)for(var p:((ServerLevel)level()).players())if(valid(p)&&getBoundingBox().intersects(p.getBoundingBox())){var attack=MonsterDamageSource.contact(this);p.hurt(attack,attack.baseDamage());}
        }
    }
    private void clearSourceTile(){
        if(monsterState().context().generation()==null)return;
        var server=(ServerLevel)level();var standing=blockPosition();var floor=standing.below();var state=level().getBlockState(floor);
        for(var theme:MineBuildingTheme.values())if(state.is(theme.soil())||state.is(theme.looseSoil())){level().setBlock(floor,theme.soil().defaultBlockState(),3);break;}
        // Source removes objects without loot. Architecture is not a spawned object.
        var object=level().getBlockState(standing).getBlock();
        if(!OrdinaryMineRuntime.isArchitecture(server,standing)&&(object instanceof MineStoneBlock||object instanceof MineralNodeBlock||object instanceof MineBarrelBlock||object instanceof MineGroundWeedsBlock||object instanceof MineRockClumpBlock)){
            level().setBlock(standing,Blocks.AIR.defaultBlockState(),3);
            var data=MonsterFactory.ownedFloor(this);if(data!=null){data.forgetGeneratedStone(standing);MineFloorDataManager.get(server).setFloorData(monsterState().context().floor(),data);}
        }
    }
    private void soilParticles(){((ServerLevel)level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,level().getBlockState(blockPosition().below())),getX(),getY()+.05,getZ(),7,.23,.03,.23,.07);}
    @Override public boolean hurt(DamageSource source,float amount){
        if(source.is(DamageTypes.GENERIC_KILL)||source.is(DamageTypes.FELL_OUT_OF_WORLD))return super.hurt(source,amount);
        if(isInvisible())return false;float hp=getHealth();boolean result=super.hurt(source,amount);if(!level().isClientSide&&getHealth()<hp)entityData.set(HIT,level().getGameTime());return result;
    }
    @Override public boolean isPickable(){return !isInvisible()&&super.isPickable();}
    @Override public boolean isPushable(){return false;}
    @Override public void push(Entity other){}
    @Override public void knockback(double strength,double x,double z){}
    @Override public void travel(Vec3 input){setDeltaMovement(Vec3.ZERO);}
    @Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport){super.lerpTo(x, y, z, yaw, pitch, 1, teleport);}
    @Override protected SoundEvent getHurtSound(DamageSource s){return ModSounds.MONSTER_BAT_HIT.get();}
    @Override protected SoundEvent getDeathSound(){return ModSounds.MONSTER_CRAB_DEATH.get();}
    @Override protected void onFinalDeath(DamageSource s){((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(.55F,.13F,.16F),.65F),getX(),getY()+.4,getZ(),16,.2,.2,.2,.03);}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("DuggyLifecycle",lifecycle.save());t.putInt("DuggyStun",stunMilliseconds);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);if(t.contains("DuggyLifecycle"))lifecycle.load(t.getCompound("DuggyLifecycle"));stunMilliseconds=t.getInt("DuggyStun");entityData.set(CURSOR,(float)lifecycle.cursor());setInvisible(lifecycle.hidden());setNoGravity(true);if(initialized())syncDamage();}
}
