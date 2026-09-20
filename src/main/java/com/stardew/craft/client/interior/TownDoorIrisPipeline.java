package com.stardew.craft.client.interior;

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
            vanilla = Class.forName(VANILLA_PIPELINE, false,
                    TownDoorIrisPipeline.class.getClassLoader()).getConstructor().newInstance();
        } catch (Throwable ignored) {
            vanilla = null;
        }
        return vanilla;
    }
}
