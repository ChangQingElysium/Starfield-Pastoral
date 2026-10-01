package com.stardew.craft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
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
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;

/** Four-season geometry for the automatically installed board and ticket box. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpecialOrderSiteModels {
    private static final String[] SEASONS = {"spring", "summer", "autumn", "winter"};
    private static final String[] KINDS = {"special_orders_board", "prize_ticket_box"};
    private SpecialOrderSiteModels() {}
    private static ModelResourceLocation id(String season,String kind) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/decor/special_orders/"+kind+"/"+season),"standalone");
    }
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        for(String season:SEASONS)for(String kind:KINDS)event.register(id(season,kind));
    }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        var models=event.getModels();
        for(String kind:KINDS){
            var block=kind.equals("special_orders_board")?ModBlocks.SPECIAL_ORDERS_BOARD.get():ModBlocks.PRIZE_TICKET_BOX.get();
            BakedModel[] seasons=new BakedModel[4];
            for(int s=0;s<4;s++)seasons[s]=Objects.requireNonNull(models.get(id(SEASONS[s],kind)));
            for(BlockState state:block.getStateDefinition().getPossibleStates()){
                var key=BlockModelShaper.stateToModelLocation(state);
                models.put(key,new Seasonal(Objects.requireNonNull(models.get(key)),seasons,
                        state.getValue(MapDecorStaticBlock.FACING),state.getValue(MapDecorStaticBlock.PART)==MapDecorStaticBlock.Part.MAIN));
            }
            var item=new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,kind),"inventory");
            if (models.containsKey(item)) models.put(item,new Seasonal(models.get(item),seasons,Direction.NORTH,true));
        }
    }

    private static BakedQuad rotate(BakedQuad source, Direction facing) {
        int turns = switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        if (turns == 0) return source;
        int[] vertices = source.getVertices().clone();
        int stride = vertices.length / 4;
        for (int i = 0; i < 4; i++) {
            int at = i * stride;
            float x = Float.intBitsToFloat(vertices[at]), z = Float.intBitsToFloat(vertices[at + 2]);
            int packed = vertices[at + 7], nx = (byte) packed, nz = (byte) (packed >> 16);
            for (int t = 0; t < turns; t++) {
                float previousX = x; x = 1 - z; z = previousX;
                int previousNx = nx; nx = -nz; nz = previousNx;
            }
            vertices[at] = Float.floatToRawIntBits(x);
            vertices[at + 2] = Float.floatToRawIntBits(z);
            vertices[at + 7] = (packed & 0xff00ff00) | (nx & 255) | ((nz & 255) << 16);
        }
        Direction face = source.getDirection();
        if (face.getAxis().isHorizontal()) for (int t = 0; t < turns; t++) face = face.getClockWise();
        return new BakedQuad(vertices, source.getTintIndex(), face, source.getSprite(), source.isShade(), source.hasAmbientOcclusion());
    }

    private static final class Seasonal extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel[] seasons;
        private final List<List<BakedQuad>> quads;
        Seasonal(BakedModel original, BakedModel[] seasons, Direction facing, boolean visible) {
            super(original);
            this.seasons = seasons;
            quads = java.util.Arrays.stream(seasons).map(model -> (visible ? model.getQuads(null, null, RandomSource.create(0)) : List.<BakedQuad>of())
                    .stream().map(quad -> rotate(quad, facing)).toList()).toList();
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
