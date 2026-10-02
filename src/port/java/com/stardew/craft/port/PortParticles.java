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
                // 1.21 SpellParticle.MobEffectProvider: new SpellParticle(...) with the velocity passed through, then
                // setColor(option rgb) and setAlpha(option alpha). 1.20.1 MobProvider runs the same constructor and
                // colours from the velocity first; the option colour then replaces it. The alpha is set afterwards
                // like 1.21, which also overrides the constructor's 0 when the local player is scoping nearby
                // (1.20.1 SpellParticle#tick lerps towards 1, the alpha of every float-RGB option the mod creates,
                // where 1.21 lerps towards that same originalAlpha).
                Particle particle = new SpellParticle.MobProvider(sprites)
                        .createParticle(ParticleTypes.ENTITY_EFFECT, level, x, y, z, dx, dy, dz);
                if (particle != null) {
                    particle.setColor(option.getRed(), option.getGreen(), option.getBlue());
                    ((com.stardew.craft.mixin.PortParticleAlphaInvoker) particle).stardewcraft$setAlpha(option.getAlpha());
                }
                return particle;
            });
        }
    }
}
