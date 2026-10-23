package com.stardew.craft.platform.fabric.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.mixin.client.model.mixin.ItemModelTransformMixin;
import com.stardew.craft.platform.fabric.modeldata.ModelDataProvider;
import com.stardew.craft.platform.fabric.modeldata.ModelDataUpdates;
import io.github.fabricators_of_create.porting_lib.models.IModelBuilder;
import io.github.fabricators_of_create.porting_lib.models.geometry.IGeometryBakingContext;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.impl.client.indigo.renderer.IndigoRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class ModelBridgeStructureTest {
    @BeforeAll static void renderer() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        if (RendererAccess.INSTANCE.getRenderer() == null) RendererAccess.INSTANCE.registerRenderer(IndigoRenderer.INSTANCE);
    }

    @Test void geometryBuilderKeepsTheFaceAoAndEmissionInsteadOfUsingPortingLibsLossyNativeQuadPath() {
        var owner = (IGeometryBakingContext) Proxy.newProxyInstance(IGeometryBakingContext.class.getClassLoader(),
                new Class<?>[]{IGeometryBakingContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getRenderTypeHint" -> null;
                    case "useAmbientOcclusion" -> true;
                    default -> throw new AssertionError(method);
                });
        var builder = new CapturingBuilder();
        int[] data = new int[32];
        for (int vertex = 0; vertex < 4; vertex++) data[vertex * 8 + 6] = 0x00F000F0;
        GeometryQuads.addUnculled(owner, builder, new ModelQuad(data, 3, Direction.NORTH, null, false, false));
        assertEquals(net.fabricmc.fabric.api.util.TriState.FALSE, builder.ao);
        assertTrue(builder.diffuseDisabled);
        assertEquals(0x00F000F0, builder.light);
        assertEquals(3, builder.tint);
        assertNull(builder.cull);
    }

    @Test void nativeQuadsDefaultToAoAndCustomAoIsExplicit() {
        var nativeQuad = new BakedQuad(new int[32], -1, Direction.UP, null, true);
        assertTrue(ModelQuad.ambientOcclusion(nativeQuad));
        assertFalse(ModelQuad.ambientOcclusion(new ModelQuad(new int[32], -1, Direction.UP, null, true, false)));
    }

    @Test void itemRenderTypeMappingKeepsTheNativeTransparencyChoice() {
        assertSame(Sheets.cutoutBlockSheet(), RenderTypeHelper.getEntityRenderType(RenderType.solid(), false, true));
        assertSame(Sheets.cutoutBlockSheet(), RenderTypeHelper.getEntityRenderType(RenderType.cutoutMipped(), true, false));
        assertSame(Sheets.translucentCullBlockSheet(), RenderTypeHelper.getEntityRenderType(RenderType.translucent(), false, false));
        assertSame(Sheets.translucentItemSheet(), RenderTypeHelper.getEntityRenderType(RenderType.translucent(), false, true));
        assertSame(Sheets.translucentCullBlockSheet(), RenderTypeHelper.getEntityRenderType(RenderType.translucent(), true, true));
    }

    @Test void itemTransformHookMatchesTheSingleNativeApplicationInsideThePushedPose() throws Exception {
        var hook = java.util.Arrays.stream(ItemModelTransformMixin.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation.class))
                .filter(annotation -> annotation != null).findFirst().orElseThrow();
        assertEquals(1, hook.require());
        String descriptor = "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V";
        assertArrayEquals(new String[]{"render" + descriptor}, hook.method());
        List<String> calls = new ArrayList<>();
        try (var stream = getClass().getResourceAsStream("/net/minecraft/client/renderer/entity/ItemRenderer.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!name.equals("render") || !desc.equals(descriptor)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                            calls.add(owner + "." + name);
                        }
                    };
                }
            }, 0);
        }
        String apply = "net/minecraft/client/renderer/block/model/ItemTransform.apply";
        assertEquals(1, calls.stream().filter(apply::equals).count());
        assertTrue(calls.indexOf("com/mojang/blaze3d/vertex/PoseStack.pushPose") < calls.indexOf(apply));
        assertTrue(calls.indexOf(apply) < calls.indexOf("com/mojang/blaze3d/vertex/PoseStack.translate"));
    }

    @Test void commonBlockEntityDataContractsDoNotLinkClientClassesOrCarryClientEnvironmentStripping() throws Exception {
        for (Class<?> type : List.of(ModelDataProvider.class, ModelDataUpdates.class)) {
            try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                assertNotNull(stream);
                var bytes = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(bytes.contains("net/minecraft/client/"));
                assertFalse(bytes.contains("net/fabricmc/api/Environment"));
            }
        }
    }

    private static final class CapturingBuilder implements IModelBuilder<CapturingBuilder> {
        net.fabricmc.fabric.api.util.TriState ao;
        boolean diffuseDisabled;
        int light;
        int tint;
        Direction cull;
        @Override public CapturingBuilder addCulledFace(Direction face, BakedQuad quad) { throw new AssertionError("lost FRAPI metadata"); }
        @Override public CapturingBuilder addUnculledFace(BakedQuad quad) { throw new AssertionError("lost FRAPI metadata"); }
        @Override public CapturingBuilder addFace(QuadView quad) {
            ao = quad.material().ambientOcclusion(); diffuseDisabled = quad.material().disableDiffuse();
            light = quad.lightmap(0); tint = quad.colorIndex(); cull = quad.cullFace(); return this;
        }
        @Override public BakedModel build() { throw new AssertionError("unused fixture"); }
    }
}
