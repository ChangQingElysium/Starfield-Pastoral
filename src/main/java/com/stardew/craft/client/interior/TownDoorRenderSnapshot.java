package com.stardew.craft.client.interior;

import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.lang.reflect.Field;

/** A scoped snapshot of the explicitly selected Iris fields touched by a nested level render. */
final class TownDoorRenderSnapshot {
    private final Object owner;
    private final Field[] fields;
    private final Object[] values;
    private final Object[] copies;

    private TownDoorRenderSnapshot(Object owner, Field[] fields) throws IllegalAccessException {
        this.owner = owner;
        this.fields = fields;
        this.values = new Object[fields.length];
        this.copies = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) {
            Object value = fields[i].get(owner);
            values[i] = value;
            copies[i] = value instanceof Matrix4f matrix ? new Matrix4f(matrix)
                    : value instanceof Vector3d vector ? new Vector3d(vector) : value;
        }
    }

    static Field[] fields(Class<?> type, String... names) throws NoSuchFieldException {
        Field[] fields = new Field[names.length];
        for (int i = 0; i < names.length; i++) {
            fields[i] = type.getDeclaredField(names[i]);
            fields[i].setAccessible(true);
        }
        return fields;
    }

    static TownDoorRenderSnapshot capture(Object owner, Field[] fields) throws IllegalAccessException {
        return new TownDoorRenderSnapshot(owner, fields);
    }

    void restore() throws IllegalAccessException {
        IllegalAccessException failure = null;
        for (int i = 0; i < fields.length; i++) {
            // Iris mutates fog vectors in place. Preserve both the value and the original object,
            // including references retained by another renderer or an outer nested scope.
            if (values[i] instanceof Matrix4f matrix) matrix.set((Matrix4f) copies[i]);
            else if (values[i] instanceof Vector3d vector) vector.set((Vector3d) copies[i]);
            try {
                fields[i].set(owner, values[i]);
            } catch (IllegalAccessException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
