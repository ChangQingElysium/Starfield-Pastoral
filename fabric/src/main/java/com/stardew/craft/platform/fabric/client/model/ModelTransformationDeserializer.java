/*
 * Adapted from NeoForge TransformationHelper.Deserializer.
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package com.stardew.craft.platform.fabric.client.model;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The existing NeoForge model transform JSON, installed only on BlockModel's Gson. */
public final class ModelTransformationDeserializer implements JsonDeserializer<Transformation> {
    public static final ModelTransformationDeserializer INSTANCE = new ModelTransformationDeserializer();

    private ModelTransformationDeserializer() {}

    public static GsonBuilder register(GsonBuilder builder) {
        return builder.registerTypeAdapter(Transformation.class, INSTANCE);
    }

    @Override
    public Transformation deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            if (json.getAsString().equals("identity")) return Transformation.identity();
            throw new JsonParseException("TRSR: unknown default string: " + json.getAsString());
        }
        if (json.isJsonArray()) return new Transformation(matrix(json));
        if (!json.isJsonObject()) throw new JsonParseException("TRSR: expected array or object");
        JsonObject object = json.getAsJsonObject();
        if (object.has("matrix")) {
            if (object.size() != 1) throw new JsonParseException("TRSR: can't combine matrix and other keys");
            return new Transformation(matrix(object.get("matrix")));
        }
        Vector3f translation = null;
        Quaternionf left = null;
        Vector3f scale = null;
        Quaternionf right = null;
        // NeoForge 21.1 deliberately retains this historical default, not center.
        Vector3f origin = new Vector3f(1, 1, 1);
        var remaining = new HashSet<>(object.keySet());
        if (object.has("translation")) {
            translation = new Vector3f(numbers(object.get("translation"), 3, "Translation"));
            remaining.remove("translation");
        }
        if (object.has("rotation")) {
            left = rotation(object.get("rotation"));
            remaining.remove("rotation");
        } else if (object.has("left_rotation")) {
            left = rotation(object.get("left_rotation"));
            remaining.remove("left_rotation");
        }
        if (object.has("scale")) {
            JsonElement value = object.get("scale");
            if (value.isJsonArray()) scale = new Vector3f(numbers(value, 3, "Scale"));
            else {
                float uniform = number(value, "Scale");
                scale = new Vector3f(uniform, uniform, uniform);
            }
            remaining.remove("scale");
        }
        if (object.has("right_rotation")) {
            right = rotation(object.get("right_rotation"));
            remaining.remove("right_rotation");
        } else if (object.has("post-rotation")) {
            right = rotation(object.get("post-rotation"));
            remaining.remove("post-rotation");
        }
        if (object.has("origin")) {
            origin = origin(object.get("origin"));
            remaining.remove("origin");
        }
        if (!remaining.isEmpty()) throw new JsonParseException("TRSR: unknown or conflicting transform keys: " + remaining);
        Transformation result = new Transformation(translation, left, scale, right);
        if (result.equals(Transformation.identity())) return Transformation.identity();
        // Same T(origin) * TRSR * T(-origin) as ITransformationExtension.applyOrigin;
        // use native matrices so this parser does not require an injected helper.
        return new Transformation(new Matrix4f().translation(origin).mul(result.getMatrix())
                .translate(-origin.x(), -origin.y(), -origin.z()));
    }

    private static Vector3f origin(JsonElement json) {
        if (json.isJsonArray()) return new Vector3f(numbers(json, 3, "Origin"));
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            return switch (json.getAsString()) {
                case "center" -> new Vector3f(.5F, .5F, .5F);
                case "corner" -> new Vector3f();
                case "opposing-corner" -> new Vector3f(1, 1, 1);
                default -> throw new JsonParseException("Origin: expected center, corner or opposing-corner");
            };
        }
        throw new JsonParseException("Origin: expected an array or named origin");
    }

    private static Matrix4f matrix(JsonElement json) {
        if (!json.isJsonArray() || json.getAsJsonArray().size() != 3) {
            throw new JsonParseException("Matrix: expected three rows");
        }
        Matrix4f matrix = new Matrix4f();
        for (int row = 0; row < 3; row++) {
            float[] values = numbers(json.getAsJsonArray().get(row), 4, "Matrix row");
            for (int column = 0; column < 4; column++) matrix.set(column, row, values[column]);
        }
        // JOML set(column,row,...) leaves the previous identity flags cached.
        return matrix.determineProperties();
    }

    private static float[] numbers(JsonElement json, int length, String label) {
        if (!json.isJsonArray() || json.getAsJsonArray().size() != length) {
            throw new JsonParseException(label + ": expected an array of length " + length);
        }
        float[] values = new float[length];
        for (int i = 0; i < length; i++) values[i] = number(json.getAsJsonArray().get(i), label + " element");
        return values;
    }

    private static float number(JsonElement json, String label) {
        try {
            return json.getAsNumber().floatValue();
        } catch (RuntimeException malformed) {
            throw new JsonParseException(label + ": expected number", malformed);
        }
    }

    private static Quaternionf axisRotation(JsonElement json) {
        if (!json.isJsonObject() || json.getAsJsonObject().size() != 1) {
            throw new JsonParseException("Axis rotation: expected a single axis object");
        }
        Map.Entry<String, JsonElement> axis = json.getAsJsonObject().entrySet().iterator().next();
        float angle = number(axis.getValue(), "Axis rotation value");
        return switch (axis.getKey()) {
            case "x" -> Axis.XP.rotationDegrees(angle);
            case "y" -> Axis.YP.rotationDegrees(angle);
            case "z" -> Axis.ZP.rotationDegrees(angle);
            default -> throw new JsonParseException("Axis rotation: unknown axis " + axis.getKey());
        };
    }

    private static Quaternionf rotation(JsonElement json) {
        if (json.isJsonObject()) return axisRotation(json);
        if (!json.isJsonArray()) throw new JsonParseException("Rotation: expected array or object");
        JsonArray values = json.getAsJsonArray();
        if (values.isEmpty()) throw new JsonParseException("Rotation: empty array");
        if (values.get(0).isJsonObject()) {
            Quaternionf rotation = new Quaternionf();
            for (JsonElement value : values) rotation.mul(axisRotation(value));
            return rotation;
        }
        if (values.size() == 3) {
            float[] xyz = numbers(json, 3, "Rotation");
            float radians = (float) Math.PI / 180;
            return new Quaternionf().rotationXYZ(xyz[0] * radians, xyz[1] * radians, xyz[2] * radians);
        }
        float[] xyzw = numbers(json, 4, "Rotation");
        return new Quaternionf(xyzw[0], xyzw[1], xyzw[2], xyzw[3]);
    }
}
