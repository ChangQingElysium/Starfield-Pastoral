package com.stardew.craft.client.interior;

import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the real Iris classes after our Mixins apply, without starting Minecraft or creating GL resources. */
class TownDoorIrisContractTest {
    private ClassLoader previousLoader;

    @Test void sodiumVisibilitySnapshotsOwnTheRegionArrays() throws Exception {
        var source = new net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList(null);
        String[] names = {"sectionsWithGeometry", "sectionsWithSprites", "sectionsWithEntities"};
        for (int i = 0; i < names.length; i++) {
            var array = source.getClass().getDeclaredField(names[i]); array.setAccessible(true);
            ((byte[]) array.get(source))[0] = (byte) (7 + i);
            var count = source.getClass().getDeclaredField(names[i] + "Count"); count.setAccessible(true);
            count.setInt(source, 1);
        }
        var constructor = net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists.class
                .getDeclaredConstructor(it.unimi.dsi.fastutil.objects.ObjectArrayList.class);
        constructor.setAccessible(true);
        Object sorted = constructor.newInstance(it.unimi.dsi.fastutil.objects.ObjectArrayList.of(source));
        var snapshot = (net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists)
                new TownDoorSodiumLists().copy(sorted);
        source.reset(99);
        for (String name : names) {
            var array = source.getClass().getDeclaredField(name); array.setAccessible(true);
            java.util.Arrays.fill((byte[]) array.get(source), (byte) 42);
        }
        var saved = snapshot.iterator(false).next();
        assertEquals(7, saved.sectionsWithGeometryIterator(false).nextByteAsInt());
        assertEquals(8, saved.sectionsWithSpritesIterator().nextByteAsInt());
        assertEquals(9, saved.sectionsWithEntitiesIterator().nextByteAsInt());
        assertEquals(1, saved.getSectionsWithGeometryCount());
        assertEquals(0, source.getSectionsWithGeometryCount());
    }

    @org.junit.jupiter.api.BeforeEach void useTransformedServiceLoader() {
        previousLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
    }

    @org.junit.jupiter.api.AfterEach void restoreServiceLoader() {
        Thread.currentThread().setContextClassLoader(previousLoader);
    }

    @Test void acquiringNestedVanillaDoesNotResetShaderPackWorldSettings() {
        var vanilla = new VanillaRenderingPipeline();
        var settings = WorldRenderingSettings.INSTANCE;
        settings.setUseSeparateAo(true);
        settings.setAmbientOcclusionLevel(.35f);
        settings.setVoxelizeLightBlocks(true);
        var format = settings.getVertexFormat();
        settings.clearReloadRequired();
        for (int i = 0; i < 3; i++) assertSame(vanilla, TownDoorIrisPipeline.vanilla());
        assertTrue(settings.shouldUseSeparateAo());
        assertEquals(.35f, settings.getAmbientOcclusionLevel());
        assertTrue(settings.shouldVoxelizeLightBlocks());
        assertSame(format, settings.getVertexFormat());
        assertFalse(settings.isReloadRequired());
    }

    @Test void everyManagerQuerySeesTheChildWithoutReplacingTheOuterPipeline() throws Exception {
        PipelineManager manager = new PipelineManager(dimension -> { throw new AssertionError("Must not build another pipeline"); });
        Object outer = manager.getPipelineNullable();
        WorldRenderingPipeline child = new VanillaRenderingPipeline();
        Object previous = TownDoorIrisPipeline.exchangeNested(child);
        TownDoorRenderContext.begin(new Vec3(0, 1, -2), new Vec3(0, 1, 0), new Vec3(0, 0, 1), 2);
        try {
            assertSame(child, manager.preparePipeline(new NamespacedId("minecraft", "overworld")));
            assertSame(child, manager.getPipelineNullable());
            assertSame(child, manager.getPipeline().orElseThrow());
        } finally {
            TownDoorRenderContext.end();
            TownDoorIrisPipeline.exchangeNested(previous);
        }
        assertSame(outer, manager.getPipelineNullable());
        assertSame(outer, manager.getPipeline().orElseThrow());
    }

    @Test void actualIrisTransformClipsGeometryAndLeavesCompositeAndComputeUntouched(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var config = net.irisshaders.iris.Iris.class.getDeclaredField("irisConfig");
        config.setAccessible(true);
        Object previousConfig = config.get(null);
        // In-memory defaults only: never initialize(), read or save a user's Iris configuration.
        config.set(null, new net.irisshaders.iris.config.IrisConfig(directory.resolve("iris.properties"),
                directory.resolve("excluded.json")));
        try {
            String vertex = "#version 120\nvoid main() { gl_Position = ftransform(); }";
            String fragment = "#version 120\nvoid main() { gl_FragData[0] = vec4(1.0); }";
            var textures = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<
                    net.irisshaders.iris.helpers.Tri<String, net.irisshaders.iris.gl.texture.TextureType,
                            net.irisshaders.iris.shaderpack.texture.TextureStage>, String>();
            var sodium = net.irisshaders.iris.pipeline.transform.TransformPatcher.patchSodium(
                    "portal-contract", vertex, null, null, null, fragment,
                    net.irisshaders.iris.gl.blending.AlphaTest.ALWAYS, textures);
            String patched = sodium.get(net.irisshaders.iris.pipeline.transform.PatchShaderType.FRAGMENT);
            assertTrue(patched.contains(TownDoorClipping.SCREEN_UNIFORM), patched);
            assertTrue(patched.contains("discard;"), patched);
            var composite = net.irisshaders.iris.pipeline.transform.TransformPatcher.patchComposite(
                    "portal-composite-contract", vertex, null, fragment,
                    net.irisshaders.iris.shaderpack.texture.TextureStage.COMPOSITE_AND_FINAL, textures);
            assertFalse(composite.values().stream().anyMatch(source -> source != null && source.contains(TownDoorClipping.SCREEN_UNIFORM)));
            String compute = net.irisshaders.iris.pipeline.transform.TransformPatcher.patchCompute(
                    "portal-compute-contract", "#version 430\nlayout(local_size_x=1) in; void main() {}",
                    net.irisshaders.iris.shaderpack.texture.TextureStage.COMPOSITE_AND_FINAL, textures);
            assertFalse(compute.contains(TownDoorClipping.SCREEN_UNIFORM));
        } finally {
            config.set(null, previousConfig);
        }
    }

    @Test void synthesizedFallbacksAlsoReceivePortalClipping() {
        var formats = new com.mojang.blaze3d.vertex.VertexFormat[]{
                com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION};
        for (var format : formats) for (var fog : net.irisshaders.iris.gl.state.FogMode.values()) {
            var inputs = new net.irisshaders.iris.gl.state.ShaderAttributeInputs(
                    format, false, false, false, false, false);
            String source = net.irisshaders.iris.pipeline.fallback.ShaderSynthesizer.fsh(inputs, fog,
                    net.irisshaders.iris.gl.blending.AlphaTest.ALWAYS, false,
                    format == com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_LIGHTMAP);
            assertTrue(source.contains(TownDoorClipping.SCREEN_UNIFORM), "Unclipped Iris fallback: " + format + "/" + fog);
            assertTrue(source.contains(TownDoorClipping.VIEWPORT_UNIFORM));
            assertTrue(source.contains("discard;"));
        }
    }

    @Test void underwaterFogHistoryPreservesTheOuterTransition() throws Exception {
        Class.forName("net.minecraft.client.renderer.FogRenderer", true, getClass().getClassLoader());
        var original = TownDoorFogState.capture();
        var outer = new TownDoorFogState(0x123456, 0x112233, 12345L);
        try {
            outer.restore();
            var saved = TownDoorFogState.capture();
            TownDoorFogState.INITIAL.restore();
            assertEquals(TownDoorFogState.INITIAL, TownDoorFogState.capture());
            new TownDoorFogState(0xabcdef, 0xfedcba, 99999L).restore();
            saved.restore();
            assertEquals(outer, TownDoorFogState.capture());
        } finally {
            original.restore();
        }
    }

    @Test void independentViewResolvesTheActualSupportedPipelineLayout() throws Exception {
        // Resolves every reflected constructor/field/method without allocating a shader pipeline.
        // The headless fixture has no selected shader pack or Minecraft instance.
        try (var views = new TownDoorShaderView()) {
            assertFalse(views.hasShaderPack());
            assertNull(views.activePipeline());
        }
    }

    /** A selected pack is metadata; no GL resources are needed to model its continued presence. */
    public static java.util.Optional<Object> selectedPack() {
        return java.util.Optional.of(TownDoorIrisContractTest.class);
    }

    @Test void failedPackUsesNativePortalsAndReloadCanReturnToShaderRendering() throws Exception {
        var iris = net.irisshaders.iris.Iris.class;
        var fallback = iris.getDeclaredField("fallback");
        var managerField = iris.getDeclaredField("pipelineManager");
        fallback.setAccessible(true);
        managerField.setAccessible(true);
        Object previousManager = managerField.get(null);
        boolean previousFallback = fallback.getBoolean(null);
        var manager = new PipelineManager(dimension -> { throw new AssertionError("No shader compilation expected"); });
        try (var views = new TownDoorShaderView()) {
            managerField.set(null, manager);
            views.activePipeline(); // Resolve the real supported Iris binary and its methods.
            var packField = TownDoorShaderView.class.getDeclaredField("currentPack");
            packField.setAccessible(true);
            var selected = getClass().getDeclaredMethod("selectedPack");
            selected.setAccessible(true);
            packField.set(views, selected);
            fallback.setBoolean(null, false);
            assertTrue(views.hasShaderPack(), "Selected pack must prepare a deferred view before its first frame");
            fallback.setBoolean(null, true);
            assertFalse(views.hasShaderPack(), "Iris's vanilla fallback must use the native portal path");
            assertNull(views.activePipeline());
            fallback.setBoolean(null, false);
            assertTrue(views.hasShaderPack(), "Reload must not leave portals stuck on the fallback path");
        } finally {
            fallback.setBoolean(null, previousFallback);
            managerField.set(null, previousManager);
        }
    }

    @Test void shaderAndShadowHooksActuallyTransformTheSupportedBinary() throws Exception {
        for (String name : new String[]{"net.minecraft.client.renderer.LevelRenderer", "net.minecraft.client.renderer.GameRenderer",
                "net.irisshaders.iris.pipeline.programs.SodiumShader",
                "net.irisshaders.iris.pipeline.programs.FallbackShader",
                "net.irisshaders.iris.pipeline.fallback.ShaderSynthesizer",
                "net.irisshaders.iris.pipeline.programs.ExtendedShader", "net.irisshaders.iris.pipeline.IrisRenderingPipeline",
                "net.irisshaders.iris.uniforms.CommonUniforms", "net.irisshaders.iris.uniforms.BiomeUniforms",
                "net.irisshaders.iris.pipeline.transform.TransformPatcher"}) {
            Class<?> type = Class.forName(name, false, getClass().getClassLoader());
            // Loading applies required injections, including Iris and portal hooks on the same target.
            assertTrue(java.util.Arrays.stream(type.getDeclaredMethods())
                    .anyMatch(method -> method.getName().contains("stardewcraft")), "No applied hook: " + name);
        }
    }
}
