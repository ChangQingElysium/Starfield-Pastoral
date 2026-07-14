package com.stardew.craft.api.v1.profession;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Data-driven metadata for a profession attached to an existing core skill. */
public record StardewProfessionDefinition(
        ResourceLocation skill,
        int requiredLevel,
        Optional<ResourceLocation> parent,
        String nameKey,
        String descKey,
        Optional<ResourceLocation> effectHandler
) {
    public static final Codec<StardewProfessionDefinition> CODEC =
            RecordCodecBuilder.<StardewProfessionDefinition>create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("skill").forGetter(StardewProfessionDefinition::skill),
                    Codec.intRange(1, 10).fieldOf("required_level").forGetter(StardewProfessionDefinition::requiredLevel),
                    ResourceLocation.CODEC.optionalFieldOf("parent").forGetter(StardewProfessionDefinition::parent),
                    Codec.STRING.fieldOf("name_key").forGetter(StardewProfessionDefinition::nameKey),
                    Codec.STRING.fieldOf("desc_key").forGetter(StardewProfessionDefinition::descKey),
                    ResourceLocation.CODEC.optionalFieldOf("effect_handler")
                            .forGetter(StardewProfessionDefinition::effectHandler)
            ).apply(instance, StardewProfessionDefinition::new));

    public StardewProfessionDefinition {
        parent = parent == null ? Optional.empty() : parent;
        effectHandler = effectHandler == null ? Optional.empty() : effectHandler;
    }
}
