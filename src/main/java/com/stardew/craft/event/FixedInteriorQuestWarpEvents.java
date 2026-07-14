package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.interior.InteriorRegionRegistry;
import com.stardew.craft.quest.StardewQuestEvents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FixedInteriorQuestWarpEvents {
    private static final Map<UUID, String> LAST_FIXED_INTERIOR = new HashMap<>();

    private FixedInteriorQuestWarpEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 5 != 0) {
            return;
        }
        if (!ModDimensions.STARDEW_VALLEY.equals(player.level().dimension())) {
            LAST_FIXED_INTERIOR.remove(player.getUUID());
            return;
        }

        var region = InteriorRegionRegistry.fixedInteriorAt(player.blockPosition()).orElse(null);
        String current = region != null ? region.id() : "";
        String previous = LAST_FIXED_INTERIOR.getOrDefault(player.getUUID(), "");
        if (current.equals(previous)) {
            return;
        }
        if (current.isEmpty()) {
            LAST_FIXED_INTERIOR.remove(player.getUUID());
            return;
        }

        LAST_FIXED_INTERIOR.put(player.getUUID(), current);
        StardewQuestEvents.fireWarped(player, current);
        for (String alias : region.aliases()) {
            if (!alias.isBlank() && !alias.equals(current)) {
                StardewQuestEvents.fireWarped(player, alias);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_FIXED_INTERIOR.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LAST_FIXED_INTERIOR.clear();
    }
}
