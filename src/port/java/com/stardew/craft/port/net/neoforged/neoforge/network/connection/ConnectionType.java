package com.stardew.craft.port.net.neoforged.neoforge.network.connection;

/**
 * NeoForge connection flavour. On Forge 1.20.1 every connection that reaches the Stardew payload
 * channel is a modded (Forge) connection, which plays the role of {@link #NEOFORGE}.
 */
public enum ConnectionType {
    NEOFORGE,
    OTHER;

    public boolean isNeoForge() {
        return this == NEOFORGE;
    }

    public boolean isOther() {
        return this == OTHER;
    }
}
