package com.stardew.craft.platform.fabric.config;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Retains the three existing TOML scopes, including world-local server configs. */
public final class FabricConfig {
    private static final Set<String> REGISTERED = new HashSet<>();

    private FabricConfig() {}

    public static synchronized void register(String modId, ModConfigSpec common,
            ModConfigSpec client, ModConfigSpec server, Consumer<ModConfig> onChanged) {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(common, "common");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(onChanged, "onChanged");
        if (!REGISTERED.add(modId)) {
            throw new IllegalStateException("Configs were already registered for " + modId);
        }
        NeoForgeModConfigEvents.loading(modId).register(onChanged::accept);
        NeoForgeModConfigEvents.reloading(modId).register(onChanged::accept);
        NeoForgeConfigRegistry registry = NeoForgeConfigRegistry.INSTANCE;
        registry.register(modId, ModConfig.Type.COMMON, common);
        registry.register(modId, ModConfig.Type.CLIENT, client);
        registry.register(modId, ModConfig.Type.SERVER, server);
    }
}
