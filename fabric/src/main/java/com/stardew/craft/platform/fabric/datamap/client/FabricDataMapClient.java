package com.stardew.craft.platform.fabric.datamap.client;

import com.stardew.craft.platform.fabric.datamap.DataMapSync;
import com.stardew.craft.platform.mixin.network.client.ClientCommonConnectionAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

@Environment(EnvType.CLIENT)
public final class FabricDataMapClient {
    private static boolean installed;
    private FabricDataMapClient() {}

    public static synchronized void install() {
        if (installed) return;
        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> DataMapSync.forget(
                ((ClientCommonConnectionAccessor) handler).stardewcraft$getConnection()));
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> {
            DataMapSync.forget(((ClientCommonConnectionAccessor) handler).stardewcraft$getConnection());
            DataMapSync.clearClientValues();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            DataMapSync.forget(((ClientCommonConnectionAccessor) handler).stardewcraft$getConnection());
            DataMapSync.clearClientValues();
        });
        installed = true;
    }
}
