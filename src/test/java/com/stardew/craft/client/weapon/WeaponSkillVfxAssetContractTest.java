package com.stardew.craft.client.weapon;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSkillVfxAssetContractTest {
    private static final String TEXTURE_ROOT =
            "/assets/stardewcraft/textures/particle/";

    @Test
    void bladeTrailsAreNarrowTipWeightedAndTailFaded() throws IOException {
        BufferedImage crescent = image("crescent_blade_trail.png");
        BufferedImage forest = image("forest_blade_trail.png");

        assertEquals(64, crescent.getWidth());
        assertEquals(16, crescent.getHeight());
        assertEquals(64, forest.getWidth());
        assertEquals(16, forest.getHeight());

        assertTrue(alphaRows(crescent, 0, 2) > alphaRows(crescent, 13, 15) * 4L);
        assertTrue(alphaRows(forest, 0, 2) > alphaRows(forest, 13, 15) * 4L);
        assertTrue(alphaColumns(crescent, 0, 7) < alphaColumns(crescent, 56, 63) / 3L);
        assertTrue(alphaColumns(forest, 0, 7) < alphaColumns(forest, 56, 63) / 3L);
    }

    @Test
    void impactAndBlessingUseDedicatedNonRingSprites() throws IOException {
        for (int frame = 0; frame < 4; frame++) {
            assertNotNull(image("crescent_impact_" + frame + ".png"));
            assertNotNull(image("forest_leaf_" + frame + ".png"));
            assertNotNull(image("forest_wisp_" + frame + ".png"));
        }

        assertNotNull(resource("/assets/stardewcraft/particles/crescent_impact.json"));
        assertNotNull(resource("/assets/stardewcraft/particles/forest_leaf.json"));
        assertNotNull(resource("/assets/stardewcraft/particles/forest_wisp.json"));
        assertNull(resource("/assets/stardewcraft/particles/forest_pulse.json"));
    }

    private static BufferedImage image(String name) throws IOException {
        try (InputStream stream = resource(TEXTURE_ROOT + name)) {
            assertNotNull(stream, name);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, name);
            return image;
        }
    }

    private static InputStream resource(String path) {
        return WeaponSkillVfxAssetContractTest.class.getResourceAsStream(path);
    }

    private static long alphaRows(BufferedImage image, int from, int to) {
        long total = 0L;
        for (int y = from; y <= to; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                total += image.getRGB(x, y) >>> 24;
            }
        }
        return total;
    }

    private static long alphaColumns(BufferedImage image, int from, int to) {
        long total = 0L;
        for (int x = from; x <= to; x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                total += image.getRGB(x, y) >>> 24;
            }
        }
        return total;
    }
}
