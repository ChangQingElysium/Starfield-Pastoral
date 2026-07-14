package com.stardew.craft.api.v1.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Immutable server-authoritative shop definition. */
public record StardewShopDefinition(
        String legacyId,
        String ownerNpc,
        String ownerDialogue,
        List<StardewShopEntry> entries,
        List<String> acceptedSellTypes,
        List<ResourceLocation> inventoryProviders
) {
    public static final Codec<StardewShopDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("legacy_id", "").forGetter(StardewShopDefinition::legacyId),
            Codec.STRING.optionalFieldOf("owner_npc", "").forGetter(StardewShopDefinition::ownerNpc),
            Codec.STRING.optionalFieldOf("owner_dialogue", "").forGetter(StardewShopDefinition::ownerDialogue),
            StardewShopEntry.CODEC.listOf().optionalFieldOf("entries", List.of())
                    .forGetter(StardewShopDefinition::entries),
            Codec.STRING.listOf().optionalFieldOf("accepted_sell_types", List.of())
                    .forGetter(StardewShopDefinition::acceptedSellTypes),
            ResourceLocation.CODEC.listOf().optionalFieldOf("inventory_providers", List.of())
                    .forGetter(StardewShopDefinition::inventoryProviders)
    ).apply(instance, StardewShopDefinition::new));

    public StardewShopDefinition {
        entries = List.copyOf(entries);
        acceptedSellTypes = List.copyOf(acceptedSellTypes);
        inventoryProviders = List.copyOf(inventoryProviders);
    }
}
