package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.utility.LoomBlock;
import com.stardew.craft.block.utility.MachineModelFootprint;
import com.stardew.craft.block.utility.MapUtilityStaticBlock;
import com.stardew.craft.blockentity.LoomBlockEntity;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.stardew.craft.port.net.neoforged.neoforge.capabilities.Capabilities;
import java.util.List;
import java.util.UUID;


import com.stardew.craft.block.utility.ReclamationMachineBlock;
import com.stardew.craft.blockentity.ReclamationMachineBlockEntity;
import com.stardew.craft.item.artisan.DeconstructorRecipes;
import net.minecraft.world.item.Items;
@GameTestHolder("stardewcraft_reclamation")
@PrefixGameTestTemplate(false)
public final class ReclamationMachineGameTests {
    private static FakePlayer prepare(GameTestHelper h) {
        var level=h.getLevel();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<=5;y++)
            level.setBlock(h.absolutePos(new BlockPos(x,y,z)),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"ReclamationTest"));
        var away=h.absolutePos(new BlockPos(20,1,20));p.setPos(away.getX(),away.getY(),away.getZ());return p;
    }
    private static BlockPlaceContext context(FakePlayer p, Block block, BlockPos pos) {
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block,2));
        return new BlockPlaceContext(new UseOnContext(p,InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false)));
    }
    private static List<BlockPos> cells(GameTestHelper h,Block block,BlockPos origin) {
        return BlockPos.betweenClosedStream(origin.offset(-2,0,-2),origin.offset(2,2,2))
            .filter(p->h.getLevel().getBlockState(p).is(block)).map(BlockPos::immutable).toList();
    }
    @GameTest(batch="reclamation",templateNamespace="stardewcraft_reclamation",template="machine_test",timeoutTicks=100)
    public static void fourFacingsFootprintAndSingleDrop(GameTestHelper h) {
        var player=prepare(h);var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,1,8));
        for(Block block:List.of(ModBlocks.DECONSTRUCTOR.get(),ModBlocks.WOOD_CHIPPER.get())) {
            for(Direction facing:Direction.Plane.HORIZONTAL) {
                player.setYRot(facing.toYRot());
                var ctx=context(player,block,origin);
                h.assertTrue(((BlockItem)ctx.getItemInHand().getItem()).place(ctx).consumesAction(),"Placement failed "+block+facing);
                var cells=cells(h,block,origin);
                h.assertTrue(cells.size()==2,"Expected exactly two occupied cells "+block+facing+cells);
                h.assertTrue(level.getBlockEntity(origin)!=null,"Missing production entity");
                BlockPos extension=cells.stream().filter(p->!p.equals(origin)).findFirst().orElseThrow();
                h.assertTrue(level.getBlockEntity(extension)==null,"Duplicate BE on extension");
                for(BlockPos cell:cells) {
                    var shape=level.getBlockState(cell).getCollisionShape(level,cell);
                    h.assertTrue(shape.toAabbs().size()==1,"Must use one overall box per part");
                }
                h.assertTrue(extension.equals(origin.above()),"Tall machine did not reserve upper cell");
                h.assertTrue(com.stardew.craft.port.PortCapabilities.getCapability(level, Capabilities.ItemHandler.BLOCK,extension,Direction.UP)!=null,"Extension automation lost owner");
                level.getEntitiesOfClass(ItemEntity.class,new AABB(origin).inflate(4)).forEach(ItemEntity::discard);
                // Mining the upper part must remove the whole machine and drop exactly one item.
                var removed = level.getBlockState(extension);
                block.playerWillDestroy(level, extension, removed, player);
                if (level.getBlockState(extension).is(block)) level.destroyBlock(extension,true);
                h.assertTrue(cells(h,block,origin).isEmpty(),"Removal left machine fragments");
                var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(origin).inflate(4));
                h.assertTrue(drops.stream().mapToInt(e->e.getItem().is(block.asItem())?e.getItem().getCount():0).sum()==1,"Expected one machine drop: "+block+" "+facing+" "+drops.stream().map(e->e.getItem().toString()).toList());
                drops.forEach(ItemEntity::discard);
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_reclamation", template="machine_test")
    public static void originalDeconstructionRules(GameTestHelper h) {
        var ringItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("stardewcraft:iridium_band"));
        var ring = DeconstructorRecipes.output(new ItemStack(ringItem));
        h.assertTrue(ring.is(ModItems.IRIDIUM_BAR.get()) && ring.getCount() == 5, "Crafted rings retain original (O) item type and must be accepted");
        var crab = DeconstructorRecipes.output(new ItemStack(ModItems.CRAB_POT.get()));
        h.assertTrue(crab.is(ModItems.COPPER_BAR.get()) && crab.getCount() == 2, "Crab pot must return two copper bars");
        var sprinkler = DeconstructorRecipes.output(new ItemStack(ModBlocks.IRIDIUM_SPRINKLER.get()));
        h.assertTrue(sprinkler.is(ModItems.IRIDIUM_BAR.get()) && sprinkler.getCount() == 1, "Original iridium sprinkler materials lost");
        h.assertTrue(DeconstructorRecipes.output(new ItemStack(ModItems.IRON_BAR.get())).isEmpty(), "Transmutation output name is not a reversible crafting recipe");
        h.assertTrue(DeconstructorRecipes.output(new ItemStack(Items.OAK_PLANKS)).isEmpty(), "Minecraft conversion recipes must not be deconstructed");
        var coal = DeconstructorRecipes.output(new ItemStack(ModBlocks.SCARECROW_0.get()));
        h.assertTrue(coal.is(ModItems.WOOD_NORMAL.get()) && coal.getCount() == 50, "Compare total value, not unit price");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_reclamation", template="machine_test")
    public static void productionSaveSimulationAndFairyDust(GameTestHelper h) {
        var level = h.getLevel();
        int x = 3;
        for (var block : java.util.List.of(ModBlocks.DECONSTRUCTOR.get(), ModBlocks.WOOD_CHIPPER.get())) {
            var pos = h.absolutePos(new BlockPos(x, 2, 3)); x += 4;
            level.setBlock(pos, block.defaultBlockState(), 3);
            var be = (ReclamationMachineBlockEntity) level.getBlockEntity(pos);
            var input = new ItemStack(block == ModBlocks.DECONSTRUCTOR.get() ? ModItems.CRAB_POT.get() : ModItems.DRIFTWOOD.get(), 3);
            h.assertTrue(be.insertAutomation(input, true).getCount() == 2 && !be.isWorking() && be.getProduct().isEmpty(), "Simulation mutated machine");
            h.assertTrue(be.insertAutomation(input, false).getCount() == 2 && be.isWorking() && !be.isReady(), "Machine did not start one item");
            h.assertTrue(be.getRemainingAbsMinutes() == (be.isWoodChipper() ? 180 : 60), "Original processing duration differs");
            var restored = new ReclamationMachineBlockEntity(pos, level.getBlockState(pos)); restored.setLevel(level);
            restored.load(be.getUpdateTag());
            h.assertTrue(restored.isWorking() && ItemStack.matches(restored.getProduct(), be.getProduct())
                    && restored.getStartedAtGameTick() == be.getStartedAtGameTick(), "Save/client sync lost animation or rerolled output");
            h.assertTrue(be.applyFairyDust() && be.isReady() && !be.isWorking() && level.getBlockState(pos).getValue(ReclamationMachineBlock.READY), "Fairy dust ready state missing");
            var result = be.harvestOne();
            h.assertTrue(!result.isEmpty() && !be.isWorking() && !be.isReady() && be.harvestOne().isEmpty(), "Harvest must return to idle and never duplicate");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_reclamation", template="machine_test")
    public static void olderFurnitureReservesUpperCellWithoutOverwriting(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 2, 8));
        for (var block : List.of(ModBlocks.MINI_OBELISK.get(), ModBlocks.FRIDGE.get())) {
            for (var facing : Direction.Plane.HORIZONTAL) {
                var state = block.defaultBlockState().setValue(MapUtilityStaticBlock.FACING, facing);
                level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(pos, state, 3);
                MachineModelFootprint.repair(level, pos, state);
                h.assertTrue(level.getBlockState(pos.above()).is(Blocks.STONE), "Migration overwrote a saved block");
                level.removeBlock(pos.above(), false);
                MachineModelFootprint.repair(level, pos, state);
                var upper = level.getBlockState(pos.above());
                h.assertTrue(upper.is(block) && upper.getValue(MapUtilityStaticBlock.PART) == MapUtilityStaticBlock.Part.EXTENSION,
                    "Taller furniture has no upper collision/interaction cell");
                h.assertTrue(pos.equals(((MapUtilityStaticBlock) block).findMainPos(level, pos.above(), upper)), "Upper part lost its owner");
                h.assertTrue(level.getBlockEntity(pos.above()) == null, "Upper part duplicated storage");
                MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(pos, false));
                h.assertTrue(level.getBlockState(pos.above()).isAir(), "Removing furniture left upper collision");
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_reclamation", template="machine_test")
    public static void registeredInputsHaveCanonicalOutput(GameTestHelper h) {
        for (var id : DeconstructorRecipes.inputs()) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) continue;
            h.assertTrue(!DeconstructorRecipes.output(new ItemStack(item)).isEmpty(), "Missing original winning material for " + id);
        }
        h.succeed();
    }
}
