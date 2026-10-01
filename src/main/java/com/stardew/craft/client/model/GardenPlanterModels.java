package com.stardew.craft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.GardenPlanterBlock;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Seasonal native parts retain cardinal/concave connections and isolated item geometry. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid=StardewCraft.MODID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class GardenPlanterModels {
    private static final String[] SEASONS = {"spring","summer","fall","winter"};
    private static final String[] CORNERS = {"north_west","north_east","south_west","south_east"};
    private static final int[][] CORNER_SIDES = {{0,3},{0,1},{2,3},{2,1}};
    private static final ModelProperty<Boolean> LOWERED = new ModelProperty<>();
    private GardenPlanterModels() {}
    private static ModelResourceLocation id(String season,String part) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/decor/garden_planter/"+season+"/"+part),"standalone");
    }
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        for(String season:SEASONS) {
            event.register(id(season,"garden_planter"));
            for(int mask=0;mask<16;mask++) event.register(id(season,String.format(Locale.ROOT,"garden_planter_%02d",mask)));
            for(String corner:CORNERS) event.register(id(season,"inner_corner_"+corner));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void bake(ModelEvent.ModifyBakingResult event) {
        var models=event.getModels();
        for(BlockState state:ModBlocks.GARDEN_PLANTER.get().getStateDefinition().getPossibleStates()) {
            int mask=0;for(int i=0;i<4;i++)if(state.getValue(GardenPlanterBlock.CONNECTIONS[i]))mask|=1<<i;
            List<List<BakedModel>> seasons=new ArrayList<>();
            for(String season:SEASONS) {
                List<BakedModel> parts=new ArrayList<>();
                parts.add(Objects.requireNonNull(models.get(id(season,String.format(Locale.ROOT,"garden_planter_%02d",mask)))));
                for(int c=0;c<4;c++)if(state.getValue(GardenPlanterBlock.CONNECTIONS[CORNER_SIDES[c][0]])
                        &&state.getValue(GardenPlanterBlock.CONNECTIONS[CORNER_SIDES[c][1]])&&!state.getValue(GardenPlanterBlock.DIAGONALS[c]))
                    parts.add(Objects.requireNonNull(models.get(id(season,"inner_corner_"+CORNERS[c]))));
                seasons.add(List.copyOf(parts));
            }
            var key=BlockModelShaper.stateToModelLocation(state);
            models.put(key,new Seasonal(Objects.requireNonNull(models.get(key)),List.copyOf(seasons)));
        }
        var item=new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,"garden_planter"),"inventory");
        List<List<BakedModel>> itemSeasons=new ArrayList<>();
        for(String season:SEASONS)itemSeasons.add(List.of(Objects.requireNonNull(models.get(id(season,"garden_planter")))));
        models.put(item,new Seasonal(Objects.requireNonNull(models.get(item)),List.copyOf(itemSeasons)));
        // Includes ordinary and modded flowers, tall flowers, saplings, mushrooms and crops.
        // Wrap world models only: inventory models and custom plant renderers retain their behavior.
        for(var block:BuiltInRegistries.BLOCK)if(block instanceof BushBlock || block==net.minecraft.world.level.block.Blocks.SPORE_BLOSSOM) {
            for(var state:block.getStateDefinition().getPossibleStates()) {
                var key=BlockModelShaper.stateToModelLocation(state);var base=models.get(key);
                if(base!=null)models.put(key,new InSoil(base,block==net.minecraft.world.level.block.Blocks.SPORE_BLOSSOM));
            }
        }
    }
    private static final class Seasonal extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final List<List<BakedModel>> seasons;
        Seasonal(BakedModel original,List<List<BakedModel>> seasons){super(original);this.seasons=seasons;}
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state,@Nullable Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state,@Nullable Direction side,RandomSource random,ModelData data,@Nullable RenderType type){
            var out=new ArrayList<BakedQuad>();
            for(var part:seasons.get(TerrainSeasonTextures.currentTextureSet()))out.addAll(part.getQuads(state,side,random,data,type));
            return out;
        }
        @Override public TextureAtlasSprite getParticleIcon(){return seasons.get(TerrainSeasonTextures.currentTextureSet()).getFirst().getParticleIcon();}
        @Override public TextureAtlasSprite getParticleIcon(ModelData data){return getParticleIcon();}
        @Override public BakedModel applyTransform(ItemDisplayContext context,PoseStack pose,boolean left){getTransforms().getTransform(context).apply(left,pose);return this;}
        @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){return List.of(this);}
    }
    private static final class InSoil extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final Map<BakedQuad,BakedQuad> shifted=Collections.synchronizedMap(new WeakHashMap<>());
        private final boolean upright;
        InSoil(BakedModel original,boolean upright){super(original);this.upright=upright;}
        @Override public ModelData getModelData(BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData data){
            return originalModel.getModelData(level,pos,state,data).derive().with(LOWERED,GardenPlanterBlock.lowersPlant(level,pos,state)).build();
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state,@Nullable Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state,@Nullable Direction side,RandomSource random,ModelData data,@Nullable RenderType type){
            var quads=originalModel.getQuads(state,side,random,data,type);
            if(!Boolean.TRUE.equals(data.get(LOWERED)))return quads;
            return quads.stream().map(q->shifted.computeIfAbsent(q,source->{
                int[] vertices=source.getVertices().clone();int stride=vertices.length/4;
                for(int i=0;i<4;i++) {
                    float y=Float.intBitsToFloat(vertices[i*stride+1]);
                    vertices[i*stride+1]=Float.floatToRawIntBits(upright?.75f-y:y-.25f);
                    if(upright) {
                        vertices[i*stride+2]=Float.floatToRawIntBits(1-Float.intBitsToFloat(vertices[i*stride+2]));
                        int normal=vertices[i*stride+7];
                        vertices[i*stride+7]=(normal&0xff0000ff)|((-((byte)(normal>>8))&255)<<8)|((-((byte)(normal>>16))&255)<<16);
                    }
                }
                Direction direction=source.getDirection();
                if(upright && direction.getAxis()!=Direction.Axis.X)direction=direction.getOpposite();
                return new BakedQuad(vertices,source.getTintIndex(),direction,source.getSprite(),source.isShade(),source.hasAmbientOcclusion());
            })).toList();
        }
    }
}
