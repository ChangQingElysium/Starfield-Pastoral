package com.stardew.craft.gametest;

import com.stardew.craft.block.mine.DesertWallConnections;
import com.stardew.craft.block.terrain.TerrainFaceConnections;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Checks the actual shipped wall phases, including variant seams and negative coordinates. */
@GameTestHolder("stardewcraft_ginger_collision")
@PrefixGameTestTemplate(false)
public final class GingerIslandWallMaterialGameTests {
    private GingerIslandWallMaterialGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_ginger_collision", template = "empty")
    public static void volcanoWallPhasesMatchTheirMastersAndKeepVariantConnectionBands(GameTestHelper h) {
        BufferedImage phases = image("volcano_wall_phases");
        h.assertTrue(phases.getWidth() == 192 && phases.getHeight() == 16, "Wall phase atlas has wrong dimensions");
        BufferedImage base = image("volcano_wall_master");
        for (int variant = 0; variant < 3; variant++) {
            BufferedImage master = image("volcano_wall_master" + (variant == 0 ? "" : variant == 1 ? "_b" : "_c"));
            h.assertTrue(master.getWidth() == 32 && master.getHeight() == 32, "Wall master changed its native scale");
            for (int phase = 0; phase < 4; phase++) {
                int ox = phase % 2 * 16, oy = phase / 2 * 16;
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                    int pixel = master.getRGB(ox + x, oy + y);
                    h.assertTrue((pixel >>> 24) == 255
                                    && phases.getRGB((phase * 3 + variant) * 16 + x, y) == pixel,
                            "Wall phase has missing, stretched or wrong source pixels");
                    if (x < 2 || x >= 14 || y < 2 || y >= 14) {
                        h.assertTrue(pixel == base.getRGB(ox + x, oy + y), "Wall variation breaks a shared connection band");
                    }
                }
            }
        }
        for (Direction face : Direction.values()) {
            var frame = TerrainFaceConnections.frame(face);
            for (int i = -4; i <= 4; i++) {
                BlockPos pos = new BlockPos(i, i - 2, -i);
                int phase = DesertWallConnections.phase(pos, face);
                h.assertTrue(DesertWallConnections.phase(pos.relative(frame.u()), face) == (phase ^ 1)
                                && DesertWallConnections.phase(pos.relative(frame.v()), face) == (phase ^ 2),
                        "Wall UV phases do not follow adjacent native face coordinates");
                int variant = DesertWallConnections.variant(pos, face);
                h.assertTrue(variant >= 0 && variant < 3, "Wall phase selected a nonexistent variant");
            }
        }
        h.succeed();
    }

    private static BufferedImage image(String name) {
        String path = "/assets/stardewcraft/textures/block/ginger_island/volcano/" + name + ".png";
        try (var input = GingerIslandWallMaterialGameTests.class.getResourceAsStream(path)) {
            if (input == null) throw new AssertionError("Missing packaged wall texture: " + path);
            BufferedImage result = ImageIO.read(input);
            if (result == null) throw new AssertionError("Invalid wall texture: " + path);
            return result;
        } catch (java.io.IOException e) {
            throw new AssertionError("Cannot read wall texture: " + path, e);
        }
    }
}
