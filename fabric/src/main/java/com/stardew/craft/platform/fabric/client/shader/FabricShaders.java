package com.stardew.craft.platform.fabric.client.shader;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.impl.client.rendering.FabricShaderProgram;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

/** Uses the pinned rendering-v1 implementation, whose native mixins support namespaced shader IDs. */
@Environment(EnvType.CLIENT)
public final class FabricShaders {
    private FabricShaders() {}
    public static ShaderInstance create(ResourceProvider provider, ResourceLocation id, VertexFormat format) throws IOException {
        return new FabricShaderProgram(provider, id, format);
    }
}
