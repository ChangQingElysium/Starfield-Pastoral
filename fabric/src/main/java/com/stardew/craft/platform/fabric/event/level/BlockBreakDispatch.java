package com.stardew.craft.platform.fabric.event.level;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.GameMasterBlock;

public final class BlockBreakDispatch {
    private static final ThreadLocal<Deque<BlockEvent.BreakEvent>> BREAKS = ThreadLocal.withInitial(ArrayDeque::new);

    private BlockBreakDispatch() {}

    public static boolean destroy(ServerLevel level, ServerPlayer player, GameType mode, BlockPos pos, BooleanSupplier vanilla) {
        var state = level.getBlockState(pos);
        ItemStack tool = player.getMainHandItem();
        var event = new BlockEvent.BreakEvent(level, pos, state, player);
        event.setCanceled(!tool.isEmpty() && !tool.getItem().canAttackBlock(state, level, pos, player)
                || player.blockActionRestricted(level, pos, mode)
                || state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks());
        FabricEvents.GAME.post(event);
        if (event.isCanceled()) {
            correctPrediction(level, player, pos);
            return false;
        }
        Deque<BlockEvent.BreakEvent> stack = BREAKS.get();
        stack.push(event);
        try { return vanilla.getAsBoolean(); }
        finally { stack.pop(); if (stack.isEmpty()) BREAKS.remove(); }
    }

    public static boolean hasApprovedBreak() { return !BREAKS.get().isEmpty(); }

    public static void correctPrediction(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (player.connection == null) return;
        player.connection.send(new ClientboundBlockUpdatePacket(pos, level.getBlockState(pos)));
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null && blockEntity.getUpdatePacket() != null) player.connection.send(blockEntity.getUpdatePacket());
    }
}
