package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.GardenPlanterBlock;
import com.stardew.craft.item.catalog.StardewCatalogTab;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class GardenPlanterGameTests {
    private GardenPlanterGameTests() {}
    private static final int[][] OFFSETS = {{0,-1},{1,0},{0,1},{-1,0},{-1,-1},{1,-1},{-1,1},{1,1}};

    @GameTest(templateNamespace = "stardewcraft_planter", template = "ring_utilities")
    public static void planterAllNeighborhoodsUpdateCornersAndCollision(GameTestHelper helper) {
        var level = helper.getLevel(); var block = ModBlocks.GARDEN_PLANTER.get();
        var pos = helper.absolutePos(new BlockPos(8,3,8));
        level.setBlock(pos, block.defaultBlockState(), 3);
        for (int mask = 0; mask < 256; mask++) {
            for (int i = 0; i < 8; i++) level.setBlock(pos.offset(OFFSETS[i][0],0,OFFSETS[i][1]),
                    (mask & (1 << i)) != 0 ? block.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            var state = level.getBlockState(pos);
            for (int i = 0; i < 8; i++) helper.assertTrue(state.getValue(i < 4 ? GardenPlanterBlock.CONNECTIONS[i]
                    : GardenPlanterBlock.DIAGONALS[i-4]) == ((mask & (1 << i)) != 0), "Stale planter neighbor " + mask + ":" + i);
            var shape = state.getCollisionShape(level,pos);
            for (int i = 0; i < 4; i++) {
                boolean corner = (mask & (1 << (i < 2 ? 0 : 2))) == 0 || (mask & (1 << (i % 2 == 0 ? 3 : 1))) == 0
                        || (mask & (1 << (i+4))) == 0;
                double x = i % 2 == 0 ? .01 : .9, z = i < 2 ? .01 : .9;
                helper.assertTrue(Shapes.joinIsNotEmpty(shape, Shapes.box(x,.9,z,x+.08,.98,z+.08), BooleanOp.AND) == corner,
                        "Concave corner collision disagrees with rendering " + mask);
            }
            var rotated = state.rotate(Rotation.CLOCKWISE_90);
            for (int i = 0; i < 4; i++) helper.assertTrue(rotated.getValue(GardenPlanterBlock.CONNECTIONS[(i+1)%4])
                    .equals(state.getValue(GardenPlanterBlock.CONNECTIONS[i])), "Rotation lost cardinal connection");
            int[] diagonalRotation = {1,3,0,2};
            for (int i = 0; i < 4; i++) helper.assertTrue(rotated.getValue(GardenPlanterBlock.DIAGONALS[diagonalRotation[i]])
                    .equals(state.getValue(GardenPlanterBlock.DIAGONALS[i])), "Rotation lost diagonal connection");
            helper.assertTrue(state.mirror(Mirror.LEFT_RIGHT).mirror(Mirror.LEFT_RIGHT).equals(state), "Mirror did not round trip");
            var encoded = BlockState.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow();
            helper.assertTrue(BlockState.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow().equals(state), "Planter state did not persist");
        }
        // Removing only the diagonal of a filled 2x2 restores its concave corner.
        level.setBlock(pos.north().west(), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(!level.getBlockState(pos).getValue(GardenPlanterBlock.DIAGONALS[0]), "Diagonal removal left stale rim");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_planter", template = "ring_utilities")
    public static void planterPlacementDropsOnlyOneAndResealsNeighbor(GameTestHelper helper) {
        var level = helper.getLevel(); var block = ModBlocks.GARDEN_PLANTER.get();
        var pos = helper.absolutePos(new BlockPos(8,3,8));
        var player = FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Planter placement"));
        player.setGameMode(GameType.CREATIVE);
        for (int i = 0; i < 2; i++) {
            var at = pos.east(i);level.setBlock(at.below(),Blocks.STONE.defaultBlockState(),3);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block));
            var ctx = new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(at.below()).add(0,.5,0),Direction.UP,at.below(),false)));
            helper.assertTrue(((BlockItem)ctx.getItemInHand().getItem()).place(ctx).consumesAction(),"Planter item placement failed");
        }
        helper.assertTrue(level.getBlockState(pos).getValue(GardenPlanterBlock.CONNECTIONS[1])
                && level.getBlockState(pos.east()).getValue(GardenPlanterBlock.CONNECTIONS[3]),"Planters did not connect reciprocally");
        helper.assertTrue(level.getBlockState(pos.above()).isAir(),"One-cell planter reserved the flower space");
        level.destroyBlock(pos.east(),true);
        helper.assertTrue(level.getBlockState(pos).is(block) && !level.getBlockState(pos).getValue(GardenPlanterBlock.CONNECTIONS[1]),"Neighbor did not reseal");
        var drops = level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3));
        helper.assertTrue(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==1 && com.stardew.craft.port.PortJava.getFirst(drops).getItem().is(block.asItem()),"Wrong planter drops");
        helper.assertTrue(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE),"Planter missing axe tag");
        helper.assertTrue(StardewItemCatalog.tabForItem(block.asItem())==StardewCatalogTab.BUILDING,"Planter missing building catalog");
        helper.succeed();
    }
    @GameTest(templateNamespace = "stardewcraft_planter", template = "ring_utilities")
    public static void allFlowersUseRealPlacementAndTallPlantsRemainWhole(GameTestHelper helper) {
        var level=helper.getLevel();var pos=helper.absolutePos(new BlockPos(8,3,8));
        var planter=ModBlocks.GARDEN_PLANTER.get();
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Planting flowers"));
        player.setGameMode(GameType.CREATIVE);
        int flowers=0;
        for(var flower:net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            boolean natural=flower instanceof com.stardew.craft.block.decor.NaturalPlantBlock n
                    &&n.kind().habitat==com.stardew.craft.block.decor.NaturalDecorKind.Habitat.LAND;
            if(!(flower.defaultBlockState().is(BlockTags.FLOWERS)||natural)||!(flower.asItem() instanceof BlockItem item))continue;
            level.setBlock(pos.above(2),Blocks.AIR.defaultBlockState(),3);
            level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
            level.setBlock(pos,planter.defaultBlockState(),3);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
            var ctx=new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false)));
            helper.assertTrue(item.place(ctx).consumesAction(),"Flower placement failed: "+flower);
            var planted=level.getBlockState(pos.above());
            helper.assertTrue(planted.is(flower)&&planted.canSurvive(level,pos.above()),"Flower cannot stay: "+flower);
            if(flower instanceof net.minecraft.world.level.block.DoublePlantBlock) {
                helper.assertTrue(level.getBlockState(pos.above(2)).is(flower),"Missing upper flower half");
                helper.assertTrue(GardenPlanterBlock.lowersPlant(level,pos.above(2),level.getBlockState(pos.above(2))),"Upper flower half not lowered");
            }
            if(flower instanceof net.minecraft.world.level.block.BushBlock || flower==Blocks.SPORE_BLOSSOM) {
                helper.assertTrue(GardenPlanterBlock.lowersPlant(level,pos.above(),planted),"Plant not seated in soil");
                helper.assertTrue(planted.getShape(level,pos.above()).min(Direction.Axis.Y)<0,"Plant selection is above its rendered soil base");
            }
            level.destroyBlock(pos,true);
            if(flower instanceof net.minecraft.world.level.block.BushBlock || flower==Blocks.SPORE_BLOSSOM || natural)
                helper.assertTrue(!level.getBlockState(pos.above()).is(flower),"Removed planter left unsupported flower: "+flower);
            flowers++;
        }
        helper.assertTrue(flowers>=20,"Flower test missed vanilla or our plants: "+flowers);
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_planter", template = "ring_utilities")
    public static void genericPlantSupportDoesNotDependOnFlowerIds(GameTestHelper helper) {
        var level=helper.getLevel();var pos=helper.absolutePos(new BlockPos(8,3,8));
        var planter=ModBlocks.GARDEN_PLANTER.get();level.setBlock(pos,planter.defaultBlockState(),3);
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.OAK_SAPLING,Blocks.RED_MUSHROOM,
                Blocks.BROWN_MUSHROOM,Blocks.DEAD_BUSH,Blocks.FERN,Blocks.WHEAT,Blocks.NETHER_WART}) {
            var plant=block.defaultBlockState();
            helper.assertTrue(plant.canSurvive(level,pos.above()),"Standard plant hook was not used: "+block);
            // PORT(1.20.1): Forge has no TriState; "not forced" means a side answer equals the planter's default-rule answer.
            var plantable=(net.minecraftforge.common.IPlantable)block;
            helper.assertTrue(planter.defaultBlockState().canSustainPlant(level,pos,Direction.NORTH,plantable)
                    ==planter.defaultBlockState().canSustainPlant(level,pos,Direction.DOWN,plantable),"Planter forced side planting");
        }
        level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
        helper.assertTrue(!Blocks.OAK_SAPLING.defaultBlockState().canSurvive(level,pos.above()),"Plant support leaked outside planters");
        helper.succeed();
    }

}
