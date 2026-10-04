package com.stardew.craft.port;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/** 1.20.5+ {@code GameTestHelper} members missing from 1.20.1, as static helpers taking the helper first. */
public final class PortGameTests {
    private PortGameTests() {}

    /** 1.21 public bounds include the authored origin and every complete structure block. */
    public static AABB getBounds(GameTestHelper helper) {
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(BlockPos.ZERO));
        if (!(blockEntity instanceof StructureBlockEntity structure)) {
            throw new GameTestAssertException("Missing GameTest structure block for bounds query");
        }
        return PortGameTestHarness121.structureBounds(structure);
    }

    /**
     * 1.21 {@code @GameTest(skyAccess = true)} skips only the encasement ceiling, not its four walls.
     * Our 1.20.1 harness ports that encasement with a ceiling by default, so open its interior top plane
     * before test setup. Use the harness's origin-aware whole-block bounds, not vanilla 1.20.1 bounds,
     * and leave non-barrier authored blocks, all lower blocks and the wall edges untouched.
     */
    public static void allowSkyAccess(GameTestHelper helper) {
        var level = helper.getLevel();
        var blockEntity = level.getBlockEntity(helper.absolutePos(BlockPos.ZERO));
        if (!(blockEntity instanceof StructureBlockEntity structure)) {
            throw new GameTestAssertException("Missing GameTest structure block for sky-access setup");
        }
        AABB bounds = PortGameTestHarness121.structureBounds(structure);
        BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos max = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        // Encasement walls are at min X/Z - 1 and max X/Z; only the cells between them are ceiling.
        for (BlockPos pos : BlockPos.betweenClosed(min.getX(), max.getY(), min.getZ(),
                max.getX() - 1, max.getY(), max.getZ() - 1)) {
            if (level.getBlockState(pos).is(Blocks.BARRIER)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** 1.21 {@code GameTestHelper#makeMockPlayer(GameType)}; 1.20.1 only has the creative-mode variant. */
    public static Player makeMockPlayer(GameTestHelper helper, GameType gameType) {
        return new Player(helper.getLevel(), BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "test-mock-player")) {
            @Override
            public boolean isSpectator() {
                return gameType == GameType.SPECTATOR;
            }

            @Override
            public boolean isCreative() {
                return gameType.isCreative();
            }

            @Override
            public boolean isLocalPlayer() {
                return true;
            }
        };
    }

    /** 1.21 {@code GameTestHelper#assertValueEqual}. */
    public static <N> void assertValueEqual(GameTestHelper helper, N actual, N expected, String valueName) {
        if (!actual.equals(expected)) {
            throw new GameTestAssertException("Expected " + valueName + " to be " + expected + ", but was " + actual);
        }
    }
}
