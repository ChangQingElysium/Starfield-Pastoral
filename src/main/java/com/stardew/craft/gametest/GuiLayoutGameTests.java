package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.GuiLayoutMath;
import com.stardew.craft.client.gui.common.GuiScissorMath;
import org.joml.Matrix4f;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class GuiLayoutGameTests {
    private GuiLayoutGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void guiCanvasAndMouseStayAlignedAcrossScales(GameTestHelper helper) {
        int[][] sizes = {{640, 360}, {960, 540}, {1280, 720}, {1920, 1080}, {1931, 1079},
                {2560, 1440}, {3840, 2160}, {800, 1200}, {3440, 1440}};
        for (int[] size : sizes) {
            var reference = GuiLayoutMath.viewport(size[0], size[1], 4);
            for (int scale = 1; scale <= 8; scale++) {
                int guiWidth = (size[0] + scale - 1) / scale;
                int guiHeight = (size[1] + scale - 1) / scale;
                var layout = GuiLayoutMath.viewport(size[0], size[1], scale);
                helper.assertTrue(layout.width() == reference.width() && layout.height() == reference.height(),
                        "GUI scale changed the authored layout");
                helper.assertTrue(layout.x() >= -1e-8 && layout.y() >= -1e-8
                        && layout.x() + layout.width() * layout.scale() <= guiWidth + 1e-8
                        && layout.y() + layout.height() * layout.scale() <= guiHeight + 1e-8, "canvas exceeds viewport");
                for (double fraction : new double[]{0, 0.1, 0.5, 0.9, 1}) {
                    double logicalX = fraction * layout.width();
                    double logicalY = fraction * layout.height();
                    double renderedX = layout.x() + logicalX * layout.scale();
                    double renderedY = layout.y() + logicalY * layout.scale();
                    // MouseHandler normalizes window coordinates after applying the viewport correction.
                    double rawX = renderedX * scale;
                    double rawY = renderedY * scale;
                    double mouseX = layout.windowMouseX(rawX, size[0]) * layout.width() / size[0];
                    double mouseY = layout.windowMouseY(rawY, size[1]) * layout.height() / size[1];
                    helper.assertTrue(Math.abs(mouseX - logicalX) < 1e-6 && Math.abs(mouseY - logicalY) < 1e-6,
                            "rendered controls and mouse hit boxes diverged");
                    helper.assertTrue(Math.abs(renderedX * scale - (reference.x() + logicalX * reference.scale()) * 4) < 1e-6
                                    && Math.abs(renderedY * scale - (reference.y() + logicalY * reference.scale()) * 4) < 1e-6,
                            "GUI setting moved the physical image");
                }
            }
        }
        var baseline = GuiLayoutMath.viewport(1920, 1080, 4);
        helper.assertTrue(baseline.scale() == 1 && baseline.x() == 0 && baseline.y() == 0,
                "GUI 4 reference layout changed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void guiScissorIncludesLocalPoseAndKeepsPhysicalEdges(GameTestHelper helper) {
        for (int scale = 1; scale <= 8; scale++) {
            var layout = GuiLayoutMath.viewport(1920, 1080, scale);
            var pose = new Matrix4f().translate((float) layout.x(), (float) layout.y(), 0)
                    .scale((float) layout.scale(), (float) layout.scale(), 1);
            var clip = GuiScissorMath.framebuffer(pose, scale, 100, 50, 200, 100);
            helper.assertTrue(clip.equals(new GuiScissorMath.Bounds(400, 200, 800, 400)),
                    "physical scissor diverged from the rendered rectangle");
            pose.translate(20, 10, 0).scale(0.75f, 0.75f, 1);
            var local = GuiScissorMath.framebuffer(pose, scale, 100, 50, 200, 100);
            helper.assertTrue(local.equals(new GuiScissorMath.Bounds(380, 190, 680, 340)),
                    "scissor ignored the page's local transform");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void guiLongContentFitsWithoutChangingItsInternalProportions(GameTestHelper helper) {
        for (int[] size : new int[][]{{640, 360}, {1920, 1080}, {800, 1200}, {3440, 1440}}) {
            for (int scale = 1; scale <= 8; scale++) {
                var layout = GuiLayoutMath.viewport(size[0], size[1], (double) scale, 600, 720);
                helper.assertTrue(layout.width() >= 600 && layout.height() >= 720, "intrinsic content was truncated");
                helper.assertTrue((layout.x() + layout.width() * layout.scale()) * scale <= size[0] + 1e-6
                                && (layout.y() + layout.height() * layout.scale()) * scale <= size[1] + 1e-6,
                        "fitted content escaped physical window");
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void localizedTextFitsBothWidthAndHeightWithoutCropping(GameTestHelper helper) {
        for (int width : new int[]{16, 40, 70, 120, 240, 800}) {
            for (int height : new int[]{9, 12, 18, 24}) {
                float scale = GuiLayoutMath.fitTextScale(0.75F, width, height, 38, 9);
                helper.assertTrue(width * scale <= 38.0001 && height * scale <= 9.0001,
                        "localized HUD text escaped its cell");
                helper.assertTrue(scale > 0 && scale <= 0.75, "fitted text lost its content or grew past authored size");
            }
        }
        helper.assertTrue(GuiLayoutMath.fitTextScale(0.75F, 40, 9, 38, 9) == 0.75F,
                "short text changed size unnecessarily");
        helper.succeed();
    }
}
