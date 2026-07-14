package com.stardew.craft.api.v1.cutscene;

import com.google.gson.JsonObject;
import com.stardew.craft.cutscene.command.EventCommand;

/** Client-side factory for an add-on's namespaced visual cutscene command. */
@FunctionalInterface
public interface StardewCutsceneCommandFactory {
    EventCommand create(JsonObject data);
}
