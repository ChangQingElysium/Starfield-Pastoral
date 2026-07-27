package com.stardew.craft.communitycenter.data;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleItemResolverTest {
    @Test
    void namespacedItemIdsResolveWithoutMixin() {
        assertTrue(BundleItemResolver.resolveItemStack("minecraft:apple").is(Items.APPLE));
        assertTrue(BundleItemResolver.resolveItemStack("missing_namespace:missing").isEmpty());
    }
}
