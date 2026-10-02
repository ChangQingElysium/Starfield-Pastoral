package com.stardew.craft.port.net.minecraft.core.particles;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.stardew.craft.port.PortParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * 1.20.5+ {@code net.minecraft.core.particles.ColorParticleOption}: a particle carrying an ARGB colour.
 * <p>
 * 1.20.1 particle options serialise themselves and their type supplies a {@link ParticleOptions.Deserializer}; use
 * {@link #DESERIALIZER} when constructing a {@code ParticleType<ColorParticleOption>}. Vanilla 1.20.1
 * {@code ENTITY_EFFECT} is an uncoloured {@link SimpleParticleType}, so {@link #create(SimpleParticleType, float, float, float)}
 * maps it to {@link PortParticles#ENTITY_EFFECT}, which renders the vanilla spell sprite with this colour.
 */
public class ColorParticleOption implements ParticleOptions {
    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<ColorParticleOption> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public ColorParticleOption fromCommand(ParticleType<ColorParticleOption> type, StringReader reader)
                throws CommandSyntaxException {
            reader.expect(' ');
            return new ColorParticleOption(type, reader.readInt());
        }

        @Override
        public ColorParticleOption fromNetwork(ParticleType<ColorParticleOption> type, FriendlyByteBuf buffer) {
            return new ColorParticleOption(type, buffer.readInt());
        }
    };

    private final ParticleType<ColorParticleOption> type;
    private final int color;

    public static MapCodec<ColorParticleOption> codec(ParticleType<ColorParticleOption> type) {
        return Codec.INT.xmap(color -> new ColorParticleOption(type, color), option -> option.color).fieldOf("color");
    }

    private ColorParticleOption(ParticleType<ColorParticleOption> type, int color) {
        this.type = type;
        this.color = color;
    }

    @Override
    public ParticleType<ColorParticleOption> getType() {
        return this.type;
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buffer) {
        buffer.writeInt(this.color);
    }

    @Override
    public String writeToString() {
        return BuiltInRegistries.PARTICLE_TYPE.getKey(this.type) + " " + this.color;
    }

    public float getRed() {
        return FastColor.ARGB32.red(this.color) / 255.0F;
    }

    public float getGreen() {
        return FastColor.ARGB32.green(this.color) / 255.0F;
    }

    public float getBlue() {
        return FastColor.ARGB32.blue(this.color) / 255.0F;
    }

    public float getAlpha() {
        return FastColor.ARGB32.alpha(this.color) / 255.0F;
    }

    public static ColorParticleOption create(ParticleType<ColorParticleOption> type, int color) {
        return new ColorParticleOption(type, color);
    }

    public static ColorParticleOption create(ParticleType<ColorParticleOption> type, float red, float green, float blue) {
        return create(type, FastColor.ARGB32.color(255, channel(red), channel(green), channel(blue)));
    }

    /** 1.21 {@code ParticleTypes.ENTITY_EFFECT} is a {@code ParticleType<ColorParticleOption>}; see class docs. */
    public static ColorParticleOption create(SimpleParticleType type, float red, float green, float blue) {
        if (type != ParticleTypes.ENTITY_EFFECT) {
            throw new UnsupportedOperationException("PORT(1.20.1): no coloured counterpart for " + type);
        }
        return create(PortParticles.ENTITY_EFFECT.get(), red, green, blue);
    }

    /** 1.21 {@code FastColor.as8BitChannel}. */
    private static int channel(float value) {
        return Mth.floor(value * 255.0F);
    }
}
