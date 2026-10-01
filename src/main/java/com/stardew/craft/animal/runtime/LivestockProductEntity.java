package com.stardew.craft.animal.runtime;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.building.runtime.BuildingService;
import com.stardew.craft.building.runtime.BuildingWorldData;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Ledger-owned floor product. No merging, hopper extraction or five-minute despawn. */
public final class LivestockProductEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(LivestockProductEntity.class, EntityDataSerializers.ITEM_STACK);
    private BlockPos homeAnchor;
    public LivestockProductEntity(EntityType<? extends LivestockProductEntity> type, Level level) {
        super(type, level); setNoGravity(true); noPhysics = true;
    }
    @Override protected void defineSynchedData() { this.entityData.define(ITEM, ItemStack.EMPTY); }
    public ItemStack getItem() { return entityData.get(ITEM); }
    public void setItem(ItemStack stack) { entityData.set(ITEM, stack.copy()); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    public static ItemStack stack(LivestockWorldData.Product egg, ServerLevel level) {
        if(!egg.stackData().isEmpty())return PortItemStacks.parseOptional(level.registryAccess(),egg.stackData());
        return LivestockProducts.stack(egg.item(), egg.count(), egg.quality());
    }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public void tick() {
        super.tick(); setDeltaMovement(0, 0, 0);
        if (!(level() instanceof ServerLevel level)) return;
        var egg = LivestockWorldData.get(level.getServer()).egg(getUUID());
        var home = egg == null ? null : BuildingWorldData.get(level.getServer()).find(egg.home());
        if (home == null || homeAnchor != null && !homeAnchor.equals(home.anchor())) { discard(); return; }
        homeAnchor = home.anchor();
    }
    @Override public void playerTouch(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || isRemoved()) return;
        var data = LivestockWorldData.get(serverPlayer.serverLevel().getServer());
        var egg = data.egg(getUUID()); if (egg == null || data.pending() != null) return;
        var home = BuildingWorldData.get(serverPlayer.serverLevel().getServer()).find(egg.home());
        if (home == null || !BuildingService.canManage(serverPlayer, home)) return;
        var stack = stack(egg,serverPlayer.serverLevel());
        if (stack.isEmpty()) return;
        boolean truffle = (egg.item().equals("truffle") || egg.item().equals("stardewcraft:truffle"));
        var random = com.stardew.craft.util.StardewDeterministicRandom.create(egg.id().getLeastSignificantBits(), com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay(), 0);
        if (truffle) {
            int foraging = com.stardew.craft.player.PlayerStardewDataAPI.getSkillLevel(serverPlayer, com.stardew.craft.player.SkillType.FORAGING);
            int quality = com.stardew.craft.player.PlayerStardewDataAPI.hasProfession(serverPlayer, com.stardew.craft.player.ProfessionType.BOTANIST) ? 4
                    : random.nextDouble() < foraging / 30f ? 2 : random.nextDouble() < foraging / 15f ? 1 : 0;
            stack = LivestockProducts.stack("truffle", 1, quality);
        }
        if (!LivestockProducts.fits(serverPlayer, stack)) return;
        var bonus = stack.copy(); player.getInventory().add(stack);
        data.collect(egg.id());
        int count = egg.count();
        if (truffle && com.stardew.craft.player.ForagingProfessionRules.hasGathererBonus(serverPlayer, random.nextDouble())
                && LivestockProducts.fits(serverPlayer, bonus)) { player.getInventory().add(bonus); count++; }
        com.stardew.craft.player.PlayerStardewDataAPI.addExperience(serverPlayer, truffle ? com.stardew.craft.player.SkillType.FORAGING : com.stardew.craft.player.SkillType.FARMING, truffle ? 7 * count : 5 * count);
        if (!truffle) com.stardew.craft.player.PlayerStardewDataAPI.recordAnimalProductsCollected(serverPlayer.getUUID(), count);
        level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                net.minecraft.sounds.SoundSource.PLAYERS, .2f, 1.1f);
        discard();
    }
}
