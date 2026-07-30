package com.stardew.craft.client.gui.overnight;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OvernightAssetContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("stardewcraft.projectDir", "."))
            .toAbsolutePath()
            .normalize();
    private static final Path GUI = PROJECT.resolve(Path.of(
            "src", "main", "resources", "assets", "stardewcraft", "textures", "gui"));
    private static final Path OVERNIGHT = GUI.resolve("overnight");

    @Test
    void completeRuntimeSheetsAreBundledWithStableContent() throws Exception {
        assertImage(
                GUI.resolve("cursors.png"),
                704,
                2256,
                "83895c54bebcef5f32421eea877ca049dbc414515043dfe1100669fdc7722aa9");
        assertImage(
                OVERNIGHT.resolve("animations.png"),
                640,
                3328,
                "64938392d90e9b4b35d2d7d701fdcbcb71f43515b10a6fe64a2ad1594348af7d");
    }

    @Test
    void overnightSlicesHaveTheirRequiredRuntimeDimensions() throws Exception {
        List<ExpectedImage> images = List.of(
                new ExpectedImage("btn_ok.png", 64, 64),
                new ExpectedImage("btn_back.png", 12, 11),
                new ExpectedImage("btn_forward.png", 12, 11),
                new ExpectedImage("btn_plus.png", 10, 11),
                new ExpectedImage("btn_plus_hover.png", 10, 11),
                new ExpectedImage("coin.png", 9, 11),
                new ExpectedImage("dial_dots.png", 7, 11),
                new ExpectedImage("shipping_sky_strip.png", 1, 184),
                new ExpectedImage("shipping_green_rain_sky_strip.png", 1, 184),
                new ExpectedImage("bg_stars.png", 639, 195),
                new ExpectedImage("levelup_header_ribbon.png", 58, 22),
                new ExpectedImage("little_star.png", 35, 5));
        for (ExpectedImage expected : images) {
            assertDimensions(OVERNIGHT.resolve(expected.file()), expected.width(), expected.height());
        }
    }

    @Test
    void levelUpIconsHaveTheirRequiredRuntimeDimensions() throws Exception {
        for (String skill : List.of("farming", "fishing", "foraging", "mining", "other")) {
            assertDimensions(OVERNIGHT.resolve("icon_" + skill + ".png"), 16, 16);
        }
        for (int profession = 0; profession < 36; profession++) {
            assertDimensions(OVERNIGHT.resolve("profession_" + profession + ".png"), 16, 16);
        }
    }

    private static void assertImage(
            Path path,
            int expectedWidth,
            int expectedHeight,
            String expectedSha256
    ) throws Exception {
        assertDimensions(path, expectedWidth, expectedHeight);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        assertEquals(expectedSha256, HexFormat.of().formatHex(digest), path.toString());
    }

    private static void assertDimensions(Path path, int expectedWidth, int expectedHeight)
            throws Exception {
        assertTrue(Files.isRegularFile(path), () -> "Missing image: " + path);
        BufferedImage image = ImageIO.read(path.toFile());
        assertNotNull(image, () -> "Unreadable image: " + path);
        assertEquals(expectedWidth, image.getWidth(), path.toString());
        assertEquals(expectedHeight, image.getHeight(), path.toString());
    }

    private record ExpectedImage(String file, int width, int height) {
    }
}
