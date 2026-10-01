package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.templates.MaterialTemplateBlock;
import com.stardew.craft.templates.TemplateBlockEntity;
import com.stardew.craft.templates.TemplateContent;
import com.stardew.craft.templates.TemplateShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class TemplateLightingGameTests {
    private TemplateLightingGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void partialTemplatesTransmitLightAndRefreshMaterialOcclusion(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        for (var entry : TemplateContent.TEMPLATE_BLOCKS.entrySet()) {
            if (!(entry.getValue().get() instanceof MaterialTemplateBlock block)) continue;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (boolean flipped : new boolean[]{false, true}) {
                    var state = block.defaultBlockState().setValue(MaterialTemplateBlock.FACING, direction)
                            .setValue(MaterialTemplateBlock.FLIPPED, flipped);
                    level.setBlock(pos, state, Block.UPDATE_ALL);
                    var template = (TemplateBlockEntity) level.getBlockEntity(pos);
                    template.setMaterial(Blocks.STONE.defaultBlockState());
                    state = level.getBlockState(pos);
                    if (!Block.isShapeFullBlock(state.getShape(level, pos))) {
                        helper.assertTrue(state.getLightBlock(level, pos) == 0, "Partial template consumes skylight: " + entry.getKey());
                    }
                    template.setMaterial(Blocks.GLASS.defaultBlockState());
                    var glass = level.getBlockState(pos);
                    helper.assertTrue(!glass.getValue(MaterialTemplateBlock.SOLID) && glass.getOcclusionShape(level, pos).isEmpty(),
                            "Glass template still has opaque occlusion: " + entry.getKey());
                    helper.assertTrue(glass.getLightBlock(level, pos) == 0, "Glass template blocks light: " + entry.getKey());
                    template.setMaterial(Blocks.STONE.defaultBlockState());
                    var fill = template.effectiveFillMaterial();
                    helper.assertTrue(level.getBlockState(pos).getValue(MaterialTemplateBlock.SOLID)
                                    == (fill == null || fill.canOcclude()), "Combined material occlusion is incorrect");
                    if (block instanceof com.stardew.craft.templates.CompositeTemplateBlock) {
                        template.setFillMaterial(Blocks.STONE.defaultBlockState());
                        helper.assertTrue(level.getBlockState(pos).getValue(MaterialTemplateBlock.SOLID),
                                "Opaque primary and fill did not restore occlusion");
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void changingTemplateMaterialUpdatesWorldLightWithoutReplacingShape(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var state = TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.SLAB).get().defaultBlockState();
        level.setBlock(pos, state, Block.UPDATE_ALL);
        var template = (TemplateBlockEntity) level.getBlockEntity(pos);
        template.setMaterial(Blocks.GLOWSTONE.defaultBlockState());
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(level.getBrightness(LightLayer.BLOCK, pos.above()) >= 14,
                "Glowing template light: emission=" + level.getBlockState(pos).getLightEmission(level, pos)
                        + ", at=" + level.getBrightness(LightLayer.BLOCK, pos) + ", above=" + level.getBrightness(LightLayer.BLOCK, pos.above())
                        + ", material=" + template.material()))
                .thenExecute(() -> template.setMaterial(Blocks.STONE.defaultBlockState()))
                .thenWaitUntil(() -> helper.assertTrue(level.getBrightness(LightLayer.BLOCK, pos.above()) == 0,
                        "Removed glowing material left stale block light"))
                .thenSucceed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void everyTemplatePublishesAndClearsThreadSafeMaterialLight(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        for (var entry : TemplateContent.TEMPLATE_BLOCKS.entrySet()) {
            var state = entry.getValue().get().defaultBlockState();
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            var template = (TemplateBlockEntity) level.getBlockEntity(pos);
            template.setMaterial(Blocks.GLOWSTONE.defaultBlockState());
            helper.assertTrue(level.getBlockState(pos).getLightEmission(level, pos) == 15
                            && level.getAuxLightManager(pos).getLightAt(pos) == 15,
                    "Template did not publish its light for background lighting/chunk saves: " + entry.getKey());
            template.setMaterial(null);
            helper.assertTrue(level.getAuxLightManager(pos).getLightAt(pos) == 0, "Removing a material left cached light");
            template.setMaterial(Blocks.GLOWSTONE.defaultBlockState());
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.assertTrue(level.getAuxLightManager(pos).getLightAt(pos) == 0, "Breaking a template left ghost light");
        }
        helper.succeed();
    }
}
