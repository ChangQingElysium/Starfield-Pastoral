package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.network.payload.OpenSleepConfirmScreenPayload;
import com.stardew.craft.network.payload.SleepFadeRestorePayload;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SleepInteractionHandler {
    private SleepInteractionHandler() {
    }

    /** 玩家右键床后暂存的床位置，确认睡觉时取出使用 */
    private static final Map<UUID, BlockPos> pendingBedPositions = new ConcurrentHashMap<>();

    /** 保存玩家待确认的床位置 */
    public static void storePendingBedPos(ServerPlayer player, BlockPos bedPos) {
        pendingBedPositions.put(player.getUUID(), bedPos.immutable());
    }

    /** 取出并移除玩家待确认的床位置 */
    public static BlockPos consumePendingBedPos(ServerPlayer player) {
        return pendingBedPositions.remove(player.getUUID());
    }

    /** 清除所有暂存的床位置 */
    public static void clearAllPendingBedPositions() {
        pendingBedPositions.clear();
    }

    /**
     * 允许星露谷维度的玩家在任何时间（包括白天）继续睡觉。
     * 原版会在白天踢出睡觉的玩家（BedSleepingProblem.NOT_POSSIBLE_NOW），这里覆盖该行为。
     */
    @SubscribeEvent
    public static void onCanContinueSleeping(CanContinueSleepingEvent event) {
        var dim = event.getEntity().level().dimension();
        if (dim == ModDimensions.STARDEW_VALLEY || dim == ModMiningDimensions.STARDEW_MINING) {
            event.setContinueSleeping(true);
        }
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onCanPlayerSleep(CanPlayerSleepEvent event) {
        var dim = event.getEntity().level().dimension();
        if ((dim == ModDimensions.STARDEW_VALLEY || dim == ModMiningDimensions.STARDEW_MINING)
                && isSleepAnchorState(event.getState())) {
            event.setProblem(null);
        }
    }

    @SubscribeEvent
    public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (!event.updateLevel() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        consumePendingBedPos(player);
        if (SleepVoteTracker.hasVoted(player)) {
            SleepVoteTracker.revokeVoteAndBroadcast(player);
            PacketDistributor.sendToPlayer(
                    player,
                    new SleepFadeRestorePayload());
        }
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onRightClickBed(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        var state = player.level().getBlockState(event.getPos());
        if (!isSleepAnchorState(state)) {
            return;
        }

        // 只能在已登记的玩家农场实例中睡觉。
        if (!com.stardew.craft.core.FarmAreaResolver.isInFarmArea(player.level(), event.getPos())) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("stardewcraft.farm.sleep_farm_only"), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // 不能在别人农场睡觉（需要 PERM_FULL 权限）
        if (com.stardew.craft.event.FarmAreaProtectionEvents.isOnProtectedFarm(player, event.getPos())) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("stardewcraft.farm.sleep_no_permission"), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        int currentMinute = StardewTimeManager.get().getCurrentTime();
        BlockPos sleepAnchor = resolveSleepAnchor(player.level(), event.getPos(), state);
        if (!player.isSleeping()) {
            player.startSleepInBed(sleepAnchor);
            if (!player.isSleeping()) {
                player.startSleeping(sleepAnchor);
                player.serverLevel().updateSleepingPlayerList();
            }
        }
        if (player.isSleeping()) {
            storePendingBedPos(player, sleepAnchor);
            com.stardew.craft.player.PlayerStardewData sleepData =
                    com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
            sleepData.setLastSleepPoint(sleepAnchor);
            com.stardew.craft.player.PlayerDataManager.get().setDirty();
            PacketDistributor.sendToPlayer(player, new OpenSleepConfirmScreenPayload(currentMinute));
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static boolean isSleepAnchorState(BlockState state) {
        return state.getBlock() instanceof BedBlock
                || state.is(ModBlocks.BED_1.get())
                || state.is(ModBlocks.BED_2.get());
    }

    private static BlockPos resolveSleepAnchor(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
            BlockPos headPos = pos.relative(state.getValue(BedBlock.FACING));
            if (level.getBlockState(headPos).is(state.getBlock())) {
                return headPos;
            }
        }
        if ((state.is(ModBlocks.BED_1.get()) || state.is(ModBlocks.BED_2.get()))
                && state.getBlock() instanceof MapDecorStaticBlock decorBlock
                && state.hasProperty(MapDecorStaticBlock.FACING)) {
            BlockPos mainPos = decorBlock.findMainPos(level, pos, state);
            if (mainPos == null) {
                mainPos = pos;
            }
            Direction facing = state.getValue(MapDecorStaticBlock.FACING);
            LocalCellOffset clickedOffset = unrotateCellOffset(
                    pos.getX() - mainPos.getX(),
                    pos.getZ() - mainPos.getZ(),
                    facing);
            int laneX = state.is(ModBlocks.BED_2.get()) && clickedOffset.dx() < 0 ? -1 : 0;
            LocalCellOffset headOffset = rotateCellOffset(laneX, 1, facing);
            BlockPos headPos = mainPos.offset(headOffset.dx(), 0, headOffset.dz());
            if (level.getBlockState(headPos).is(state.getBlock())) {
                return headPos;
            }
        }
        return pos;
    }

    private static LocalCellOffset rotateCellOffset(int localDx, int localDz, Direction facing) {
        return switch (facing) {
            case EAST -> new LocalCellOffset(-localDz, localDx);
            case SOUTH -> new LocalCellOffset(-localDx, -localDz);
            case WEST -> new LocalCellOffset(localDz, -localDx);
            default -> new LocalCellOffset(localDx, localDz);
        };
    }

    private static LocalCellOffset unrotateCellOffset(int worldDx, int worldDz, Direction facing) {
        return switch (facing) {
            case EAST -> new LocalCellOffset(worldDz, -worldDx);
            case SOUTH -> new LocalCellOffset(-worldDx, -worldDz);
            case WEST -> new LocalCellOffset(-worldDz, worldDx);
            default -> new LocalCellOffset(worldDx, worldDz);
        };
    }

    private record LocalCellOffset(int dx, int dz) {
    }
}
