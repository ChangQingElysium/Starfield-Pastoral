package com.stardew.craft.client.interior;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * One-layer, resolved-color Iris views. Each retained viewpoint owns its deferred buffers,
 * shadow maps and temporal uniforms. Render only after the outer level has finalized; never
 * re-enter its pipeline or feed resolved RGB into a shader pack's private gbuffer layout.
 */
final class TownDoorShaderView implements AutoCloseable {
    private static final int MAX_VIEWS = 2;
    private final LinkedHashMap<ViewKey, View> views = new LinkedHashMap<>(4, .75f, true);
    private boolean resolved;
    private Class<?> shaderPipeline;
    private Method manager;
    private Method currentPipeline;
    private Method currentDimension;
    private Method currentPack;
    private Method isFallback;
    private Method programSet;
    private Method destroy;
    private Constructor<?> constructor;
    private Field storageBuffers;
    private Field renderTargets;
    private Method opaqueDepth;
    private Method depthTextureId;
    private Method bindStorageBuffers;
    private Object settings;
    private Field[] settingFields;
    private Object ownerPipeline;

    static void clearTarget(RenderTarget target) {
        // Iris's final pass leaves depth writes disabled. glClear obeys that mask too:
        // without resetting it, the child keeps undefined/previous depth and draws only sky.
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.disableScissor();
        target.setClearColor(0, 0, 0, 1);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
    }

    Object activePipeline() throws ReflectiveOperationException {
        resolve();
        if (shaderPipeline == null) return null;
        Object pipeline = currentPipeline.invoke(manager.invoke(null));
        return shaderPipeline.isInstance(pipeline) ? pipeline : null;
    }

    boolean hasShaderPack() throws ReflectiveOperationException {
        resolve();
        if (shaderPipeline == null || ((Optional<?>) currentPack.invoke(null)).isEmpty()) return false;
        Object pipeline = currentPipeline.invoke(manager.invoke(null));
        // Iris keeps the selected pack after a compile failure, but renders through vanilla.
        // Treating selection as activation would skip the native portal and then draw nothing.
        // A null pipeline still needs the deferred path on the first frame after a reload.
        if (pipeline != null && !shaderPipeline.isInstance(pipeline)
                && (boolean) isFallback.invoke(null)) return false;
        if (!com.stardew.craft.mixin.StardewCraftMixinPlugin.hasAuditedOculus()) {
            throw new IllegalStateException("Iris portal hooks were not applied; refusing to reuse an unsupported outer pipeline");
        }
        return true;
    }

    void bindStorage(Object pipeline) throws ReflectiveOperationException {
        Object buffers = storageBuffers.get(pipeline);
        if (buffers != null) bindStorageBuffers.invoke(buffers);
    }

    int opaqueDepth(Object pipeline) throws ReflectiveOperationException {
        return (int) depthTextureId.invoke(opaqueDepth.invoke(renderTargets.get(pipeline)));
    }

    View acquire(int doorId, boolean entering, Object parent, RenderTarget main) throws ReflectiveOperationException {
        if (ownerPipeline != parent) {
            close();
            ownerPipeline = parent;
        }
        ViewKey key = new ViewKey(doorId, entering);
        View view = views.get(key);
        if (view != null) {
            if (view.target.width != main.width || view.target.height != main.height) {
                view.target.resize(main.width, main.height, Minecraft.ON_OSX);
            }
            return view;
        }
        if (views.size() == MAX_VIEWS) dispose(com.stardew.craft.port.PortJava.pollFirstEntry(views).getValue());

        RenderTarget target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        var minecraft = (com.stardew.craft.mixin.TownDoorMinecraftAccessor) Minecraft.getInstance();
        var snapshot = TownDoorRenderSnapshot.capture(settings, settingFields);
        Object child = null;
        try {
            minecraft.stardewcraft$setMainRenderTarget(target);
            Object pack = ((Optional<?>) currentPack.invoke(null)).orElseThrow();
            Object programs = programSet.invoke(pack, currentDimension.invoke(null));
            // Do not call Iris.createPipeline: its failure handler disables the user's whole pack
            // and force-deletes SSBOs belonging to the outer view.
            child = constructor.newInstance(programs);
            // Oculus initializes material IDs in the constructor, not beginLevelRendering.
            // Restoring WorldRenderingSettings below also restores its reload flag.
            view = new View(target, child);
            views.put(key, view);
            return view;
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (child != null) {
                try { destroy.invoke(child); } catch (ReflectiveOperationException cleanup) { error.addSuppressed(cleanup); }
            }
            target.destroyBuffers();
            throw error;
        } finally {
            try {
                snapshot.restore();
            } finally {
                minecraft.stardewcraft$setMainRenderTarget(main);
                main.bindWrite(true);
                bindStorage(parent);
            }
        }
    }

    private void resolve() throws ReflectiveOperationException {
        if (resolved) return;
        ClassLoader loader = getClass().getClassLoader();
        Class<?> iris;
        try {
            iris = Class.forName("net.irisshaders.iris.Iris", false, loader);
        } catch (ClassNotFoundException absent) {
            resolved = true;
            return;
        }
        Class<?> pipelineType = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline", false, loader);
        Class<?> managerType = Class.forName("net.irisshaders.iris.pipeline.PipelineManager", false, loader);
        Class<?> dimensionType = Class.forName("net.irisshaders.iris.shaderpack.materialmap.NamespacedId", false, loader);
        Class<?> packType = Class.forName("net.irisshaders.iris.shaderpack.ShaderPack", false, loader);
        Class<?> programsType = Class.forName("net.irisshaders.iris.shaderpack.programs.ProgramSet", false, loader);
        Class<?> settingsType = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings", false, loader);
        manager = iris.getMethod("getPipelineManager");
        currentPipeline = managerType.getMethod("getPipelineNullable");
        currentPack = iris.getMethod("getCurrentPack");
        isFallback = iris.getMethod("isFallback");
        currentDimension = iris.getMethod("getCurrentDimension");
        programSet = packType.getMethod("getProgramSet", dimensionType);
        constructor = pipelineType.getConstructor(programsType);
        destroy = pipelineType.getMethod("destroy");
        storageBuffers = pipelineType.getDeclaredField("shaderStorageBufferHolder");
        storageBuffers.setAccessible(true);
        renderTargets = pipelineType.getDeclaredField("renderTargets");
        renderTargets.setAccessible(true);
        opaqueDepth = renderTargets.getType().getMethod("getDepthTextureNoTranslucents");
        depthTextureId = opaqueDepth.getReturnType().getMethod("getTextureId");
        bindStorageBuffers = storageBuffers.getType().getMethod("setupBuffers");
        settings = settingsType.getField("INSTANCE").get(null);
        settingFields = TownDoorRenderSnapshot.fields(settingsType, "reloadRequired", "blockStateIds", "blockTypeIds",
                "entityIds", "itemIds", "ambientOcclusionLevel", "disableDirectionalShading", "hasVillagerConversionId",
                "useSeparateAo", "separateEntityDraws", "voxelizeLightBlocks", "useExtendedVertexFormat");
        shaderPipeline = pipelineType;
        resolved = true;
    }

    private void dispose(View view) throws ReflectiveOperationException {
        try {
            try { TownDoorOculusPrograms.release(view.pipeline); }
            finally { destroy.invoke(view.pipeline); }
        } finally {
            view.target.destroyBuffers();
        }
    }

    @Override public void close() throws ReflectiveOperationException {
        ReflectiveOperationException failure = null;
        for (View view : views.values()) {
            try { dispose(view); }
            catch (ReflectiveOperationException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        views.clear();
        ownerPipeline = null;
        if (failure != null) throw failure;
    }

    record View(RenderTarget target, Object pipeline) {}
    private record ViewKey(int doorId, boolean entering) {}
}
