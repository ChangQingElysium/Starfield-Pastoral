package com.stardew.craft.api.v1.network;

/** How a locally registered network capability constrains the remote side. */
public enum StardewNetworkCapabilityRequirement {
    /** The feature may be disabled when the remote side does not advertise it. */
    OPTIONAL,
    /** The connection is rejected unless the remote side advertises the same version. */
    REQUIRED_REMOTE
}
