package com.stardew.craft.port.net.neoforged.neoforge.network.registration;

/** Where a payload handler is invoked. NeoForge (and this port) default to {@link #MAIN}. */
public enum HandlerThread {
    MAIN,
    NETWORK
}
