package com.stardew.craft.api.v1.internal.progress;

import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.progress.StardewProgressDomains;
import com.stardew.craft.api.v1.progress.StardewProgressKey;
import com.stardew.craft.api.v1.progress.StardewProgressOperation;
import com.stardew.craft.api.v1.progress.StardewProgressPhase;
import com.stardew.craft.api.v1.progress.StardewProgressSnapshot;
import com.stardew.craft.api.v1.requirement.StardewRequirement;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import com.stardew.craft.api.v1.requirement.StardewRequirementTypes;
import com.stardew.craft.api.v1.requirement.StardewRequirements;
import com.stardew.craft.quest.QuestDataLoader;
import com.stardew.craft.quest.QuestManager;
import com.stardew.craft.specialorder.SpecialOrderManager;
import com.stardew.craft.specialorder.SpecialOrderWorldData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Built-in adapters plus conservative phase semantics for addon domains. */
public final class StardewProgressRequirementRegistry {
    private StardewProgressRequirementRegistry() {
    }

    public static StardewRequirementReport requirements(
            ServerPlayer player,
            StardewProgressKey key,
            StardewProgressOperation operation
    ) {
        StardewProgressSnapshot snapshot =
                StardewProgressRegistry.inspect(player, key);
        if (snapshot == null) {
            return new StardewRequirementReport(List.of(
                    requirement(
                            StardewRequirementTypes
                                    .PROGRESS_ENTRY_EXISTS,
                            false,
                            Component.translatable(
                                    "stardewcraft.requirement.progress.exists",
                                    key.id().toString()))));
        }

        ArrayList<StardewRequirement> requirements =
                new ArrayList<>();
        requirements.add(requirement(
                StardewRequirementTypes.PROGRESS_ENTRY_EXISTS,
                true,
                Component.translatable(
                        "stardewcraft.requirement.progress.exists",
                        key.id().toString())));
        requirements.add(requirement(
                StardewRequirementTypes
                        .PROGRESS_DEFINITION_AVAILABLE,
                snapshot.definitionAvailable(),
                Component.translatable(
                        "stardewcraft.requirement.progress.definition",
                        key.id().toString())));

        if (operation == StardewProgressOperation.ACCEPT
                && key.domain().equals(
                        StardewProgressDomains.QUEST)) {
            addQuestAcceptance(
                    player, key, requirements);
        } else if (operation
                        == StardewProgressOperation.ACCEPT
                && key.domain().equals(
                        StardewProgressDomains.SPECIAL_ORDER)) {
            addSpecialOrderAcceptance(
                    player, key, requirements);
        } else if (operation
                == StardewProgressOperation.CLAIM_REWARD) {
            requirements.add(requirement(
                    StardewRequirementTypes
                            .PROGRESS_REWARD_CLAIMABLE,
                    snapshot.rewardClaimable(),
                    Component.translatable(
                            "stardewcraft.requirement.progress.reward",
                            key.id().toString())));
        } else {
            requirements.add(requirement(
                    StardewRequirementTypes
                            .PROGRESS_ACCEPT_AVAILABLE,
                    snapshot.phase()
                            == StardewProgressPhase.AVAILABLE,
                    Component.translatable(
                            "stardewcraft.requirement.progress.accept",
                            key.id().toString())));
        }
        return new StardewRequirementReport(requirements);
    }

    private static void addQuestAcceptance(
            ServerPlayer player,
            StardewProgressKey key,
            List<StardewRequirement> requirements
    ) {
        QuestManager manager = QuestManager.of(player);
        boolean notActive = manager != null
                && !manager.hasQuest(key.id().toString());
        requirements.add(requirement(
                StardewRequirementTypes.PROGRESS_ACCEPT_AVAILABLE,
                notActive,
                Component.translatable(
                        "stardewcraft.requirement.progress.quest_not_active",
                        key.id().toString())));

        var definition =
                QuestDataLoader.getDefinition(key.id());
        if (definition != null) {
            requirements.addAll(
                    StardewRequirements.evaluateAll(
                            StardewConditionContext.forPlayer(player),
                            definition.availableWhen())
                    .requirements());
        }
    }

    private static void addSpecialOrderAcceptance(
            ServerPlayer player,
            StardewProgressKey key,
            List<StardewRequirement> requirements
    ) {
        SpecialOrderWorldData data =
                SpecialOrderWorldData.get(player.serverLevel());
        boolean unlocked =
                SpecialOrderManager.isUnlockedFor(player);
        boolean slotAvailable = data.active().stream()
                .noneMatch(order -> order.accepted()
                        && !order.complete()
                        && !order.failed())
                && !data.normalOrderAcceptedThisRefresh();
        boolean offered = data.available().stream()
                .map(order -> StardewProgressRegistry
                        .specialOrderKey(order.orderId()))
                .anyMatch(key::equals);
        requirements.add(requirement(
                StardewRequirementTypes
                        .PROGRESS_ACCESS_UNLOCKED,
                unlocked,
                Component.translatable(
                        "stardewcraft.requirement.progress.access",
                        key.id().toString())));
        requirements.add(requirement(
                StardewRequirementTypes
                        .PROGRESS_ACCEPTANCE_SLOT_AVAILABLE,
                slotAvailable,
                Component.translatable(
                        "stardewcraft.requirement.progress.slot",
                        key.id().toString())));
        requirements.add(requirement(
                StardewRequirementTypes
                        .PROGRESS_ACCEPT_AVAILABLE,
                offered,
                Component.translatable(
                        "stardewcraft.requirement.progress.accept",
                        key.id().toString())));
    }

    private static StardewRequirement requirement(
            ResourceLocation type,
            boolean satisfied,
            Component description
    ) {
        return new StardewRequirement(
                type,
                satisfied
                        ? StardewRequirement.State.SATISFIED
                        : StardewRequirement.State.UNSATISFIED,
                description,
                true);
    }
}
