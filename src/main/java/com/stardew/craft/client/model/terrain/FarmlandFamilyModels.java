package com.stardew.craft.client.model.terrain;

import com.stardew.craft.StardewCraft;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.event.ModelEvent;

/** Equal-rank soil families meet halfway, retaining the recipient's fertilizer. */
final class FarmlandFamilyModels {
    private static final String[] SEASONS = {"spring", "summer", "fall", "winter"};
    private FarmlandFamilyModels() {}

    private static ModelResourceLocation id(int family, int season, int wet) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "block/terrain/farmland_family_edges/" + family + "/" + SEASONS[season] + (wet == 1 ? "_wet" : "_dry")), "standalone");
    }

    static void register(ModelEvent.RegisterAdditional event) {
        for (int family=0;family<3;family++) for(int season=0;season<4;season++) for(int wet=0;wet<2;wet++)
            event.register(id(family,season,wet));
    }

    static void bind(Map<ResourceLocation, BakedModel> models, TerrainFarmlandQuads[][][] plain,
            TerrainFarmlandQuads[][][][] fertilizers) {
        for (int family=0;family<3;family++) for(int season=0;season<4;season++) for(int wet=0;wet<2;wet++) {
            BakedQuad atlas=com.stardew.craft.port.PortJava.getFirst(Objects.requireNonNull(models.get(id(family,season,wet)))
                    .getQuads(null,null,RandomSource.create(0)));
            for(int column=0;column<=fertilizers[family][season].length;column++) {
                BakedQuad[] peers=new BakedQuad[9];
                for(int row=0;row<9;row++) peers[row]=tile(atlas,column,row);
                (column==0 ? plain[family][season][wet] : fertilizers[family][season][column-1][wet]).setPeers(peers);
            }
        }
    }

    private static BakedQuad tile(BakedQuad source,int column,int row) {
        int[] vertices=source.getVertices().clone(); int stride=vertices.length/4;
        var sprite=source.getSprite();
        for(int v=0;v<vertices.length;v+=stride) {
            float x=Float.intBitsToFloat(vertices[v])>.5f?15.999f:.001f;
            float z=Float.intBitsToFloat(vertices[v+2])>.5f?15.999f:.001f;
            vertices[v+4]=Float.floatToRawIntBits(com.stardew.craft.port.PortSprites.getU(sprite, (column*16+x)/160f));
            vertices[v+5]=Float.floatToRawIntBits(com.stardew.craft.port.PortSprites.getV(sprite, (row*16+z)/144f));
        }
        return new BakedQuad(vertices,source.getTintIndex(),source.getDirection(),sprite,source.isShade(),source.hasAmbientOcclusion());
    }
}
