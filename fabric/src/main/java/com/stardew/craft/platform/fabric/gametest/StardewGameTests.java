package com.stardew.craft.platform.fabric.gametest;

import java.util.Collection;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;

/** Register this one class under Fabric's "fabric-gametest" entrypoint. */
public final class StardewGameTests {
    public StardewGameTests() {}

    @GameTestGenerator
    public Collection<TestFunction> allSourceTests() {
        FabricSmokeTestReporter.installIfRequested();
        return GameTestBridge.indexedTests();
    }
}
