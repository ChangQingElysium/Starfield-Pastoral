package com.stardew.craft.entity.minecart;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Authored mine prop. Claim state is saved on the entity, never reset by ticking or loading. */
@SuppressWarnings("null")
public final class CoalMinecartEntity extends Entity {
    private static final EntityDataAccessor<Boolean> LOADED =
            SynchedEntityData.defineId(CoalMinecartEntity.class, EntityDataSerializers.BOOLEAN);
    public static final ResourceKey<LootTable> COAL_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            new ResourceLocation(StardewCraft.MODID, "gameplay/mine_coal_cart"));

    public CoalMinecartEntity(EntityType<? extends CoalMinecartEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public boolean isLoaded() { return entityData.get(LOADED); }
    public void setLoaded(boolean loaded) { entityData.set(LOADED, loaded); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(LOADED, true); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        setLoaded(!tag.contains("Loaded") || tag.getBoolean("Loaded"));
        setYRot(Math.round(getYRot() / 90.0F) * 90.0F);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putBoolean("Loaded", isLoaded()); }
    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return true; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public void push(double x, double y, double z) { }
    @Override public void push(Entity other) { }
    @Override public void move(MoverType type, Vec3 movement) { }

    @Override
    protected AABB makeBoundingBox() {
        boolean eastWest = (Math.round(getYRot() / 90.0F) & 1) != 0;
        double x = eastWest ? 0.75D : 0.5D, z = eastWest ? 0.5D : 0.75D;
        return new AABB(getX() - x, getY(), getZ() - z, getX() + x, getY() + 1.1875D, getZ() + z);
    }

    @Override public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        setBoundingBox(makeBoundingBox());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !isLoaded()) return InteractionResult.PASS;
        if (!(level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, position())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player).withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);
        var drops = level.getServer().reloadableRegistries().getLootTable(COAL_LOOT).getRandomItems(params);
        setLoaded(false);
        com.stardew.craft.mining.OrdinaryMineRuntime.coalCacheOpened(level, blockPosition());
        drops.forEach(stack -> spawnAtLocation(stack, 1.2F));
        level.playSound(null, blockPosition(), SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8F, 0.9F);
        return InteractionResult.CONSUME;
    }
}
