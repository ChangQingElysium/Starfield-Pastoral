package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.interior.PlayerInteriorAllocator;
import com.stardew.craft.manager.FarmCaveDailyService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 农场洞穴选择对外 API。未来剧情系统（Demetrius 事件等）直接调这里。
 *
 * <p>当前 Step 1 实现只负责：读写 {@link FarmInstance#caveChoice} + 持久化。
 * Step 2 / Step 5 会接入实际副作用：
 * <ul>
 *   <li>切到 {@link FarmCaveChoice#MUSHROOMS} 时在洞内放 6 个蘑菇培养盆；</li>
 *   <li>切到 {@link FarmCaveChoice#FRUIT_BATS} / {@link FarmCaveChoice#NONE} 时清空蘑菇盆；</li>
 *   <li>切到 {@link FarmCaveChoice#NONE} 时清理洞内已生成的水果 forage。</li>
 * </ul>
 */
public final class FarmCaveAPI {

    private FarmCaveAPI() {}

    /** 读取指定玩家所在农场的洞穴选择。玩家不属于任何农场则返回 {@link FarmCaveChoice#NONE}。 */
    public static FarmCaveChoice getCaveChoice(ServerPlayer player) {
        FarmInstance farm = getFarm(player.getUUID());
        return farm != null ? farm.getCaveChoice() : FarmCaveChoice.NONE;
    }

    /** 读取指定 owner 的洞穴选择。农场不存在则返回 {@link FarmCaveChoice#NONE}。 */
    public static FarmCaveChoice getCaveChoice(UUID ownerUUID) {
        FarmInstance farm = FarmInstanceRegistry.get().getFarm(ownerUUID);
        return farm != null ? farm.getCaveChoice() : FarmCaveChoice.NONE;
    }

    /**
     * 设置玩家所在农场的洞穴选择。共享农场成员与 owner 等价。
     *
     * @return true 成功；false 玩家不属于任何农场
     */
    public static boolean setCaveChoice(ServerPlayer player, FarmCaveChoice choice) {
        FarmInstanceRegistry reg = FarmInstanceRegistry.get();
        FarmInstance farm = reg.getFarmForPlayer(player.getUUID());
        if (farm == null) return false;
        return applyChoice(farm, choice, reg);
    }

    /** 管理员路径：直接按 owner 设置。 */
    public static boolean setCaveChoice(UUID ownerUUID, FarmCaveChoice choice) {
        FarmInstanceRegistry reg = FarmInstanceRegistry.get();
        FarmInstance farm = reg.getFarm(ownerUUID);
        if (farm == null) return false;
        return applyChoice(farm, choice, reg);
    }

    private static boolean applyChoice(FarmInstance farm, FarmCaveChoice choice, FarmInstanceRegistry reg) {
        if (choice == null) choice = FarmCaveChoice.NONE;
        FarmCaveChoice old = farm.getCaveChoice();
        if (old == choice) return true;
        farm.setCaveChoice(choice);
        reg.setDirty();

        // 洞穴内副作用（需要 ServerLevel）
        ServerLevel level = resolveStardewLevel();
        if (level != null) {
            PlayerInteriorAllocator alloc = PlayerInteriorAllocator.get(level);
            UUID owner = farm.getOwnerUUID();
            if (alloc.isCavePlaced(owner)) {
                BlockPos caveOrigin = alloc.getCaveOrigin(owner);
                // 切到蘑菇：放 6 个蘑菇盆；离开蘑菇：拆除
                if (choice == FarmCaveChoice.MUSHROOMS) {
                    placeMushroomBoxes(level, caveOrigin);
                } else if (old == FarmCaveChoice.MUSHROOMS) {
                    removeMushroomBoxes(level, caveOrigin);
                }
                // 离开 FRUIT_BATS（无论切到 NONE 还是 MUSHROOMS）都清除残留水果
                if (old == FarmCaveChoice.FRUIT_BATS) {
                    clearCaveFruits(level, caveOrigin);
                }
            }
            // 广播给农场所有在线成员
            broadcastChoice(level, farm, choice);
        }

        StardewCraft.LOGGER.info("FarmCave: owner={} {} -> {}",
                farm.getOwnerUUID(), old.getName(), choice.getName());
        return true;
    }

    private static void broadcastChoice(ServerLevel level, FarmInstance farm, FarmCaveChoice choice) {
        var server = level.getServer();
        net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.translatable(
                "stardewcraft.farm_cave.choice_broadcast",
                com.stardew.craft.player.PlayerDisplayName.get(server, farm.getOwnerUUID()),
                net.minecraft.network.chat.Component.translatable("stardewcraft.farm_cave.choice." + choice.getName()));
        for (UUID uuid : farm.getAllFarmers()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) sp.sendSystemMessage(msg);
        }
    }

    private static void placeMushroomBoxes(ServerLevel level, BlockPos caveOrigin) {
        Block box = ModBlocks.MUSHROOM_BOX.get();
        for (BlockPos off : FarmCaveDailyService.MUSHROOM_BOX_OFFSETS) {
            BlockPos p = caveOrigin.offset(off);
            if (!level.getBlockState(p).isAir()) continue;
            level.setBlock(p, box.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void removeMushroomBoxes(ServerLevel level, BlockPos caveOrigin) {
        Block box = ModBlocks.MUSHROOM_BOX.get();
        for (BlockPos off : FarmCaveDailyService.MUSHROOM_BOX_OFFSETS) {
            BlockPos p = caveOrigin.offset(off);
            if (level.getBlockState(p).is(box)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void clearCaveFruits(ServerLevel level, BlockPos caveOrigin) {
        // 复用 DailyService 的清理逻辑通过放置空水果列表的方式不太直观，
        // 这里直接对水果层做一次快速扫描。
        int w = com.stardew.craft.interior.InteriorSubspaceManager.FARM_CAVE_SCHEM_W;
        int l = com.stardew.craft.interior.InteriorSubspaceManager.FARM_CAVE_SCHEM_L;
        Block[] fruits = new Block[] {
                ModBlocks.FORAGE_SALMONBERRY.get(), ModBlocks.FORAGE_SPICE_BERRY.get(),
                ModBlocks.FORAGE_WILD_PLUM.get(), ModBlocks.FORAGE_BLACKBERRY.get(),
                ModBlocks.FORAGE_APPLE.get(), ModBlocks.FORAGE_APRICOT.get(),
                ModBlocks.FORAGE_ORANGE.get(), ModBlocks.FORAGE_PEACH.get(),
                ModBlocks.FORAGE_POMEGRANATE.get(), ModBlocks.FORAGE_MANGO.get()
        };
        for (int lx = 0; lx < w; lx++) {
            for (int lz = 0; lz < l; lz++) {
                BlockPos p = caveOrigin.offset(lx, 1, lz);
                var st = level.getBlockState(p);
                for (Block f : fruits) {
                    if (st.is(f)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        break;
                    }
                }
            }
        }
    }

    @Nullable
    private static ServerLevel resolveStardewLevel() {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
    }

    @Nullable
    private static FarmInstance getFarm(UUID playerUUID) {
        return FarmInstanceRegistry.get().getFarmForPlayer(playerUUID);
    }
}
