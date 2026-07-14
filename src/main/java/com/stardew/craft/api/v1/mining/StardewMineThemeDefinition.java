package com.stardew.craft.api.v1.mining;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Reloadable material pools and floor range bound to a registered mine terrain mechanic. */
public record StardewMineThemeDefinition(
        String mechanicId,
        int minFloor,
        int maxFloor,
        int priority,
        ResourceLocation mainStone,
        ResourceLocation darkStone,
        List<ResourceLocation> decorA,
        List<ResourceLocation> decorB,
        List<ResourceLocation> decorativeStones,
        List<ResourceLocation> vanillaAccents,
        List<ResourceLocation> caveDecorations,
        Map<String, ResourceLocation> ores
) {
    public static final Codec<StardewMineThemeDefinition> CODEC = RecordCodecBuilder.<StardewMineThemeDefinition>create(instance -> instance.group(
            Codec.STRING.fieldOf("mechanic_id").forGetter(StardewMineThemeDefinition::mechanicId),
            Codec.INT.fieldOf("min_floor").forGetter(StardewMineThemeDefinition::minFloor),
            Codec.INT.fieldOf("max_floor").forGetter(StardewMineThemeDefinition::maxFloor),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(StardewMineThemeDefinition::priority),
            ResourceLocation.CODEC.fieldOf("main_stone").forGetter(StardewMineThemeDefinition::mainStone),
            ResourceLocation.CODEC.fieldOf("dark_stone").forGetter(StardewMineThemeDefinition::darkStone),
            ResourceLocation.CODEC.listOf().optionalFieldOf("decor_a", List.of())
                    .forGetter(StardewMineThemeDefinition::decorA),
            ResourceLocation.CODEC.listOf().optionalFieldOf("decor_b", List.of())
                    .forGetter(StardewMineThemeDefinition::decorB),
            ResourceLocation.CODEC.listOf().optionalFieldOf("decorative_stones", List.of())
                    .forGetter(StardewMineThemeDefinition::decorativeStones),
            ResourceLocation.CODEC.listOf().optionalFieldOf("vanilla_accents", List.of())
                    .forGetter(StardewMineThemeDefinition::vanillaAccents),
            ResourceLocation.CODEC.listOf().optionalFieldOf("cave_decorations", List.of())
                    .forGetter(StardewMineThemeDefinition::caveDecorations),
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC).fieldOf("ores")
                    .forGetter(StardewMineThemeDefinition::ores)
    ).apply(instance, StardewMineThemeDefinition::new)).validate(StardewMineThemeDefinition::validate);

    public StardewMineThemeDefinition {
        mechanicId = mechanicId == null ? "" : mechanicId.trim().toLowerCase(java.util.Locale.ROOT);
        decorA = List.copyOf(decorA == null ? List.of() : decorA);
        decorB = List.copyOf(decorB == null ? List.of() : decorB);
        decorativeStones = List.copyOf(decorativeStones == null ? List.of() : decorativeStones);
        vanillaAccents = List.copyOf(vanillaAccents == null ? List.of() : vanillaAccents);
        caveDecorations = List.copyOf(caveDecorations == null ? List.of() : caveDecorations);
        ores = Map.copyOf(ores == null ? Map.of() : ores);
    }

    private static DataResult<StardewMineThemeDefinition> validate(StardewMineThemeDefinition definition) {
        if (definition.mechanicId().isBlank()) return DataResult.error(() -> "mine mechanic_id must not be blank");
        if (definition.maxFloor() < definition.minFloor()) {
            return DataResult.error(() -> "mine max_floor must be >= min_floor");
        }
        if (!definition.ores().containsKey("copper")) {
            return DataResult.error(() -> "mine theme needs at least a copper fallback ore");
        }
        return DataResult.success(definition);
    }
}
