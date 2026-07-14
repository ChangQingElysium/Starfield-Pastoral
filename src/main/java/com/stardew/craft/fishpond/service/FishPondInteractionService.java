package com.stardew.craft.fishpond.service;

import com.stardew.craft.blockentity.FishPondBucketBlockEntity;
import com.stardew.craft.fishpond.data.FishPondWorldData;
import com.stardew.craft.fishpond.model.FishPondRecord;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class FishPondInteractionService {
    private static final int HARVEST_BASE_EXP = 10;
    private static final float HARVEST_OUTPUT_EXP_MULTIPLIER = 0.04F;

    public enum OutputCollectResult {
        NO_POND(false),
        EMPTY(false),
        INVENTORY_FULL(false),
        COLLECTED(true);

        private final boolean changedState;

        OutputCollectResult(boolean changedState) {
            this.changedState = changedState;
        }

        public boolean changedState() {
            return changedState;
        }
    }

    public enum ItemAbsorbResult {
        IGNORED(false),
        NEED_ITEM_ACCEPTED(true),
        FISH_ACCEPTED(true),
        GOLDEN_CRACKER_ACCEPTED(true),
        WRONG_FISH(false),
        POND_FULL(false);

        private final boolean changedState;

        ItemAbsorbResult(boolean changedState) {
            this.changedState = changedState;
        }

        public boolean changedState() {
            return changedState;
        }
    }

    private FishPondInteractionService() {
    }

    public static OutputCollectResult collectOutputAtBucket(ServerLevel level, BlockPos bucketPos, ServerPlayer player) {
        FishPondWorldData worldData = FishPondWorldData.get(level);
        FishPondRecord pond = worldData.findPondByBucket(level.dimension().location().toString(), bucketPos).orElse(null);
        if (pond == null) {
            player.displayClientMessage(Component.translatable("message.stardew_craft.fish_pond_bucket.empty"), true);
            return OutputCollectResult.NO_POND;
        }
        return collectOutput(level, bucketPos, player, pond, worldData);
    }

    public static OutputCollectResult collectOutput(ServerLevel level,
                                                    BlockPos bucketPos,
                                                    ServerPlayer player,
                                                    FishPondRecord pond,
                                                    FishPondWorldData worldData) {
        ItemStack output = FishPondDailyUpdateService.createOutputStack(pond);
        if (output.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.stardew_craft.fish_pond_bucket.empty"), true);
            return OutputCollectResult.EMPTY;
        }

        if (!canFullyAddToInventory(player.getInventory(), output)) {
            player.displayClientMessage(Component.translatable("message.stardew_craft.fish_pond_bucket.inventory_full"), true);
            return OutputCollectResult.INVENTORY_FULL;
        }

        ItemStack granted = output.copy();
        if (!player.getInventory().add(granted) || !granted.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.stardew_craft.fish_pond_bucket.inventory_full"), true);
            return OutputCollectResult.INVENTORY_FULL;
        }

        pond.setOutputItemId("");
        pond.setOutputCount(0);
        worldData.markChanged();
        FishPondBucketBlockEntity.syncVisualState(level, bucketPos);

        awardHarvestExperience(player, output);
        ItemPickupHudPacket.sendTo(player, output, output.getCount(), false);
        player.displayClientMessage(Component.translatable("message.stardew_craft.fish_pond_bucket.collected"), true);
        return OutputCollectResult.COLLECTED;
    }

    public static ItemStack pullFishForFishingRod(ServerLevel level, BlockPos bobberPos) {
        if (StardewTimeManager.get().getCurrentTime() >= StardewTimeManager.PASS_OUT_TIME) {
            return ItemStack.EMPTY;
        }

        FishPondWorldData worldData = FishPondWorldData.get(level);
        FishPondRecord pond = worldData.findPondContainingWater(level.dimension().location().toString(), bobberPos).orElse(null);
        if (pond == null || pond.currentPopulation() <= 0 || pond.fishTypeId().isBlank()) {
            return ItemStack.EMPTY;
        }

        ResourceLocation fishId = ResourceLocation.tryParse(pond.fishTypeId());
        if (fishId == null || !BuiltInRegistries.ITEM.containsKey(fishId)) {
            return ItemStack.EMPTY;
        }

        pond.setCurrentPopulation(Math.max(0, pond.currentPopulation() - 1));
        pond.setWaterColor(FishPondDataService.get().resolveWaterColor(pond));
        worldData.markChanged();
        FishPondBucketBlockEntity.syncVisualState(level, pond.bucketPos());
        FishPondColorSyncService.broadcastSnapshot(level);
        return new ItemStack(BuiltInRegistries.ITEM.get(fishId));
    }

    public static ItemAbsorbResult absorbItemEntity(ServerLevel level, FishPondRecord pond, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) {
            return ItemAbsorbResult.IGNORED;
        }

        ItemAbsorbResult needResult = tryResolveNeededItem(level, pond, itemEntity, stack);
        if (needResult != ItemAbsorbResult.IGNORED) {
            return needResult;
        }

        ItemAbsorbResult crackerResult = tryApplyGoldenAnimalCracker(level, pond, itemEntity, stack);
        if (crackerResult != ItemAbsorbResult.IGNORED) {
            return crackerResult;
        }

        FishPondDataService pondData = FishPondDataService.get();
        if (pondData.resolve(stack).isEmpty()) {
            return ItemAbsorbResult.IGNORED;
        }

        String itemId = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        boolean pondEmpty = pond.empty() || pond.currentPopulation() <= 0 || pond.fishTypeId().isBlank();
        if (!pondEmpty && !itemId.equals(pond.fishTypeId())) {
            return ItemAbsorbResult.WRONG_FISH;
        }

        int maxPopulation = pond.maxPopulation() > 0 ? pond.maxPopulation() : pondData.resolveMaxPopulation(stack);
        if (pond.currentPopulation() >= maxPopulation) {
            return ItemAbsorbResult.POND_FULL;
        }

        if (pondEmpty) {
            pond.setFishTypeId(itemId);
            pond.setMaxPopulation(maxPopulation);
        } else if (pond.maxPopulation() <= 0) {
            pond.setMaxPopulation(maxPopulation);
        }

        pond.setCurrentPopulation(pond.currentPopulation() + 1);
        pond.setEmpty(false);
        pond.setWaterColor(pondData.resolveWaterColor(pond));
        FishPondWorldData.get(level).markChanged();
        FishPondBucketBlockEntity.syncVisualState(level, pond.bucketPos());
        FishPondColorSyncService.broadcastSnapshot(level);

        consumeItemEntity(level, itemEntity, stack, 1);
        return ItemAbsorbResult.FISH_ACCEPTED;
    }

    private static ItemAbsorbResult tryResolveNeededItem(ServerLevel level,
                                                         FishPondRecord pond,
                                                         ItemEntity itemEntity,
                                                         ItemStack stack) {
        if (pond.neededItemId().isBlank() || pond.neededItemCount() <= 0 || pond.hasCompletedRequest()) {
            return ItemAbsorbResult.IGNORED;
        }
        if (!FishPondQualifiedItemService.matches(pond.neededItemId(), stack)) {
            return ItemAbsorbResult.IGNORED;
        }

        int consumed = Math.min(pond.neededItemCount(), stack.getCount());
        if (consumed <= 0) {
            return ItemAbsorbResult.IGNORED;
        }

        pond.setNeededItemCount(Math.max(0, pond.neededItemCount() - consumed));
        if (pond.neededItemCount() <= 0) {
            FishPondDailyUpdateService.resolveNeeds(level, pond, resolveResponsiblePlayer(level, itemEntity, pond));
        }
        pond.setWaterColor(FishPondDataService.get().resolveWaterColor(pond));
        FishPondWorldData.get(level).markChanged();
        FishPondBucketBlockEntity.syncVisualState(level, pond.bucketPos());
        FishPondColorSyncService.broadcastSnapshot(level);

        consumeItemEntity(level, itemEntity, stack, consumed);
        return ItemAbsorbResult.NEED_ITEM_ACCEPTED;
    }

    private static ItemAbsorbResult tryApplyGoldenAnimalCracker(ServerLevel level,
                                                                FishPondRecord pond,
                                                                ItemEntity itemEntity,
                                                                ItemStack stack) {
        if (!stack.is(ModItems.GOLDEN_ANIMAL_CRACKER.get())) {
            return ItemAbsorbResult.IGNORED;
        }
        if (pond.goldenAnimalCracker() || pond.currentPopulation() <= 0 || pond.fishTypeId().isBlank()) {
            return ItemAbsorbResult.IGNORED;
        }

        pond.setGoldenAnimalCracker(true);
        FishPondWorldData.get(level).markChanged();
        consumeItemEntity(level, itemEntity, stack, 1);
        return ItemAbsorbResult.GOLDEN_CRACKER_ACCEPTED;
    }

    private static void consumeItemEntity(ServerLevel level, ItemEntity itemEntity, ItemStack stack, int amount) {
        spawnEntrySplash(level, itemEntity);
        stack.shrink(amount);
        if (stack.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(stack);
        }
    }

    private static ServerPlayer resolveResponsiblePlayer(ServerLevel level, ItemEntity itemEntity, FishPondRecord pond) {
        if (itemEntity.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        if (pond.ownerPlayerUuid().isBlank()) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(UUID.fromString(pond.ownerPlayerUuid()));
    }

    private static void spawnEntrySplash(ServerLevel level, ItemEntity itemEntity) {
        level.playSound(null, itemEntity.blockPosition(), ModSounds.DROP_ITEM_IN_WATER.get(), SoundSource.BLOCKS, 0.45F, 0.95F + level.random.nextFloat() * 0.1F);
        level.sendParticles(
            ParticleTypes.SPLASH,
            itemEntity.getX(),
            itemEntity.getY(),
            itemEntity.getZ(),
            8,
            0.18D,
            0.04D,
            0.18D,
            0.02D
        );
    }

    private static boolean canFullyAddToInventory(Inventory inventory, ItemStack stack) {
        int remaining = stack.getCount();
        int inventoryMax = inventory.getMaxStackSize();

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                remaining -= Math.min(stack.getMaxStackSize(), inventoryMax);
            } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int slotLimit = Math.min(existing.getMaxStackSize(), inventoryMax);
                remaining -= Math.max(0, slotLimit - existing.getCount());
            }
            if (remaining <= 0) {
                return true;
            }
        }

        return false;
    }

    private static void awardHarvestExperience(ServerPlayer player, ItemStack stack) {
        int totalSellPrice = Math.max(0, StardewItemDataApi.getSellPrice(stack))
                * Math.max(1, stack.getCount());
        int bonusExperience = (int) (totalSellPrice * HARVEST_OUTPUT_EXP_MULTIPLIER);
        PlayerStardewDataAPI.addExperience(player, SkillType.FISHING, HARVEST_BASE_EXP + bonusExperience);
    }
}
