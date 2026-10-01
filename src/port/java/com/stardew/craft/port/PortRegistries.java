package com.stardew.craft.port;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 1.21 threads a {@link HolderLookup.Provider} through every NBT method. 1.20.1 does not, and its
 * item/block/enchantment registries are static, so a lookup is recovered from the running server
 * (or the frozen built-in registries when none exists, e.g. on a remote client or during load).
 */
public final class PortRegistries {
    private static RegistryAccess builtIn;

    private PortRegistries() {
    }

    public static HolderLookup.Provider lookup() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess();
        }
        return builtIn();
    }

    public static synchronized RegistryAccess builtIn() {
        if (builtIn == null) {
            builtIn = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        }
        return builtIn;
    }
}
