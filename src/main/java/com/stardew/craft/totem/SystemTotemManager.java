package com.stardew.craft.totem;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.totem.TotemPoleBlock;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.utility.totem.TotemType;
import com.stardew.craft.blockentity.TotemPoleBlockEntity;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * 系统图腾柱管理器 — 在星露谷维度加载时确保主地图系统柱存在。
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public class SystemTotemManager {

    /** 旧版农场系统柱：农场现在是玩家实例，不再属于主地图固定系统柱。 */
    private static final int OLD_SYSTEM_ID_FARM = 0;
    private static final BlockPos OLD_POS_FARM = new BlockPos(135, -12, 136);

    /** 系统柱固定ID：1=Mountain, 2=Beach, 3=Desert */
    private static final int SYSTEM_ID_MOUNTAIN = 1;
    private static final int SYSTEM_ID_BEACH = 2;
    private static final int SYSTEM_ID_DESERT = 3;

    /** 系统柱坐标 */
    private static final BlockPos POS_MOUNTAIN = new BlockPos(75, 81, -105);
    private static final BlockPos POS_BEACH = new BlockPos(44, 60, 93);
    private static final BlockPos POS_DESERT = new BlockPos(-203, 64, -157);

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(ModDimensions.STARDEW_VALLEY)) return;

        removeOldFarmSystemPole(level);
        ensureSystemPole(level, POS_MOUNTAIN, TotemType.MOUNTAIN, ModBlocks.TOTEM_POLE_MOUNTAIN, SYSTEM_ID_MOUNTAIN, Direction.NORTH);
        ensureSystemPole(level, POS_BEACH, TotemType.BEACH, ModBlocks.TOTEM_POLE_BEACH, SYSTEM_ID_BEACH, Direction.NORTH);
        ensureSystemPole(level, POS_DESERT, TotemType.DESERT, ModBlocks.TOTEM_POLE_DESERT, SYSTEM_ID_DESERT, Direction.SOUTH);
    }

    private static void removeOldFarmSystemPole(ServerLevel level) {
        BlockState existing = level.getBlockState(OLD_POS_FARM);
        if (!(existing.getBlock() instanceof TotemPoleBlock existingPole)
                || existingPole.getTotemType() != TotemType.FARM) {
            return;
        }

        if (level.getBlockEntity(OLD_POS_FARM) instanceof TotemPoleBlockEntity pole
                && pole.isSystemPole()
                && pole.getPoleId() == OLD_SYSTEM_ID_FARM) {
            TotemPoleTracker.get(level).unregister(OLD_SYSTEM_ID_FARM);
            level.setBlock(OLD_POS_FARM, Blocks.AIR.defaultBlockState(), 35);
            StardewCraft.LOGGER.info("Removed old main-map farm system totem pole at {}", OLD_POS_FARM);
        }
    }

    @SuppressWarnings("null")
    private static void ensureSystemPole(ServerLevel level, BlockPos pos, TotemType type,
                                         net.neoforged.neoforge.registries.DeferredBlock<Block> blockHolder,
                                         int systemId, Direction facing) {
        String name = type.getDefaultNameKey();
        // 如果已经是该图腾柱，跳过
        BlockState existing = level.getBlockState(pos);
        if (existing.getBlock() instanceof TotemPoleBlock existingPole
                && existingPole.getTotemType() == type) {
            // 确保 BE 存在且为系统柱
            if (level.getBlockEntity(pos) instanceof TotemPoleBlockEntity pole) {
                if (!pole.isSystemPole() || !name.equals(pole.getPoleName())) {
                    pole.initSystemPole(level, systemId, name);
                }
            }
            return;
        }

        // 放置主块
        Block block = blockHolder.get();
        BlockState mainState = block.defaultBlockState()
                .setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
                .setValue(MapDecorStaticBlock.FACING, facing)
                .setValue(TotemPoleBlock.ACTIVATED, true);
        level.setBlock(pos, mainState, 3);

        // 触发 setPlacedBy 以放置扩展块（图腾柱是多格高，自动处理）
        block.setPlacedBy(level, pos, mainState, null, net.minecraft.world.item.ItemStack.EMPTY);

        // 初始化 BlockEntity 为系统柱
        if (level.getBlockEntity(pos) instanceof TotemPoleBlockEntity pole) {
            pole.initSystemPole(level, systemId, name);
        }

        StardewCraft.LOGGER.info("Placed system totem pole: {} at {}", type.getId(), pos);
    }
}
