package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainVariants;
import com.stardew.craft.block.terrain.TerrainVariantWeights;
import com.stardew.craft.block.terrain.TerrainWorldUpgrade;
import com.stardew.craft.block.terrain.TownPavingConnections;
import com.stardew.craft.item.catalog.StardewCatalogTab;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import com.stardew.craft.port.net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class TownPavingGameTests {
    private TownPavingGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void pavingConnectsAcrossVariantsAndKeepsConcaveCorners(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var paving = ModBlocks.TOWN_PAVING.get().defaultBlockState();
        level.setBlock(pos, paving, 3);
        int[][] offsets = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
        var rows = new HashSet<Integer>();
        for (int mask = 0; mask < 256; mask++) {
            int expected = mask;
            for (int i = 0; i < 8; i++) level.setBlock(pos.offset(offsets[i][0], 0, offsets[i][1]),
                    (mask & 1 << i) == 0 ? Blocks.AIR.defaultBlockState() : paving.setValue(TerrainVariants.PAVING, i % 6), 3);
            if ((mask & 3) != 3) expected &= ~16;
            if ((mask & 6) != 6) expected &= ~32;
            if ((mask & 12) != 12) expected &= ~64;
            if ((mask & 9) != 9) expected &= ~128;
            helper.assertTrue(TownPavingConnections.mask(level, pos) == expected, "Wrong corner/rail selection for neighbors " + mask);
            rows.add(TownPavingConnections.row(expected));
        }
        helper.assertTrue(rows.size() == 47 && rows.contains(0) && rows.contains(46), "Missing connection atlas cells");
        level.setBlock(pos.east(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(pos.east().above(), paving, 3);
        helper.assertTrue((TownPavingConnections.mask(level, pos) & 2) == 0, "Connected to another material or elevation");
        helper.assertTrue(TownPavingConnections.phase(new BlockPos(-1, 0, -1)) == 3
                && TownPavingConnections.phase(new BlockPos(-16, 0, 16)) == 0, "Layout discontinuity at negative coordinates/chunk boundary");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void pavingFixedCopiesPlaceAndSaveEveryStructure(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var block = ModBlocks.TOWN_PAVING.get();
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "Paving test"), ClientInformation.createDefault());
        player.getAbilities().instabuild = true;
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        for (int variant = 0; variant < 6; variant++) {
            var source = block.defaultBlockState().setValue(TerrainVariants.PAVING, variant);
            var ordinary = new ItemStack(block);
            var fixed = TerrainVariants.fixedCopy(ordinary, source);
            helper.assertTrue(!ordinary.has(DataComponents.BLOCK_STATE), "Ctrl-copy fixed the original stack");
            player.setItemInHand(InteractionHand.MAIN_HAND, fixed);
            for (int repeat = 0; repeat < 5; repeat++) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                var context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
                helper.assertTrue(((BlockItem) fixed.getItem()).place(context).consumesAction(), "Paving placement failed");
                var placed = level.getBlockState(pos);
                helper.assertTrue(placed.equals(source), "Copied structure rerolled on placement");
                var nbt = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, placed).getOrThrow();
                helper.assertTrue(BlockState.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow().equals(source), "Lost structure in save");
                helper.assertTrue(TerrainWorldUpgrade.varied(source, 123, pos).equals(source), "Terrain migration changed paving");
            }
        }
        var state = block.defaultBlockState();
        helper.assertTrue(state.isCollisionShapeFullBlock(level, pos) && state.is(BlockTags.MINEABLE_WITH_PICKAXE), "Wrong collision/mining family");
        helper.assertTrue(state.getDestroySpeed(level, pos) == Blocks.STONE_BRICKS.defaultBlockState().getDestroySpeed(level, pos), "Wrong stone hardness");
        var drops = Block.getDrops(state, level, pos, null, null, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()), "Paving did not drop itself");
        helper.assertTrue(StardewItemCatalog.tabForItem(block.asItem()) == StardewCatalogTab.BUILDING, "Missing building catalog entry");
        int[] counts = new int[6];
        for (int roll = 0; roll < 100; roll++) counts[TerrainVariantWeights.paving(roll)]++;
        helper.assertTrue(java.util.Arrays.equals(counts, new int[]{28,10,14,23,22,3}), "Placement weights differ from documented map proportions");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void pavingPackagedSeasonsHaveEveryConnectedVariant(GameTestHelper helper) throws java.io.IOException {
        String root = "/assets/stardewcraft/";
        try (var stream = TownPavingGameTests.class.getResourceAsStream(root + "town_paving_manifest.json")) {
            helper.assertTrue(stream != null, "Missing paving manifest");
            var manifest = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var rows = manifest.getAsJsonArray("rows");
            helper.assertTrue(rows.size() == 47, "Missing border row");
            for (int row = 0; row < 47; row++) helper.assertTrue(TownPavingConnections.row(rows.get(row).getAsInt()) == row, "Java and resource corner order differ");
        }
        for (String season : new String[]{"spring", "summer", "fall", "winter"}) {
            try (var stream = TownPavingGameTests.class.getResourceAsStream(root + "textures/block/town_paving/" + season + "/top.png")) {
                helper.assertTrue(stream != null, "Missing season " + season);
                var image = javax.imageio.ImageIO.read(stream);
                helper.assertTrue(image.getWidth() == 384 && image.getHeight() == 752, "Wrong atlas dimensions");
                for (int row = 0; row < 47; row++) for (int phase = 0; phase < 4; phase++) for (int variant = 1; variant < 6; variant++)
                    for (int i = 0; i < 16; i++) for (int edge = 0; edge < 4; edge++) {
                        int x = edge == 0 ? 0 : edge == 1 ? 15 : i;
                        int y = edge == 2 ? 0 : edge == 3 ? 15 : i;
                        helper.assertTrue(image.getRGB((phase * 6 + variant) * 16 + x, row * 16 + y)
                                == image.getRGB(phase * 96 + x, row * 16 + y), "Variant changed a shared seam in " + season);
                    }
            }
        }
        helper.succeed();
    }
}
