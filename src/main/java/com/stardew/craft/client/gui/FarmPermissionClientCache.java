package com.stardew.craft.client.gui;

import com.stardew.craft.network.payload.FarmPermSyncPayload;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端缓存：农场权限数据，供 StardewGameMenuScreen 农场管理 Tab 使用。
 */
@OnlyIn(Dist.CLIENT)
public final class FarmPermissionClientCache {

    private static int defaultPerm = 1;
    private static List<FarmPermSyncPayload.PlayerPermEntry> players = new ArrayList<>();
    private static long lastUpdateTime = 0;

    private FarmPermissionClientCache() {}

    public static void update(int defaultPerm, List<FarmPermSyncPayload.PlayerPermEntry> players) {
        FarmPermissionClientCache.defaultPerm = defaultPerm;
        FarmPermissionClientCache.players = new ArrayList<>(players);
        FarmPermissionClientCache.lastUpdateTime = System.currentTimeMillis();
    }

    public static int getDefaultPerm() { return defaultPerm; }
    public static List<FarmPermSyncPayload.PlayerPermEntry> getPlayers() { return players; }
    public static boolean hasData() { return lastUpdateTime > 0; }
}
