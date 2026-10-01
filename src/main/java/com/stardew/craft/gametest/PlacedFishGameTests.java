package com.stardew.craft.gametest;

import com.stardew.craft.port.PortGameTests;
import com.stardew.craft.port.PortItemData;
import com.google.gson.JsonParser;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.PlacedFishBlock;
import com.stardew.craft.blockentity.PlacedFishBlockEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.stardew.craft.port.PortBlockInteraction;

@GameTestHolder("stardewcraft_fish_placement")
@PrefixGameTestTemplate(false)
public final class PlacedFishGameTests {
    private static BlockPos origin(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(8,2,8));
        for(int x=-2;x<=2;x++)for(int y=-1;y<=2;y++)for(int z=-2;z<=2;z++)h.getLevel().setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        return pos;
    }
    private static Player player(GameTestHelper h, GameType mode) {
        var player=PortGameTests.makeMockPlayer(h, mode);mode.updatePlayerAbilities(player.getAbilities());player.setShiftKeyDown(true);return player;
    }
    private static ItemStack fish() {
        var stack=new ItemStack(ModItems.LEGEND.get(),5);QualityHelper.setQuality(stack,3);
        PortItemData.set(stack, DataComponents.CUSTOM_NAME,Component.literal("My first legend"));return stack;
    }
    private static InteractionResult place(GameTestHelper h, Player player, BlockPos support, Direction face, ItemStack fish) {
        player.setItemInHand(InteractionHand.MAIN_HAND,fish);
        var hit=new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,support,false);
        return fish.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
    }
    private static void assertFishDrop(GameTestHelper h, BlockPos pos, ItemStack expected) {
        var items=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3));
        h.assertTrue(items.size()==1&&ItemStack.matches(expected.copyWithCount(1),items.getFirst().getItem()),"Lost, altered or duplicated catch");
        items.forEach(ItemEntity::discard);
    }
    @GameTest(templateNamespace="stardewcraft_fish_placement",template="empty")
    public static void fishDishesAndBottlesSharePlacementLookupAndControls(GameTestHelper h) {
        var pos=origin(h);var level=h.getLevel();level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        var player=player(h,GameType.SURVIVAL);
        h.assertTrue(com.stardew.craft.item.cooking.PlacedFoodPlacement.blockFor(new ItemStack(Items.STONE))==null,"Stone acquired a food placement badge");
        var dish=BuiltInRegistries.ITEM.stream().filter(i->i instanceof com.stardew.craft.item.cooking.CookingDishItem
                &&com.stardew.craft.item.cooking.PlacedFoodPlacement.blockFor(new ItemStack(i))!=null).findFirst().orElseThrow();
        for(var item:java.util.List.of(ModItems.SALMON.get(),ModItems.WINE.get(),ModItems.MEAD.get(),dish)) {
            var held=new ItemStack(item,3);var target=com.stardew.craft.item.cooking.PlacedFoodPlacement.blockFor(held);
            h.assertTrue(target!=null,"Missing shared placement lookup "+item);
            h.assertTrue(place(h,player,pos.below(),Direction.UP,held).consumesAction(),"Shared placement rejected "+item);
            h.assertTrue(level.getBlockState(pos).is(target)&&held.getCount()==2,"Shared placement chose wrong block or count");
            if(item==ModItems.WINE.get()||item==ModItems.MEAD.get())
                h.assertTrue(ItemStack.matches(new ItemStack(item),((com.stardew.craft.blockentity.CookingPlacedFoodBlockEntity)level.getBlockEntity(pos)).getStoredFood()),"Bottle data was not initialized");
            level.removeBlock(pos,false);
        }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_fish_placement",template="empty")
    public static void everyApprovedCatchPlacesAndReturnsItsOriginalComponents(GameTestHelper h) throws Exception {
        var pos=origin(h);var level=h.getLevel();level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        var player=player(h,GameType.SURVIVAL);int count=0;
        try(var reader=new java.io.InputStreamReader(java.util.Objects.requireNonNull(PlacedFishGameTests.class.getResourceAsStream("/assets/stardewcraft/pond_fish/manifest.json")),java.nio.charset.StandardCharsets.UTF_8)) {
            for(var record:JsonParser.parseReader(reader).getAsJsonArray()) {
                String id=record.getAsJsonObject().get("id").getAsString();
                var held=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("stardewcraft",id)),3);
                QualityHelper.setQuality(held,2);PortItemData.set(held, DataComponents.CUSTOM_NAME,Component.literal(id+" trophy"));var expected=held.copyWithCount(1);
                h.assertTrue(place(h,player,pos.below(),Direction.UP,held).consumesAction(),"Cannot place "+id);
                h.assertTrue(held.getCount()==2&&!level.getBlockState(pos).getValue(PlacedFishBlock.WALL),"Wrong consumption or attachment "+id);
                var entity=(PlacedFishBlockEntity)level.getBlockEntity(pos);
                h.assertTrue(ItemStack.matches(expected,entity.fish()),"Components lost "+id);
                var client=new PlacedFishBlockEntity(pos,entity.getBlockState());
                client.onDataPacket(null,entity.getUpdatePacket());
                h.assertTrue(ItemStack.matches(expected,client.fish()),"Live packet lost fish components "+id);
                entity.takeFish();
                client.onDataPacket(null,entity.getUpdatePacket());
                h.assertTrue(client.fish().isEmpty(),"Empty packet retained the old fish "+id);
                entity.storeFish(expected);
                var clone=ModBlocks.PLACED_FISH.get().getCloneItemStack(level.getBlockState(pos),null,level,pos,player);
                h.assertTrue(ItemStack.matches(expected,clone),"Pick block must return the fish "+id);
                player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                PortBlockInteraction.stateUseWithoutItem(level.getBlockState(pos), level,player,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
                h.assertTrue(level.isEmptyBlock(pos)&&ItemStack.matches(expected,player.getMainHandItem()),"Cannot recover "+id);
                h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).isEmpty(),"Pickup also dropped an item "+id);
                count++;
            }
        }
        h.assertTrue(count==71,"Catch coverage changed");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_fish_placement",template="empty")
    public static void wallFacingSupportLossAndBreakingNeverCreateAMountItem(GameTestHelper h) {
        var pos=origin(h);var level=h.getLevel();
        h.assertTrue(ModBlocks.PLACED_FISH.get().asItem()==Items.AIR,"Placed fish must not introduce a new item");
        for(var facing:Direction.Plane.HORIZONTAL)for(var mode:new GameType[]{GameType.SURVIVAL,GameType.CREATIVE})for(boolean removeSupport:new boolean[]{false,true}) {
            var support=pos.relative(facing.getOpposite());level.setBlock(support,Blocks.STONE.defaultBlockState(),3);
            var held=fish();var expected=held.copyWithCount(1);var player=player(h,mode);
            h.assertTrue(place(h,player,support,facing,held).consumesAction(),"Wall placement rejected "+facing);
            var state=level.getBlockState(pos);
            h.assertTrue(state.getValue(PlacedFishBlock.WALL)&&state.getValue(PlacedFishBlock.FACING)==facing,"Wrong wall orientation");
            h.assertTrue(held.getCount()==(mode==GameType.CREATIVE?5:4),"Wrong held count");
            if(removeSupport)level.removeBlock(support,false);else level.destroyBlock(pos,true);
            h.assertTrue(level.isEmptyBlock(pos),"Fish remained suspended after removal");assertFishDrop(h,pos,expected);
            level.removeBlock(support,false);
        }
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        var held=fish();place(h,player(h,GameType.SURVIVAL),pos.below(),Direction.UP,held);
        level.removeBlock(pos.below(),false);h.assertTrue(level.isEmptyBlock(pos),"Ground fish survived missing support");assertFishDrop(h,pos,fish());
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_fish_placement",template="empty")
    public static void invalidSurfacesAndMissingShiftLeaveTheCatchUntouched(GameTestHelper h) {
        var pos=origin(h);var level=h.getLevel();var player=player(h,GameType.SURVIVAL);
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        for(int attempt=0;attempt<6;attempt++) {
            var held=fish();player.setShiftKeyDown(attempt!=0);
            var support=pos.below();var face=Direction.UP;
            if(attempt==1){support=pos.above();face=Direction.DOWN;level.setBlock(support,Blocks.STONE.defaultBlockState(),3);}
            if(attempt==2){support=pos.south();face=Direction.NORTH;level.setBlock(support,Blocks.GLASS_PANE.defaultBlockState(),3);}
            if(attempt==3)level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
            if(attempt==4)level.setBlock(pos,Blocks.WATER.defaultBlockState(),3);
            if(attempt==5)player.getAbilities().mayBuild=false;
            h.assertTrue(!place(h,player,support,face,held).consumesAction(),"Invalid placement accepted "+attempt);
            h.assertTrue(held.getCount()==5&&!level.getBlockState(pos).is(ModBlocks.PLACED_FISH.get()),"Invalid placement consumed fish "+attempt);
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        }
        h.succeed();
    }
}
