package com.example.stardewaddon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.action.StardewActionResult;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.agriculture.StardewAgricultureDataApi;
import com.stardew.craft.api.v1.agriculture.StardewAnimalData;
import com.stardew.craft.api.v1.agriculture.StardewAnimalDailyHandlers;
import com.stardew.craft.api.v1.agriculture.StardewAnimalPurchaseDisplay;
import com.stardew.craft.api.v1.agriculture.StardewAnimalPurchaseDisplays;
import com.stardew.craft.api.v1.agriculture.StardewAnimalPersistentData;
import com.stardew.craft.api.v1.agriculture.StardewAnimalQueryDefinition;
import com.stardew.craft.api.v1.agriculture.StardewAnimalQueryDefinitions;
import com.stardew.craft.api.v1.agriculture.StardewAnimalReproductionRules;
import com.stardew.craft.api.v1.agriculture.StardewBuildingData;
import com.stardew.craft.api.v1.agriculture.StardewCropData;
import com.stardew.craft.api.v1.agriculture.StardewCropDailyContext;
import com.stardew.craft.api.v1.agriculture.StardewCropHarvestContext;
import com.stardew.craft.api.v1.agriculture.StardewCropHarvestResult;
import com.stardew.craft.api.v1.agriculture.StardewCropRemovalCause;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntimeAdapter;
import com.stardew.craft.api.v1.agriculture.StardewCropState;
import com.stardew.craft.api.v1.agriculture.StardewCropType;
import com.stardew.craft.api.v1.agriculture.StardewCropTypes;
import com.stardew.craft.api.v1.agriculture.StardewTreeData;
import com.stardew.craft.api.v1.agriculture.StardewTruffleFoundHandlers;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.content.StardewContentAlias;
import com.stardew.craft.api.v1.content.StardewContentDefinition;
import com.stardew.craft.api.v1.content.StardewContentKey;
import com.stardew.craft.api.v1.content.StardewContentReference;
import com.stardew.craft.api.v1.content.StardewContentReferenceRoles;
import com.stardew.craft.api.v1.content.StardewContentTypes;
import com.stardew.craft.api.v1.content.StardewContents;
import com.stardew.craft.api.v1.communitycenter.StardewCommunityCenterPersistentData;
import com.stardew.craft.api.v1.communitycenter.StardewCommunityCenterRewards;
import com.stardew.craft.api.v1.communitycenter.StardewCommunityCenterVariants;
import com.stardew.craft.api.v1.cutscene.StardewCutsceneTriggers;
import com.stardew.craft.api.v1.equipment.StardewEquipmentData;
import com.stardew.craft.api.v1.equipment.StardewEquipmentDataApi;
import com.stardew.craft.api.v1.equipment.StardewWeaponSkillHandlers;
import com.stardew.craft.api.v1.economy.StardewCost;
import com.stardew.craft.api.v1.economy.StardewCosts;
import com.stardew.craft.api.v1.economy.StardewCurrencies;
import com.stardew.craft.api.v1.economy.StardewCurrency;
import com.stardew.craft.api.v1.economy.StardewCurrencyCost;
import com.stardew.craft.api.v1.economy.StardewCurrencyHandler;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionPersistentData;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionEvents;
import com.stardew.craft.api.v1.festival.StardewFestivalActivities;
import com.stardew.craft.api.v1.festival.StardewFestivalActivityDecision;
import com.stardew.craft.api.v1.festival.StardewFestivalMechanicCapability;
import com.stardew.craft.api.v1.festival.StardewFestivalMechanicHandler;
import com.stardew.craft.api.v1.festival.StardewFestivalMechanics;
import com.stardew.craft.api.v1.festival.StardewFestivalRewardDescriptor;
import com.stardew.craft.api.v1.festival.StardewFestivalRewardPreparation;
import com.stardew.craft.api.v1.festival.StardewFestivalRewards;
import com.stardew.craft.api.v1.farm.StardewFarmInitializationSteps;
import com.stardew.craft.api.v1.farm.StardewFarmCaveDailyHandlers;
import com.stardew.craft.api.v1.farm.StardewFarmDailyTasks;
import com.stardew.craft.api.v1.farm.StardewFarmDebrisPlacements;
import com.stardew.craft.api.v1.farm.StardewFarmLifecycle;
import com.stardew.craft.api.v1.farm.StardewFarmLifecycleListener;
import com.stardew.craft.api.v1.farm.StardewFarmPersistentData;
import com.stardew.craft.api.v1.fishing.StardewFishingLocationDisplays;
import com.stardew.craft.api.v1.fishing.StardewFishingLocationKeys;
import com.stardew.craft.api.v1.fishing.StardewFishingRuleConditions;
import com.stardew.craft.api.v1.item.StardewItemData;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.api.v1.item.StardewAcquisitionSource;
import com.stardew.craft.api.v1.item.StardewAcquisitionSources;
import com.stardew.craft.api.v1.npc.StardewNpcEntities;
import com.stardew.craft.api.v1.npc.StardewNpcDisplay;
import com.stardew.craft.api.v1.npc.StardewNpcDefinition;
import com.stardew.craft.api.v1.npc.StardewNpcFriendshipRewards;
import com.stardew.craft.api.v1.npc.StardewNpcGifts;
import com.stardew.craft.api.v1.npc.StardewNpcInteractions;
import com.stardew.craft.api.v1.npc.StardewNpcLifecycleEvents;
import com.stardew.craft.api.v1.npc.StardewNpcProfile;
import com.stardew.craft.api.v1.npc.StardewNpcProfiles;
import com.stardew.craft.api.v1.npc.StardewNpcSocialRules;
import com.stardew.craft.api.v1.network.StardewNetworkCapabilities;
import com.stardew.craft.api.v1.network.StardewNetworkCapabilityRequirement;
import com.stardew.craft.api.v1.progress.StardewProgress;
import com.stardew.craft.api.v1.progress.StardewProgressEvents;
import com.stardew.craft.api.v1.progress.StardewProgressKey;
import com.stardew.craft.api.v1.progress.StardewProgressMetric;
import com.stardew.craft.api.v1.progress.StardewProgressOperation;
import com.stardew.craft.api.v1.progress.StardewProgressPhase;
import com.stardew.craft.api.v1.progress.StardewProgressProvider;
import com.stardew.craft.api.v1.progress.StardewProgressRequirements;
import com.stardew.craft.api.v1.progress.StardewProgressScope;
import com.stardew.craft.api.v1.progress.StardewProgressSnapshot;
import com.stardew.craft.api.v1.reward.StardewRewardComponent;
import com.stardew.craft.api.v1.reward.StardewRewardPreview;
import com.stardew.craft.api.v1.reward.StardewRewardPreviews;
import com.stardew.craft.api.v1.requirement.StardewRequirement;
import com.stardew.craft.api.v1.requirement.StardewRequirements;
import com.stardew.craft.api.v1.mining.StardewMineMonsterProfiles;
import com.stardew.craft.api.v1.machine.StardewArtisanResolvers;
import com.stardew.craft.api.v1.machine.StardewMachineRecipeDisplay;
import com.stardew.craft.api.v1.machine.StardewMachineRecipeDisplays;
import com.stardew.craft.api.v1.machine.StardewMachineType;
import com.stardew.craft.api.v1.machine.StardewMachineTypes;
import com.stardew.craft.api.v1.machine.StardewMachineCycleKind;
import com.stardew.craft.api.v1.machine.StardewMachineCycles;
import com.stardew.craft.api.v1.machine.StardewProductionPlan;
import com.stardew.craft.api.v1.machine.StardewProductionPlans;
import com.stardew.craft.api.v1.machine.StardewProductionEvents;
import com.stardew.craft.api.v1.tree.StardewTreeRuntimeAdapter;
import com.stardew.craft.api.v1.tree.StardewTreeState;
import com.stardew.craft.api.v1.tree.StardewTreeType;
import com.stardew.craft.api.v1.tree.StardewTreeTypes;
import com.stardew.craft.api.v1.world.StardewArtifactSpotDrops;
import com.stardew.craft.api.v1.world.StardewLocationEvents;
import com.stardew.craft.api.v1.world.StardewWorldEvents;
import com.stardew.craft.api.v1.profession.StardewProfessionEffectHandlers;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.quest.QuestObjectiveResult;
import com.stardew.craft.api.v1.quest.QuestObjectiveRuntime;
import com.stardew.craft.api.v1.quest.QuestProgressEvents;
import com.stardew.craft.api.v1.quest.StardewQuestObjectives;
import com.stardew.craft.api.v1.shop.StardewShopEntry;
import com.stardew.craft.api.v1.shop.StardewShopInventories;
import com.stardew.craft.api.v1.shop.StardewShopInventoryProviders;
import com.stardew.craft.api.v1.shop.StardewShopRowKey;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import com.stardew.craft.api.v1.specialorder.SpecialOrderProgressEvent;
import com.stardew.craft.api.v1.specialorder.StardewSpecialOrderObjectives;
import com.stardew.craft.api.v1.specialorder.StardewSpecialOrderRewards;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

@Mod(ExampleStardewAddon.MOD_ID)
public final class ExampleStardewAddon {
    public static final String MOD_ID = "example_stardew_addon";

    public ExampleStardewAddon() {
        StardewNetworkCapabilities.register(
                id("orchard_catalog_display"),
                1,
                StardewNetworkCapabilityRequirement.OPTIONAL);
        registerStackMetadataProvider();
        registerAcquisitionSource();
        registerContentProjection();
        registerCurrency();
        registerCondition();
        registerItemQuery();
        registerAction();
        registerQuestObjective();
        registerShopInventoryProvider();
        registerCutsceneTrigger();
        registerSpecialOrderTypes();
        registerProgressExamples();
        registerNpcInteractionProvider();
        registerNpcSocialExtensions();
        registerAgricultureProvider();
        registerCropRuntimeExample();
        registerAnimalLifecycleExamples();
        registerFarmLifecycleExamples();
        registerWorldAndFestivalExamples();
        registerWorldEventExample();
        registerMachineExamples();
        registerTreeExamples();
        registerCommunityCenterExamples();
        registerFishingExamples();
        registerArtifactSpotExample();
        registerEquipmentProvider();
        registerWeaponSkill();
        registerMineMonsterProvider();
        registerProfessionEffect();
    }

    private static void registerStackMetadataProvider() {
        StardewItemDataApi.registerProvider(id("charged_amethyst"), 100, stack -> {
            if (!stack.is(Items.AMETHYST_SHARD) || !stack.hasFoil()) {
                return Optional.empty();
            }
            return Optional.of(new StardewItemData(
                    id("charged_gem"), 500, -300, 0, 0, false));
        });
    }

    private static void registerAcquisitionSource() {
        StardewAcquisitionSources.register(
                id("charged_gem_source"),
                100,
                context -> context.targetItemId().equals(
                                BuiltInRegistries.ITEM.getKey(
                                        Items.AMETHYST_SHARD))
                        ? List.of(new StardewAcquisitionSource(
                                context.targetItemId(),
                                StardewAcquisitionSource.Kind.OTHER,
                                id("charged_gem_ritual"),
                                1,
                                Component.literal(
                                        "Charge an amethyst in the addon ritual"),
                                true))
                        : List.of());
    }

    private static void registerContentProjection() {
        ResourceLocation featureType = id("content_type/orchard_feature");
        ResourceLocation source = id("content/orchard_tasting");
        StardewContentKey canonical = new StardewContentKey(
                featureType, id("orchard_tasting"));
        StardewContents.register(source, 0, () -> List.of(
                new StardewContentDefinition(
                        canonical,
                        source,
                        List.of(
                                StardewContentReference.required(
                                        StardewContentReferenceRoles.LOCATION,
                                        new StardewContentKey(
                                                StardewContentTypes.LOCATION,
                                                ResourceLocation.fromNamespaceAndPath(
                                                        "stardewcraft",
                                                        "town"))),
                                StardewContentReference.required(
                                        StardewContentReferenceRoles.PRODUCT_ITEM,
                                        new StardewContentKey(
                                                StardewContentTypes.ITEM,
                                                ResourceLocation.fromNamespaceAndPath(
                                                        "minecraft",
                                                        "apple")))))));
        StardewContents.registerAliases(
                id("content/orchard_aliases"),
                0,
                () -> List.of(new StardewContentAlias(
                        new StardewContentKey(
                                featureType, id("legacy_orchard_tasting")),
                        canonical)));
    }

    private static void registerCurrency() {
        ResourceLocation orchardMarks = id("orchard_marks");
        StardewCurrencies.register(
                new StardewCurrency(
                        orchardMarks,
                        Component.literal("Orchard Marks"),
                        new ItemStack(Items.EXPERIENCE_BOTTLE),
                        21863L),
                new StardewCurrencyHandler() {
                    @Override
                    public long balance(
                            net.minecraft.server.level.ServerPlayer player
                    ) {
                        return player.experienceLevel;
                    }

                    @Override
                    public boolean withdraw(
                            net.minecraft.server.level.ServerPlayer player,
                            long amount
                    ) {
                        if (amount > player.experienceLevel) {
                            return false;
                        }
                        player.giveExperienceLevels(-(int) amount);
                        return true;
                    }

                    @Override
                    public boolean deposit(
                            net.minecraft.server.level.ServerPlayer player,
                            long amount
                    ) {
                        if (amount > 21863L - player.experienceLevel) {
                            return false;
                        }
                        player.giveExperienceLevels((int) amount);
                        return true;
                    }
                });
    }

    private static void registerCondition() {
        StardewConditions.register(id("player_named"), PlayerNamedCondition.CODEC,
                (context, data) -> context.player() != null
                        && context.player().getGameProfile().getName().equalsIgnoreCase(data.name()));
        StardewRequirements.register(
                id("player_named_requirement"),
                100,
                (context, condition, proposed) ->
                        condition.type().equals(id("player_named"))
                                ? new StardewRequirement(
                                        condition.type(),
                                        proposed.state(),
                                        Component.literal(
                                                "Player name matches the configured value"),
                                        true)
                                : null);
    }

    private static void registerItemQuery() {
        StardewItemQueries.register(id("apples"), AppleQuery.CODEC,
                (context, data) -> List.of(
                        new ItemStack(Items.APPLE, data.count())),
                (owner, data) -> List.of(
                        StardewContentReference.required(
                                StardewContentReferenceRoles.QUERY_ITEM,
                                new StardewContentKey(
                                        StardewContentTypes.ITEM,
                                        ResourceLocation.fromNamespaceAndPath(
                                                "minecraft", "apple")))));
    }

    private static void registerAction() {
        StardewActions.register(id("heal"), HealAction.CODEC, (context, data) -> {
            context.player().heal(data.health());
            return StardewActionResult.ok();
        });
        StardewActions.register(
                id("spend_orchard_marks"),
                Codec.intRange(1, 100),
                (context, amount) -> {
                    var payment = StardewCosts.pay(
                            context.player(),
                            StardewCost.of(new StardewCurrencyCost(
                                    id("orchard_marks"), amount)));
                    if (!payment.success()) {
                        return StardewActionResult.failure(
                                payment.failureReason());
                    }
                    context.player().heal(amount);
                    return StardewActionResult.ok();
                },
                (owner, amount) -> List.of(
                        StardewContentReference.required(
                                StardewContentReferenceRoles.CURRENCY,
                                new StardewContentKey(
                                        StardewContentTypes.CURRENCY,
                                        id("orchard_marks")))));
    }

    private static void registerQuestObjective() {
        StardewQuestObjectives.register(id("break_targets"), BreakTargetsObjective.CODEC,
                BreakTargetsRuntime::new);
    }

    private static void registerShopInventoryProvider() {
        StardewShopInventoryProviders.register(id("daily_apples"), context -> List.of(
                new StardewShopEntry(
                        "minecraft:apple", "", "", 75, 5,
                        Optional.empty(), 0, List.of(), 1, 0, Optional.empty(),
                        -1, 0, 1, List.of()),
                new StardewShopEntry(
                        "example_stardew_addon:orchard_blessing",
                        "Orchard Blessing",
                        "Restore two hearts without receiving an item.",
                        0, 1, Optional.empty(), 0, List.of(), 1, 0,
                        Optional.empty(), -1, 0, 1, List.of())));
    }

    private static void registerCutsceneTrigger() {
        StardewCutsceneTriggers.register(id("experience_level"), Codec.INT,
                (player, minimum) -> player.experienceLevel >= minimum);
    }

    private static void registerSpecialOrderTypes() {
        StardewSpecialOrderObjectives.register(id("slay_named"), Codec.STRING,
                (context, target, event) -> event.kind() == SpecialOrderProgressEvent.Kind.MONSTER_SLAIN
                        && target.equalsIgnoreCase(event.target()) ? event.amount() : 0);
        StardewSpecialOrderRewards.register(id("heal"), Codec.FLOAT,
                (context, health) -> context.player().heal(health));
    }

    private static void registerProgressExamples() {
        ResourceLocation domain = id("orchard_trial");
        ResourceLocation firstHarvest = id("first_harvest");
        StardewProgress.registerProvider(
                id("orchard_progress"),
                100,
                domain,
                new StardewProgressProvider() {
                    @Override
                    public StardewProgressSnapshot inspect(
                            net.minecraft.server.level.ServerPlayer player,
                            ResourceLocation entryId
                    ) {
                        return !entryId.equals(firstHarvest)
                                ? null
                                : new StardewProgressSnapshot(
                                        new StardewProgressKey(
                                                domain, entryId),
                                        StardewProgressScope.PLAYER,
                                        player.experienceLevel >= 5
                                                ? StardewProgressPhase.COMPLETED
                                                : StardewProgressPhase.ACTIVE,
                                        List.of(new StardewProgressMetric(
                                                id("experience_levels"),
                                                Math.min(
                                                        player.experienceLevel,
                                                        5),
                                                5)),
                                        true,
                                        false,
                                        OptionalInt.empty());
                    }

                    @Override
                    public java.util.Collection<ResourceLocation> entries(
                            net.minecraft.server.level.ServerPlayer player
                    ) {
                        return List.of(firstHarvest);
                    }
                });
        StardewRewardPreviews.register(
                id("orchard_reward_preview"),
                100,
                (player, progress, proposed) ->
                        progress.equals(new StardewProgressKey(
                                domain, firstHarvest))
                                ? new StardewRewardPreview(
                                        progress,
                                        List.of(new StardewRewardComponent(
                                                StardewRewardComponent.Kind.ITEM,
                                                BuiltInRegistries.ITEM.getKey(
                                                        Items.EXPERIENCE_BOTTLE),
                                                2,
                                                new ItemStack(
                                                        Items.EXPERIENCE_BOTTLE,
                                                        2),
                                                Component.literal(
                                                        "2 Experience Bottles"),
                                                false)),
                                        true)
                                : null);
        StardewProgressEvents.register(
                id("progress_observer"),
                100,
                event -> System.getLogger(
                                ExampleStardewAddon.class.getName())
                        .log(System.Logger.Level.DEBUG,
                                "Progress {0}: {1} -> {2}",
                                event.after().key(),
                                event.before()
                                        .map(snapshot -> snapshot.phase().name())
                                        .orElse("NONE"),
                                event.after().phase()));
    }

    private static void registerNpcInteractionProvider() {
        StardewNpcInteractions.register(id("lewis_apple_greeting"), 100, context -> {
            if (!context.npcId().equals(ResourceLocation.fromNamespaceAndPath("stardewcraft", "lewis"))
                    || !context.player().getItemInHand(context.hand()).is(Items.APPLE)) {
                return InteractionResult.PASS;
            }
            context.player().displayClientMessage(Component.literal("Lewis notices the addon apple."), false);
            return InteractionResult.SUCCESS;
        });
    }

    private static void registerNpcSocialExtensions() {
        ResourceLocation archivist = id("archivist");
        StardewNpcProfiles.register(
                id("archivist_display"),
                100,
                new StardewNpcDefinition(
                        archivist,
                        new StardewNpcProfile(
                                archivist, true, false, "idle_only",
                                0, 0, 0, 0, 0, false),
                        new StardewNpcDisplay(
                                archivist,
                                "entity.example_stardew_addon.npc.archivist",
                                id("textures/portraits/archivist.png"),
                                128,
                                320,
                                id("textures/mugshots/archivist.png"),
                                16,
                                24,
                                "example_stardew_addon.relationship.archivist",
                                false))
        );
        StardewNpcSocialRules.register(id("archivist_social_rules"), 100,
                (context, rule, proposed) -> {
                    if (!context.npcId().equals(archivist)) {
                        return StardewNpcSocialRules.Decision.PASS;
                    }
                    return switch (rule) {
                        case CAN_SOCIALIZE, CAN_RECEIVE_GIFTS,
                             SHOW_ON_SOCIAL_PAGE,
                             CREATE_FRIENDSHIP_FOR_SOCIAL_PAGE ->
                                StardewNpcSocialRules.Decision.ALLOW;
                        case INCLUDE_IN_INTRODUCTIONS ->
                                StardewNpcSocialRules.Decision.PASS;
                    };
                });
        StardewNpcLifecycleEvents.register(
                id("archivist_lifecycle"),
                100,
                event -> {
                    if (event.npcId().equals(archivist)) {
                        System.getLogger(ExampleStardewAddon.class.getName())
                                .log(System.Logger.Level.DEBUG,
                                        "Archivist lifecycle: {0} ({1})",
                                        event.phase(), event.reason());
                    }
                });
        StardewNpcGifts.registerConfirmationPolicy(
                id("archivist_gift_confirmation"),
                100,
                context -> context.npcId().equals(archivist)
                        && context.player().isShiftKeyDown()
                        ? StardewNpcGifts.Confirmation.GIVE_IMMEDIATELY
                        : StardewNpcGifts.Confirmation.PASS
        );
        StardewNpcGifts.registerBeforeHook(
                id("archivist_gift_guard"),
                100,
                context -> StardewNpcGifts.BeforeDecision.PASS
        );
        StardewNpcGifts.registerAfterHook(
                id("archivist_gift_observer"),
                100,
                result -> {
                    if (result.npcId().equals(archivist)
                            && result.status() == StardewNpcGifts.Status.ACCEPTED) {
                        result.player().displayClientMessage(
                                Component.literal("The archivist recorded that gift."),
                                false);
                    }
                }
        );
        StardewNpcFriendshipRewards.register(
                id("archivist_two_hearts"),
                100,
                context -> {
                    if (!context.npcId().equals(archivist)
                            || context.points() < 500) {
                        return StardewNpcFriendshipRewards.Outcome.PASS;
                    }
                    return context.player().addItem(new ItemStack(Items.EMERALD))
                            ? StardewNpcFriendshipRewards.Outcome.COMPLETE
                            : StardewNpcFriendshipRewards.Outcome.PASS;
                }
        );
        StardewNpcEntities.register(
                id("archivist_entity"),
                100,
                context -> {
                    if (!context.npcId().equals(archivist)) {
                        return null;
                    }
                    for (net.minecraft.world.entity.Entity entity
                            : context.level().getAllEntities()) {
                        if (entity.getTags().contains(
                                "example_stardew_addon_archivist")) {
                            return entity;
                        }
                    }
                    return null;
                }
        );
    }

    private static void registerAgricultureProvider() {
        StardewAgricultureDataApi.registerCropProvider(id("highland_parsnip"), 100, (level, pos, state) -> {
            if (pos.getY() <= 80 || !isBlock(state, "parsnip_crop")) return null;
            return new StardewCropData(
                    List.of("spring", "summer", "fall"), List.of(1, 1, 1, 1, 1, 1, 1),
                    -1, 12,
                    ResourceLocation.fromNamespaceAndPath("stardewcraft", "grab"),
                    ResourceLocation.fromNamespaceAndPath("stardewcraft", "parsnip"),
                    ResourceLocation.fromNamespaceAndPath("stardewcraft", "parsnip_seeds"));
        });
        StardewAgricultureDataApi.registerTreeProvider(id("highland_apple_tree"), 100, (level, pos, state) -> {
            if (pos.getY() <= 80 || !isBlock(state, "apple_tree")) return null;
            return new StardewTreeData(
                    id("highland_apple_tree"), 28, ResourceLocation.withDefaultNamespace("golden_apple"),
                    2, 4, List.of("spring", "summer", "fall", "winter"));
        });
        StardewAgricultureDataApi.registerAnimalProvider(id("highland_cow"), 100, entity -> {
            if (entity.getY() <= 80 || entity.getType() != EntityType.COW) return null;
            return new StardewAnimalData(
                    id("highland_barn"), 1500, 5,
                    ResourceLocation.withDefaultNamespace("honey_bottle"), 2);
        });
        StardewAgricultureDataApi.registerBuildingProvider(id("highland_manager"), 100, (level, pos, state) -> {
            if (pos.getY() <= 80
                    || (!isBlock(state, "coop_manager") && !isBlock(state, "barn_manager"))) return null;
            return new StardewBuildingData(
                    id("highland_barn"), 2, List.of(ResourceLocation.withDefaultNamespace("cow")), List.of());
        });
    }

    private static void registerCropRuntimeExample() {
        ResourceLocation cropId = id("moonberry");
        ResourceLocation markerBlockId = id("moonberry_crop");
        StardewCropTypes.register(
                new StardewCropType(
                        cropId,
                        "block.example_stardew_addon.moonberry_crop",
                        5,
                        List.of(markerBlockId),
                        new StardewCropData(
                                List.of("fall"),
                                List.of(1, 2, 2, 3),
                                4,
                                18,
                                ResourceLocation.fromNamespaceAndPath(
                                        "stardewcraft", "grab"),
                                id("moonberry"),
                                id("moonberry_seeds"))
                ),
                100,
                new StardewCropRuntimeAdapter() {
                    @Override
                    public StardewCropState inspect(
                            net.minecraft.world.level.LevelReader level,
                            BlockPos position
                    ) {
                        // A real addon reads its own BlockState or BlockEntity and resolves the
                        // logical root. No StardewCropBlock inheritance is required.
                        if (!BuiltInRegistries.BLOCK.getKey(
                                level.getBlockState(position).getBlock()).equals(markerBlockId)) {
                            return null;
                        }
                        return new StardewCropState(
                                cropId,
                                position,
                                StardewCropState.Part.ROOT,
                                0,
                                false,
                                List.of(position.below())
                        );
                    }

                    @Override
                    public DailyResult growOneDay(
                            net.minecraft.server.level.ServerLevel level,
                            StardewCropState crop,
                            StardewCropDailyContext context
                    ) {
                        // Read and persist the addon's phase here. Context supplies watering,
                        // season bypass, absolute day and whether this is offline catch-up.
                        // Return REMOVED after replacing an out-of-season crop.
                        return DailyResult.UNCHANGED;
                    }

                    @Override
                    public StardewCropHarvestResult harvest(
                            net.minecraft.server.level.ServerLevel level,
                            StardewCropState crop,
                            StardewCropHarvestContext context,
                            java.util.function.Consumer<ItemStack> output
                    ) {
                        // Validate the addon's mature phase/tool rule, mutate its saved state,
                        // then stage every primary/byproduct stack. The runtime only releases
                        // staged stacks when HARVESTED is returned.
                        return StardewCropHarvestResult.notReady();
                    }

                    @Override
                    public boolean remove(
                            net.minecraft.server.level.ServerLevel level,
                            StardewCropState crop,
                            StardewCropRemovalCause cause
                    ) {
                        // Remove every block/BE belonging to the logical crop. Do not emit produce.
                        return false;
                    }
                }
        );
    }

    private static void registerEquipmentProvider() {
        StardewEquipmentDataApi.registerProvider(id("enchanted_diamond_sword"), 100, stack -> {
            if (!stack.is(Items.DIAMOND_SWORD) || !stack.hasFoil()) return null;
            return new StardewEquipmentData(
                    ResourceLocation.fromNamespaceAndPath("stardewcraft", "weapon"),
                    0, 0, 3, 0.04F, 0.5F, 0, 0.0F, 0.0F, 0,
                    List.of(id("apple_guard")), Optional.of(new StardewEquipmentData.Weapon(
                    "sword", 22.0F, 30.0F, 0.04F, 0, 0, 0.0F, 1.0F,
                    Optional.of(id("apple_dash")), Optional.empty())));
        });
    }

    private static void registerAnimalLifecycleExamples() {
        String exampleAnimalType = MOD_ID + ":example_animal";
        StardewAnimalPersistentData.Key persistentKey =
                StardewAnimalPersistentData.register(id("example_animal_state"), 1);
        StardewAnimalDailyHandlers.register(
                id("example_animal_daily"),
                exampleAnimalType,
                100,
                context -> {
                    if (context.persistentData(persistentKey).isEmpty()) {
                        CompoundTag initialState = new CompoundTag();
                        initialState.putInt("days_observed", 1);
                        context.setPersistentData(persistentKey, initialState);
                    }
                    return StardewAnimalDailyHandlers.Result.PASS;
                }
        );
        StardewAnimalReproductionRules.register(
                id("example_animal_reproduction"),
                exampleAnimalType,
                100,
                context -> StardewAnimalReproductionRules.Decision.PASS
        );
        StardewTruffleFoundHandlers.register(
                id("example_animal_truffle_replacement"),
                100,
                context -> {
                    if (!exampleAnimalType.equals(context.animalTypeId())) {
                        return StardewTruffleFoundHandlers.Result.PASS;
                    }
                    ItemEntity replacement = new ItemEntity(
                            context.level(),
                            context.anchor().getX() + 0.5D,
                            context.anchor().getY() + 0.25D,
                            context.anchor().getZ() + 0.5D,
                            new ItemStack(Items.GOLDEN_APPLE));
                    return context.level().addFreshEntity(replacement)
                            ? StardewTruffleFoundHandlers.Result.REPLACE_TRUFFLE
                            : StardewTruffleFoundHandlers.Result.PASS;
                }
        );
        StardewAnimalQueryDefinitions.register(new StardewAnimalQueryDefinition(
                id("example_animal_query"),
                exampleAnimalType,
                1_000,
                1_300,
                true
        ));
        StardewAnimalPurchaseDisplays.register(new StardewAnimalPurchaseDisplay(
                id("example_animal_purchase_display"),
                exampleAnimalType,
                id("textures/gui/example_animal.png"),
                32,
                16
        ));
    }

    private static void registerFarmLifecycleExamples() {
        StardewFarmPersistentData.Key farmState =
                StardewFarmPersistentData.register(id("farm_state"), 1);
        StardewFarmLifecycle.register(id("farm_lifecycle"), 100,
                new StardewFarmLifecycleListener() {
                    @Override
                    public void afterCreate(StardewFarmLifecycle.FarmContext context) {
                        if (context.server() == null) {
                            return;
                        }
                        CompoundTag state = new CompoundTag();
                        state.putString("created_as", context.farm().farmTypeId().toString());
                        StardewFarmPersistentData.write(
                                context.server(), context.farm().ownerUuid(), farmState, state);
                    }
                });
        StardewFarmInitializationSteps.register(
                id("farm_content"),
                1,
                100,
                StardewFarmInitializationSteps.FailurePolicy.CONTINUE,
                context -> {
                    // Place addon-owned farm content here. Successful version 1 is persisted,
                    // while an exception leaves this step pending for the next login/retry.
                }
        );
        StardewFarmCaveDailyHandlers.registerFruitProvider(
                id("farm_cave_fruit"),
                100,
                List::<Block>of,
                context -> context.currentFruit()
        );
        StardewFarmDailyTasks.register(
                id("farm_daily"),
                100,
                context -> {
                    // Run addon maintenance once per active initialized farm.
                }
        );
        StardewFarmDebrisPlacements.register(
                id("farm_debris"),
                100,
                context -> context.proposedState()
        );
    }

    private static void registerWorldAndFestivalExamples() {
        ResourceLocation orchardFestival =
                id("orchard_celebration");
        ResourceLocation appleToss = id("apple_toss");
        StardewFestivalActivities.register(
                id("orchard_apple_toss"),
                100,
                id("orchard_celebration"),
                appleToss,
                context -> {
                    // A real addon can open its own minigame payload here.
                    return StardewFestivalActivityDecision.STARTED;
                });
        StardewProgress.registerProvider(
                id("orchard_apple_toss_progress"),
                100,
                StardewFestivalActivities.progressDomain(
                        orchardFestival),
                new StardewProgressProvider() {
                    @Override
                    public StardewProgressSnapshot inspect(
                            net.minecraft.server.level.ServerPlayer player,
                            ResourceLocation entryId
                    ) {
                        if (!entryId.equals(appleToss)) {
                            return null;
                        }
                        int score = Math.min(
                                player.experienceLevel, 10);
                        return new StardewProgressSnapshot(
                                StardewFestivalActivities.progressKey(
                                        orchardFestival, appleToss),
                                StardewProgressScope.PLAYER,
                                score >= 10
                                        ? StardewProgressPhase.COMPLETED
                                        : StardewProgressPhase.ACTIVE,
                                List.of(new StardewProgressMetric(
                                        id("apples_tossed"),
                                        score,
                                        10)),
                                true,
                                false,
                                OptionalInt.empty());
                    }

                    @Override
                    public java.util.Collection<ResourceLocation> entries(
                            net.minecraft.server.level.ServerPlayer player
                    ) {
                        return List.of(appleToss);
                    }
                });
        StardewFestivalMechanics.register(
                id("orchard_festival_rewards"),
                100,
                id("orchard_celebration"),
                Set.of(
                        StardewFestivalMechanicCapability.REWARDS,
                        StardewFestivalMechanicCapability
                                .PERSISTENT_STATE),
                new StardewFestivalMechanicHandler() {
                    @Override
                    public void onSessionChanged(
                            com.stardew.craft.api.v1.festival
                                    .StardewFestivalMechanicContext context,
                            com.stardew.craft.api.v1.festival
                                    .StardewFestivalSessionEvent event
                    ) {
                        // This layer only receives sessions whose mechanic_id
                        // is example_stardew_addon:orchard_celebration.
                    }
                });
        StardewFestivalRewards.register(
                id("orchard_festival_prize"),
                100,
                context -> context.festivalId().equals(
                                id("orchard_celebration"))
                        && context.rewardId().equals(
                                id("orchard_festival_prize"))
                        ? StardewFestivalRewardPreparation.accept(
                                grantContext -> grantContext.player()
                                        .getInventory().add(
                                                new ItemStack(Items.APPLE)))
                        : StardewFestivalRewardPreparation.pass());
        StardewFestivalRewards.registerDescriptor(
                id("orchard_festival_prize_display"),
                100,
                new StardewFestivalRewardDescriptor(
                        orchardFestival,
                        id("orchard_festival_prize"),
                        List.of(new StardewRewardComponent(
                                StardewRewardComponent.Kind.ITEM,
                                BuiltInRegistries.ITEM.getKey(Items.APPLE),
                                1,
                                new ItemStack(Items.APPLE),
                                Items.APPLE.getDescription(),
                                false)),
                        true));
        // A real mechanic can use this key with
        // StardewFestivalSessionPersistentData.read/write during its session.
        StardewFestivalSessionPersistentData.register(
                id("orchard_festival_state"), 1);
        StardewFestivalSessionEvents.register(
                id("orchard_festival_lifecycle"),
                100,
                event -> {
                    // Observe all active/passive session phase, map and
                    // participant changes without depending on one festival.
                });
        StardewLocationEvents.register(
                id("orchard_location_observer"),
                100,
                transition -> {
                    // Observe transition.enteredLocation()/leftLocation().
                    // State-changing mechanics still belong in server APIs.
        });
    }

    private static void registerWorldEventExample() {
        StardewWorldEvents.register(
                id("orchard_blossom"),
                context -> {
                    BlockPos target = context.origin();
                    CompoundTag state = new CompoundTag();
                    state.putString("display_key",
                            "example_stardew_addon.world_event"
                                    + ".orchard_blossom");
                    return new StardewWorldEvents.Plan(
                            List.of(new StardewWorldEvents.BlockChange(
                                    target,
                                    context.level()
                                            .getBlockState(target),
                                    Blocks.PINK_WOOL
                                            .defaultBlockState())),
                            state);
                });
    }

    private static void registerMachineExamples() {
        StardewArtisanResolvers.registerCaskAgingRate(
                id("cask_rate"),
                100,
                input -> input.is(Items.HONEY_BOTTLE) ? 1.0F : null
        );
        StardewArtisanResolvers.registerSmokedOutput(
                id("smoked_output"),
                100,
                input -> input.is(Items.COD)
                        ? new ItemStack(Items.COOKED_COD)
                        : ItemStack.EMPTY
        );
        StardewArtisanResolvers.registerSeedMakerOutput(
                id("seed_output"),
                100,
                item -> item == Items.APPLE
                        ? StardewArtisanResolvers.SeedResult.output(Items.MELON_SEEDS)
                        : StardewArtisanResolvers.SeedResult.pass()
        );
        StardewProductionPlans.register(
                id("faster_apple_preserves"),
                100,
                (context, proposed) -> {
                    if (!context.machineId().equals(
                                    ResourceLocation.fromNamespaceAndPath(
                                            "stardewcraft",
                                            "preserves_jar"))
                            || !context.input().is(Items.APPLE)) {
                        return Optional.of(proposed);
                    }
                    return Optional.of(new StardewProductionPlan(
                            proposed.output(),
                            Math.max(1, proposed.minutes() / 2)));
                });
        StardewProductionEvents.register(
                id("production_observer"),
                100,
                event -> {
                    // Feed addon quests/statistics from STARTED, READY or COLLECTED.
                });
        StardewMachineCycles.registerPlan(
                id("faster_diamond_repeater"),
                100,
                (context, proposed) -> {
                    if (context.kind()
                                    != StardewMachineCycleKind.REPEATING
                            || !context.input().is(Items.DIAMOND)) {
                        return Optional.of(proposed);
                    }
                    return Optional.of(new StardewProductionPlan(
                            proposed.output(),
                            Math.max(1, proposed.minutes() / 2)));
                });
        StardewMachineCycles.registerListener(
                id("all_machine_cycle_observer"),
                100,
                event -> {
                    // Observes BATCH, REPEATING, PASSIVE and ENVIRONMENTAL cycles.
                });

        ResourceLocation machineId = id("example_fermenter");
        StardewMachineTypes.register(
                id("example_fermenter_registration"),
                new StardewMachineType(
                        machineId,
                        ResourceLocation.withDefaultNamespace("barrel"),
                        "jei.example_stardew_addon.example_fermenter",
                        StardewMachineType.Layout.AUXILIARY_INPUT,
                        true,
                        List.of(new StardewMachineType.AuxiliaryInput(
                                ResourceLocation.withDefaultNamespace("sugar"), 1))
                )
        );
        StardewMachineRecipeDisplays.register(
                id("example_fermenter_displays"),
                100,
                requestedMachine -> requestedMachine.equals(machineId)
                        ? List.of(new StardewMachineRecipeDisplay(
                                id("example_fermenter/apple"),
                                machineId,
                                List.of(
                                        new StardewMachineRecipeDisplay.Input(
                                                List.of(new ItemStack(Items.APPLE)), 1, false),
                                        new StardewMachineRecipeDisplay.Input(
                                                List.of(new ItemStack(Items.SUGAR)), 1, true)
                                ),
                                List.of(new StardewMachineRecipeDisplay.Output(
                                        List.of(new ItemStack(Items.HONEY_BOTTLE)),
                                        1, 1, 1.0D)),
                                120,
                                false,
                                -1
                        ))
                        : List.of()
        );
    }

    private static void registerTreeExamples() {
        ResourceLocation treeId = id("example_moon_tree");
        ResourceLocation markerBlockId = id("example_moon_tree_marker");
        StardewTreeTypes.register(
                new StardewTreeType(
                        treeId,
                        StardewTreeType.Kind.WILD,
                        "block.example_stardew_addon.example_moon_tree",
                        21,
                        6,
                        true
                ),
                100,
                new StardewTreeRuntimeAdapter() {
                    @Override
                    public StardewTreeState inspect(
                            net.minecraft.world.level.LevelReader level,
                            BlockPos position
                    ) {
                        // A real addon would register markerBlockId as one of its own tree blocks.
                        if (!BuiltInRegistries.BLOCK.getKey(
                                level.getBlockState(position).getBlock()).equals(markerBlockId)) {
                            return null;
                        }
                        return new StardewTreeState(
                                treeId,
                                position,
                                StardewTreeState.Part.TRUNK,
                                5,
                                true,
                                List.of(position)
                        );
                    }

                    @Override
                    public boolean growOneDay(
                            net.minecraft.server.level.ServerLevel level,
                            StardewTreeState tree
                    ) {
                        // Advance addon-owned saved growth state here.
                        return true;
                    }

                    @Override
                    public FertilizerResult fertilize(
                            net.minecraft.server.level.ServerLevel level,
                            StardewTreeState tree
                    ) {
                        return tree.mature()
                                ? FertilizerResult.MATURE
                                : FertilizerResult.APPLIED;
                    }

                    @Override
                    public TapperCycle resolveTapperCycle(
                            net.minecraft.server.level.ServerLevel level,
                            StardewTreeState tree,
                            BlockPos supportPosition
                    ) {
                        return new TapperCycle(new ItemStack(Items.HONEY_BOTTLE), 4);
                    }
                }
        );
    }

    private static void registerCommunityCenterExamples() {
        StardewCommunityCenterPersistentData.Key difficulty =
                StardewCommunityCenterPersistentData.register(
                        id("community_center_difficulty"), 1);
        StardewCommunityCenterVariants.register(
                id("community_center_variant"),
                100,
                (context, current) -> StardewCommunityCenterPersistentData.read(
                                context.server().overworld(),
                                context.playerId(),
                                difficulty)
                        .filter(value -> value.payload().getBoolean("hard"))
                        .map(value -> current.withVariant(
                                id("hard_bundles"), current.definitions()))
                        .orElse(null)
        );
        StardewCommunityCenterRewards.register(
                id("community_center_reward"),
                100,
                context -> context.rewardDescriptor().equals("example:apple_reward")
                        ? new ItemStack(Items.GOLDEN_APPLE)
                        : null
        );
    }

    private static void registerFishingExamples() {
        String festivalPool = MOD_ID + ":moonlight_pool";
        StardewFishingLocationKeys.register(
                id("moonlight_pool_keys"),
                100,
                context -> {
                    if (context.position() == null
                            || context.position().getY() <= 80) {
                        return null;
                    }
                    List<String> keys =
                            new java.util.ArrayList<>(context.proposedKeys());
                    keys.add(festivalPool);
                    return keys;
                }
        );
        StardewFishingRuleConditions.register(
                id("moonlight_pool_condition"),
                100,
                (context, proposed) ->
                        context.rule().locations().contains(festivalPool)
                                && !context.player().isShiftKeyDown()
                                ? StardewFishingRuleConditions.Decision.DENY
                                : StardewFishingRuleConditions.Decision.PASS
        );
        StardewFishingLocationDisplays.register(
                id("moonlight_pool_display"),
                100,
                raw -> raw.equals(festivalPool)
                        ? Component.literal("Addon Moonlight Pool")
                        : null
        );
    }

    private static void registerArtifactSpotExample() {
        StardewArtifactSpotDrops.register(
                id("highland_artifact_spots"),
                100,
                context -> context.position().getY() > 120
                                && context.random().nextFloat() < 0.05F
                        ? List.of(new ItemStack(Items.EMERALD))
                        : null
        );
    }

    private static void registerWeaponSkill() {
        StardewWeaponSkillHandlers.register(id("apple_dash"), context -> {
            context.player().push(context.player().getLookAngle().x * 0.8, 0.1,
                    context.player().getLookAngle().z * 0.8);
            return InteractionResultHolder.success(context.weapon());
        });
    }

    private static void registerMineMonsterProvider() {
        ResourceLocation profileId = id("orchard_silverfish");
        StardewMineMonsterProfiles.register(
                profileId,
                EntityType.SILVERFISH,
                Set.of("example_stardew_addon:orchard_silverfish"),
                (mob, context) -> {
                });
    }

    private static void registerProfessionEffect() {
        StardewProfessionEffectHandlers.register(id("apple_tiller"), context -> {
            if (StardewProfessionEffectHandlers.SELL_PRICE_MULTIPLIER.equals(context.operation())
                    && context.stack().is(Items.APPLE)) {
                return context.value() * 1.02;
            }
            return context.value();
        });
    }

    /**
     * Example menu/diagnostic preflight. The actual purchase must still use
     * StardewCraft's server-authoritative shop transaction.
     */
    public static StardewRequirementReport orchardAppleStock(
            net.minecraft.server.level.ServerPlayer player,
            int quantity
    ) {
        return StardewShopInventories.requirements(
                player,
                new StardewShopRowKey(
                        "example_stardew_addon:orchard_stand",
                        "minecraft:apple"),
                quantity);
    }

    /**
     * Candidate rows are suitable for locked-item menus: unavailable entries
     * stay visible with server-authored requirement explanations.
     */
    public static java.util.List<com.stardew.craft.api.v1.shop.StardewShopInventorySnapshot>
            orchardCatalog(
                    net.minecraft.server.level.ServerPlayer player
            ) {
        return StardewShopInventories.candidates(
                player,
                "example_stardew_addon:orchard_stand");
    }

    /**
     * Addon domains get conservative phase-based operation explanations. The
     * addon must still execute and revalidate its own state-changing action.
     */
    public static StardewRequirementReport orchardTrialAcceptance(
            net.minecraft.server.level.ServerPlayer player
    ) {
        return StardewProgressRequirements.requirements(
                player,
                new StardewProgressKey(
                        id("orchard_trial"),
                        id("first_harvest")),
                StardewProgressOperation.ACCEPT);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static boolean isBlock(net.minecraft.world.level.block.state.BlockState state, String path) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock())
                .equals(ResourceLocation.fromNamespaceAndPath("stardewcraft", path));
    }

    private record PlayerNamedCondition(String name) {
        private static final Codec<PlayerNamedCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(PlayerNamedCondition::name)
        ).apply(instance, PlayerNamedCondition::new));
    }

    private record AppleQuery(int count) {
        private static final Codec<AppleQuery> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 999).optionalFieldOf("count", 1).forGetter(AppleQuery::count)
        ).apply(instance, AppleQuery::new));
    }

    private record HealAction(float health) {
        private static final Codec<HealAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.floatRange(0.0F, 1024.0F).fieldOf("health").forGetter(HealAction::health)
        ).apply(instance, HealAction::new));
    }

    private record BreakTargetsObjective(String target, int count) {
        private static final Codec<BreakTargetsObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("target").forGetter(BreakTargetsObjective::target),
                Codec.intRange(1, 999).fieldOf("count").forGetter(BreakTargetsObjective::count)
        ).apply(instance, BreakTargetsObjective::new));
    }

    private static final class BreakTargetsRuntime implements QuestObjectiveRuntime {
        private final BreakTargetsObjective definition;
        private int progress;

        private BreakTargetsRuntime(BreakTargetsObjective definition) {
            this.definition = definition;
        }

        @Override
        public QuestObjectiveResult onProgress(
                com.stardew.craft.api.v1.quest.QuestObjectiveContext context,
                com.stardew.craft.api.v1.quest.QuestProgressEvent event
        ) {
            if (!QuestProgressEvents.MONSTER_SLAIN.equals(event.type())
                    || !definition.target().equals(event.subject())) {
                return QuestObjectiveResult.NONE;
            }
            progress = Math.min(definition.count(), progress + Math.max(1, event.amount()));
            return QuestObjectiveResult.progress(progress >= definition.count());
        }

        @Override
        public CompoundTag saveState() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Progress", progress);
            return tag;
        }

        @Override
        public void loadState(CompoundTag state) {
            progress = Math.min(definition.count(), Math.max(0, state.getInt("Progress")));
        }

        @Override
        public List<Component> objectiveComponents(Component fallback) {
            return List.of(Component.literal(
                    "Defeat " + definition.target() + ": " + progress + "/" + definition.count()));
        }

        @Override
        public int currentCount() {
            return progress;
        }

        @Override
        public int targetCount() {
            return definition.count();
        }
    }
}
