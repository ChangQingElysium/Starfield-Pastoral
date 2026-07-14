package com.stardew.craft.api.v1.cutscene;

import com.google.gson.JsonObject;
import com.stardew.craft.cutscene.command.EventCommand;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Public client command factory registry. Custom commands are visual-only; state uses Actions. */
public final class StardewCutsceneCommands {
    private static final Map<ResourceLocation, StardewCutsceneCommandFactory> FACTORIES = new LinkedHashMap<>();

    private StardewCutsceneCommands() {
    }

    public static synchronized void register(ResourceLocation id, StardewCutsceneCommandFactory factory) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(factory, "factory");
        if (FACTORIES.putIfAbsent(id, factory) != null) {
            throw new IllegalStateException("Cutscene command already registered: " + id);
        }
    }

    public static synchronized Set<ResourceLocation> registeredIds() {
        return Set.copyOf(FACTORIES.keySet());
    }

    @Nullable
    public static synchronized EventCommand create(ResourceLocation id, JsonObject data) {
        StardewCutsceneCommandFactory factory = FACTORIES.get(id);
        return factory == null ? null : factory.create(data);
    }
}
