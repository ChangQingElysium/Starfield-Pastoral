package com.stardew.craft.platform.fabric.client.extension;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientBlockEffectsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void hitReceivesTheActualFractionalHitAndHandledParticlesSuppressOnlyTheVanillaFallback() {
        var state = Blocks.STONE.defaultBlockState();
        var hit = new BlockHitResult(new Vec3(3.125, 8.875, -2.375), Direction.WEST, new BlockPos(3, 8, -3), true);
        var fallback = new AtomicInteger();
        var extension = new IClientBlockExtensions() {
            @Override public boolean addHitEffects(BlockState actual, Level level, HitResult target, ParticleEngine engine) {
                assertSame(state, actual); assertSame(hit, target);
                assertEquals(3.125, target.getLocation().x); assertTrue(((BlockHitResult) target).isInside());
                return true;
            }
        };
        ClientBlockEffects.hit(extension, state, null, hit, null, fallback::incrementAndGet);
        assertEquals(0, fallback.get());
        ClientBlockEffects.hit(IClientBlockExtensions.DEFAULT, state, null, hit, null, fallback::incrementAndGet);
        assertEquals(1, fallback.get());
    }

    @Test
    void defaultDestroyAndTintRulesRetainNativeTerrainParticleFlagsAndGrassBehavior() {
        var normal = Blocks.STONE.defaultBlockState();
        var suppressed = BuiltInRegistries.BLOCK.stream().map(block -> block.defaultBlockState())
                .filter(state -> !state.shouldSpawnTerrainParticles()).findFirst().orElseThrow();
        assertFalse(IClientBlockExtensions.DEFAULT.addDestroyEffects(normal, null, BlockPos.ZERO, null));
        assertTrue(IClientBlockExtensions.DEFAULT.addDestroyEffects(suppressed, null, BlockPos.ZERO, null));
        assertFalse(IClientBlockExtensions.DEFAULT.areBreakingParticlesTinted(Blocks.GRASS_BLOCK.defaultBlockState(), null, BlockPos.ZERO));
        assertTrue(IClientBlockExtensions.DEFAULT.areBreakingParticlesTinted(normal, null, BlockPos.ZERO));
    }
}
