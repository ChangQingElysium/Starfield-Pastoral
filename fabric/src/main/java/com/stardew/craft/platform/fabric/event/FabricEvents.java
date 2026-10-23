package com.stardew.craft.platform.fabric.event;

/** Shared game bus. Client subscriber loading belongs in the client initializer. */
public final class FabricEvents {
    public static final EventBus GAME = new EventBus();
    public static final EventBus MOD = new EventBus();

    private FabricEvents() {}
}
