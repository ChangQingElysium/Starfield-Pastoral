package com.stardew.craft.festival;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.minecart.MinecartStationEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SpiritEveFestivalInteractionEvents {
    private SpiritEveFestivalInteractionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (SpiritEveFestivalService.tryOpenGoldenPumpkinChest(player, event.getPos())) {
            event.setUseBlock(TriState.FALSE.toResult());
            event.setUseItem(TriState.FALSE.toResult());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (event.getTarget() instanceof MinecartStationEntity minecart
            && SpiritEveFestivalService.tryUseShortcutMinecart(player, minecart)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (event.getTarget() instanceof MinecartStationEntity minecart
            && SpiritEveFestivalService.tryUseShortcutMinecart(player, minecart)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
