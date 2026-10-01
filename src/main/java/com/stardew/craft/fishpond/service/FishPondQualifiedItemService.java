package com.stardew.craft.fishpond.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FishPondQualifiedItemService {
    private static final String OBJECTS_RESOURCE_PATH = "/data/stardewcraft/npc/vanilla/data/objects.json";
    private static final Map<String, ObjectInfo> OBJECT_INFOS = loadObjectInfos();
    private static final Map<String, String> ITEM_ID_OVERRIDES = createItemIdOverrides();
    private static final Map<String, Set<String>> ITEM_CONTEXT_TAGS = loadItemContextTags();

    private FishPondQualifiedItemService() {
    }

    public static Optional<ResolvedItem> resolve(String qualifiedItemId) {
        if (qualifiedItemId == null || qualifiedItemId.isBlank()) {
            return Optional.empty();
        }

        if (!qualifiedItemId.startsWith("(O)")) {
            ResourceLocation directId = ResourceLocation.tryParse(qualifiedItemId);
            if (directId == null || !BuiltInRegistries.ITEM.containsKey(directId)) {
                return Optional.empty();
            }
            Item item = BuiltInRegistries.ITEM.get(directId);
            return Optional.of(new ResolvedItem(
                qualifiedItemId,
                directId.getPath(),
                directId.getPath(),
                directId,
                item,
                ITEM_CONTEXT_TAGS.getOrDefault(directId.toString(), Set.of())
            ));
        }

        String objectKey = qualifiedItemId.substring(3);
        ObjectInfo objectInfo = OBJECT_INFOS.getOrDefault(objectKey, new ObjectInfo(objectKey, Set.of()));
        String name = objectInfo.name();
        ResourceLocation registryId = resolveRegistryId(objectKey, name);
        Item item = registryId != null && BuiltInRegistries.ITEM.containsKey(registryId)
            ? BuiltInRegistries.ITEM.get(registryId)
            : null;
        return Optional.of(new ResolvedItem(
            qualifiedItemId,
            objectKey,
            name,
            registryId,
            item,
            registryId == null ? objectInfo.contextTags() : ITEM_CONTEXT_TAGS.getOrDefault(registryId.toString(), objectInfo.contextTags())
        ));
    }

    public static boolean matches(String qualifiedItemId, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Optional<ResolvedItem> resolved = resolve(qualifiedItemId);
        if (resolved.isEmpty() || resolved.get().item() == null) {
            return false;
        }
        return stack.is(resolved.get().item());
    }

    public static ItemStack createItemStack(String qualifiedItemId, int count) {
        Optional<ResolvedItem> resolved = resolve(qualifiedItemId);
        if (resolved.isEmpty() || resolved.get().item() == null || count <= 0) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(resolved.get().item(), count);
    }

    public static Set<String> getContextTags(ItemStack stack) {
        if (stack.isEmpty()) {
            return Set.of();
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ITEM_CONTEXT_TAGS.getOrDefault(itemId.toString(), Set.of());
    }

    private static ResourceLocation resolveRegistryId(String objectKey, String objectName) {
        String override = ITEM_ID_OVERRIDES.get(objectKey);
        if (override != null) {
            ResourceLocation overrideId = ResourceLocation.tryParse(override);
            if (overrideId != null && BuiltInRegistries.ITEM.containsKey(overrideId)) {
                return overrideId;
            }
        }

        String normalizedPath = normalizeName(objectName);
        ResourceLocation stardewcraftId = new ResourceLocation(StardewCraft.MODID, normalizedPath);
        if (BuiltInRegistries.ITEM.containsKey(stardewcraftId)) {
            return stardewcraftId;
        }
        ResourceLocation minecraftId = new ResourceLocation(normalizedPath);
        if (BuiltInRegistries.ITEM.containsKey(minecraftId)) {
            return minecraftId;
        }
        return null;
    }

    private static String normalizeName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT)
            .replace("'", "")
            .replace(":", " ")
            .replace("-", " ")
            .replace("(", " ")
            .replace(")", " ");
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        return normalized.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
    }

    private static Map<String, ObjectInfo> loadObjectInfos() {
        Map<String, ObjectInfo> infos = new HashMap<>();
        try (var stream = FishPondQualifiedItemService.class.getResourceAsStream(OBJECTS_RESOURCE_PATH)) {
            if (stream == null) {
                StardewCraft.LOGGER.warn("Fish pond object data resource missing: {}", OBJECTS_RESOURCE_PATH);
                return infos;
            }
            Reader reader = new java.io.InputStreamReader(stream, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonObject object = entry.getValue().getAsJsonObject();
                if (!object.has("Name")) {
                    continue;
                }
                Set<String> contextTags = new LinkedHashSet<>();
                if (object.has("ContextTags") && object.get("ContextTags").isJsonArray()) {
                    for (JsonElement tagElement : object.getAsJsonArray("ContextTags")) {
                        if (!tagElement.isJsonPrimitive()) {
                            continue;
                        }
                        contextTags.add(tagElement.getAsString().toLowerCase(Locale.ROOT));
                    }
                }
                infos.put(entry.getKey(), new ObjectInfo(object.get("Name").getAsString(), Collections.unmodifiableSet(contextTags)));
            }
        } catch (Exception ex) {
            StardewCraft.LOGGER.error("Failed to load fish pond object data from {}", OBJECTS_RESOURCE_PATH, ex);
        }
        return infos;
    }

    private static Map<String, Set<String>> loadItemContextTags() {
        Map<String, Set<String>> tagsByItemId = new HashMap<>();
        for (Map.Entry<String, ObjectInfo> entry : OBJECT_INFOS.entrySet()) {
            ResourceLocation registryId = resolveRegistryId(entry.getKey(), entry.getValue().name());
            if (registryId == null) {
                continue;
            }
            tagsByItemId.put(registryId.toString(), entry.getValue().contextTags());
        }
        return tagsByItemId;
    }

    private static Map<String, String> createItemIdOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("388", "stardewcraft:wood_normal");
        overrides.put("571", "stardewcraft:limestone_mineral");
        overrides.put("689", "stardewcraft:warp_totem_mountain");
        overrides.put("709", "stardewcraft:wood_hard");
        overrides.put("766", "stardewcraft:slime_item");
        overrides.put("PowdermelonSeeds", "stardewcraft:powder_melon_seeds");
        for (int skill = 0; skill < 5; skill++) {
            overrides.put("SkillBook_" + skill, "stardewcraft:skill_book_" + skill);
        }
        return overrides;
    }

    public record ResolvedItem(String qualifiedItemId,
                               String objectKey,
                               String displayName,
                               ResourceLocation registryId,
                               Item item,
                               Set<String> contextTags) {
    }

    private record ObjectInfo(String name, Set<String> contextTags) {
    }
}
