package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.event.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlas;

/** Published on the client mod bus after atlas upload and sprite/ticker installation succeed. */
@Environment(EnvType.CLIENT)
public final class TextureAtlasStitchedEvent extends Event implements ModBusEvent {
    private final TextureAtlas atlas;
    public TextureAtlasStitchedEvent(TextureAtlas atlas) { this.atlas = atlas; }
    public TextureAtlas getAtlas() { return atlas; }
}
