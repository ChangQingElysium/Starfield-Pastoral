package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.NaturalDecorKind;
import com.stardew.craft.block.decor.NaturalPlantBlock;
import com.stardew.craft.block.decor.FloatingPlantMotion;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.catalog.StardewCatalogTab;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class NaturalDecorGameTests {
    private NaturalDecorGameTests() {}
    private static FakePlayer player(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "Natural decor"));
        player.setGameMode(GameType.CREATIVE); return player;
    }
    private static BlockPlaceContext context(FakePlayer player, ItemStack stack, BlockPos support) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0), Direction.UP, support, false)));
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void floatingPlantsRequireOpenSourceWaterAndDropWithSupport(GameTestHelper helper) {
        var level = helper.getLevel(); var player = player(helper);
        for (var kind : new NaturalDecorKind[]{NaturalDecorKind.FLOATING_LEAF, NaturalDecorKind.WATER_LILY}) {
            var block = ModBlocks.NATURAL_DECOR.get(kind.id).get();
            var pos = helper.absolutePos(new BlockPos(kind == NaturalDecorKind.FLOATING_LEAF ? 5 : 11, 4, 8));
            var invalid = new BlockState[]{Blocks.STONE.defaultBlockState(), Blocks.ICE.defaultBlockState(),
                    Blocks.LAVA.defaultBlockState(), Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 1),
                    Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)};
            for (var support : invalid) {
                level.setBlock(pos.below(), support, 3); level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                helper.assertTrue(!block.canSurvive(block.defaultBlockState(), level, pos), "Accepted invalid water support: " + support);
                var ctx = context(player, new ItemStack(block), pos.below());
                helper.assertTrue(!((BlockItem)ctx.getItemInHand().getItem()).place(ctx).consumesAction(), "Item bypassed water-only placement");
            }
            level.setBlock(pos.below(), Blocks.WATER.defaultBlockState(), 3);
            helper.assertTrue(block.canSurvive(block.defaultBlockState(), level, pos), "Rejected source water");
            // Exercise the actual item water raycast, not a synthetic placement position.
            player.setPos(pos.getX()+.5,pos.getY()+.2,pos.getZ()+2.5);
            player.setYRot(180);
            player.setXRot((float)Math.toDegrees(Math.atan2(player.getEyeY()-(pos.getY()-1.0/9),2)));
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block));
            helper.assertTrue(block.asItem().use(level,player,InteractionHand.MAIN_HAND).getResult().consumesAction(), "Water raycast placement failed");
            helper.assertTrue(level.getBlockState(pos).is(block) && level.getBlockEntity(pos) != null, "Floating renderer entity missing");
            helper.assertTrue(level.getBlockState(pos).getCollisionShape(level,pos).isEmpty(), "Decor has collision");
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(level.getBlockState(pos).isAir(), "Floating decor survived draining");
            int count = level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).stream()
                    .filter(e -> e.getItem().is(block.asItem())).mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(count == 1, "Draining must drop exactly one decoration: " + count);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void landPlantsUsePlanterSoilAndKeepTheirCopiedVariant(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(8,4,8));
        var player = player(helper); var block = ModBlocks.NATURAL_DECOR.get("wild_grass_clump").get();
        level.setBlock(pos.below(),ModBlocks.GARDEN_PLANTER.get().defaultBlockState(),3);
        var source = block.defaultBlockState().setValue(NaturalPlantBlock.VARIANT,1);
        ItemStack fixed = NaturalPlantBlock.fixedCopy(new ItemStack(block),source);
        for (int i=0;i<8;i++) {
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            var ctx=context(player,fixed.copy(),pos.below());
            helper.assertTrue(((BlockItem)fixed.getItem()).place(ctx).consumesAction(),"Plant would not place over planter");
            var state=level.getBlockState(pos);
            helper.assertTrue(state.getValue(NaturalPlantBlock.VARIANT)==1,"Fixed variant rerolled");
            helper.assertTrue(state.getValue(NaturalPlantBlock.IN_PLANTER),"Plant was not lowered to soil");
            helper.assertTrue(state.getShape(level,pos).min(Direction.Axis.Y)==-.25,"Planter plant floats above soil");
            var encoded=BlockState.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow();
            helper.assertTrue(BlockState.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow().equals(state),"Plant state failed save round-trip");
        }
        helper.assertTrue(fixed.get(DataComponents.BLOCK_STATE).get(NaturalPlantBlock.VARIANT)==1,"Fixed item changed");
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        helper.assertTrue(!level.getBlockState(pos).getValue(NaturalPlantBlock.IN_PLANTER),"Plant retained planter offset on ground");
        helper.assertTrue(level.getBlockState(pos).getValue(NaturalPlantBlock.VARIANT)==1,"Support update rerolled plant");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void aquaticGrassNeedsWaterAndRetainsWaterWhenBroken(GameTestHelper helper) {
        var level=helper.getLevel(); var pos=helper.absolutePos(new BlockPos(8,4,8));var player=player(helper);
        var block=ModBlocks.NATURAL_DECOR.get("aquatic_grass_clump").get();
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        helper.assertTrue(block.getStateForPlacement(context(player,new ItemStack(block),pos.below()))==null,"Aquatic grass accepted dry placement");
        level.setBlock(pos,Blocks.WATER.defaultBlockState(),3);
        var ctx=context(player,new ItemStack(block),pos.below());
        helper.assertTrue(((BlockItem)ctx.getItemInHand().getItem()).place(ctx).consumesAction(),"Aquatic grass rejected water");
        helper.assertTrue(level.getBlockState(pos).getValue(NaturalPlantBlock.WATERLOGGED),"Water not retained in aquatic grass");
        level.destroyBlock(pos,true);
        helper.assertTrue(level.getFluidState(pos).is(Fluids.WATER),"Breaking aquatic grass deleted water");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void naturalCatalogAndAnimationKeepStableIdentities(GameTestHelper helper) {
        for (var kind:NaturalDecorKind.values()) {
            var block=ModBlocks.NATURAL_DECOR.get(kind.id).get();
            helper.assertTrue(StardewItemCatalog.tabForItem(block.asItem())==StardewCatalogTab.NATURE,"Decoration misplaced: "+kind.id);
            helper.assertTrue(!block.defaultBlockState().isRandomlyTicking(),"Decor has growth/decay ticks");
        }
        for(var item:new net.minecraft.world.item.Item[]{ModItems.DIRT.get(),ModItems.GRASS_BLOCK.get(),ModItems.DARK_GRASS_BLOCK.get(),ModItems.FARMLAND.get(),ModItems.CLIFF.get()})
            helper.assertTrue(StardewItemCatalog.tabForItem(item)==StardewCatalogTab.NATURE,"Terrain still in building tab");
        for(var item:new net.minecraft.world.item.Item[]{ModItems.TOWN_PAVING.get(),ModItems.ASPHALT_ROAD.get(),ModItems.ROAD_SIGN.get(),ModItems.TICKET_MACHINE.get(),ModItems.GARDEN_PLANTER.get()})
            helper.assertTrue(StardewItemCatalog.tabForItem(item)==StardewCatalogTab.BUILDING,"Built prop outside building tab");
        for(var kind:new NaturalDecorKind[]{NaturalDecorKind.FLOATING_LEAF,NaturalDecorKind.WATER_LILY}) {
            int period = FloatingPlantMotion.period(kind);
            BlockPos pos = new BlockPos(17,64,-31);
            long oldWorldTick = 1L << 40;
            double before = FloatingPlantMotion.phase(kind,pos,oldWorldTick,.99f);
            double after = FloatingPlantMotion.phase(kind,pos,oldWorldTick+1,0);
            helper.assertTrue(Math.abs(FloatingPlantMotion.bob(before)-FloatingPlantMotion.bob(after))<.0001,
                    "Motion jumps at a tick boundary in an old world");
            double start = FloatingPlantMotion.phase(kind,pos,0,0);
            double loop = FloatingPlantMotion.phase(kind,pos,period,0);
            helper.assertTrue(Math.abs(FloatingPlantMotion.bob(start)-FloatingPlantMotion.bob(loop))<1e-9,"Loop is not seamless");
            double subTick = FloatingPlantMotion.phase(kind,pos,0,.5f);
            helper.assertTrue(Math.abs(subTick-start)>1e-4,"Motion ignores partial ticks");
            helper.assertTrue(Math.abs(start-FloatingPlantMotion.phase(kind,pos.east(),0,0))>.001,"Neighbors move in lockstep");
            for(int sample=0;sample<1000;sample++) {
                double phase = 2*Math.PI*sample/1000;
                double bob = FloatingPlantMotion.bob(phase);
                helper.assertTrue(bob>=-.0250001 && bob<=.0750001,"Plant leaves its floating range");
                double lowestCorner = 1.0/16+bob-.5*Math.abs(Math.sin(Math.toRadians(FloatingPlantMotion.pitch(phase))))
                        -.5*Math.abs(Math.sin(Math.toRadians(FloatingPlantMotion.roll(phase))));
                helper.assertTrue(lowestCorner>0,"Rocking dips the leaf below water");
                for(int ring=0;ring<2;ring++) {
                    double progress = FloatingPlantMotion.rippleProgress(phase,ring);
                    helper.assertTrue(progress>=0&&progress<1,"Invalid ripple progress");
                    helper.assertTrue(FloatingPlantMotion.rippleAlpha(progress)>=0&&FloatingPlantMotion.rippleAlpha(progress)<=.550001,"Invalid ripple alpha");
                }
            }
            helper.assertTrue(FloatingPlantMotion.rippleAlpha(0)==0&&FloatingPlantMotion.rippleAlpha(1)<1e-8,
                    "Ripple must be invisible when its expansion resets");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void shippedNaturalModelsResolveSeasonsFramesAndOpaqueParticles(GameTestHelper helper) throws Exception {
        for(var kind:NaturalDecorKind.values()) for(String season:new String[]{"spring","summer","fall","winter"}) {
            int count=kind.variants;
            for(int i=0;i<count;i++) {
                var model=readModel("block/natural/"+kind.id+"/"+season+"/"+i);
                var textures=model.getAsJsonObject("textures");
                helper.assertTrue(textures!=null && textures.has("particle"),"Missing inherited particle: "+kind.id);
                String particle=textures.get("particle").getAsString();
                try(var stream=NaturalDecorGameTests.class.getResourceAsStream("/assets/stardewcraft/textures/"+particle.split(":",2)[1]+".png")) {
                    helper.assertTrue(stream!=null,"Missing particle sprite: "+particle);
                    var image=javax.imageio.ImageIO.read(stream);
                    for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
                        helper.assertTrue((image.getRGB(x,y)>>>24)==255,"Transparent particle: "+particle);
                }
                var elements=model.getAsJsonArray("elements");
                boolean dormant = season.equals("winter") && kind.hiddenInWinter();
                helper.assertTrue(elements!=null && elements.isEmpty()==dormant,
                        "Only dormant winter plants should have empty geometry: "+kind.id+"/"+season);
                for(var element:elements)for(var face:element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    String binding=face.getValue().getAsJsonObject().get("texture").getAsString();
                    String texture=textures.get(binding.substring(1)).getAsString();
                    helper.assertTrue(NaturalDecorGameTests.class.getResource("/assets/stardewcraft/textures/"+texture.split(":",2)[1]+".png")!=null,"Missing material: "+texture);
                }
                if(kind.habitat==NaturalDecorKind.Habitat.SURFACE) {
                    helper.assertTrue(textures.get("1").getAsString().endsWith("_ripples_"+i),"Ripple phase differs from model pose");
                    int y=elements.get(0).getAsJsonObject().getAsJsonArray("from").get(1).getAsInt();
                    helper.assertTrue(y==1,"Continuous motion must start from the full-height native pose");
                }
            }
        }
        helper.succeed();
    }

    private static com.google.gson.JsonObject readModel(String path) throws Exception {
        try(var stream=NaturalDecorGameTests.class.getResourceAsStream("/assets/stardewcraft/models/"+path+".json")) {
            if(stream==null)throw new IllegalStateException("Missing shipped model "+path);
            var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if(json.has("elements"))return json;
            return readModel(json.get("parent").getAsString().split(":",2)[1]);
        }
    }
}
