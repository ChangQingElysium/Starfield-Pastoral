package com.stardew.craft.floor;

import com.stardew.craft.StardewCraft;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SurfaceFloorEvents {
    private SurfaceFloorEvents() {}

    // PORT(1.20.1): Forge fires ChunkWatchEvent.Watch after the chunk packet was sent (NeoForge: Sent).
    @SubscribeEvent public static void sent(ChunkWatchEvent.Watch event) {
        SurfaceFloorData.get(event.getLevel()).sendChunk(event.getLevel(), event.getPos(), event.getPlayer());
    }
    @SubscribeEvent public static void unwatch(ChunkWatchEvent.UnWatch event) {
        PacketDistributor.sendToPlayer(event.getPlayer(), new SurfaceFloorPacket(event.getLevel().dimension().location(),
                event.getPos().toLong(), true, List.of()));
    }

    /** Deliberate removal gesture: never mines the support or strips the underlying log. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void remove(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown() || event.getFace() != Direction.UP) return;
        var stack = event.getItemStack();
        if (!stack.canPerformAction(ToolActions.AXE_DIG) && !stack.canPerformAction(ToolActions.PICKAXE_DIG)) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!SurfaceFloorItem.mayEdit(player, event.getPos())) return;
            if (SurfaceFloorData.get(player.serverLevel()).remove(player.serverLevel(), event.getPos(), !player.isCreative())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        } else if (com.stardew.craft.client.floor.ClientSurfaceFloors.at(event.getPos()) != null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
