package com.stardew.craft.animal.service;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 筒仓建造验证：需要 2×2×10 的红砖柱，管理器替换其中任意一个红砖即算成型。
 */
@SuppressWarnings("null")
public final class SiloManagerValidationService {
    private SiloManagerValidationService() {}

    /** 筒仓高度（方块数） */
    private static final int SILO_HEIGHT = 10;
    /** 筒仓水平尺寸 */
    private static final int SILO_WIDTH = 2;

    /**
     * 验证管理器位置是否满足筒仓建造条件。
     * 管理器必须在 2×2×10 红砖柱内，且柱子其余位置全为红砖。
     */
    public static ValidationResult validate(ServerLevel level, BlockPos managerPos) {
        // 管理器可以在 2×2 底面的任意象限
        // 尝试 4 种偏移：manager 分别在 (x,z), (x-1,z), (x,z-1), (x-1,z-1) 相对于柱子左下角
        for (int dx = 0; dx >= -1; dx--) {
            for (int dz = 0; dz >= -1; dz--) {
                int baseX = managerPos.getX() + dx;
                int baseZ = managerPos.getZ() + dz;

                // 管理器可以在柱子任何高度 → 尝试所有可能的 baseY
                for (int yOff = 0; yOff < SILO_HEIGHT; yOff++) {
                    int baseY = managerPos.getY() - yOff;

                    ValidationResult result = checkColumn(level, managerPos, baseX, baseY, baseZ);
                    if (result.success()) {
                        return result;
                    }
                }
            }
        }

        return new ValidationResult(false, Component.translatable("stardewcraft.manager.silo.validation.structure"), 0, 0, 0, 0, 0, 0);
    }

    private static ValidationResult checkColumn(ServerLevel level, BlockPos managerPos,
                                                 int baseX, int baseY, int baseZ) {
        int totalBlocks = SILO_WIDTH * SILO_WIDTH * SILO_HEIGHT;
        int brickCount = 0;
        boolean managerFound = false;

        for (int y = 0; y < SILO_HEIGHT; y++) {
            for (int x = 0; x < SILO_WIDTH; x++) {
                for (int z = 0; z < SILO_WIDTH; z++) {
                    BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);

                    if (pos.equals(managerPos)) {
                        managerFound = true;
                        brickCount++;
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.BRICKS)) {
                        brickCount++;
                    } else {
                        return new ValidationResult(false, Component.empty(), 0, 0, 0, 0, 0, 0);
                    }
                }
            }
        }

        if (!managerFound) {
            return new ValidationResult(false, Component.empty(), 0, 0, 0, 0, 0, 0);
        }

        if (brickCount < totalBlocks) {
            return new ValidationResult(false, Component.empty(), 0, 0, 0, 0, 0, 0);
        }

        return new ValidationResult(
            true,
            Component.translatable("stardewcraft.manager.validation.success"),
            baseX, baseY, baseZ,
            baseX + SILO_WIDTH - 1,
            baseY + SILO_HEIGHT - 1,
            baseZ + SILO_WIDTH - 1
        );
    }

    public record ValidationResult(boolean success, Component message,
                                   int minX, int minY, int minZ,
                                   int maxX, int maxY, int maxZ) {
    }
}
