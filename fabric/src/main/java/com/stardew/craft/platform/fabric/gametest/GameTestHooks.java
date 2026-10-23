package com.stardew.craft.platform.fabric.gametest;

/** Uses Fabric API's actual headless GameTest switch, including property-presence semantics. */
public final class GameTestHooks {
    private static final boolean HEADLESS = System.getProperty("fabric-api.gametest") != null;

    public static boolean isGametestServer() {
        return HEADLESS;
    }

    private GameTestHooks() {}
}
