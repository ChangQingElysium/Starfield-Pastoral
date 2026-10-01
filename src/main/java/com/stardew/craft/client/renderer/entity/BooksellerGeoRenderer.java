package com.stardew.craft.client.renderer.entity;

import com.stardew.craft.client.model.entity.BooksellerGeoModel;
import com.stardew.craft.entity.npc.BooksellerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.stardew.craft.client.model.nativebb.BlockbenchEntityRenderer;

@SuppressWarnings("null")
public class BooksellerGeoRenderer extends BlockbenchEntityRenderer<BooksellerEntity> {

    public BooksellerGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new BooksellerGeoModel());
        this.shadowRadius = 0.45F;
    }
}
