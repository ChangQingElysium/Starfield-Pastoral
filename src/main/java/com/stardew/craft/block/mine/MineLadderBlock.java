package com.stardew.craft.block.mine;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.mining.MiningCoordinates;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.mining.MiningPlayerData;
import com.stardew.craft.network.MiningFloorSyncPacket;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 矿井梯子方块 - 通往下一层的传送点
 * 
 * 功能：
 * - 右键交互传送到下一层
 * - 只能在矿井维度使用
 * - 不可破坏（硬度很高）
 * - 紫色粒子效果（普通梯子）/ 红色粒子效果（竖井/shaft）
 * - SHAFT=true 时：确认对话 → 跳多层 + 伤害（SDV 原版 Skull Cavern 机制）
 */
public class MineLadderBlock extends Block {
    
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** 是否为竖井（骷髅矿 20% 概率）。竖井需确认对话，跳 3-15 层并造成伤害。 */
    public static final BooleanProperty SHAFT = BooleanProperty.create("shaft");
    private static final VoxelShape[] SHAPES = ModelVoxelShapeCache.horizontalShapes("stardewcraft:block/ladder", Direction.NORTH);

    @SuppressWarnings("null")
    public MineLadderBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SHAFT, false));
    }

    @Override
    protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAFT);
    }

    @SuppressWarnings("null")
    @Override
    public BlockState getStateForPlacement(@SuppressWarnings("null") BlockPlaceContext context) {
        // 仅允许在矿井维度放置
        if (!ModMiningDimensions.STARDEW_MINING.equals(context.getLevel().dimension())) {
            return null;
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @SuppressWarnings("null")
    @Override
    public BlockState rotate(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("null")
    @Override
    public BlockState mirror(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @SuppressWarnings("null")
    @Override
    public VoxelShape getShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        return SHAPES[ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING))];
    }

    @SuppressWarnings("null")
    @Override
    public VoxelShape getCollisionShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        return SHAPES[ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING))];
    }

    /**
     * 禁止生存模式破坏矿井梯子
     */
    @SuppressWarnings("null")
    @Override
    public boolean onDestroyedByPlayer(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, boolean willHarvest, @SuppressWarnings("null") net.minecraft.world.level.material.FluidState fluid) {
        // 只有创造模式才能破坏
        if (!player.isCreative()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.stardewcraft.cannot_break_ladder"), true);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /**
     * 右键交互 - 普通梯子直接传送，竖井弹确认对话
     */
    @SuppressWarnings("null")
    @Override
    protected InteractionResult useWithoutItem(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, @SuppressWarnings("null") BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!ModMiningDimensions.STARDEW_MINING.equals(level.dimension())) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        boolean isShaft = state.getValue(SHAFT);
        if (isShaft) {
            // 竖井：发送确认对话包到客户端
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                serverPlayer,
                new com.stardew.craft.network.ShaftConfirmPacket(pos)
            );
            return InteractionResult.SUCCESS;
        }

        // 普通梯子：直接下一层
        descendOneFloor(serverPlayer, (ServerLevel) level, pos);
        return InteractionResult.SUCCESS;
    }

    /**
     * 兜底：若客户端握有物品，MC 会走 useItemOn 路径。默认返回 PASS_TO_DEFAULT_BLOCK_INTERACTION
     * 应该已经回落到 useWithoutItem，但有些 Item（如 BlockItem）会吞掉交互。显式转发确保可靠。
     */
    @SuppressWarnings("null")
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            @SuppressWarnings("null") net.minecraft.world.item.ItemStack stack,
            @SuppressWarnings("null") BlockState state,
            @SuppressWarnings("null") Level level,
            @SuppressWarnings("null") BlockPos pos,
            @SuppressWarnings("null") Player player,
            @SuppressWarnings("null") InteractionHand hand,
            @SuppressWarnings("null") BlockHitResult hit) {
        InteractionResult r = useWithoutItem(state, level, pos, player, hit);
        return switch (r) {
            case SUCCESS -> net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
            case CONSUME, CONSUME_PARTIAL -> net.minecraft.world.ItemInteractionResult.CONSUME;
            case FAIL -> net.minecraft.world.ItemInteractionResult.FAIL;
            default -> net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }

    /**
     * 普通梯子下降 1 层
     */
    public static void descendOneFloor(ServerPlayer serverPlayer, ServerLevel level, BlockPos pos) {
        MiningPlayerData playerData = MiningDataManager.getPlayerData(serverPlayer);
        int currentFloor = playerData.getCurrentFloor();

        // 禁止从普通矿井 120 层用梯子直接下到骷髅矿（121+）
        // 骷髅矿入口必须走沙漠传送门（handleDesertMineEntrance）
        // 注意：骷髅矿（121+）梯子允许继续下降，因此只拦截 currentFloor == 120
        if (currentFloor == 120) {
            serverPlayer.displayClientMessage(
                Component.translatable("message.stardewcraft.mine_bottom_reached"), true);
            return;
        }

        int previousMaxFloor = playerData.getMaxFloorReached();
        int nextFloor = currentFloor + 1;

        StardewCraft.LOGGER.info("[MINE] Player {} descending from floor {} to floor {}", 
            serverPlayer.getName().getString(), currentFloor, nextFloor);

        com.stardew.craft.mining.MineFloorGenerator.generateFloor(level, nextFloor);

        playerData.setCurrentFloor(nextFloor);
        MiningDataManager.savePlayerData(serverPlayer, playerData);
        PlayerStardewDataAPI.applyStardewCraftingConditionUnlocks(serverPlayer);
        com.stardew.craft.quest.StardewQuestEvents.fireMineFloorReached(serverPlayer, nextFloor);

        level.playSound(null, pos, com.stardew.craft.sound.ModSounds.STAIRS_DOWN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);

        MiningCoordinates.teleportPlayerToFloor(serverPlayer, level, nextFloor);

        // 骷髅矿会话追踪
        if (nextFloor > 120) {
            com.stardew.craft.mining.SkullCavernSessionManager.onPlayerEnter(serverPlayer);
            com.stardew.craft.mining.SkullCavernSessionManager.updateDeepestFloor(nextFloor);
            com.stardew.craft.festival.desert.DesertFestivalMineService.recordFloorReached(serverPlayer, currentFloor, nextFloor, false);
            com.stardew.craft.secretnote.SecretNoteStoryTriggerService
                    .grantTemporaryQiCaveRewardIfEligible(serverPlayer, nextFloor);
        }

        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            serverPlayer, new MiningFloorSyncPacket(nextFloor));
        com.stardew.craft.event.MiningBlockBreakHandler.syncLadderStateForPlayer(serverPlayer, nextFloor);

        final int floor = nextFloor;
        level.getServer().tell(new net.minecraft.server.TickTask(
            level.getServer().getTickCount() + 3,
            () -> com.stardew.craft.mining.MineFloorGenerator.forceClientLightRefresh(level, floor)
        ));

        if (nextFloor <= 120 && nextFloor % 5 == 0 && nextFloor > previousMaxFloor) {
            level.getServer().tell(new net.minecraft.server.TickTask(
                level.getServer().getTickCount() + 30,
                () -> {
                    if (serverPlayer.isAlive() && serverPlayer.level().dimension() == ModMiningDimensions.STARDEW_MINING) {
                        level.playSound(null, serverPlayer.blockPosition(),
                            com.stardew.craft.sound.ModSounds.CRYSTAL.get(),
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                }
            ));
        }
    }

    /**
     * 竖井跳跃 — 跳 3-15 层 + 伤害（SDV enterMineShaft 精确还原）
     * 由 ShaftJumpPacket（C→S）触发。
     */
    public static void enterShaft(ServerPlayer serverPlayer, ServerLevel level, BlockPos shaftPos) {
        MiningPlayerData playerData = MiningDataManager.getPlayerData(serverPlayer);
        int currentFloor = playerData.getCurrentFloor();

        // SDV 原版：3-8 层，10% 翻倍
        java.util.Random rng = new java.util.Random(
                currentFloor * 31L + level.getServer().overworld().getGameTime());
        int levelsDown = 3 + rng.nextInt(6); // 3~8
        if (rng.nextDouble() < 0.1) {
            levelsDown = levelsDown * 2 - 1; // 5~15
        }

        int targetFloor = currentFloor + levelsDown;

        StardewCraft.LOGGER.info("[MINE] Player {} jumping shaft from floor {} down {} levels to floor {}",
                serverPlayer.getName().getString(), currentFloor, levelsDown, targetFloor);

        // 生成目标层
        com.stardew.craft.mining.MineFloorGenerator.generateFloor(level, targetFloor);

        // 更新数据
        playerData.setCurrentFloor(targetFloor);
        MiningDataManager.savePlayerData(serverPlayer, playerData);
        PlayerStardewDataAPI.applyStardewCraftingConditionUnlocks(serverPlayer);
        com.stardew.craft.quest.StardewQuestEvents.fireMineFloorReached(serverPlayer, targetFloor);

        // SDV 原版音效：fallDown — 暂用原版坠落音
        level.playSound(null, shaftPos, net.minecraft.sounds.SoundEvents.PLAYER_BIG_FALL,
                SoundSource.PLAYERS, 1.0F, 0.8F);

        // 传送到目标层
        MiningCoordinates.teleportPlayerToFloor(serverPlayer, level, targetFloor);

        // 骷髅矿会话追踪
        com.stardew.craft.mining.SkullCavernSessionManager.onPlayerEnter(serverPlayer);
        com.stardew.craft.mining.SkullCavernSessionManager.updateDeepestFloor(targetFloor);
        com.stardew.craft.festival.desert.DesertFestivalMineService.recordFloorReached(serverPlayer, currentFloor, targetFloor, true);
        com.stardew.craft.secretnote.SecretNoteStoryTriggerService
                .grantTemporaryQiCaveRewardIfEligible(serverPlayer, targetFloor);

        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                serverPlayer, new MiningFloorSyncPacket(targetFloor));
        com.stardew.craft.event.MiningBlockBreakHandler.syncLadderStateForPlayer(serverPlayer, targetFloor);

        // SD 体力伤害：levelsDown × 3
        int damage = levelsDown * 3;
        com.stardew.craft.player.PlayerStardewData sdData =
                com.stardew.craft.player.PlayerDataManager.get()
                        .getOrCreateData(serverPlayer.getUUID());
        int newHealth = Math.max(1, sdData.getHealth() - damage);
        sdData.setHealth(newHealth);
        com.stardew.craft.player.PlayerDataManager.get().setDirty();
        com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(serverPlayer, sdData);

        // 落地消息
        String msgKey = levelsDown > 7
                ? "message.stardewcraft.shaft_fell_far"
                : "message.stardewcraft.shaft_fell";
        serverPlayer.displayClientMessage(
                Component.translatable(msgKey, levelsDown), false);

        final int floor = targetFloor;
        level.getServer().tell(new net.minecraft.server.TickTask(
            level.getServer().getTickCount() + 3,
            () -> com.stardew.craft.mining.MineFloorGenerator.forceClientLightRefresh(level, floor)
        ));
    }

    /**
     * 粒子效果
     */
    @SuppressWarnings("null")
    @Override
    public void animateTick(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") RandomSource random) {
        super.animateTick(state, level, pos, random);
        
        double centerX = pos.getX() + 0.5;
        double centerY = pos.getY() + 0.5;
        double centerZ = pos.getZ() + 0.5;
        
        // 传送门粒子 - 从边缘向中心
        for (int i = 0; i < 3; i++) {
            double x = centerX + (random.nextDouble() - 0.5) * 0.8;
            double y = centerY + random.nextDouble() * 0.6;
            double z = centerZ + (random.nextDouble() - 0.5) * 0.8;
            
            double vx = (centerX - x) * 0.5;
            double vy = -0.1;
            double vz = (centerZ - z) * 0.5;
            
            level.addParticle(ParticleTypes.PORTAL, x, y, z, vx, vy, vz);
        }
        
        // 附魔符文粒子
        if (random.nextInt(3) == 0) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 1.0 + random.nextDouble() * 1.0;
            double x = centerX + Math.cos(angle) * radius;
            double z = centerZ + Math.sin(angle) * radius;
            double y = centerY + random.nextDouble() * 1.5;
            
            double vx = centerX - x;
            double vy = centerY - y + 0.5;
            double vz = centerZ - z;
            
            level.addParticle(ParticleTypes.ENCHANT, x, y, z, vx, vy, vz);
        }
    }
}
