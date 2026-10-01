package com.stardew.craft.entity.monster;

import com.stardew.craft.port.PortItemStacks;import com.stardew.craft.combat.MonsterStats;
import com.stardew.craft.monster.*;
import com.stardew.craft.sound.ModSounds;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** BigSlime.cs: faceless gel, area-scaled constructor, held loot and death-time small slimes. */
@SuppressWarnings("null")
public final class MineBigSlimeEntity extends StardewMonsterEntity {
    public static final float WIDTH=1.94F,HEIGHT=1.4F;
    private static final EntityDataAccessor<Integer> COLOR=SynchedEntityData.defineId(MineBigSlimeEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MOVING=SynchedEntityData.defineId(MineBigSlimeEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ItemStack> HELD=SynchedEntityData.defineId(MineBigSlimeEntity.class,EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineBigSlimeEntity.class,EntityDataSerializers.LONG);
    private final SourceGroundMovement movement=new SourceGroundMovement(this,2,2);
    private int slipperiness=2,stunMilliseconds;private double fallSpeed,lastTrajectoryX,lastTrajectoryZ;
    private boolean splitPrepared;private double localGelPhase;
    public MineBigSlimeEntity(EntityType<? extends MineBigSlimeEntity> type,Level level){super(type,level);addTag("sd_mob_big_slime");}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,60).add(Attributes.ATTACK_DAMAGE,5).add(Attributes.MOVEMENT_SPEED,.25).add(com.stardew.craft.port.PortAttributes.STEP_HEIGHT.get(),0).add(Attributes.FOLLOW_RANGE,128);}
    @Override public void tick(){super.tick();if(level().isClientSide&&isAlive()){double before=localGelPhase;localGelPhase+=moving()?1/16.:1/32.;if((int)localGelPhase>(int)before)level().playLocalSound(getX(),getY(),getZ(),ModSounds.MONSTER_SLIME_HIT.get(),getSoundSource(),1,1,false);}}
    @Override protected net.minecraft.world.phys.AABB makeBoundingBox() {
        if (getAttributes() == null) return super.makeBoundingBox();
        return collisionBoxAtYaw(getYRot());
    }
    private net.minecraft.world.phys.AABB collisionBoxAtYaw(float yaw) {
        return BigSlimeRules.collisionBox(getX(), getY(), getZ(), yaw, getScale());
    }
    @Override public void setYRot(float yaw) {
        if (getAttributes() != null && Float.isFinite(yaw)) {
            var box = collisionBoxAtYaw(yaw);
            if (!level().isClientSide && isAddedToWorld() && !level().noCollision(this, box)) return;
            super.setYRot(yaw);
            setBoundingBox(box);
        } else super.setYRot(yaw);
    }
    @Override protected void registerGoals(){}
    @Override protected ResourceLocation definitionId(){return new ResourceLocation("stardewcraft:big_slime");}
    @Override protected void configureSpawn(MonsterDefinition definition,MonsterSpawnContext context){
        var base=MonsterStatResolver.base(definition,context,random);int area=BigSlimeRules.area(context.floor());var stats=base.combat();
        setInitialHealth(base.initialHealth()*BigSlimeRules.healthMultiplier(area));replaceCombatStats(MonsterStats.builder().damage(stats.getDamage()*BigSlimeRules.damageMultiplier(area)).resilience(stats.getResilience()).missChance(stats.getMissChance()).experience(stats.getExperience()*BigSlimeRules.experienceMultiplier(area)).build());
        entityData.set(COLOR,BigSlimeRules.color(area,random));if(BigSlimeRules.holdsCake(area,random))heldItem(MonsterSourceLoot.item("221",1));
        random.nextBoolean(); // Source consumes this before testing the disabled SC_NO_FOOD task rule.
    }
    @Override protected void defineSynchedData(){super.defineSynchedData();this.entityData.define(COLOR,0xff8a2be2);this.entityData.define(MOVING,false);this.entityData.define(HELD,ItemStack.EMPTY);this.entityData.define(HIT,-100L);}
    public int color(){return entityData.get(COLOR);}
    public boolean moving(){return entityData.get(MOVING);}
    public ItemStack heldItem(){return entityData.get(HELD);}
    public void heldItem(ItemStack item){entityData.set(HELD,item.copy());if(item.isEmpty())getPersistentData().remove("StardewMonsterHeldLoot");else getPersistentData().putString("StardewMonsterHeldLoot",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString());}
    public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}
    public void stunFor(int ms){stunMilliseconds=Math.max(stunMilliseconds,ms);}
    private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(com.stardew.craft.effect.ModMobEffects.AVOID_MONSTERS.get())&&(monsterState().context().generation()==null||com.stardew.craft.mining.OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
    @Override protected void customServerAiStep(){
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,121));
        var target=level().getNearestPlayer(getX(),getY(),getZ(),128,e->e instanceof Player p&&valid(p));setTarget(target);double x=getX(),z=getZ();movement.slipperiness(slipperiness);
        for(int i=0;i<3;i++){if(stunMilliseconds>0){stunMilliseconds=Math.max(0,stunMilliseconds-16);continue;}movement.tick(target,true,5,false,.01,()->{});}
        entityData.set(MOVING,Math.abs(getX()-x)+Math.abs(getZ()-z)>.00001);
        for(var player:((ServerLevel)level()).players())if(valid(player)&&getBoundingBox().intersects(player.getBoundingBox())&&!com.stardew.craft.combat.equipment.EquipmentResolver.getMergedStats(player).hasSlimeCharmer()){var attack=MonsterDamageSource.contact(this);player.hurt(attack,attack.baseDamage());}
    }
    @Override public boolean isPushable(){return false;}
    @Override public void travel(Vec3 input){fallSpeed=isNoGravity()?0:onGround()?-.08:Math.max(-3.9,(fallSpeed-.08)*.98);move(MoverType.SELF,new Vec3(0,fallSpeed,0));setDeltaMovement(Vec3.ZERO);}
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return false;}
    @Override public boolean hurt(DamageSource source,float amount){float before=getHealth();boolean accepted=super.hurt(source,amount);if(!level().isClientSide&&getHealth()<before){slipperiness=3;entityData.set(HIT,level().getGameTime());}return accepted;}
    @Override public void knockback(double strength,double x,double z){super.knockback(strength,x,z);var v=getDeltaMovement();lastTrajectoryX=v.x*64;lastTrajectoryZ=v.z*64;movement.knockback(lastTrajectoryX,lastTrajectoryZ);setDeltaMovement(Vec3.ZERO);}
    @Override protected void dropAllDeathLoot(DamageSource source){ServerLevel level=(ServerLevel)level(); // PORT(1.20.1): no ServerLevel parameter
        // LivingDeath has already accepted death. Add children before LivingDrops
        // settles population/ladder rolls, matching BigSlime.takeDamage ordering.
        if(!splitPrepared){splitPrepared=true;if(!source.is(DamageTypes.GENERIC_KILL)&&!source.is(DamageTypes.FELL_OUT_OF_WORLD)){
            int count=BigSlimeRules.splitCount(random);var context=monsterState().context();
            for(int i=0;i<count;i++){
                var type=context.floor()>79?com.stardew.craft.entity.ModEntities.SLUDGE.get():context.floor()>39?com.stardew.craft.entity.ModEntities.FROST_JELLY.get():com.stardew.craft.entity.ModEntities.GREEN_SLIME.get();var child=type.create(level);if(child==null)continue;
                child.moveTo(getX(),getY(),getZ(),getYRot(),0);
                // A normal constructor, not the breeding/offspring-color constructor.
                child.initialize(new MonsterSpawnContext(context.floor()>120?MonsterSpawnContext.Source.SKULL_CAVERN:MonsterSpawnContext.Source.ORDINARY_MINE,context.floor(),context.bottomReached(),context.generation()));
                double dx=(int)lastTrajectoryX/8+random.nextInt(-2,3),dz=(int)lastTrajectoryZ/8+random.nextInt(-2,3);float scale=.75F+random.nextInt(-5,10)/100F;child.fromBigSlime(scale,dx,dz);MonsterFactory.addOffspring(level,child);
            }
        }}
        super.dropAllDeathLoot(source);
    }
    private void splash(double x,double z,boolean slow){
        if(level() instanceof ServerLevel server){var type=slow?com.stardew.craft.weather.ModParticles.BIG_SLIME_SPLASH_SLOW.get():com.stardew.craft.weather.ModParticles.BIG_SLIME_SPLASH.get();server.sendParticles(com.stardew.craft.port.net.minecraft.core.particles.ColorParticleOption.create(type,color()),getX()+x,getY()+.5,getZ()+z,1,0,0,0,0);}
    }
    @Override protected void onFinalDeath(DamageSource source){splash(0,0,false);}
    @Override protected void tickDeath(){super.tickDeath();if(deathTime==2)splash(-.5,0,false);if(deathTime==4)splash(.5,0,false);if(deathTime==6)splash(0,-.5,true);}
    @Override protected SoundEvent getHurtSound(DamageSource source){return ModSounds.MONSTER_BAT_HIT.get();}
    @Override protected SoundEvent getDeathSound(){return ModSounds.SLIMEDEAD.get();}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putInt("BigSlimeColor",color());if(!heldItem().isEmpty())t.put("BigSlimeHeld",PortItemStacks.save(heldItem(), level().registryAccess()));t.put("BigSlimeMovement",movement.save());t.putInt("BigSlimeSlip",slipperiness);t.putInt("BigSlimeStun",stunMilliseconds);t.putDouble("BigSlimeTrajectoryX",lastTrajectoryX);t.putDouble("BigSlimeTrajectoryZ",lastTrajectoryZ);t.putBoolean("BigSlimeSplit",splitPrepared);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);entityData.set(COLOR,t.getInt("BigSlimeColor"));heldItem(t.contains("BigSlimeHeld")?PortItemStacks.parseOptional(level().registryAccess(),t.getCompound("BigSlimeHeld")):ItemStack.EMPTY);movement.load(t.getCompound("BigSlimeMovement"));slipperiness=t.contains("BigSlimeSlip")?t.getInt("BigSlimeSlip"):2;stunMilliseconds=t.getInt("BigSlimeStun");lastTrajectoryX=t.getDouble("BigSlimeTrajectoryX");lastTrajectoryZ=t.getDouble("BigSlimeTrajectoryZ");splitPrepared=t.getBoolean("BigSlimeSplit");}
}
