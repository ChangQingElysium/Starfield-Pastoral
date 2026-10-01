package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.utility.MachineModelFootprint;
import com.stardew.craft.block.utility.MapUtilityStaticBlock;
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

@GameTestHolder("stardewcraft_craftables")
@PrefixGameTestTemplate(false)
public final class CraftablesModelIntegrationGameTests {
    private static FakePlayer prepare(GameTestHelper h) {
        var level=h.getLevel();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<=5;y++)
            level.setBlock(h.absolutePos(new BlockPos(x,y,z)),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"CraftableTest"));
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
    @GameTest(batch="craftables",templateNamespace="stardewcraft_craftables",template="machine_test",timeoutTicks=100)
    public static void fourFacingsFootprintAndSingleDrop(GameTestHelper h) {
        var player=prepare(h);var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,1,8));
        for(Block block:List.of(ModBlocks.CHEESE_PRESS.get(),ModBlocks.MAYONNAISE_MACHINE.get(),ModBlocks.SEED_MAKER.get(),ModBlocks.CRYSTALARIUM.get(),ModBlocks.WORM_BIN.get(),ModBlocks.DELUXE_WORM_BIN.get(),ModBlocks.LIGHTNING_ROD.get(),ModBlocks.SOLAR_PANEL.get(),ModBlocks.FARM_COMPUTER.get())) {
            for(Direction facing:Direction.Plane.HORIZONTAL) {
                player.setYRot(facing.toYRot());
                var ctx=context(player,block,origin);
                h.assertTrue(((BlockItem)ctx.getItemInHand().getItem()).place(ctx).consumesAction(),"Placement failed "+block+facing);
                var cells=cells(h,block,origin);
                h.assertTrue(cells.size()==2,"Expected exactly two occupied cells "+block+facing+cells);
                h.assertTrue((level.getBlockEntity(origin)!=null)==(block!=ModBlocks.FARM_COMPUTER.get()),"Unexpected production entity");
                BlockPos extension=cells.stream().filter(p->!p.equals(origin)).findFirst().orElseThrow();
                h.assertTrue(level.getBlockEntity(extension)==null,"Duplicate BE on extension");
                for(BlockPos cell:cells) {
                    var shape=level.getBlockState(cell).getCollisionShape(level,cell);
                    h.assertTrue(shape.toAabbs().size()==1,"Must use one overall box per part");
                }
                if(block==ModBlocks.KEG.get()) {
                    var bounds=level.getBlockState(origin).getCollisionShape(level,origin).bounds();
                    h.assertTrue(bounds.maxY==1&&Math.max(bounds.getXsize(),bounds.getZsize())<=2,"Keg exceeds 2x1x1");
                } else if(block!=ModBlocks.SOLAR_PANEL.get()) h.assertTrue(extension.equals(origin.above()),"Tall machine did not reserve upper cell");
                if(block!=ModBlocks.FARM_COMPUTER.get()) h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK,extension,Direction.UP)!=null,"Extension automation lost owner "+block);
                level.getEntitiesOfClass(ItemEntity.class,new AABB(origin).inflate(4)).forEach(ItemEntity::discard);
                // Exercise normal player mining, including the furnace's existing upper-part drop hook.
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

    @GameTest(batch="craftables",templateNamespace="stardewcraft_craftables",template="machine_test",timeoutTicks=100)
    public static void clearanceAndLegacyInventory(GameTestHelper h) {
        var player=prepare(h);var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,1,8));
        for(Block block:List.of(ModBlocks.SEED_MAKER.get(),ModBlocks.DELUXE_WORM_BIN.get(),ModBlocks.FARM_COMPUTER.get())) {
            level.setBlock(pos.above(),Blocks.STONE.defaultBlockState(),3);
            var ctx=context(player,block,pos);
            h.assertTrue(block.getStateForPlacement(ctx)==null,"Tall model placed through occupied upper cell "+block);
            var state=block.defaultBlockState();level.setBlock(pos,state,3);
            var before=level.getBlockEntity(pos);
            MachineModelFootprint.repair(level,pos,state);
            h.assertTrue(level.getBlockState(pos.above()).is(Blocks.STONE),"Legacy repair overwrote a saved block");
            h.assertTrue(level.getBlockEntity(pos)==before,"Legacy repair replaced machine inventory");
            level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
            MachineModelFootprint.repair(level,pos,state);
            h.assertTrue(level.getBlockState(pos.above()).is(block),"Legacy upper cell not repaired");
            h.assertTrue(level.getBlockEntity(pos)==before,"Successful repair replaced machine inventory");
            level.destroyBlock(pos,false);
        }
        h.succeed();
    }

    @GameTest(batch="craftables",templateNamespace="stardewcraft_craftables",template="machine_test",timeoutTicks=100)
    public static void workingReadyPacketRoundTrip(GameTestHelper h) throws ReflectiveOperationException {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,1,8));
        for(Block block:List.of(ModBlocks.KEG.get(),ModBlocks.PRESERVES_JAR.get(),ModBlocks.CHEESE_PRESS.get(),
                ModBlocks.MAYONNAISE_MACHINE.get(),ModBlocks.SEED_MAKER.get(),ModBlocks.CRYSTALARIUM.get(),
                ModBlocks.WORM_BIN.get(),ModBlocks.DELUXE_WORM_BIN.get(),ModBlocks.LIGHTNING_ROD.get(),
                ModBlocks.OIL_MAKER.get(),ModBlocks.LOOM.get())) {
            var factory=(net.minecraft.world.level.block.EntityBlock)block;
            var state=block.defaultBlockState();var original=factory.newBlockEntity(pos,state);
            var data=new net.minecraft.nbt.CompoundTag();
            var stack=new ItemStack(net.minecraft.world.item.Items.DIAMOND);
            data.put("input",stack.save(level.registryAccess()));data.put("product",stack.save(level.registryAccess()));
            data.putLong("readyAtAbsMinute",Long.MAX_VALUE/2);data.putBoolean("ready",false);
            original.loadWithComponents(data,level.registryAccess());
            var received=factory.newBlockEntity(pos,state);
            received.loadWithComponents(original.getUpdateTag(level.registryAccess()),level.registryAccess());
            var working=received.getClass().getMethod("isWorking");
            h.assertTrue((boolean)working.invoke(received),"Work flag lost through packet "+block);
            data.putBoolean("ready",true);original.loadWithComponents(data,level.registryAccess());
            received.loadWithComponents(original.getUpdateTag(level.registryAccess()),level.registryAccess());
            h.assertTrue(!(boolean)working.invoke(received),"Ready machine continues working "+block);
        }
        h.succeed();
    }

    @GameTest(batch="craftables",templateNamespace="stardewcraft_craftables",template="machine_test",timeoutTicks=100)
    public static void computerChunkRepair(GameTestHelper h) {
        prepare(h);var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,1,8));
        var computer=ModBlocks.FARM_COMPUTER.get();
        level.setBlock(pos,computer.defaultBlockState(),3);
        level.setBlock(pos.above(),Blocks.STONE.defaultBlockState(),3);
        var chunk=level.getChunkAt(pos);
        com.stardew.craft.block.utility.FarmComputerFootprintMigration.loaded(
                new net.minecraftforge.event.level.ChunkEvent.Load(chunk,false));
        for(int i=0;i<100;i++) com.stardew.craft.block.utility.FarmComputerFootprintMigration.tick(
                new com.stardew.craft.port.net.neoforged.neoforge.event.tick.LevelTickEvent.Post(() -> true, level));
        h.assertTrue(level.getBlockState(pos.above()).is(Blocks.STONE),"Chunk repair overwrote occupied space");
        level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
        com.stardew.craft.block.utility.FarmComputerFootprintMigration.loaded(
                new net.minecraftforge.event.level.ChunkEvent.Load(chunk,false));
        for(int i=0;i<100;i++) com.stardew.craft.block.utility.FarmComputerFootprintMigration.tick(
                new com.stardew.craft.port.net.neoforged.neoforge.event.tick.LevelTickEvent.Post(() -> true, level));
        h.assertTrue(level.getBlockState(pos.above()).is(computer),"Chunk repair left missing computer upper cell");
        h.succeed();
    }
}
