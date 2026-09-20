package com.stardew.craft.client.interior;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Shader clipping adapted from Immersive Portals' FrontClipping (Apache-2.0).
 * The plane normal points into the destination side that remains visible.
 */
public final class TownDoorClipping {
    public static final String UNIFORM = "stardewcraft_TownDoorClipEquation";
    private static final float[] DISABLED = {0, 0, 0, 1};
    private static final float[] BEFORE_MODEL_VIEW = {0, 0, 0, 1};
    private static final float[] AFTER_MODEL_VIEW = {0, 0, 0, 1};
    private static boolean enabled;
    private static int cachedProgram;
    private static int cachedLocation = -1;

    private TownDoorClipping() {}

    static void enable(Vec3 planePoint, Vec3 normal, Vec3 camera, Matrix4f modelView) {
        Vec3 relativePoint = planePoint.add(normal.scale(.01)).subtract(camera);
        Vector4f before = new Vector4f((float) normal.x, (float) normal.y, (float) normal.z,
                (float) -normal.dot(relativePoint));
        BEFORE_MODEL_VIEW[0] = before.x;
        BEFORE_MODEL_VIEW[1] = before.y;
        BEFORE_MODEL_VIEW[2] = before.z;
        BEFORE_MODEL_VIEW[3] = before.w;
        Vector4f after = new Matrix4f(modelView).invert().transpose().transform(new Vector4f(before));
        AFTER_MODEL_VIEW[0] = after.x;
        AFTER_MODEL_VIEW[1] = after.y;
        AFTER_MODEL_VIEW[2] = after.z;
        AFTER_MODEL_VIEW[3] = after.w;
        GL11.glEnable(GL11.GL_CLIP_PLANE0);
        enabled = true;
    }

    static void disable() {
        if (enabled) GL11.glDisable(GL11.GL_CLIP_PLANE0);
        enabled = false;
    }

    /**
     * A renderer backend can keep the currently bound shader program alive across the nested
     * world render.  Upload the disabled equation immediately when we return to the outer view;
     * waiting for the next backend state bind leaves the outer terrain clipped by the doorway.
     */
    static void resetActiveShaderUniform() {
        if (!RenderSystem.isOnRenderThread()) return;
        // Do not issue a uniform-location query on every portal frame.  Sodium's state hook
        // populates this pair when it binds a new program; only touch it if that same program is
        // still active after the nested pass.
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program == cachedProgram) uploadRendererModUniform(cachedLocation);
    }

    public static void updateVanillaUniform(Uniform uniform) {
        float[] equation = enabled ? BEFORE_MODEL_VIEW : DISABLED;
        uniform.set(equation[0], equation[1], equation[2], equation[3]);
    }

    /** Renderer-mod shader objects cache this lookup once instead of synchronizing with GL every draw. */
    public static int activeProgram() {
        return !RenderSystem.isOnRenderThread() ? 0 : GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    }

    public static int findInActiveProgram() {
        if (!RenderSystem.isOnRenderThread()) return Integer.MIN_VALUE;
        int program = activeProgram();
        if (program == 0) return -1;
        if (program != cachedProgram) {
            cachedProgram = program;
            cachedLocation = GL20.glGetUniformLocation(program, UNIFORM);
        }
        return cachedLocation;
    }

    public static void uploadRendererModUniform(int location) {
        if (location < 0) return;
        float[] equation = enabled ? AFTER_MODEL_VIEW : DISABLED;
        GL20.glUniform4f(location, equation[0], equation[1], equation[2], equation[3]);
    }
}
