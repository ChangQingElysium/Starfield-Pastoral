package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.farm.StardewFarmCaveDailyRegistry;
import com.stardew.craft.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.UUID;

/** Farm cave choice API; installation and mode changes run through the readiness queue. */
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
            com.stardew.craft.interior.FarmCaveRuntime.request(level,farm);
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

    public static void clearCaveFruits(ServerLevel level, BlockPos caveOrigin) {
        // 复用 DailyService 的清理逻辑通过放置空水果列表的方式不太直观，
        // 这里直接对水果层做一次快速扫描。
        int w = com.stardew.craft.interior.FarmCaveLayout.WIDTH;
        int l = com.stardew.craft.interior.FarmCaveLayout.LENGTH;
        java.util.Set<Block> fruits = new java.util.LinkedHashSet<>(java.util.List.of(
                ModBlocks.FORAGE_SALMONBERRY.get(), ModBlocks.FORAGE_SPICE_BERRY.get(),
                ModBlocks.FORAGE_WILD_PLUM.get(), ModBlocks.FORAGE_BLACKBERRY.get(),
                ModBlocks.FORAGE_APPLE.get(), ModBlocks.FORAGE_APRICOT.get(),
                ModBlocks.FORAGE_ORANGE.get(), ModBlocks.FORAGE_PEACH.get(),
                ModBlocks.FORAGE_POMEGRANATE.get(), ModBlocks.FORAGE_MANGO.get()
        ));
        fruits.addAll(StardewFarmCaveDailyRegistry.managedFruitBlocks());
        for (int lx = 0; lx < w; lx++) {
            for (int lz = 0; lz < l; lz++) {
                BlockPos p = caveOrigin.offset(lx, com.stardew.craft.interior.FarmCaveLayout.FLOOR, lz);
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
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
    }

    @Nullable
    private static FarmInstance getFarm(UUID playerUUID) {
        return FarmInstanceRegistry.get().getFarmForPlayer(playerUUID);
    }
}
