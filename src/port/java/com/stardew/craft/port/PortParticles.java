package com.stardew.craft.port;

import com.mojang.serialization.Codec;
import com.stardew.craft.port.net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpellParticle;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Particle types that 1.21 vanilla has and 1.20.1 lacks. {@link #ENTITY_EFFECT} is the coloured 1.20.5+
 * {@code minecraft:entity_effect}: same sprites (assets/stardewcraft/particles/port_entity_effect.json mirrors the
 * vanilla list) and the same {@code SpellParticle}, coloured by the option instead of by the velocity.
 */
public final class PortParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, PortBootstrap.NAMESPACE);

    @SuppressWarnings("deprecation")
    public static final RegistryObject<ParticleType<ColorParticleOption>> ENTITY_EFFECT = TYPES.register(
            "port_entity_effect", () -> new ParticleType<ColorParticleOption>(false, ColorParticleOption.DESERIALIZER) {
                @Override
                public Codec<ColorParticleOption> codec() {
                    return ColorParticleOption.codec(this).codec();
                }
            });

    private static boolean registered;

    private PortParticles() {}

    public static synchronized void register(IEventBus modBus) {
        if (registered) return;
        registered = true;
        TYPES.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(Client::registerProviders);
        }
    }

    private static final class Client {
        private static void registerProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ENTITY_EFFECT.get(), sprites -> (option, level, x, y, z, dx, dy, dz) -> {
                // 1.21 SpellParticle.MobEffectProvider: velocity passes through, colour comes from the option.
                // 1.20.1 MobProvider colours from the velocity first; the option colour then replaces it. Alpha is
                // always 1 for the float factories the mod uses (Particle#setAlpha is not accessible here).
                Particle particle = new SpellParticle.MobProvider(sprites)
                        .createParticle(ParticleTypes.ENTITY_EFFECT, level, x, y, z, dx, dy, dz);
                if (particle != null) particle.setColor(option.getRed(), option.getGreen(), option.getBlue());
                return particle;
            });
        }
    }
}
