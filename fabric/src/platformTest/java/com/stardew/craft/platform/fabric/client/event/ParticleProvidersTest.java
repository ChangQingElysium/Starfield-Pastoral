package com.stardew.craft.platform.fabric.client.event;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class ParticleProvidersTest {
    @Test
    void specialAndSpriteSetFactoriesReachTheNativeRegistryWithoutEagerSpriteLookup() {
        var registry = new CapturingRegistry();
        var event = new RegisterParticleProvidersEvent(registry);
        var type = new FixtureParticleType();
        ParticleProvider<SimpleParticleType> provider = (options, level, x, y, z, dx, dy, dz) -> null;
        event.registerSpecial(type, provider);
        assertSame(type, registry.type);
        assertSame(provider, registry.direct);

        var receivedSprites = new AtomicReference<SpriteSet>();
        event.registerSpriteSet(type, sprites -> {
            receivedSprites.set(sprites);
            return provider;
        });
        assertNull(receivedSprites.get());
        var sprites = new FixtureSprites();
        assertSame(provider, registry.pending.create(sprites));
        assertSame(sprites, receivedSprites.get());
    }

    @Test
    void singleSpriteFactoriesMayDeclineSpawningAsVanillaDoes() {
        var registry = new CapturingRegistry();
        var event = new RegisterParticleProvidersEvent(registry);
        var type = new FixtureParticleType();
        var received = new AtomicReference<SimpleParticleType>();
        event.registerSprite(type, (options, level, x, y, z, dx, dy, dz) -> {
            received.set(options);
            assertEquals(5, dx);
            return null;
        });
        @SuppressWarnings("unchecked")
        ParticleProvider<SimpleParticleType> factory = (ParticleProvider<SimpleParticleType>) registry.pending.create(new FixtureSprites());
        assertNull(factory.createParticle(type, null, 1, 2, 3, 5, 6, 7));
        assertSame(type, received.get());
    }

    private static final class FixtureParticleType extends SimpleParticleType {
        FixtureParticleType() { super(false); }
    }

    private static final class CapturingRegistry implements ParticleFactoryRegistry {
        ParticleType<?> type;
        ParticleProvider<?> direct;
        PendingParticleFactory<?> pending;
        @Override public <T extends ParticleOptions> void register(ParticleType<T> type, ParticleProvider<T> provider) {
            this.type = type;
            direct = provider;
        }
        @Override public <T extends ParticleOptions> void register(ParticleType<T> type, PendingParticleFactory<T> factory) {
            this.type = type;
            pending = factory;
        }
    }

    private static final class FixtureSprites implements FabricSpriteProvider {
        @Override public TextureAtlasSprite get(int age, int lifetime) { throw new AssertionError("eager sprite access"); }
        @Override public TextureAtlasSprite get(RandomSource random) { throw new AssertionError("eager sprite access"); }
        @Override public TextureAtlas getAtlas() { throw new AssertionError("eager atlas access"); }
        @Override public List<TextureAtlasSprite> getSprites() { throw new AssertionError("eager sprite access"); }
    }
}
