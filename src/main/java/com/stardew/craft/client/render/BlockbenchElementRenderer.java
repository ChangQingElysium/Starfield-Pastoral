package com.stardew.craft.client.render;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class BlockbenchElementRenderer {
    private static final Gson GSON = new Gson();
    private static final Map<ResourceLocation, Optional<Model>> CACHE = new ConcurrentHashMap<>();
    private static final float JAVA_MODEL_UV_SIZE = 16.0F;
    private static final float ZERO_THICKNESS_FACE_OFFSET = 0.002F;

    private BlockbenchElementRenderer() {
    }

    static void renderAll(ResourceLocation modelLocation, PoseStack poseStack, MultiBufferSource buffer,
                          int packedLight, int packedOverlay) {
        render(modelLocation, poseStack, buffer, packedLight, packedOverlay, false, false, null);
    }

    /** Render a Java/Blockbench element model whose face U direction follows vanilla model JSON. */
    static void renderAllWithReversedU(ResourceLocation modelLocation, PoseStack poseStack, MultiBufferSource buffer,
                                       int packedLight, int packedOverlay) {
        render(modelLocation, poseStack, buffer, packedLight, packedOverlay, false, true, null);
    }

    /** Render the enabled Gold Clock with model-authored hand pivots and live Z rotations. */
    static void renderGoldClock(ResourceLocation modelLocation, PoseStack poseStack, MultiBufferSource buffer,
                                int packedLight, int packedOverlay, float hourDegrees, float minuteDegrees) {
        render(modelLocation, poseStack, buffer, packedLight, packedOverlay, false, true,
                new ClockHandAngles(hourDegrees, minuteDegrees));
    }

    static void renderNegativeOnly(ResourceLocation modelLocation, PoseStack poseStack, MultiBufferSource buffer,
                                   int packedLight, int packedOverlay) {
        render(modelLocation, poseStack, buffer, packedLight, packedOverlay, true, false, null);
    }

    static void renderHeadDisplay(ResourceLocation modelLocation, PoseStack poseStack, MultiBufferSource buffer,
                                  int packedLight, int packedOverlay) {
        Model model = CACHE.computeIfAbsent(modelLocation, BlockbenchElementRenderer::load).orElse(null);
        if (model == null) {
            return;
        }
        poseStack.pushPose();
        // Match vanilla CustomHeadLayer.translateToHead before applying ItemDisplayContext.HEAD.
        poseStack.translate(0.0F, -0.25F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.625F, -0.625F, -0.625F);
        model.headDisplay().apply(poseStack);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        renderLoaded(model, poseStack, buffer, packedLight, packedOverlay, false, false, null);
        poseStack.popPose();
    }

    private static void render(ResourceLocation modelLocation, PoseStack poseStack, MultiBufferSource buffer,
                               int packedLight, int packedOverlay, boolean negativeOnly, boolean reverseU,
                               ClockHandAngles clockHands) {
        Model model = CACHE.computeIfAbsent(modelLocation, BlockbenchElementRenderer::load).orElse(null);
        if (model == null) {
            return;
        }
        renderLoaded(model, poseStack, buffer, packedLight, packedOverlay, negativeOnly, reverseU, clockHands);
    }

    private static void renderLoaded(Model model, PoseStack poseStack, MultiBufferSource buffer,
                                     int packedLight, int packedOverlay, boolean negativeOnly, boolean reverseU,
                                     ClockHandAngles clockHands) {
        PoseStack.Pose pose = poseStack.last();
        for (JsonElement elementValue : model.elements()) {
            if (!elementValue.isJsonObject()) {
                continue;
            }
            JsonObject element = elementValue.getAsJsonObject();
            float[] from = vector3(element.getAsJsonArray("from"));
            float[] to = vector3(element.getAsJsonArray("to"));
            boolean negative = from[0] > to[0] || from[1] > to[1] || from[2] > to[2];
            if (negativeOnly && !negative) {
                continue;
            }

            JsonObject faces = element.getAsJsonObject("faces");
            if (faces == null) {
                continue;
            }

            Rotation rotation = rotation(element);
            if (clockHands != null && element.has("name")) {
                String name = element.get("name").getAsString();
                if ("hour".equals(name)) {
                    rotation = withAdditionalZ(rotation, element, clockHands.hourDegrees());
                } else if ("minute".equals(name)) {
                    rotation = withAdditionalZ(rotation, element, clockHands.minuteDegrees());
                }
            }
            float minX = Math.min(from[0], to[0]) / 16.0F;
            float minY = Math.min(from[1], to[1]) / 16.0F;
            float minZ = Math.min(from[2], to[2]) / 16.0F;
            float maxX = Math.max(from[0], to[0]) / 16.0F;
            float maxY = Math.max(from[1], to[1]) / 16.0F;
            float maxZ = Math.max(from[2], to[2]) / 16.0F;
            boolean thinX = minX == maxX;
            boolean thinY = minY == maxY;
            boolean thinZ = minZ == maxZ;

            for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                if (!faceEntry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject face = faceEntry.getValue().getAsJsonObject();
                ResourceLocation texture = model.texture(textureKey(face));
                if (texture == null) {
                    continue;
                }
                VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(texture));
                renderFace(consumer, pose, rotation, faceEntry.getKey().toLowerCase(Locale.ROOT), face,
                        minX, minY, minZ, maxX, maxY, maxZ, model.textureWidth(), model.textureHeight(),
                        packedLight, packedOverlay, negative, model.javaModelUv(), thinX, thinY, thinZ, reverseU);
            }
        }
    }

    private static Optional<Model> load(ResourceLocation modelLocation) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(modelLocation);
            if (resource.isEmpty()) {
                return Optional.empty();
            }
            try (var reader = resource.get().openAsReader()) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null) {
                    return Optional.empty();
                }

                int textureWidth = 16;
                int textureHeight = 16;
                JsonArray textureSize = root.getAsJsonArray("texture_size");
                if (textureSize != null && textureSize.size() >= 2) {
                    textureWidth = textureSize.get(0).getAsInt();
                    textureHeight = textureSize.get(1).getAsInt();
                }

                Map<String, ResourceLocation> textures = new HashMap<>();
                JsonObject textureJson = root.getAsJsonObject("textures");
                if (textureJson != null) {
                    for (Map.Entry<String, JsonElement> entry : textureJson.entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) {
                            textures.put(entry.getKey(), textureLocation(entry.getValue().getAsString()));
                        }
                    }
                }

                JsonArray elements = root.getAsJsonArray("elements");
                JsonArray modelElements = elements == null ? new JsonArray() : elements;
                DisplayTransform headDisplay = displayTransform(root, "head");
                return Optional.of(new Model(textureWidth, textureHeight, textures,
                        modelElements, headDisplay, usesJavaModelUv(modelElements)));
            }
        } catch (Exception ex) {
            StardewCraft.LOGGER.warn("Failed to load Blockbench element model {}", modelLocation, ex);
            return Optional.empty();
        }
    }

    private static void renderFace(VertexConsumer consumer, PoseStack.Pose pose, Rotation rotation, String side,
                                   JsonObject face, float minX, float minY, float minZ,
                                   float maxX, float maxY, float maxZ, int textureWidth, int textureHeight,
                                   int packedLight, int packedOverlay, boolean inward, boolean javaModelUv,
                                   boolean thinX, boolean thinY, boolean thinZ, boolean reverseU) {
        float[] uv = face.has("uv") ? vector4(face.getAsJsonArray("uv")) : new float[] {0, 0, 16, 16};
        float uScale = javaModelUv ? JAVA_MODEL_UV_SIZE : textureWidth;
        float vScale = javaModelUv ? JAVA_MODEL_UV_SIZE : textureHeight;
        float u1 = uv[0] / uScale;
        float v1 = uv[1] / vScale;
        float u2 = uv[2] / uScale;
        float v2 = uv[3] / vScale;
        if (reverseU) {
            float swap = u1;
            u1 = u2;
            u2 = swap;
        }
        // Blockbench writes the Gold Clock hour hand with a 180-degree
        // per-face UV rotation. Geometry rotation and face UV rotation are
        // separate model properties; ignoring the latter corrupts only the
        // hour hand because the minute hand uses the default UV orientation.
        int faceRotation = face.has("rotation")
                ? Math.floorMod(face.get("rotation").getAsInt(), 360) : 0;
        if (faceRotation == 180) {
            float swap = u1;
            u1 = u2;
            u2 = swap;
            swap = v1;
            v1 = v2;
            v2 = swap;
        }
        float[] normal = faceNormal(side);
        if (rotation != null) {
            normal = rotateNormal(rotation, normal);
        }
        if (inward) {
            normal = new float[] {-normal[0], -normal[1], -normal[2]};
        }
        float[] faceOffset = thinFaceOffset(side, thinX, thinY, thinZ);

        switch (side) {
            case "north" -> quad(consumer, pose, rotation, packedLight, packedOverlay, inward,
                    normal, faceOffset,
                    minX, minY, minZ, u1, v2, minX, maxY, minZ, u1, v1,
                    maxX, maxY, minZ, u2, v1, maxX, minY, minZ, u2, v2);
            case "south" -> quad(consumer, pose, rotation, packedLight, packedOverlay, inward,
                    normal, faceOffset,
                    maxX, minY, maxZ, u1, v2, maxX, maxY, maxZ, u1, v1,
                    minX, maxY, maxZ, u2, v1, minX, minY, maxZ, u2, v2);
            case "east" -> quad(consumer, pose, rotation, packedLight, packedOverlay, inward,
                    normal, faceOffset,
                    maxX, minY, minZ, u1, v2, maxX, maxY, minZ, u1, v1,
                    maxX, maxY, maxZ, u2, v1, maxX, minY, maxZ, u2, v2);
            case "west" -> quad(consumer, pose, rotation, packedLight, packedOverlay, inward,
                    normal, faceOffset,
                    minX, minY, maxZ, u1, v2, minX, maxY, maxZ, u1, v1,
                    minX, maxY, minZ, u2, v1, minX, minY, minZ, u2, v2);
            case "up" -> quad(consumer, pose, rotation, packedLight, packedOverlay, inward,
                    normal, faceOffset,
                    minX, maxY, minZ, u1, v2, minX, maxY, maxZ, u1, v1,
                    maxX, maxY, maxZ, u2, v1, maxX, maxY, minZ, u2, v2);
            case "down" -> quad(consumer, pose, rotation, packedLight, packedOverlay, inward,
                    normal, faceOffset,
                    minX, minY, maxZ, u1, v2, minX, minY, minZ, u1, v1,
                    maxX, minY, minZ, u2, v1, maxX, minY, maxZ, u2, v2);
            default -> {
            }
        }
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Rotation rotation,
                             int packedLight, int packedOverlay, boolean inward, float[] normal, float[] faceOffset,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float x4, float y4, float z4, float u4, float v4) {
        if (inward) {
            vertex(consumer, pose, rotation, packedLight, packedOverlay, normal, faceOffset, x1, y1, z1, u1, v1);
            vertex(consumer, pose, rotation, packedLight, packedOverlay, normal, faceOffset, x4, y4, z4, u4, v4);
            vertex(consumer, pose, rotation, packedLight, packedOverlay, normal, faceOffset, x3, y3, z3, u3, v3);
            vertex(consumer, pose, rotation, packedLight, packedOverlay, normal, faceOffset, x2, y2, z2, u2, v2);
            return;
        }
        vertex(consumer, pose, rotation, packedLight, packedOverlay, normal, faceOffset, x1, y1, z1, u1, v1);
        vertex(consumer, pose, rotation, packedLight, packedOverlay, normal, faceOffset, x2, y2, z2, u2, v2);
        vertex(consumer, pose, rotation, packedLight, packedOverlay, normal, faceOffset, x3, y3, z3, u3, v3);
        vertex(consumer, pose, rotation, packedLight, packedOverlay, normal, faceOffset, x4, y4, z4, u4, v4);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Rotation rotation,
                               int packedLight, int packedOverlay, float[] normal, float[] faceOffset,
                               float x, float y, float z, float u, float v) {
        x += faceOffset[0];
        y += faceOffset[1];
        z += faceOffset[2];
        float[] rotated = rotate(rotation, x, y, z);
        consumer.addVertex(pose, rotated[0], rotated[1], rotated[2])
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay == 0 ? OverlayTexture.NO_OVERLAY : packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, normal[0], normal[1], normal[2]);
    }

    private static float[] rotate(Rotation rotation, float x, float y, float z) {
        if (rotation == null) {
            return new float[] {x, y, z};
        }
        float ox = rotation.origin()[0] / 16.0F;
        float oy = rotation.origin()[1] / 16.0F;
        float oz = rotation.origin()[2] / 16.0F;
        float rx = x - ox;
        float ry = y - oy;
        float rz = z - oz;
        float[] afterX = rotateAxis(rx, ry, rz, 'x', rotation.x());
        float[] afterY = rotateAxis(afterX[0], afterX[1], afterX[2], 'y', rotation.y());
        float[] afterZ = rotateAxis(afterY[0], afterY[1], afterY[2], 'z', rotation.z());
        return new float[] {afterZ[0] + ox, afterZ[1] + oy, afterZ[2] + oz};
    }

    private static float[] faceNormal(String side) {
        return switch (side) {
            case "north" -> new float[] {0.0F, 0.0F, -1.0F};
            case "south" -> new float[] {0.0F, 0.0F, 1.0F};
            case "east" -> new float[] {1.0F, 0.0F, 0.0F};
            case "west" -> new float[] {-1.0F, 0.0F, 0.0F};
            case "up" -> new float[] {0.0F, 1.0F, 0.0F};
            case "down" -> new float[] {0.0F, -1.0F, 0.0F};
            default -> new float[] {0.0F, 1.0F, 0.0F};
        };
    }

    private static float[] thinFaceOffset(String side, boolean thinX, boolean thinY, boolean thinZ) {
        return switch (side) {
            case "north" -> thinZ ? new float[] {0.0F, 0.0F, -ZERO_THICKNESS_FACE_OFFSET} : new float[] {0.0F, 0.0F, 0.0F};
            case "south" -> thinZ ? new float[] {0.0F, 0.0F, ZERO_THICKNESS_FACE_OFFSET} : new float[] {0.0F, 0.0F, 0.0F};
            case "east" -> thinX ? new float[] {ZERO_THICKNESS_FACE_OFFSET, 0.0F, 0.0F} : new float[] {0.0F, 0.0F, 0.0F};
            case "west" -> thinX ? new float[] {-ZERO_THICKNESS_FACE_OFFSET, 0.0F, 0.0F} : new float[] {0.0F, 0.0F, 0.0F};
            case "up" -> thinY ? new float[] {0.0F, ZERO_THICKNESS_FACE_OFFSET, 0.0F} : new float[] {0.0F, 0.0F, 0.0F};
            case "down" -> thinY ? new float[] {0.0F, -ZERO_THICKNESS_FACE_OFFSET, 0.0F} : new float[] {0.0F, 0.0F, 0.0F};
            default -> new float[] {0.0F, 0.0F, 0.0F};
        };
    }

    private static float[] rotateNormal(Rotation rotation, float[] normal) {
        float[] afterX = rotateAxis(normal[0], normal[1], normal[2], 'x', rotation.x());
        float[] afterY = rotateAxis(afterX[0], afterX[1], afterX[2], 'y', rotation.y());
        float[] afterZ = rotateAxis(afterY[0], afterY[1], afterY[2], 'z', rotation.z());
        float length = (float) Math.sqrt(afterZ[0] * afterZ[0] + afterZ[1] * afterZ[1] + afterZ[2] * afterZ[2]);
        if (length == 0.0F) {
            return new float[] {0.0F, 1.0F, 0.0F};
        }
        return new float[] {afterZ[0] / length, afterZ[1] / length, afterZ[2] / length};
    }

    private static float[] rotateAxis(float x, float y, float z, char axis, float degrees) {
        if (degrees == 0.0F) {
            return new float[] {x, y, z};
        }
        double radians = Math.toRadians(degrees);
        float sin = (float) Math.sin(radians);
        float cos = (float) Math.cos(radians);
        return switch (axis) {
            case 'x' -> new float[] {x, y * cos - z * sin, y * sin + z * cos};
            case 'y' -> new float[] {x * cos + z * sin, y, -x * sin + z * cos};
            case 'z' -> new float[] {x * cos - y * sin, x * sin + y * cos, z};
            default -> new float[] {x, y, z};
        };
    }

    private static Rotation rotation(JsonObject element) {
        float[] origin = element.has("origin") ? vector3(element.getAsJsonArray("origin")) : new float[] {8, 8, 8};
        if (!element.has("rotation")) {
            return null;
        }
        JsonElement rotation = element.get("rotation");
        if (rotation.isJsonArray()) {
            float[] euler = vector3(rotation.getAsJsonArray());
            return new Rotation(euler[0], euler[1], euler[2], origin);
        }
        if (rotation.isJsonObject()) {
            JsonObject object = rotation.getAsJsonObject();
            float angle = object.has("angle") ? object.get("angle").getAsFloat() : 0.0F;
            float[] objectOrigin = object.has("origin") ? vector3(object.getAsJsonArray("origin")) : origin;
            return switch (object.has("axis") ? object.get("axis").getAsString() : "y") {
                case "x" -> new Rotation(angle, 0.0F, 0.0F, objectOrigin);
                case "z" -> new Rotation(0.0F, 0.0F, angle, objectOrigin);
                default -> new Rotation(0.0F, angle, 0.0F, objectOrigin);
            };
        }
        return null;
    }

    private static Rotation withAdditionalZ(Rotation base, JsonObject element, float degrees) {
        if (base != null) {
            return new Rotation(base.x(), base.y(), base.z() + degrees, base.origin());
        }
        float[] origin = element.has("origin")
                ? vector3(element.getAsJsonArray("origin")) : new float[] {8.0F, 8.0F, 8.0F};
        return new Rotation(0.0F, 0.0F, degrees, origin);
    }

    private static String textureKey(JsonObject face) {
        if (!face.has("texture")) {
            return "";
        }
        JsonElement texture = face.get("texture");
        String key = texture.isJsonPrimitive() ? texture.getAsString() : "";
        return key.startsWith("#") ? key.substring(1) : key;
    }

    private static boolean usesJavaModelUv(JsonArray elements) {
        float maxUv = 0.0F;
        for (JsonElement elementValue : elements) {
            if (!elementValue.isJsonObject()) {
                continue;
            }
            JsonObject faces = elementValue.getAsJsonObject().getAsJsonObject("faces");
            if (faces == null) {
                continue;
            }
            for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                if (!faceEntry.getValue().isJsonObject()) {
                    continue;
                }
                JsonArray uv = faceEntry.getValue().getAsJsonObject().getAsJsonArray("uv");
                if (uv == null) {
                    continue;
                }
                for (JsonElement coordinate : uv) {
                    maxUv = Math.max(maxUv, Math.abs(coordinate.getAsFloat()));
                }
            }
        }
        return maxUv <= JAVA_MODEL_UV_SIZE;
    }

    private static DisplayTransform displayTransform(JsonObject root, String context) {
        JsonObject display = root.getAsJsonObject("display");
        if (display == null) {
            return DisplayTransform.IDENTITY;
        }
        JsonObject object = display.getAsJsonObject(context);
        if (object == null) {
            return DisplayTransform.IDENTITY;
        }
        float[] rotation = object.has("rotation") ? vector3(object.getAsJsonArray("rotation")) : new float[] {0.0F, 0.0F, 0.0F};
        float[] translation = object.has("translation") ? vector3(object.getAsJsonArray("translation")) : new float[] {0.0F, 0.0F, 0.0F};
        float[] scale = object.has("scale") ? vector3(object.getAsJsonArray("scale")) : new float[] {1.0F, 1.0F, 1.0F};
        return new DisplayTransform(rotation, translation, scale);
    }

    private static ResourceLocation textureLocation(String textureRef) {
        ResourceLocation ref = textureRef.contains(":")
                ? ResourceLocation.tryParse(textureRef)
                : ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, textureRef);
        if (ref == null) {
            ref = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, textureRef);
        }
        return ResourceLocation.fromNamespaceAndPath(ref.getNamespace(), "textures/" + ref.getPath() + ".png");
    }

    private static float[] vector3(JsonArray array) {
        if (array == null || array.size() < 3) {
            return new float[] {0.0F, 0.0F, 0.0F};
        }
        return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static float[] vector4(JsonArray array) {
        if (array == null || array.size() < 4) {
            return new float[] {0.0F, 0.0F, 16.0F, 16.0F};
        }
        return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat(),
                array.get(2).getAsFloat(), array.get(3).getAsFloat()};
    }

    private record Model(int textureWidth, int textureHeight, Map<String, ResourceLocation> textures,
                         JsonArray elements, DisplayTransform headDisplay, boolean javaModelUv) {
        private ResourceLocation texture(String key) {
            return textures.get(key);
        }
    }

    private record DisplayTransform(float[] rotation, float[] translation, float[] scale) {
        private static final DisplayTransform IDENTITY = new DisplayTransform(
                new float[] {0.0F, 0.0F, 0.0F},
                new float[] {0.0F, 0.0F, 0.0F},
                new float[] {1.0F, 1.0F, 1.0F}
        );

        private void apply(PoseStack poseStack) {
            poseStack.translate(translation[0] / 16.0F, translation[1] / 16.0F, translation[2] / 16.0F);
            float radians = (float) (Math.PI / 180.0);
            poseStack.mulPose(new Quaternionf().rotationXYZ(
                    rotation[0] * radians,
                    rotation[1] * radians,
                    rotation[2] * radians));
            poseStack.scale(scale[0], scale[1], scale[2]);
        }
    }

    private record Rotation(float x, float y, float z, float[] origin) {
    }

    private record ClockHandAngles(float hourDegrees, float minuteDegrees) {
    }
}
