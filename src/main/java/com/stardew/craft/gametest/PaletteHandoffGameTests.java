package com.stardew.craft.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Isolated palette regression namespace, including the real async world-generation handoff. */
@GameTestHolder("stardewcraft_palette_handoff")
@PrefixGameTestTemplate(false)
public final class PaletteHandoffGameTests {
    private PaletteHandoffGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_palette_handoff", template = "empty", timeoutTicks = 400)
    public static void generationHandoffAndConcurrentResize(GameTestHelper helper) throws Exception {
        PalettedContainerAccessGameTests.paletteSupportsWorldGenerationHandoffAndConcurrentResize(helper);
    }
}
