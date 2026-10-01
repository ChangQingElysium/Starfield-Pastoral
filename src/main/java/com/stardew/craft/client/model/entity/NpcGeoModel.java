package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

@SuppressWarnings("null")
public class NpcGeoModel extends GeoModel<StardewNpcEntity> {

    @Override
    public ResourceLocation getModelResource(StardewNpcEntity animatable) {
        String npcId = resolveNpcId(animatable);
        return new ResourceLocation(StardewCraft.MODID, "geo/entity/npc/" + npcId + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(StardewNpcEntity animatable) {
        String npcId = resolveNpcId(animatable);
        return new ResourceLocation(StardewCraft.MODID, "textures/entity/npc/" + npcId + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(StardewNpcEntity animatable) {
        String npcId = resolveNpcId(animatable);
        return new ResourceLocation(StardewCraft.MODID, "animations/entity/npc/" + npcId + ".animation.json");
    }

    private static String resolveNpcId(StardewNpcEntity entity) {
        return com.stardew.craft.client.npcnative.NativeNpcAssets.legacyId(entity.getNpcId());
    }
}
