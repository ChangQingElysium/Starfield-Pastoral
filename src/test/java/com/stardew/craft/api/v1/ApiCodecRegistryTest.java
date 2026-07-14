package com.stardew.craft.api.v1;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.action.StardewActionResult;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.cutscene.StardewCutsceneTriggers;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.quest.QuestObjectiveRuntime;
import com.stardew.craft.api.v1.quest.StardewQuestObjectives;
import com.stardew.craft.api.v1.shop.StardewShopEntry;
import com.stardew.craft.api.v1.shop.StardewShopInventoryProviders;
import com.stardew.craft.api.v1.specialorder.StardewSpecialOrderObjectives;
import com.stardew.craft.api.v1.specialorder.StardewSpecialOrderRewards;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiCodecRegistryTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void conditionCodecRoundTripsRegisteredPayload() {
        ResourceLocation id = uniqueId("condition");
        StardewConditions.register(id, Codec.INT, (context, value) -> value > 0);

        JsonObject root = typedValue(id, 7);
        var decoded = StardewConditions.CODEC.parse(JsonOps.INSTANCE, root).result();

        assertTrue(decoded.isPresent());
        assertEquals(id, decoded.get().type());
        assertTrue(StardewConditions.CODEC.encodeStart(JsonOps.INSTANCE, decoded.get()).result().isPresent());
    }

    @Test
    void itemQueryCodecRoundTripsRegisteredPayload() {
        ResourceLocation id = uniqueId("query");
        StardewItemQueries.register(id, Codec.STRING, (context, value) -> List.of());

        JsonObject root = typedValue(id, "anything");
        var decoded = StardewItemQueries.CODEC.parse(JsonOps.INSTANCE, root).result();

        assertTrue(decoded.isPresent());
        assertEquals(id, decoded.get().type());
        assertTrue(StardewItemQueries.CODEC.encodeStart(JsonOps.INSTANCE, decoded.get()).result().isPresent());
    }

    @Test
    void actionCodecRoundTripsRegisteredPayload() {
        ResourceLocation id = uniqueId("action");
        StardewActions.register(id, Codec.BOOL, (context, value) -> StardewActionResult.ok());

        JsonObject root = typedValue(id, true);
        var decoded = StardewActions.CODEC.parse(JsonOps.INSTANCE, root).result();

        assertTrue(decoded.isPresent());
        assertEquals(id, decoded.get().type());
        assertTrue(StardewActions.CODEC.encodeStart(JsonOps.INSTANCE, decoded.get()).result().isPresent());
    }

    @Test
    void questObjectiveCodecCreatesRegisteredRuntime() {
        ResourceLocation id = uniqueId("objective");
        StardewQuestObjectives.register(id, Codec.INT, value -> new QuestObjectiveRuntime() {
            @Override
            public int targetCount() {
                return value;
            }
        });

        JsonObject root = typedValue(id, 4);
        var decoded = StardewQuestObjectives.CODEC.parse(JsonOps.INSTANCE, root).result();

        assertTrue(decoded.isPresent());
        assertEquals(id, decoded.get().type());
        assertEquals(4, StardewQuestObjectives.createRuntime(decoded.get()).result().orElseThrow().targetCount());
        assertTrue(StardewQuestObjectives.CODEC.encodeStart(JsonOps.INSTANCE, decoded.get()).result().isPresent());
    }

    @Test
    void unknownAndUnnamespacedTypesFailClosed() {
        JsonObject unknown = typedValue(ResourceLocation.fromNamespaceAndPath("addon", "missing"), true);
        assertTrue(StardewConditions.CODEC.parse(JsonOps.INSTANCE, unknown).error().isPresent());

        JsonObject unnamespaced = new JsonObject();
        unnamespaced.addProperty("type", "missing");
        unnamespaced.addProperty("data", true);
        assertTrue(StardewConditions.CODEC.parse(JsonOps.INSTANCE, unnamespaced).error().isPresent());
    }

    @Test
    void duplicateTypeIdsAreRejected() {
        ResourceLocation id = uniqueId("duplicate");
        StardewActions.register(id, Codec.BOOL, (context, value) -> StardewActionResult.ok());

        assertThrows(IllegalStateException.class,
                () -> StardewActions.register(id, Codec.BOOL,
                        (context, value) -> StardewActionResult.ok()));
    }

    @Test
    void phaseThreeAddonRegistriesDecodePayloadsAndExposeProviders() {
        ResourceLocation trigger = uniqueId("trigger");
        StardewCutsceneTriggers.register(trigger, Codec.INT, (player, value) -> value > 0);
        assertTrue(StardewCutsceneTriggers.validate(trigger, primitiveData(3)).result().orElseThrow());

        ResourceLocation objective = uniqueId("special_objective");
        StardewSpecialOrderObjectives.register(objective, Codec.STRING, (context, value, event) -> 0);
        assertTrue(StardewSpecialOrderObjectives.decode(objective, JsonOps.INSTANCE.createString("target"))
                .result().isPresent());

        ResourceLocation reward = uniqueId("special_reward");
        StardewSpecialOrderRewards.register(reward, Codec.FLOAT, (context, value) -> { });
        assertTrue(StardewSpecialOrderRewards.decode(reward, JsonOps.INSTANCE.createFloat(2.0F))
                .result().isPresent());

        ResourceLocation provider = uniqueId("shop_provider");
        StardewShopInventoryProviders.register(provider, context -> List.of(new StardewShopEntry(
                "minecraft:apple", "", "", 1, 1, Optional.empty(), 0,
                List.of(), 1, 0, Optional.empty(), -1, 0, 1, List.of())));
        assertTrue(StardewShopInventoryProviders.registeredIds().contains(provider));
    }

    private static JsonObject primitiveData(int value) {
        JsonObject root = new JsonObject();
        root.addProperty("data", value);
        return root;
    }

    private static JsonObject typedValue(ResourceLocation id, boolean value) {
        JsonObject root = new JsonObject();
        root.addProperty("type", id.toString());
        root.addProperty("data", value);
        return root;
    }

    private static JsonObject typedValue(ResourceLocation id, int value) {
        JsonObject root = new JsonObject();
        root.addProperty("type", id.toString());
        root.addProperty("data", value);
        return root;
    }

    private static JsonObject typedValue(ResourceLocation id, String value) {
        JsonObject root = new JsonObject();
        root.addProperty("type", id.toString());
        root.addProperty("data", value);
        return root;
    }

    private static ResourceLocation uniqueId(String path) {
        return ResourceLocation.fromNamespaceAndPath("testaddon", path + "_" + IDS.incrementAndGet());
    }
}
