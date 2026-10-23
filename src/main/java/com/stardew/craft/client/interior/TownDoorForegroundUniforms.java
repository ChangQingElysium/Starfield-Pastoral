package com.stardew.craft.client.interior;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Keeps foreground material coordinates and eye lighting in the physical camera's world. */
public final class TownDoorForegroundUniforms {
    private static final String[] NAMES = {
            "cameraPosition", "previousCameraPosition", "eyeBrightness", "eyeBrightnessSmooth"
    };
    private static boolean enabled;
    private static boolean collecting;
    private static boolean drawing;
    private static Value[] outer;
    private static final List<Saved> changed = new ArrayList<>();

    private TownDoorForegroundUniforms() {}

    static void prepare(boolean active) {
        enabled = active;
        outer = null;
    }

    public static void collect(Runnable render) {
        if (!enabled) { render.run(); return; }
        collecting = true;
        outer = null;
        try { render.run(); }
        finally { collecting = false; }
    }

    static void draw(Runnable render) {
        drawing = true;
        try { render.run(); }
        finally {
            drawing = false;
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            try {
                for (Saved saved : changed) {
                    GL20.glUseProgram(saved.program);
                    saved.value.upload(saved.location);
                }
            } finally {
                changed.clear();
                GL20.glUseProgram(program);
            }
        }
    }

    /** One instance per Iris program, so reloads cannot reuse a deleted program's locations. */
    public static final class Program {
        private int[] locations;

        public void applied() {
            if (!collecting && !drawing) return;
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            if (program == 0) return;
            if (locations == null) {
                locations = Arrays.stream(NAMES).mapToInt(name -> GL20.glGetUniformLocation(program, name)).toArray();
            }
            if (collecting) {
                outer = new Value[NAMES.length];
                for (int i = 0; i < locations.length; i++) {
                    if (locations[i] >= 0) outer[i] = Value.read(program, locations[i], i >= 2);
                }
            } else if (outer != null) {
                for (int i = 0; i < locations.length; i++) {
                    int location = locations[i];
                    if (location < 0 || outer[i] == null) continue;
                    boolean saved = false;
                    for (Saved previous : changed) {
                        if (previous.program == program && previous.location == location) { saved = true; break; }
                    }
                    if (!saved) changed.add(new Saved(program, location, Value.read(program, location, i >= 2)));
                    outer[i].upload(location);
                }
            }
        }
    }

    private record Saved(int program, int location, Value value) {}

    private record Value(float[] floats, int[] integers) {
        static Value read(int program, int location, boolean integer) {
            if (integer) {
                int[] value = new int[2];
                GL20.glGetUniformiv(program, location, value);
                return new Value(null, value);
            }
            float[] value = new float[3];
            GL20.glGetUniformfv(program, location, value);
            return new Value(value, null);
        }

        void upload(int location) {
            if (integers != null) GL20.glUniform2iv(location, integers);
            else GL20.glUniform3fv(location, floats);
        }
    }
}
