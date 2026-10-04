package com.stardew.craft.gametest;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.gingerisland.GingerIslandAssets;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import java.lang.reflect.Proxy;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockGetter;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_ginger_collision")
@PrefixGameTestTemplate(false)
public final class GingerIslandPlantCollisionGameTests {
    private GingerIslandPlantCollisionGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_ginger_collision", template = "empty")
    public static void herbsRemainSelectableWithoutBlockingMovement(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(5, 3, 5));
        BlockGetter noWorldReads = (BlockGetter) Proxy.newProxyInstance(
                GingerIslandPlantCollisionGameTests.class.getClassLoader(),
                new Class<?>[]{BlockGetter.class},
                (proxy, method, args) -> { throw new AssertionError("Passable herb queried world: " + method.getName()); });
        int owners = 0;
        for (var asset : GingerIslandAssets.blocks()) {
            if (!asset.passable()) continue;
            owners++;
            var block = GingerIslandBlocks.get(asset.id());
            var choices = asset.state_models() == null || asset.state_models().isEmpty()
                    ? java.util.List.of(block.defaultBlockState())
                    : asset.state_models().keySet().stream().map(value ->
                            new BlockItemStateProperties(Map.of(asset.state_property(), value))
                                    .apply(block.defaultBlockState())).toList();
            for (var state : choices) {
                helper.assertTrue(!state.getShape(helper.getLevel(), pos).isEmpty(),
                        "Herb lost its selection shape: " + asset.id());
                for (var part : MapDecorStaticBlock.Part.values()) {
                    helper.assertTrue(state.setValue(MapDecorStaticBlock.PART, part)
                                    .getCollisionShape(noWorldReads, pos).isEmpty(),
                            "Herb blocks movement: " + asset.id() + " / " + part);
                }
            }
        }
        helper.assertTrue(owners == 6, "Reviewed herbaceous owners are missing");
        for (String id : new String[]{"ginger_south_palm", "ginger_golden_walnut_bush_rework"}) {
            helper.assertTrue(!GingerIslandBlocks.get(id).defaultBlockState()
                            .getCollisionShape(helper.getLevel(), pos).isEmpty(),
                    "Tree or walnut obstacle lost its collision: " + id);
        }
        helper.succeed();
    }
}
