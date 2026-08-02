package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.mining.LadderProbabilityCalculator;
import com.stardew.craft.mining.MineFloorData;
import com.stardew.craft.mining.MineFloorDataManager;
import com.stardew.craft.network.LadderSyncPacket;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 矿井方块破坏事件处理器
 * 
 * 处理矿井石头计数。
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public class MiningBlockBreakHandler {
    private static final double BOMB_LADDER_CHANCE_MULTIPLIER = 0.2D;
    
    /**
     * 检查方块是否是可计数的"石头节点"。
     *
     * SDV 原版 breakStone() 会让 quartz / earth_crystal / frozen_tear / fire_quartz 也参与
     * stonesLeft 递减 + 梯子概率，但本 mod 里玩家普遍反馈这种"挖个晶簇就把通道挖出来了"很别扭，
     * 而且这些节点本身就稀有 + 可放置装饰，更像奖励物而不是"石头"。
     * 所以在我们的实现里：
     *  - 主石头 + 装饰石（含 Dark 变体）
     *  - 全部矿石（copper / iron / gold / iridium / coal，含 earth/frost/lava/desert 四套主题）
     *  - 宝石矿石（amethyst / aquamarine / diamond / emerald / jade / ruby / topaz）
     *  - 骷髅矿主石头（desert_bedrock / dark_desert_bedrock）+ 装饰石（sulfur_rock / weathered_stone）
     * **不**计入：mineral 节点（quartz / earth_crystal / frozen_tear / fire_quartz）、
     *           mine_barrel（补给箱）、toxic_spore_block、quicksand 等功能方块。
     */
    private static boolean isCountableStone(Block block) {
        // 主石头 + 装饰石（主矿洞，含 Dark 变体）
        if (block == ModBlocks.EARTH_SHALE.get() ||
            block == ModBlocks.DARK_EARTH_SHALE.get() ||
            block == ModBlocks.FROST_GNEISS.get() ||
            block == ModBlocks.DARK_FROST_GNEISS.get() ||
            block == ModBlocks.LAVA_BASALT.get() ||
            block == ModBlocks.DARK_LAVA_BASALT.get() ||
            block == ModBlocks.BANDED_MARBLE.get() ||
            block == ModBlocks.LIMESTONE.get() ||
            block == ModBlocks.MOSSY_SANDSTONE.get() ||
            block == ModBlocks.CRACKED_SLATE.get() ||
            block == ModBlocks.SCORIA.get() ||
            block == ModBlocks.SALT_ROCK.get()) {
            return true;
        }
        // 骷髅矿主石头 + 装饰石
        if (block == ModBlocks.DESERT_BEDROCK.get() ||
            block == ModBlocks.DARK_DESERT_BEDROCK.get() ||
            block == ModBlocks.SULFUR_ROCK.get() ||
            block == ModBlocks.WEATHERED_STONE.get()) {
            return true;
        }
        // 金属矿石（earth / frost / lava 三段）
        if (block == ModBlocks.EARTH_COPPER_ORE.get() ||
            block == ModBlocks.FROST_COPPER_ORE.get() ||
            block == ModBlocks.LAVA_COPPER_ORE.get() ||
            block == ModBlocks.EARTH_IRON_ORE.get() ||
            block == ModBlocks.FROST_IRON_ORE.get() ||
            block == ModBlocks.LAVA_IRON_ORE.get() ||
            block == ModBlocks.EARTH_GOLD_ORE.get() ||
            block == ModBlocks.FROST_GOLD_ORE.get() ||
            block == ModBlocks.LAVA_GOLD_ORE.get() ||
            block == ModBlocks.EARTH_IRIDIUM_ORE.get() ||
            block == ModBlocks.FROST_IRIDIUM_ORE.get() ||
            block == ModBlocks.LAVA_IRIDIUM_ORE.get() ||
            block == ModBlocks.EARTH_COAL_ORE.get() ||
            block == ModBlocks.FROST_COAL_ORE.get() ||
            block == ModBlocks.LAVA_COAL_ORE.get()) {
            return true;
        }
        // 骷髅矿矿石（desert 主题）
        if (block == ModBlocks.DESERT_COPPER_ORE.get() ||
            block == ModBlocks.DESERT_IRON_ORE.get() ||
            block == ModBlocks.DESERT_GOLD_ORE.get() ||
            block == ModBlocks.DESERT_IRIDIUM_ORE.get() ||
            block == ModBlocks.DESERT_COAL_ORE.get()) {
            return true;
        }
        // 宝石矿石
        if (block == ModBlocks.AMETHYST_ORE.get() ||
            block == ModBlocks.AQUAMARINE_ORE.get() ||
            block == ModBlocks.DIAMOND_ORE.get() ||
            block == ModBlocks.EMERALD_ORE.get() ||
            block == ModBlocks.JADE_ORE.get() ||
            block == ModBlocks.RUBY_ORE.get() ||
            block == ModBlocks.TOPAZ_ORE.get()) {
            return true;
        }
        // 注：mineral 节点（quartz / earth_crystal / frozen_tear / fire_quartz）刻意不计入，
        // 它们是稀有装饰节点，不应触发 stonesLeft 递减或梯子概率。
        return false;
    }
    
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        
        // 检查是否在矿井维度
        if (!isMiningDimension(serverLevel)) {
            return;
        }
        
        // 检查是否是玩家破坏
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        handleStoneBreak(serverLevel, player, pos, state);
    }

    /** 炸弹破坏入口：按 3D/2D 覆盖体积比归一化整次石头事件。 */
    @SuppressWarnings("null")
    public static void handleStoneBreakFromBomb(ServerLevel serverLevel, ServerPlayer player, BlockPos pos, BlockState state) {
        // A 3D bomb reaches roughly five times as many blocks as the original 2D
        // blast. Sample the whole stone event so both stonesLeft and ladder rolls
        // use the same normalized probability; scaling only the ladder roll made
        // bombs exhaust stonesLeft five times too quickly and force a ladder.
        if (serverLevel.getRandom().nextDouble() < BOMB_LADDER_CHANCE_MULTIPLIER) {
            handleStoneBreak(serverLevel, player, pos, state);
        }
    }

    @SuppressWarnings("null")
    public static void handleStoneBreak(
            ServerLevel serverLevel,
            ServerPlayer player,
            BlockPos pos,
            BlockState state
    ) {
        Block block = state.getBlock();

        // 检查是否是可计数的主石头
        if (!isCountableStone(block)) {
            return;
        }

        // 获取楼层数据
        int floorNumber = getFloorNumber(pos);
        MineFloorDataManager manager = MineFloorDataManager.get(serverLevel);
        MineFloorData floorData = manager.getFloorData(floorNumber);

        if (floorData == null) {
            // 楼层数据不存在，不处理（可能是玩家还未正式进入楼层）
            return;
        }

        // 减少stonesLeft计数
        floorData.decrementStone();
        manager.setFloorData(floorNumber, floorData);

        // 动态楼梯生成：每次挖石后判断概率
        if (!floorData.hasLadderFound()) {
            double ladderChance = LadderProbabilityCalculator.calculateProbability(
                    floorData.getStonesLeft(),
                    PlayerStardewDataAPI.getLuckBuffLevel(player),
                    PlayerStardewDataAPI.getDailyLuck(player),
                    floorData.getEnemyCount(),
                    player.hasEffect(com.stardew.craft.effect.ModMobEffects.DWARF_STATUE_1));
            boolean shouldSpawn = serverLevel.getRandom().nextDouble() < ladderChance;
            if (shouldSpawn) {
                // 先标记数据，避免重复生成
                floorData.setLadderFound(true);
                floorData.setLadderPos(pos);
                manager.setFloorData(floorNumber, floorData);

                StardewCraft.LOGGER.info("[MINE] Ladder spawned at {} on floor {} by {}",
                        pos, floorNumber, player.getName().getString());

                // 延迟 1 tick 放置楼梯方块：BreakEvent 在方块移除之前触发，
                // 如果在这里直接 setBlock，随后的破坏逻辑会把它覆盖成空气。
                final BlockPos ladderPos = pos.immutable();
                // SDV: 骷髅矿（floor > 120）非必杀层 20% 概率生成竖井
                final boolean isShaft = floorNumber > 120
                        && !floorData.isMonsterArea()
                        && serverLevel.getRandom().nextDouble() < 0.2;
                serverLevel.getServer().execute(() -> {
                    net.minecraft.world.level.block.state.BlockState ladderState =
                            ModBlocks.MINE_LADDER.get().defaultBlockState()
                                    .setValue(com.stardew.craft.block.mine.MineLadderBlock.SHAFT, isShaft);
                    serverLevel.setBlock(ladderPos, ladderState, 3);

                    // SDV 原版：发现楼梯播放 "hoeHit" 音效
                    serverLevel.playSound(null, ladderPos, ModSounds.HOE_HIT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);

                    // SDV 原版：显示全局消息提示楼梯已出现（仿 MineShaft.cs:9484）
                    notifyLadderFoundToPlayers(serverLevel, floorNumber, ladderPos, isShaft);
                });
            }
        }
    }

    public static void tryCreateLadderFromMonsterDrop(ServerLevel serverLevel, ServerPlayer player, BlockPos pos) {
        if (!isMiningDimension(serverLevel)) {
            return;
        }

        int floorNumber = getFloorNumber(pos);
        MineFloorDataManager manager = MineFloorDataManager.get(serverLevel);
        MineFloorData floorData = manager.getFloorData(floorNumber);
        if (floorData == null || floorData.hasLadderFound()) {
            return;
        }

        floorData.decrementEnemy();
        manager.setFloorData(floorNumber, floorData);

        boolean clearedMonsterArea = floorData.isMonsterArea() && floorData.getEnemyCount() <= 0;
        double extraLadderChance = player.hasEffect(com.stardew.craft.effect.ModMobEffects.DWARF_STATUE_1) ? 0.07D : 0.0D;
        if (!clearedMonsterArea
                && serverLevel.getRandom().nextDouble() >= 0.15D + extraLadderChance) {
            return;
        }

        BlockPos ladderPos = pos.immutable();
        floorData.setLadderFound(true);
        floorData.setLadderPos(ladderPos);
        manager.setFloorData(floorNumber, floorData);

        serverLevel.getServer().execute(() -> {
            net.minecraft.world.level.block.state.BlockState ladderState =
                    ModBlocks.MINE_LADDER.get().defaultBlockState()
                            .setValue(com.stardew.craft.block.mine.MineLadderBlock.SHAFT, false);
            serverLevel.setBlock(ladderPos, ladderState, 3);
            serverLevel.playSound(null, ladderPos, ModSounds.HOE_HIT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            notifyLadderFoundToPlayers(serverLevel, floorNumber, ladderPos, false);
        });
    }

    public static void syncLadderStateForPlayer(ServerPlayer player, int floorNumber) {
        if (!(player.level() instanceof ServerLevel serverLevel) || !isMiningDimension(serverLevel)) {
            return;
        }
        MineFloorDataManager manager = MineFloorDataManager.get(serverLevel);
        MineFloorData floorData = manager.getFloorData(floorNumber);
        if (floorData == null || !floorData.hasLadderFound() || floorData.getLadderPos() == null) {
            PacketDistributor.sendToPlayer(player, new LadderSyncPacket(floorNumber, false, BlockPos.ZERO, false));
            return;
        }

        BlockPos ladderPos = floorData.getLadderPos();
        BlockState state = serverLevel.getBlockState(ladderPos);
        if (!state.is(ModBlocks.MINE_LADDER.get())) {
            floorData.setLadderFound(false);
            floorData.setLadderPos(null);
            manager.setFloorData(floorNumber, floorData);
            PacketDistributor.sendToPlayer(player, new LadderSyncPacket(floorNumber, false, BlockPos.ZERO, false));
            return;
        }

        boolean isShaft = state.hasProperty(com.stardew.craft.block.mine.MineLadderBlock.SHAFT)
                && state.getValue(com.stardew.craft.block.mine.MineLadderBlock.SHAFT);
        PacketDistributor.sendToPlayer(player, new LadderSyncPacket(floorNumber, true, ladderPos, isShaft));
    }

    private static void notifyLadderFoundToPlayers(ServerLevel serverLevel, int floorNumber, BlockPos ladderPos, boolean isShaft) {
        for (ServerPlayer p : serverLevel.players()) {
            if (!isPlayerOnMiningFloor(p, floorNumber)) {
                continue;
            }
            PacketDistributor.sendToPlayer(p, new LadderSyncPacket(floorNumber, true, ladderPos, isShaft));
            p.displayClientMessage(
                    Component.translatable(isShaft
                            ? "message.stardewcraft.shaft_found"
                            : "message.stardewcraft.ladder_found"), false);
        }
    }

    private static boolean isPlayerOnMiningFloor(ServerPlayer player, int floorNumber) {
        if (!ModMiningDimensions.STARDEW_MINING.equals(player.level().dimension())) {
            return false;
        }
        return com.stardew.craft.mining.MiningDataManager.getPlayerData(player).getCurrentFloor() == floorNumber;
    }
    
    /**
     * 检查是否在矿井维度
     * TODO: 实现正确的维度检查
     */
    private static boolean isMiningDimension(ServerLevel level) {
        return level.dimension() == ModMiningDimensions.STARDEW_MINING;
    }
    
    /**
     * 从坐标计算楼层编号
     * 每层中心在 (0, 64, floor * FLOOR_SPACING)
     */
    private static int getFloorNumber(BlockPos pos) {
        return Math.round(pos.getZ() / (float) com.stardew.craft.mining.MiningCoordinates.FLOOR_SPACING);
    }
}
