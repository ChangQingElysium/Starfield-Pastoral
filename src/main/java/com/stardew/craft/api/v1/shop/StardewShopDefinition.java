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
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "legacy_id", "").forGetter(StardewShopDefinition::legacyId),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "owner_npc", "").forGetter(StardewShopDefinition::ownerNpc),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "owner_dialogue", "").forGetter(StardewShopDefinition::ownerDialogue),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(StardewShopEntry.CODEC.listOf(), "entries", List.of())
                    .forGetter(StardewShopDefinition::entries),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING.listOf(), "accepted_sell_types", List.of())
                    .forGetter(StardewShopDefinition::acceptedSellTypes),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(ResourceLocation.CODEC.listOf(), "inventory_providers", List.of())
                    .forGetter(StardewShopDefinition::inventoryProviders)
    ).apply(instance, StardewShopDefinition::new));

    public StardewShopDefinition {
        entries = List.copyOf(entries);
        acceptedSellTypes = List.copyOf(acceptedSellTypes);
        inventoryProviders = List.copyOf(inventoryProviders);
    }
}
