package com.stardew.craft.client.gui.overnight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShippingMenuFadeTimelineTest {
    @Test
    void introStartsBlackAndRevealsEveryLayerTogether() {
        assertEquals(1.0F, ShippingMenuFadeTimeline.introBlackAlpha(3500, 3500));
        assertEquals(0.5F, ShippingMenuFadeTimeline.introBlackAlpha(1750, 3500));
        assertEquals(0.0F, ShippingMenuFadeTimeline.introBlackAlpha(0, 3500));
        assertEquals(0.0F, ShippingMenuFadeTimeline.introBlackAlpha(-100, 3500));
    }

    @Test
    void outroFadesFromVisibleToBlack() {
        assertEquals(0.0F, ShippingMenuFadeTimeline.outroBlackAlpha(800, 800));
        assertEquals(0.5F, ShippingMenuFadeTimeline.outroBlackAlpha(400, 800));
        assertEquals(1.0F, ShippingMenuFadeTimeline.outroBlackAlpha(0, 800));
    }

    @Test
    void transitionLayersCoverVanillaItemsButKeepDatePlaqueOrdering() {
        assertTrue(ShippingMenuFadeTimeline.hasSafeLayerOrdering());
        assertTrue(ShippingMenuFadeTimeline.CONTENT_BLACKOUT_Z
            > ShippingMenuFadeTimeline.VANILLA_ITEM_DECORATION_Z);
        assertTrue(ShippingMenuFadeTimeline.OUTRO_FOREGROUND_Z
            > ShippingMenuFadeTimeline.CONTENT_BLACKOUT_Z);
        assertTrue(ShippingMenuFadeTimeline.FINAL_BLACKOUT_Z
            > ShippingMenuFadeTimeline.OUTRO_FOREGROUND_Z);
    }

    @Test
    void blackColorUsesClampedAlpha() {
        assertEquals(0x00000000, ShippingMenuFadeTimeline.blackArgb(-1.0F));
        assertEquals(0x80000000, ShippingMenuFadeTimeline.blackArgb(0.5F));
        assertEquals(0xFF000000, ShippingMenuFadeTimeline.blackArgb(2.0F));
    }
}
