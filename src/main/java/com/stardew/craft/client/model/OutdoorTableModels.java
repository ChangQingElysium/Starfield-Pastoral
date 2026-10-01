package com.stardew.craft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.OutdoorTableBlock;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;

/** Season selection preserves the placed variant and uses isolated geometry for items. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class OutdoorTableModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private OutdoorTableModels() {}
    private static ModelResourceLocation id(String season,int mask,int variant){
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/outdoor_table/"+season+"/"+mask+"_"+variant),"standalone");
    }
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event){
        for(String season:SEASONS)for(int mask=0;mask<256;mask++)if(OutdoorTableBlock.canonical(mask)==mask)
            for(int v=0;v<3;v++)event.register(id(season,mask,v));
    }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){
        var models=event.getModels();
        for(BlockState state:ModBlocks.OUTDOOR_TABLE.get().getStateDefinition().getPossibleStates()){
            BakedModel[] seasons=new BakedModel[4];
            for(int s=0;s<4;s++)seasons[s]=Objects.requireNonNull(models.get(id(SEASONS[s],
                    OutdoorTableBlock.canonical(state.getValue(OutdoorTableBlock.CONNECTIONS)),state.getValue(OutdoorTableBlock.VARIANT))));
            var key=BlockModelShaper.stateToModelLocation(state);
            models.put(key,new Seasonal(Objects.requireNonNull(models.get(key)),seasons));
        }
        var item=new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,"outdoor_table"),"inventory");
        BakedModel[] seasons=new BakedModel[4];
        for(int s=0;s<4;s++)seasons[s]=Objects.requireNonNull(models.get(id(SEASONS[s],0,0)));
        models.put(item,new Seasonal(Objects.requireNonNull(models.get(item)),seasons));
    }

    private static final class Seasonal extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[] seasons;
        private final List<List<BakedQuad>> quads;
        Seasonal(BakedModel original, BakedModel[] seasons) {
            super(original);
            this.seasons = seasons;
            quads = java.util.Arrays.stream(seasons).map(model -> List.copyOf(model.getQuads(null, null, RandomSource.create(0)))).toList();
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random, ModelData data, @Nullable RenderType type) {
            if (side != null || (state != null && type != null && type != RenderType.solid())) return List.of();
            return quads.get(TerrainSeasonTextures.currentTextureSet());
        }
        @Override public TextureAtlasSprite getParticleIcon() { return seasons[TerrainSeasonTextures.currentTextureSet()].getParticleIcon(); }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return getParticleIcon(); }
        @Override public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean left) {
            getTransforms().getTransform(context).apply(left, pose); return this;
        }
        @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) { return List.of(this); }
    }
}
