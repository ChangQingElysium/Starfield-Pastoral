package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.mining.StructureLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * /stardew debug spawn_wizard_tower
 * 在玩家当前位置放置一座完整的巫师塔（含入口传送方块）。
 */
@SuppressWarnings("null")
public final class StructureDebugCommand {

    private static final String WIZARD_TOWER_SCHEM = "data/stardewcraft/structures/wizard_tower_exterior.schem";
    private static final String MARKER_TAG = "sdv_portal_marker:wizard_tower_overworld";
    private static final String TARGET_TAG = "sdv_portal_target:wizard_tower_overworld_enter";
    private static final int PORTAL_HEIGHT = 2;
    private static final BlockPos WIZARD_TOWER_PORTAL_OFFSET = new BlockPos(6, 0, 3);

    private StructureDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardew")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("debug")
                .then(Commands.literal("spawn_wizard_tower")
                    .executes(StructureDebugCommand::spawnWizardTower))));
    }

    private static int spawnWizardTower(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("stardewcraft.command.structure.player_only"));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos placePos = player.blockPosition().relative(player.getDirection(), 3);

        // 放置结构
        boolean success = StructureLoader.loadAndPlaceWithResult(level, WIZARD_TOWER_SCHEM, placePos);
        if (!success) {
            source.sendFailure(Component.translatable("stardewcraft.command.structure.wizard_tower_failed"));
            return 0;
        }

        // 确保巫师塔内部亚空间已加载（传送需要）
        ServerLevel stardewLevel = player.server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
        if (stardewLevel != null) {
            InteriorSubspaceManager.ensureLoaded(stardewLevel, "wizard_tower");
        }

        // 调试命令直接按结构固定入口偏移放置 portal；搜索到门时优先对齐门口，
        // 搜索失败则回退到设计稿里的固定入口坐标，避免再出现“未找到门”。
        BlockPos portalPos = resolveWizardTowerPortalPos(level, placePos);
        InteriorSubspaceManager.placePortalTriggerArea(
            level, portalPos, PORTAL_HEIGHT, 1, 1,
            MARKER_TAG, TARGET_TAG
        );
        source.sendSuccess(() -> Component.translatable(
            "stardewcraft.command.structure.wizard_tower_success",
            placePos.toShortString(), portalPos.toShortString()),
            true);

        StardewCraft.LOGGER.info("[DEBUG] Player {} spawned wizard tower at {}", player.getName().getString(), placePos);
        return 1;
    }

    /**
     * 在给定区域内搜索最低的深色橡木门（下半部分）。
     */
    private static BlockPos resolveWizardTowerPortalPos(ServerLevel level, BlockPos structureOrigin) {
        BlockPos doorPos = findLowestDoor(level, structureOrigin, 32, 32, 32);
        if (doorPos != null) {
            return doorPos;
        }
        return structureOrigin.offset(WIZARD_TOWER_PORTAL_OFFSET);
    }

    private static BlockPos findLowestDoor(ServerLevel level, BlockPos origin, int sx, int sy, int sz) {
        BlockPos lowestDoor = null;
        int lowestY = Integer.MAX_VALUE;

        for (int x = origin.getX(); x < origin.getX() + sx; x++) {
            for (int z = origin.getZ(); z < origin.getZ() + sz; z++) {
                for (int y = origin.getY(); y < origin.getY() + sy; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof DoorBlock
                        && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                        && y < lowestY) {
                        lowestY = y;
                        lowestDoor = pos;
                    }
                }
            }
        }
        return lowestDoor;
    }
}
