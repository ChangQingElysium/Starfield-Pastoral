package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.port.PortGameTests;
import com.google.gson.JsonParser;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.gingerisland.GingerIslandAssets;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.GingerIslandStateDecorBlock;
import com.stardew.craft.gingerisland.GingerIslandVariantStacks;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
        helper.assertTrue(owners == 8, "Reviewed herbaceous owners are missing");
        for (String id : new String[]{"ginger_south_palm", "ginger_golden_walnut_bush_rework"}) {
            helper.assertTrue(!GingerIslandBlocks.get(id).defaultBlockState()
                            .getCollisionShape(helper.getLevel(), pos).isEmpty(),
                    "Tree or walnut obstacle lost its collision: " + id);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ginger_collision", template = "empty")
    public static void herbRootsFitBetweenBuildingBlocksWithoutLeafEnvelopeExtensions(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos main = h.absolutePos(new BlockPos(10, 3, 10));
        var player = PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(main.south(5)));
        level.setBlock(main.below(), Blocks.STONE.defaultBlockState(), 2);
        // Leaf rectangles may overlap these cells visually, but cannot reserve
        // or replace them. This catches a passable collider with a huge footprint.
        for (int y = 0; y <= 3; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    level.setBlock(main.offset(x, y, z), Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        int owners = 0;
        for (var asset : GingerIslandAssets.blocks()) {
            if (!asset.passable() && !asset.id().equals("ginger_golden_walnut_bush_rework")) continue;
            owners++;
            var block = (MapDecorStaticBlock) GingerIslandBlocks.get(asset.id());
            var choices = asset.state_models() == null || asset.state_models().isEmpty()
                    ? java.util.List.of(new ItemStack(block)) : GingerIslandVariantStacks.stacksFor(block.asItem());
            for (var choice : choices) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    h.assertTrue(block.placementPositions(main, facing).equals(Set.of(main)),
                            "Plant foliage still claims neighboring cells: " + asset.id() + "/" + facing);
                    player.setYRot(facing.getOpposite().toYRot());
                    var hand = choice.copyWithCount(2);
                    player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                    var hit = new BlockHitResult(Vec3.atCenterOf(main.below()).add(0, .5, 0),
                            Direction.UP, main.below(), false);
                    h.assertTrue(hand.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                            "Plant cannot fit in its single root cell: " + asset.id());
                    var state = level.getBlockState(main);
                    var box = state.getShape(level, main).bounds();
                    h.assertTrue(state.is(block) && hand.getCount() == 1
                                    && box.minX >= 0 && box.minY == 0 && box.minZ >= 0
                                    && box.maxX <= 1 && box.maxY <= .75 && box.maxZ <= 1,
                            "Plant root selection exceeds its local cell: " + asset.id());
                    for (Direction side : Direction.values()) {
                        h.assertTrue(level.getBlockState(main.relative(side)).is(Blocks.STONE),
                                "Plant replaced neighboring construction: " + asset.id());
                    }
                    MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(main, false));
                }
            }
        }
        h.assertTrue(owners == 9, "Root-placement audit skipped an herb or the walnut bush");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ginger_collision", template = "empty")
    public static void emptyWalnutBushKeepsTheSameItemAndLeafGeometry(GameTestHelper h) {
        var level = h.getLevel();
        var main = h.absolutePos(new BlockPos(10, 3, 10));
        var block = (GingerIslandStateDecorBlock) GingerIslandBlocks.get("ginger_golden_walnut_bush_rework");
        var choices = GingerIslandVariantStacks.stacksFor(block.asItem());
        h.assertTrue(choices.size() == 2 && choices.stream().allMatch(stack -> stack.is(block.asItem())),
                "Empty walnut bush is absent from construction choices or uses a second item");
        var ripe = readModel("golden_walnut_bush_rework");
        var empty = readModel("golden_walnut_bush_empty");
        h.assertTrue(ripe.getAsJsonArray("parts").size() == 87 && empty.getAsJsonArray("parts").size() == 77
                        && ripe.get("textures").equals(empty.get("textures")),
                "Empty bush changed the reviewed foliage material or removed the wrong geometry count");
        for (int i = 0; i < 77; i++) {
            h.assertTrue(ripe.getAsJsonArray("parts").get(i + 10).equals(empty.getAsJsonArray("parts").get(i)),
                    "Empty state changes a leaf/stem instead of removing only fruit: part " + i);
        }
        var state = block.defaultBlockState().setValue(GingerIslandStateDecorBlock.VARIANT, 1);
        level.setBlock(main, state, 2);
        h.assertTrue(block.modelForState(state).endsWith("/golden_walnut_bush_empty"),
                "Empty walnut state selects the ripe model");
        var picked = block.getCloneItemStack(level, main, state);
        var drops = Block.getDrops(state, level, main, null);
        for (var stack : java.util.List.of(picked, com.stardew.craft.port.PortJava.getFirst(drops))) {
            h.assertTrue(stack.is(block.asItem()) && PortItemData.get(stack, DataComponents.BLOCK_STATE) != null
                            && "1".equals(PortItemData.get(stack, DataComponents.BLOCK_STATE).properties().get("variant")),
                    "Picking/breaking an empty bush restores its fruit or loses the selected state");
        }
        h.assertTrue(GingerIslandAssets.blocks().stream().filter(asset -> asset.id().startsWith("ginger_golden_walnut_bush")).count() == 1,
                "Walnut world states were registered as separate objects");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ginger_collision", template = "empty")
    public static void canvasFloorCullsSharedCubeBoundaries(GameTestHelper h) {
        var level = h.getLevel();
        var block = GingerIslandBlocks.get("ginger_field_office_canvas_floor");
        var state = block.defaultBlockState();
        var main = h.absolutePos(new BlockPos(8, 3, 8));
        level.setBlock(main, state, 2);
        h.assertTrue(state.canOcclude(), "Canvas floor is still registered as a non-occluding decoration");
        var model = readModel("field_office_canvas_floor");
        var faces = model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
        for (Direction face : Direction.values()) {
            BlockPos neighbor = main.relative(face);
            var material = faces.getAsJsonObject(face.getName());
            h.assertTrue(material.has("cullface") && material.get("cullface").getAsString().equals(face.getName()),
                    "Canvas cube face lacks its boundary cull direction: " + face);
            level.setBlock(neighbor, state, 2);
            h.assertTrue(!Block.shouldRenderFace(state, level, main, face, neighbor),
                    "Canvas floors still render their shared boundary: " + face);
            level.removeBlock(neighbor, false);
            h.assertTrue(Block.shouldRenderFace(state, level, main, face, neighbor),
                    "Canvas floor loses its exposed face: " + face);
        }
        h.succeed();
    }

    private static com.google.gson.JsonObject readModel(String name) {
        String resource = "/assets/stardewcraft/models/block/ginger_island/" + name + ".json";
        try (var input = GingerIslandPlantCollisionGameTests.class.getResourceAsStream(resource)) {
            if (input == null) throw new AssertionError("Missing packaged model: " + resource);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new AssertionError("Cannot read packaged model: " + resource, e);
        }
    }

    @GameTest(templateNamespace = "stardewcraft_ginger_collision", template = "empty")
    public static void oldLeafEnvelopeCellCanBeBuiltOverWithoutRemovingThePlantRoot(GameTestHelper h) {
        var level = h.getLevel();
        var block = (MapDecorStaticBlock) GingerIslandBlocks.get("ginger_island_broadleaf_rosette");
        BlockPos main = h.absolutePos(new BlockPos(5, 3, 5));
        BlockPos ghost = main.east();
        level.setBlock(main.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(ghost.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(main, block.defaultBlockState(), 2);
        level.setBlock(ghost, block.defaultBlockState().setValue(MapDecorStaticBlock.PART,
                MapDecorStaticBlock.Part.EXTENSION), 2);
        var player = PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(main.south(4)));
        var stack = new ItemStack(Blocks.STONE, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(ghost.below()).add(0, .5, 0),
                Direction.UP, ghost.below(), false);
        h.assertTrue(stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                "Old crown EXT still prevents ordinary construction");
        h.assertTrue(level.getBlockState(ghost).is(Blocks.STONE) && stack.getCount() == 1
                        && level.getBlockState(main).is(block)
                        && level.getBlockState(main).getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.MAIN,
                "Replacing the legacy leaf cell deleted the live root or consumed the wrong item count");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ginger_collision", template = "empty")
    public static void fossilWallMountsReserveOnlyTheirCenteredThinColumn(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos main = h.absolutePos(new BlockPos(6, 3, 6));
        for (String id : new String[]{"ginger_dig_ammonite_relief", "ginger_dig_fossil_fragments"}) {
            var block = (MapDecorStaticBlock) GingerIslandBlocks.get(id);
            h.assertTrue(block.placementPositions(main, Direction.NORTH).equals(Set.of(main, main.above())),
                    "Relief still claims neighboring wall columns: " + id);
            var shape = block.defaultBlockState().getShape(level, main).bounds();
            h.assertTrue(shape.minX == .25 && shape.maxX == .75 && shape.maxZ == 1
                            && shape.minZ >= .8 && shape.maxY > 1 && shape.maxY < 2,
                    "Relief mounting column is not centered or flush with its backing wall: " + id);
        }
        var shrine = (MapDecorStaticBlock) GingerIslandBlocks.get("ginger_gem_shrine_assembly");
        var footprint = shrine.placementPositions(main, Direction.NORTH);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                h.assertTrue(footprint.contains(main.offset(dx, 0, dz)),
                        "Shrine no longer reserves its centered odd-width base");
            }
        }
        h.assertTrue(footprint.stream().allMatch(p -> Math.abs(p.getX() - main.getX()) <= 1
                        && Math.abs(p.getZ() - main.getZ()) <= 1),
                "Shrine base is biased to one side of its MAIN");
        h.succeed();
    }
}
