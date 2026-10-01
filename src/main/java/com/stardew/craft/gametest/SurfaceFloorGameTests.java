package com.stardew.craft.gametest;

import com.stardew.craft.port.PortGameTests;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.command.GroundVariantDebugCommand;
import com.stardew.craft.floor.*;
import com.stardew.craft.item.catalog.StardewCatalogTab;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import io.netty.buffer.Unpooled;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_surface_floor")
@PrefixGameTestTemplate(false)
public final class SurfaceFloorGameTests {
    private SurfaceFloorGameTests() {}
    private static ServerPlayer player(GameTestHelper helper) {
        var level = helper.getLevel();
        return new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "Floor test"));
    }

    @GameTest(templateNamespace = "stardewcraft_surface_floor", template = "ring_utilities")
    public static void groundVariantDebugRepairOnlyRerollsEligibleTerrain(GameTestHelper helper) {
        var level = helper.getLevel();
        var start = helper.absolutePos(new BlockPos(1, 1, 1));
        for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++) {
            var state = (x + z) % 2 == 0
                    ? com.stardew.craft.block.ModBlocks.GRASS_BLOCK.get().defaultBlockState()
                    : com.stardew.craft.block.ModBlocks.DIRT.get().defaultBlockState();
            level.setBlock(start.offset(x, 0, z), state, 3);
        }
        var end = start.offset(7, 0, 7);
        var darkGrassPos = start.above();
        var vanillaDirtPos = start.above(2);
        level.setBlock(darkGrassPos, com.stardew.craft.block.ModBlocks.DARK_GRASS_BLOCK.get().defaultBlockState(), 3);
        level.setBlock(vanillaDirtPos, Blocks.DIRT.defaultBlockState(), 3);

        var job = new GroundVariantDebugCommand.BatchJob(start, end);
        var random = net.minecraft.util.RandomSource.create(42L);
        PortGameTests.assertValueEqual(helper, job.processBatch(level, random, 5), 5,
                "debug repair ignored its per-tick block budget");
        helper.assertTrue(!job.isComplete(), "debug repair completed an oversized job in its first batch");
        while (!job.isComplete()) job.processBatch(level, random, 5);
        var result = job.result();
        PortGameTests.assertValueEqual(helper, result.matched(), 64L, "debug repair matched non-varied terrain");
        helper.assertTrue(result.changed() > 0, "debug repair did not change any pre-existing variants");
        PortGameTests.assertValueEqual(helper, result.skippedUnloaded(), 0L, "loaded test positions were skipped");
        long variedGrass = 0L;
        long variedDirt = 0L;
        for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++) {
            var state = level.getBlockState(start.offset(x, 0, z));
            if (state.is(com.stardew.craft.block.ModBlocks.GRASS_BLOCK.get())
                    && state.getValue(com.stardew.craft.block.terrain.TerrainVariants.GRASS) != 0) variedGrass++;
            if (state.is(com.stardew.craft.block.ModBlocks.DIRT.get())
                    && state.getValue(com.stardew.craft.block.terrain.TerrainVariants.DIRT) != 0) variedDirt++;
        }
        helper.assertTrue(variedGrass > 0, "debug repair did not create a visible grass variant");
        helper.assertTrue(variedDirt > 0, "debug repair did not create a visible dirt variant");
        helper.assertTrue(level.getBlockState(darkGrassPos).is(com.stardew.craft.block.ModBlocks.DARK_GRASS_BLOCK.get()),
                "debug repair changed dark grass");
        helper.assertTrue(level.getBlockState(vanillaDirtPos).is(Blocks.DIRT),
                "debug repair changed vanilla dirt");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_surface_floor", template = "ring_utilities")
    public static void allMaterialsPlaceWithoutReplacingTerrainOrFurniture(GameTestHelper helper) {
        var level = helper.getLevel(); var player = player(helper);
        var pos = helper.absolutePos(new BlockPos(5, 1, 5));
        var ground = Blocks.DIRT.defaultBlockState(); var furniture = Blocks.CHEST.defaultBlockState();
        level.setBlock(pos, ground, 3); level.setBlock(pos.above(), furniture, 3);
        var chest = level.getBlockEntity(pos.above());
        var data = SurfaceFloorData.get(level);
        for (var type : SurfaceFloorType.values()) {
            var stack = new ItemStack(type.item(), 2); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0), Direction.UP, pos, false));
            helper.assertTrue(((SurfaceFloorItem) type.item()).onItemUseFirst(stack, context).consumesAction(), "Placement failed: " + type);
            helper.assertTrue(data.at(pos) != null && data.at(pos).type() == type && stack.getCount() == 1, "Wrong cover or consumption: " + type);
            helper.assertTrue(level.getBlockState(pos) == ground && level.getBlockState(pos.above()) == furniture
                    && level.getBlockEntity(pos.above()) == chest, "Changed host, furniture or block entity");
            helper.assertTrue(ground.isCollisionShapeFullBlock(level, pos), "Host collision changed");
            PortGameTests.assertValueEqual(helper, StardewItemDataApi.getSellPrice(stack), 1,
                    "Floor item lost its original 1g base price: " + type);
            ((SurfaceFloorItem) type.item()).onItemUseFirst(stack, context);
            helper.assertTrue(stack.getCount() == 1, "Duplicate placement consumed another item");
            helper.assertTrue(StardewItemCatalog.tabForItem(type.item()) == StardewCatalogTab.BUILDING, "Not in building catalog");
        }
        helper.assertTrue(data.remove(level, pos, false) && !data.remove(level, pos, false), "Removal was not idempotent");
        helper.assertTrue(level.getBlockState(pos) == ground && level.getBlockEntity(pos.above()) == chest, "Removal broke furniture");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_surface_floor", template = "ring_utilities")
    public static void supportDestructionAndGroundChangesHaveDistinctResults(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(5, 1, 5));
        var data = SurfaceFloorData.get(level); var player = player(helper);
        level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        data.place(level, pos, SurfaceFloorType.STEPPING_STONE_PATH, player);
        var cover = data.at(pos);
        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
        helper.assertTrue(cover.equals(data.at(pos)), "Ground conversion rerolled or removed the path");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(data.at(pos) == null, "Unsupported covering survived host destruction (mixin missing)");
        helper.assertTrue(!SurfaceFloorItem.supports(level, pos, Blocks.WATER.defaultBlockState()), "Allowed water");
        helper.assertTrue(!SurfaceFloorItem.supports(level, pos, Blocks.OAK_SLAB.defaultBlockState()), "Allowed a recessed slab surface");
        helper.assertTrue(SurfaceFloorItem.supports(level, pos, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)), "Rejected a full top at Y=1");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_surface_floor", template = "ring_utilities")
    public static void strawNeedsFullSurroundAndConnectionsCrossChunkBorders(GameTestHelper helper) {
        var cells = new HashSet<BlockPos>(); var center = new BlockPos(-16, 0, 16);
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) cells.add(center.offset(x,0,z));
        helper.assertTrue(cells.stream().noneMatch(p -> SurfaceFloorConnections.hasHay(p,cells::contains)), "2x2 produced hay");
        cells.clear(); for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) cells.add(center.offset(x,0,z));
        helper.assertTrue(SurfaceFloorConnections.hasHay(center,cells::contains)
                && cells.stream().filter(p -> SurfaceFloorConnections.hasHay(p,cells::contains)).count() == 1, "3x3 center rule failed");
        cells.remove(center.offset(1,0,1));
        helper.assertTrue(!SurfaceFloorConnections.hasHay(center,cells::contains), "Missing diagonal still produced hay");
        var rows = new HashSet<Integer>();
        for (int mask = 0; mask < 256; mask++) rows.add(SurfaceFloorConnections.row(mask));
        helper.assertTrue(rows.size() == 47 && !rows.contains(-1), "Missing connected state");
        helper.assertTrue(SurfaceFloorConnections.phase(SurfaceFloorType.CRYSTAL,new BlockPos(-1,0,-1)) == 11, "Negative phase failed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_surface_floor", template = "ring_utilities")
    public static void saveAndNetworkPreservePathVariantAndRemoval(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(5,1,5));
        level.setBlock(pos, Blocks.STONE.defaultBlockState(),3);
        var data = SurfaceFloorData.get(level); data.place(level,pos,SurfaceFloorType.STEPPING_STONE_PATH,player(helper));
        var saved = data.save(new CompoundTag());
        var loaded = SurfaceFloorData.load(saved,level.registryAccess());
        helper.assertTrue(data.at(pos).equals(loaded.at(pos)), "Save changed type/variant");
        var packet = new SurfaceFloorPacket(level.dimension().location(),new ChunkPos(pos).toLong(),true,
                List.of(SurfaceFloorPacket.Entry.of(pos,data.at(pos)),SurfaceFloorPacket.Entry.of(pos.east(),null)));
        var buffer = Unpooled.buffer();
        try {
            SurfaceFloorPacket.CODEC.encode(buffer,packet);
            helper.assertTrue(packet.equals(SurfaceFloorPacket.CODEC.decode(buffer)), "Snapshot codec changed state or removal sentinel");
        } finally { buffer.release(); }
        data.remove(level,pos,false);
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_surface_floor", template = "ring_utilities")
    public static void strawArtworkRetainsItsBodyPaletteAndOverhangingTips(GameTestHelper helper) throws Exception {
        String root = "/assets/stardewcraft/textures/block/surface_floor/";
        try (var source = SurfaceFloorGameTests.class.getResourceAsStream(root + "straw_hay.png");
                var itemSource = SurfaceFloorGameTests.class.getResourceAsStream(root + "straw_floor_item.png")) {
            helper.assertTrue(source != null && itemSource != null, "Missing shipped straw artwork");
            var atlas = javax.imageio.ImageIO.read(source);
            var item = javax.imageio.ImageIO.read(itemSource);
            helper.assertTrue(atlas.getWidth() == 256 && atlas.getHeight() == 80, "Wrong straw atlas layout");
            var palette = new HashSet<Integer>();
            for (int tile = 0; tile < 64; tile++) {
                int painted = 0;
                for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    int color = atlas.getRGB(tile % 16 * 16 + x, tile / 16 * 16 + z);
                    if ((color >>> 24) != 0) { painted++; palette.add(color); }
                }
                // The approved center is a dense straw bed, not a few surviving stalks.
                helper.assertTrue(painted >= 224, "Straw body vanished from connected tile " + tile);
            }
            helper.assertTrue(palette.size() == 8, "Straw lost its approved material color groups");
            for (int tip : new int[]{1, 3, 5, 7}) {
                int painted = 0;
                for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                    if ((atlas.getRGB(tip * 16 + x, 64 + z) >>> 24) != 0) painted++;
                helper.assertTrue(painted > 0 && painted <= 16, "Missing or oversized straw tips on wooden neighbor " + tip);
            }
            int itemHay = 0;
            for (int z = 0; z < item.getHeight(); z++) for (int x = 0; x < item.getWidth(); x++)
                if (palette.contains(item.getRGB(x, z))) itemHay++;
            helper.assertTrue(itemHay >= 224, "Held straw floor lost its straw material swatch");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_surface_floor", template = "ring_utilities")
    public static void canceledSupportTransactionsKeepTheirFloor(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(5,1,5));
        var other = pos.east(); var data = SurfaceFloorData.get(level); var player = player(helper);
        for (var p : List.of(pos,other)) {
            level.setBlock(p,Blocks.STONE.defaultBlockState(),3);
            data.place(level,p,SurfaceFloorType.WOOD,player);
        }
        int snapshots = level.capturedBlockSnapshots.size();
        level.captureBlockSnapshots = true;
        try {
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(other,Blocks.AIR.defaultBlockState(),3);
            helper.assertTrue(data.at(pos) != null && data.at(other) != null,"Removed before transaction completed");
            level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
        } finally {
            level.captureBlockSnapshots = false;
            level.capturedBlockSnapshots.subList(snapshots,level.capturedBlockSnapshots.size()).clear();
        }
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(data.at(pos) != null,"Rollback lost the floor");
            helper.assertTrue(data.at(other) == null,"Committed destruction kept the floor");
            data.remove(level,pos,false);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "stardewcraft_surface_floor", template = "ring_utilities")
    public static void shippedAssetsAndRecipesAreSelfContained(GameTestHelper helper) throws Exception {
        try (var stream = SurfaceFloorGameTests.class.getResourceAsStream("/assets/stardewcraft/models/item/surface_floor_display.json")) {
            helper.assertTrue(stream != null, "Missing shared item display model");
            var shared = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
            helper.assertTrue(shared.get("parent").getAsString().equals("minecraft:block/block"),
                    "Invalid vanilla model path: minecraft:block does not exist");
            var display = shared.getAsJsonObject("display");
            for (String slot : List.of("gui", "firstperson_righthand", "firstperson_lefthand", "ground", "fixed")) {
                var transform = display.getAsJsonObject(slot);
                helper.assertTrue(transform != null, "Missing flat-surface display: " + slot);
                double pitch = Math.toRadians(transform.getAsJsonArray("rotation").get(0).getAsDouble());
                helper.assertTrue(Math.abs(Math.sin(pitch)) > .5, "Horizontal surface is edge-on: " + slot);
                helper.assertTrue(transform.getAsJsonArray("scale").get(0).getAsDouble() >= .5, "Item too small: " + slot);
            }
            for (String slot : List.of("thirdperson_righthand", "thirdperson_lefthand"))
                helper.assertTrue(display.has(slot), "Missing third-person display: " + slot);
        }
        for (var type : SurfaceFloorType.values()) {
            String path = "/assets/stardewcraft/models/item/" + type.id + ".json";
            try (var stream = SurfaceFloorGameTests.class.getResourceAsStream(path)) {
                helper.assertTrue(stream != null,"Missing item model " + type);
                var model = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
                helper.assertTrue(model.get("parent").getAsString().equals("stardewcraft:item/surface_floor_display") && !model.has("display"),
                        "Item must inherit the shared flat-surface display");
                for (var entry : model.getAsJsonObject("textures").entrySet()) {
                    var texture = entry.getValue().getAsString().split(":");
                    try (var png = SurfaceFloorGameTests.class.getResourceAsStream("/assets/"+texture[0]+"/textures/"+texture[1]+".png")) {
                        helper.assertTrue(png != null,"Missing texture " + entry.getValue());
                    }
                }
            }
            helper.assertTrue(com.stardew.craft.player.StardewCraftingRecipeData.getRecipe(type.id).isPresent(), "Recipe not loaded: " + type);
        }
        helper.succeed();
    }
}
