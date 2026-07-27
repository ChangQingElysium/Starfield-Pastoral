package com.stardew.craft.api.v1.mining;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Weighted reference to a registered mine-monster profile. */
public record StardewMineMonsterSpawnEntry(
        ResourceLocation profile,
        int weight
) {
    public static final Codec<StardewMineMonsterSpawnEntry> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("profile")
                            .forGetter(StardewMineMonsterSpawnEntry::profile),
                    Codec.intRange(1, 1_000_000)
                            .optionalFieldOf("weight", 1)
                            .forGetter(StardewMineMonsterSpawnEntry::weight)
            ).apply(instance, StardewMineMonsterSpawnEntry::new));
}
