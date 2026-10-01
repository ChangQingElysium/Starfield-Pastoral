package com.stardew.craft.gametest;

import com.stardew.craft.block.FertilizerType;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainFaceConnections;
import com.stardew.craft.block.utility.SprinklerBlock;
import com.stardew.craft.block.utility.SprinklerTier;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.tool.WateringCanItem;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.manager.FertilizerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_sandy_soil")
@PrefixGameTestTemplate(false)
public final class SandySoilGameTests {
    private SandySoilGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_sandy_soil", template = "ring_utilities")
    public static void sandTillsSupportsCropsAndDropsItsOwnSubstrate(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(6, 2, 6));
        var player = FakePlayerFactory.getMinecraft(level);
        var old = player.getMainHandItem();
        try {
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, ModBlocks.SAND.get().defaultBlockState(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            var tilled = level.getBlockState(pos).getToolModifiedState(context, ToolActions.HOE_TILL, false);
            h.assertTrue(tilled != null && tilled.is(ModBlocks.SANDY_FARMLAND.get()), "Sand did not till to sandy farmland");
            level.setBlock(pos, tilled, 3);
            for (Block crop : new Block[]{Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.PUMPKIN_STEM, ModBlocks.PARSNIP_CROP.get()})
                h.assertTrue(crop.defaultBlockState().canSurvive(level, pos.above()), "Unsupported crop " + crop);
            for (int moisture = 0; moisture < 8; moisture++) {
                var state = tilled.setValue(FarmBlock.MOISTURE, moisture);
                h.assertTrue(state.getCollisionShape(level, pos).max(Direction.Axis.Y) == 15/16d, "Wrong soil height");
                var drops = Block.getDrops(state, level, pos, null);
                h.assertTrue(drops.size() == 1 && drops.getFirst().is(ModItems.SAND.get()), "Farmland dropped another substrate");
            }
            h.assertTrue(Blocks.SAND.defaultBlockState().getToolModifiedState(context, ToolActions.HOE_TILL, true) == null,
                    "Vanilla sand identity changed");
            for (Block grass : new Block[]{ModBlocks.GRASS_BLOCK.get(), ModBlocks.DARK_GRASS_BLOCK.get()})
                h.assertTrue(grass.defaultBlockState().getToolModifiedState(context, ToolActions.HOE_TILL, true) == null, "Grass became tillable");
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
            h.assertTrue(ModBlocks.SAND.get().defaultBlockState().getToolModifiedState(context, ToolActions.HOE_TILL, true) == null,
                    "Covered sand allowed tilling");
        } finally { player.setItemInHand(InteractionHand.MAIN_HAND, old); }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_sandy_soil", template = "ring_utilities")
    public static void sprinklerSkipsSandButWateringAndAllFertilizersWork(GameTestHelper h) throws Exception {
        var level = h.getLevel(); var center = h.absolutePos(new BlockPos(6, 3, 6));
        var sandy = center.below().north(); var earth = center.below().south();
        level.setBlock(sandy.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(earth.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(sandy, ModBlocks.SANDY_FARMLAND.get().defaultBlockState(), 3);
        level.setBlock(earth, ModBlocks.FARMLAND.get().defaultBlockState(), 3);
        SprinklerBlock.waterNow(level, center, SprinklerTier.BASIC, false);
        h.assertTrue(level.getBlockState(sandy).getValue(FarmBlock.MOISTURE) == 0, "Sprinkler watered sandy soil");
        h.assertTrue(level.getBlockState(earth).getValue(FarmBlock.MOISTURE) == 7, "Sprinkler failed on ordinary farmland");
        var water = WateringCanItem.class.getDeclaredMethod("waterTile", Level.class, BlockPos.class);
        water.setAccessible(true);
        h.assertTrue((boolean) water.invoke(ModItems.WATERING_CAN.get(), level, sandy), "Watering can rejected sand");
        h.assertTrue(level.getBlockState(sandy).getValue(FarmBlock.MOISTURE) == 7, "Manual watering failed");
        var manager = new FertilizerManager();
        for (FertilizerType type : FertilizerType.values()) {
            h.assertTrue(manager.tryApplyFertilizer(level, sandy, type), "Fertilizer rejected " + type);
            var loaded = FertilizerManager.load(manager.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            h.assertTrue(loaded.getFertilizer(level, sandy) == type, "Saved fertilizer changed");
            h.assertTrue(level.getBlockState(sandy).is(ModBlocks.SANDY_FARMLAND.get()), "Fertilizer changed block identity");
            manager.removeFertilizer(level, sandy);
        }
        var player = FakePlayerFactory.getMinecraft(level);
        var placement = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.SPRINKLER.get()),
                new BlockHitResult(Vec3.atCenterOf(sandy).add(0,.5,0), Direction.UP, sandy, false));
        h.assertTrue(ModBlocks.SPRINKLER.get().getStateForPlacement(placement) == null, "Sprinkler allowed placement on sandy farmland");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_sandy_soil", template = "ring_utilities")
    public static void overnightRetainingSoilAndReversionKeepSandyIdentity(GameTestHelper h) throws Exception {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(4, 2, 5)); var b = a.east(3);
        var manager = new CropGrowthManager(); var fertilizer = FertilizerManager.get(level);
        for (var pos : new BlockPos[]{a,b}) {
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, ModBlocks.SANDY_FARMLAND.get().defaultBlockState().setValue(FarmBlock.MOISTURE,7),3);
            level.setBlock(pos.above(), Blocks.WHEAT.defaultBlockState(),3);
            manager.addCrop(level,pos.above());
        }
        try {
            h.assertTrue(fertilizer.tryApplyFertilizer(level,b,FertilizerType.DELUXE_RETAINING_SOIL), "Cannot apply retaining soil");
            var settle = CropGrowthManager.class.getDeclaredMethod("dryAllFarmland", net.minecraft.server.level.ServerLevel.class);
            settle.setAccessible(true); settle.invoke(manager,level);
            h.assertTrue(level.getBlockState(a).is(ModBlocks.SANDY_FARMLAND.get()) && level.getBlockState(a).getValue(FarmBlock.MOISTURE)==0,
                    "Overnight drying lost soil identity or crop-protected farmland");
            h.assertTrue(level.getBlockState(b).is(ModBlocks.SANDY_FARMLAND.get()) && level.getBlockState(b).getValue(FarmBlock.MOISTURE)==7,
                    "Deluxe retaining soil lost water overnight");
        } finally { fertilizer.removeFertilizer(level,b); }
        level.setBlock(a.above(),Blocks.AIR.defaultBlockState(),3);
        FarmBlock.turnToDirt(null,level.getBlockState(a),level,a);
        h.assertTrue(level.getBlockState(a).is(ModBlocks.SAND.get()), "Reversion returned dirt instead of sand");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_sandy_soil", template = "ring_utilities")
    public static void terrainPrioritiesConnectExposedFacesWithoutBridgingInsetGaps(GameTestHelper h) {
        var dry=ModBlocks.FARMLAND.get().defaultBlockState();var sandy=ModBlocks.SANDY_FARMLAND.get().defaultBlockState();
        h.assertTrue(TerrainFaceConnections.rank(dry)==TerrainFaceConnections.rank(sandy), "Soil families have different dry ranks");
        h.assertTrue(TerrainFaceConnections.rank(dry.setValue(FarmBlock.MOISTURE,7))==TerrainFaceConnections.rank(sandy.setValue(FarmBlock.MOISTURE,7)), "Wet ranks differ");
        var order=java.util.List.of(ModBlocks.CLIFF.get().defaultBlockState(),ModBlocks.SAND.get().defaultBlockState(),
                ModBlocks.DIRT.get().defaultBlockState(),sandy,sandy.setValue(FarmBlock.MOISTURE,7),
                ModBlocks.GRASS_BLOCK.get().defaultBlockState(),ModBlocks.DARK_GRASS_BLOCK.get().defaultBlockState());
        for(int i=1;i<order.size();i++) h.assertTrue(TerrainFaceConnections.rank(order.get(i))>TerrainFaceConnections.rank(order.get(i-1)),"Wrong priority chain");
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(6,3,6));
        for(int x=-1;x<=1;x++)for(int y=0;y<=2;y++)for(int z=-1;z<=1;z++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        level.setBlock(pos,order.get(1),3);level.setBlock(pos.north(),order.get(2),3);
        h.assertTrue(TerrainFaceConnections.collect(level,pos,order.get(1),Direction.UP).stream().anyMatch(c->c.state().is(ModBlocks.DIRT.get())&&!c.folded()),"No dirt-to-sand top edge");
        level.setBlock(pos.north(),Blocks.AIR.defaultBlockState(),3);level.setBlock(pos.north().above(),order.get(1),3);level.setBlock(pos,order.getFirst(),3);
        h.assertTrue(TerrainFaceConnections.collect(level,pos,order.getFirst(),Direction.UP).stream().anyMatch(c->c.folded()&&c.face()==Direction.SOUTH),"No sand-to-cliff floor/wall fold");
        level.setBlock(pos,sandy,3);level.setBlock(pos.north().above(),order.getLast(),3);
        h.assertTrue(TerrainFaceConnections.collect(level,pos,sandy,Direction.UP).isEmpty(),"Connection bridged the recessed top air gap");
        h.succeed();
    }
}
