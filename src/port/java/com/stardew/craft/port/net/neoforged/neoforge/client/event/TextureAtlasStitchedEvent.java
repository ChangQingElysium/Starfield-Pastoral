package com.stardew.craft.port.net.neoforged.neoforge.client.event;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * PORT(1.20.1): NeoForge 21.1 atlas-stitched event (mod bus), bridged from Forge {@code TextureStitchEvent.Post}.
 */
public class TextureAtlasStitchedEvent extends Event implements IModBusEvent {
    private final TextureAtlas atlas;

    public TextureAtlasStitchedEvent() {
        this(null);
    }

    public TextureAtlasStitchedEvent(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    public TextureAtlas getAtlas() {
        return this.atlas;
    }
}
