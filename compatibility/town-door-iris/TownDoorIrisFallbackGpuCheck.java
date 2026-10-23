package com.stardew.craft.client.interior;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import net.irisshaders.iris.pipeline.fallback.ShaderSynthesizer;
import org.lwjgl.opengl.GL33;

/** Compiles actual Iris fallback output with our production patch, in the invisible GPU fixture. */
public final class TownDoorIrisFallbackGpuCheck {
    public static void main(String[] args) throws Exception {
        TownDoorShaderGpuCheck.inContext(() -> {
            for (VertexFormat format : new VertexFormat[]{DefaultVertexFormat.BLOCK, DefaultVertexFormat.NEW_ENTITY,
                    DefaultVertexFormat.PARTICLE, DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, DefaultVertexFormat.POSITION}) {
                for (FogMode fog : FogMode.values()) {
                    var inputs = new ShaderAttributeInputs(format, false, false, false, false, false);
                    boolean leash = format == DefaultVertexFormat.POSITION_COLOR_LIGHTMAP;
                    String vertex = ShaderSynthesizer.vsh(true, inputs, fog, inputs.hasNormal(), leash);
                    String fragment = ShaderSynthesizer.fsh(inputs, fog, AlphaTest.ALWAYS, false, leash);
                    // A plain JavaExec has no Mixin launcher. The JUnit contract separately proves
                    // the real fsh() hook calls this transform, as well as FallbackShader.apply().
                    int program = TownDoorShaderGpuCheck.link(vertex,
                            TownDoorShaderPatcher.transformIrisFragment(fragment));
                    GL33.glDeleteProgram(program);
                }
            }
        });
    }
}
