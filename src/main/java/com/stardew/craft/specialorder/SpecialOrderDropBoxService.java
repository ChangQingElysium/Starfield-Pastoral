package com.stardew.craft.specialorder;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.menu.SpecialOrderDropBoxMenu;
import com.stardew.craft.network.payload.SpecialOrderDropBoxHintPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.Set;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SpecialOrderDropBoxService {

    private SpecialOrderDropBoxService() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpecialOrderManager.migrateQueuedDonations(player);
            syncHints(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ModDimensions.STARDEW_VALLEY.equals(player.level().dimension())) {
            return;
        }
        SpecialOrderDropBoxAnchor.at(event.getPos()).ifPresent(anchor -> {
            if (!activeDropBoxIds(player).contains(anchor.dropBoxId())) {
                return;
            }
            player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new SpecialOrderDropBoxMenu(containerId, inventory, anchor),
                Component.translatable(anchor.translationKey())));
            event.setCanceled(true);
        });
    }

    public static void syncHints(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SpecialOrderDropBoxHintPayload(activeDropBoxIds(player)));
    }

    public static void syncHints(ServerPlayer player, Collection<String> activeDropBoxIds) {
        PacketDistributor.sendToPlayer(player, new SpecialOrderDropBoxHintPayload(activeDropBoxIds));
    }

    public static Set<String> activeDropBoxIds(ServerPlayer player) {
        return SpecialOrderManager.activeDropBoxIds(player);
    }
}
