package com.stardew.craft.api.v1.museum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** A museum library book unlocked by the world-shared lost-book counter. */
public record StardewLostBookDefinition(
        int unlockAt,
        String text,
        List<StardewCondition> availableWhen,
        List<Interaction> interactions
) {
    public static final Codec<StardewLostBookDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("unlock_at")
                    .forGetter(StardewLostBookDefinition::unlockAt),
            Codec.STRING.fieldOf("text").forGetter(StardewLostBookDefinition::text),
            StardewConditions.CODEC.listOf().optionalFieldOf("available_when", List.of())
                    .forGetter(StardewLostBookDefinition::availableWhen),
            Interaction.CODEC.listOf().optionalFieldOf("interactions", List.of())
                    .forGetter(StardewLostBookDefinition::interactions)
    ).apply(instance, StardewLostBookDefinition::new));

    public StardewLostBookDefinition {
        text = text == null ? "" : text.trim();
        availableWhen = List.copyOf(availableWhen == null ? List.of() : availableWhen);
        interactions = List.copyOf(interactions == null ? List.of() : interactions);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("lost book text or translation key cannot be blank");
        }
    }

    /** Exact server-side block interaction binding for a library book. */
    public record Interaction(ResourceLocation dimension, int x, int y, int z) {
        private static final ResourceLocation DEFAULT_DIMENSION =
                ResourceLocation.fromNamespaceAndPath("stardewcraft", "stardew_valley");

        public static final Codec<Interaction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("dimension", DEFAULT_DIMENSION)
                        .forGetter(Interaction::dimension),
                Codec.INT.fieldOf("x").forGetter(Interaction::x),
                Codec.INT.fieldOf("y").forGetter(Interaction::y),
                Codec.INT.fieldOf("z").forGetter(Interaction::z)
        ).apply(instance, Interaction::new));
    }
}
