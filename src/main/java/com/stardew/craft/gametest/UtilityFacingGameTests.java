package com.stardew.craft.gametest;

import com.google.gson.JsonParser;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_utility_facing")
@PrefixGameTestTemplate(false)
public final class UtilityFacingGameTests {
    @GameTest(templateNamespace = "stardewcraft_utility_facing", template = "ring_utilities")
    public static void fourPlacementDirectionsMatchModelsAndCollision(GameTestHelper h) throws Exception {
        for (Block block : List.of(ModBlocks.FARM_COMPUTER.get(), ModBlocks.MINI_OBELISK.get())) {
            String id = block == ModBlocks.FARM_COMPUTER.get() ? "farm_computer" : "mini_obelisk";
            for (Direction looking : Direction.Plane.HORIZONTAL) {
                BlockPos pos = h.absolutePos(new BlockPos(3, 2, 3));
                var context = new DirectionalPlaceContext(h.getLevel(), pos, looking, new ItemStack(block), Direction.UP);
                var state = block.getStateForPlacement(context);
                h.assertTrue(state != null && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == looking.getOpposite(),
                        id + " does not face placer looking " + looking);
                var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                String variant = id.equals("mini_obelisk")
                        ? "part=main,facing=" + facing.getSerializedName()
                        : "facing=" + facing.getSerializedName() + ",part=main";
                var model = ModelVoxelShapeCache.variantShape("stardewcraft:" + id, variant);
                if (id.equals("mini_obelisk") && !model.isEmpty()) model = Shapes.create(model.bounds());
                h.assertTrue(!model.isEmpty(), "Missing model variant " + id + " " + facing);
                h.assertTrue(!Shapes.joinIsNotEmpty(model, state.getShape(h.getLevel(), pos), BooleanOp.NOT_SAME),
                        "Selection shape differs from rotated model " + id + " " + facing);
                h.assertTrue(!Shapes.joinIsNotEmpty(model, state.getCollisionShape(h.getLevel(), pos), BooleanOp.NOT_SAME),
                        "Collision differs from rotated model " + id + " " + facing);
                h.assertTrue(state.rotate(Rotation.CLOCKWISE_90).getValue(BlockStateProperties.HORIZONTAL_FACING)
                        == facing.getClockWise(), "Structure rotation lost facing");
                for (Mirror mirror : Mirror.values()) {
                    h.assertTrue(state.mirror(mirror).getValue(BlockStateProperties.HORIZONTAL_FACING) == mirror.mirror(facing),
                            "Structure mirror lost facing");
                }
            }
            try (var stream = UtilityFacingGameTests.class.getResourceAsStream(
                    "/assets/stardewcraft/models/block/utility/" + id + ".json")) {
                h.assertTrue(stream != null, "Model absent");
                var model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                var particle = model.getAsJsonObject("textures").get("particle").getAsString();
                String[] parts = particle.split(":", 2);
                try (var texture = UtilityFacingGameTests.class.getResourceAsStream("/assets/" + parts[0] + "/textures/" + parts[1] + ".png")) {
                    h.assertTrue(texture != null, "Missing particle texture");
                }
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_utility_facing", template = "ring_utilities")
    public static void oldSavedStatesKeepTheirModelOrientation(GameTestHelper h) {
        for (String id : List.of("farm_computer", "mini_obelisk")) {
            var tag = new CompoundTag();
            tag.putString("Name", "stardewcraft:" + id);
            var state = NbtUtils.readBlockState(h.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK), tag);
            var original = ModelVoxelShapeCache.shape("stardewcraft:block/utility/" + id);
            if (id.equals("mini_obelisk")) original = Shapes.create(original.bounds());
            if (id.equals("farm_computer")) original = ModelVoxelShapeCache.rotateY(original, 3);
            h.assertTrue(!Shapes.joinIsNotEmpty(original, state.getShape(h.getLevel(), BlockPos.ZERO), BooleanOp.NOT_SAME),
                    "Old saved " + id + " unexpectedly rotated");
        }
        h.succeed();
    }
}
