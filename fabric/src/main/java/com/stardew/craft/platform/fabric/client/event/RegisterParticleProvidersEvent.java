package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

@Environment(EnvType.CLIENT)
public final class RegisterParticleProvidersEvent extends Event implements ModBusEvent {
    private final ParticleFactoryRegistry registry;

    public RegisterParticleProvidersEvent() {
        this(ParticleFactoryRegistry.getInstance());
    }

    RegisterParticleProvidersEvent(ParticleFactoryRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public <T extends ParticleOptions> void registerSpecial(ParticleType<T> type, ParticleProvider<T> provider) {
        registry.register(Objects.requireNonNull(type, "type"), Objects.requireNonNull(provider, "provider"));
    }

    public <T extends ParticleOptions> void registerSprite(ParticleType<T> type, ParticleProvider.Sprite<T> sprite) {
        Objects.requireNonNull(sprite, "sprite");
        // The vanilla Sprite overload picks the supplied sprite after construction,
        // and permits a factory to decline spawning by returning null.
        registerSpriteSet(type, sprites -> (options, level, x, y, z, dx, dy, dz) -> {
            var particle = sprite.createParticle(options, level, x, y, z, dx, dy, dz);
            if (particle != null) particle.pickSprite(sprites);
            return particle;
        });
    }

    public <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type, SpriteParticleRegistration<T> factory) {
        Objects.requireNonNull(factory, "factory");
        registry.register(Objects.requireNonNull(type, "type"),
                (ParticleFactoryRegistry.PendingParticleFactory<T>) sprites -> factory.create(sprites));
    }

    /** Public counterpart of vanilla's package-private ParticleEngine factory type. */
    @FunctionalInterface
    public interface SpriteParticleRegistration<T extends ParticleOptions> {
        ParticleProvider<T> create(SpriteSet sprites);
    }
}
