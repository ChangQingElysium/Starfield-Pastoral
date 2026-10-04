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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 拦截唱片机方块的右键交互 → 打开选曲 GUI。
 * 监听唱片机方块破坏 → 广播停止播放。
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class JukeboxInteractHandler {
    private JukeboxInteractHandler() {}

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onRightClickJukebox(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        var state = player.level().getBlockState(event.getPos());
        if (!state.is(ModBlocks.JUKEBOX.get())) return;

        // MiniJukebox.checkForAction: only on the farm / greenhouse / cellar / Ginger Island west,
        // and never outdoors in the rain.
        if (player.level() instanceof ServerLevel here) {
            boolean farm = com.stardew.craft.farm.FarmInstanceAllocator.isInFarmInstanceRegion(event.getPos())
                    && com.stardew.craft.core.FarmAreaResolver.isInAnyFarm(here, event.getPos());
            boolean greenhouse = com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(here, event.getPos());
            var locations = com.stardew.craft.api.v1.world.StardewLocations.hierarchy(here.dimension().location(), event.getPos());
            boolean islandWest = locations.stream().anyMatch(com.stardew.craft.mining.IslandStoneRewards::isWest);
            if (!farm && !greenhouse && !islandWest) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.stardewcraft.jukebox.not_farm_play"), true);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
            String weather = com.stardew.craft.weather.WeatherManager.getCurrentWeather(here);
            boolean raining = "Rain".equals(weather) || "Storm".equals(weather) || "GreenRain".equals(weather);
            boolean outdoors = !greenhouse && locations.stream().noneMatch(com.stardew.craft.api.v1.world.StardewLocation::indoor);
            if (outdoors && raining) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.stardewcraft.jukebox.outdoor_rainy"), true);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
        }

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
