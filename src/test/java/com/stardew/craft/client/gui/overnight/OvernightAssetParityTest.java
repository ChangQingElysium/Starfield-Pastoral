package com.stardew.craft.client.gui.overnight;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OvernightAssetParityTest {
    private static final Path REPOSITORY_ROOT = findRepositoryRoot();
    private static final Path ORIGINAL_CURSORS =
        REPOSITORY_ROOT.resolve(Path.of("源文件", "Content", "LooseSprites", "Cursors.png"));
    private static final Path RUNTIME_CURSORS =
        REPOSITORY_ROOT.resolve(Path.of(
            "src", "main", "resources", "assets", "stardewcraft", "textures", "gui", "cursors.png"));
    private static final Path ORIGINAL_BUFF_ICONS =
        REPOSITORY_ROOT.resolve(Path.of("源文件", "Content", "TileSheets", "BuffsIcons.png"));
    private static final Path ORIGINAL_ANIMATIONS =
        REPOSITORY_ROOT.resolve(Path.of("源文件", "Content", "TileSheets", "animations.png"));
    private static final Path PROCESSED_ANIMATIONS =
        REPOSITORY_ROOT.resolve(Path.of(
            "build", "resources", "main", "assets", "stardewcraft",
            "textures", "gui", "overnight", "animations.png"));
    private static final Path OVERNIGHT =
        REPOSITORY_ROOT.resolve(Path.of(
            "src", "main", "resources", "assets", "stardewcraft", "textures", "gui", "overnight"));

    @Test
    void runtimeCursorSheetIsPixelIdenticalToRepositoryOriginal() throws Exception {
        BufferedImage original = read(ORIGINAL_CURSORS);
        BufferedImage runtime = read(RUNTIME_CURSORS);
        assertEquals(original.getWidth(), runtime.getWidth());
        assertEquals(original.getHeight(), runtime.getHeight());
        assertPixelsEqual(runtime, original, 0, 0);
    }

    @Test
    void processedMoneyDialAnimationsArePixelIdenticalToRepositoryOriginal() throws Exception {
        BufferedImage original = read(ORIGINAL_ANIMATIONS);
        BufferedImage processed = read(PROCESSED_ANIMATIONS);
        assertEquals(original.getWidth(), processed.getWidth());
        assertEquals(original.getHeight(), processed.getHeight());
        assertPixelsEqual(processed, original, 0, 0);
    }

    @Test
    void overnightSlicesStillInUseMatchOriginalCursorCoordinates() throws Exception {
        BufferedImage cursors = read(ORIGINAL_CURSORS);
        List<Crop> crops = List.of(
            new Crop("btn_ok.png", 128, 256),
            new Crop("btn_back.png", 352, 495),
            new Crop("btn_forward.png", 365, 495),
            new Crop("btn_plus.png", 392, 361),
            new Crop("btn_plus_hover.png", 402, 361),
            new Crop("coin.png", 408, 476),
            new Crop("dial_dots.png", 355, 476),
            new Crop("shipping_sky_strip.png", 639, 858),
            new Crop("shipping_green_rain_sky_strip.png", 640, 858),
            new Crop("bg_stars.png", 0, 1453),
            new Crop("levelup_header_ribbon.png", 363, 87),
            new Crop("little_star.png", 364, 79)
        );
        for (Crop crop : crops) {
            BufferedImage slice = read(OVERNIGHT.resolve(crop.file()));
            assertPixelsEqual(slice, cursors, crop.x(), crop.y());
        }
    }

    @Test
    void levelUpIconsMatchOriginalSkillAndProfessionCoordinates() throws Exception {
        BufferedImage cursors = read(ORIGINAL_CURSORS);
        BufferedImage buffs = read(ORIGINAL_BUFF_ICONS);
        List<Crop> skillIcons = List.of(
            new Crop("icon_farming.png", 0, 0),
            new Crop("icon_fishing.png", 16, 0),
            new Crop("icon_foraging.png", 80, 0),
            new Crop("icon_mining.png", 32, 0),
            new Crop("icon_other.png", 128, 16)
        );
        for (Crop crop : skillIcons) {
            assertPixelsEqual(read(OVERNIGHT.resolve(crop.file())), buffs, crop.x(), crop.y());
        }

        for (int profession = 0; profession < 36; profession++) {
            BufferedImage icon = read(OVERNIGHT.resolve("profession_" + profession + ".png"));
            assertPixelsEqual(icon, cursors, profession % 6 * 16, 624 + profession / 6 * 16);
        }
    }

    private static BufferedImage read(Path path) throws Exception {
        assertTrue(path.toFile().isFile(), () -> "Missing image: " + path);
        BufferedImage image = ImageIO.read(path.toFile());
        assertNotNull(image, () -> "Unreadable image: " + path);
        return image;
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (current.resolve("源文件").toFile().isDirectory()
                    && current.resolve("src/main/resources").toFile().isDirectory()) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate StardewCraft repository root");
    }

    private static void assertPixelsEqual(
            BufferedImage slice,
            BufferedImage source,
            int sourceX,
            int sourceY
    ) {
        for (int y = 0; y < slice.getHeight(); y++) {
            for (int x = 0; x < slice.getWidth(); x++) {
                assertEquals(
                    source.getRGB(sourceX + x, sourceY + y),
                    slice.getRGB(x, y),
                    "Pixel mismatch at " + x + "," + y
                );
            }
        }
    }

    private record Crop(String file, int x, int y) {
    }
}
