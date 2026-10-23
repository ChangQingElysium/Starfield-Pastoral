package com.stardew.craft.platform.fabric.client.shader;

import static org.junit.jupiter.api.Assertions.*;
import net.fabricmc.fabric.impl.client.rendering.FabricShaderProgram;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class ShaderFactoryTest {
    @Test
    void factoryUsesThePinnedFapiNamespacedShaderClassRatherThanTheVanillaStringConstructor() throws Exception {
        assertEquals(ResourceLocation.parse("stardewcraft:shaders/core/black_hole.json"),
                FabricShaderProgram.rewriteAsId("shaders/core/stardewcraft:black_hole.json", "stardewcraft:black_hole"));
        var node = new ClassNode();
        try (var bytes = getClass().getClassLoader().getResourceAsStream("com/stardew/craft/platform/fabric/client/shader/FabricShaders.class")) {
            assertNotNull(bytes); new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
        }
        var method = node.methods.stream().filter(value -> value.name.equals("create")).findFirst().orElseThrow();
        assertTrue(java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false).anyMatch(value -> value instanceof MethodInsnNode call
                && call.owner.equals("net/fabricmc/fabric/impl/client/rendering/FabricShaderProgram") && call.name.equals("<init>")
                && call.desc.equals("(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V")));
    }
}
