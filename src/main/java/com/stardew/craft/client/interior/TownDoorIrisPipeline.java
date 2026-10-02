package com.stardew.craft.client.interior;

import java.lang.reflect.Field;

/**
 * Keeps the optional Iris dependency behind reflection.  Iris' vanilla pipeline is deliberately
 * used for the nested door view: a shader-pack pipeline owns deferred framebuffers for the outer
 * frame and cannot be entered recursively without copying those buffers first.
 */
public final class TownDoorIrisPipeline {
    private static final String VANILLA_PIPELINE = "net.irisshaders.iris.pipeline.VanillaRenderingPipeline";
    private static Object vanilla;
    private static boolean resolved;

    private TownDoorIrisPipeline() {}

    public static Object vanilla() {
        if (resolved) return vanilla;
        resolved = true;
        try {
            ClassLoader loader = TownDoorIrisPipeline.class.getClassLoader();
            Class<?> settingsType = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings", false, loader);
            Object settings = settingsType.getField("INSTANCE").get(null);
            // PORT(1.20.1): Oculus' vanilla constructor resets shared shader-pack settings.
            // The nested pipeline must not change outer chunk formats, ids, AO or reload state.
            String[] names = {"disableDirectionalShading", "useSeparateAo", "separateEntityDraws",
                    "ambientOcclusionLevel", "useExtendedVertexFormat", "voxelizeLightBlocks", "blockTypeIds", "reloadRequired"};
            Field[] fields = new Field[names.length];
            Object[] values = new Object[names.length];
            for (int i = 0; i < names.length; i++) {
                fields[i] = settingsType.getDeclaredField(names[i]);
                fields[i].setAccessible(true);
                values[i] = fields[i].get(settings);
            }
            try {
                vanilla = Class.forName(VANILLA_PIPELINE, false, loader).getConstructor().newInstance();
            } finally {
                for (int i = 0; i < fields.length; i++) fields[i].set(settings, values[i]);
            }
        } catch (Throwable ignored) {
            vanilla = null;
        }
        return vanilla;
    }
}
