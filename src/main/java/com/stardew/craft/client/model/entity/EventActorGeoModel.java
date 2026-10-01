package com.stardew.craft.client.model.entity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.cutscene.runtime.EventActorEntity;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

/**
 * Native Blockbench resource selection for {@link EventActorEntity}.
 * Reuses the same NPC model/texture/animation files based on npcId,
 * identical to {@link NpcGeoModel}.
 */
public class EventActorGeoModel extends BlockbenchModel<EventActorEntity> {


    @Override
    public ResourceLocation getModelResource(EventActorEntity entity) {
        String id = resolveNpcId(entity);
        return new ResourceLocation(StardewCraft.MODID,
                "geo/entity/npc/" + id + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(EventActorEntity entity) {
        String id = resolveNpcId(entity);
        return new ResourceLocation(StardewCraft.MODID,
                "textures/entity/npc/" + id + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(EventActorEntity entity) {
        String id = resolveNpcId(entity);
        return new ResourceLocation(StardewCraft.MODID,
                "animations/entity/npc/" + id + ".animation.json");
    }

    private static String resolveNpcId(EventActorEntity entity) {
        return com.stardew.craft.client.npcnative.NativeNpcAssets.legacyId(entity.getNpcId());
    }
}
