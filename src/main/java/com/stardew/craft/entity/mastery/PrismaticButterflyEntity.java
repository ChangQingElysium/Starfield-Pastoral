package com.stardew.craft.entity.mastery;

import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("null")
public class PrismaticButterflyEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(PrismaticButterflyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> CAPTURING = SynchedEntityData.defineId(PrismaticButterflyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> VISUAL_ONLY = SynchedEntityData.defineId(PrismaticButterflyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> POWDER_VISUAL = SynchedEntityData.defineId(PrismaticButterflyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> BASE_FRAME = SynchedEntityData.defineId(PrismaticButterflyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BURST_TICKS = SynchedEntityData.defineId(PrismaticButterflyEntity.class, EntityDataSerializers.INT);
    private static final double CAPTURE_DISTANCE = 2.0D;
    private static final int CAPTURE_TICKS = 40;
    private static final int VISUAL_LIFETIME_TICKS = 180;
    private static final int POWDER_LIFETIME_TICKS = 60;

    private Vec3 motion = Vec3.ZERO;
    private float motionMultiplier = 1.0f;
    private int flapTimer;
    private int captureTicks;
    private boolean rewarded;
    private boolean debugSpawn;

    public PrismaticButterflyEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setGlowingTag(true);
    }

    public PrismaticButterflyEntity(Level level, ServerPlayer owner, double x, double y, double z) {
        this(ModEntities.PRISMATIC_BUTTERFLY.get(), level);
        setOwner(owner.getUUID());
        setPos(x, y, z);
        this.motion = initialMotion();
        this.flapTimer = 1 + random.nextInt(80);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER, Optional.empty());
        this.entityData.define(CAPTURING, false);
        this.entityData.define(VISUAL_ONLY, false);
        this.entityData.define(POWDER_VISUAL, false);
        this.entityData.define(BASE_FRAME, 394);
        this.entityData.define(BURST_TICKS, 0);
    }

    public static PrismaticButterflyEntity createBlessingVisual(Level level, double x, double y, double z) {
        PrismaticButterflyEntity entity = new PrismaticButterflyEntity(ModEntities.PRISMATIC_BUTTERFLY.get(), level);
        entity.entityData.set(VISUAL_ONLY, true);
        entity.entityData.set(BASE_FRAME, 163);
        entity.setGlowingTag(false);
        entity.setPos(x, y, z);
        entity.motion = entity.initialMotion();
        entity.flapTimer = 1 + entity.random.nextInt(40);
        return entity;
    }

    public static PrismaticButterflyEntity createPowderVisual(Level level, double x, double y, double z) {
        PrismaticButterflyEntity entity = new PrismaticButterflyEntity(ModEntities.PRISMATIC_BUTTERFLY.get(), level);
        entity.entityData.set(VISUAL_ONLY, true);
        entity.entityData.set(POWDER_VISUAL, true);
        entity.setGlowingTag(false);
        entity.setPos(x, y, z);
        entity.motion = new Vec3((entity.random.nextDouble() - 0.5D) * 0.035D,
                0.035D + entity.random.nextDouble() * 0.025D,
                (entity.random.nextDouble() - 0.5D) * 0.035D);
        return entity;
    }

    public Optional<UUID> getOwnerUuid() {
        return entityData.get(OWNER);
    }

    public boolean isOwnedBy(UUID uuid) {
        return getOwnerUuid().map(uuid::equals).orElse(false);
    }

    public boolean isCapturing() {
        return entityData.get(CAPTURING);
    }

    public boolean isDebugSpawn() {
        return debugSpawn;
    }

    public boolean isVisualOnly() {
        return entityData.get(VISUAL_ONLY);
    }

    public boolean isPowderVisual() {
        return entityData.get(POWDER_VISUAL);
    }

    public int visualLifetimeTicks() {
        return isPowderVisual() ? POWDER_LIFETIME_TICKS : VISUAL_LIFETIME_TICKS;
    }

    public int getBaseFrame() {
        return entityData.get(BASE_FRAME);
    }

    public int getBurstTicks() {
        return entityData.get(BURST_TICKS);
    }

    public void setDebugSpawn(boolean debugSpawn) {
        this.debugSpawn = debugSpawn;
    }

    private void setOwner(UUID owner) {
        entityData.set(OWNER, Optional.of(owner));
    }

    private void setCapturing(boolean capturing) {
        entityData.set(CAPTURING, capturing);
    }

    @Override
    public void tick() {
        super.tick();
        this.setGlowingTag(!isVisualOnly());

        if (level().isClientSide) {
            return;
        }

        if (isVisualOnly()) {
            tickVisualButterfly();
            return;
        }

        if (getBurstTicks() > 0) {
            entityData.set(BURST_TICKS, getBurstTicks() - 1);
            if (getBurstTicks() <= 0) {
                discard();
            }
            return;
        }

        ServerPlayer owner = getOwnerPlayer();
        if (owner == null || (!debugSpawn && !owner.hasEffect(ModMobEffects.STATUE_OF_BLESSINGS_6.get()))) {
            discard();
            return;
        }

        if (isCapturing()) {
            tickCapture(owner);
        } else {
            tickFlight(owner);
        }
    }

    private void tickFlight(ServerPlayer owner) {
        flapTimer--;
        if (flapTimer <= 0) {
            motionMultiplier = 1.0f;
            motion = motion.add((random.nextDouble() * 1.6D - 0.8D) * 0.05D, -(random.nextDouble() + 0.25D) * 0.075D, (random.nextDouble() * 1.6D - 0.8D) * 0.05D);
            double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            if (horizontal > 0.075D) {
                double scale = 0.075D / horizontal;
                motion = new Vec3(motion.x * scale, motion.y, motion.z * scale);
            }
            if (Math.abs(motion.y) > 0.15D) {
                motion = new Vec3(motion.x, 0.15D * Math.signum(motion.y), motion.z);
            }
            flapTimer = 200 + random.nextInt(11) - 5;
        }

        setDeltaMovement(motion.scale(motionMultiplier));
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        motion = motion.add(0.0D, 0.00025D, 0.0D);
        motionMultiplier = Math.max(0.0f, motionMultiplier - 0.01f);

        if (distanceToSqr(owner) < CAPTURE_DISTANCE * CAPTURE_DISTANCE) {
            setCapturing(true);
            captureTicks = CAPTURE_TICKS;
        }
    }

    private void tickCapture(ServerPlayer owner) {
        Vec3 target = owner.position().add(0.0D, 1.45D, 0.0D);
        Vec3 toPlayer = target.subtract(position()).scale(0.1D);
        double swirl = captureTicks / 150.0D;
        double angle = tickCount / 5.0D;
        Vec3 wave = new Vec3(Math.cos(angle) * swirl, Math.sin(angle) * swirl * 0.5D, Math.sin(angle) * swirl);
        setDeltaMovement(toPlayer.add(wave));
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        captureTicks--;

        if (captureTicks <= 0 && !rewarded) {
            rewarded = true;
            reward(owner);
            setCapturing(false);
            entityData.set(BURST_TICKS, 20);
        }
    }

    private void tickVisualButterfly() {
        if (isPowderVisual()) {
            setDeltaMovement(motion);
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        } else if (getBaseFrame() >= 0) {
            tickFlightMotion();
        }
        if (tickCount >= visualLifetimeTicks()) {
            discard();
        }
    }

    private void tickFlightMotion() {
        flapTimer--;
        if (flapTimer <= 0) {
            motionMultiplier = 1.0f;
            motion = motion.add((random.nextDouble() * 1.6D - 0.8D) * 0.04D, (random.nextDouble() * 0.9D + 0.1D) * 0.035D, (random.nextDouble() * 1.6D - 0.8D) * 0.04D);
            double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            if (horizontal > 0.065D) {
                double scale = 0.065D / horizontal;
                motion = new Vec3(motion.x * scale, motion.y, motion.z * scale);
            }
            if (Math.abs(motion.y) > 0.12D) {
                motion = new Vec3(motion.x, 0.12D * Math.signum(motion.y), motion.z);
            }
            flapTimer = 20 + random.nextInt(30);
        }
        setDeltaMovement(motion.scale(motionMultiplier));
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        motion = motion.add(0.0D, -0.0002D, 0.0D);
        motionMultiplier = Math.max(0.0f, motionMultiplier - 0.01f);
    }

    private void reward(ServerPlayer owner) {
        owner.removeEffect(ModMobEffects.STATUE_OF_BLESSINGS_6.get());
        owner.playNotifySound(ModSounds.YOBA.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

        PlayerStardewData data = PlayerStardewDataAPI.getData(owner);
        double dailyLuck = PlayerStardewDataAPI.getDailyLuck(owner);
        long seed = owner.getUUID().getLeastSignificantBits() ^ ((long) com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay() * 0x9E3779B97F4A7C15L);
        java.util.Random rewardRandom = new java.util.Random(seed);
        if (rewardRandom.nextDouble() < 0.05000000074505806D + dailyLuck) {
            ItemStack shard = new ItemStack(ModItems.PRISMATIC_SHARD.get());
            owner.drop(shard, false);
        }

        int money = Math.max(100, Math.min(50000, (int) (data.getTotalMoneyEarned() * 0.005f)));
        PlayerStardewDataAPI.addMoney(owner, money);
    }

    private ServerPlayer getOwnerPlayer() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return getOwnerUuid()
            .map(serverLevel::getPlayerByUUID)
            .filter(ServerPlayer.class::isInstance)
            .map(ServerPlayer.class::cast)
            .orElse(null);
    }

    private Vec3 initialMotion() {
        double x = (random.nextDouble() + 0.25D) * 0.075D * (random.nextBoolean() ? 1D : -1D);
        double y = (random.nextDouble() + 0.5D) * 0.075D * (random.nextBoolean() ? 1D : -1D);
        double z = (random.nextDouble() + 0.25D) * 0.075D * (random.nextBoolean() ? 1D : -1D);
        return new Vec3(x, y, z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            setOwner(tag.getUUID("Owner"));
        }
        captureTicks = tag.getInt("CaptureTicks");
        rewarded = tag.getBoolean("Rewarded");
        debugSpawn = tag.getBoolean("DebugSpawn");
        entityData.set(VISUAL_ONLY, tag.getBoolean("VisualOnly"));
        entityData.set(POWDER_VISUAL, tag.getBoolean("PowderVisual"));
        entityData.set(BASE_FRAME, tag.contains("BaseFrame") ? tag.getInt("BaseFrame") : 394);
        entityData.set(BURST_TICKS, tag.getInt("BurstTicks"));
        setCapturing(tag.getBoolean("Capturing"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        getOwnerUuid().ifPresent(uuid -> tag.putUUID("Owner", uuid));
        tag.putInt("CaptureTicks", captureTicks);
        tag.putBoolean("Rewarded", rewarded);
        tag.putBoolean("DebugSpawn", debugSpawn);
        tag.putBoolean("VisualOnly", isVisualOnly());
        tag.putBoolean("PowderVisual", isPowderVisual());
        tag.putInt("BaseFrame", getBaseFrame());
        tag.putInt("BurstTicks", getBurstTicks());
        tag.putBoolean("Capturing", isCapturing());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
