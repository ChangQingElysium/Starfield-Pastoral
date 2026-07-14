package com.stardew.craft.quest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.action.StardewAction;
import com.stardew.craft.api.v1.action.StardewActionContext;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.quest.QuestObjectiveContext;
import com.stardew.craft.api.v1.quest.QuestObjectiveResult;
import com.stardew.craft.api.v1.quest.QuestObjectiveRuntime;
import com.stardew.craft.api.v1.quest.QuestProgressEvent;
import com.stardew.craft.api.v1.quest.QuestProgressEvents;
import com.stardew.craft.api.v1.quest.QuestText;
import com.stardew.craft.api.v1.quest.StardewQuestDefinition;
import com.stardew.craft.api.v1.quest.StardewQuestObjective;
import com.stardew.craft.api.v1.quest.StardewQuestObjectives;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Runtime state for one immutable {@link StardewQuestDefinition}. */
public final class DataDrivenQuest extends StardewQuest {
    private StardewQuestDefinition definition;
    private StardewQuestObjective objective;
    private QuestObjectiveRuntime runtime;
    private CompoundTag suspendedObjectiveState;

    public DataDrivenQuest() {
        this.questType = TYPE_DATA_DRIVEN;
    }

    public DataDrivenQuest(ResourceLocation id, StardewQuestDefinition definition) {
        this();
        bind(id, definition);
    }

    private void bind(ResourceLocation id, StardewQuestDefinition definition) {
        this.definitionId = id;
        this.id = QuestDataLoader.displayId(id);
        this.definition = definition;
        this.objective = definition.objective();
        this.runtime = StardewQuestObjectives.createRuntime(objective)
                .getOrThrow(message -> new IllegalArgumentException("Quest " + id + ": " + message));
        applyText(definition.title(), TextSlot.TITLE);
        applyText(definition.description(), TextSlot.DESCRIPTION);
        applyText(definition.objectiveText(), TextSlot.OBJECTIVE);
        this.moneyReward = definition.moneyReward();
        this.rewardDescription = definition.rewardDescription()
                .map(text -> text.translate().isBlank() ? text.literal() : text.translate())
                .orElse(null);
        this.canBeCancelled = definition.canCancel();
        this.daysLeft = definition.days();
        this.nextQuests = definition.nextQuests().stream().map(ResourceLocation::toString).toList();
    }

    @Override
    public void onAccept(ServerPlayer player) {
        if (runtime != null) {
            runtime.onAccepted(new QuestObjectiveContext(player));
        }
        if (definition != null) {
            executeActions(player, definition.onAccept(), "on_accept");
        }
    }

    @Override
    public void questComplete(ServerPlayer player) {
        if (completed) {
            return;
        }
        super.questComplete(player);
        if (runtime != null) {
            runtime.onCompleted(new QuestObjectiveContext(player));
        }
        if (definition != null) {
            executeActions(player, definition.onComplete(), "on_complete");
        }
    }

    @Override
    public void onMonsterSlain(ServerPlayer player, String monsterType) {
        progress(player, new QuestProgressEvent(QuestProgressEvents.MONSTER_SLAIN, monsterType, "", 1));
    }

    @Override
    public void onFishCaught(ServerPlayer player, String itemId, int count) {
        progress(player, new QuestProgressEvent(QuestProgressEvents.FISH_CAUGHT, itemId, "", count));
    }

    @Override
    public void onItemReceived(ServerPlayer player, String itemId, int count) {
        progress(player, new QuestProgressEvent(QuestProgressEvents.ITEM_RECEIVED, itemId, "", count));
    }

    @Override
    public boolean onItemOfferedToNpc(ServerPlayer player, String npcId, String itemId) {
        return progress(player, new QuestProgressEvent(
                QuestProgressEvents.ITEM_OFFERED_TO_NPC, itemId, npcId, 1)).consumed();
    }

    @Override
    public void onRecipeCrafted(ServerPlayer player, String recipeId) {
        progress(player, new QuestProgressEvent(QuestProgressEvents.RECIPE_CRAFTED, recipeId, "", 1));
    }

    @Override
    public void onNpcSocialized(ServerPlayer player, String npcId) {
        progress(player, new QuestProgressEvent(QuestProgressEvents.NPC_SOCIALIZED, npcId, "", 1));
    }

    @Override
    public void onWarped(ServerPlayer player, String location) {
        progress(player, new QuestProgressEvent(QuestProgressEvents.WARPED, location, "", 1));
    }

    @Override
    public void onBuildingExists(ServerPlayer player, String buildingType) {
        progress(player, new QuestProgressEvent(QuestProgressEvents.BUILDING_EXISTS, buildingType, "", 1));
    }

    @Override
    public void onMineFloorReached(ServerPlayer player, int floor) {
        progress(player, new QuestProgressEvent(
                QuestProgressEvents.MINE_FLOOR_REACHED, Integer.toString(floor), "", 1));
    }

    @Override
    public List<Component> getObjectiveComponents() {
        return runtime == null ? super.getObjectiveComponents() : runtime.objectiveComponents(getObjectiveComponent());
    }

    @Override
    public int getCurrentObjectiveCount() {
        return runtime == null ? -1 : runtime.currentCount();
    }

    @Override
    public int getTotalObjectiveCount() {
        return runtime == null ? -1 : runtime.targetCount();
    }

    public ResourceLocation getObjectiveType() {
        return objective == null ? ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "unavailable")
                : objective.type();
    }

    void refreshDefinition() {
        if (definitionId == null) {
            return;
        }
        CompoundTag previousState = runtime == null ? suspendedObjectiveState : runtime.saveState();
        StardewQuestDefinition current = QuestDataLoader.getDefinition(definitionId);
        if (current == null) {
            definition = null;
            objective = null;
            runtime = null;
            suspendedObjectiveState = previousState == null ? new CompoundTag() : previousState.copy();
            title = "Unavailable quest: " + definitionId;
            titleKey = "";
            description = "The datapack definition for this quest is currently unavailable.";
            descriptionKey = "";
            objectiveText = "";
            objectiveKey = "";
            canBeCancelled = true;
            return;
        }

        boolean previousAccepted = accepted;
        boolean previousCompleted = completed;
        boolean previousDaily = dailyQuest;
        boolean previousShowNew = showNew;
        boolean previousDestroy = destroy;
        boolean previousNotified = notifiedComplete;
        int previousDaysLeft = daysLeft;
        int previousAcceptedDay = dayQuestAccepted;
        bind(definitionId, current);
        accepted = previousAccepted;
        completed = previousCompleted;
        dailyQuest = previousDaily;
        showNew = previousShowNew;
        destroy = previousDestroy;
        notifiedComplete = previousNotified;
        daysLeft = previousDaysLeft;
        dayQuestAccepted = previousAcceptedDay;
        if (previousState != null && runtime != null) {
            runtime.loadState(previousState);
        }
        suspendedObjectiveState = null;
    }

    @Override
    public boolean matchesItemDelivery(String npcId, String itemId) {
        return accepted && !completed && !destroy && runtime != null
                && runtime.matchesItemDelivery(npcId, itemId);
    }

    @Override
    public String getDeliveryTargetMessage() {
        return runtime == null ? "" : runtime.deliveryTargetMessage();
    }

    @Override
    public boolean isSecretQuest() {
        return runtime != null && runtime.isSecret();
    }

    private QuestObjectiveResult progress(ServerPlayer player, QuestProgressEvent event) {
        if (completed || !accepted || runtime == null) {
            return QuestObjectiveResult.NONE;
        }
        QuestObjectiveResult result = runtime.onProgress(new QuestObjectiveContext(player), event);
        if (result.completed()) {
            questComplete(player);
        }
        return result;
    }

    private void executeActions(ServerPlayer player, List<StardewAction> actions, String phase) {
        StardewActionContext context = StardewActionContext.forPlayer(player);
        for (StardewAction action : actions) {
            StardewActions.execute(action, context).resultOrPartial(message ->
                    StardewCraft.LOGGER.error("[Quest] {} action failed for {}: {}", phase, definitionId, message));
        }
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        if (objective != null) {
            StardewQuestObjectives.CODEC.encodeStart(JsonOps.INSTANCE, objective)
                    .resultOrPartial(message -> StardewCraft.LOGGER.error(
                            "[Quest] Failed to encode objective for {}: {}", definitionId, message))
                    .ifPresent(json -> tag.putString("ObjectiveJson", json.toString()));
        }
        saveStateExtra(tag);
    }

    @Override
    protected void saveStateExtra(CompoundTag tag) {
        if (runtime != null) {
            tag.put("ObjectiveState", runtime.saveState());
        } else if (suspendedObjectiveState != null) {
            tag.put("ObjectiveState", suspendedObjectiveState.copy());
        }
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        if (definitionId != null) {
            StardewQuestDefinition current = QuestDataLoader.getDefinition(definitionId);
            if (current != null) {
                bind(definitionId, current);
            }
        }
        if (runtime == null && tag.contains("ObjectiveJson", 8)) {
            StardewQuestObjectives.CODEC.parse(
                            JsonOps.INSTANCE, JsonParser.parseString(tag.getString("ObjectiveJson")))
                    .resultOrPartial(message -> StardewCraft.LOGGER.error(
                            "[Quest] Failed to decode saved objective for {}: {}", definitionId, message))
                    .ifPresent(value -> {
                        objective = value;
                        runtime = StardewQuestObjectives.createRuntime(value)
                                .resultOrPartial(message -> StardewCraft.LOGGER.error(
                                        "[Quest] Failed to create saved objective for {}: {}", definitionId, message))
                                .orElse(null);
                    });
        }
        loadStateExtra(tag);
    }

    @Override
    protected void loadStateExtra(CompoundTag tag) {
        if (runtime != null && tag.contains("ObjectiveState", 10)) {
            runtime.loadState(tag.getCompound("ObjectiveState"));
        } else if (runtime == null && tag.contains("ObjectiveState", 10)) {
            suspendedObjectiveState = tag.getCompound("ObjectiveState").copy();
        }
    }

    private void applyText(QuestText text, TextSlot slot) {
        String[] args = text.args().toArray(String[]::new);
        switch (slot) {
            case TITLE -> {
                title = text.literal();
                setLocalizedTitle(text.translate(), args);
            }
            case DESCRIPTION -> {
                description = text.literal();
                setLocalizedDescription(text.translate(), args);
            }
            case OBJECTIVE -> {
                objectiveText = text.literal();
                setLocalizedObjective(text.translate(), args);
            }
        }
    }

    private enum TextSlot {
        TITLE,
        DESCRIPTION,
        OBJECTIVE
    }
}
