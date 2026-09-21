package com.stardew.craft.api.v1.network;

import java.util.List;

/** Immutable capability view negotiated for one connection. */
public record StardewNetworkCapabilitySession(
        List<StardewNetworkCapability> local,
        List<StardewNetworkCapability> remote,
        List<StardewNetworkCapability> negotiated
) {
    public StardewNetworkCapabilitySession {
        local = List.copyOf(local);
        remote = List.copyOf(remote);
        negotiated = List.copyOf(negotiated);
    }
}
