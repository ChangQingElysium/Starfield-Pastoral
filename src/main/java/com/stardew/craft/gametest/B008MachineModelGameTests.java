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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.capabilities.Capabilities;
import java.util.List;
import java.util.UUID;

@GameTestHolder("stardewcraft_b008")
@PrefixGameTestTemplate(false)
public final class B008MachineModelGameTests {
    private static final List<Block> ANIMATED_UTILITY_MACHINES = List.of(
        ModBlocks.KEG.get(), ModBlocks.PRESERVES_JAR.get(), ModBlocks.CASK.get(),
        ModBlocks.CHEESE_PRESS.get(), ModBlocks.MAYONNAISE_MACHINE.get(), ModBlocks.OIL_MAKER.get(),
        ModBlocks.FURNACE.get(), ModBlocks.HEAVY_FURNACE.get(), ModBlocks.CHARCOAL_KILN.get(),
        ModBlocks.BAIT_MAKER.get(), ModBlocks.CRAB_POT.get(), ModBlocks.CRYSTALARIUM.get(),
        ModBlocks.DEHYDRATOR.get(), ModBlocks.FISH_SMOKER.get(), ModBlocks.HEATER.get(),
        ModBlocks.LIGHTNING_ROD.get(), ModBlocks.RECYCLING_MACHINE.get(), ModBlocks.SEED_MAKER.get(),
        ModBlocks.BONE_MILL.get(), ModBlocks.COFFEE_MAKER.get(), ModBlocks.GEODE_CRUSHER.get(),
        ModBlocks.LOOM.get()
    );

    private static FakePlayer prepare(GameTestHelper h) {
        var level=h.getLevel();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<=5;y++)
            level.setBlock(h.absolutePos(new BlockPos(x,y,z)),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"B008Test"));
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

    @GameTest(batch="b008",templateNamespace="stardewcraft_b008",template="machine_test",timeoutTicks=20)
    public static void idleMachineBodiesUseChunkMeshes(GameTestHelper h) {
        for (Block block : ANIMATED_UTILITY_MACHINES) {
            BlockState idle = block.defaultBlockState();
            Property<?> working = property(idle, "working");
            h.assertTrue(idle.getRenderShape() == RenderShape.MODEL,
                "Idle machine still uses per-frame block-entity rendering: " + block);
            BlockState active = setProperty(idle, working, true);
            h.assertTrue(active.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED,
                "Working machine lost its animation renderer: " + block);

            idle.getProperties().stream().filter(property -> property.getName().equals("part")).findFirst()
                .flatMap(property -> property.getValue("extension").map(value -> new PropertyValue(property, value)))
                .ifPresent(extension -> h.assertTrue(
                    setProperty(idle, extension.property(), extension.value()).getRenderShape() == RenderShape.INVISIBLE,
                    "Machine extension unexpectedly renders a duplicate model: " + block));
        }
        h.succeed();
    }

    private static Property<?> property(BlockState state, String name) {
        return state.getProperties().stream().filter(property -> property.getName().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing " + name + " property on " + state));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setProperty(BlockState state, Property property, Comparable value) {
        return state.setValue(property, value);
    }

    private record PropertyValue(Property<?> property, Comparable<?> value) {}

    @GameTest(batch="b008",templateNamespace="stardewcraft_b008",template="machine_test",timeoutTicks=100)
    public static void fourFacingsFootprintAndSingleDrop(GameTestHelper h) {
        var player=prepare(h);var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,1,8));
        for(Block block:List.of(ModBlocks.KEG.get(),ModBlocks.PRESERVES_JAR.get(),ModBlocks.FURNACE.get(),ModBlocks.HEAVY_FURNACE.get(),ModBlocks.LOOM.get())) {
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
                if(block==ModBlocks.KEG.get()) {
                    var bounds=level.getBlockState(origin).getCollisionShape(level,origin).bounds();
                    h.assertTrue(bounds.maxY==1&&Math.max(bounds.getXsize(),bounds.getZsize())<=2,"Keg exceeds 2x1x1");
                } else h.assertTrue(extension.equals(origin.above()),"Tall machine did not reserve upper cell");
                h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK,extension,Direction.UP)!=null,"Extension automation lost owner");
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
    @GameTest(batch="b008",templateNamespace="stardewcraft_b008",template="machine_test",timeoutTicks=100)
    public static void tallClearanceAndSafeLegacyRepair(GameTestHelper h) {
        var p=prepare(h);var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,1,8));
        for(Block b:List.of(ModBlocks.PRESERVES_JAR.get(),ModBlocks.LOOM.get(),ModBlocks.FURNACE.get(),ModBlocks.HEAVY_FURNACE.get())) {
            level.setBlock(origin.above(),Blocks.STONE.defaultBlockState(),3);
            var ctx=context(p,b,origin);
            h.assertTrue(!((BlockItem)ctx.getItemInHand().getItem()).place(ctx).consumesAction(),"Allowed obstructed tall machine");
            h.assertTrue(level.getBlockState(origin).isAir()&&ctx.getItemInHand().getCount()==2,"Failed placement consumed or partially placed");
            if(b instanceof MapUtilityStaticBlock block) {
                level.setBlock(origin,b.defaultBlockState(),3);
                MachineModelFootprint.repair(level,origin,level.getBlockState(origin));
                h.assertTrue(level.getBlockState(origin.above()).is(Blocks.STONE),"Legacy repair overwrote saved block");
                level.removeBlock(origin.above(),false);
                MachineModelFootprint.repair(level,origin,level.getBlockState(origin));
                h.assertTrue(level.getBlockState(origin.above()).is(b),"Vacant legacy upper cell not repaired");
                MapDecorStaticBlock.runWithDropsSuppressed(()->level.removeBlock(origin,false));
            }
            level.removeBlock(origin.above(),false);
        }
        var keg=(MapUtilityStaticBlock)ModBlocks.KEG.get();
        level.setBlock(origin,keg.defaultBlockState(),3);
        level.setBlock(origin.above(),keg.defaultBlockState().setValue(MapDecorStaticBlock.PART,MapDecorStaticBlock.Part.EXTENSION),2);
        MachineModelFootprint.repair(level,origin,level.getBlockState(origin));
        h.assertTrue(level.getBlockState(origin.above()).isAir(),"Old tall keg left ghost collision");
        h.assertTrue(cells(h,keg,origin).size()==2,"Keg repair lost new footprint");
        h.succeed();
    }
    @GameTest(batch="b008",templateNamespace="stardewcraft_b008",template="machine_test",timeoutTicks=100)
    public static void loomWorkingReadyHarvestAndSavedState(GameTestHelper h) {
        var p=prepare(h);var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,1,8));
        var ctx=context(p,ModBlocks.LOOM.get(),origin);
        h.assertTrue(((BlockItem)ctx.getItemInHand().getItem()).place(ctx).consumesAction(),"Loom placement failed");
        var loom=(LoomBlockEntity)level.getBlockEntity(origin);
        h.assertTrue(!loom.isReady()&&!loom.isWorking(),"New loom not idle");
        h.assertTrue(loom.tryInsert(new ItemStack(ModItems.WOOL.get()),p)&&loom.isWorking(),"Wool did not start threaded wheel state");
        var saved=loom.getUpdateTag(level.registryAccess());
        var restored=new LoomBlockEntity(origin,level.getBlockState(origin));restored.setLevel(level);
        restored.loadWithComponents(saved,level.registryAccess());
        h.assertTrue(restored.isWorking()&&!restored.isReady(),"Working state absent from save/client sync");
        h.assertTrue(loom.applyFairyDust()&&loom.isReady()&&!loom.isWorking(),"Completed loom still rotates");
        h.assertTrue(level.getBlockState(origin).getValue(LoomBlock.READY)&&loom.getProduct().is(ModItems.CLOTH.get()),"Ready cloth state missing");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        var upper=origin.above();
        level.getBlockState(upper).useWithoutItem(level,p,new BlockHitResult(Vec3.atCenterOf(upper),Direction.NORTH,upper,false));
        h.assertTrue(!loom.isReady()&&!loom.isWorking()&&!level.getBlockState(origin).getValue(LoomBlock.READY),"Upper-part harvest did not restore idle");
        h.succeed();
    }
}
