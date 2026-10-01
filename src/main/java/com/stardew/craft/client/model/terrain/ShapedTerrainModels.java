package com.stardew.craft.client.model.terrain;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainShapeBlock;
import com.stardew.craft.block.terrain.TerrainVariants;
import com.stardew.craft.templates.TemplateBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;

@SuppressWarnings("removal")
@EventBusSubscriber(modid=StardewCraft.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ShapedTerrainModels {
    private ShapedTerrainModels() {}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){
        for(Block block:BuiltInRegistries.BLOCK)if(block instanceof TerrainShapeBlock){
            for(BlockState state:block.getStateDefinition().getPossibleStates()){
                var id=BlockModelShaper.stateToModelLocation(state);event.getModels().put(id,new Surface(event.getModels().get(id),state,false,false));
            }
            var id=new ModelResourceLocation(BuiltInRegistries.BLOCK.getKey(block),"inventory");
            event.getModels().put(id,new Surface(event.getModels().get(id),block.defaultBlockState(),true,false));
        }
    }
    /** Called after the ordinary terrain wrappers, so a cube can receive the edge of a neighboring half block. */
    public static void wrapFull(Map<net.minecraft.resources.ResourceLocation, BakedModel> models){
        for(Block block:List.of(ModBlocks.GRASS_BLOCK.get(),ModBlocks.DARK_GRASS_BLOCK.get(),ModBlocks.DIRT.get(),ModBlocks.CLIFF.get(),ModBlocks.FARMLAND.get(),ModBlocks.SAND.get(),ModBlocks.SANDY_FARMLAND.get(), ModBlocks.HARD_SOIL.get(), ModBlocks.INFERTILE_FARMLAND.get(),
                ModBlocks.TOWN_PAVING.get(),ModBlocks.PLAZA_RED_BRICKS.get(),ModBlocks.ASPHALT_ROAD.get(),ModBlocks.PALE_PAVING.get()))
            for(BlockState state:block.getStateDefinition().getPossibleStates()){
                var id=BlockModelShaper.stateToModelLocation(state);models.put(id,new Surface(models.get(id),state,false,true));
            }
    }
    private static final class Surface extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BlockState state,material;private final boolean item,full;
        private final Map<Key,List<BakedQuad>> cache=java.util.Collections.synchronizedMap(new LinkedHashMap<>(32,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<Key,List<BakedQuad>> e){return size()>128;}});
        Surface(BakedModel model,BlockState state,boolean item,boolean full){super(model);this.state=state;this.material=TerrainShapeBlock.material(state);this.item=item;this.full=full;}
        private BakedModel materialModel(){return full?originalModel:Minecraft.getInstance().getBlockRenderer().getBlockModel(material);}
        @Override public ModelData getModelData(BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData data){
            if(full){
                boolean needed=false;
                for(BlockPos p:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,1,1))){Block b=level.getBlockState(p).getBlock();if(b instanceof TerrainShapeBlock||b instanceof TemplateBlock){needed=true;break;}}
                if(!needed)return originalModel.getModelData(level,pos,state,data);
            }
            return data.derive().with(ShapedMaterialContext.PROPERTY,ShapedMaterialContext.capture(level,pos)).build();
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state,@Nullable Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
        @Override public List<BakedQuad> getQuads(@Nullable BlockState ignored,@Nullable Direction side,RandomSource random,ModelData data,@Nullable RenderType type){
            var context=data.get(ShapedMaterialContext.PROPERTY);
            if(full&&context==null)return originalModel.getQuads(state,side,random,data,type);
            Key key=new Key(side,type,TerrainSeasonTextures.currentTextureSet(),context);
            List<BakedQuad> cached=cache.get(key);
            if(cached!=null)return cached;
            // Source models can query this cache again; never hold its lock while building.
            List<BakedQuad> built=buildQuads(side,type,context);
            List<BakedQuad> existing=cache.putIfAbsent(key,built);
            return existing==null?built:existing;
        }
        private List<BakedQuad> buildQuads(@Nullable Direction side,@Nullable RenderType type,@Nullable ShapedMaterialContext context){
            BakedModel source=materialModel();
            List<BakedQuad> geometry=ShapedMaterialContext.all(originalModel,state,ModelData.EMPTY,null);
            List<BakedQuad> targets=item?(side==null?geometry:List.of()):originalModel.getQuads(state,side,RandomSource.create(42),ModelData.EMPTY,null);
            if(type!=null&&!source.getRenderTypes(material,RandomSource.create(42),ModelData.EMPTY).contains(type))return List.of();
            List<BakedQuad> result=new ArrayList<>();
            for(BakedQuad q:targets){Direction face=q.getDirection();
                result.addAll(ShapedMaterialQuads.map(q,geometry,material.getBlock() instanceof GrassBlock,
                        context==null?List.of():context.breaks(face,true),context==null?List.of():context.breaks(face,false),
                        (patch,f)->context==null?nativeFace(source,material,f,type):context.sources(source,material,patch,f,type)));
            }
            return List.copyOf(result);
        }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){return materialModel().getRenderTypes(material,random,ModelData.EMPTY);}
        @Override public TextureAtlasSprite getParticleIcon(){return materialModel().getParticleIcon(ModelData.EMPTY);}
        @Override public TextureAtlasSprite getParticleIcon(ModelData data){return getParticleIcon();}
        @Override public ItemOverrides getOverrides(){
            if(!item)return super.getOverrides();
            return new ItemOverrides(){@Override public BakedModel resolve(BakedModel model,ItemStack stack,@Nullable ClientLevel level,@Nullable LivingEntity entity,int seed){
                var property=TerrainVariants.property(state);Integer v=property==null?null:PortItemData.getOrDefault(stack, DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).get(property);
                return v==null||v==state.getValue(property)?Surface.this:new Surface(originalModel,state.setValue(property,v),true,false);
            }};
        }
        private record Key(Direction side,RenderType type,int season,ShapedMaterialContext context){}
    }
    public static List<BakedQuad> nativeFace(BakedModel model,BlockState material,Direction face,RenderType type){
        RandomSource r=RandomSource.create(42);List<BakedQuad> out=model.getQuads(material,face,r,ModelData.EMPTY,type);
        return out.isEmpty()?model.getQuads(material,null,r,ModelData.EMPTY,type).stream().filter(q->q.getDirection()==face).toList():out;
    }
}
