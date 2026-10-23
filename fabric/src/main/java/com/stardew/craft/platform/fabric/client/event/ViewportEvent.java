package com.stardew.craft.platform.fabric.client.event;

import com.mojang.blaze3d.shaders.FogShape;
import com.stardew.craft.platform.fabric.event.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.material.FogType;

/** The two viewport stages consumed by the project: resolved fog color and resolved fog planes. */
@Environment(EnvType.CLIENT)
public abstract class ViewportEvent extends Event {
    private final GameRenderer renderer;
    private final Camera camera;
    private final double partialTick;
    protected ViewportEvent(boolean cancelable, GameRenderer renderer, Camera camera, double partialTick) {
        super(cancelable); this.renderer = renderer; this.camera = camera; this.partialTick = partialTick;
    }
    public GameRenderer getRenderer() { return renderer; }
    public Camera getCamera() { return camera; }
    public double getPartialTick() { return partialTick; }

    public static final class ComputeFogColor extends ViewportEvent {
        private float red;
        private float green;
        private float blue;
        public ComputeFogColor(Camera camera, float partialTick, float red, float green, float blue) {
            this(Minecraft.getInstance().gameRenderer, camera, partialTick, red, green, blue);
        }
        public ComputeFogColor(GameRenderer renderer, Camera camera, float partialTick, float red, float green, float blue) {
            super(false, renderer, camera, partialTick); this.red = red; this.green = green; this.blue = blue;
        }
        public float getRed() { return red; }
        public float getGreen() { return green; }
        public float getBlue() { return blue; }
        public void setRed(float value) { red = value; }
        public void setGreen(float value) { green = value; }
        public void setBlue(float value) { blue = value; }
    }

    public static final class RenderFog extends ViewportEvent {
        private final FogRenderer.FogMode mode;
        private final FogType type;
        private float near;
        private float far;
        private FogShape shape;
        public RenderFog(FogRenderer.FogMode mode, FogType type, Camera camera, float partialTick, float near, float far, FogShape shape) {
            this(Minecraft.getInstance().gameRenderer, mode, type, camera, partialTick, near, far, shape);
        }
        public RenderFog(GameRenderer renderer, FogRenderer.FogMode mode, FogType type, Camera camera, float partialTick,
                         float near, float far, FogShape shape) {
            super(true, renderer, camera, partialTick); this.mode = mode; this.type = type; this.near = near; this.far = far; this.shape = shape;
        }
        public FogRenderer.FogMode getMode() { return mode; }
        public FogType getType() { return type; }
        public float getNearPlaneDistance() { return near; }
        public float getFarPlaneDistance() { return far; }
        public FogShape getFogShape() { return shape; }
        public void setNearPlaneDistance(float value) { near = value; }
        public void setFarPlaneDistance(float value) { far = value; }
        public void setFogShape(FogShape value) { shape = value; }
        public void scaleNearPlaneDistance(float scale) { near *= scale; }
        public void scaleFarPlaneDistance(float scale) { far *= scale; }
    }
}
