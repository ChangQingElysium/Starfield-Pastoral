package com.stardew.craft.api.v1.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;

/** Reloadable portal destination metadata. Portal trigger placement remains a world operation. */
public record StardewPortalDefinition(
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        Mode mode
) {
    public static final Codec<StardewPortalDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("x").forGetter(StardewPortalDefinition::x),
            Codec.DOUBLE.fieldOf("y").forGetter(StardewPortalDefinition::y),
            Codec.DOUBLE.fieldOf("z").forGetter(StardewPortalDefinition::z),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(StardewPortalDefinition::yaw),
            Codec.FLOAT.optionalFieldOf("pitch", 0.0F).forGetter(StardewPortalDefinition::pitch),
            Mode.CODEC.optionalFieldOf("mode", Mode.NONE).forGetter(StardewPortalDefinition::mode)
    ).apply(instance, StardewPortalDefinition::new));

    public enum Mode {
        ENTRANCE,
        EXIT,
        NONE;

        public static final Codec<Mode> CODEC = Codec.STRING.comapFlatMap(raw -> {
            try {
                return DataResult.<Mode>success(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return DataResult.<Mode>error(() -> "unknown portal mode: " + raw);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
