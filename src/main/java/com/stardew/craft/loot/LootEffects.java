package com.stardew.craft.loot;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.action.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/** Effects collected during loot selection are applied only by the actual award path, never by previews. */
public final class LootEffects {
    private LootEffects() {}
    public static void commit(ServerPlayer player, List<StardewAction> actions) {
        if (player == null) return;
        for (var action : actions) {
            var result = StardewActions.execute(action, StardewActionContext.forPlayer(player));
            if (result.result().isEmpty() || !result.result().get().success())
                StardewCraft.LOGGER.error("[Loot] Deferred action {} failed: {}", action.type(), result);
        }
    }
}
