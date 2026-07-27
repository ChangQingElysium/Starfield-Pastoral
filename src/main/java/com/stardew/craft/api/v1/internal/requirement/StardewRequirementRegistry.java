package com.stardew.craft.api.v1.internal.requirement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.requirement.StardewRequirement;
import com.stardew.craft.api.v1.requirement.StardewRequirementProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Objects;

/** Built-in descriptions plus ordered, failure-isolated addon composition. */
public final class StardewRequirementRegistry {
    private static final OrderedExtensionRegistry<StardewRequirementProvider> PROVIDERS =
            new OrderedExtensionRegistry<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "condition/requirements"));

    private StardewRequirementRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewRequirementProvider provider
    ) {
        PROVIDERS.register(
                Objects.requireNonNull(id, "id"),
                priority,
                Objects.requireNonNull(provider, "provider"));
    }

    public static StardewRequirement evaluate(
            StardewConditionContext context,
            StardewCondition condition
    ) {
        String[] error = new String[1];
        var tested = StardewConditions.test(condition, context)
                .resultOrPartial(message -> error[0] = message);
        StardewRequirement.State state = tested
                .map(value -> value
                        ? StardewRequirement.State.SATISFIED
                        : StardewRequirement.State.UNSATISFIED)
                .orElse(StardewRequirement.State.ERROR);
        StardewRequirement current = builtin(condition, state);
        for (var entry : PROVIDERS.entries()) {
            try {
                StardewRequirement proposedRequirement = current;
                StardewRequirement candidate = PROVIDERS.invoke(
                        entry,
                        provider -> provider.describe(
                                context,
                                condition,
                                proposedRequirement));
                if (candidate == null) {
                    continue;
                }
                if (!candidate.type().equals(condition.type())
                        || candidate.state() != state) {
                    StardewCraft.LOGGER.error(
                            "Requirement provider {} changed authoritative identity/state for {}",
                            entry.id(), condition.type());
                    continue;
                }
                current = candidate;
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Requirement provider {} failed for {}",
                        entry.id(), condition.type(), exception);
            }
        }
        if (state == StardewRequirement.State.ERROR && error[0] != null) {
            StardewCraft.LOGGER.error(
                    "Condition requirement evaluation failed for {}: {}",
                    condition.type(), error[0]);
        }
        return current;
    }

    private static StardewRequirement builtin(
            StardewCondition condition,
            StardewRequirement.State state
    ) {
        JsonElement encoded = StardewConditions.encodeData(condition)
                .result().orElse(null);
        Component description = encoded != null && encoded.isJsonObject()
                ? builtinDescription(condition.type(), encoded.getAsJsonObject())
                : null;
        return new StardewRequirement(
                condition.type(),
                state,
                description == null
                        ? Component.literal(condition.type().toString())
                        : description,
                description != null);
    }

    private static Component builtinDescription(
            ResourceLocation type,
            JsonObject data
    ) {
        if (!StardewCraft.MODID.equals(type.getNamespace())) {
            return null;
        }
        try {
            return switch (type.getPath()) {
                case "always" -> data.has("value")
                        && data.get("value").getAsBoolean()
                        ? Component.literal("Always")
                        : Component.translatable(
                                "stardewcraft.jei.shop.condition.never");
                case "has_item" -> itemRequirement(
                        data, "stardewcraft.jei.shop.condition.has_item");
                case "lacks_item" -> itemRequirement(
                        data, "stardewcraft.jei.shop.condition.lacks_item");
                case "money" -> moneyRequirement(data);
                case "flag" -> Component.translatable(data.has("present")
                                && !data.get("present").getAsBoolean()
                                ? "stardewcraft.jei.shop.condition.story_absent"
                                : "stardewcraft.jei.shop.condition.story_required");
                case "skill" -> Component.translatable(
                        "stardewcraft.jei.shop.condition.skill",
                        data.get("skill").getAsString(),
                        data.get("level").getAsInt());
                case "season" -> Component.literal(
                        "Season: " + join(data, "seasons"));
                case "time" -> Component.literal(
                        "Time: " + data.get("start").getAsInt()
                                + "-" + data.get("end").getAsInt());
                case "seen_event" -> Component.literal(
                        "Event: " + data.get("id").getAsString());
                case "location" -> Component.literal("Location requirement");
                default -> null;
            };
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Component itemRequirement(
            JsonObject data,
            String translationKey
    ) {
        ResourceLocation id = ResourceLocation.tryParse(
                data.get("item").getAsString());
        Item item = id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? BuiltInRegistries.ITEM.get(id) : Items.AIR;
        Object display = item == Items.AIR
                ? data.get("item").getAsString()
                : item.getDescription();
        return Component.translatable(
                translationKey,
                display,
                data.has("count") ? data.get("count").getAsInt() : 1);
    }

    private static Component moneyRequirement(JsonObject data) {
        int min = data.get("min").getAsInt();
        int max = data.get("max").getAsInt();
        if (min == Integer.MIN_VALUE) {
            return Component.translatable(
                    "stardewcraft.jei.shop.condition.money_max", max);
        }
        if (max == Integer.MAX_VALUE) {
            return Component.translatable(
                    "stardewcraft.jei.shop.condition.money_min", min);
        }
        return Component.translatable(
                "stardewcraft.jei.shop.condition.money_range", min, max);
    }

    private static String join(JsonObject data, String field) {
        StringBuilder result = new StringBuilder();
        for (JsonElement value : data.getAsJsonArray(field)) {
            if (!result.isEmpty()) {
                result.append(", ");
            }
            result.append(value.getAsString());
        }
        return result.toString();
    }
}
