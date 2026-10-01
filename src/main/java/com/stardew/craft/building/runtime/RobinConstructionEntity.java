package com.stardew.craft.building.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** A construction-order projection. It never enters NPC schedules, shops or social relationships. */
public final class RobinConstructionEntity extends Entity {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> HIGH = SynchedEntityData.defineId(RobinConstructionEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    public void setHigh(boolean high) { entityData.set(HIGH, high); }
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> WORKING = SynchedEntityData.defineId(RobinConstructionEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private int swingTick, pauseTicks;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SWING = SynchedEntityData.defineId(RobinConstructionEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    public boolean high() { return entityData.get(HIGH); }
    public double animationSeconds(float partialTick) {
        return (entityData.get(SWING) + (entityData.get(WORKING) ? partialTick : 0)) / 20.0;
    }
    private UUID buildingId;
    public RobinConstructionEntity(EntityType<? extends RobinConstructionEntity> type, Level level) {
        super(type, level); noPhysics = true;
    }
    public void bind(UUID buildingId) { this.buildingId = buildingId; }
    public UUID buildingId() { return buildingId; }
    @Override protected void defineSynchedData() { this.entityData.define(HIGH, false); this.entityData.define(WORKING,true); this.entityData.define(SWING,0); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { buildingId = tag.hasUUID("Building") ? tag.getUUID("Building") : null; }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { if (buildingId != null) tag.putUUID("Building", buildingId); }
    @Override public net.minecraft.world.phys.Vec3 getLightProbePosition(float partialTick) {
        // The visible head is 24–32 model pixels high; use its actual exposed space.
        return getPosition(partialTick).add(0,1.75,0);
    }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            var record = buildingId == null ? null : BuildingWorldData.get(level.getServer()).find(buildingId);
            if (record == null || record.phase() != BuildingRecord.Phase.CONSTRUCTING && record.phase() != BuildingRecord.Phase.UPGRADING) { discard(); return; }
            var clock=com.stardew.craft.time.StardewTimeManager.get();
            boolean holiday=com.stardew.craft.festival.FestivalService.isFestivalDay(clock.getCurrentDay(),clock.getCurrentSeason())
                    || clock.getCurrentYear()==1 && "GreenRain".equals(com.stardew.craft.weather.WeatherManager.getCurrentWeather(level));
            if(holiday || pauseTicks>0) {entityData.set(WORKING,false);entityData.set(SWING,swingTick);if(!holiday)pauseTicks--;return;}
            entityData.set(WORKING,true);
            entityData.set(SWING,swingTick);
            if (swingTick == 16 || swingTick == 37) {
                level.playSound(null, blockPosition(), (random.nextFloat() < 0.1f ? com.stardew.craft.sound.ModSounds.CLANK.get() : com.stardew.craft.sound.ModSounds.AXCHOP.get()), SoundSource.BLOCKS, 0.55f, 0.95f);
            }
            if(++swingTick>=64) {swingTick=0;pauseTicks=random.nextFloat()<.4f?6:random.nextFloat()<.25f?10+random.nextInt(70):20+random.nextInt(60);}
        }
    }
}
