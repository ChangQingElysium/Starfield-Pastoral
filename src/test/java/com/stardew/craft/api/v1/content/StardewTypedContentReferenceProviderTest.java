package com.stardew.craft.api.v1.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.cutscene.StardewCutsceneCommands;
import com.stardew.craft.api.v1.cutscene.StardewCutsceneTriggers;
import com.stardew.craft.api.v1.quest.QuestObjectiveRuntime;
import com.stardew.craft.api.v1.quest.StardewQuestObjectives;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.cutscene.command.EventCommand;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewTypedContentReferenceProviderTest {
    private static final ResourceLocation CONDITION =
            id("typed_condition");
    private static final ResourceLocation ACTION = id("typed_action");
    private static final ResourceLocation OBJECTIVE =
            id("typed_objective");
    private static final ResourceLocation ITEM_QUERY =
            id("typed_item_query");
    private static final ResourceLocation CUTSCENE_COMMAND =
            id("typed_cutscene_command");
    private static final ResourceLocation CUTSCENE_TRIGGER =
            id("typed_cutscene_trigger");

    @Test
    void typedPayloadsProjectOwnerAwareReferencesAndIsolateFailures() {
        ResourceLocation role = id("points_to");
        ResourceLocation targetType = id("target_type");
        StardewContentKey owner = new StardewContentKey(
                id("owner_type"),
                ResourceLocation.fromNamespaceAndPath(
                        "owner_namespace", "entry"));

        ArrayList<StardewContentReference> mutable =
                new ArrayList<>();
        StardewConditions.register(
                CONDITION,
                Codec.STRING,
                (context, value) -> true,
                (key, value) -> {
                    StardewContentReference reference =
                            reference(
                                    role,
                                    targetType,
                                    key.id().getNamespace(),
                                    value);
                    mutable.add(reference);
                    return mutable;
                });
        StardewActions.register(
                ACTION,
                Codec.STRING,
                (context, value) -> null,
                (key, value) -> List.of(reference(
                        role,
                        targetType,
                        key.id().getNamespace(),
                        value)));
        StardewQuestObjectives.register(
                OBJECTIVE,
                Codec.STRING,
                value -> new QuestObjectiveRuntime() {
                },
                (key, value) -> {
                    throw new IllegalStateException("expected failure");
                });
        StardewItemQueries.register(
                ITEM_QUERY,
                Codec.STRING,
                (context, value) -> List.of(),
                (key, value) -> List.of(reference(
                        role,
                        targetType,
                        key.id().getNamespace(),
                        value)));

        var condition = StardewConditions.decode(
                        CONDITION, new JsonPrimitive("condition_target"))
                .getOrThrow();
        List<StardewContentReference> conditionReferences =
                StardewConditions.contentReferences(owner, condition)
                        .getOrThrow();
        assertEquals(
                ResourceLocation.fromNamespaceAndPath(
                        "owner_namespace", "condition_target"),
                conditionReferences.getFirst().target().id());
        mutable.clear();
        assertEquals(1, conditionReferences.size(),
                "reference results must be defensive copies");

        var action = StardewActions.decode(
                        ACTION, new JsonPrimitive("action_target"))
                .getOrThrow();
        assertEquals(
                ResourceLocation.fromNamespaceAndPath(
                        "owner_namespace", "action_target"),
                StardewActions.contentReferences(owner, action)
                        .getOrThrow().getFirst().target().id());

        var itemQuery = StardewItemQueries.decode(
                        ITEM_QUERY,
                        new JsonPrimitive("query_target"))
                .getOrThrow();
        assertEquals(
                ResourceLocation.fromNamespaceAndPath(
                        "owner_namespace", "query_target"),
                StardewItemQueries.contentReferences(owner, itemQuery)
                        .getOrThrow().getFirst().target().id());

        var objective = StardewQuestObjectives.decode(
                        OBJECTIVE,
                        new JsonPrimitive("objective_target"))
                .getOrThrow();
        var failed = StardewQuestObjectives.contentReferences(
                owner, objective);
        assertFalse(failed.result().isPresent());
        assertTrue(failed.error().orElseThrow().message()
                .contains("expected failure"));
    }

    @Test
    void cutsceneExtensionsProjectTypedReferences() {
        ResourceLocation role = id("cutscene_points_to");
        ResourceLocation targetType = id("cutscene_target");
        StardewContentKey owner = new StardewContentKey(
                id("cutscene_owner"),
                ResourceLocation.fromNamespaceAndPath(
                        "orchard", "intro"));
        Codec<String> payload =
                Codec.STRING.fieldOf("target").codec();
        StardewCutsceneCommands.register(
                CUTSCENE_COMMAND,
                payload,
                ignored -> completedCommand(),
                (key, value) -> List.of(reference(
                        role,
                        targetType,
                        key.id().getNamespace(),
                        value)));
        StardewCutsceneTriggers.register(
                CUTSCENE_TRIGGER,
                payload,
                (player, ignored) -> true,
                (key, value) -> List.of(reference(
                        role,
                        targetType,
                        key.id().getNamespace(),
                        value)));

        JsonObject commandData = new JsonObject();
        commandData.addProperty("target", "command_target");
        assertEquals(
                ResourceLocation.fromNamespaceAndPath(
                        "orchard", "command_target"),
                StardewCutsceneCommands.contentReferences(
                                owner,
                                CUTSCENE_COMMAND,
                                commandData)
                        .getOrThrow().getFirst().target().id());
        JsonObject triggerData = new JsonObject();
        triggerData.addProperty("target", "trigger_target");
        assertEquals(
                ResourceLocation.fromNamespaceAndPath(
                        "orchard", "trigger_target"),
                StardewCutsceneTriggers.contentReferences(
                                owner,
                                CUTSCENE_TRIGGER,
                                triggerData)
                        .getOrThrow().getFirst().target().id());
    }

    private static EventCommand completedCommand() {
        return new EventCommand() {
            @Override
            public void tick(EventPlayer player) {
            }

            @Override
            public boolean isComplete() {
                return true;
            }
        };
    }

    private static StardewContentReference reference(
            ResourceLocation role,
            ResourceLocation type,
            String namespace,
            String path
    ) {
        return StardewContentReference.required(
                role,
                new StardewContentKey(
                        type,
                        ResourceLocation.fromNamespaceAndPath(
                                namespace, path)));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_test", path);
    }
}
