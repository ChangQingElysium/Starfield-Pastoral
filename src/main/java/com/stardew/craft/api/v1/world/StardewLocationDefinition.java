package com.stardew.craft.api.v1.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Reloadable location metadata; it never places or moves a pre-generated structure. */
public record StardewLocationDefinition(
        ResourceLocation dimension,
        String ledgerId,
        Vec3i min,
        Vec3i max,
        List<String> aliases,
        int priority
) {
    public static final Codec<StardewLocationDefinition> CODEC = RecordCodecBuilder.<StardewLocationDefinition>create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("dimension",
                    ResourceLocation.fromNamespaceAndPath("stardewcraft", "stardew_valley"))
                    .forGetter(StardewLocationDefinition::dimension),
            Codec.STRING.optionalFieldOf("ledger_id", "").forGetter(StardewLocationDefinition::ledgerId),
            Vec3i.CODEC.fieldOf("min").forGetter(StardewLocationDefinition::min),
            Vec3i.CODEC.fieldOf("max").forGetter(StardewLocationDefinition::max),
            Codec.STRING.listOf().optionalFieldOf("aliases", List.of()).forGetter(StardewLocationDefinition::aliases),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(StardewLocationDefinition::priority)
    ).apply(instance, StardewLocationDefinition::new)).validate(StardewLocationDefinition::validate);

    public StardewLocationDefinition {
        ledgerId = ledgerId == null ? "" : ledgerId.trim();
        aliases = List.copyOf(aliases == null ? List.of() : aliases);
    }

    private static DataResult<StardewLocationDefinition> validate(StardewLocationDefinition definition) {
        if (definition.max().x() < definition.min().x()
                || definition.max().y() < definition.min().y()
                || definition.max().z() < definition.min().z()) {
            return DataResult.error(() -> "location max values must be >= min values");
        }
        return DataResult.success(definition);
    }

    public record Vec3i(int x, int y, int z) {
        public static final Codec<Vec3i> CODEC = Codec.INT.listOf().comapFlatMap(values ->
                values.size() == 3
                        ? DataResult.success(new Vec3i(values.get(0), values.get(1), values.get(2)))
                        : DataResult.error(() -> "location vector must contain exactly three integers"),
                value -> List.of(value.x(), value.y(), value.z()));
    }
}
