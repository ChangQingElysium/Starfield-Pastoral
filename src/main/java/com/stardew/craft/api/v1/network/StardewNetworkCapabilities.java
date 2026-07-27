package com.stardew.craft.api.v1.network;

import com.stardew.craft.api.v1.internal.network.StardewNetworkCapabilityRegistry;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Registration and read-only connection queries for addon network capabilities.
 *
 * <p>Register during mod initialization. Optional capabilities permit display or
 * integration features to degrade without changing server authority.
 */
public final class StardewNetworkCapabilities {
    private StardewNetworkCapabilities() {
    }

    public static void register(
            ResourceLocation id,
            int version,
            StardewNetworkCapabilityRequirement requirement
    ) {
        StardewNetworkCapabilityRegistry.register(
                new StardewNetworkCapability(id, version, requirement));
    }

    public static List<StardewNetworkCapability> local() {
        return StardewNetworkCapabilityRegistry.local();
    }

    public static Optional<StardewNetworkCapabilitySession> session(
            Connection connection
    ) {
        return StardewNetworkCapabilityRegistry.session(connection);
    }

    public static boolean supports(
            Connection connection,
            ResourceLocation capabilityId
    ) {
        return session(connection)
                .map(value -> value.negotiated().stream()
                        .anyMatch(capability ->
                                capability.id().equals(capabilityId)))
                .orElse(false);
    }
}
