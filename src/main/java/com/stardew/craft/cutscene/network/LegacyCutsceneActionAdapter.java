package com.stardew.craft.cutscene.network;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.action.StardewActionContext;
import com.stardew.craft.api.v1.action.StardewActionResult;
import com.stardew.craft.api.v1.action.StardewActions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/** Routes legacy cutscene command payloads through the public action registry. */
final class LegacyCutsceneActionAdapter {
    private static final Logger LOGGER = LogUtils.getLogger();

    private LegacyCutsceneActionAdapter() {
    }

    static boolean tryExecute(String legacyAction, String value, ServerPlayer player) {
        ResourceLocation type;
        JsonObject data = new JsonObject();
        switch (legacyAction) {
            case "add_quest" -> {
                type = id("start_quest");
                data.addProperty("quest", value);
            }
            case "remove_quest" -> {
                type = id("remove_quest");
                data.addProperty("quest", value);
            }
            case "set_flag" -> {
                type = id("set_flag");
                data.addProperty("id", value);
            }
            case "add_item", "remove_item" -> {
                int separator = value.lastIndexOf(':');
                if (separator <= 0 || separator >= value.length() - 1) {
                    LOGGER.warn("Cutscene {} action has invalid value '{}'", legacyAction, value);
                    return true;
                }
                type = id(legacyAction);
                data.addProperty("item", value.substring(0, separator));
                try {
                    data.addProperty("count", Integer.parseInt(value.substring(separator + 1)));
                } catch (NumberFormatException exception) {
                    LOGGER.warn("Cutscene {} action has invalid count '{}'", legacyAction, value);
                    return true;
                }
            }
            default -> {
                return false;
            }
        }

        var decoded = StardewActions.decode(type, data).result();
        if (decoded.isEmpty()) {
            LOGGER.warn("Failed to decode registered cutscene action {}", type);
            return true;
        }
        var executed = StardewActions.execute(decoded.get(), StardewActionContext.forPlayer(player)).result();
        if (executed.isEmpty()) {
            LOGGER.warn("Registered cutscene action {} failed during execution", type);
            return true;
        }
        StardewActionResult result = executed.get();
        if (!result.success()) {
            LOGGER.warn("Registered cutscene action {} was rejected: {}", type, result.message());
        }
        return true;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
    }
}
