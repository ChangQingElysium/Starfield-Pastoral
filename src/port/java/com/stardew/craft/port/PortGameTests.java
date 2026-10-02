package com.stardew.craft.port;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/** 1.20.5+ {@code GameTestHelper} members missing from 1.20.1, as static helpers taking the helper first. */
public final class PortGameTests {
    private PortGameTests() {}

    /** 1.21 exposes this publicly; 1.20.1 delegates its private method to the same structure utility. */
    public static AABB getBounds(GameTestHelper helper) {
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(BlockPos.ZERO));
        if (!(blockEntity instanceof StructureBlockEntity structure)) {
            throw new GameTestAssertException("Missing GameTest structure block for bounds query");
        }
        return StructureUtils.getStructureBounds(structure);
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
