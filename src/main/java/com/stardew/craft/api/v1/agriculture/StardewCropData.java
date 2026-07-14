package com.stardew.craft.api.v1.agriculture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Shared crop metadata for growth-aware tools, harvest XP and add-on integrations. */
public record StardewCropData(
        List<String> seasons,
        List<Integer> phaseDays,
        int regrowDays,
        int farmingExperience,
        ResourceLocation harvestMethod,
        ResourceLocation produce,
        ResourceLocation seed
) {
    public static final Codec<StardewCropData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("seasons", List.of("spring", "summer", "fall", "winter"))
                    .forGetter(StardewCropData::seasons),
            Codec.INT.listOf().optionalFieldOf("phase_days", List.of())
                    .forGetter(StardewCropData::phaseDays),
            Codec.INT.optionalFieldOf("regrow_days", -1).forGetter(StardewCropData::regrowDays),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("farming_experience", 0)
                    .forGetter(StardewCropData::farmingExperience),
            ResourceLocation.CODEC.optionalFieldOf("harvest_method",
                    ResourceLocation.fromNamespaceAndPath("stardewcraft", "grab"))
                    .forGetter(StardewCropData::harvestMethod),
            ResourceLocation.CODEC.fieldOf("produce").forGetter(StardewCropData::produce),
            ResourceLocation.CODEC.fieldOf("seed").forGetter(StardewCropData::seed)
    ).apply(instance, StardewCropData::new));

    public StardewCropData {
        seasons = List.copyOf(seasons == null ? List.of() : seasons);
        phaseDays = List.copyOf(phaseDays == null ? List.of() : phaseDays);
    }
}
