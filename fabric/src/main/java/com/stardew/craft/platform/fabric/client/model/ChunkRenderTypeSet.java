package com.stardew.craft.platform.fabric.client.model;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.client.renderer.RenderType;

/** Immutable ordered layers; all layers are emitted in one FRAPI block invocation. */
public final class ChunkRenderTypeSet implements Iterable<RenderType> {
    private final List<RenderType> layers;
    private ChunkRenderTypeSet(List<RenderType> layers) { this.layers = layers; }

    public static ChunkRenderTypeSet of(RenderType... layers) {
        return new ChunkRenderTypeSet(List.copyOf(new LinkedHashSet<>(List.of(layers))));
    }

    public static ChunkRenderTypeSet union(ChunkRenderTypeSet... sets) {
        var layers = new LinkedHashSet<RenderType>();
        for (var set : sets) layers.addAll(set.layers);
        return new ChunkRenderTypeSet(List.copyOf(layers));
    }

    public boolean contains(RenderType type) { return layers.contains(type); }
    public List<RenderType> asList() { return layers; }
    @Override public Iterator<RenderType> iterator() { return layers.iterator(); }
}
