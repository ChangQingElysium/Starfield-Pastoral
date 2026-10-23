package com.stardew.craft.client.interior;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Standalone, invisible GL context. No Minecraft client, game window, world or user configuration. */
public final class TownDoorShaderGpuCheck {
    private static final String VERTEX = "#version 150\nvoid main() {\n"
            + "vec2 p = gl_VertexID == 0 ? vec2(-1,-1) : (gl_VertexID == 1 ? vec2(3,-1) : vec2(-1,3));\n"
            + "gl_Position = vec4(p,0,1); }";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        inContext(() -> {
            checkFragments();
            checkProjectedPlane();
            checkComposite();
            checkForegroundUniforms();
            checkSodiumVertex();
            checkFogRestore();
            checkDepthClearAfterFinalPass();
        });
    }

    private static void checkFogRestore() {
        if (!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())
            com.mojang.blaze3d.systems.RenderSystem.initRenderThread();
        var outer = new TownDoorFogState.Shader(Float.MAX_VALUE, 192, .2f, .4f, .6f, 1,
                com.mojang.blaze3d.shaders.FogShape.CYLINDER);
        outer.restore();
        var saved = TownDoorFogState.Shader.capture();
        new TownDoorFogState.Shader(1, 8, .7f, .5f, .3f, .5f,
                com.mojang.blaze3d.shaders.FogShape.SPHERE).restore();
        saved.restore();
        if (!outer.equals(TownDoorFogState.Shader.capture())) throw new AssertionError("Nested fog leaked into HUD");
        assertions++;
        // The snapshot must own its color values, not RenderSystem's mutable color array.
        com.mojang.blaze3d.systems.RenderSystem.getShaderFogColor()[0] = 0;
        saved.restore();
        if (!outer.equals(TownDoorFogState.Shader.capture())) throw new AssertionError("Aliased fog color snapshot");
        assertions++;
    }

    private static void checkDepthClearAfterFinalPass() {
        var target = new com.mojang.blaze3d.pipeline.TextureTarget(7, 5, true, false);
        try {
            target.bindWrite(true);
            com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
            GL33.glClearDepth(.25);
            GL33.glClear(GL33.GL_DEPTH_BUFFER_BIT);
            // Reproduce the caller state after Iris final, including unrelated clear masks.
            com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
            com.mojang.blaze3d.systems.RenderSystem.colorMask(false, false, false, false);
            com.mojang.blaze3d.systems.RenderSystem.enableScissor(0, 0, 1, 1);
            float[] depth = new float[1];
            target.clear(false);
            target.bindWrite(true);
            GL33.glReadPixels(3, 3, 1, 1, GL33.GL_DEPTH_COMPONENT, GL33.GL_FLOAT, depth);
            if (Math.abs(depth[0] - .25f) > 1e-6f) throw new AssertionError("Masked-clear reproduction is invalid");
            assertions++;
            TownDoorShaderView.clearTarget(target);
            GL33.glReadPixels(3, 3, 1, 1, GL33.GL_DEPTH_COMPONENT, GL33.GL_FLOAT, depth);
            if (depth[0] != 1) throw new AssertionError("Portal depth was not cleared after Iris final: " + depth[0]);
            assertions++;
            ByteBuffer color = org.lwjgl.BufferUtils.createByteBuffer(4);
            GL33.glReadPixels(3, 3, 1, 1, GL33.GL_RGBA, GL33.GL_UNSIGNED_BYTE, color);
            if (color.get(0) != 0 || color.get(1) != 0 || color.get(2) != 0 || (color.get(3) & 255) != 255)
                throw new AssertionError("Portal color clear was masked/scissored");
            assertions++;
        } finally { target.destroyBuffers(); }
    }

    @FunctionalInterface interface Check { void run() throws Exception; }

    static void inContext(Check check) throws Exception {
        assertions = 0;
        if (!GLFW.glfwInit()) throw new IllegalStateException("Cannot initialize a windowless GL fixture");
        long window = 0;
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
            window = GLFW.glfwCreateWindow(64, 64, "Portal shader fixture (invisible)", 0, 0);
            if (window == 0) throw new IllegalStateException("Cannot create an invisible GL context");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("PORTAL-GPU " + GL33.glGetString(GL33.GL_RENDERER) + " / " + GL33.glGetString(GL33.GL_VERSION));
            int vao = GL33.glGenVertexArrays();
            GL33.glBindVertexArray(vao);
            try {
                check.run();
                if (GL33.glGetError() != GL33.GL_NO_ERROR) throw new AssertionError("GL error after fixtures");
                System.out.println("PORTAL-GPU PASS " + assertions + " assertions; fixture compilation and pixels only, not in-game acceptance");
            } finally {
                GL33.glDeleteVertexArrays(vao);
            }
        } finally {
            if (window != 0) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void checkFragments() {
        for (String version : new String[]{"150", "330", "410"}) {
            String fragment = "#version " + version + "\nout vec4 color;\n// void main() {}\n"
                    + "void main(void) { if (gl_FragCoord.x < 0.0) return; color = vec4(0,1,0,1); }\n"
                    + "float helper() { return 1.0; }\n";
            int program = link(VERTEX.replace("150", version), TownDoorShaderPatcher.transformIrisFragment(fragment));
            try {
                for (int[] viewport : new int[][]{{0, 0, 64, 64}, {8, 4, 32, 16}, {0, 0, 31, 23}}) {
                    GL33.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                    GL33.glClearColor(1, 0, 0, 1);
                    GL33.glClear(GL33.GL_COLOR_BUFFER_BIT);
                    GL33.glUseProgram(program);
                    GL33.glUniform4f(GL33.glGetUniformLocation(program, TownDoorClipping.SCREEN_UNIFORM), 1, 0, 0, 0);
                    GL33.glUniform4f(GL33.glGetUniformLocation(program, TownDoorClipping.VIEWPORT_UNIFORM),
                            viewport[0], viewport[1], viewport[2], viewport[3]);
                    GL33.glDrawArrays(GL33.GL_TRIANGLES, 0, 3);
                    pixel(viewport[0] + viewport[2] / 4, viewport[1] + viewport[3] / 2, true);
                    pixel(viewport[0] + viewport[2] * 3 / 4, viewport[1] + viewport[3] / 2, false);
                    // Disabled outer/shadow view must draw both sides using the same compiled pack program.
                    GL33.glUniform4f(GL33.glGetUniformLocation(program, TownDoorClipping.SCREEN_UNIFORM), 0, 0, 0, 1);
                    GL33.glDrawArrays(GL33.GL_TRIANGLES, 0, 3);
                    pixel(viewport[0] + viewport[2] / 4, viewport[1] + viewport[3] / 2, false);
                }
            } finally { GL33.glDeleteProgram(program); }
        }
    }

    private static void checkProjectedPlane() {
        int program = link(VERTEX, TownDoorShaderPatcher.transformIrisFragment(
                "#version 150\nout vec4 color; void main() { color = vec4(0,1,0,1); }"));
        var camera = new net.minecraft.world.phys.Vec3(12, 60, -4);
        var normal = new net.minecraft.world.phys.Vec3(1, 0, 0);
        var point = camera;
        var projection = new org.joml.Matrix4f().perspective((float) Math.toRadians(75), 1.3f, .05f, 100f);
        int[] locations = {GL33.glGetUniformLocation(program, TownDoorClipping.SCREEN_UNIFORM),
                GL33.glGetUniformLocation(program, TownDoorClipping.VIEWPORT_UNIFORM)};
        GL33.glViewport(3, 5, 51, 43);
        GL33.glUseProgram(program);
        try {
            for (org.joml.Matrix4f view : new org.joml.Matrix4f[]{new org.joml.Matrix4f(),
                    new org.joml.Matrix4f().rotateY(.35f).rotateX(.2f),
                    new org.joml.Matrix4f().translate(.01f, .02f, 0).rotateZ(.3f).rotateY(-.25f)}) {
                GL33.glClearColor(1, 0, 0, 1);
                GL33.glClear(GL33.GL_COLOR_BUFFER_BIT);
                // Exercise the production inverse-transpose plane and viewport upload, not a copied formula.
                TownDoorClipping.enable(point, normal, camera, view, projection);
                TownDoorClipping.uploadIrisUniforms(locations);
                GL33.glDrawArrays(GL33.GL_TRIANGLES, 0, 3);
                var inverse = new org.joml.Matrix4f(projection).mul(view).invert();
                for (int y = 10; y < 43; y += 8) for (int x = 8; x < 51; x += 8) {
                    var relative = inverse.transform(new org.joml.Vector4f(
                            ((x + .5f - 3) / 51) * 2 - 1, ((y + .5f - 5) / 43) * 2 - 1, 0, 1));
                    double distance = relative.x / relative.w - .01;
                    if (Math.abs(distance) > 1e-5) pixel(x, y, distance < 0);
                }
                TownDoorRenderContext.shadowPass(true);
                TownDoorClipping.uploadIrisUniforms(locations);
                GL33.glDrawArrays(GL33.GL_TRIANGLES, 0, 3);
                pixel(8, 10, false);
                TownDoorRenderContext.shadowPass(false);
            }
        } finally {
            TownDoorRenderContext.shadowPass(false);
            TownDoorClipping.disable();
            GL33.glDeleteProgram(program);
        }
    }

    private static void checkComposite() throws Exception {
        String base = "/assets/stardewcraft/shaders/core/town_door_composite.";
        int program = link(resource(base + "vsh"), resource(base + "fsh"));
        GL33.glDeleteProgram(program);
        program = link(VERTEX, resource(base + "fsh"));
        int[] textures = {GL33.glGenTextures(), GL33.glGenTextures(), GL33.glGenTextures()};
        try {
            GL33.glUseProgram(program);
            GL33.glUniform2f(GL33.glGetUniformLocation(program, "ScreenSize"), 64, 64);
            for (int i = 0; i < textures.length; i++) {
                GL33.glActiveTexture(GL33.GL_TEXTURE0 + i);
                GL33.glBindTexture(GL33.GL_TEXTURE_2D, textures[i]);
                GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_NEAREST);
                GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_NEAREST);
                GL33.glUniform1i(GL33.glGetUniformLocation(program, "Sampler" + i), i);
            }
            GL33.glActiveTexture(GL33.GL_TEXTURE0);
            GL33.glTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_RGBA32F, 1, 1, 0,
                    GL33.GL_RGBA, GL33.GL_FLOAT, new float[]{0, 1, 0, 1});
            GL33.glViewport(0, 0, 64, 64);
            GL33.glEnable(GL33.GL_DEPTH_TEST);
            GL33.glDepthFunc(GL33.GL_ALWAYS);
            GL33.glDepthMask(true);
            // Opaque foreground rejects the aperture; transparent foreground is already in
            // the child color, so a nearer value in the main depth must not reject it.
            for (float[] scenario : new float[][]{{.25f, 1, .1f}, {.75f, 1, .5f}, {.75f, .2f, .2f}}) {
                GL33.glActiveTexture(GL33.GL_TEXTURE1);
                GL33.glTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_R32F, 1, 1, 0,
                        GL33.GL_RED, GL33.GL_FLOAT, new float[]{scenario[0]});
                GL33.glActiveTexture(GL33.GL_TEXTURE2);
                GL33.glTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_R32F, 1, 1, 0,
                        GL33.GL_RED, GL33.GL_FLOAT, new float[]{scenario[1]});
                GL33.glClearColor(1, 0, 0, 1);
                GL33.glClearDepth(.1);
                GL33.glClear(GL33.GL_COLOR_BUFFER_BIT | GL33.GL_DEPTH_BUFFER_BIT);
                GL33.glDrawArrays(GL33.GL_TRIANGLES, 0, 3);
                pixel(32, 32, scenario[0] < .5f);
                float[] depth = new float[1];
                GL33.glReadPixels(32, 32, 1, 1, GL33.GL_DEPTH_COMPONENT, GL33.GL_FLOAT, depth);
                if (Math.abs(depth[0] - scenario[2]) > 1e-5)
                    throw new AssertionError("Incorrect aperture/foreground depth: " + depth[0]);
                assertions++;
            }
        } finally {
            GL33.glDeleteProgram(program);
            for (int texture : textures) GL33.glDeleteTextures(texture);
            GL33.glActiveTexture(GL33.GL_TEXTURE0);
            GL33.glDisable(GL33.GL_DEPTH_TEST);
            GL33.glClearDepth(1);
        }
    }

    private static void checkForegroundUniforms() {
        String fragment = "#version 150\nuniform vec3 cameraPosition; uniform vec3 previousCameraPosition;"
                + "uniform ivec2 eyeBrightness; uniform ivec2 eyeBrightnessSmooth; out vec4 color;"
                + "void main(){ color=vec4(cameraPosition+previousCameraPosition,1)"
                + "+vec4(eyeBrightness,eyeBrightnessSmooth); }";
        int outer = link(VERTEX, fragment), child = link(VERTEX, fragment);
        var outerHook = new TownDoorForegroundUniforms.Program();
        var childHook = new TownDoorForegroundUniforms.Program();
        try {
            GL33.glUseProgram(outer);
            setEyeUniforms(outer, 67, 240);
            TownDoorForegroundUniforms.prepare(true);
            TownDoorForegroundUniforms.collect(outerHook::applied);
            GL33.glUseProgram(child);
            setEyeUniforms(child, 37, 0);
            try {
                TownDoorForegroundUniforms.draw(() -> {
                    childHook.applied();
                    assertEyeUniforms(child, 67, 240);
                    // Multiple applications must not overwrite the original restore values.
                    childHook.applied();
                    GL33.glUseProgram(0);
                    throw new IllegalStateException("fixture");
                });
            } catch (IllegalStateException expected) {
                if (!expected.getMessage().equals("fixture")) throw expected;
            }
            assertEyeUniforms(child, 37, 0);
            assertEyeUniforms(outer, 67, 240);
            if (GL33.glGetInteger(GL33.GL_CURRENT_PROGRAM) != 0) throw new AssertionError("Program binding leaked");
            assertions++;
            // A closed door/reload must not reuse the previous frame's material state.
            TownDoorForegroundUniforms.prepare(false);
            GL33.glUseProgram(child);
            TownDoorForegroundUniforms.draw(childHook::applied);
            assertEyeUniforms(child, 37, 0);
        } finally {
            TownDoorForegroundUniforms.prepare(false);
            GL33.glUseProgram(0);
            GL33.glDeleteProgram(outer); GL33.glDeleteProgram(child);
        }
    }

    private static void setEyeUniforms(int program, int height, int light) {
        for (String name : new String[]{"cameraPosition", "previousCameraPosition"})
            GL33.glUniform3f(GL33.glGetUniformLocation(program, name), 30, height, 17);
        for (String name : new String[]{"eyeBrightness", "eyeBrightnessSmooth"})
            GL33.glUniform2i(GL33.glGetUniformLocation(program, name), 80, light);
    }

    private static void assertEyeUniforms(int program, int height, int light) {
        for (String name : new String[]{"cameraPosition", "previousCameraPosition"}) {
            float[] value = new float[3];
            GL33.glGetUniformfv(program, GL33.glGetUniformLocation(program, name), value);
            if (value[0] != 30 || value[1] != height || value[2] != 17) throw new AssertionError(name);
            assertions++;
        }
        for (String name : new String[]{"eyeBrightness", "eyeBrightnessSmooth"}) {
            int[] value = new int[2];
            GL33.glGetUniformiv(program, GL33.glGetUniformLocation(program, name), value);
            if (value[0] != 80 || value[1] != light) throw new AssertionError(name);
            assertions++;
        }
    }

    private static void checkSodiumVertex() {
        String vertex = "#version 150\nuniform mat4 u_ModelViewMatrix;\n"
                + "void main(void) { vec3 position = vec3(0); gl_Position = vec4(position, 1); }\n"
                + "float trailingHelper() { return 1.0; }\n";
        int program = link(TownDoorShaderPatcher.transformSodiumVertex(vertex),
                "#version 150\nout vec4 color; void main() { color = vec4(1); }");
        GL33.glDeleteProgram(program);
    }

    private static void pixel(int x, int y, boolean red) {
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        GL33.glReadPixels(x, y, 1, 1, GL33.GL_RGBA, GL33.GL_UNSIGNED_BYTE, pixel);
        int r = Byte.toUnsignedInt(pixel.get(0)), g = Byte.toUnsignedInt(pixel.get(1));
        if (red ? (r < 250 || g > 5) : (g < 250 || r > 5)) {
            throw new AssertionError("Wrong clip pixel at " + x + "," + y + ": " + r + "," + g);
        }
        assertions++;
    }

    static int link(String vertex, String fragment) {
        int v = compile(GL33.GL_VERTEX_SHADER, vertex), f = compile(GL33.GL_FRAGMENT_SHADER, fragment);
        int program = GL33.glCreateProgram();
        try {
            GL33.glAttachShader(program, v); GL33.glAttachShader(program, f);
            GL33.glLinkProgram(program);
            if (GL33.glGetProgrami(program, GL33.GL_LINK_STATUS) == 0) throw new AssertionError(GL33.glGetProgramInfoLog(program));
            assertions++;
            return program;
        } finally { GL33.glDeleteShader(v); GL33.glDeleteShader(f); }
    }

    private static int compile(int type, String source) {
        int shader = GL33.glCreateShader(type);
        GL33.glShaderSource(shader, source); GL33.glCompileShader(shader);
        if (GL33.glGetShaderi(shader, GL33.GL_COMPILE_STATUS) == 0) {
            throw new AssertionError(GL33.glGetShaderInfoLog(shader) + "\n" + source);
        }
        assertions++;
        return shader;
    }

    private static String resource(String name) throws Exception {
        try (var input = TownDoorShaderGpuCheck.class.getResourceAsStream(name)) {
            if (input == null) throw new AssertionError("Missing shader " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
