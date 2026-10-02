package com.stardew.craft.client.render;

import net.minecraft.core.Direction;

/** Loadable runtime contracts; ordinary interface Mixins themselves must never be loaded/cast. */
public final class EmbeddiumQuadAccess {
    private EmbeddiumQuadAccess() {}

    public interface Coordinates {
        float stardewcraft$getX(int vertex);
        float stardewcraft$getY(int vertex);
        float stardewcraft$getZ(int vertex);
    }

    public interface Lighting extends Coordinates {
        int stardewcraft$getLight(int vertex);
        Direction stardewcraft$getLightFace();
    }

    public interface Normal {
        int stardewcraft$getComputedFaceNormal();
    }
}
