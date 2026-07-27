package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.equipment.StardewEquipmentData;
import com.stardew.craft.api.v1.equipment.StardewEquipmentDataApi;
import com.stardew.craft.api.v1.economy.StardewCost;
import com.stardew.craft.api.v1.economy.StardewCosts;
import com.stardew.craft.api.v1.economy.StardewCurrencies;
import com.stardew.craft.api.v1.economy.StardewCurrencyCost;
import com.stardew.craft.api.v1.economy.StardewItemCost;
import com.stardew.craft.api.v1.communitycenter.StardewCommunityCenterProgress;
import com.stardew.craft.api.v1.communitycenter.StardewBundleDefinition;
import com.stardew.craft.api.v1.communitycenter.StardewCommunityCenterPersistentData;
import com.stardew.craft.api.v1.communitycenter.StardewCommunityCenterRewards;
import com.stardew.craft.api.v1.communitycenter.StardewCommunityCenterVariants;
import com.stardew.craft.api.v1.combat.StardewCombatDamageContext;
import com.stardew.craft.api.v1.combat.StardewCombatDamageDecision;
import com.stardew.craft.api.v1.combat.StardewCombatEvents;
import com.stardew.craft.api.v1.combat.StardewCombatKillContext;
import com.stardew.craft.api.v1.content.StardewContentDefinition;
import com.stardew.craft.api.v1.content.StardewContentAlias;
import com.stardew.craft.api.v1.content.StardewContentKey;
import com.stardew.craft.api.v1.content.StardewContentReference;
import com.stardew.craft.api.v1.content.StardewContentReferenceRoles;
import com.stardew.craft.api.v1.content.StardewContentTypes;
import com.stardew.craft.api.v1.content.StardewContents;
import com.stardew.craft.api.v1.agriculture.StardewAnimalPersistentData;
import com.stardew.craft.api.v1.agriculture.StardewAnimalTypes;
import com.stardew.craft.api.v1.agriculture.StardewCropDailyContext;
import com.stardew.craft.api.v1.agriculture.StardewCropHarvestContext;
import com.stardew.craft.api.v1.agriculture.StardewCropHarvestResult;
import com.stardew.craft.api.v1.agriculture.StardewCropRemovalCause;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntime;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntimeAdapter;
import com.stardew.craft.api.v1.agriculture.StardewCropState;
import com.stardew.craft.api.v1.agriculture.StardewCropType;
import com.stardew.craft.api.v1.agriculture.StardewCropTypes;
import com.stardew.craft.api.v1.farm.StardewFarmInitializationSteps;
import com.stardew.craft.api.v1.farm.StardewFarmLayout;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutConfigField;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutMigrations;
import com.stardew.craft.api.v1.farm.StardewFarmLayouts;
import com.stardew.craft.api.v1.farm.StardewFarms;
import com.stardew.craft.api.v1.farm.StardewFarmCaveDailyHandlers;
import com.stardew.craft.api.v1.farm.StardewFarmDailyTasks;
import com.stardew.craft.api.v1.farm.StardewFarmDebrisPlacements;
import com.stardew.craft.api.v1.farm.StardewFarmPersistentData;
import com.stardew.craft.api.v1.fishing.StardewFishingLocationDisplays;
import com.stardew.craft.api.v1.fishing.StardewFishingLocationKeys;
import com.stardew.craft.api.v1.fishing.StardewFishingRuleConditions;
import com.stardew.craft.api.v1.fishpond.StardewFishPondDailyContext;
import com.stardew.craft.api.v1.fishpond.StardewFishPondEvents;
import com.stardew.craft.api.v1.fishpond.StardewFishPondSnapshot;
import com.stardew.craft.api.v1.fishpond.StardewFishPonds;
import com.stardew.craft.api.v1.festival.StardewFestivalMechanicCapability;
import com.stardew.craft.api.v1.festival.StardewFestivalMechanicHandler;
import com.stardew.craft.api.v1.festival.StardewFestivalMechanics;
import com.stardew.craft.api.v1.festival.StardewFestivalActivities;
import com.stardew.craft.api.v1.festival.StardewFestivalActivityDecision;
import com.stardew.craft.api.v1.festival.StardewFestivalActivityResult;
import com.stardew.craft.api.v1.festival.StardewFestivalShopOpenResult;
import com.stardew.craft.api.v1.festival.StardewFestivalShops;
import com.stardew.craft.api.v1.festival.StardewFestivalRewardClaimResult;
import com.stardew.craft.api.v1.festival.StardewFestivalRewardDescriptor;
import com.stardew.craft.api.v1.festival.StardewFestivalRewardPreparation;
import com.stardew.craft.api.v1.festival.StardewFestivalRewards;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionEvents;
import com.stardew.craft.api.v1.internal.fishing.StardewFishingLocationDisplayRegistry;
import com.stardew.craft.api.v1.internal.fishing.StardewFishingRuleConditionRegistry;
import com.stardew.craft.api.v1.internal.combat.StardewCombatRegistry;
import com.stardew.craft.api.v1.internal.fishpond.StardewFishPondEventRegistry;
import com.stardew.craft.api.v1.internal.progress.StardewProgressRegistry;
import com.stardew.craft.api.v1.internal.farm.StardewFarmCaveDailyRegistry;
import com.stardew.craft.api.v1.internal.farm.StardewFarmDailyTaskRegistry;
import com.stardew.craft.api.v1.internal.farm.StardewFarmDebrisPlacementRegistry;
import com.stardew.craft.api.v1.internal.farm.StardewFarmSnapshots;
import com.stardew.craft.api.v1.machine.StardewArtisanResolvers;
import com.stardew.craft.api.v1.machine.StardewMachineType;
import com.stardew.craft.api.v1.machine.StardewMachineTypes;
import com.stardew.craft.api.v1.machine.StardewMachineCycleContext;
import com.stardew.craft.api.v1.machine.StardewMachineCycleEvent;
import com.stardew.craft.api.v1.machine.StardewMachineCycleKind;
import com.stardew.craft.api.v1.machine.StardewMachineCycles;
import com.stardew.craft.api.v1.machine.StardewProductionContext;
import com.stardew.craft.api.v1.machine.StardewProductionEvent;
import com.stardew.craft.api.v1.machine.StardewProductionEvents;
import com.stardew.craft.api.v1.machine.StardewProductionPhase;
import com.stardew.craft.api.v1.machine.StardewProductionPlan;
import com.stardew.craft.api.v1.machine.StardewProductionPlans;
import com.stardew.craft.api.v1.mining.StardewMineMonsterProfiles;
import com.stardew.craft.api.v1.item.StardewAcquisitionSource;
import com.stardew.craft.api.v1.item.StardewAcquisitionSources;
import com.stardew.craft.api.v1.internal.machine.StardewProductionEventRegistry;
import com.stardew.craft.api.v1.npc.StardewNpcDefinition;
import com.stardew.craft.api.v1.npc.StardewNpcDisplay;
import com.stardew.craft.api.v1.npc.StardewNpcEntities;
import com.stardew.craft.api.v1.npc.StardewNpcFriendshipRewards;
import com.stardew.craft.api.v1.npc.StardewNpcContents;
import com.stardew.craft.api.v1.npc.StardewNpcLifecycleEvents;
import com.stardew.craft.api.v1.npc.StardewNpcLifecyclePhase;
import com.stardew.craft.api.v1.npc.StardewNpcProfile;
import com.stardew.craft.api.v1.npc.StardewNpcProfiles;
import com.stardew.craft.api.v1.npc.StardewNpcSocialContext;
import com.stardew.craft.api.v1.npc.StardewNpcSocialRules;
import com.stardew.craft.api.v1.progress.StardewProgress;
import com.stardew.craft.api.v1.progress.StardewProgressDomains;
import com.stardew.craft.api.v1.progress.StardewProgressEvent;
import com.stardew.craft.api.v1.progress.StardewProgressEventType;
import com.stardew.craft.api.v1.progress.StardewProgressEvents;
import com.stardew.craft.api.v1.progress.StardewProgressKey;
import com.stardew.craft.api.v1.progress.StardewProgressOperation;
import com.stardew.craft.api.v1.progress.StardewProgressPhase;
import com.stardew.craft.api.v1.progress.StardewProgressProvider;
import com.stardew.craft.api.v1.progress.StardewProgressRequirements;
import com.stardew.craft.api.v1.progress.StardewProgressScope;
import com.stardew.craft.api.v1.progress.StardewProgressSnapshot;
import com.stardew.craft.api.v1.reward.StardewRewardComponent;
import com.stardew.craft.api.v1.reward.StardewRewardPreviews;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.requirement.StardewRequirement;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import com.stardew.craft.api.v1.requirement.StardewRequirementTypes;
import com.stardew.craft.api.v1.requirement.StardewRequirements;
import com.stardew.craft.api.v1.shop.StardewShopCosts;
import com.stardew.craft.api.v1.shop.StardewShopInventories;
import com.stardew.craft.api.v1.shop.StardewShopRowKey;
import com.stardew.craft.api.v1.shop.StardewShopProductContext;
import com.stardew.craft.api.v1.shop.StardewShopProductDecision;
import com.stardew.craft.api.v1.shop.StardewShopProductPreparation;
import com.stardew.craft.api.v1.shop.StardewShopProducts;
import com.stardew.craft.api.v1.internal.shop.StardewShopProductRegistry;
import com.stardew.craft.api.v1.internal.npc.StardewNpcSocialRuleRegistry;
import com.stardew.craft.api.v1.tree.StardewTreeRuntime;
import com.stardew.craft.api.v1.tree.StardewTreeRuntimeAdapter;
import com.stardew.craft.api.v1.tree.StardewTreeState;
import com.stardew.craft.api.v1.tree.StardewTreeType;
import com.stardew.craft.api.v1.tree.StardewTreeTypes;
import com.stardew.craft.api.v1.world.StardewArtifactSpotDrops;
import com.stardew.craft.api.v1.world.StardewLocationEvents;
import com.stardew.craft.api.v1.world.StardewLocationTransition;
import com.stardew.craft.api.v1.world.StardewLocations;
import com.stardew.craft.api.v1.world.StardewRegions;
import com.stardew.craft.api.v1.world.StardewWorldEvents;
import com.stardew.craft.api.v1.internal.world.StardewLocationTransitionRegistry;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalAcquisitionSource;
import com.stardew.craft.animal.model.AnimalBuildingType;
import com.stardew.craft.combat.equipment.EquipmentSlotResolver;
import com.stardew.craft.communitycenter.data.BundleDataManager;
import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import com.stardew.craft.blockentity.CaskBlockEntity;
import com.stardew.craft.blockentity.BaitMakerBlockEntity;
import com.stardew.craft.blockentity.TapperBlockEntity;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.TapperBlock;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.fishing.data.FishingDataManager;
import com.stardew.craft.fishing.data.SpawnFishRule;
import com.stardew.craft.fishpond.model.FishPondRecord;
import com.stardew.craft.fishpond.service.FishPondInteractionService;
import com.stardew.craft.festival.FestivalMapOverlayPhase;
import com.stardew.craft.festival.FestivalSessionPhase;
import com.stardew.craft.festival.FestivalSessionState;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.FestivalWorldData;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.mail.MailService;
import com.stardew.craft.quest.QuestManager;
import com.stardew.craft.specialorder.SpecialOrderDefinitions;
import com.stardew.craft.specialorder.SpecialOrderDataLoader;
import com.stardew.craft.specialorder.SpecialOrderInstance;
import com.stardew.craft.shop.ShopCostService;
import com.stardew.craft.shop.ShopRegistry;
import com.stardew.craft.shop.ShopStockTracker;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopStockTracker;
import com.stardew.craft.item.artisan.SeedMakerOutputResolver;
import com.stardew.craft.item.artisan.SmokedOutputResolver;
import com.stardew.craft.integration.jei.MachineJeiRegistry;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.interior.InteriorRegionRegistry;
import com.stardew.craft.manager.ArtifactDropService;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.tree.fruit.FruitTreeRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Runtime acceptance tests for contracts which require live registries. */
@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ApiContractGameTests {
    private static final ResourceLocation TEST_ANIMAL_TYPE_REGISTRATION =
            ResourceLocation.fromNamespaceAndPath("stardewcraft_gametest", "managed_goose_type");
    private static final String TEST_ANIMAL_TYPE = "stardewcraft_gametest:managed_goose";
    private static final StardewAnimalPersistentData.Key TEST_ANIMAL_DATA =
            registerAnimalPersistentDataKey();
    private static final AtomicBoolean CONTENT_PROVIDER_REGISTERED =
            new AtomicBoolean();

    private ApiContractGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void worldEventsCommitPersistAndCleanAtomically(
            GameTestHelper helper
    ) {
        ResourceLocation eventType =
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "orchard_blossom_" + UUID.randomUUID());
        UUID instanceId = UUID.randomUUID();
        BlockPos first = helper.absolutePos(
                new BlockPos(1, 1, 1));
        BlockPos second = helper.absolutePos(
                new BlockPos(2, 1, 1));
        helper.getLevel().setBlock(
                first, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(
                second, Blocks.DIRT.defaultBlockState(), 3);
        AtomicInteger preparations = new AtomicInteger();
        StardewWorldEvents.register(
                eventType,
                context -> {
                    preparations.incrementAndGet();
                    CompoundTag state = new CompoundTag();
                    state.putString("Sample", "orchard");
                    return new StardewWorldEvents.Plan(
                            List.of(
                                    new StardewWorldEvents.BlockChange(
                                            first,
                                            Blocks.STONE
                                                    .defaultBlockState(),
                                            Blocks.PINK_WOOL
                                                    .defaultBlockState()),
                                    new StardewWorldEvents.BlockChange(
                                            second,
                                            Blocks.DIRT
                                                    .defaultBlockState(),
                                            Blocks.MOSS_BLOCK
                                                    .defaultBlockState())),
                            state);
                });
        StardewWorldEvents.Context context =
                new StardewWorldEvents.Context(
                        eventType,
                        instanceId,
                        helper.getLevel(),
                        first,
                        42L,
                        new CompoundTag());

        StardewWorldEvents.Result committed =
                StardewWorldEvents.start(context);
        helper.assertValueEqual(
                committed.status(),
                StardewWorldEvents.Status.COMMITTED,
                "world event did not commit");
        helper.assertTrue(
                helper.getLevel().getBlockState(first)
                        .is(Blocks.PINK_WOOL),
                "first world-event block was not committed");
        helper.assertTrue(
                helper.getLevel().getBlockState(second)
                        .is(Blocks.MOSS_BLOCK),
                "second world-event block was not committed");
        helper.assertValueEqual(
                StardewWorldEvents.persistentData(
                                helper.getLevel().getServer(),
                                instanceId,
                                eventType)
                        .orElseThrow().getString("Sample"),
                "orchard",
                "world-event owner state was not persisted");

        StardewWorldEvents.Result replay =
                StardewWorldEvents.start(context);
        helper.assertValueEqual(
                replay.status(),
                StardewWorldEvents.Status.ALREADY_ACTIVE,
                "replayed world event was not idempotent");
        helper.assertValueEqual(
                preparations.get(),
                1,
                "idempotent replay invoked the handler again");

        helper.getLevel().setBlock(
                second,
                Blocks.EMERALD_BLOCK.defaultBlockState(),
                3);
        StardewWorldEvents.Result conflicted =
                StardewWorldEvents.cleanup(
                        helper.getLevel().getServer(), instanceId);
        helper.assertValueEqual(
                conflicted.status(),
                StardewWorldEvents.Status.CONFLICT,
                "cleanup ignored an external block change");
        helper.assertTrue(
                helper.getLevel().getBlockState(first)
                        .is(Blocks.PINK_WOOL),
                "conflicted cleanup partially restored the plan");

        helper.getLevel().setBlock(
                second, Blocks.MOSS_BLOCK.defaultBlockState(), 3);
        StardewWorldEvents.Result cleaned =
                StardewWorldEvents.cleanup(
                        helper.getLevel().getServer(), instanceId);
        helper.assertValueEqual(
                cleaned.status(),
                StardewWorldEvents.Status.CLEANED,
                "world event did not clean up");
        helper.assertTrue(
                helper.getLevel().getBlockState(first)
                        .is(Blocks.STONE),
                "cleanup did not restore the first block");
        helper.assertTrue(
                helper.getLevel().getBlockState(second)
                        .is(Blocks.DIRT),
                "cleanup did not restore the second block");
        helper.assertValueEqual(
                StardewWorldEvents.cleanup(
                                helper.getLevel().getServer(),
                                instanceId)
                        .status(),
                StardewWorldEvents.Status.NOT_ACTIVE,
                "cleaned event remained active");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void combatExtensionsComposeAndIsolateFailures(
            GameTestHelper helper
    ) {
        String suffix = UUID.randomUUID().toString();
        ServerPlayer player = FakePlayerFactory.get(
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "Combat API"));
        List<String> observedKills = new ArrayList<>();

        StardewCombatEvents.registerDamageModifier(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "damage_throwing_" + suffix),
                300,
                context -> {
                    if (player.getUUID().equals(
                            context.target().getUUID())) {
                        throw new IllegalStateException(
                                "expected damage modifier failure");
                    }
                    return StardewCombatDamageDecision.pass();
                });
        StardewCombatEvents.registerDamageModifier(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "damage_half_" + suffix),
                200,
                context -> player.getUUID().equals(
                        context.target().getUUID())
                        ? StardewCombatDamageDecision.set(
                                context.amount() / 2.0F)
                        : StardewCombatDamageDecision.pass());
        StardewCombatEvents.registerDamageModifier(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "damage_add_one_" + suffix),
                100,
                context -> player.getUUID().equals(
                        context.target().getUUID())
                        ? StardewCombatDamageDecision.set(
                                context.amount() + 1.0F)
                        : StardewCombatDamageDecision.pass());

        float modified = StardewCombatRegistry.applyDamageModifiers(
                new StardewCombatDamageContext(
                        player,
                        null,
                        player.damageSources().generic(),
                        helper.getLevel().dimension(),
                        10.0F));
        helper.assertValueEqual(
                modified,
                6.0F,
                "damage modifiers were not ordered and composed");

        StardewCombatEvents.registerKillListener(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "kill_throwing_" + suffix),
                300,
                context -> {
                    if (player.getUUID().equals(
                            context.player().getUUID())) {
                        throw new IllegalStateException(
                                "expected kill listener failure");
                    }
                });
        StardewCombatEvents.registerKillListener(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "kill_first_" + suffix),
                200,
                context -> {
                    if (player.getUUID().equals(
                            context.player().getUUID())) {
                        observedKills.add("first");
                    }
                });
        StardewCombatEvents.registerKillListener(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "kill_second_" + suffix),
                100,
                context -> {
                    if (player.getUUID().equals(
                            context.player().getUUID())) {
                        observedKills.add("second");
                    }
                });

        var target = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(target != null,
                "combat test target was unavailable");
        StardewCombatRegistry.announceKill(
                new StardewCombatKillContext(
                        player,
                        target,
                        player.damageSources().playerAttack(player),
                        ResourceLocation.withDefaultNamespace("zombie"),
                        Set.of("stardewcraft_gametest"),
                        helper.getLevel().dimension(),
                        helper.absolutePos(BlockPos.ZERO)));
        helper.assertValueEqual(
                observedKills,
                List.of("first", "second"),
                "kill listeners were not ordered or failure-isolated");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void fishPondRuntimeViewsAndEventsAreReadOnlyAndOrdered(
            GameTestHelper helper
    ) {
        String suffix = UUID.randomUUID().toString();
        List<String> observed = new ArrayList<>();
        StardewFishPondSnapshot pond = new StardewFishPondSnapshot(
                "pond_" + suffix,
                Optional.empty(),
                helper.getLevel().dimension(),
                helper.absolutePos(BlockPos.ZERO),
                helper.absolutePos(new BlockPos(1, 0, 0)),
                Optional.of(ResourceLocation.withDefaultNamespace("cod")),
                3,
                5,
                Optional.empty(),
                0,
                Optional.empty(),
                0,
                true,
                6,
                0,
                -1,
                false,
                false);

        StardewFishPondEvents.registerDailyListener(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "pond_daily_throwing_" + suffix),
                200,
                context -> {
                    if (pond.id().equals(context.pond().id())) {
                        throw new IllegalStateException(
                                "expected pond daily listener failure");
                    }
                });
        StardewFishPondEvents.registerDailyListener(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "pond_daily_observer_" + suffix),
                100,
                context -> {
                    if (pond.id().equals(context.pond().id())) {
                        observed.add("daily");
                    }
                });
        StardewFishPondEvents.registerRequestListener(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "pond_request_observer_" + suffix),
                100,
                context -> {
                    if (pond.id().equals(context.pond().id())) {
                        observed.add("request");
                    }
                });

        StardewFishPondEventRegistry.announceDaily(
                new StardewFishPondDailyContext(
                        helper.getLevel(), 10, pond));
        FishPondRecord mutablePond = new FishPondRecord(
                pond.id(),
                "",
                helper.getLevel().dimension().location().toString(),
                pond.managerPosition(),
                pond.bucketPosition(),
                Set.of(),
                Set.of(),
                pond.managerPosition().getX(),
                pond.managerPosition().getY(),
                pond.managerPosition().getZ(),
                pond.managerPosition().getX() + 2,
                pond.managerPosition().getY(),
                pond.managerPosition().getZ() + 2,
                "minecraft:cod",
                3,
                3,
                "",
                0,
                "minecraft:diamond",
                1,
                false,
                3,
                0,
                -1,
                0,
                false,
                false);
        ItemEntity delivered = new ItemEntity(
                helper.getLevel(),
                pond.managerPosition().getX() + 0.5D,
                pond.managerPosition().getY() + 0.5D,
                pond.managerPosition().getZ() + 0.5D,
                new ItemStack(Items.DIAMOND));
        helper.assertValueEqual(
                FishPondInteractionService.absorbItemEntity(
                        helper.getLevel(), mutablePond, delivered),
                FishPondInteractionService.ItemAbsorbResult
                        .NEED_ITEM_ACCEPTED,
                "fish pond request completion was not accepted");
        helper.assertTrue(
                delivered.getItem().isEmpty(),
                "fish pond request notification ran before item consumption");

        helper.assertValueEqual(
                observed,
                List.of("daily", "request"),
                "fish pond listeners were not ordered or failure-isolated");
        helper.assertTrue(
                StardewFishPonds.all(helper.getLevel()) != null,
                "fish pond runtime query did not return a snapshot list");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void unifiedProgressProjectsAndPublishesRealSystems(
            GameTestHelper helper
    ) {
        String suffix = UUID.randomUUID().toString();
        ServerPlayer player = FakePlayerFactory.get(
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "Progress API"));
        List<StardewProgressEvent> observed = new ArrayList<>();
        StardewProgressEvents.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "progress_throwing_" + suffix),
                200,
                event -> {
                    if (player.getUUID().equals(
                            event.actor().orElse(null))) {
                        throw new IllegalStateException(
                                "expected progress listener failure");
                    }
                });
        StardewProgressEvents.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "progress_observer_" + suffix),
                100,
                event -> {
                    if (player.getUUID().equals(
                            event.actor().orElse(null))) {
                        observed.add(event);
                    }
                });

        QuestManager quests = QuestManager.of(player);
        helper.assertTrue(quests != null, "player quest manager was unavailable");
        StardewProgressKey questKey = new StardewProgressKey(
                StardewProgressDomains.QUEST,
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft", "6"));
        helper.assertTrue(
                StardewProgressRequirements.requirements(
                                player,
                                questKey,
                                StardewProgressOperation.ACCEPT)
                        .satisfied(),
                "available quest acceptance reported a blocker");
        quests.acceptQuest("6", player);
        StardewProgressSnapshot activeQuest =
                StardewProgress.inspect(player, questKey);
        helper.assertTrue(activeQuest != null,
                "active built-in quest was absent from unified progress");
        helper.assertValueEqual(
                activeQuest.phase(),
                StardewProgressPhase.ACTIVE,
                "accepted quest did not project ACTIVE");
        helper.assertTrue(
                StardewProgressRequirements.requirements(
                                player,
                                questKey,
                                StardewProgressOperation.ACCEPT)
                        .blocking().stream()
                        .anyMatch(requirement ->
                                requirement.type().equals(
                                        StardewRequirementTypes
                                                .PROGRESS_ACCEPT_AVAILABLE)),
                "active quest omitted acceptance blocker");

        com.google.gson.JsonObject neverData =
                new com.google.gson.JsonObject();
        neverData.addProperty("value", false);
        var neverCondition = StardewConditions.decode(
                        ResourceLocation.fromNamespaceAndPath(
                                StardewCraft.MODID, "always"),
                        neverData)
                .result().orElseThrow();
        StardewRequirements.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "requirement_display_" + suffix),
                100,
                (context, condition, proposed) ->
                        condition == neverCondition
                                ? new StardewRequirement(
                                        condition.type(),
                                        proposed.state(),
                                        Component.literal(
                                                "GameTest blocker"),
                                        true)
                                : null);
        var requirementReport = StardewRequirements.evaluateAll(
                StardewConditionContext.forPlayer(player),
                List.of(neverCondition));
        helper.assertTrue(
                !requirementReport.satisfied()
                        && requirementReport.blocking().size() == 1,
                "condition report did not preserve authoritative blocker");
        helper.assertValueEqual(
                requirementReport.requirements().getFirst()
                        .description().getString(),
                "GameTest blocker",
                "requirement provider did not compose display metadata");

        MailService.addMailForTomorrow(player, "guildQuest");
        StardewProgressKey mailKey = new StardewProgressKey(
                StardewProgressDomains.MAIL,
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft", "guildquest"));
        StardewProgressSnapshot scheduledMail =
                StardewProgress.inspect(player, mailKey);
        helper.assertTrue(scheduledMail != null,
                "scheduled mail was absent from unified progress");
        helper.assertValueEqual(
                scheduledMail.phase(),
                StardewProgressPhase.SCHEDULED,
                "tomorrow mail did not project SCHEDULED");
        helper.assertTrue(
                StardewAcquisitionSources.find(
                        new ItemStack(
                                com.stardew.craft.item.ModItems
                                        .COPPER_PAN.get())).stream()
                        .anyMatch(source -> source.kind()
                                == StardewAcquisitionSource.Kind.PROGRESS),
                "mail attachment did not reach unified acquisition sources");
        StardewProgressKey attachmentMailKey = new StardewProgressKey(
                StardewProgressDomains.MAIL,
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft", "ccfishtankpan"));
        helper.assertTrue(
                StardewRewardPreviews.preview(player, attachmentMailKey)
                        .components().stream()
                        .anyMatch(component ->
                                component.kind()
                                        == StardewRewardComponent.Kind.ITEM
                                && component.icon().is(
                                        com.stardew.craft.item.ModItems
                                                .COPPER_PAN.get())),
                "mail attachment was absent from unified reward preview");

        BundleDefinition progressBundle = BundleDataManager.getAllBundles()
                .stream()
                .filter(definition -> definition.requiredCount() > 1)
                .findFirst()
                .orElseThrow();
        CommunityCenterSavedData communityCenter =
                CommunityCenterSavedData.get(helper.getLevel());
        communityCenter.markSlotComplete(
                player.getUUID(), progressBundle.bundleId(), 0);
        StardewProgressKey bundleKey =
                StardewProgressRegistry.communityCenterBundleKey(
                        progressBundle.bundleId());
        StardewProgressSnapshot activeBundle =
                StardewProgress.inspect(player, bundleKey);
        helper.assertTrue(activeBundle != null,
                "Community Center bundle was absent from unified progress");
        helper.assertValueEqual(
                activeBundle.phase(),
                StardewProgressPhase.ACTIVE,
                "partially filled bundle did not project ACTIVE");
        helper.assertValueEqual(
                activeBundle.metrics().getFirst().target(),
                progressBundle.requiredCount(),
                "bundle required count was not projected");
        communityCenter.markBundleAllSlotsComplete(
                player.getUUID(), progressBundle.bundleId());
        communityCenter.setRewardAvailable(
                player.getUUID(), progressBundle.bundleId(), true);
        helper.assertValueEqual(
                StardewProgress.inspect(player, bundleKey).phase(),
                StardewProgressPhase.REWARD_AVAILABLE,
                "completed bundle reward did not become claimable");
        ItemStack bundleReward =
                com.stardew.craft.communitycenter.network
                        .BundleClaimRewardPayload.parseRewardString(
                                progressBundle.rewardString());
        helper.assertTrue(
                !bundleReward.isEmpty()
                        && StardewAcquisitionSources.find(
                                bundleReward, player).stream()
                        .anyMatch(source ->
                                source.sourceId().equals(bundleKey.id())),
                "bundle reward did not reach unified acquisition sources");
        helper.assertTrue(
                StardewRewardPreviews.preview(player, bundleKey)
                        .components().stream()
                        .anyMatch(component ->
                                component.kind()
                                        == StardewRewardComponent.Kind.ITEM
                                && component.icon().is(
                                        bundleReward.getItem())),
                "bundle reward was absent from unified reward preview");

        com.stardew.craft.museum.MuseumDonationData museum =
                com.stardew.craft.museum.MuseumDonationData.get(
                        helper.getLevel());
        museum.donate(
                player.getUUID(),
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "ancient_seed").toString());
        var ancientSeedReward =
                com.stardew.craft.museum.MuseumRewardRegistry
                        .getAllRewards().stream()
                        .filter(reward -> reward.id().equals(
                                "ancient_seed_reward"))
                        .findFirst().orElseThrow();
        StardewProgressKey museumRewardKey =
                StardewProgressRegistry.museumRewardKey(
                        ancientSeedReward.id());
        StardewProgressSnapshot museumReward =
                StardewProgress.inspect(player, museumRewardKey);
        helper.assertTrue(museumReward != null,
                "museum reward was absent from unified progress");
        helper.assertValueEqual(
                museumReward.phase(),
                StardewProgressPhase.REWARD_AVAILABLE,
                "qualifying museum donation did not expose its reward");
        helper.assertTrue(
                StardewAcquisitionSources.find(
                        new ItemStack(
                                com.stardew.craft.item.ModItems
                                        .ANCIENT_FRUIT_SEEDS.get()),
                        player).stream()
                        .anyMatch(source ->
                                source.sourceId().equals(
                                        museumRewardKey.id())),
                                "museum reward did not reach unified acquisition sources");
        helper.assertTrue(
                StardewRewardPreviews.preview(player, museumRewardKey)
                        .components().stream()
                        .anyMatch(component ->
                                component.icon().is(
                                        com.stardew.craft.item.ModItems
                                                .ANCIENT_FRUIT_SEEDS.get())),
                "museum action reward was absent from unified reward preview");

        var specialDefinition = SpecialOrderDefinitions.all()
                .stream().findFirst().orElseThrow();
        ResourceLocation specialId = SpecialOrderDataLoader
                .snapshot().definitions()
                .entrySet().stream()
                .filter(entry -> entry.getValue()
                        .id().equals(specialDefinition.id()))
                .map(Map.Entry::getKey)
                .findFirst().orElseThrow();
        SpecialOrderInstance available = SpecialOrderInstance.create(
                specialDefinition, 1, 8);
        com.stardew.craft.player.PlayerDataManager
                .getPlayerData(player)
                .addMailFlag(
                        com.stardew.craft.specialorder
                                .SpecialOrderManager
                                .BOARD_UNLOCK_FLAG);
        com.stardew.craft.specialorder.SpecialOrderWorldData
                .get(helper.getLevel())
                .available().add(available);
        StardewProgressSnapshot availableOrder =
                StardewProgressRegistry.specialOrderSnapshot(
                        player, available, StardewProgressPhase.AVAILABLE);
        helper.assertTrue(availableOrder != null,
                "available special order was absent from unified progress");
        helper.assertValueEqual(
                availableOrder.key().id(),
                specialId,
                "special order lost its datapack definition ID");
        helper.assertValueEqual(
                availableOrder.scope(),
                StardewProgressScope.TEAM,
                "special order did not retain shared scope");
        helper.assertValueEqual(
                availableOrder.phase(),
                StardewProgressPhase.AVAILABLE,
                "board order did not project AVAILABLE");
        helper.assertValueEqual(
                availableOrder.metrics().size(),
                specialDefinition.objectives().size(),
                "special-order objective metrics were not projected");
        helper.assertTrue(
                StardewProgressRequirements.requirements(
                                player,
                                availableOrder.key(),
                                StardewProgressOperation.ACCEPT)
                        .satisfied(),
                "offered special order acceptance reported a blocker");

        ResourceLocation customDomain =
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "orchard_trial");
        ResourceLocation customEntry =
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "first_harvest");
        StardewProgress.registerProvider(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "orchard_listing_throwing_" + suffix),
                200,
                customDomain,
                new StardewProgressProvider() {
                    @Override
                    public StardewProgressSnapshot inspect(
                            ServerPlayer target,
                            ResourceLocation entryId
                    ) {
                        return null;
                    }

                    @Override
                    public java.util.Collection<ResourceLocation> entries(
                            ServerPlayer target
                    ) {
                        throw new IllegalStateException(
                                "expected progress listing failure");
                    }
                });
        StardewProgress.registerProvider(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "orchard_provider_" + suffix),
                100,
                customDomain,
                new StardewProgressProvider() {
                    @Override
                    public StardewProgressSnapshot inspect(
                            ServerPlayer target,
                            ResourceLocation entryId
                    ) {
                        return entryId.equals(customEntry)
                                ? new StardewProgressSnapshot(
                                        new StardewProgressKey(
                                                customDomain, entryId),
                                        StardewProgressScope.PLAYER,
                                        StardewProgressPhase.ACTIVE,
                                        List.of(),
                                        true,
                                        false,
                                        OptionalInt.empty())
                                : null;
                    }

                    @Override
                    public java.util.Collection<ResourceLocation> entries(
                            ServerPlayer target
                    ) {
                        return List.of(customEntry);
                    }
                });
        helper.assertTrue(
                StardewProgress.inspect(
                        player,
                        new StardewProgressKey(
                                customDomain, customEntry)) != null,
                "addon-owned progress provider was not resolved");
        helper.assertTrue(
                StardewProgressRequirements.requirements(
                                player,
                                new StardewProgressKey(
                                        customDomain, customEntry),
                                StardewProgressOperation.ACCEPT)
                        .blocking().stream()
                        .anyMatch(requirement ->
                                requirement.type().equals(
                                        StardewRequirementTypes
                                                .PROGRESS_ACCEPT_AVAILABLE)),
                "addon ACTIVE snapshot did not receive generic acceptance blocker");
        helper.assertTrue(
                StardewProgress.list(player, customDomain).stream()
                        .anyMatch(snapshot ->
                                snapshot.key().id().equals(customEntry)),
                "addon-owned progress entry was absent from its domain catalog");
        helper.assertTrue(
                StardewProgress.list(
                        player,
                        StardewProgressDomains.COMMUNITY_CENTER).stream()
                        .anyMatch(snapshot ->
                                snapshot.key().equals(bundleKey)),
                "Community Center bundle was absent from domain catalog");
        helper.assertTrue(
                StardewProgress.list(
                        player,
                        StardewProgressDomains.MUSEUM).stream()
                        .anyMatch(snapshot ->
                                snapshot.key().equals(museumRewardKey)),
                "museum milestone was absent from domain catalog");

        helper.assertTrue(
                observed.stream().anyMatch(event ->
                        event.type()
                                == StardewProgressEventType.ACCEPTED
                                && event.after().key().equals(questKey)),
                "real quest acceptance did not reach unified listeners");
        helper.assertTrue(
                observed.stream().anyMatch(event ->
                        event.type()
                                == StardewProgressEventType.SCHEDULED
                                && event.after().key().equals(mailKey)),
                "real mail scheduling did not reach unified listeners");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void equipmentStackRoundTrip(GameTestHelper helper) {
        ItemStack source = new ItemStack(Items.DIAMOND, 16);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("API component sentinel"));

        PlayerStardewData original = new PlayerStardewData(UUID.randomUUID());
        original.setEquippedLeftRingStack(source);
        CompoundTag saved = original.toNBT(helper.getLevel().registryAccess());
        PlayerStardewData loaded = PlayerStardewData.fromNBT(
                saved, UUID.randomUUID(), helper.getLevel().registryAccess());

        ItemStack restored = loaded.getEquippedLeftRingStack();
        helper.assertTrue(restored.is(Items.DIAMOND), "equipment item changed during NBT round trip");
        helper.assertValueEqual(restored.getCount(), 1, "equipment slot must contain one item");
        helper.assertValueEqual(restored.get(DataComponents.CUSTOM_NAME),
                Component.literal("API component sentinel"), "equipment component was lost");
        helper.assertValueEqual(source.getCount(), 16, "equipment setter mutated the source stack");

        restored.setCount(0);
        helper.assertValueEqual(loaded.getEquippedLeftRingStack().getCount(), 1,
                "equipment getter leaked its internal stack");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void legacyEquipmentIdsMigrate(GameTestHelper helper) {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("EquippedLeftRing", "minecraft:diamond");
        legacy.putString("EquippedRightRing", "minecraft:emerald");
        legacy.putString("EquippedBoots", "minecraft:iron_boots");

        PlayerStardewData loaded = PlayerStardewData.fromNBT(
                legacy, UUID.randomUUID(), helper.getLevel().registryAccess());

        helper.assertTrue(loaded.getEquippedLeftRingStack().is(Items.DIAMOND),
                "legacy left ring ID did not migrate");
        helper.assertTrue(loaded.getEquippedRightRingStack().is(Items.EMERALD),
                "legacy right ring ID did not migrate");
        helper.assertTrue(loaded.getEquippedBootsStack().is(Items.IRON_BOOTS),
                "legacy boots ID did not migrate");
        CompoundTag rewritten = loaded.toNBT(helper.getLevel().registryAccess());
        helper.assertTrue(rewritten.contains("EquippedLeftRingStack", CompoundTag.TAG_COMPOUND),
                "migrated equipment was not written in the full-stack format");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void compositePaymentRefundsMoneyAndExactStacks(
            GameTestHelper helper
    ) {
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(
                        UUID.randomUUID(), "Economy API"),
                ClientInformation.createDefault());
        PlayerStardewDataAPI.setMoney(player, 500);
        ItemStack markedDiamonds =
                new ItemStack(Items.DIAMOND, 3);
        markedDiamonds.set(
                DataComponents.CUSTOM_NAME,
                Component.literal("Payment sentinel"));
        helper.assertTrue(
                player.getInventory().add(markedDiamonds),
                "could not prepare payment inventory");

        StardewCost compositeCost = StardewCost.of(
                new StardewCurrencyCost(
                        StardewCurrencies.MONEY, 125),
                new StardewItemCost(
                        ResourceLocation.withDefaultNamespace(
                                "diamond"),
                        2));
        helper.assertTrue(
                StardewCosts.requirements(
                        player, compositeCost).satisfied(),
                "affordable composite cost reported a blocker");
        var payment = StardewCosts.pay(player, compositeCost);
        helper.assertTrue(
                payment.success(),
                "composite payment was rejected");
        helper.assertValueEqual(
                PlayerStardewDataAPI.getMoney(player),
                375,
                "money component was not withdrawn");
        helper.assertValueEqual(
                player.getInventory().countItem(Items.DIAMOND),
                1,
                "item component was not withdrawn");
        helper.assertTrue(
                StardewCosts.requirements(player, compositeCost)
                        .blocking().stream().anyMatch(requirement ->
                                requirement.type().equals(
                                        StardewRequirementTypes
                                                .COST_ITEM)),
                "spent item cost did not report an affordability blocker");

        helper.assertTrue(
                payment.receipt().orElseThrow().refund(),
                "composite payment did not refund");
        helper.assertValueEqual(
                PlayerStardewDataAPI.getMoney(player),
                500,
                "money refund was not exact");
        helper.assertValueEqual(
                player.getInventory().countItem(Items.DIAMOND),
                3,
                "item refund was not exact");
        helper.assertTrue(
                StardewCosts.requirements(
                        player, compositeCost).satisfied(),
                "refunded composite cost still reported a blocker");
        helper.assertTrue(
                player.getInventory().items.stream()
                        .filter(stack -> stack.is(Items.DIAMOND))
                        .anyMatch(stack -> Component.literal(
                                "Payment sentinel").equals(
                                stack.get(
                                        DataComponents.CUSTOM_NAME))),
                "refunded item lost its data components");

        String shopId = "stardewcraft_gametest:orchard_"
                + UUID.randomUUID();
        player.getInventory().add(
                new ItemStack(Items.EMERALD, 2));
        StardewShopCosts.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "cost_throwing_" + UUID.randomUUID()),
                200,
                (context, proposed) -> {
                    if (context.shopId().equals(shopId)) {
                        throw new IllegalStateException(
                                "expected shop cost provider failure");
                    }
                    return proposed;
                });
        StardewShopCosts.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "cost_replace_" + UUID.randomUUID()),
                100,
                (context, proposed) -> context.shopId()
                        .equals(shopId)
                        ? StardewCost.of(new StardewItemCost(
                                ResourceLocation
                                        .withDefaultNamespace(
                                                "emerald"),
                                2))
                        : proposed);
        ShopItemEntry apple = ShopItemEntry.fromNetwork(
                "minecraft:apple", "", "",
                75, 1, "", 0, 1);
        var customCost = ShopCostService.resolve(
                player, shopId, apple, 1,
                StardewCurrencies.MONEY).orElseThrow();
        helper.assertTrue(
                customCost.modified(),
                "addon shop cost did not replace the legacy price");
        var shopCostContext =
                new com.stardew.craft.api.v1.shop
                        .StardewShopCostContext(
                                player,
                                shopId,
                                ShopCostService.toApiEntry(apple),
                                1);
        helper.assertTrue(
                StardewShopCosts.requirements(
                        shopCostContext,
                        StardewCost.of(new StardewCurrencyCost(
                                StardewCurrencies.MONEY, 75)))
                        .satisfied(),
                "resolved addon shop cost reported a false blocker");
        helper.assertTrue(
                StardewCosts.pay(
                        player, customCost.cost()).success(),
                "resolved addon shop cost could not be paid");
        helper.assertValueEqual(
                PlayerStardewDataAPI.getMoney(player),
                500,
                "custom item-only cost also charged legacy money");
        helper.assertValueEqual(
                player.getInventory().countItem(Items.EMERALD),
                0,
                "custom shop item cost was not consumed");
        helper.assertTrue(
                StardewShopCosts.requirements(
                        shopCostContext,
                        StardewCost.of(new StardewCurrencyCost(
                                StardewCurrencies.MONEY, 75)))
                        .blocking().stream().anyMatch(requirement ->
                                requirement.type().equals(
                                        StardewRequirementTypes
                                                .COST_ITEM)),
                "shop cost preflight ignored the resolved addon item cost");

        String productId =
                "stardewcraft_gametest:orchard_blessing";
        StardewShopProducts.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "product_throwing_" + UUID.randomUUID()),
                200,
                context -> {
                    if (context.entry().item()
                            .equals(productId)) {
                        throw new IllegalStateException(
                                "expected product prepare failure");
                    }
                    return StardewShopProductPreparation.pass();
                });
        AtomicBoolean granted = new AtomicBoolean();
        StardewShopProducts.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "product_accept_" + UUID.randomUUID()),
                100,
                context -> {
                    if (!context.entry().item()
                            .equals(productId)) {
                        return StardewShopProductPreparation.pass();
                    }
                    return StardewShopProductPreparation.accept(
                            grantContext -> {
                                granted.set(true);
                                return true;
                            });
                });
        ShopItemEntry virtualEntry =
                ShopItemEntry.fromNetwork(
                        productId, "Orchard Blessing", "",
                        0, 1, "", 0, 1);
        StardewShopProductContext productContext =
                new StardewShopProductContext(
                        player, shopId,
                        ShopCostService.toApiEntry(virtualEntry), 1);
        var productResolution =
                StardewShopProductRegistry.resolve(productContext);
        helper.assertValueEqual(
                productResolution.decision(),
                StardewShopProductDecision.ACCEPT,
                "fallback product handler was not selected");
        helper.assertTrue(
                StardewShopProductRegistry.grant(
                        productResolution, productContext),
                "selected product handler did not grant");
        helper.assertTrue(
                granted.get(),
                "selected product handler was not invoked");
        helper.assertTrue(
                productResolution.requirements().satisfied(),
                "accepted virtual product reported a blocker");

        String blockedProductId =
                "stardewcraft_gametest:cooldown_service";
        ResourceLocation cooldownRequirement =
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "orchard_service_ready");
        StardewShopProducts.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "product_cooldown_"
                                + UUID.randomUUID()),
                150,
                context -> context.entry().item()
                                .equals(blockedProductId)
                        ? StardewShopProductPreparation.reject()
                        : StardewShopProductPreparation.pass(),
                (context, decision) ->
                        new StardewRequirementReport(List.of(
                                new StardewRequirement(
                                        cooldownRequirement,
                                        StardewRequirement.State
                                                .UNSATISFIED,
                                        Component.literal(
                                                "Orchard service is cooling down"),
                                        true))));
        ShopItemEntry blockedProduct =
                ShopItemEntry.fromNetwork(
                        blockedProductId, "Cooldown Service", "",
                        0, 1, "", 0, 1);
        helper.assertTrue(
                StardewShopProducts.requirements(
                                new StardewShopProductContext(
                                        player,
                                        shopId,
                                        ShopCostService.toApiEntry(
                                                blockedProduct),
                                        1))
                        .blocking().stream()
                        .anyMatch(requirement ->
                                requirement.type().equals(
                                        cooldownRequirement)),
                "virtual product omitted addon-authored rejection reason");

        ShopItemEntry unknownRecipe =
                ShopItemEntry.fromNetwork(
                        "recipe:stardewcraft_gametest:missing",
                        "", "", 100, 1, "", 0, 1);
        var rejectedRecipe =
                StardewShopProductRegistry.resolve(
                        new StardewShopProductContext(
                                player, shopId,
                                ShopCostService.toApiEntry(
                                        unknownRecipe),
                                1));
        helper.assertValueEqual(
                rejectedRecipe.decision(),
                StardewShopProductDecision.REJECT,
                "unknown built-in recipe was not rejected");
        helper.assertTrue(
                rejectedRecipe.requirements().blocking().stream()
                        .anyMatch(requirement ->
                                requirement.type().equals(
                                        StardewRequirementTypes
                                                .SHOP_PRODUCT_ACCEPTED)),
                "built-in virtual product rejection was not explained");

        String limitedItem =
                "stardewcraft_gametest:weekly_stock_"
                        + UUID.randomUUID();
        helper.assertValueEqual(
                ShopStockTracker.getRemaining(
                        player, shopId, limitedItem, 5),
                5,
                "fresh limited stock was not available");
        ShopStockTracker.recordPurchase(
                player, shopId, limitedItem, 2);
        helper.assertValueEqual(
                ShopStockTracker.getRemaining(
                        player, shopId, limitedItem, 5),
                3,
                "persistent stock ledger did not record purchase");
        StardewShopRowKey key = new StardewShopRowKey(
                "AnimalShop", "stardewcraft:milk_pail");
        var before = StardewShopInventories.inspect(
                player, key).orElseThrow();
        helper.assertValueEqual(
                before.key(), key,
                "shop inventory snapshot changed compound identity");
        helper.assertValueEqual(
                before.remainingStock(), 1,
                "shop inventory snapshot lost initial stock");
        helper.assertTrue(
                before.available(),
                "in-stock shop row reported a blocker");

        ShopStockTracker.recordPurchase(
                player, key.shopId(), key.entryId(), 1);
        helper.assertTrue(
                ShopRegistry.getFilteredItemsForPlayer(
                                key.shopId(),
                                ShopRegistry.get(key.shopId()),
                                player)
                        .stream().noneMatch(entry ->
                                entry.itemId().equals(
                                        key.entryId())),
                "normal shop inventory retained a sold-out row");
        var soldOut = StardewShopInventories.inspect(
                player, key).orElseThrow();
        helper.assertValueEqual(
                soldOut.remainingStock(), 0,
                "diagnostic shop inventory did not retain sold-out row");
        helper.assertTrue(
                soldOut.requirements().blocking().stream()
                        .anyMatch(requirement ->
                                requirement.type().equals(
                                        StardewRequirementTypes
                                                .SHOP_STOCK_AVAILABLE)),
                                "sold-out row omitted stock blocker");

        player.getInventory().add(
                new ItemStack(ModItems.MILK_PAIL.get()));
        helper.assertTrue(
                StardewShopInventories.candidates(
                                player, key.shopId())
                        .stream().anyMatch(candidate ->
                                candidate.key().equals(key)),
                "candidate directory discarded a condition-hidden row");
        var hidden = StardewShopInventories.inspect(
                player, key).orElseThrow();
        helper.assertTrue(
                hidden.requirements().blocking().stream()
                        .anyMatch(requirement ->
                                requirement.type().equals(
                                        ResourceLocation
                                                .fromNamespaceAndPath(
                                                        StardewCraft.MODID,
                                                        "lacks_item"))),
                "condition-hidden row omitted its condition blocker");
        helper.assertTrue(
                hidden.requirements().blocking().stream()
                        .anyMatch(requirement ->
                                requirement.type().equals(
                                        StardewRequirementTypes
                                                .SHOP_ENTRY_LISTED)),
                "condition-hidden row was reported as currently listed");

        StardewShopRowKey missing = new StardewShopRowKey(
                key.shopId(),
                "stardewcraft_gametest:missing_product");
        helper.assertTrue(
                StardewShopInventories.requirements(
                                player, missing, 1)
                        .blocking().stream().anyMatch(requirement ->
                                requirement.type().equals(
                                        StardewRequirementTypes
                                                .SHOP_ENTRY_LISTED)),
                "missing shop row omitted existence blocker");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void productionPlanProvidersComposeAndIsolateFailures(
            GameTestHelper helper
    ) {
        ResourceLocation machineId =
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "orchard_press_" + UUID.randomUUID());
        StardewProductionPlans.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "production_throwing_"
                                + UUID.randomUUID()),
                200,
                (context, proposed) -> {
                    if (context.machineId().equals(machineId)) {
                        throw new IllegalStateException(
                                "expected production provider failure");
                    }
                    return Optional.of(proposed);
                });
        StardewProductionPlans.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "production_replace_"
                                + UUID.randomUUID()),
                100,
                (context, proposed) ->
                        context.machineId().equals(machineId)
                                ? Optional.of(
                                        new StardewProductionPlan(
                                                new ItemStack(
                                                        Items.GOLDEN_APPLE,
                                                        2),
                                                30))
                                : Optional.of(proposed));

        var resolved = StardewProductionPlans.resolve(
                new StardewProductionContext(
                        machineId,
                        helper.getLevel(),
                        BlockPos.ZERO,
                        new ItemStack(Items.APPLE),
                        Optional.empty(),
                        true),
                new StardewProductionPlan(
                        new ItemStack(Items.HONEY_BOTTLE),
                        120)).orElseThrow();
        helper.assertTrue(
                resolved.output().is(Items.GOLDEN_APPLE),
                "production output provider did not apply");
        helper.assertValueEqual(
                resolved.output().getCount(),
                2,
                "production output count changed");
        helper.assertValueEqual(
                resolved.minutes(),
                30,
                "production duration provider did not apply");

        ItemStack leaked = resolved.output();
        leaked.setCount(1);
        helper.assertValueEqual(
                resolved.output().getCount(),
                2,
                "production plan leaked its output stack");

        AtomicInteger observed = new AtomicInteger();
        StardewProductionEvents.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "production_event_throwing_"
                                + UUID.randomUUID()),
                200,
                event -> {
                    if (event.machineId().equals(machineId)) {
                        throw new IllegalStateException(
                                "expected production event failure");
                    }
                });
        StardewProductionEvents.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "production_event_observer_"
                                + UUID.randomUUID()),
                100,
                event -> {
                    if (event.machineId().equals(machineId)
                            && event.phase()
                            == StardewProductionPhase.STARTED) {
                        observed.incrementAndGet();
                    }
                });
        StardewProductionEvent event =
                new StardewProductionEvent(
                        StardewProductionPhase.STARTED,
                        machineId,
                        helper.getLevel(),
                        BlockPos.ZERO,
                        new ItemStack(Items.APPLE),
                        resolved.output(),
                        30);
        StardewProductionEventRegistry.dispatch(event);
        helper.assertValueEqual(
                observed.get(),
                1,
                "production event failure blocked later listeners");
        ItemStack eventOutput = event.output();
        eventOutput.setCount(1);
        helper.assertValueEqual(
                event.output().getCount(),
                2,
                "production event leaked its output stack");

        ResourceLocation weatherMachineId =
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "rain_collector_" + UUID.randomUUID());
        StardewMachineCycles.registerPlan(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "cycle_plan_" + UUID.randomUUID()),
                100,
                (context, proposed) ->
                        context.machineId().equals(weatherMachineId)
                                && context.kind()
                                == StardewMachineCycleKind.ENVIRONMENTAL
                                ? Optional.of(
                                        new StardewProductionPlan(
                                                new ItemStack(
                                                        Items.WATER_BUCKET),
                                                15))
                                : Optional.of(proposed));
        var environmental = StardewMachineCycles.resolve(
                new StardewMachineCycleContext(
                        weatherMachineId,
                        helper.getLevel(),
                        BlockPos.ZERO,
                        StardewMachineCycleKind.ENVIRONMENTAL,
                        ItemStack.EMPTY,
                        Optional.empty(),
                        true),
                new StardewProductionPlan(
                        new ItemStack(Items.BUCKET), 60))
                .orElseThrow();
        helper.assertTrue(
                environmental.output().is(Items.WATER_BUCKET),
                "environmental cycle did not accept empty input");
        helper.assertValueEqual(
                environmental.minutes(), 15,
                "general cycle provider did not replace duration");
        AtomicInteger environmentalEvents = new AtomicInteger();
        StardewMachineCycles.registerListener(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "environmental_observer_"
                                + UUID.randomUUID()),
                100,
                transition -> {
                    if (transition.machineId()
                                    .equals(weatherMachineId)
                            && transition.kind()
                            == StardewMachineCycleKind.ENVIRONMENTAL) {
                        environmentalEvents.incrementAndGet();
                    }
                });
        StardewMachineCycles.announce(
                new StardewMachineCycleEvent(
                        StardewProductionPhase.STARTED,
                        StardewMachineCycleKind.ENVIRONMENTAL,
                        weatherMachineId,
                        helper.getLevel(),
                        BlockPos.ZERO,
                        ItemStack.EMPTY,
                        environmental.output(),
                        15,
                        true));
        helper.assertValueEqual(
                environmentalEvents.get(), 1,
                "addon-owned machine could not announce a cycle");

        ResourceLocation baitMakerId =
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft", "bait_maker");
        AtomicInteger started = new AtomicInteger();
        AtomicInteger ready = new AtomicInteger();
        AtomicInteger collected = new AtomicInteger();
        StardewProductionEvents.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "production_machine_observer_"
                                + UUID.randomUUID()),
                50,
                transition -> {
                    if (!transition.machineId()
                            .equals(baitMakerId)) {
                        return;
                    }
                    switch (transition.phase()) {
                        case STARTED -> started.incrementAndGet();
                        case READY -> ready.incrementAndGet();
                        case COLLECTED ->
                                collected.incrementAndGet();
                    }
                });
        BaitMakerBlockEntity baitMaker =
                new BaitMakerBlockEntity(
                        BlockPos.ZERO,
                        ModBlocks.BAIT_MAKER.get()
                                .defaultBlockState());
        baitMaker.setLevel(helper.getLevel());
        ItemStack sardine = new ItemStack(
                com.stardew.craft.item.ModItems.SARDINE.get());
        helper.assertTrue(
                baitMaker.tryInsertWithResult(
                        sardine, null).inserted(),
                "migrated machine rejected valid production");
        helper.assertValueEqual(
                sardine.getCount(),
                0,
                "migrated machine did not consume input");
        baitMaker.advanceDays(1);
        helper.assertFalse(
                baitMaker.harvestOne().isEmpty(),
                "migrated machine did not collect output");
        helper.assertValueEqual(
                started.get(), 1,
                "machine did not emit STARTED");
        helper.assertValueEqual(
                ready.get(), 1,
                "machine did not emit READY");
        helper.assertValueEqual(
                collected.get(), 1,
                "machine did not emit COLLECTED");

        ResourceLocation crystalariumId =
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft", "crystalarium");
        AtomicInteger repeatingStarted = new AtomicInteger();
        AtomicInteger repeatingReady = new AtomicInteger();
        AtomicInteger repeatingCollected = new AtomicInteger();
        StardewMachineCycles.registerPlan(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "crystalarium_cycle_"
                                + UUID.randomUUID()),
                1000,
                (context, proposed) ->
                        context.machineId().equals(crystalariumId)
                                && context.kind()
                                == StardewMachineCycleKind.REPEATING
                                ? Optional.of(
                                        new StardewProductionPlan(
                                                proposed.output(), 0))
                                : Optional.of(proposed));
        StardewMachineCycles.registerListener(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "crystalarium_observer_"
                                + UUID.randomUUID()),
                100,
                transition -> {
                    if (!transition.machineId()
                            .equals(crystalariumId)
                            || transition.kind()
                            != StardewMachineCycleKind.REPEATING) {
                        return;
                    }
                    switch (transition.phase()) {
                        case STARTED ->
                                repeatingStarted.incrementAndGet();
                        case READY ->
                                repeatingReady.incrementAndGet();
                        case COLLECTED ->
                                repeatingCollected.incrementAndGet();
                    }
                });
        com.stardew.craft.blockentity.CrystalariumBlockEntity
                crystalarium =
                new com.stardew.craft.blockentity
                        .CrystalariumBlockEntity(
                        BlockPos.ZERO,
                        ModBlocks.CRYSTALARIUM.get()
                                .defaultBlockState());
        crystalarium.setLevel(helper.getLevel());
        ItemStack quartz = new ItemStack(
                com.stardew.craft.item.ModItems.QUARTZ.get());
        helper.assertTrue(
                crystalarium.tryInsertWithResult(
                        quartz, null).inserted(),
                "repeating machine rejected its catalyst");
        helper.assertTrue(
                crystalarium.isReady(),
                "cycle provider duration was not consumed");
        helper.assertFalse(
                crystalarium.harvestOne().isEmpty(),
                "repeating machine did not collect output");
        helper.assertValueEqual(
                crystalarium.stardewCycleKind(),
                StardewMachineCycleKind.REPEATING,
                "repeating cycle kind was not retained");
        helper.assertTrue(
                crystalarium.stardewAutomationStarted(),
                "automatic repeat was not identified");
        helper.assertValueEqual(
                repeatingStarted.get(), 2,
                "repeating machine did not begin its next cycle");
        helper.assertValueEqual(
                repeatingReady.get(), 1,
                "repeating machine did not emit READY");
        helper.assertValueEqual(
                repeatingCollected.get(), 1,
                "repeating machine did not emit COLLECTED");

        ResourceLocation wormBinId =
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft", "worm_bin");
        ResourceLocation lightningRodId =
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft", "lightning_rod");
        AtomicInteger passiveTransitions = new AtomicInteger();
        AtomicInteger environmentalTransitions =
                new AtomicInteger();
        StardewMachineCycles.registerPlan(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "autonomous_cycles_"
                                + UUID.randomUUID()),
                1000,
                (context, proposed) ->
                        context.machineId().equals(wormBinId)
                                || context.machineId()
                                        .equals(lightningRodId)
                                ? Optional.of(
                                        new StardewProductionPlan(
                                                proposed.output(), 0))
                                : Optional.of(proposed));
        StardewMachineCycles.registerListener(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "autonomous_observer_"
                                + UUID.randomUUID()),
                100,
                transition -> {
                    if (transition.machineId().equals(wormBinId)
                            && transition.kind()
                            == StardewMachineCycleKind.PASSIVE) {
                        passiveTransitions.incrementAndGet();
                    }
                    if (transition.machineId()
                                    .equals(lightningRodId)
                            && transition.kind()
                            == StardewMachineCycleKind.ENVIRONMENTAL) {
                        environmentalTransitions.incrementAndGet();
                    }
                });
        com.stardew.craft.blockentity.WormBinBlockEntity
                wormBin = new com.stardew.craft.blockentity
                .WormBinBlockEntity(
                        BlockPos.ZERO,
                        ModBlocks.WORM_BIN.get()
                                .defaultBlockState());
        wormBin.setLevel(helper.getLevel());
        com.stardew.craft.blockentity.WormBinBlockEntity
                .serverTick(
                        helper.getLevel(),
                        BlockPos.ZERO,
                        ModBlocks.WORM_BIN.get()
                                .defaultBlockState(),
                        wormBin);
        helper.assertTrue(
                wormBin.isReady(),
                "passive cycle provider was not applied");
        helper.assertFalse(
                wormBin.harvestOne().isEmpty(),
                "passive output could not be collected");
        helper.assertValueEqual(
                wormBin.stardewCycleKind(),
                StardewMachineCycleKind.PASSIVE,
                "passive machine kind changed");
        helper.assertValueEqual(
                passiveTransitions.get(), 4,
                "passive machine did not emit one complete cycle "
                        + "and the next STARTED");

        com.stardew.craft.blockentity.LightningRodBlockEntity
                lightningRod =
                new com.stardew.craft.blockentity
                        .LightningRodBlockEntity(
                        BlockPos.ZERO,
                        ModBlocks.LIGHTNING_ROD.get()
                                .defaultBlockState());
        lightningRod.setLevel(helper.getLevel());
        lightningRod.startChargingFromStrike();
        helper.assertTrue(
                lightningRod.isReady(),
                "environmental cycle provider was not applied");
        helper.assertFalse(
                lightningRod.harvestOne().isEmpty(),
                "environmental output could not be collected");
        helper.assertValueEqual(
                lightningRod.stardewCycleKind(),
                StardewMachineCycleKind.ENVIRONMENTAL,
                "environmental machine kind changed");
        helper.assertValueEqual(
                environmentalTransitions.get(), 3,
                "environmental machine did not emit a complete cycle");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void playerProfileRoundTripAndLegacyDetection(GameTestHelper helper) {
        PlayerStardewData legacy = PlayerStardewData.fromNBT(new CompoundTag(), UUID.randomUUID());
        helper.assertFalse(legacy.isProfileComplete(), "legacy save was not marked for profile collection");

        PlayerStardewData original = new PlayerStardewData(UUID.randomUUID());
        original.setProfile("Farmer", "strawberries", 1);
        PlayerStardewData loaded = PlayerStardewData.fromNBT(original.toNBT(), UUID.randomUUID());
        helper.assertTrue(loaded.isProfileComplete(), "completed player profile was lost");
        helper.assertFalse(loaded.isMale(), "female gender changed during NBT round trip");
        helper.assertValueEqual(loaded.getPreferredName(), "Farmer", "preferred name changed");
        helper.assertValueEqual(loaded.getFavoriteThing(), "strawberries", "favorite thing changed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void addonEquipmentSlotControlsGameplayResolver(GameTestHelper helper) {
        ResourceLocation providerId = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "brick_ring");
        try {
            StardewEquipmentDataApi.registerProvider(providerId, 1000, stack ->
                    stack.is(Items.BRICK) ? ringData() : null);
        } catch (IllegalStateException duplicateOnRerun) {
            // The development server may rerun the same test registry without restarting the JVM.
        }

        helper.assertTrue(EquipmentSlotResolver.isRing(new ItemStack(Items.BRICK)),
                "public equipment slot was not consumed by the gameplay resolver");
        helper.assertFalse(EquipmentSlotResolver.isBoots(new ItemStack(Items.BRICK)),
                "public equipment slot resolved to the wrong gameplay slot");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void addonAnimalRecordMovesAndRoundTrips(GameTestHelper helper) {
        registerTestAnimalType();
        AnimalWorldData worldData = AnimalWorldData.get(helper.getLevel());
        UUID owner = UUID.randomUUID();
        BlockPos firstManager = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos secondManager = helper.absolutePos(new BlockPos(12, 2, 2));
        String firstBuilding = worldData.createBuilding(
                helper.getLevel(),
                AnimalBuildingType.COOP_TIER_3,
                owner,
                firstManager,
                3,
                "GameTest Coop A",
                12
        );
        String secondBuilding = worldData.createBuilding(
                helper.getLevel(),
                AnimalBuildingType.COOP_TIER_3,
                owner,
                secondManager,
                3,
                "GameTest Coop B",
                12
        );

        var animal = worldData.createAnimal(
                TEST_ANIMAL_TYPE,
                "Canary Goose",
                firstBuilding,
                AnimalAcquisitionSource.PURCHASE
        );
        CompoundTag addonState = new CompoundTag();
        addonState.putString("lineage", "silver");
        helper.assertTrue(
                StardewAnimalPersistentData.write(
                        helper.getLevel(), animal.animalId(), TEST_ANIMAL_DATA, addonState),
                "addon animal data could not be written through the authoritative world service"
        );
        helper.assertTrue(
                worldData.moveAnimalToBuilding(animal.animalId(), secondBuilding, null),
                "registered addon animal could not move between compatible buildings"
        );
        helper.assertValueEqual(
                StardewAnimalTypes.entityType(TEST_ANIMAL_TYPE),
                ModEntities.WHITE_CHICKEN.get(),
                "registered addon animal entity projection changed"
        );

        CompoundTag saved = worldData.save(
                new CompoundTag(), helper.getLevel().registryAccess());
        AnimalWorldData loaded = AnimalWorldData.load(
                saved, helper.getLevel().registryAccess());
        var restored = loaded.getAnimal(animal.animalId()).orElse(null);
        helper.assertTrue(restored != null, "addon animal disappeared during world-data round trip");
        helper.assertValueEqual(
                restored.buildingId(),
                secondBuilding,
                "addon animal move was not persisted"
        );
        helper.assertValueEqual(
                StardewAnimalPersistentData.read(restored, TEST_ANIMAL_DATA)
                        .orElseThrow().payload().getString("lineage"),
                "silver",
                "addon animal namespaced state was not persisted"
        );

        worldData.removeAnimal(animal.animalId());
        worldData.removeBuilding(firstBuilding);
        worldData.removeBuilding(secondBuilding);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void addonFarmStepsRetryAndStateSurvivesTransfer(GameTestHelper helper) {
        String suffix = UUID.randomUUID().toString();
        UUID owner = UUID.randomUUID();
        UUID newOwner = UUID.randomUUID();
        FarmInstanceRegistry registry = FarmInstanceRegistry.get(helper.getLevel().getServer());
        FarmInstance farm = registry.createFarm(
                owner, "Farm Step Owner", "API Canary Farm", FarmType.STANDARD);
        List<String> calls = new ArrayList<>();

        ResourceLocation firstStep = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "farm_first_" + suffix);
        ResourceLocation failingStep = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "farm_failing_" + suffix);
        ResourceLocation finalStep = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "farm_final_" + suffix);
        StardewFarmInitializationSteps.register(
                firstStep, 1, 300,
                StardewFarmInitializationSteps.FailurePolicy.CONTINUE,
                context -> calls.add("first"));
        StardewFarmInitializationSteps.register(
                failingStep, 1, 200,
                StardewFarmInitializationSteps.FailurePolicy.CONTINUE,
                context -> {
                    calls.add("failing");
                    throw new IllegalStateException("expected GameTest failure");
                });
        StardewFarmInitializationSteps.register(
                finalStep, 1, 100,
                StardewFarmInitializationSteps.FailurePolicy.CONTINUE,
                context -> calls.add("final"));

        StardewFarmInitializationSteps.RunReport firstRun =
                StardewFarmInitializationSteps.runPending(helper.getLevel(), owner);
        helper.assertValueEqual(firstRun.attempted(), 3, "not all farm steps were attempted");
        helper.assertValueEqual(firstRun.succeeded(), 2, "successful farm steps were not recorded");
        helper.assertValueEqual(firstRun.failed(), 1, "failing farm step was not reported");
        helper.assertValueEqual(calls, List.of("first", "failing", "final"),
                "farm initialization ordering changed");

        StardewFarmInitializationSteps.RunReport retry =
                StardewFarmInitializationSteps.runPending(helper.getLevel(), owner);
        helper.assertValueEqual(retry.attempted(), 1, "successful farm steps were executed twice");
        helper.assertValueEqual(calls, List.of("first", "failing", "final", "failing"),
                "only the failed farm step should remain pending");

        StardewFarmPersistentData.Key stateKey = StardewFarmPersistentData.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "farm_state_" + suffix),
                2);
        CompoundTag state = new CompoundTag();
        state.putString("bundle_variant", "hard");
        helper.assertTrue(StardewFarmPersistentData.write(
                        helper.getLevel().getServer(), owner, stateKey, state),
                "addon farm state could not be written");
        helper.assertTrue(registry.transferFarm(owner, newOwner, "New Farm Owner"),
                "farm transfer failed");
        helper.assertValueEqual(
                StardewFarmPersistentData.read(
                                helper.getLevel().getServer(), newOwner, stateKey)
                        .orElseThrow().payload().getString("bundle_variant"),
                "hard",
                "addon farm state did not survive ownership transfer"
        );
        FarmInstance transferred = registry.getFarm(newOwner);
        helper.assertTrue(transferred != null, "transferred farm disappeared");
        helper.assertValueEqual(
                transferred.getInitializationStepVersion(firstStep),
                1,
                "completed initialization step version did not survive transfer"
        );

        registry.deleteFarm(newOwner);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void addonFarmLayoutConfigurationAndMigrationsAreAuthoritative(
            GameTestHelper helper
    ) {
        String suffix = UUID.randomUUID().toString();
        ResourceLocation layoutId = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "layout_" + suffix);
        ResourceLocation cabins = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "cabins_" + suffix);
        ResourceLocation standardId =
                com.stardew.craft.api.v1.internal.farm
                        .StardewFarmLayoutRegistry.builtinId(
                                FarmType.STANDARD);
        StardewFarmLayout base = StardewFarmLayouts.find(
                standardId).orElseThrow();
        StardewFarmLayout layout = new StardewFarmLayout(
                layoutId,
                true,
                Component.literal("Migration Canary"),
                Component.literal("Server-authored layout preview canary"),
                base.iconTexture(),
                base.schematic(),
                base.originY(),
                base.width(),
                base.height(),
                base.length(),
                base.spawnOffset(),
                base.spawnYaw(),
                base.greenhouseOffset(),
                base.totemOffset(),
                base.entrySouth(),
                base.entryEast(),
                base.entryWest(),
                base.biomeId(),
                base.forageZoneMin(),
                base.forageZoneMax(),
                base.caveBlackWall(),
                base.cavePortalWall(),
                base.caveClearBox(),
                base.caveExitSpawn(),
                base.caveExitYaw());
        StardewFarmLayouts.register(
                layout,
                3,
                List.of(StardewFarmLayoutConfigField.integer(
                        cabins,
                        Component.literal("Cabins"),
                        Component.empty(),
                        1,
                        0,
                        4)));

        List<String> calls = new ArrayList<>();
        StardewFarmLayoutMigrations.register(
                layoutId,
                2,
                StardewFarmLayoutMigrations.FailurePolicy.CONTINUE,
                context -> calls.add(
                        context.fromVersion() + "->"
                                + context.targetVersion() + ":"
                                + context.configuration()
                                .integerValue(cabins, -1)));
        StardewFarmLayoutMigrations.register(
                layoutId,
                3,
                StardewFarmLayoutMigrations.FailurePolicy.STOP,
                context -> {
                    calls.add("failed-3");
                    throw new IllegalStateException(
                            "expected migration retry canary");
                });

        UUID owner = UUID.randomUUID();
        FarmInstanceRegistry registry =
                FarmInstanceRegistry.get(helper.getLevel().getServer());
        FarmInstance fresh = registry.createFarm(
                owner,
                "Migration Owner",
                "Migration Farm",
                layoutId,
                Map.of(cabins, "4"));
        helper.assertValueEqual(
                fresh.getFarmLayoutVersion(),
                3,
                "new farm did not start at current layout version");
        helper.assertValueEqual(
                fresh.getFarmLayoutConfiguration().integerValue(cabins, -1),
                4,
                "server-normalized layout option was not persisted");
        helper.assertValueEqual(
                StardewFarms.find(
                                helper.getLevel().getServer(), owner)
                        .orElseThrow().farmLayoutVersion(),
                3,
                "public farm snapshot lookup lost layout metadata");

        // Simulate loading a farm saved before layout v2. Production reaches
        // this state through FarmInstance.load; reflection only avoids replacing
        // the SavedData registry's private ownership map in this GameTest.
        try {
            java.lang.reflect.Field versionField =
                    FarmInstance.class.getDeclaredField(
                            "farmLayoutVersion");
            versionField.setAccessible(true);
            versionField.setInt(fresh, 1);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Could not prepare legacy layout GameTest", exception);
        }
        StardewFarmLayoutMigrations.RunReport report =
                StardewFarmLayoutMigrations.runPending(
                        helper.getLevel(), owner);
        helper.assertValueEqual(report.attempted(), 2,
                "pending layout migrations were not attempted in order");
        helper.assertValueEqual(report.succeeded(), 1,
                "successful layout migration was not recorded");
        helper.assertValueEqual(report.failed(), 1,
                "failed layout migration was not reported");
        helper.assertTrue(report.stopped(),
                "STOP layout migration policy was ignored");
        helper.assertValueEqual(fresh.getFarmLayoutVersion(), 2,
                "failed migration incorrectly advanced the layout version");
        helper.assertValueEqual(calls, List.of("1->2:4", "failed-3"),
                "layout migration ordering or context changed");

        StardewFarmLayoutMigrations.RunReport retry =
                StardewFarmLayoutMigrations.runPending(
                        helper.getLevel(), owner);
        helper.assertValueEqual(retry.attempted(), 1,
                "successful layout migration ran twice");
        helper.assertValueEqual(retry.resultingVersion(), 2,
                "failed retry advanced layout version");

        registry.deleteFarm(owner);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void addonFarmDailyProvidersComposeAndIsolateFailures(GameTestHelper helper) {
        String suffix = UUID.randomUUID().toString();
        UUID owner = UUID.randomUUID();
        FarmInstanceRegistry registry = FarmInstanceRegistry.get(helper.getLevel().getServer());
        FarmInstance farm = registry.createFarm(
                owner, "Daily API Owner", "Daily API Farm", FarmType.STANDARD);
        farm.markInitialized();
        List<String> calls = new ArrayList<>();

        StardewFarmCaveDailyHandlers.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "cave_throwing_" + suffix),
                300,
                context -> {
                    calls.add("cave-throwing");
                    throw new IllegalStateException("expected cave handler failure");
                }
        );
        StardewFarmCaveDailyHandlers.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "cave_selected_" + suffix),
                200,
                context -> {
                    calls.add("cave-selected");
                    return StardewFarmCaveDailyHandlers.Result.SKIP_DEFAULT;
                }
        );
        StardewFarmCaveDailyHandlers.Result caveResult =
                StardewFarmCaveDailyRegistry.runHandlers(
                        new StardewFarmCaveDailyHandlers.Context(
                                helper.getLevel(),
                                StardewFarmSnapshots.from(farm),
                                helper.absolutePos(BlockPos.ZERO),
                                helper.getLevel().getRandom()
                        ));
        helper.assertValueEqual(
                caveResult,
                StardewFarmCaveDailyHandlers.Result.SKIP_DEFAULT,
                "addon cave handler could not replace default processing"
        );

        StardewFarmCaveDailyHandlers.registerFruitProvider(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "cave_fruit_" + suffix),
                100,
                () -> List.of(Blocks.DIAMOND_BLOCK),
                context -> Blocks.DIAMOND_BLOCK
        );
        var resolvedFruit = StardewFarmCaveDailyRegistry.resolveFruit(
                new StardewFarmCaveDailyHandlers.FruitContext(
                        helper.getLevel(),
                        StardewFarmSnapshots.from(farm),
                        helper.absolutePos(BlockPos.ZERO),
                        helper.getLevel().getRandom(),
                        Blocks.OAK_SAPLING
                ));
        helper.assertValueEqual(
                resolvedFruit,
                Blocks.DIAMOND_BLOCK,
                "addon fruit provider did not replace the default fruit"
        );
        helper.assertTrue(
                StardewFarmCaveDailyRegistry.managedFruitBlocks().contains(Blocks.DIAMOND_BLOCK),
                "addon fruit was not included in cave cleanup"
        );

        StardewFarmDebrisPlacements.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "debris_" + suffix),
                100,
                context -> Blocks.GOLD_BLOCK.defaultBlockState()
        );
        var resolvedDebris = StardewFarmDebrisPlacementRegistry.resolve(
                new StardewFarmDebrisPlacements.Context(
                        helper.getLevel(),
                        StardewFarmSnapshots.from(farm),
                        helper.absolutePos(new BlockPos(1, 1, 1)),
                        Blocks.OAK_SAPLING.defaultBlockState(),
                        StardewFarmDebrisPlacements.Stage.YOUNG_TREE,
                        helper.getLevel().getRandom()
                ));
        helper.assertTrue(
                resolvedDebris.is(Blocks.GOLD_BLOCK),
                "addon debris provider did not replace the proposed state"
        );

        StardewFarmDailyTasks.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "daily_throwing_" + suffix),
                300,
                context -> {
                    calls.add("daily-throwing");
                    throw new IllegalStateException("expected daily task failure");
                }
        );
        StardewFarmDailyTasks.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "daily_after_" + suffix),
                200,
                context -> calls.add("daily-after")
        );
        StardewFarmDailyTaskRegistry.run(new StardewFarmDailyTasks.Context(
                helper.getLevel(),
                StardewFarmSnapshots.from(farm),
                42,
                1,
                14,
                helper.getLevel().getRandom()
        ));
        helper.assertValueEqual(
                calls,
                List.of("cave-throwing", "cave-selected", "daily-throwing", "daily-after"),
                "farm daily providers were not ordered or isolated"
        );

        registry.deleteFarm(owner);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void addonSpecialMachineResolversDriveRuntimeServices(GameTestHelper helper) {
        String suffix = UUID.randomUUID().toString();
        ResourceLocation machineId = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "machine_" + suffix);
        StardewMachineTypes.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "machine_registration_" + suffix),
                new StardewMachineType(
                        machineId,
                        ResourceLocation.withDefaultNamespace("barrel"),
                        "jei.stardewcraft_gametest.machine",
                        StardewMachineType.Layout.STANDARD,
                        true,
                        List.of()
                )
        );
        StardewArtisanResolvers.registerCaskAgingRate(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "cask_rate_" + suffix),
                100,
                input -> input.is(Items.BRICK) ? 2.0F : null
        );
        StardewArtisanResolvers.registerSmokedOutput(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "smoked_output_" + suffix),
                100,
                input -> input.is(Items.COD)
                        ? new ItemStack(Items.DIAMOND)
                        : ItemStack.EMPTY
        );
        StardewArtisanResolvers.registerSeedMakerOutput(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "seed_output_" + suffix),
                100,
                input -> input == Items.APPLE
                        ? StardewArtisanResolvers.SeedResult.output(Items.MELON_SEEDS)
                        : StardewArtisanResolvers.SeedResult.pass()
        );

        ItemStack caskInput = new ItemStack(Items.BRICK);
        CaskBlockEntity cask = new CaskBlockEntity(
                helper.absolutePos(new BlockPos(1, 1, 1)),
                ModBlocks.CASK.get().defaultBlockState()
        );
        helper.assertTrue(cask.tryInsert(caskInput, null),
                "addon cask rate did not reach CaskBlockEntity");
        helper.assertTrue(SmokedOutputResolver.resolve(new ItemStack(Items.COD)).is(Items.DIAMOND),
                "addon smoked output did not reach shared gameplay/JEI resolver");
        helper.assertValueEqual(SeedMakerOutputResolver.resolve(Items.APPLE), Items.MELON_SEEDS,
                "addon seed output did not reach runtime resolver");
        helper.assertTrue(MachineJeiRegistry.find(machineId).isPresent(),
                "addon machine descriptor did not reach the JEI registry adapter");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void addonTreeAdapterDrivesGrowthFertilizerAndTapper(GameTestHelper helper) {
        String suffix = UUID.randomUUID().toString();
        ResourceLocation treeId = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "tree_" + suffix);
        int[] growthCalls = {0};
        int[] fertilizerCalls = {0};
        StardewTreeTypes.register(
                new StardewTreeType(
                        treeId,
                        StardewTreeType.Kind.WILD,
                        "block.stardewcraft_gametest.tree",
                        18,
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
                        if (!level.getBlockState(position).is(Blocks.BIRCH_LOG)) {
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
                        growthCalls[0]++;
                        return true;
                    }

                    @Override
                    public FertilizerResult fertilize(
                            net.minecraft.server.level.ServerLevel level,
                            StardewTreeState tree
                    ) {
                        fertilizerCalls[0]++;
                        return FertilizerResult.APPLIED;
                    }

                    @Override
                    public TapperCycle resolveTapperCycle(
                            net.minecraft.server.level.ServerLevel level,
                            StardewTreeState tree,
                            BlockPos supportPosition
                    ) {
                        return new TapperCycle(new ItemStack(Items.DIAMOND), 2);
                    }
                }
        );

        BlockPos treePos = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.getLevel().setBlock(treePos, Blocks.BIRCH_LOG.defaultBlockState(), 3);
        helper.assertValueEqual(
                StardewTreeRuntime.inspect(helper.getLevel(), treePos).typeId(),
                treeId,
                "addon tree was not recognized"
        );
        helper.assertTrue(StardewTreeRuntime.growOneDay(helper.getLevel(), treePos),
                "addon tree growth adapter did not handle the request");
        helper.assertValueEqual(growthCalls[0], 1, "addon tree grew more than once");
        helper.assertValueEqual(
                StardewTreeRuntime.fertilize(helper.getLevel(), treePos),
                StardewTreeRuntimeAdapter.FertilizerResult.APPLIED,
                "addon fertilizer adapter did not handle the request"
        );
        helper.assertValueEqual(fertilizerCalls[0], 1, "addon fertilizer ran more than once");

        BlockPos plantingPos = treePos.west(2);
        helper.getLevel().setBlock(plantingPos.below(), Blocks.DIRT.defaultBlockState(), 3);
        helper.getLevel().setBlock(plantingPos, Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(plantingPos.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.assertFalse(FruitTreeRules.canPlantSapling(helper.getLevel(), plantingPos),
                "addon tree was ignored by fruit-tree spacing rules");

        BlockPos tapperPos = treePos.east();
        helper.getLevel().setBlock(
                tapperPos,
                ModBlocks.TAPPER.get().defaultBlockState()
                        .setValue(TapperBlock.FACING, net.minecraft.core.Direction.WEST),
                3
        );
        helper.assertTrue(helper.getLevel().getBlockEntity(tapperPos)
                        instanceof TapperBlockEntity,
                "tapper block entity was not created");
        TapperBlockEntity tapper =
                (TapperBlockEntity) helper.getLevel().getBlockEntity(tapperPos);
        tapper.ensureCycleStarted(helper.getLevel().getBlockState(tapperPos));
        helper.assertTrue(tapper.getProduct().is(Items.DIAMOND),
                "addon tapper cycle did not reach the block entity");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void addonCropAdapterDrivesTrackedDailyGrowth(GameTestHelper helper) {
        String suffix = UUID.randomUUID().toString();
        ResourceLocation cropId = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "crop_" + suffix);
        ResourceLocation failingId = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "failing_crop_" + suffix);
        AtomicInteger growthCalls = new AtomicInteger();
        AtomicInteger harvestCalls = new AtomicInteger();
        StardewCropDailyContext[] receivedContext = {null};
        BlockPos cropPos = helper.absolutePos(new BlockPos(7, 2, 7));
        BlockPos removalPos = cropPos.east(2);

        StardewCropTypes.register(
                new StardewCropType(
                        failingId,
                        "block.stardewcraft_gametest.failing_crop",
                        1,
                        List.of(ResourceLocation.withDefaultNamespace("gold_block")),
                        null),
                200,
                (level, position) -> {
                    throw new IllegalStateException("intentional crop adapter failure");
                }
        );
        StardewCropTypes.register(
                new StardewCropType(
                        cropId,
                        "block.stardewcraft_gametest.crop",
                        4,
                        List.of(ResourceLocation.withDefaultNamespace("gold_block")),
                        new com.stardew.craft.api.v1.agriculture.StardewCropData(
                                List.of("spring"),
                                List.of(1, 1, 1),
                                -1,
                                7,
                                ResourceLocation.fromNamespaceAndPath(
                                        "stardewcraft", "grab"),
                                ResourceLocation.withDefaultNamespace("diamond"),
                                ResourceLocation.withDefaultNamespace("gold_ingot"))),
                100,
                new StardewCropRuntimeAdapter() {
                    @Override
                    public StardewCropState inspect(
                            net.minecraft.world.level.LevelReader level,
                            BlockPos position
                    ) {
                        if ((!position.equals(cropPos)
                                && !position.equals(removalPos))
                                || !level.getBlockState(position).is(Blocks.GOLD_BLOCK)) {
                            return null;
                        }
                        int stage = Math.min(3, growthCalls.get());
                        return new StardewCropState(
                                cropId,
                                position,
                                StardewCropState.Part.ROOT,
                                stage,
                                stage == 3,
                                List.of(position.below())
                        );
                    }

                    @Override
                    public DailyResult growOneDay(
                            net.minecraft.server.level.ServerLevel level,
                            StardewCropState crop,
                            StardewCropDailyContext context
                    ) {
                        growthCalls.incrementAndGet();
                        receivedContext[0] = context;
                        return DailyResult.CHANGED;
                    }

                    @Override
                    public StardewCropHarvestResult harvest(
                            net.minecraft.server.level.ServerLevel level,
                            StardewCropState crop,
                            StardewCropHarvestContext context,
                            java.util.function.Consumer<ItemStack> output
                    ) {
                        int attempt = harvestCalls.incrementAndGet();
                        output.accept(attempt == 1
                                ? new ItemStack(Items.DIRT)
                                : new ItemStack(Items.DIAMOND, 2));
                        if (attempt == 1) {
                            return StardewCropHarvestResult.notReady();
                        }
                        level.setBlock(crop.root(), Blocks.AIR.defaultBlockState(), 3);
                        return StardewCropHarvestResult.harvested(7);
                    }

                    @Override
                    public boolean remove(
                            net.minecraft.server.level.ServerLevel level,
                            StardewCropState crop,
                            StardewCropRemovalCause cause
                    ) {
                        if (cause != StardewCropRemovalCause.CROW) {
                            return false;
                        }
                        level.setBlock(crop.root(), Blocks.AIR.defaultBlockState(), 3);
                        return true;
                    }
                }
        );

        helper.getLevel().setBlock(cropPos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        StardewCropRuntime.track(helper.getLevel(), cropPos);
        helper.assertTrue(
                CropGrowthManager.get(helper.getLevel()).getAllCropPositions().stream()
                        .anyMatch(value -> value.pos().equals(cropPos)),
                "addon crop was not added to the persistent daily scheduler"
        );
        helper.assertValueEqual(
                StardewCropRuntime.inspect(helper.getLevel(), cropPos).typeId(),
                cropId,
                "addon crop inspection did not isolate a failing higher-priority adapter"
        );
        helper.assertTrue(StardewCropRuntime.isRegisteredBlock(
                        helper.getLevel().getBlockState(cropPos)),
                "addon crop block was absent from the fast display/tool index");
        helper.assertTrue(StardewAcquisitionSources.find(
                        new ItemStack(Items.DIAMOND)).stream()
                        .anyMatch(source ->
                                source.kind()
                                        == StardewAcquisitionSource.Kind.FARMING
                                        && source.sourceId().equals(cropId)),
                "addon crop descriptor did not reach acquisition sources");
        helper.assertValueEqual(
                StardewCropRuntime.growOneDay(
                        helper.getLevel(), cropPos, true, true),
                StardewCropRuntimeAdapter.DailyResult.CHANGED,
                "addon crop daily adapter did not handle the request"
        );
        helper.assertValueEqual(growthCalls.get(), 1,
                "addon crop daily adapter ran more than once");
        helper.assertTrue(receivedContext[0] != null
                        && receivedContext[0].watered()
                        && receivedContext[0].offlineCatchUp(),
                "addon crop did not receive authoritative daily context");

        List<ItemStack> automatedOutput = new ArrayList<>();
        helper.assertValueEqual(
                StardewCropRuntime.harvestForAutomation(
                        helper.getLevel(), cropPos, 4, automatedOutput::add),
                StardewCropHarvestResult.notReady(),
                "non-successful addon harvest did not preserve its result"
        );
        helper.assertTrue(automatedOutput.isEmpty(),
                "staged outputs leaked from an unsuccessful addon harvest");
        helper.assertValueEqual(
                StardewCropRuntime.harvestForAutomation(
                        helper.getLevel(), cropPos, 4, automatedOutput::add),
                StardewCropHarvestResult.harvested(7),
                "addon automation harvest did not commit"
        );
        helper.assertTrue(automatedOutput.size() == 1
                        && automatedOutput.getFirst().is(Items.DIAMOND)
                        && automatedOutput.getFirst().getCount() == 2,
                "addon automation harvest did not route copied outputs");
        helper.assertValueEqual(
                StardewCropRuntime.growOneDay(
                        helper.getLevel(), cropPos, false, false),
                StardewCropRuntimeAdapter.DailyResult.REMOVED,
                "revalidation did not reject a replaced addon crop"
        );
        helper.getLevel().setBlock(removalPos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        StardewCropRuntime.track(helper.getLevel(), removalPos);
        helper.assertTrue(
                StardewCropRuntime.remove(
                        helper.getLevel(),
                        removalPos,
                        StardewCropRemovalCause.CROW),
                "addon crop did not handle a non-harvest removal cause"
        );
        helper.assertTrue(helper.getLevel().getBlockState(removalPos).isAir(),
                "addon crop removal did not commit its world mutation");
        helper.assertFalse(
                CropGrowthManager.get(helper.getLevel()).getAllCropPositions().stream()
                        .anyMatch(value -> value.pos().equals(removalPos)),
                "removed addon crop remained in the daily scheduler"
        );
        StardewCropRuntime.untrack(helper.getLevel(), cropPos);
        helper.assertFalse(
                CropGrowthManager.get(helper.getLevel()).getAllCropPositions().stream()
                        .anyMatch(value -> value.pos().equals(cropPos)),
                "addon crop remained in the persistent daily scheduler after untrack"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void communityCenterProgressSnapshotIsDeeplyDetached(GameTestHelper helper) {
        BundleDefinition definition = BundleDataManager.getAllBundles().stream()
                .filter(bundle -> bundle.totalSlots() > 0)
                .findFirst()
                .orElseThrow();
        UUID playerId = UUID.randomUUID();
        CommunityCenterSavedData data = CommunityCenterSavedData.get(helper.getLevel());
        boolean[] liveSlots = data.getSlots(playerId, definition.bundleId());
        liveSlots[0] = true;
        data.setRewardAvailable(playerId, definition.bundleId(), true);

        StardewCommunityCenterProgress.Snapshot snapshot =
                StardewCommunityCenterProgress.snapshot(helper.getLevel(), playerId);
        StardewCommunityCenterProgress.BundleProgress progress = snapshot.bundles().stream()
                .filter(bundle -> bundle.bundleId() == definition.bundleId())
                .findFirst()
                .orElseThrow();
        liveSlots[0] = false;
        data.setRewardAvailable(playerId, definition.bundleId(), false);

        helper.assertTrue(progress.slots().getFirst(),
                "progress snapshot leaked the mutable slot array");
        helper.assertTrue(progress.rewardAvailable(),
                "progress snapshot changed with live reward state");
        helper.assertValueEqual(progress.filledSlots(), 1,
                "progress snapshot filled-slot count changed");
        data.resetAll(playerId);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void communityCenterVariantRewardAndPersistentStateCompose(GameTestHelper helper) {
        UUID playerId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString();
        ResourceLocation variantId = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "hard_catalog_" + suffix);
        StardewCommunityCenterVariants.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "variant_provider_" + suffix),
                100,
                (context, current) -> {
                    if (!context.playerId().equals(playerId)) {
                        return null;
                    }
                    StardewBundleDefinition selected = current.definitions().stream()
                            .filter(definition -> definition.totalSlots() > 1)
                            .findFirst()
                            .orElseThrow();
                    int replacementRequired = selected.requiredCount() == 1 ? 2 : 1;
                    StardewBundleDefinition replacement = new StardewBundleDefinition(
                            selected.bundleId(),
                            selected.areaId(),
                            selected.internalName(),
                            selected.displayNameKey(),
                            selected.rewardDescriptor(),
                            selected.ingredients(),
                            selected.color(),
                            replacementRequired
                    );
                    List<StardewBundleDefinition> definitions =
                            new ArrayList<>(current.definitions());
                    definitions.set(definitions.indexOf(selected), replacement);
                    return current.withVariant(variantId, definitions);
                }
        );

        StardewCommunityCenterVariants.Catalog catalog =
                StardewCommunityCenterVariants.catalog(
                        helper.getLevel().getServer(), playerId);
        helper.assertTrue(catalog.variantIds().contains(variantId),
                "player-specific bundle variant was not applied");

        StardewCommunityCenterPersistentData.Key key =
                StardewCommunityCenterPersistentData.register(
                        ResourceLocation.fromNamespaceAndPath(
                                "stardewcraft_gametest", "cc_state_" + suffix),
                        2
                );
        CompoundTag source = new CompoundTag();
        source.putString("difficulty", "hard");
        StardewCommunityCenterPersistentData.write(
                helper.getLevel(), playerId, key, source);
        source.putString("difficulty", "mutated");
        StardewCommunityCenterPersistentData.Value stored =
                StardewCommunityCenterPersistentData.read(
                        helper.getLevel(), playerId, key).orElseThrow();
        helper.assertValueEqual(stored.storedVersion(), 2,
                "Community Center addon-state version was lost");
        helper.assertValueEqual(stored.payload().getString("difficulty"), "hard",
                "Community Center addon state leaked its source tag");
        CommunityCenterSavedData liveData =
                CommunityCenterSavedData.get(helper.getLevel());
        CompoundTag serialized = liveData.save(
                new CompoundTag(), helper.getLevel().registryAccess());
        CommunityCenterSavedData restored = CommunityCenterSavedData.load(
                serialized, helper.getLevel().registryAccess());
        helper.assertValueEqual(
                restored.getAddonData(playerId, key.id().toString())
                        .getCompound("payload").getString("difficulty"),
                "hard",
                "Community Center addon state did not survive save/load"
        );

        StardewCommunityCenterRewards.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "reward_" + suffix),
                100,
                context -> context.bundleId() == 987654
                        ? new ItemStack(Items.DIAMOND)
                        : null
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void npcSocialAndEntityProvidersCompose(GameTestHelper helper) {
        String suffix = UUID.randomUUID().toString();
        ResourceLocation npcId = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "archivist_" + suffix);
        StardewNpcProfiles.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "profile_" + suffix),
                100,
                new StardewNpcDefinition(
                        npcId,
                        new StardewNpcProfile(
                                npcId, true, false, "idle_only",
                                0, 0, 0, 0, 0, false),
                        new StardewNpcDisplay(
                                npcId,
                                "entity.stardewcraft_gametest.npc.archivist",
                                ResourceLocation.fromNamespaceAndPath(
                                        "stardewcraft_gametest",
                                        "textures/portraits/archivist.png"),
                                128,
                                320,
                                ResourceLocation.fromNamespaceAndPath(
                                        "stardewcraft_gametest",
                                        "textures/mugshots/archivist.png"),
                                16,
                                24,
                                "stardewcraft_gametest.relationship.archivist",
                                false))
        );
        helper.assertTrue(StardewNpcProfiles.ids().contains(npcId),
                "addon NPC profile was not enumerable");
        helper.assertTrue(StardewNpcProfiles.resolve(npcId).isPresent(),
                "addon NPC profile was not resolvable");
        helper.assertTrue(StardewNpcContents.inspect(npcId).hasProfile(),
                "addon NPC profile was absent from the content projection");
        int[] socialCalls = {0};
        StardewNpcSocialRules.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "social_throwing_" + suffix),
                200,
                (context, rule, proposed) -> {
                    if (!context.npcId().equals(npcId)) {
                        return StardewNpcSocialRules.Decision.PASS;
                    }
                    throw new IllegalStateException("expected social provider failure");
                }
        );
        StardewNpcSocialRules.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "social_allow_" + suffix),
                100,
                (context, rule, proposed) -> {
                    if (!context.npcId().equals(npcId)) {
                        return StardewNpcSocialRules.Decision.PASS;
                    }
                    socialCalls[0]++;
                    return StardewNpcSocialRules.Decision.ALLOW;
                }
        );
        helper.assertTrue(StardewNpcSocialRuleRegistry.evaluate(
                        new StardewNpcSocialContext(npcId, null, null, null),
                        StardewNpcSocialRules.Rule.CAN_SOCIALIZE,
                        false),
                "lower-priority social provider did not survive an earlier failure");
        helper.assertValueEqual(socialCalls[0], 1,
                "social provider ran an unexpected number of times");

        net.minecraft.world.entity.decoration.ArmorStand marker =
                EntityType.ARMOR_STAND.create(helper.getLevel());
        helper.assertTrue(marker != null, "failed to create NPC resolver marker");
        marker.moveTo(helper.absolutePos(new BlockPos(2, 2, 2)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(marker);
        int[] lifecycleCounts = {0, 0};
        StardewNpcLifecycleEvents.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "npc_lifecycle_throwing_" + suffix),
                200,
                event -> {
                    if (event.npcId().equals(npcId)) {
                        throw new IllegalStateException(
                                "expected NPC lifecycle listener failure");
                    }
                });
        StardewNpcLifecycleEvents.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "npc_lifecycle_observer_" + suffix),
                100,
                event -> {
                    if (!event.npcId().equals(npcId)) {
                        return;
                    }
                    if (event.phase() == StardewNpcLifecyclePhase.SPAWNED) {
                        lifecycleCounts[0]++;
                    } else if (event.phase()
                            == StardewNpcLifecyclePhase.REMOVED) {
                        lifecycleCounts[1]++;
                    }
                });
        StardewNpcLifecycleEvents.announceSpawned(
                npcId, marker, "gametest_spawn");
        StardewNpcEntities.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "entity_" + suffix),
                100,
                context -> context.npcId().equals(npcId) ? marker : null
        );
        helper.assertTrue(StardewNpcEntities.resolve(helper.getLevel(), npcId)
                        .orElse(null) == marker,
                "addon-owned NPC entity was not resolved");
        helper.assertTrue(StardewNpcContents.inspect(
                        helper.getLevel(), npcId).entityPresent(),
                "NPC runtime diagnostics did not see the resolved addon entity");

        StardewNpcFriendshipRewards.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "friendship_reward_" + suffix),
                100,
                context -> context.npcId().equals(npcId) && context.points() >= 500
                        ? StardewNpcFriendshipRewards.Outcome.COMPLETE
                        : StardewNpcFriendshipRewards.Outcome.PASS
        );
        StardewNpcLifecycleEvents.announceRemoved(
                npcId, marker, "gametest_remove");
        marker.discard();
        helper.assertValueEqual(lifecycleCounts[0], 1,
                "NPC spawn lifecycle event was not isolated and observed");
        helper.assertValueEqual(lifecycleCounts[1], 1,
                "NPC removal lifecycle event was not isolated and observed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void festivalSessionLifecycleEventsComposeAndIsolateFailures(
            GameTestHelper helper
    ) {
        String suffix = UUID.randomUUID().toString();
        ResourceLocation festivalId = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "apple_day_" + suffix);
        int[] calls = {0, 0, 0, 0};
        List<StardewProgressEvent> progressEvents = new ArrayList<>();
        StardewProgressEvents.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "festival_progress_" + suffix),
                100,
                event -> {
                    if (event.after().key().equals(
                            StardewProgress.festival(festivalId))) {
                        progressEvents.add(event);
                    }
                });
        StardewFestivalSessionEvents.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "festival_session_throwing_" + suffix),
                200,
                event -> {
                    if (event.session().festivalId().equals(festivalId)) {
                        throw new IllegalStateException(
                                "expected festival session listener failure");
                    }
                });
        StardewFestivalSessionEvents.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "festival_session_observer_" + suffix),
                100,
                event -> {
                    if (!event.session().festivalId().equals(festivalId)) {
                        return;
                    }
                    switch (event.type()) {
                        case PHASE_CHANGED -> {
                            calls[0]++;
                            helper.assertTrue(
                                    event.previousPhase().orElseThrow()
                                            == com.stardew.craft.api.v1.festival
                                                .StardewFestivalSessionSnapshot
                                                .Phase.SCHEDULED,
                                    "festival previous phase was incorrect");
                        }
                        case MAP_PHASE_CHANGED -> calls[1]++;
                        case PARTICIPANT_JOINED -> calls[2]++;
                        case PARTICIPANT_LEFT -> calls[3]++;
                    }
                });

        FestivalSessionState state = new FestivalSessionState(
                festivalId.toString(), 2, 1, 12);
        state.attachLevel(helper.getLevel());
        state.setPhase(FestivalSessionPhase.OPEN);
        state.setPhase(FestivalSessionPhase.OPEN);
        state.setMapOverlayPhase(FestivalMapOverlayPhase.APPLIED);
        UUID participant = UUID.randomUUID();
        state.addParticipant(participant);
        state.addParticipant(participant);
        state.removeParticipant(participant);
        state.removeParticipant(participant);

        helper.assertValueEqual(calls[0], 1,
                "festival phase event was duplicated or lost");
        helper.assertValueEqual(calls[1], 1,
                "festival map phase event was duplicated or lost");
        helper.assertValueEqual(calls[2], 1,
                "festival participant event was duplicated or lost");
        helper.assertValueEqual(calls[3], 1,
                "festival participant leave event was duplicated or lost");
        helper.assertValueEqual(progressEvents.size(), 1,
                "festival phase did not publish one unified progress event");
        helper.assertValueEqual(
                progressEvents.getFirst().type(),
                StardewProgressEventType.MADE_AVAILABLE,
                "festival OPEN phase did not project MADE_AVAILABLE");
        helper.assertTrue(
                progressEvents.getFirst().actor().isEmpty(),
                "world festival phase incorrectly attributed a player actor");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void festivalMechanicsComposeCapabilitiesAndLifecycle(
            GameTestHelper helper
    ) {
        String suffix = UUID.randomUUID().toString();
        ResourceLocation festivalId = ResourceLocation
                .fromNamespaceAndPath("stardewcraft", "spring13");
        ResourceLocation mechanicId = ResourceLocation
                .fromNamespaceAndPath("stardewcraft", "egg_hunt");
        int[] calls = {0};
        StardewFestivalMechanics.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "festival_mechanic_throwing_" + suffix),
                200,
                mechanicId,
                Set.of(StardewFestivalMechanicCapability.REWARDS),
                new StardewFestivalMechanicHandler() {
                    @Override
                    public void onSessionChanged(
                            com.stardew.craft.api.v1.festival
                                    .StardewFestivalMechanicContext context,
                            com.stardew.craft.api.v1.festival
                                    .StardewFestivalSessionEvent event
                    ) {
                        throw new IllegalStateException(
                                "expected mechanic contribution failure");
                    }
                });
        StardewFestivalMechanics.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "festival_mechanic_observer_" + suffix),
                100,
                mechanicId,
                Set.of(
                        StardewFestivalMechanicCapability.REWARDS,
                        StardewFestivalMechanicCapability.CURRENCY),
                new StardewFestivalMechanicHandler() {
                    @Override
                    public void onSessionChanged(
                            com.stardew.craft.api.v1.festival
                                    .StardewFestivalMechanicContext context,
                            com.stardew.craft.api.v1.festival
                                    .StardewFestivalSessionEvent event
                    ) {
                        calls[0]++;
                    }
                });

        var diagnostic = StardewFestivalMechanics.inspect(festivalId)
                .orElseThrow();
        helper.assertTrue(diagnostic.capabilities().contains(
                        StardewFestivalMechanicCapability.REWARDS),
                "festival mechanic reward capability was not composed");
        helper.assertTrue(diagnostic.capabilities().contains(
                        StardewFestivalMechanicCapability.CURRENCY),
                "festival mechanic currency capability was not composed");
        helper.assertTrue(diagnostic.contributions().stream()
                        .anyMatch(registration -> registration.id()
                                .getPath().equals(
                                        "festival_mechanic_observer_"
                                                + suffix)),
                "festival mechanic contribution was absent from diagnostics");

        FestivalSessionState state = new FestivalSessionState(
                "spring13", 2, 0, 13);
        state.attachLevel(helper.getLevel());
        state.setPhase(FestivalSessionPhase.OPEN);
        helper.assertValueEqual(calls[0], 1,
                "festival mechanic failure was not isolated");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void festivalShopDirectoryPreservesCanonicalAndLegacyIdentity(
            GameTestHelper helper
    ) {
        ResourceLocation festivalId = ResourceLocation
                .fromNamespaceAndPath("stardewcraft", "spring13");
        ResourceLocation shopId = ResourceLocation
                .fromNamespaceAndPath(
                        "stardewcraft",
                        "festival_egg_festival_pierre");
        var shops = StardewFestivalShops.list(festivalId);
        helper.assertValueEqual(shops.size(), 1,
                "egg festival shop was absent from the public directory");
        helper.assertValueEqual(shops.getFirst().shopId(), shopId,
                "legacy festival shop did not resolve to its canonical ID");
        helper.assertValueEqual(
                shops.getFirst().runtimeShopId(),
                "Festival_EggFestival_Pierre",
                "festival shop lost its purchase-compatible runtime ID");

        var definition = FestivalRegistry.get(festivalId).orElseThrow();
        FestivalSessionState session = FestivalWorldData
                .get(helper.getLevel())
                .getOrCreateSession(definition, 3, 0, 13);
        session.setPhase(FestivalSessionPhase.OPEN);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "Festival Shop API"),
                ClientInformation.createDefault());
        var result = StardewFestivalShops.open(
                player, festivalId, shopId);
        helper.assertValueEqual(
                result.status(),
                StardewFestivalShopOpenResult.Status
                        .PARTICIPATION_REQUIRED,
                "active festival shop bypassed participant validation");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void festivalRewardClaimsAreOrderedIsolatedAndIdempotent(
            GameTestHelper helper
    ) {
        String suffix = UUID.randomUUID().toString();
        ResourceLocation festivalId = ResourceLocation
                .fromNamespaceAndPath("stardewcraft", "spring13");
        ResourceLocation rewardId = ResourceLocation
                .fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "orchard_prize_" + suffix);
        int[] grants = {0};
        StardewFestivalRewards.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "reward_throwing_" + suffix),
                200,
                context -> {
                    if (context.rewardId().equals(rewardId)) {
                        throw new IllegalStateException(
                                "expected reward prepare failure");
                    }
                    return StardewFestivalRewardPreparation.pass();
                });
        StardewFestivalRewards.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "reward_grant_" + suffix),
                100,
                context -> context.rewardId().equals(rewardId)
                        ? StardewFestivalRewardPreparation.accept(
                                grantContext -> {
                                    grants[0]++;
                                    return true;
                                })
                        : StardewFestivalRewardPreparation.pass());
        StardewFestivalRewards.registerDescriptor(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "reward_descriptor_" + suffix),
                100,
                new StardewFestivalRewardDescriptor(
                        festivalId,
                        rewardId,
                        List.of(new StardewRewardComponent(
                                StardewRewardComponent.Kind.ITEM,
                                ResourceLocation.withDefaultNamespace(
                                        "apple"),
                                1,
                                new ItemStack(Items.APPLE),
                                Items.APPLE.getDescription(),
                                false)),
                        true));

        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "Festival Reward API"),
                ClientInformation.createDefault());
        var definition = FestivalRegistry.get(festivalId).orElseThrow();
        FestivalSessionState session = FestivalWorldData
                .get(helper.getLevel())
                .getOrCreateSession(definition, 4, 0, 13);
        session.setPhase(FestivalSessionPhase.OPEN);
        session.addParticipant(player.getUUID());
        StardewProgressKey rewardProgressKey =
                StardewFestivalRewards.progressKey(
                        festivalId, rewardId);
        List<StardewProgressEvent> rewardProgressEvents =
                new ArrayList<>();
        StardewProgressEvents.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "festival_reward_progress_" + suffix),
                100,
                event -> {
                    if (event.after().key().equals(
                            rewardProgressKey)) {
                        rewardProgressEvents.add(event);
                    }
                });
        helper.assertTrue(
                StardewFestivalRewards.catalog(festivalId)
                        .stream().anyMatch(descriptor ->
                                descriptor.rewardId().equals(
                                        rewardId)),
                "festival reward descriptor was not discoverable");
        helper.assertValueEqual(
                StardewProgress.inspect(
                        player, rewardProgressKey).phase(),
                StardewProgressPhase.AVAILABLE,
                "unclaimed festival reward did not project AVAILABLE");
        helper.assertTrue(
                StardewRewardPreviews.preview(
                        player, rewardProgressKey)
                        .components().stream()
                        .anyMatch(component ->
                                component.icon().is(Items.APPLE)),
                "festival reward descriptor did not reach reward preview");
        var rewardRequirementsBefore =
                StardewFestivalRewards.requirements(
                        player, festivalId, rewardId);
        helper.assertTrue(
                rewardRequirementsBefore.satisfied(),
                "festival reward preflight reported a false blocker");
        StardewProgressSnapshot festivalProgress =
                StardewProgress.inspect(
                        player, StardewProgress.festival(festivalId));
        helper.assertTrue(
                festivalProgress != null,
                "festival session was absent from unified progress");
        helper.assertValueEqual(
                festivalProgress.phase(),
                StardewProgressPhase.AVAILABLE,
                "open festival did not project AVAILABLE");
        helper.assertTrue(
                StardewProgress.list(
                        player, StardewProgressDomains.FESTIVAL)
                        .stream().anyMatch(snapshot ->
                                snapshot.key().equals(
                                        StardewProgress.festival(
                                                festivalId))),
                "festival domain enumeration omitted a known festival");
        StardewProgressKey eggHuntProgress =
                StardewFestivalActivities.progressKey(
                        festivalId,
                        ResourceLocation.fromNamespaceAndPath(
                                StardewCraft.MODID, "egg_hunt"));
        helper.assertTrue(
                StardewProgress.inspect(
                        player, eggHuntProgress) != null,
                "built-in festival activity progress was absent");
        helper.assertTrue(
                StardewProgress.list(
                        player,
                        StardewFestivalActivities.progressDomain(
                                festivalId))
                        .stream().anyMatch(snapshot ->
                                snapshot.key().equals(
                                        eggHuntProgress)),
                "festival activity domain omitted the egg hunt");

        var first = StardewFestivalRewards.claim(
                player, festivalId, rewardId);
        var duplicate = StardewFestivalRewards.claim(
                player, festivalId, rewardId);
        helper.assertValueEqual(
                first.status(),
                StardewFestivalRewardClaimResult.Status.CLAIMED,
                "festival reward was not granted after provider isolation");
        helper.assertValueEqual(
                duplicate.status(),
                StardewFestivalRewardClaimResult.Status.ALREADY_CLAIMED,
                "festival reward duplicate claim was not rejected");
        helper.assertValueEqual(grants[0], 1,
                "festival reward grant ran more than once");
        helper.assertTrue(StardewFestivalRewards.hasClaimed(
                        player, festivalId, rewardId),
                "festival reward claim was not persisted in the session");
        helper.assertValueEqual(
                StardewProgress.inspect(
                        player, rewardProgressKey).phase(),
                StardewProgressPhase.COMPLETED,
                "claimed festival reward did not project COMPLETED");
        var rewardRequirementsAfter =
                StardewFestivalRewards.requirements(
                        player, festivalId, rewardId);
        helper.assertTrue(
                rewardRequirementsAfter
                        .blocking().stream().anyMatch(requirement ->
                                requirement.type().equals(
                                        StardewRequirementTypes
                                                .FESTIVAL_REWARD_UNCLAIMED)),
                "festival reward preflight omitted the claimed blocker");
        helper.assertTrue(
                rewardProgressEvents.stream().anyMatch(event ->
                        event.type()
                                == StardewProgressEventType
                                        .REWARD_CLAIMED),
                "festival reward claim did not publish unified progress");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void festivalActivitiesAreOrderedAndAuthorityChecked(
            GameTestHelper helper
    ) {
        String suffix = UUID.randomUUID().toString();
        ResourceLocation festivalId = ResourceLocation
                .fromNamespaceAndPath("stardewcraft", "spring13");
        ResourceLocation mechanicId = ResourceLocation
                .fromNamespaceAndPath("stardewcraft", "egg_hunt");
        ResourceLocation activityId = ResourceLocation
                .fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "apple_toss_" + suffix);
        List<String> calls = new ArrayList<>();
        StardewFestivalActivities.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "activity_low_" + suffix),
                0,
                mechanicId,
                activityId,
                context -> {
                    calls.add("low");
                    return StardewFestivalActivityDecision.STARTED;
                });
        StardewFestivalActivities.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "activity_high_" + suffix),
                100,
                mechanicId,
                activityId,
                context -> {
                    calls.add("high");
                    return StardewFestivalActivityDecision.PASS;
                });
        var registrations = StardewFestivalActivities.registrations(
                mechanicId).stream()
                .filter(registration -> registration.activityId()
                        .equals(activityId))
                .toList();
        helper.assertValueEqual(
                registrations.stream()
                        .map(registration -> registration.id().getPath())
                        .toList(),
                List.of(
                        "activity_high_" + suffix,
                        "activity_low_" + suffix),
                "festival activities were not discoverable in priority order");

        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "Festival Activity API"),
                ClientInformation.createDefault());
        var definition = FestivalRegistry.get(festivalId).orElseThrow();
        FestivalSessionState session = FestivalWorldData
                .get(helper.getLevel())
                .getOrCreateSession(definition, 5, 0, 13);
        session.setPhase(FestivalSessionPhase.OPEN);
        var denied = StardewFestivalActivities.start(
                player, festivalId, activityId);
        helper.assertValueEqual(
                denied.status(),
                StardewFestivalActivityResult.Status.NOT_PARTICIPATING,
                "festival activity bypassed participant validation");
        session.addParticipant(player.getUUID());
        var result = StardewFestivalActivities.start(
                player, festivalId, activityId);
        helper.assertValueEqual(
                result.status(),
                StardewFestivalActivityResult.Status.WRONG_LOCATION,
                "resolved core festival location accepted the wrong dimension");
        helper.assertTrue(calls.isEmpty(),
                "festival activity handler ran before location validation");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void fishingLocationAndConditionProvidersCompose(
            GameTestHelper helper
    ) {
        String suffix = UUID.randomUUID().toString();
        String addonPool = "stardewcraft_gametest:festival_pool_" + suffix;
        StardewFishingLocationKeys.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "fishing_keys_throwing_" + suffix),
                200,
                context -> {
                    throw new IllegalStateException(
                            "expected fishing location provider failure");
                }
        );
        StardewFishingLocationKeys.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "fishing_keys_" + suffix),
                100,
                context -> {
                    List<String> result =
                            new ArrayList<>(context.proposedKeys());
                    result.add(addonPool);
                    result.add(addonPool);
                    return result;
                }
        );

        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        List<String> keys =
                FishingDataManager.resolveVanillaAlignedLocationKeysStatic(
                        helper.getLevel(),
                        helper.getLevel().getBiome(position),
                        position);
        helper.assertTrue(keys.contains(addonPool),
                "addon fishing pool was not appended");
        helper.assertValueEqual(
                keys.stream().filter(addonPool::equals).count(),
                1L,
                "addon fishing pool was not deduplicated");

        SpawnFishRule rule = FishingDataManager.get().getAllFishRules().stream()
                .findFirst()
                .orElseThrow();
        StardewFishingRuleConditions.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "fishing_condition_" + suffix),
                100,
                (context, proposed) -> context.rule().id().equals(rule.id())
                        ? StardewFishingRuleConditions.Decision.DENY
                        : StardewFishingRuleConditions.Decision.PASS
        );
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "Fishing API"),
                ClientInformation.createDefault());
        helper.assertFalse(StardewFishingRuleConditionRegistry.evaluate(
                        player,
                        helper.getLevel(),
                        position,
                        helper.getLevel().getBiome(position),
                        rule,
                        false,
                        true),
                "addon fishing rule condition did not deny the candidate");

        StardewFishingLocationDisplays.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "fishing_display_" + suffix),
                100,
                raw -> raw.equals(addonPool)
                        ? Component.literal("Festival Pool")
                        : null
        );
        helper.assertValueEqual(
                StardewFishingLocationDisplayRegistry.resolve(addonPool),
                Component.literal("Festival Pool"),
                "addon fishing location label was not resolved");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void artifactSpotDropProvidersArePositionAware(
            GameTestHelper helper
    ) {
        String suffix = UUID.randomUUID().toString();
        BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
        StardewArtifactSpotDrops.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "artifact_spot_throwing_" + suffix),
                200,
                context -> {
                    if (!context.position().equals(target)) {
                        return null;
                    }
                    throw new IllegalStateException(
                            "expected artifact-spot provider failure");
                }
        );
        ItemStack source = new ItemStack(Items.DIAMOND, 2);
        ResourceLocation providerId =
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "artifact_spot_" + suffix);
        StardewArtifactSpotDrops.register(
                providerId,
                100,
                context -> context.position().equals(target)
                        ? List.of(source)
                        : null,
                owner -> List.of(StardewContentReference.required(
                        StardewContentReferenceRoles.DROP_ITEM,
                        new StardewContentKey(
                                StardewContentTypes.ITEM,
                                ResourceLocation.withDefaultNamespace(
                                        "diamond"))))
        );

        List<ItemStack> drops =
                ArtifactDropService.rollDrops(helper.getLevel(), target, null);
        helper.assertValueEqual(drops.size(), 1,
                "addon artifact-spot provider returned the wrong number of drops");
        helper.assertTrue(drops.getFirst().is(Items.DIAMOND),
                "addon artifact-spot provider returned the wrong item");
        helper.assertValueEqual(drops.getFirst().getCount(), 2,
                "addon artifact-spot stack count changed");
        helper.assertTrue(drops.getFirst() != source,
                "artifact-spot provider leaked its source stack");
        var providerNode = StardewContents.find(
                new StardewContentKey(
                        StardewContentTypes
                                .ARTIFACT_SPOT_DROP_PROVIDER,
                        providerId)).orElseThrow();
        helper.assertTrue(
                providerNode.references().stream().anyMatch(reference ->
                        reference.role().equals(
                                StardewContentReferenceRoles.DROP_ITEM)
                                && reference.required()
                                && reference.resolved()),
                "artifact-spot provider references were not projected");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void mineMonsterProfilesConfigureAndProject(
            GameTestHelper helper
    ) {
        String suffix = UUID.randomUUID().toString();
        ResourceLocation profileId =
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "clockwork_monster_" + suffix);
        AtomicBoolean configured = new AtomicBoolean();
        StardewMineMonsterProfiles.register(
                profileId,
                EntityType.ZOMBIE,
                Set.of("stardewcraft_gametest:clockwork"),
                (mob, context) -> configured.set(
                        context.floor() == 42));
        var mob = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(mob != null,
                "test monster could not be created");
        helper.assertTrue(
                StardewMineMonsterProfiles.mark(mob, profileId),
                "registered profile could not mark its entity");
        helper.assertTrue(
                StardewMineMonsterProfiles.applyMarkedProfile(mob, 42),
                "marked profile could not configure its entity");
        helper.assertTrue(configured.get(),
                "profile configurator did not receive the floor");
        helper.assertTrue(
                mob.getTags().contains(
                        "stardewcraft_gametest:clockwork"),
                "profile progress tag was not applied");

        var node = StardewContents.find(new StardewContentKey(
                StardewContentTypes.MINE_MONSTER_PROFILE,
                profileId)).orElseThrow();
        helper.assertTrue(
                node.references().stream().anyMatch(reference ->
                        reference.role().equals(
                                StardewContentReferenceRoles.SPAWN_ENTITY)
                                && reference.required()
                                && reference.resolved()),
                "monster profile entity type was not projected");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void locationTransitionListenersComposeAndIsolateFailures(
            GameTestHelper helper
    ) {
        String suffix = UUID.randomUUID().toString();
        List<String> calls = new ArrayList<>();
        StardewLocationEvents.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "location_throwing_" + suffix),
                200,
                transition -> {
                    throw new IllegalStateException(
                            "expected location listener failure");
                });
        StardewLocationEvents.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "location_high_" + suffix),
                100,
                transition -> calls.add("high"));
        StardewLocationEvents.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest",
                        "location_low_" + suffix),
                0,
                transition -> calls.add("low"));

        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "Location API"),
                ClientInformation.createDefault());
        ResourceLocation locationId =
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "orchard");
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        StardewLocationTransition transition =
                new StardewLocationTransition(
                        player,
                        null,
                        locationId,
                        helper.getLevel().dimension().location(),
                        helper.getLevel().dimension().location(),
                        position,
                        position,
                        StardewLocationTransition.Reason.INITIAL);
        StardewLocationTransitionRegistry.dispatch(transition);

        helper.assertValueEqual(
                calls,
                List.of("high", "low"),
                "location listeners did not compose in priority order");
        helper.assertTrue(
                transition.enteredLocation(),
                "initial logical location did not report entry");
        helper.assertFalse(
                transition.leftLocation(),
                "initial logical location incorrectly reported leave");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void coreOutdoorLocationsUseSharedExactGeometry(
            GameTestHelper helper
    ) {
        ResourceLocation town = ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, "town");
        ResourceLocation mountain = ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, "mountain");
        ResourceLocation dimension =
                ModDimensions.STARDEW_VALLEY.location();

        helper.assertValueEqual(
                StardewLocations.resolveId("Town").orElse(null),
                town,
                "legacy Town alias did not resolve to the canonical location");
        helper.assertValueEqual(
                StardewLocations.resolveId("BusStop")
                        .map(ResourceLocation::getPath)
                        .orElse(""),
                "bus_stop",
                "legacy BusStop alias did not resolve");
        helper.assertFalse(
                StardewLocations.get(town).orElseThrow().indoor(),
                "core Town location was incorrectly marked as an interior");
        helper.assertTrue(
                StardewLocations.get(ResourceLocation.fromNamespaceAndPath(
                                StardewCraft.MODID, "pierre_house"))
                        .orElseThrow().indoor(),
                "fixed interior lost its indoor metadata");
        helper.assertValueEqual(
                StardewLocations.find(
                                dimension, new BlockPos(0, 64, 0))
                        .map(location -> location.id())
                        .orElse(null),
                town,
                "Town geometry was not published to the logical location catalog");
        helper.assertTrue(
                InteriorRegionRegistry.fixedInteriorAt(
                        new BlockPos(0, 64, 0)).isEmpty(),
                "outdoor location leaked into the legacy fixed-interior facade");
        helper.assertTrue(
                StardewRegions.forLocation(mountain).stream()
                        .anyMatch(region -> region.contains(
                                dimension, new BlockPos(20, 85, -100))),
                "Mountain's second region was not published");
        helper.assertFalse(
                StardewRegions.forLocation(mountain).stream()
                        .anyMatch(region -> region.contains(
                                dimension, new BlockPos(-10, 80, -120))),
                "Mountain's disjoint geometry collapsed into its envelope");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void contentCatalogResolvesProviderReferences(
            GameTestHelper helper
    ) {
        ResourceLocation type = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "orchard_feature");
        StardewContentKey root = new StardewContentKey(
                type,
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "root"));
        StardewContentKey child = new StardewContentKey(
                type,
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "child"));
        StardewContentKey missing = new StardewContentKey(
                type,
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "missing"));
        StardewContentKey legacyChild = new StardewContentKey(
                type,
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "legacy_child"));
        ResourceLocation source = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "content_projection");
        ResourceLocation childRole = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", "child");

        if (CONTENT_PROVIDER_REGISTERED.compareAndSet(false, true)) {
            StardewContents.register(source, 0, () -> List.of(
                    new StardewContentDefinition(
                            root,
                            source,
                            List.of(
                                    StardewContentReference.required(
                                            childRole, legacyChild),
                                    StardewContentReference.required(
                                            childRole, missing))),
                    new StardewContentDefinition(
                            child, source, List.of())));
            StardewContents.registerAliases(
                    ResourceLocation.fromNamespaceAndPath(
                            "stardewcraft_gametest",
                            "content_aliases"),
                    0,
                    () -> List.of(new StardewContentAlias(
                            legacyChild, child)));
        }

        var rootSnapshot = StardewContents.find(root).orElseThrow();
        helper.assertValueEqual(
                rootSnapshot.references().size(),
                2,
                "provider references were not projected");
        helper.assertTrue(
                rootSnapshot.references().stream()
                        .anyMatch(reference ->
                                reference.target().equals(legacyChild)
                                        && reference.resolved()),
                "aliased provider target was not resolved in the second pass");
        helper.assertTrue(
                rootSnapshot.references().stream()
                        .anyMatch(reference ->
                                reference.target().equals(missing)
                                        && !reference.resolved()),
                "missing provider target was not reported");
        helper.assertFalse(
                rootSnapshot.healthy(),
                "required missing target did not make the node unhealthy");
        helper.assertTrue(
                StardewContents.find(child)
                        .orElseThrow()
                        .healthy(),
                "reference-free provider node was not healthy");
        helper.assertValueEqual(
                StardewContents.resolve(legacyChild).orElse(null),
                child,
                "content alias did not resolve to the canonical key");
        helper.assertValueEqual(
                StardewContents.find(legacyChild)
                        .map(node -> node.key())
                        .orElse(null),
                child,
                "aliased content lookup did not return the canonical node");
        var catalog = StardewContents.snapshot();
        Set<ResourceLocation> projectedTypes =
                catalog.nodes().stream()
                        .map(node -> node.key().type())
                        .collect(java.util.stream.Collectors.toSet());
        helper.assertTrue(
                projectedTypes.containsAll(Set.of(
                        StardewContentTypes.LOCATION,
                        StardewContentTypes.REGION,
                        StardewContentTypes.FESTIVAL,
                        StardewContentTypes.SHOP,
                        StardewContentTypes.QUEST,
                        StardewContentTypes.SKILL,
                        StardewContentTypes.PROFESSION,
                        StardewContentTypes.MASTERY_REWARD,
                        StardewContentTypes.UNLOCK_SOURCE,
                        StardewContentTypes.SECRET_NOTE,
                        StardewContentTypes.LOST_BOOK,
                        StardewContentTypes.CUTSCENE_EVENT,
                        StardewContentTypes.DAILY_QUEST_POOL,
                        StardewContentTypes.MAIL,
                        StardewContentTypes.SPECIAL_ORDER,
                        StardewContentTypes.BUILDING_BLUEPRINT,
                        StardewContentTypes.COMMUNITY_BUNDLE,
                        StardewContentTypes.MACHINE,
                        StardewContentTypes.ARTISAN_RECIPE,
                        StardewContentTypes.CRAFTING_RECIPE,
                        StardewContentTypes.COOKING_RECIPE,
                        StardewContentTypes.CURRENCY,
                        StardewContentTypes.PRIZE_TICKET_REWARD,
                        StardewContentTypes.MINE_CHEST_REWARD,
                        StardewContentTypes.WORLD_LOOT_POOL,
                        StardewContentTypes.FISHING_POOL,
                        StardewContentTypes.FISH_POND_RULE,
                        StardewContentTypes.MUSEUM_REWARD,
                        StardewContentTypes.MONSTER_SLAYER_GOAL,
                        StardewContentTypes.ARTIFACT_SPOT_POOL,
                        StardewContentTypes.MINE_MONSTER_PROFILE,
                        StardewContentTypes.FORAGE_ZONE,
                        StardewContentTypes.MINE_THEME,
                        StardewContentTypes.BLOCK,
                        StardewContentTypes.CROP_TYPE,
                        StardewContentTypes.TREE_TYPE,
                        StardewContentTypes.ANIMAL_TYPE,
                        StardewContentTypes.ENTITY_TYPE)),
                "one or more core content domains were not projected: "
                        + projectedTypes);
        StardewContentKey townForage = new StardewContentKey(
                StardewContentTypes.FORAGE_ZONE,
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "town"));
        var townForageNode = StardewContents.find(townForage)
                .orElseThrow();
        helper.assertTrue(
                townForageNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles.REGION)
                                        && !reference.required()
                                        && reference.resolved()),
                "forage zone did not expose its optional same-ID region");
        helper.assertTrue(
                townForageNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles.FORAGE_BLOCK)
                                        && reference.required()
                                        && reference.resolved()),
                "forage block references were not resolved");
        StardewContentKey farmingMastery = new StardewContentKey(
                StardewContentTypes.MASTERY_REWARD,
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "farming"));
        var farmingMasteryNode = StardewContents.find(farmingMastery)
                .orElseThrow();
        helper.assertTrue(
                farmingMasteryNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles.SKILL)
                                        && reference.required()
                                        && reference.resolved()),
                "mastery reward did not resolve its skill");
        helper.assertTrue(
                farmingMasteryNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles.REWARD_ITEM)
                                        && reference.required()
                                        && reference.resolved()),
                "mastery reward items were not resolved");
        StardewContentKey whiteChicken = new StardewContentKey(
                StardewContentTypes.ANIMAL_TYPE,
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "white_chicken"));
        var whiteChickenNode = StardewContents.find(whiteChicken)
                .orElseThrow();
        helper.assertTrue(
                whiteChickenNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles
                                                .PRODUCE_ITEM)
                                        && reference.required()
                                        && reference.resolved()),
                "animal produce items were not resolved");
        helper.assertTrue(
                whiteChickenNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles
                                                .ANIMAL_ENTITY)
                                        && reference.required()
                                        && reference.resolved()),
                "animal entity type was not resolved");
        StardewContentKey fishingRodEvent = new StardewContentKey(
                StardewContentTypes.CUTSCENE_EVENT,
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "willy_fishing_rod"));
        var fishingRodEventNode = StardewContents.find(
                fishingRodEvent).orElseThrow();
        helper.assertTrue(
                fishingRodEventNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles.LOCATION)
                                        && reference.required()
                                        && reference.resolved()),
                "cutscene trigger location was not resolved");
        helper.assertTrue(
                fishingRodEventNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles.MAIL)
                                        && reference.required()
                                        && reference.resolved()),
                "cutscene mail precondition was not resolved");
        helper.assertTrue(
                fishingRodEventNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles
                                                .ACTION_ITEM)
                                        && reference.required()
                                        && reference.resolved()),
                "cutscene item command was not resolved");
        helper.assertTrue(
                fishingRodEventNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles.QUEST)
                                        && reference.required()
                                        && reference.resolved()),
                "cutscene quest command was not resolved");
        StardewContentKey dailyQuestPool = new StardewContentKey(
                StardewContentTypes.DAILY_QUEST_POOL,
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "default"));
        var dailyQuestPoolNode = StardewContents.find(
                dailyQuestPool).orElseThrow();
        helper.assertTrue(
                dailyQuestPoolNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles.TARGET_NPC)
                                        && reference.required()
                                        && reference.resolved()),
                "daily quest pool NPCs were not resolved; NPC nodes="
                        + catalog.nodes().stream()
                                .filter(node -> node.key().type().equals(
                                        StardewContentTypes.NPC))
                                .limit(8)
                                .map(node -> node.key().id())
                                .toList()
                        + ", issues=" + catalog.issues().stream()
                                .limit(4)
                                .toList());
        helper.assertTrue(
                dailyQuestPoolNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles
                                                .OBJECTIVE_ITEM)
                                        && reference.required()
                                        && reference.resolved()),
                "daily quest pool item candidates were not resolved");
        StardewContentKey beachFishingPool = new StardewContentKey(
                StardewContentTypes.FISHING_POOL,
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "legacy/beach"));
        var beachFishingPoolNode = StardewContents.find(
                beachFishingPool).orElseThrow();
        helper.assertTrue(
                beachFishingPoolNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles
                                                .CATCH_ITEM)
                                        && reference.required()
                                        && reference.resolved()),
                "fishing pool catch items were not resolved");
        helper.assertTrue(
                beachFishingPoolNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles.LOCATION)
                                        && !reference.required()
                                        && reference.resolved()),
                "legacy fishing pool did not expose its logical location");
        StardewContentKey sturgeonPond = new StardewContentKey(
                StardewContentTypes.FISH_POND_RULE,
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "legacy/sturgeon"));
        var sturgeonPondNode = StardewContents.find(
                sturgeonPond).orElseThrow();
        helper.assertTrue(
                sturgeonPondNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles
                                                .POND_PRODUCT)
                                        && reference.required()
                                        && reference.resolved()),
                "fish pond products were not resolved");
        helper.assertTrue(
                sturgeonPondNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles
                                                .POPULATION_GATE_ITEM)
                                        && reference.required()
                                        && reference.resolved()),
                "fish pond gate items were not resolved");
        StardewContentKey museumFive = new StardewContentKey(
                StardewContentTypes.MUSEUM_REWARD,
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "legacy/museum5"));
        var museumFiveNode = StardewContents.find(
                museumFive).orElseThrow();
        helper.assertTrue(
                museumFiveNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles
                                                .ACTION_ITEM)
                                        && reference.required()
                                        && reference.resolved()),
                "museum reward action items were not resolved");
        StardewContentKey slimeGoal = new StardewContentKey(
                StardewContentTypes.MONSTER_SLAYER_GOAL,
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "legacy/slimes"));
        var slimeGoalNode = StardewContents.find(
                slimeGoal).orElseThrow();
        helper.assertTrue(
                slimeGoalNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles
                                                .MONSTER_PROFILE)
                                        && reference.required()
                                        && reference.resolved()),
                "monster-slayer tags were not connected to profiles");
        helper.assertTrue(
                slimeGoalNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles
                                                .ACTION_ITEM)
                                        && reference.required()
                                        && reference.resolved()),
                "monster-slayer reward items were not resolved");
        StardewContentKey townArtifactPool = new StardewContentKey(
                StardewContentTypes.ARTIFACT_SPOT_POOL,
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "legacy/town"));
        var townArtifactPoolNode = StardewContents.find(
                townArtifactPool).orElseThrow();
        helper.assertTrue(
                townArtifactPoolNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles.DROP_ITEM)
                                        && reference.required()
                                        && reference.resolved()),
                "artifact-spot static drops were not resolved");
        helper.assertTrue(
                townArtifactPoolNode.references().stream()
                        .anyMatch(reference ->
                                reference.role().equals(
                                        StardewContentReferenceRoles.LOCATION)
                                        && !reference.required()
                                        && reference.resolved()),
                "artifact-spot pool did not expose its logical location");
        helper.assertTrue(
                projectedTypes.contains(StardewContentTypes.NPC)
                        || catalog.issues().stream()
                        .anyMatch(issue -> issue.source().getPath()
                                .equals("content/projection/npcs")),
                "NPC projection was neither published nor isolated as a "
                        + "catalog issue");
        helper.succeed();
    }

    private static StardewEquipmentData ringData() {
        return new StardewEquipmentData(
                EquipmentSlotResolver.RING,
                1, 0, 0, 0.0F, 0.0F, 0, 0.0F, 0.0F, 0,
                List.of(), Optional.empty());
    }

    private static StardewAnimalPersistentData.Key registerAnimalPersistentDataKey() {
        return StardewAnimalPersistentData.register(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft_gametest", "managed_goose_state"),
                1
        );
    }

    private static void registerTestAnimalType() {
        if (StardewAnimalTypes.definition(TEST_ANIMAL_TYPE) != null) {
            return;
        }
        StardewAnimalTypes.register(
                TEST_ANIMAL_TYPE_REGISTRATION,
                TEST_ANIMAL_TYPE,
                "coop",
                5,
                ModEntities.WHITE_CHICKEN::get
        );
    }
}
