package com.stardew.craft.animal.service;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.FarmAnimalDefinition;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Shared SDV-style Milk Pail / Shears harvest transaction.
 *
 * <p>The produce is cleared only after the complete stack fits in the inventory. A successful
 * harvest applies the source friendship and Farming XP rewards. Correct-tool attempts consume
 * four energy even when the animal has no collectable produce.
 */
public final class AnimalToolHarvestService {
    private static final float TOOL_ENERGY_COST = 4.0F;
    private static final int FRIENDSHIP_GAIN = 5;
    private static final int FARMING_EXPERIENCE = 5;

    private AnimalToolHarvestService() {
    }

    public static Result harvest(
            ServerLevel level,
            ServerPlayer player,
            long animalId,
            HarvestTool tool
    ) {
        return harvest(level, player, animalId, tool.itemId());
    }

    /**
     * Data-driven tool entry point for addon managed-animal entities.
     *
     * <p>The tool ID is compared with the animal definition's {@code harvest_tool}; no core
     * animal-type switch is required.
     */
    public static Result harvest(
            ServerLevel level,
            ServerPlayer player,
            long animalId,
            ResourceLocation toolItemId
    ) {
        AnimalWorldData data = AnimalWorldData.get(level);
        FarmAnimalRecord record = data.getAnimal(animalId).orElse(null);
        if (record == null || !acceptsTool(record, toolItemId)) {
            return Result.NOT_HANDLED;
        }
        if (record.lastProcessedAbsDay()
                < com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay()) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "stardewcraft.animal.interact.catching_up"),
                    true
            );
            return Result.CATCHING_UP;
        }

        PlayerStardewDataAPI.consumeEnergy(player, TOOL_ENERGY_COST);
        if (record.isBaby() || record.currentProduceId().isBlank()) {
            return Result.NO_PRODUCE;
        }

        ItemStack produce = resolveProduceStack(record);
        if (produce.isEmpty()) {
            return Result.NO_PRODUCE;
        }
        if (record.hasEatenAnimalCracker()) {
            produce.setCount(2);
        }
        if (!canFit(player.getInventory(), produce)) {
            return Result.INVENTORY_FULL;
        }
        ItemStack collectedStack = produce.copy();
        if (!player.addItem(produce)) {
            return Result.INVENTORY_FULL;
        }

        record.setCurrentProduceId("");
        record.setProduceQuality(QualityHelper.NORMAL);
        record.addFriendship(FRIENDSHIP_GAIN);
        data.markChanged();

        PlayerStardewDataAPI.addExperience(
                player, SkillType.FARMING, FARMING_EXPERIENCE);
        FarmAnimalDefinition definition = FarmAnimalDefinitions.find(record.animalTypeId());
        AnimalProduceStatService.recordForPlayer(
                player, definition, collectedStack);
        return Result.HARVESTED;
    }

    private static boolean acceptsTool(
            FarmAnimalRecord record,
            ResourceLocation toolItemId
    ) {
        FarmAnimalDefinition definition =
                FarmAnimalDefinitions.find(record.animalTypeId());
        if (definition == null
                || definition.harvestType()
                != FarmAnimalDefinition.HarvestType.HARVEST_WITH_TOOL) {
            return false;
        }
        return definition.harvestTool() != null
                && definition.harvestTool().equals(toolItemId);
    }

    private static ItemStack resolveProduceStack(FarmAnimalRecord record) {
        ResourceLocation id = ResourceLocation.tryParse(record.currentProduceId());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        QualityHelper.setQuality(stack, record.produceQuality());
        return stack;
    }

    private static boolean canFit(Inventory inventory, ItemStack candidate) {
        int remaining = candidate.getCount();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                remaining -= candidate.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(existing, candidate)) {
                remaining -= Math.max(0, existing.getMaxStackSize() - existing.getCount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    public enum HarvestTool {
        MILK_PAIL(ResourceLocation.fromNamespaceAndPath("stardewcraft", "milk_pail")),
        SHEARS(ResourceLocation.fromNamespaceAndPath("stardewcraft", "shears"));

        private final ResourceLocation itemId;

        HarvestTool(ResourceLocation itemId) {
            this.itemId = itemId;
        }

        public ResourceLocation itemId() {
            return itemId;
        }
    }

    public enum Result {
        NOT_HANDLED,
        CATCHING_UP,
        NO_PRODUCE,
        INVENTORY_FULL,
        HARVESTED
    }
}
