package com.stardew.craft.npc.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.npc.runtime.NpcLocationGraph;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Data-driven manager for NPC/story resources.
 *
 * Expected folder layout:
 * - data/stardewcraft/npc/capabilities/*.json
 * - data/stardewcraft/npc/dialogue/*.json
 * - data/stardewcraft/npc/schedules/*.json
 * - data/stardewcraft/npc/tastes/*.json
 * - data/stardewcraft/npc/events/*.json
 */
public final class NpcDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private NpcDataManager() {
    }

    public static final class ReloadListener extends SimpleJsonResourceReloadListener {
        public ReloadListener() {
            super(GSON, "npc");
        }

        @Override
        @SuppressWarnings("null")
        protected void apply(Map<ResourceLocation, JsonElement> objects,
                             ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            Map<String, NpcCapabilityProfile> capabilities = new HashMap<>();
            Map<String, JsonObject> dialogues = new HashMap<>();
            Map<String, JsonObject> schedules = new HashMap<>();
            Map<String, JsonObject> tastes = new HashMap<>();
            Map<String, JsonObject> events = new HashMap<>();
            Set<String> locationMappings = new HashSet<>();
            Map<String, String> locationAliases = new HashMap<>();
            Map<String, NpcLocationAnchor> locationAnchors = new HashMap<>();

            for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
                ResourceLocation id = entry.getKey();
                JsonElement element = entry.getValue();
                if (element == null || !element.isJsonObject()) {
                    continue;
                }

                String path = id.getPath();
                JsonObject root = element.getAsJsonObject();
                String lowerPath = path.toLowerCase(Locale.ROOT);

                if (lowerPath.startsWith("capabilities/")) {
                    parseCapabilities(root, capabilities, id);
                    continue;
                }
                if (lowerPath.startsWith("location_mappings/")) {
                    parseLocationMappings(root, locationMappings, locationAliases, locationAnchors);
                    continue;
                }
                if (lowerPath.startsWith("dialogue/")) {
                    String npcId = extractNpcId(id, path, root, "npc_id");
                    if (npcId != null) {
                        dialogues.put(npcId, root.deepCopy());
                    }
                    continue;
                }
                if (lowerPath.startsWith("schedules/")) {
                    String npcId = extractNpcId(id, path, root, "npc_id");
                    if (npcId != null) {
                        schedules.put(npcId, root.deepCopy());
                    }
                    continue;
                }
                if (lowerPath.startsWith("tastes/")) {
                    String npcId = extractNpcId(id, path, root, "npc_id");
                    if (npcId != null) {
                        tastes.put(npcId, filterTastesWithDiagnostics(npcId, root));
                    }
                    continue;
                }
                if (lowerPath.startsWith("events/")) {
                    String eventId = extractNpcId(id, path, root, "event_id");
                    if (eventId != null) {
                        events.put(eventId, root.deepCopy());
                    }
                }
            }

            if (locationMappings.isEmpty()) {
                locationMappings.add("town");
            }

            Map<String, JsonObject> filteredSchedules = new HashMap<>();
            for (Map.Entry<String, JsonObject> entry : schedules.entrySet()) {
                filteredSchedules.put(entry.getKey(), NpcContentFilter.filterSchedules(entry.getValue(), locationMappings));
            }

            NpcDataRegistry.replaceAll(capabilities, dialogues, filteredSchedules, tastes, events,
                    locationMappings, locationAliases, locationAnchors);
            NpcLocationGraph.reload();
            NpcDataDiagnostics.validateAndLog(capabilities, dialogues, filteredSchedules, tastes);

        }

        private static String extractNpcId(ResourceLocation source, JsonObject root,
                                           String preferredKey) {
            String explicit = readString(root, preferredKey);
            if (explicit != null && !explicit.isBlank()) {
                return namespacedContentId(explicit, source);
            }
            return null;
        }

        private static String extractNpcId(ResourceLocation source, String path, JsonObject root,
                                           String preferredKey) {
            String explicit = extractNpcId(source, root, preferredKey);
            if (explicit != null) {
                return explicit;
            }

            String fileName = path;
            int slash = fileName.lastIndexOf('/');
            if (slash >= 0 && slash < fileName.length() - 1) {
                fileName = fileName.substring(slash + 1);
            }
            if (fileName.endsWith(".json")) {
                fileName = fileName.substring(0, fileName.length() - 5);
            }
            if (fileName.isBlank()) {
                return null;
            }
            return namespacedContentId(fileName, source);
        }

        private static void parseCapabilities(JsonObject root,
                                              Map<String, NpcCapabilityProfile> output,
                                              ResourceLocation resourceId) {
            if (!root.has("npcs") || !root.get("npcs").isJsonArray()) {
                return;
            }

            JsonArray npcs = root.getAsJsonArray("npcs");
            for (JsonElement element : npcs) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject obj = element.getAsJsonObject();
                String npcId = readString(obj, "id");
                if (npcId == null || npcId.isBlank()) {
                    continue;
                }
                npcId = namespacedContentId(npcId, resourceId);

                boolean implemented = readBoolean(obj, "implemented", false);
                boolean pathingEnabled = readBoolean(obj, "pathing_enabled", true);
                String animationProfile = readString(obj, "animation_profile");
                if (animationProfile == null || animationProfile.isBlank()) {
                    animationProfile = NpcCapabilityProfile.ANIM_IDLE_WALK;
                }

                int age = readInt(obj, "age", NpcCapabilityProfile.AGE_ADULT);
                int manners = readInt(obj, "manners", NpcCapabilityProfile.MANNERS_NEUTRAL);
                int socialAnxiety = readInt(obj, "social_anxiety", NpcCapabilityProfile.SOCIAL_OUTGOING);
                int optimism = readInt(obj, "optimism", NpcCapabilityProfile.OPTIMISM_POSITIVE);
                int gender = readInt(obj, "gender", NpcCapabilityProfile.GENDER_MALE);
                boolean datable = readBoolean(obj, "datable", false);

                // 动画检查仅用于客户端动画播放决策，不再禁用服务端寻路。
                // 服务端不一定能加载客户端动画资源文件，不应据此阻断 pathing。
                boolean hasWalkAnimation = NpcAnimationInspector.hasWalkAnimation(npcId.trim().toLowerCase(Locale.ROOT));
                if (pathingEnabled && !hasWalkAnimation) {
                    animationProfile = NpcCapabilityProfile.ANIM_IDLE_ONLY;
                }

                NpcCapabilityProfile profile = new NpcCapabilityProfile(
                    npcId.trim(),
                    implemented,
                    pathingEnabled,
                    animationProfile.trim().toLowerCase(Locale.ROOT),
                    age,
                    manners,
                    socialAnxiety,
                    optimism,
                    gender,
                    datable
                );

                output.put(profile.npcId(), profile);
            }
        }

        private static String namespacedContentId(String rawId, ResourceLocation source) {
            if (rawId == null || rawId.isBlank()) return null;
            String normalized = rawId.trim().toLowerCase(Locale.ROOT);
            if (normalized.indexOf(':') >= 0 || source == null
                    || StardewCraft.MODID.equals(source.getNamespace())) {
                return normalized;
            }
            return source.getNamespace().toLowerCase(Locale.ROOT) + ":" + normalized;
        }

        private static void parseLocationMappings(JsonObject root,
                                                  Set<String> locations,
                                                  Map<String, String> aliases,
                                                  Map<String, NpcLocationAnchor> anchors) {
            if (!root.has("locations") || !root.get("locations").isJsonArray()) {
                // Continue parsing aliases/anchors if provided.
            }

            if (root.has("locations") && root.get("locations").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("locations");
                for (JsonElement element : arr) {
                    if (!element.isJsonPrimitive()) {
                        continue;
                    }
                    String location = element.getAsString();
                    if (location == null || location.isBlank()) {
                        continue;
                    }
                    locations.add(location.trim().toLowerCase(Locale.ROOT));
                }
            }

            if (root.has("aliases") && root.get("aliases").isJsonObject()) {
                JsonObject aliasObj = root.getAsJsonObject("aliases");
                for (Map.Entry<String, JsonElement> entry : aliasObj.entrySet()) {
                    if (!entry.getValue().isJsonPrimitive()) {
                        continue;
                    }
                    String from = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
                    String to = entry.getValue().getAsString() == null ? "" : entry.getValue().getAsString().trim().toLowerCase(Locale.ROOT);
                    if (from.isBlank() || to.isBlank()) {
                        continue;
                    }
                    aliases.put(from, to);
                    locations.add(from);
                    locations.add(to);
                }
            }

            if (root.has("anchors") && root.get("anchors").isJsonObject()) {
                JsonObject anchorObj = root.getAsJsonObject("anchors");
                for (Map.Entry<String, JsonElement> entry : anchorObj.entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        continue;
                    }

                    String name = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
                    if (name.isBlank()) {
                        continue;
                    }

                    JsonObject obj = entry.getValue().getAsJsonObject();
                    double x = readDouble(obj, "x", 0.0D);
                    double y = readDouble(obj, "y", 0.0D);
                    double z = readDouble(obj, "z", 0.0D);
                    boolean indoor = readBoolean(obj, "indoor", false);
                    boolean useGround = readBoolean(obj, "use_ground_height", !indoor);
                    String portalTarget = readString(obj, "portal_target");
                    boolean useScheduleTileOffset = readBoolean(obj, "use_schedule_tile_offset", false);
                    String outdoorDoorPoint = readString(obj, "outdoor_door_point");
                    String indoorEntryPoint = readString(obj, "indoor_entry_point");
                    String indoorExitPoint = readString(obj, "indoor_exit_point");

                    anchors.put(name, new NpcLocationAnchor(x, y, z, indoor, portalTarget, useGround, useScheduleTileOffset, outdoorDoorPoint, indoorEntryPoint, indoorExitPoint));
                    locations.add(name);
                }
            }
        }

        private static JsonObject filterTastesWithDiagnostics(String npcId, JsonObject source) {
            JsonObject filtered = NpcContentFilter.filterTastes(source);
            logDroppedTasteTokens(npcId, source, filtered, "loved");
            logDroppedTasteTokens(npcId, source, filtered, "liked");
            logDroppedTasteTokens(npcId, source, filtered, "neutral");
            logDroppedTasteTokens(npcId, source, filtered, "disliked");
            logDroppedTasteTokens(npcId, source, filtered, "hated");
            return filtered;
        }

        private static void logDroppedTasteTokens(String npcId,
                                                  JsonObject source,
                                                  JsonObject filtered,
                                                  String category) {
            if (!source.has(category) || !source.get(category).isJsonArray()) {
                return;
            }

            Set<String> kept = new HashSet<>();
            if (filtered.has(category) && filtered.get(category).isJsonArray()) {
                JsonArray keptArray = filtered.getAsJsonArray(category);
                for (JsonElement keptEl : keptArray) {
                    if (!keptEl.isJsonPrimitive()) {
                        continue;
                    }
                    kept.add(keptEl.getAsString().toLowerCase(Locale.ROOT));
                }
            }

            List<String> dropped = new ArrayList<>();
            JsonArray sourceArray = source.getAsJsonArray(category);
            for (JsonElement sourceEl : sourceArray) {
                if (!sourceEl.isJsonPrimitive()) {
                    continue;
                }
                String rawToken = sourceEl.getAsString();
                String resolved = NpcContentFilter.resolveExistingItemId(rawToken);
                if (resolved == null || !kept.contains(resolved.toLowerCase(Locale.ROOT))) {
                    dropped.add(rawToken);
                }
            }

            if (!dropped.isEmpty()) {
            }
        }

        private static String readString(JsonObject obj, String key) {
            if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
                return null;
            }
            try {
                return obj.get(key).getAsString();
            } catch (Exception ignored) {
                return null;
            }
        }

        private static boolean readBoolean(JsonObject obj, String key, boolean fallback) {
            if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
                return fallback;
            }
            try {
                return obj.get(key).getAsBoolean();
            } catch (Exception ignored) {
                return fallback;
            }
        }

        private static double readDouble(JsonObject obj, String key, double fallback) {
            if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
                return fallback;
            }
            try {
                return obj.get(key).getAsDouble();
            } catch (Exception ignored) {
                return fallback;
            }
        }

        private static int readInt(JsonObject obj, String key, int fallback) {
            if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
                return fallback;
            }
            try {
                return obj.get(key).getAsInt();
            } catch (Exception ignored) {
                return fallback;
            }
        }
    }
}
