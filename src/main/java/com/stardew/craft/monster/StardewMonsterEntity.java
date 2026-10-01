package com.stardew.craft.monster;

import com.stardew.craft.combat.MonsterStats;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** Shared identity, persistence and action clock; species own movement and visual reactions. */
@SuppressWarnings("null")
public abstract class StardewMonsterEntity extends Monster {
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(StardewMonsterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> ACTION_START = SynchedEntityData.defineId(StardewMonsterEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> ACTION_SEQUENCE = SynchedEntityData.defineId(StardewMonsterEntity.class, EntityDataSerializers.INT);
    private MonsterState monsterState;
    protected int phaseTicks;
    private int sourceHitRecoveryTicks;
    private final java.util.ArrayDeque<java.util.List<Runnable>> hurtCompletions = new java.util.ArrayDeque<>();

    protected StardewMonsterEntity(EntityType<? extends StardewMonsterEntity> type, Level level) {
        super(type, level);
        xpReward = 0;
    }
    protected abstract ResourceLocation definitionId();
    protected abstract void configureSpawn(MonsterDefinition definition, MonsterSpawnContext context);
    public final MonsterState monsterState() { return monsterState; }
    public final boolean initialized() { return monsterState != null; }
    public final void initialize(MonsterSpawnContext context) {
        if (initialized() || level().isClientSide) return;
        var definition = MonsterDefinitions.require(definitionId());
        monsterState = new MonsterState(definition, context, random);
        configureSpawn(definition, context);
        // Monster.InitializeForLocation, ordinary difficulty. Store the outcome, never reroll on load.
        if (definition.mineMonster() && context.bottomReached() && random.nextDouble() < .001) {
            monsterState.addBornDrop(new ResourceLocation("stardewcraft", random.nextBoolean() ? "diamond" : "prismatic_shard"));
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            int count = com.stardew.craft.festival.desert.DesertFestivalMineService.monsterEggCount(server, context.floor(), random);
            for (int i = 0; i < count; i++) monsterState.addBornDrop(new ResourceLocation("stardewcraft", "calico_egg"));
        }
    }
    public final void replaceCombatStats(MonsterStats stats) {
        if (monsterState != null) monsterState.stats(stats);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(stats.getDamage());
        // Compatibility projection for unmigrated equipment/addon readers, never a second authority.
        getPersistentData().put(MonsterStats.TAG_STARDEW_MONSTER, stats.toNBT());
    }
    protected final void setInitialHealth(int health) {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(monsterState.sourceMaxHealth(), health));
        setHealth(health);
    }
    @Override public void tick() {
        if (!level().isClientSide && initialized() && monsterState.context().generation() != null
                && MonsterFactory.ownedFloor(this) == null) {
            if (monsterState.life() == MonsterState.Life.ALIVE || monsterState.life() == MonsterState.Life.DOWNED) monsterState.life(MonsterState.Life.CLEANUP);
            discard();
            return;
        }
        if (!level().isClientSide) { phaseTicks++; if (sourceHitRecoveryTicks > 0) sourceHitRecoveryTicks--; }
        super.tick();
    }
    public final boolean claimSettlement(MonsterState.Settlement settlement) {
        return initialized() && monsterState.claim(settlement);
    }
    /** Post damage precedes totem/death acceptance in LivingEntity.hurt. Keep each nested hit separate. */
    @Override public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.getEntity() instanceof net.minecraft.world.entity.player.Player
                && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN)) {
            if (sourceHitRecoveryTicks > 0) return false;
            // Ordinary source swings use 225/150 ms, not vanilla's 10-tick stronger-hit subtraction.
            invulnerableTime = 0;
        }
        var completions = new java.util.ArrayList<Runnable>();
        hurtCompletions.push(completions);
        boolean completed = false;
        try {
            boolean result = super.hurt(source, amount);
            completed = true;
            return result;
        } finally {
            hurtCompletions.pop();
            if (completed) completions.forEach(Runnable::run);
        }
    }
    public final void sourceWeaponRecovery(boolean dagger) { sourceHitRecoveryTicks = dagger ? 3 : 5; }
    public final boolean sourceHitRecoveryActive() { return sourceHitRecoveryTicks > 0; }
    public final void afterMortality(Runnable action) {
        if (isDeadOrDying() && !hurtCompletions.isEmpty()) hurtCompletions.peek().add(action);
        else action.run();
    }
    /** Called only once death has been accepted (drops, or super.die completion). */
    public final void acceptFinalDeath() {
        if (initialized() && monsterState.life() == MonsterState.Life.ALIVE) monsterState.life(MonsterState.Life.DEAD);
    }
    @Override public final void die(DamageSource source) {
        // LivingEntity.handleEntityEvent also calls die on the client for packet 3.
        // Keep vanilla's local death state/animation, but settlement and species
        // hooks (including ServerLevel particle broadcasts) belong to the server.
        if (level().isClientSide) {
            super.die(source);
            return;
        }
        if (initialized() && monsterState.life() != MonsterState.Life.ALIVE) return;
        super.die(source);
        if (dead) {
            acceptFinalDeath();
            MonsterFactory.removePopulation(this);
            if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                com.stardew.craft.combat.equipment.RingEffectHandler.onNativeMonsterKilled(player, this);
            }
            onFinalDeath(source);
        }
    }
    /** Server-only hook after an accepted death; clients use the vanilla death event. */
    protected void onFinalDeath(DamageSource source) {}
    public void onAcceptedContact(net.minecraft.server.level.ServerPlayer player) {}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ACTION, 0); builder.define(ACTION_START, 0L); builder.define(ACTION_SEQUENCE, 0);
    }
    public final int phase() { return entityData.get(ACTION); }
    public final int actionSequence() { return entityData.get(ACTION_SEQUENCE); }
    public final double animationTime(float partialTick) {
        return Math.max(0, level().getGameTime() - entityData.get(ACTION_START) + partialTick) / 20.0;
    }
    protected final void startAction(int action, boolean restart) {
        if (level().isClientSide || (!restart && phase() == action)) return;
        entityData.set(ACTION, action); entityData.set(ACTION_START, level().getGameTime());
        entityData.set(ACTION_SEQUENCE, actionSequence() + 1); phaseTicks = 0;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (initialized()) tag.put("MonsterRuntime", monsterState.save());
        tag.putInt("SourceHitRecoveryTicks", sourceHitRecoveryTicks);
        tag.putInt("MonsterAction", phase()); tag.putInt("MonsterActionTicks", phaseTicks);
        tag.putInt("MonsterActionSequence", actionSequence());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("MonsterRuntime")) {
            monsterState = MonsterState.load(tag.getCompound("MonsterRuntime"));
            if (!monsterState.identity().equals(definitionId())) throw new IllegalArgumentException("Monster identity/type mismatch");
            replaceCombatStats(monsterState.stats());
        }
        sourceHitRecoveryTicks = Math.max(0, tag.getInt("SourceHitRecoveryTicks"));
        phaseTicks = Math.max(0, tag.getInt("MonsterActionTicks"));
        entityData.set(ACTION, tag.getInt("MonsterAction"));
        entityData.set(ACTION_START, level().getGameTime() - phaseTicks);
        entityData.set(ACTION_SEQUENCE, tag.getInt("MonsterActionSequence"));
    }
}
