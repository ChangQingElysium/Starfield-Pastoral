package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.AsphaltRoadConnections;
import com.stardew.craft.block.terrain.RoadMarkingBlock;
import com.stardew.craft.block.terrain.TerrainVariants;
import com.stardew.craft.block.terrain.TerrainWorldUpgrade;
import com.stardew.craft.item.catalog.StardewCatalogTab;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class AsphaltRoadGameTests {
    private static final int[][] OFFSETS = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
    private AsphaltRoadGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void roadsJoinAcrossVariantsAndKeepEveryCurbCorner(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(8,2,8));
        var road = ModBlocks.ASPHALT_ROAD.get().defaultBlockState();
        level.setBlock(pos, road, 3);
        var rows = new HashSet<Integer>();
        for (int mask = 0; mask < 256; mask++) {
            int expected = mask;
            for (int i = 0; i < 8; i++) level.setBlock(pos.offset(OFFSETS[i][0],0,OFFSETS[i][1]),
                    (mask & (1 << i)) == 0 ? Blocks.AIR.defaultBlockState() : road.setValue(TerrainVariants.ASPHALT, i % 3), 3);
            for (int c = 0; c < 4; c++) if ((mask & (1 << c)) == 0 || (mask & (1 << ((c+1)%4))) == 0) expected &= ~(16 << c);
            helper.assertTrue(AsphaltRoadConnections.mask(level,pos) == expected,"Wrong road neighborhood " + mask);
            rows.add(AsphaltRoadConnections.row(expected));
        }
        helper.assertTrue(rows.size() == 47,"Missing curb shapes");
        level.setBlock(pos.east(),ModBlocks.TOWN_PAVING.get().defaultBlockState(),3);
        level.setBlock(pos.east().above(),road,3);
        helper.assertTrue((AsphaltRoadConnections.mask(level,pos)&2)==0,"Road connected to paving or another elevation");
        for (String retired : new String[]{"building_road","building_road_center_line","building_road_double_line","building_road_left","building_road_right","building_tavern_bricks"}) {
            var id=new ResourceLocation(StardewCraft.MODID,retired);
            helper.assertTrue(!BuiltInRegistries.BLOCK.containsKey(id) && !BuiltInRegistries.ITEM.containsKey(id),"Retired block/item remains registered: "+retired);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void roadVariantsPlaceCopyPersistAndDropRoad(GameTestHelper helper) {
        var level=helper.getLevel();var pos=helper.absolutePos(new BlockPos(8,3,8));var block=ModBlocks.ASPHALT_ROAD.get();
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Asphalt variants"));player.setGameMode(GameType.CREATIVE);
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),2);
        var hit=new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false);
        for(int variant=0;variant<3;variant++) {
            var source=block.defaultBlockState().setValue(TerrainVariants.ASPHALT,variant);var plain=new ItemStack(block);
            var fixed=TerrainVariants.fixedCopy(plain,source);
            helper.assertTrue(!PortItemData.has(plain, DataComponents.BLOCK_STATE),"Ctrl-copy altered ordinary items");
            player.setItemInHand(InteractionHand.MAIN_HAND,fixed);
            for(int repeat=0;repeat<4;repeat++) {
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                var context=new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
                helper.assertTrue(((BlockItem)fixed.getItem()).place(context).consumesAction(),"Fixed road placement failed");
                var placed=level.getBlockState(pos);
                helper.assertTrue(placed.equals(source),"Road rerolled a fixed copy");
                var nbt=BlockState.CODEC.encodeStart(NbtOps.INSTANCE,placed).getOrThrow();
                helper.assertTrue(BlockState.CODEC.parse(NbtOps.INSTANCE,nbt).getOrThrow().equals(source),"Road variant was not saved");
                helper.assertTrue(TerrainWorldUpgrade.varied(source,987,pos).equals(source),"Terrain repair changed road variant");
            }
        }
        var seen=new HashSet<Integer>();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block));level.getRandom().setSeed(8192);
        for(int i=0;i<120;i++) {
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            var context=new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
            seen.add(block.getStateForPlacement(context).getValue(TerrainVariants.ASPHALT));
        }
        helper.assertTrue(seen.size()==3,"Ordinary road placement does not select every variant");
        var state=block.defaultBlockState();
        helper.assertTrue(state.isCollisionShapeFullBlock(level,pos) && state.is(BlockTags.MINEABLE_WITH_PICKAXE),"Wrong road collision or mining family");
        var drops=Block.getDrops(state,level,pos,null,null,new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.size()==1 && drops.getFirst().is(block.asItem()),"Road failed to drop itself");
        helper.assertTrue(StardewItemCatalog.tabForItem(block.asItem())==StardewCatalogTab.BUILDING,"Road missing from building catalog");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void independentPaintRotatesHasNoCollisionAndDropsWithItsSupport(GameTestHelper helper) {
        var level=helper.getLevel();var base=helper.absolutePos(new BlockPos(8,3,8));var pos=base.above();
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Road paint"));player.setGameMode(GameType.CREATIVE);
        var hit=new BlockHitResult(Vec3.atCenterOf(base).add(0,.5,0),Direction.UP,base,false);
        for(var block:List.of(ModBlocks.ROAD_DASH.get(),ModBlocks.ROAD_DOUBLE_LINE.get())) {
            helper.assertTrue(StardewItemCatalog.tabForItem(block.asItem())==StardewCatalogTab.BUILDING,"Marking missing from building catalog");
            for(Direction facing:Direction.Plane.HORIZONTAL) {
                player.setYRot(facing.toYRot());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block));
                level.setBlock(base,Blocks.STONE.defaultBlockState(),2);level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                var invalid=new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
                helper.assertTrue(!((BlockItem)block.asItem()).place(invalid).consumesAction(),"Paint attached to non-asphalt");
                level.setBlock(base,ModBlocks.ASPHALT_ROAD.get().defaultBlockState(),3);
                var context=new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
                helper.assertTrue(((BlockItem)block.asItem()).place(context).consumesAction(),"Paint placement failed");
                var state=level.getBlockState(pos);
                helper.assertTrue(state.getValue(RoadMarkingBlock.FACING)==facing,"Paint orientation differs from placement");
                helper.assertTrue(state.rotate(Rotation.CLOCKWISE_90).getValue(RoadMarkingBlock.FACING)==facing.getClockWise(),"Paint rotation broken");
                helper.assertTrue(state.getCollisionShape(level,pos).isEmpty() && !state.getShape(level,pos).isEmpty(),"Paint is collidable or unselectable");
                level.destroyBlock(pos,true);
                helper.assertTrue(level.getBlockState(base).is(ModBlocks.ASPHALT_ROAD.get()),"Removing paint removed the road");
                var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(base).inflate(2),e->e.getItem().is(block.asItem()));
                helper.assertTrue(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==1,"Paint should drop one item");drops.forEach(Entity::discard);
                level.setBlock(pos,state,3);level.destroyBlock(base,true);
                helper.assertTrue(level.getBlockState(pos).isAir(),"Removing road left floating paint");
                drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(base).inflate(2),e->e.getItem().is(block.asItem()));
                helper.assertTrue(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==1,"Support loss duplicated or lost paint");
                level.getEntitiesOfClass(ItemEntity.class,new AABB(base).inflate(2)).forEach(Entity::discard);
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void allSeasonAtlasesIncludeRotatedPaintAndClipItAtTheCurbs(GameTestHelper helper) throws java.io.IOException {
        String root="/assets/stardewcraft/";
        com.google.gson.JsonArray rows;
        try(var stream=AsphaltRoadGameTests.class.getResourceAsStream(root+"asphalt_road_manifest.json")) {
            helper.assertTrue(stream!=null,"Missing road manifest");
            rows=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("rows");
        }
        helper.assertTrue(rows.size()==47,"Missing road masks");
        for(String season:new String[]{"spring","summer","fall","winter"}) {
            var top=image(root+"textures/block/asphalt_road/"+season+"/top.png");
            var marks=image(root+"textures/block/asphalt_road/"+season+"/markings.png");
            helper.assertTrue(top.getWidth()==48 && top.getHeight()==752 && marks.getWidth()==128 && marks.getHeight()==752,"Incorrect atlas dimensions");
            for(int row=0;row<47;row++) {
                int mask=rows.get(row).getAsInt();helper.assertTrue(AsphaltRoadConnections.row(mask)==row,"Java/resource curb order differs");
                for(int style=0;style<2;style++) {
                    var raw=image(root+"textures/block/asphalt_road/"+season+(style==0?"/dash.png":"/double.png"));
                    for(int rotation=0;rotation<4;rotation++) for(int y=0;y<16;y++) for(int x=0;x<16;x++) {
                        int u=x,v=y;
                        for(int turn=0;turn<rotation;turn++){int old=u;u=v;v=15-old;}
                        int expected=raw.getRGB(u,v);
                        for(int n=0;n<8;n++) if((mask&(1<<n))==0) {
                            int dx=OFFSETS[n][0],dz=OFFSETS[n][1];
                            int distance=Math.max(Math.max(Math.max(dx*16-x,0),x-(dx*16+15)),Math.max(Math.max(dz*16-y,0),y-(dz*16+15)));
                            if(distance<=3) expected=0;
                        }
                        helper.assertTrue(marks.getRGB((style*4+rotation)*16+x,row*16+y)==expected,"Paint rotation/clipping mismatch in "+season);
                    }
                }
            }
        }
        helper.succeed();
    }

    private static java.awt.image.BufferedImage image(String name) throws java.io.IOException {
        try(var stream=AsphaltRoadGameTests.class.getResourceAsStream(name)) {
            if(stream==null) throw new java.io.IOException("Missing packaged image "+name);
            return javax.imageio.ImageIO.read(stream);
        }
    }
}
