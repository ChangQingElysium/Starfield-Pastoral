package com.stardew.craft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ModKeyMappingsTest {

    @Test
    void unboundMappingIsNeverPolledAsAKey() {
        KeyMapping unbound = new KeyMapping(
                "key.stardewcraft.test.unbound",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                "key.categories.stardewcraft.test");

        assertFalse(ModKeyMappings.isDown(unbound));
    }
}
