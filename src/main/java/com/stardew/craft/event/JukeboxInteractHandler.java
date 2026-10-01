package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.network.payload.JukeboxPlayPayload;
import com.stardew.craft.network.payload.OpenJukeboxPayload;
import com.stardew.craft.sound.JukeboxData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * 拦截唱片机方块的右键交互 → 打开选曲 GUI。
 * 监听唱片机方块破坏 → 广播停止播放。
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.FORGE)
public final class JukeboxInteractHandler {
    private JukeboxInteractHandler() {}

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onRightClickJukebox(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        var state = player.level().getBlockState(event.getPos());
        if (!state.is(ModBlocks.JUKEBOX.get())) return;

        // 获取当前曲目
        String currentTrack = "";
        if (player.level() instanceof ServerLevel serverLevel) {
            currentTrack = JukeboxData.get(serverLevel).getTrack(event.getPos());
        }

        PacketDistributor.sendToPlayer(player, new OpenJukeboxPayload(event.getPos(), currentTrack));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * 唱片机方块被破坏时，清除 JukeboxData 并广播停止播放给附近玩家。
     */
    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        var state = event.getState();
        if (!state.is(ModBlocks.JUKEBOX.get())) return;

        BlockPos pos = event.getPos();

        // 清除 SavedData 中的曲目
        JukeboxData data = JukeboxData.get(serverLevel);
        String oldTrack = data.getTrack(pos);
        if (oldTrack != null && !oldTrack.isEmpty()) {
            data.setTrack(pos, "");

            // 广播停止给附近所有玩家
            JukeboxPlayPayload stopPayload = new JukeboxPlayPayload(pos, "");
            for (ServerPlayer nearby : serverLevel.players()) {
                if (nearby.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 4096.0) {
                    PacketDistributor.sendToPlayer(nearby, stopPayload);
                }
            }
        }
    }
}
