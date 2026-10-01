package com.stardew.craft.api.v1.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * One MobEffect applied after a configured food is consumed.
 *
 * <p>The effect may come from Minecraft, StardewCraft, or any loaded addon.
 * Amplifiers use Minecraft's zero-based convention.
 */
public record StardewFoodEffect(
        ResourceLocation effect,
        int durationTicks,
        int amplifier,
        double chance,
        boolean ambient,
        boolean showParticles,
        boolean showIcon
) {
    public static final Codec<StardewFoodEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("effect").forGetter(StardewFoodEffect::effect),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("duration_ticks")
                    .forGetter(StardewFoodEffect::durationTicks),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(0, 255), "amplifier", 0)
                    .forGetter(StardewFoodEffect::amplifier),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.doubleRange(0.0D, 1.0D), "chance", 1.0D)
                    .forGetter(StardewFoodEffect::chance),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.BOOL, "ambient", false).forGetter(StardewFoodEffect::ambient),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.BOOL, "show_particles", true).forGetter(StardewFoodEffect::showParticles),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.BOOL, "show_icon", true).forGetter(StardewFoodEffect::showIcon)
    ).apply(instance, StardewFoodEffect::new));

    public StardewFoodEffect {
        Objects.requireNonNull(effect, "effect");
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("durationTicks must be positive");
        }
        if (amplifier < 0 || amplifier > 255) {
            throw new IllegalArgumentException("amplifier must be between 0 and 255");
        }
        if (!Double.isFinite(chance) || chance < 0.0D || chance > 1.0D) {
            throw new IllegalArgumentException("chance must be between 0 and 1");
        }
    }
}
