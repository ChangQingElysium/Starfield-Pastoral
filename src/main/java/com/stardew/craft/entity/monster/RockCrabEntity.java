package com.stardew.craft.entity.monster;

import com.stardew.craft.monster.*;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** RockCrab.cs: ordinary, lava and iridium crabs. Cardinal source movement, five shell hits and fleeing. */
@SuppressWarnings("null")
public final class RockCrabEntity extends StardewMonsterEntity {
    public static final int DISGUISE=0, ACTIVE=1, BARE=2;
    public static final float WIDTH=1.0F, HEIGHT=.76F;
    private static final EntityDataAccessor<Integer> SHELL = SynchedEntityData.defineId(RockCrabEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SHELL_GONE = SynchedEntityData.defineId(RockCrabEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MOVING = SynchedEntityData.defineId(RockCrabEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> HIT_START = SynchedEntityData.defineId(RockCrabEntity.class,EntityDataSerializers.LONG);
    private final String variant;
    private final SourceGroundMovement movement;
    private boolean waiter;
    private double fallSpeed;
    private int stunMilliseconds, slipperiness=2;
    private long lastPickTick=Long.MIN_VALUE;

    public RockCrabEntity(EntityType<? extends RockCrabEntity> type,Level level) { this(type,level,"rock_crab"); }
    public RockCrabEntity(EntityType<? extends RockCrabEntity> type,Level level,String variant) { super(type,level);this.variant=variant;movement=new SourceGroundMovement(this,variant.equals("rock_crab")?2:3,2);addTag("sd_mob_crab"); }
    public String variant() { return variant; }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,30).add(Attributes.ATTACK_DAMAGE,5)
                .add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,64).add(Attributes.STEP_HEIGHT,0);
    }
    @Override protected void registerGoals() {}
    @Override protected ResourceLocation definitionId() { return new ResourceLocation("stardewcraft:"+variant); }
    @Override protected void configureSpawn(MonsterDefinition definition,MonsterSpawnContext context) {
        var resolved=MonsterStatResolver.base(definition,context,random);
        setInitialHealth(resolved.initialHealth());replaceCombatStats(resolved.combat());
        waiter=random.nextDouble()<.4;
        if(variant.equals("iridium_crab"))waiter=true;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);builder.define(SHELL,5);builder.define(SHELL_GONE,false);builder.define(MOVING,false);builder.define(HIT_START,-100L);
    }
    public int shellHealth() { return entityData.get(SHELL); }
    public boolean shellGone() { return entityData.get(SHELL_GONE); }
    public boolean moving() { return entityData.get(MOVING); }
    public boolean waiter() { return waiter; }
    public double shellHitTime(float partial) { return (level().getGameTime()-entityData.get(HIT_START)+partial)/20.; }
    public boolean disguised() { return phase()==DISGUISE && !shellGone(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source) { return false; }
    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling() { return super.getBoundingBoxForCulling().inflate(.6); }
    private boolean valid(Player p) {
        return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS)
                &&(monsterState().context().generation()==null||com.stardew.craft.mining.OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());
    }
    public static boolean withinNotice(int x,int z,int playerX,int playerZ) { return Math.abs(x-playerX)<=3&&Math.abs(z-playerZ)<=3; }
    @Override protected void customServerAiStep() {
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));
        var nearest=level().getNearestPlayer(getX(),getY(),getZ(),64,e->e instanceof Player p&&valid(p));
        setTarget(nearest);var target=nearest;
        double beforeX=getX(),beforeZ=getZ();
        movement.slipperiness(slipperiness);
        for(int sub=0;sub<3;sub++) {
            if(stunMilliseconds>0){stunMilliseconds=Math.max(0,stunMilliseconds-16);continue;}
            if(shellGone()&&target!=null) {
                // Bare RockCrab.update skips NPC pursuit and chooses the opposite cardinal direction each frame.
                movement.faceTarget(target);movement.direction((movement.facing()+2)%4);
                movement.tick(null,false,0,false,0,()->{});
            } else {
                boolean pursue=target!=null&&!waiter&&withinNotice(getBlockX(),getBlockZ(),target.getBlockX(),target.getBlockZ());
                if(!pursue)movement.halt();
                movement.tick(pursue?target:null,pursue,3,true,0,()->{});
            }
        }
        boolean moved=Math.abs(getX()-beforeX)+Math.abs(getZ()-beforeZ)>.00001;
        entityData.set(MOVING,moved);
        // An obstructed/stopped shelled crab returns to frame % 4 == 0, even with a nearby target.
        startAction(shellGone()?BARE:moved?ACTIVE:DISGUISE,false);
        for(var player:((ServerLevel)level()).players())if(valid(player)&&getBoundingBox().intersects(player.getBoundingBox())) {
            var attack=MonsterDamageSource.contact(this);player.hurt(attack,attack.baseDamage());
        }
    }
    public void stunFor(int ms) { stunMilliseconds=Math.max(stunMilliseconds,ms); }
    @Override public void travel(Vec3 input) {
        // Horizontal movement is already integrated at 60 Hz. MC supplies only the added vertical axis.
        fallSpeed=isNoGravity()?0:onGround()?-.08:Math.max(-3.9,(fallSpeed-.08)*.98);
        move(MoverType.SELF,new Vec3(0,fallSpeed,0));setDeltaMovement(Vec3.ZERO);
    }
    public boolean pickShell(Player player) {
        if(level().isClientSide)return shellHealth()>0;
        if(!isAlive()||shellHealth()<=0)return false;
        long now=level().getGameTime();
        var tool=player.getMainHandItem();
        boolean nativePick=tool.getItem() instanceof com.stardew.craft.item.tool.StardewPickaxeItem;
        int ticks=nativePick&&com.stardew.craft.enchantment.StardewEnchantments.has(tool,com.stardew.craft.enchantment.StardewEnchantments.SWIFT)?7:10;
        if(lastPickTick!=Long.MIN_VALUE&&now-lastPickTick<ticks)return true;
        // Source complete swing: 475 ms, Swift 312 ms, rounded up to 20 TPS. Shell damage ignores tier/Power.
        if(player instanceof ServerPlayer server) {
            boolean efficient=nativePick&&com.stardew.craft.enchantment.StardewEnchantments.has(tool,com.stardew.craft.enchantment.StardewEnchantments.EFFICIENT);
            float cost=efficient?0:Math.max(0,2-.1F*com.stardew.craft.player.PlayerStardewDataAPI.getSkillLevel(server,com.stardew.craft.player.SkillType.MINING));
            if(!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergyOrNotify(server,cost))return true;
        }
        lastPickTick=now;
        waiter=false;entityData.set(SHELL,shellHealth()-1);entityData.set(HIT_START,now);
        playSound(ModSounds.HAMMER.get(),1,1);
        Vec3 away=position().subtract(player.position()).multiply(1,0,1).normalize();
        if(away.lengthSqr()==0)away=player.getLookAngle().multiply(1,0,1).normalize();
        movement.knockback(away.x*(50+random.nextInt(-20,20)),away.z*(50+random.nextInt(-20,20)));
        if(shellHealth()<=0)entityData.set(SHELL_GONE,true);
        startAction(shellGone()?BARE:ACTIVE,true);
        if(shellGone())brokenParticles();
        return true;
    }
    private void breakShell() {
        if(shellGone())return;
        waiter=false;entityData.set(SHELL_GONE,true);startAction(BARE,true);
        // Source bombs expose the body without consuming pickaxe shellHealth or stoneCrack debris.
    }
    private void brokenParticles() {
        playSound(ModSounds.STONE_CRACK.get(),1,1);
        if(level() instanceof ServerLevel server)server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                (variant.equals("lava_crab")?com.stardew.craft.block.ModBlocks.MINE_STONE_56.get():variant.equals("iridium_crab")?com.stardew.craft.block.ModBlocks.MINE_STONE_765.get():com.stardew.craft.block.ModBlocks.MINE_STONE_32.get()).defaultBlockState()),getX(),getY()+.4,getZ(),
                random.nextInt(2,7)+random.nextInt(2,7),.25,.15,.25,.12);
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(source.is(DamageTypes.GENERIC_KILL)||source.is(DamageTypes.FELL_OUT_OF_WORLD))return super.hurt(source,amount);
        boolean explosion=source.is(DamageTypeTags.IS_EXPLOSION);
        if(!explosion&&source.getDirectEntity() instanceof Player p&&p.getMainHandItem().is(ItemTags.PICKAXES)&&shellHealth()>0){pickShell(p);return false;}
        if(!level().isClientSide&&explosion)breakShell();
        if(disguised()) { if(!level().isClientSide)playSound(ModSounds.CRAFTING.get(),1,1);return false; }
        float before=getHealth();boolean result=super.hurt(source,amount);
        if(!level().isClientSide&&getHealth()<before) {
            slipperiness=3;
        }
        return result;
    }
    @Override public void knockback(double strength,double x,double z) {
        super.knockback(strength,x,z);var impulse=getDeltaMovement();movement.knockback(impulse.x*64,impulse.z*64);setDeltaMovement(Vec3.ZERO);
    }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.MONSTER_BAT_HIT.get(); } // original hitEnemy sample
    @Override protected SoundEvent getDeathSound() { return ModSounds.MONSTER_CRAB_DEATH.get(); }
    @Override protected void onFinalDeath(DamageSource source) { movement.halt(); }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);tag.putInt("CrabShell",shellHealth());tag.putBoolean("CrabShellGone",shellGone());tag.putBoolean("CrabWaiter",waiter);
        tag.put("CrabMovement",movement.save());tag.putInt("CrabSlipperiness",slipperiness);tag.putInt("CrabStun",stunMilliseconds);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);entityData.set(SHELL,tag.contains("CrabShell")?Math.clamp(tag.getInt("CrabShell"),0,5):5);
        entityData.set(SHELL_GONE,tag.getBoolean("CrabShellGone"));waiter=tag.getBoolean("CrabWaiter");
        movement.load(tag.getCompound("CrabMovement"));slipperiness=tag.contains("CrabSlipperiness")?tag.getInt("CrabSlipperiness"):2;stunMilliseconds=tag.getInt("CrabStun");
    }
}
