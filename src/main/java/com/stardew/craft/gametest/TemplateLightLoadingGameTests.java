package com.stardew.craft.gametest;

import com.stardew.craft.templates.MaterialTemplateBlock;
import com.stardew.craft.templates.RoofTemplateBlock;
import com.stardew.craft.templates.SmartRoofTemplateBlock;
import com.stardew.craft.templates.TemplateContent;
import com.stardew.craft.templates.TemplateShape;
import java.lang.reflect.Proxy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_template_light_loading")
@PrefixGameTestTemplate(false)
public final class TemplateLightLoadingGameTests {
    private TemplateLightLoadingGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_template_light_loading", template = "roof_templates")
    public static void workshopRoofsDoNotReadWorldDuringLighting(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(8, 3, 8));
        int[] blockEntityReads = {0};
        BlockGetter lightingWorld = (BlockGetter) Proxy.newProxyInstance(
                TemplateLightLoadingGameTests.class.getClassLoader(),
                new Class<?>[]{BlockGetter.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMaxLightLevel" -> 15;
                    // ServerLevel returns null here on the lighting worker. Let the old
                    // roof implementation continue to its dangerous neighbor-state read.
                    case "getBlockEntity" -> {
                        blockEntityReads[0]++;
                        yield null;
                    }
                    case "toString" -> "LIGHT-stage world without loaded chunks";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError("Roof lighting queried world: "
                            + method.getName() + " at " + (args == null || args.length == 0 ? "<none>" : args[0]));
                });

        // These are the workshop's two eave profiles. Even with opaque backing,
        // each leaves air within its cell: it must not become a full light blocker.
        // The rotated/flipped cases cover the same physical profile, not new geometry.
        for (TemplateShape shape : new TemplateShape[]{TemplateShape.ROOF_LOWER, TemplateShape.ROOF_UPPER_LOW}) {
            var block = TemplateContent.TEMPLATE_BLOCKS.get(shape).get();
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (boolean flipped : new boolean[]{false, true}) {
                    for (boolean filled : new boolean[]{false, true}) {
                        for (boolean opaque : new boolean[]{true, false}) {
                            var state = block.defaultBlockState()
                                    .setValue(MaterialTemplateBlock.FACING, facing)
                                    .setValue(MaterialTemplateBlock.FLIPPED, flipped)
                                    .setValue(MaterialTemplateBlock.SOLID, opaque)
                                    .setValue(MaterialTemplateBlock.PROPAGATES_SKYLIGHT, !opaque)
                                    .setValue(RoofTemplateBlock.FILLED, filled)
                                    .setValue(SmartRoofTemplateBlock.ROOF_SHAPE, StairsShape.STRAIGHT);
                            String context = shape + " facing=" + facing + " flipped=" + flipped
                                    + " filled=" + filled + " opaque=" + opaque;
                            blockEntityReads[0] = 0;
                            helper.assertTrue(state.getLightBlock(lightingWorld, pos) == 0,
                                    "Partial roof blocked sky light: " + context);
                            helper.assertTrue(state.propagatesSkylightDown(lightingWorld, pos),
                                    "Partial roof lost its sky aperture: " + context);
                            helper.assertTrue(state.getOcclusionShape(lightingWorld, pos).isEmpty(),
                                    "Partial roof acquired a full-cell light occluder: " + context);
                            helper.assertTrue(blockEntityReads[0] == 0,
                                    "Roof lighting required block-entity data: " + context);
                        }
                    }
                }
            }
        }
        helper.succeed();
    }
}
