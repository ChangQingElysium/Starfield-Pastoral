package com.stardew.craft.entity.monster;

import com.stardew.craft.combat.MonsterStats;
import com.stardew.craft.monster.*;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.combat.equipment.EquipmentResolver;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Grounded SDV movement with independent visual hops, never vanilla Slime splitting/AI. */
@SuppressWarnings("null")
public final class GreenSlimeEntity extends StardewMonsterEntity {
    public static final int REST = 0, WALK = 1, CHARGE = 2, DASH = 3, LAND = 4;
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(GreenSlimeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANTENNA = SynchedEntityData.defineId(GreenSlimeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FROST_RUSH = SynchedEntityData.defineId(GreenSlimeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> FROST_RUSH_START = SynchedEntityData.defineId(GreenSlimeEntity.class, EntityDataSerializers.LONG);
    private ResourceLocation offspringDefinition;
    private int offspringColor;
    private boolean male, marked, focused, leftDrift;
    private int ageTicks, residualAnimation, recoveryTicks;
    private double slideX, slideZ;
    private int slipperiness = 3;
    private int wanderDirection = 4;
    private int specialNumber;
    private boolean firstGeneration = true;
    public int specialNumber() { return specialNumber; }
    public boolean firstGeneration() { return firstGeneration; }
    private final SlimeBreeding breeding = new SlimeBreeding(this);
    private static final EntityDataAccessor<Float> GROWTH = SynchedEntityData.defineId(GreenSlimeEntity.class, EntityDataSerializers.FLOAT);

    public GreenSlimeEntity(EntityType<? extends GreenSlimeEntity> type, Level level) {
        super(type, level);
        addTag("sd_mob_slime");
        xpReward = 0; // CombatExperienceRules owns SDV combat XP; no duplicate vanilla orbs.
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1)
                .add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.MOVEMENT_SPEED, .25)
                .add(Attributes.FOLLOW_RANGE, 64).add(Attributes.STEP_HEIGHT, .5);
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(COLOR, 0x55E01F);
        this.entityData.define(ANTENNA, 0);
        this.entityData.define(GROWTH, 1F);
        this.entityData.define(FROST_RUSH, false);
        this.entityData.define(FROST_RUSH_START, 0L);
    }
    @Override protected void registerGoals() {}

    // PORT(1.20.1): 1.21 LivingEntity#getDimensions is sleeping ? SLEEPING_DIMENSIONS : getDefaultDimensions(pose)
    // (times the 1.20.5+ scale attribute, always 1 here); 1.20.1 only has getDimensions.
    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return pose == net.minecraft.world.entity.Pose.SLEEPING ? SLEEPING_DIMENSIONS : getDefaultDimensions(pose);
    }
    public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        return net.minecraft.world.entity.EntityDimensions.scalable(GreenSlimeRules.collisionWidth(antenna()) * growth(),
                GreenSlimeRules.collisionHeight(antenna()) * growth() + GreenSlimeRules.COLLISION_TOP_MARGIN);
    }
    @Override protected net.minecraft.world.phys.AABB makeBoundingBox() {
        // Entity's constructor positions us before LivingEntity creates its attributes.
        if (getAttributes() == null) return super.makeBoundingBox();
        return collisionBoxAtYaw(getYRot());
    }
    private net.minecraft.world.phys.AABB collisionBoxAtYaw(float yaw) {
        return GreenSlimeRules.collisionBox(getX(), getY(), getZ(), yaw, growth() * getScale(), antenna());
    }
    @Override public void setYRot(float yaw) {
        // Rotating a rectangular body also changes its AABB. Never expand it into a wall
        // after movement has already resolved collision using the previous orientation.
        if (getAttributes() != null && Float.isFinite(yaw)) {
            var box = collisionBoxAtYaw(yaw);
            if (!level().isClientSide && isAddedToLevel() && !level().noCollision(this, box)) return;
            super.setYRot(yaw);
            setBoundingBox(box);
        } else super.setYRot(yaw);
    }
    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(growth()*getScale());
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (GROWTH.equals(key) || ANTENNA.equals(key)) refreshDimensions();
    }

    public SlimeVariant variant() {
        return getType() == com.stardew.craft.entity.ModEntities.FROST_JELLY.get() ? SlimeVariant.FROST
                : getType() == com.stardew.craft.entity.ModEntities.SLUDGE.get() ? SlimeVariant.SLUDGE : SlimeVariant.GREEN;
    }
    public boolean frostRush() { return entityData.get(FROST_RUSH); }
    public float frostGlow(float partialTick) {
        if (!frostRush()) return 0;
        double step = ((level().getGameTime() - entityData.get(FROST_RUSH_START) + partialTick) * 3) % 14;
        return (float) Math.clamp(step <= 7 ? step * .15 : 1 - (step - 7) * .15, 0, 1);
    }
    private double walkPixels() { return GreenSlimeRules.WALK_PIXELS + (frostRush() ? 2 : 0); }
    @Override protected ResourceLocation definitionId() {
        return offspringDefinition != null ? offspringDefinition : new ResourceLocation("stardewcraft", variant().id());
    }
    void initializeOffspring(MonsterSpawnContext context, int color) {
        offspringColor = color;
        offspringDefinition = new ResourceLocation("stardewcraft", SlimeVariant.offspringStats(color).id());
        initialize(context);
    }
    public void initialize(int floor) {
        if (!initialized() && level() instanceof ServerLevel server) {
            initialize(MonsterSpawnContext.capture(server, MonsterSpawnContext.Source.WORLD, floor));
        }
    }
    @Override protected void configureSpawn(MonsterDefinition definition, MonsterSpawnContext context) {
        int floor = context.floor();
        specialNumber = random.nextInt(100);
        // parseMonsterInfo: base-drop rolls precede progression; source MaxHealth stays unchanged.
        var resolved = MonsterStatResolver.base(definition, context, random);
        int health = resolved.initialHealth(), damage = (int) resolved.combat().getDamage();
        int resilience = (int) resolved.combat().getResilience();
        float miss = resolved.combat().getMissChance();
        male = random.nextDouble() < .49;
        leftDrift = random.nextBoolean();
        breeding.initialize();
        boolean offspring = context.source() == MonsterSpawnContext.Source.OFFSPRING;
        entityData.set(COLOR, offspring ? offspringColor : variant().rollColor(random, floor));
        marked = !offspring && !variant().skull(floor) && random.nextDouble() < .01 && GreenSlimeRules.canBeMarked(floor);
        if (marked) {
            entityData.set(COLOR, variant().markedColor());
            addTag(com.stardew.craft.mining.OrdinaryMineSpecialLoot.TAG);
        }
        if (variant() == SlimeVariant.FROST) addTag("sd_tier_2");
        if (variant() == SlimeVariant.SLUDGE) addTag("sd_tier_3");
        if (!offspring && variant().skull(floor)) {
            health *= 2;
            addTag("sd_tier_skull");
            while (random.nextDouble() < .08) monsterState().addBornDrop(new ResourceLocation("stardewcraft:iridium_ore"));
            if (random.nextDouble() < .009) monsterState().addBornDrop(new ResourceLocation("stardewcraft:iridium_bar"));
        }
        setInitialHealth(GreenSlimeRules.health(health, marked, male));
        replaceCombatStats(MonsterStats.builder().damage(GreenSlimeRules.damage(damage, marked, male))
                .resilience(resilience).missChance(miss).experience(definition.experience()).build());
        syncAntenna();
    }
    void setSourceStats(int health, int damage, int resilience) {
        setInitialHealth(health);
        var previous = monsterState().stats();
        replaceCombatStats(MonsterStats.builder().damage(damage).resilience(resilience)
                .missChance(previous.getMissChance()).experience(previous.getExperience()).build());
    }
    boolean male() { return male; }
    public int antenna() { return entityData.get(ANTENNA); }
    void syncAntenna() { entityData.set(ANTENNA, GreenSlimeRules.antenna(male, marked, breeding.adult())); }
    SlimeBreeding breeding() { return breeding; }
    void growth(float value) { entityData.set(GROWTH, value); }
    public float growth() { return entityData.get(GROWTH); }
    /** BigSlime creates ordinary adults at a smaller permanent scale, not breeding babies. */
    void fromBigSlime(float scale,double x,double z) {
        breeding.adultScale(scale);slideX=x;slideZ=z;
    }
    void startChild() {
        marked = focused = false;
        firstGeneration = false; specialNumber = 0;
        removeTag(com.stardew.craft.mining.OrdinaryMineSpecialLoot.TAG);
        breeding.child();
        syncAntenna();
        slipperiness = 8;
    }
    public int sourceFloor() { return initialized() ? monsterState().context().floor() : 1; }
    public int color() { return entityData.get(COLOR); }
    void phase(int value) { startAction(value, false); }
    @Override protected void customServerAiStep() {
        initialize(variant() == SlimeVariant.FROST ? 40 : variant() == SlimeVariant.SLUDGE ? 80 : 1);
        ageTicks++;
        if (recoveryTicks > 0) recoveryTicks--;
        var nearest = level().getNearestPlayer(getX(), getY(), getZ(), focused ? 64 : 4,
                p -> p instanceof net.minecraft.world.entity.player.Player player && player.isAlive()
                        && !player.isCreative() && !player.isSpectator() && !player.hasEffect(ModMobEffects.AVOID_MONSTERS.get())
                        && hasLineOfSight(player));
        if (nearest != null && (!nearest.isAlive() || nearest.isCreative() || nearest.isSpectator()
                || nearest.hasEffect(ModMobEffects.AVOID_MONSTERS.get()))) nearest = null;
        setTarget(nearest);
        var target = getTarget(); // Target events (including garlic oil) remain authoritative.
        if (phase() == CHARGE) {
            if (target == null) phase(REST);
            else if (phaseTicks >= GreenSlimeRules.CHARGE_TICKS) {
                Vec3 toward = target.position().subtract(position()).multiply(1, 0, 1).normalize();
                slideX = (int) (toward.x * (30 + random.nextInt(40)) / 2);
                slideZ = (int) (toward.z * (30 + random.nextInt(40)) / 2);
                slipperiness = 10;
                phase(DASH);
                playSound(ModSounds.MONSTER_SLIME_JUMP.get(), 1, 1);
            }
        }
        boolean mating = breeding.tick();
        if (mating) residualAnimation = 10;
        double beforeX = getX(), beforeZ = getZ();
        for (int step = 0; step < GreenSlimeRules.SOURCE_STEPS_PER_TICK; step++) {
            if (slideX != 0 || slideZ != 0) {
                double x = getX(), z = getZ();
                move(MoverType.SELF, new Vec3(slideX / 64, 0, slideZ / 64));
                boolean blocked = Math.abs(getX() - x - slideX / 64) > .00001
                        || Math.abs(getZ() - z - slideZ / 64) > .00001;
                slideX = GreenSlimeRules.decay(slideX, slipperiness, blocked);
                slideZ = GreenSlimeRules.decay(slideZ, slipperiness, blocked);
            } else if (!mating && target == null && recoveryTicks == 0 && phase() <= WALK) {
                // Monster.defaultMovementBehavior: jitteriness .01 * 1.8 per source frame,
                // four cardinal directions and two stop choices. MC adds ledge safety.
                if (random.nextDouble() < .018) wanderDirection = random.nextInt(6);
                if (wanderDirection < 4) {
                    double speed = walkPixels() / 64;
                    double dx = wanderDirection == 1 ? speed : wanderDirection == 3 ? -speed : 0;
                    double dz = wanderDirection == 2 ? speed : wanderDirection == 0 ? -speed : 0;
                    var wanderStep = new Vec3(dx, 0, dz);
                    var ground = net.minecraft.core.BlockPos.containing(getX() + dx, getY() - .05, getZ() + dz);
                    if (level().noCollision(this, getBoundingBox().move(wanderStep))
                            && !level().getBlockState(ground).getCollisionShape(level(), ground).isEmpty()) {
                        move(MoverType.SELF, wanderStep);
                        setYRot((float) Math.toDegrees(Math.atan2(-dx, dz))); yBodyRot = getYRot();
                    } else wanderDirection = 4;
                }
            } else if (!mating && target != null && phase() <= WALK && recoveryTicks == 0) {
                Vec3 delta = target.position().subtract(position());
                // Four-way pursuit; MC collision resolves actual walls, steps and uneven terrain.
                boolean horizontal = Math.abs(delta.x) > Math.abs(delta.z);
                double speed = walkPixels() / 64 * getAttributeValue(Attributes.MOVEMENT_SPEED) / .25;
                double dx = horizontal ? Math.signum(delta.x) * speed : 0;
                double dz = horizontal ? 0 : Math.signum(delta.z) * speed;
                move(MoverType.SELF, new Vec3(dx, 0, dz));
                if (horizontalCollision) move(MoverType.SELF, new Vec3(horizontal ? 0 : Math.signum(delta.x) * speed,
                        0, horizontal ? Math.signum(delta.z) * speed : 0));
                setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
                yBodyRot = getYRot();
                if (!focused && random.nextDouble() < .1) {
                    double drift = (leftDrift ? -1 : 1) * speed;
                    move(MoverType.SELF, new Vec3(horizontal ? 0 : drift, 0, horizontal ? drift : 0));
                    if (random.nextDouble() < .08) leftDrift = !leftDrift;
                } else if (GreenSlimeRules.canCharge(ageTicks, focused) && random.nextDouble() < .01) {
                    boolean rush = variant().rushInsteadOfJump(variant() == SlimeVariant.FROST ? random.nextDouble() : 1);
                    if (rush && !frostRush()) entityData.set(FROST_RUSH_START, level().getGameTime());
                    entityData.set(FROST_RUSH, rush);
                    if (!rush) phase(CHARGE);
                }
            }
        }
        if (phase() == DASH && Math.abs(slideX) + Math.abs(slideZ) < 1) phase(LAND);
        if (phase() == LAND && phaseTicks >= 7) phase(REST);
        if (phase() <= WALK) {
            if (Math.abs(getX() - beforeX) + Math.abs(getZ() - beforeZ) > .0001) residualAnimation = 10;
            else if (residualAnimation > 0) residualAnimation--;
            phase(residualAnimation > 0 ? WALK : REST);
        }
        if (target instanceof ServerPlayer player && getBoundingBox().intersects(player.getBoundingBox())
                && !EquipmentResolver.getMergedStats(player).hasSlimeCharmer()) {
            // The common player boundary gates contact effects before this hit can trigger Yoba.
            var attack = MonsterDamageSource.contact(this);
            player.hurt(attack, attack.baseDamage());
        }
    }
    @Override public void onAcceptedContact(ServerPlayer player) { SlimeContactEffects.onAcceptedContact(this, player); }
    @Override public boolean hurt(DamageSource source, float amount) {
        float before = getHealth();
        boolean accepted = super.hurt(source, amount);
        if (getHealth() < before && !level().isClientSide && isAlive()) {
            phase(REST);
            slideX = slideZ = 0;
            slipperiness = 3;
            recoveryTicks = 2;
            if (male && !focused && random.nextDouble() < .025) {
                focused = true;
                int damage = (int) getAttributeValue(Attributes.ATTACK_DAMAGE);
                var stats = monsterState().stats();
                replaceCombatStats(MonsterStats.builder().damage(damage + damage / 2).resilience(stats.getResilience())
                        .missChance(stats.getMissChance()).experience(stats.getExperience()).build());
            }
            particles(5, .15F);
        }
        return accepted;
    }
    private void particles(int count, float size) {
        if (!(level() instanceof ServerLevel server)) return;
        int c = color();
        server.sendParticles(new DustParticleOptions(new Vector3f((c >> 16 & 255) / 255F,
                (c >> 8 & 255) / 255F, (c & 255) / 255F), size),
                getX(), getY() + .25, getZ(), count, .18, .1, .18, .05);
    }
    @Override protected void onFinalDeath(DamageSource source) {
        breeding.stop();
        particles(24, .7F);
    }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.MONSTER_SLIME_HIT.get(); }
    @Override protected SoundEvent getDeathSound() { return ModSounds.SLIMEDEAD.get(); }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        breeding.save(tag);
        tag.putInt("SlimeColor", color());
        tag.putBoolean("SlimeFrostRush", frostRush());
        tag.putLong("SlimeFrostRushTicks", Math.max(0, level().getGameTime() - entityData.get(FROST_RUSH_START)));
        tag.putBoolean("SlimeMale", male);
        tag.putBoolean("SlimeMarked", marked);
        tag.putBoolean("SlimeFocused", focused);
        tag.putBoolean("SlimeLeftDrift", leftDrift);
        tag.putInt("SlimeAgeTicks", ageTicks);
        tag.putInt("SlimeWanderDirection", wanderDirection);
        tag.putInt("SlimeSpecialNumber", specialNumber);
        tag.putBoolean("SlimeFirstGeneration", firstGeneration);
        tag.putInt("SlimeRecoveryTicks", recoveryTicks);
        tag.putInt("SlimeResidualAnimation", residualAnimation);
        tag.putDouble("SlimeSlideX", slideX);
        tag.putDouble("SlimeSlideZ", slideZ);
        tag.putInt("SlimeSlipperiness", slipperiness);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        breeding.load(tag);
        if (initialized()) entityData.set(COLOR, tag.getInt("SlimeColor"));
        entityData.set(FROST_RUSH, variant() == SlimeVariant.FROST && tag.getBoolean("SlimeFrostRush"));
        entityData.set(FROST_RUSH_START, level().getGameTime() - Math.max(0, tag.getLong("SlimeFrostRushTicks")));
        male = tag.getBoolean("SlimeMale"); marked = tag.getBoolean("SlimeMarked");
        syncAntenna();
        focused = tag.getBoolean("SlimeFocused"); leftDrift = tag.getBoolean("SlimeLeftDrift");
        ageTicks = tag.getInt("SlimeAgeTicks");
        specialNumber = tag.getInt("SlimeSpecialNumber");
        firstGeneration = !tag.contains("SlimeFirstGeneration") || tag.getBoolean("SlimeFirstGeneration");
        wanderDirection = tag.contains("SlimeWanderDirection") ? Math.clamp(tag.getInt("SlimeWanderDirection"), 0, 5) : 4;
        recoveryTicks = tag.getInt("SlimeRecoveryTicks");
        residualAnimation = tag.getInt("SlimeResidualAnimation");
        slideX = tag.getDouble("SlimeSlideX"); slideZ = tag.getDouble("SlimeSlideZ");
        slipperiness = tag.contains("SlimeSlipperiness") ? tag.getInt("SlimeSlipperiness") : 3;
    }
}
