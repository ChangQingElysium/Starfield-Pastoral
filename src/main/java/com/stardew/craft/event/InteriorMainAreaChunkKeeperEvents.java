package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public class InteriorMainAreaChunkKeeperEvents {

    // 当前主线地图的稳定落点，作为主区域保活中心。
    private static final int MAIN_AREA_CENTER_X = 150;
    private static final int MAIN_AREA_CENTER_Z = 119;

    // 半径 6 chunk（13x13），用于保证 NPC 与基础逻辑持续运行。
    private static final int FORCE_RADIUS_CHUNKS = 6;

    private static boolean forcingEnabled = false;
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }

        tickCounter++;
        if (tickCounter < 20) {
            return;
        }
        tickCounter = 0;

        boolean shouldForce = false;
        for (ServerPlayer player : level.players()) {
            if (InteriorPortalInteractionEvents.isPlayerInInteriorSpace(player)) {
                shouldForce = true;
                break;
            }
        }

        if (shouldForce == forcingEnabled) {
            return;
        }

        forcingEnabled = shouldForce;
        setForcedMainArea(level, shouldForce);
        StardewCraft.LOGGER.info("[INTERIOR] Main area chunk forcing toggled: {}", shouldForce);
    }

    private static void setForcedMainArea(ServerLevel level, boolean force) {
        int centerChunkX = MAIN_AREA_CENTER_X >> 4;
        int centerChunkZ = MAIN_AREA_CENTER_Z >> 4;

        for (int dz = -FORCE_RADIUS_CHUNKS; dz <= FORCE_RADIUS_CHUNKS; dz++) {
            for (int dx = -FORCE_RADIUS_CHUNKS; dx <= FORCE_RADIUS_CHUNKS; dx++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                level.setChunkForced(chunkX, chunkZ, force);
                if (force) {
                    // 立即加载，减少首次进入室内后的一次性停顿。
                    level.getChunk(chunkX, chunkZ);
                }
            }
        }
    }
}
