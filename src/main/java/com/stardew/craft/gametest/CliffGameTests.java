package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainFaceConnections;
import com.stardew.craft.block.terrain.TerrainVariants;
import com.stardew.craft.block.terrain.TerrainVariantWeights;
import com.stardew.craft.block.terrain.TerrainWorldUpgrade;
import com.stardew.craft.item.catalog.StardewCatalogTab;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import java.util.Arrays;
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
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class CliffGameTests {
    private CliffGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void cliffFixedVariantsPersistAndDropCliff(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8,3,8));
        var block = ModBlocks.CLIFF.get();
        var player = new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"Cliff test"),ClientInformation.createDefault());
        player.getAbilities().instabuild = true;
        level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),2);
        for (int variant = 0; variant < 6; variant++) {
            var state = block.defaultBlockState().setValue(TerrainVariants.CLIFF,variant);
            var ordinary = new ItemStack(block);
            var fixed = TerrainVariants.fixedCopy(ordinary,state);
            helper.assertTrue(!ordinary.has(DataComponents.BLOCK_STATE),"Fixed-copy modified ordinary stack");
            player.setItemInHand(InteractionHand.MAIN_HAND,fixed);
            for (int repeat = 0; repeat < 5; repeat++) {
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                var context = new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
                helper.assertTrue(((BlockItem)fixed.getItem()).place(context).consumesAction(),"Cliff placement failed");
                helper.assertTrue(level.getBlockState(pos).equals(state),"Copied cliff rerolled");
                var nbt = BlockState.CODEC.encodeStart(NbtOps.INSTANCE,state).getOrThrow();
                helper.assertTrue(BlockState.CODEC.parse(NbtOps.INSTANCE,nbt).getOrThrow().equals(state),"Lost saved cliff variant");
                helper.assertTrue(TerrainWorldUpgrade.varied(state,123,pos).equals(state),"Terrain repair rerolled cliff");
            }
        }
        int[] counts = new int[6];
        for (int roll = 0; roll < 1000; roll++) counts[TerrainVariantWeights.cliff(roll)]++;
        helper.assertTrue(Arrays.equals(counts,new int[]{495,225,180,55,25,20}),"Incorrect cliff placement probabilities");
        var state = block.defaultBlockState();
        helper.assertTrue(state.isCollisionShapeFullBlock(level,pos) && state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                && state.requiresCorrectToolForDrops(),"Wrong cliff collision/mining properties");
        var drops = Block.getDrops(state,level,pos,null,null,new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()),"Cliff requires silk touch or drops the wrong block");
        helper.assertTrue(StardewItemCatalog.tabForItem(block.asItem()) == StardewCatalogTab.NATURE,"Cliff missing from nature catalog");
        helper.succeed();
    }

    private static void clear(GameTestHelper helper, BlockPos pos) {
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,1,1)))
            helper.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),2);
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void cliffConnectionsCoverEveryFaceAndFold(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8,3,8));
        var cliff = ModBlocks.CLIFF.get().defaultBlockState();
        var dirt = ModBlocks.DIRT.get().defaultBlockState();
        int cases = 0;
        for (Direction face : Direction.values()) {
            for (int edge = 0; edge < 4; edge++) for (boolean folded : new boolean[]{false,true}) {
                clear(helper,pos);
                var tangent = TerrainFaceConnections.tangent(face,edge);
                var neighbor = pos.relative(tangent);
                if (folded) neighbor = neighbor.relative(face);
                level.setBlock(pos,cliff,2); level.setBlock(neighbor,dirt,2);
                var connections = TerrainFaceConnections.collect(level,pos,cliff,face);
                helper.assertTrue(connections.size() == 1,"Missing/extra edge " + face + " " + edge + " folded=" + folded);
                var source = connections.getFirst();
                helper.assertTrue(source.edge() == edge && source.folded() == folded && !source.corner()
                        && source.face() == (folded ? tangent.getOpposite() : face),"Wrong donor face or orientation");
                level.setBlock(pos.relative(face),Blocks.STONE.defaultBlockState(),2);
                helper.assertTrue(TerrainFaceConnections.collect(level,pos,cliff,face).isEmpty(),"Hidden face connected");
                level.setBlock(pos.relative(face),Blocks.AIR.defaultBlockState(),2);
                level.setBlock(pos,dirt,2); level.setBlock(neighbor,cliff,2);
                helper.assertTrue(TerrainFaceConnections.collect(level,pos,dirt,face).isEmpty(),"Low priority cliff spread onto dirt");
                cases++;
            }
            for (int corner = 0; corner < 4; corner++) {
                clear(helper,pos);
                var neighbor = pos.relative(TerrainFaceConnections.tangent(face,corner))
                        .relative(TerrainFaceConnections.tangent(face,(corner+1)%4));
                level.setBlock(pos,cliff,2); level.setBlock(neighbor,dirt,2);
                var connections = TerrainFaceConnections.collect(level,pos,cliff,face);
                helper.assertTrue(connections.size() == 1 && connections.getFirst().corner()
                        && connections.getFirst().edge() == corner,"Missing diagonal corner on " + face);
                cases++;
            }
        }
        helper.assertTrue(cases == 72,"Missing directional cases");
        clear(helper,pos);
        var dry = ModBlocks.FARMLAND.get().defaultBlockState();
        var wet = dry.setValue(FarmBlock.MOISTURE,7);
        var grass = ModBlocks.GRASS_BLOCK.get().defaultBlockState();
        var dark = ModBlocks.DARK_GRASS_BLOCK.get().defaultBlockState();
        var sand = ModBlocks.SAND.get().defaultBlockState();
        var hardSoil = ModBlocks.HARD_SOIL.get().defaultBlockState();
        var chain = new BlockState[]{cliff,hardSoil,sand,dirt,dry,wet,grass,dark};
        for (int rank = 0; rank < chain.length; rank++) helper.assertTrue(TerrainFaceConnections.rank(chain[rank]) == rank,"Wrong material hierarchy");
        level.setBlock(pos,cliff,2); level.setBlock(pos.below().south(),wet,2);
        helper.assertTrue(TerrainFaceConnections.collect(level,pos,cliff,Direction.SOUTH).isEmpty(),"Connected through the inset farmland air gap");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void cliffPackagedSeasonsKeepSharedSeams(GameTestHelper helper) throws java.io.IOException {
        for (String season : new String[]{"spring","summer","fall","winter"}) {
            java.awt.image.BufferedImage first = null;
            for (String variant : new String[]{"plain","broad","split","plain_moss","broad_moss","split_moss"}) {
                String path = "/assets/stardewcraft/textures/block/cliff/" + season + "/" + variant + ".png";
                try (var stream = CliffGameTests.class.getResourceAsStream(path)) {
                    helper.assertTrue(stream != null,"Missing packaged cliff texture " + path);
                    var image = javax.imageio.ImageIO.read(stream);
                    helper.assertTrue(image.getWidth() == 16 && image.getHeight() == 16,"Cliff is not native 16px");
                    if (first == null) first = image;
                    for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if (x < 2 || x > 13 || y < 2 || y > 13)
                        helper.assertTrue(image.getRGB(x,y) == first.getRGB(x,y),"Variant damaged shared seam " + path);
                }
                try (var stream = CliffGameTests.class.getResourceAsStream("/assets/stardewcraft/models/block/cliff/" + season + "/" + variant + ".json")) {
                    helper.assertTrue(stream != null,"Missing seasonal native model");
                    var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject();
                    helper.assertTrue(json.get("parent").getAsString().equals("minecraft:block/cube_all"),"Cliff stopped using a native cube");
                }
            }
        }
        helper.succeed();
    }
}
