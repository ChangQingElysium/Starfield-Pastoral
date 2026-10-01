package com.stardew.craft.animal.runtime;

import com.stardew.craft.model.AnimatedModel;
import com.stardew.craft.model.ModelAnimation;
import com.stardew.craft.building.runtime.BuildingWorldData;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Separate projection: no vanilla chicken egg timer, breeding, old AI or old daily reducer. */
public final class LivestockEntity extends PathfinderMob implements AnimatedModel {
    private static final EntityDataAccessor<Boolean> BABY = SynchedEntityData.defineId(LivestockEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> EATING = SynchedEntityData.defineId(LivestockEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> SPECIES = SynchedEntityData.defineId(LivestockEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SHEARED = SynchedEntityData.defineId(LivestockEntity.class, EntityDataSerializers.BOOLEAN);
    public LivestockEntity(EntityType<? extends LivestockEntity> type, Level level) { super(type, level); setPersistenceRequired(); }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.MOVEMENT_SPEED, .176).add(com.stardew.craft.port.PortAttributes.STEP_HEIGHT.get(), 1).add(Attributes.FOLLOW_RANGE, 16);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); this.entityData.define(BABY, true); this.entityData.define(EATING, false); this.entityData.define(SPECIES, "white_chicken"); this.entityData.define(SHEARED, false); }
    @Override public boolean isBaby() { return entityData.get(BABY); }
    public void refresh(LivestockRecord record) { entityData.set(BABY, record.baby()); entityData.set(SPECIES, record.species().id()); entityData.set(SHEARED, record.produce().isEmpty()); refreshDimensions(); setCustomName(Component.literal(record.name())); }
    public LivestockSpecies species() { return LivestockSpecies.parse(entityData.get(SPECIES)); }
    public com.stardew.craft.entity.animal.CoopAnimalVariant asset() { return species() == LivestockSpecies.SHEEP && !isBaby() && entityData.get(SHEARED) ? com.stardew.craft.entity.animal.CoopAnimalVariant.SHEARED_SHEEP : species().asset(); }
    // PORT(1.20.1): 1.21 LivingEntity#getDimensions is sleeping ? SLEEPING_DIMENSIONS : getDefaultDimensions(pose)
    // (times the 1.20.5+ scale attribute, always 1 here); 1.20.1 only has getDimensions.
    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return pose == net.minecraft.world.entity.Pose.SLEEPING ? SLEEPING_DIMENSIONS : getDefaultDimensions(pose);
    }
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return species().dimensions(isBaby());
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) { super.onSyncedDataUpdated(accessor); if (accessor.equals(BABY) || accessor.equals(SPECIES)) refreshDimensions(); }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) { if (player.isShiftKeyDown()) LivestockManagement.open(serverPlayer,getUUID().toString()); else if (!LivestockProducts.interact(serverPlayer, this)) LivestockService.pet(serverPlayer, this); }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    private final LivestockBrain brain = new LivestockBrain(this);
    public void setEating(boolean value){entityData.set(EATING,value);}
    @Override public void tick(){super.tick();brain.tick();}
    @Override
    public ModelAnimation modelAnimation(boolean moving, float partialTick) {
        return ModelAnimation.loop(entityData.get(EATING) ? "eat" : moving ? "walk" : "idle");
    }

}
