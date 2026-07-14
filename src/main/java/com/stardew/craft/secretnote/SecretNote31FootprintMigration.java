package com.stardew.craft.secretnote;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Removes the exported debug blocks now replaced by per-player client decals. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SecretNote31FootprintMigration {
    private SecretNote31FootprintMigration() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) return;

        for (SecretNote31FootprintTrail.Footprint footprint : SecretNote31FootprintTrail.FOOTPRINTS) {
            if (level.getBlockState(footprint.pos()).is(ModBlocks.SHADOW_FOOTPRINT.get())) {
                level.removeBlock(footprint.pos(), false);
            }
        }
    }
}
