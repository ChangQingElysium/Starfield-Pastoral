package com.stardew.craft.client.interior;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/** Oculus 1.8's terrain shader cache is global; each child pipeline needs its own samplers/programs. */
public final class TownDoorOculusPrograms {
    private static final Map<Object, Object> views = new IdentityHashMap<>();
    private static boolean delegating;
    private static Constructor<?> constructor;
    private static Method getProgram;
    private static Method deleteShaders;

    private TownDoorOculusPrograms() {}

    public static boolean isDelegating() { return delegating; }

    public static Object program(Object pass, Object vertexType) {
        try {
            resolve();
            Object pipeline = TownDoorIrisPipeline.nested();
            if (pipeline == null) throw new IllegalStateException("No child Oculus pipeline");
            Object cache = views.get(pipeline);
            if (cache == null) {
                cache = constructor.newInstance();
                views.put(pipeline, cache);
            }
            delegating = true;
            try { return getProgram.invoke(cache, pass, vertexType); }
            finally { delegating = false; }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not isolate Oculus terrain programs", error);
        }
    }

    static void release(Object pipeline) throws ReflectiveOperationException {
        Object cache = views.remove(pipeline);
        if (cache != null) deleteShaders.invoke(cache);
    }

    private static void resolve() throws ReflectiveOperationException {
        if (constructor != null) return;
        ClassLoader loader = TownDoorOculusPrograms.class.getClassLoader();
        Class<?> type = Class.forName("net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides", false, loader);
        getProgram = type.getMethod("getProgramOverride",
                Class.forName("me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass", false, loader),
                Class.forName("me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType", false, loader));
        deleteShaders = type.getMethod("deleteShaders");
        constructor = type.getConstructor();
    }
}
