package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.LuckyPurpleShortsBlock;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.monster.LuckyPurpleShortsMonsterEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class LuckyPurpleShortsWorldEvents {
    public static final String QUEST_ID = "102";
    public static final String MARNIE_PICKED_FLAG = "foundMayorShorts";
    public static final String LEWIS_BASEMENT_EXIT_TARGET = "lewis_basement_exit";

    private static final BlockPos MARNIE_SHORTS_POS = new BlockPos(-86, 34, 12);
    private static final BlockPos BASEMENT_SHORTS_POS = new BlockPos(81, 44, 32);
    private static final BlockPos BASEMENT_EXIT_PORTAL_POS = new BlockPos(68, 44, 20);
    private static final AABB LEWIS_BASEMENT_BOUNDS = new AABB(64, 43, 19, 84, 49, 36);
    private static final Set<UUID> BASEMENT_COLLECTED_THIS_VISIT = ConcurrentHashMap.newKeySet();
    private static long lastBasementCleanupTick = -1L;

    private LuckyPurpleShortsWorldEvents() {
    }

    public static boolean isSpecialShortsPosition(Level level, BlockPos pos) {
        return level.dimension() == ModDimensions.STARDEW_VALLEY
                && (MARNIE_SHORTS_POS.equals(pos) || BASEMENT_SHORTS_POS.equals(pos));
    }

    public static InteractionResult useSpecialShorts(Level level, BlockPos pos, Player rawPlayer) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(rawPlayer instanceof ServerPlayer player)) {
            return InteractionResult.CONSUME;
        }
        ensurePlaced(player.serverLevel());
        if (MARNIE_SHORTS_POS.equals(pos)) {
            return pickUpMarnieShorts(player);
        }
        if (BASEMENT_SHORTS_POS.equals(pos)) {
            return pickUpBasementShorts(player);
        }
        return InteractionResult.PASS;
    }

    public static void onEnteredLewisBasement(ServerPlayer player) {
        BASEMENT_COLLECTED_THIS_VISIT.remove(player.getUUID());
        syncVisibility(player);
    }

    public static void syncVisibility(ServerPlayer player) {
        if (player.serverLevel().dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        ensurePlaced(player.serverLevel());
        sendBlockFor(player, MARNIE_SHORTS_POS, canSeeMarnieShorts(player));
        sendBlockFor(player, BASEMENT_SHORTS_POS, canSeeBasementShorts(player));
    }

    public static void ensurePlaced(ServerLevel level) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        ensureShortsBlock(level, MARNIE_SHORTS_POS);
        ensureShortsBlock(level, BASEMENT_SHORTS_POS);
        ensureBasementExitPortal(level);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ensurePlaced(level);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncVisibility(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncVisibility(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncVisibility(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 40 != 0) {
            return;
        }
        syncVisibility(player);
        cleanupBasementMonstersIfEmpty(player.serverLevel());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!isSpecialShortsPosition(player.level(), event.getPos())) {
            return;
        }
        event.setCanceled(true);
        syncVisibility(player);
    }

    private static InteractionResult pickUpMarnieShorts(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data == null || !canSeeMarnieShorts(player)) {
            syncVisibility(player);
            return InteractionResult.CONSUME;
        }

        giveShorts(player);
        data.addMailFlag(MARNIE_PICKED_FLAG);
        PlayerDataEventHandler.syncPlayerData(player, data);
        syncVisibility(player);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult pickUpBasementShorts(ServerPlayer player) {
        if (!canSeeBasementShorts(player)) {
            syncVisibility(player);
            return InteractionResult.CONSUME;
        }

        giveShorts(player);
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data != null) {
            data.recordBasementShortsStolen();
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        BASEMENT_COLLECTED_THIS_VISIT.add(player.getUUID());
        spawnBasementShortsMonster(player);
        syncVisibility(player);
        return InteractionResult.CONSUME;
    }

    private static void spawnBasementShortsMonster(ServerPlayer player) {
        LuckyPurpleShortsMonsterEntity monster = LuckyPurpleShortsMonsterEntity.create(player.serverLevel(), player);
        monster.moveTo(
                BASEMENT_SHORTS_POS.getX() + 0.5D,
                BASEMENT_SHORTS_POS.getY() + 0.05D,
                BASEMENT_SHORTS_POS.getZ() + 0.5D,
                player.getYRot(),
                0.0F
        );
        player.serverLevel().addFreshEntity(monster);
    }

    private static void giveShorts(ServerPlayer player) {
        ItemStack shorts = new ItemStack(ModItems.LUCKY_PURPLE_SHORTS.get());
        if (!player.getInventory().add(shorts)) {
            player.drop(shorts, false);
        }
        ItemPickupHudPacket.sendTo(player, shorts, 1, false);
        com.stardew.craft.quest.StardewQuestEvents.fireItemReceived(player, "stardewcraft:lucky_purple_shorts", 1);
    }

    private static boolean canSeeMarnieShorts(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data == null || data.hasMailFlag(MARNIE_PICKED_FLAG)) {
            return false;
        }
        var questManager = data.getQuestManager();
        return questManager.hasQuest(QUEST_ID) && !questManager.isQuestCompleted(QUEST_ID);
    }

    private static boolean canSeeBasementShorts(ServerPlayer player) {
        return !BASEMENT_COLLECTED_THIS_VISIT.contains(player.getUUID());
    }

    private static void ensureShortsBlock(ServerLevel level, BlockPos pos) {
        Block block = ModBlocks.LUCKY_PURPLE_SHORTS.get();
        BlockState wanted = block.defaultBlockState().setValue(LuckyPurpleShortsBlock.FACING, Direction.NORTH);
        if (!level.getBlockState(pos).is(block)) {
            level.setBlock(pos, wanted, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static void ensureBasementExitPortal(ServerLevel level) {
        for (int dy = 0; dy < 2; dy++) {
            BlockPos pos = BASEMENT_EXIT_PORTAL_POS.above(dy);
            if (!level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())) {
                level.setBlock(pos, ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(), Block.UPDATE_ALL);
            }
            if (level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity portal) {
                portal.configure(LEWIS_BASEMENT_EXIT_TARGET, "sdv_portal_marker:lewis_basement_exit");
            }
        }
    }

    private static void cleanupBasementMonstersIfEmpty(ServerLevel level) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        long gameTime = level.getGameTime();
        if (lastBasementCleanupTick == gameTime) {
            return;
        }
        lastBasementCleanupTick = gameTime;

        boolean hasPlayerInBasement = level.players().stream()
                .anyMatch(player -> !player.isSpectator() && LEWIS_BASEMENT_BOUNDS.contains(player.position()));
        if (hasPlayerInBasement) {
            return;
        }

        var monsters = level.getEntities(
                net.minecraft.world.level.entity.EntityTypeTest.forClass(LuckyPurpleShortsMonsterEntity.class),
                monster -> !monster.isSpectator());
        for (LuckyPurpleShortsMonsterEntity monster : monsters) {
            monster.discard();
        }
    }

    private static void sendBlockFor(ServerPlayer player, BlockPos pos, boolean visible) {
        BlockState state = visible ? player.serverLevel().getBlockState(pos) : Blocks.AIR.defaultBlockState();
        player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
    }
}
