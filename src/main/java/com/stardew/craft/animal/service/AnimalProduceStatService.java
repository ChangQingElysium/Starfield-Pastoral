package com.stardew.craft.animal.service;

import com.stardew.craft.animal.model.FarmAnimalDefinition;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Applies source {@code StatToIncrementOnProduce} entries without flattening their filters. */
public final class AnimalProduceStatService {
    private AnimalProduceStatService() {
    }

    public static int recordForPlayer(
            ServerPlayer player,
            FarmAnimalDefinition definition,
            ItemStack produce
    ) {
        if (player == null) {
            return 0;
        }
        List<String> stats =
                matchingStats(definition, produce);
        for (String stat : stats) {
            PlayerStardewDataAPI.incrementStat(
                    player, stat, produce.getCount());
        }
        return stats.size();
    }

    public static int recordForOwner(
            UUID ownerPlayerId,
            FarmAnimalDefinition definition,
            ItemStack produce
    ) {
        if (ownerPlayerId == null) {
            return 0;
        }
        List<String> stats =
                matchingStats(definition, produce);
        for (String stat : stats) {
            PlayerStardewDataAPI.incrementStat(
                    ownerPlayerId,
                    stat,
                    produce.getCount());
        }
        return stats.size();
    }

    static List<String> matchingStats(
            FarmAnimalDefinition definition,
            ItemStack produce
    ) {
        if (definition == null
                || produce == null
                || produce.isEmpty()) {
            return List.of();
        }
        ResourceLocation itemId =
                BuiltInRegistries.ITEM.getKey(
                        produce.getItem());
        if (itemId == null) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (FarmAnimalDefinition.ProduceStat stat
                : definition.produceStats()) {
            if (!stat.requiredItems().isEmpty()
                    && !stat.requiredItems()
                    .contains(itemId)) {
                continue;
            }
            boolean tagsMatch = true;
            for (ResourceLocation tagId
                    : stat.requiredTags()) {
                TagKey<Item> tag = TagKey.create(
                        Registries.ITEM, tagId);
                if (!produce.is(tag)) {
                    tagsMatch = false;
                    break;
                }
            }
            if (tagsMatch) {
                result.add(stat.statName());
            }
        }
        return List.copyOf(result);
    }
}
