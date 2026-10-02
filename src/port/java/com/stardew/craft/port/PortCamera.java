package com.stardew.craft.port;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

/** The 1.21.1 camera basis used by StardewCraft's world-space billboards. */
@OnlyIn(Dist.CLIENT)
public final class PortCamera {
    private PortCamera() {
    }

    public static Quaternionf cameraOrientation(EntityRenderDispatcher dispatcher) {
        return from1201(dispatcher.cameraOrientation());
    }

    public static Quaternionf rotation(Camera camera) {
        return from1201(camera.rotation());
    }

    /**
     * 1.20.1 Camera#setRotation produces Ry(-yaw) Rx(pitch); 1.21.1 produces
     * Ry(PI-yaw) Rx(-pitch). Post-multiplying by Ry(PI) gives exactly the latter basis,
     * including pitch. Positive billboard Z (item/count in front of a ready bubble) then
     * points towards the viewer as in 1.21.1, not behind the bubble as in 1.20.1.
     * Never rotate the shared camera/dispatcher quaternion in place.
     */
    public static Quaternionf from1201(Quaternionf orientation) {
        return new Quaternionf(orientation).rotateY((float) Math.PI);
    }
}
