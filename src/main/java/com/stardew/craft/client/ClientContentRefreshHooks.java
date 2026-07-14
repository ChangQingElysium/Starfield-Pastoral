package com.stardew.craft.client;

import net.neoforged.fml.ModList;

/** Optional client integration hooks invoked after a server content snapshot is applied. */
public final class ClientContentRefreshHooks {
    private ClientContentRefreshHooks() {
    }

    public static void onSyncedRegistriesChanged() {
        if (!ModList.get().isLoaded("jei")) {
            return;
        }
        try {
            com.stardew.craft.integration.jei.StardewJeiPlugin.refreshSyncedRecipes();
        } catch (LinkageError | RuntimeException error) {
            // JEI is optional. A broken optional classpath must not break the content sync itself.
            com.stardew.craft.StardewCraft.LOGGER.warn("Unable to refresh optional JEI integration", error);
        }
    }
}
