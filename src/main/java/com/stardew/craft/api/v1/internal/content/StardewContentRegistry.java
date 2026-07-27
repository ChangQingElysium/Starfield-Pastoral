package com.stardew.craft.api.v1.internal.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.content.StardewContentCatalogSnapshot;
import com.stardew.craft.api.v1.content.StardewContentAlias;
import com.stardew.craft.api.v1.content.StardewContentAliasProvider;
import com.stardew.craft.api.v1.content.StardewContentAliasSnapshot;
import com.stardew.craft.api.v1.content.StardewContentDefinition;
import com.stardew.craft.api.v1.content.StardewContentIssue;
import com.stardew.craft.api.v1.content.StardewContentKey;
import com.stardew.craft.api.v1.content.StardewContentNodeSnapshot;
import com.stardew.craft.api.v1.content.StardewContentProvider;
import com.stardew.craft.api.v1.content.StardewContentReference;
import com.stardew.craft.api.v1.content.StardewContentReferenceRoles;
import com.stardew.craft.api.v1.content.StardewContentReferenceSnapshot;
import com.stardew.craft.api.v1.content.StardewContentTypes;
import com.stardew.craft.api.v1.agriculture.StardewAgricultureDataApi;
import com.stardew.craft.api.v1.agriculture.StardewAnimalTypes;
import com.stardew.craft.api.v1.agriculture.StardewCropTypes;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.cutscene.StardewCutsceneCommands;
import com.stardew.craft.api.v1.cutscene.StardewCutsceneTriggers;
import com.stardew.craft.api.v1.festival.StardewFestivalMapOverlay;
import com.stardew.craft.api.v1.economy.StardewCurrencies;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.internal.machine.StardewMachineTypeRegistry;
import com.stardew.craft.api.v1.internal.world.StardewArtifactSpotDropRegistry;
import com.stardew.craft.api.v1.mastery.StardewMasteryRewardDefinition;
import com.stardew.craft.api.v1.mining.StardewMineMonsterProfiles;
import com.stardew.craft.api.v1.npc.StardewNpcContents;
import com.stardew.craft.api.v1.npc.StardewNpcInteractions;
import com.stardew.craft.api.v1.shop.StardewShopDefinition;
import com.stardew.craft.api.v1.shop.StardewShopEntry;
import com.stardew.craft.api.v1.shop.StardewShopInventoryProviders;
import com.stardew.craft.api.v1.quest.StardewQuestObjectives;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.query.StardewItemQuery;
import com.stardew.craft.api.v1.profession.StardewProfessionEffectHandlers;
import com.stardew.craft.api.v1.world.StardewLocations;
import com.stardew.craft.api.v1.world.StardewRegions;
import com.stardew.craft.api.v1.world.StardewWorldAnchors;
import com.stardew.craft.api.v1.world.StardewArtifactSpotDrops;
import com.stardew.craft.api.v1.tree.StardewTreeTypes;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.building.BuildingBlueprintRegistry;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.communitycenter.data.BundleDataManager;
import com.stardew.craft.cooking.service.VanillaCookingRecipeData;
import com.stardew.craft.cutscene.data.EventRegistry;
import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalMapOverlayDefinition;
import com.stardew.craft.festival.FestivalMapOverlayRegistry;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.interior.InteriorPortalRegistry;
import com.stardew.craft.fishing.data.FishingDataManager;
import com.stardew.craft.fishpond.service.FishPondDataService;
import com.stardew.craft.fishpond.service.FishPondQualifiedItemService;
import com.stardew.craft.mail.MailRegistry;
import com.stardew.craft.mastery.MasteryRewardRegistry;
import com.stardew.craft.mining.MineChestRewardData;
import com.stardew.craft.mining.MineMonsterSpawnTableData;
import com.stardew.craft.mining.MineThemeData;
import com.stardew.craft.museum.LostBookRegistry;
import com.stardew.craft.museum.MuseumRewardRegistry;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.data.NpcGiftTastePatchData;
import com.stardew.craft.player.StardewCraftingRecipeData;
import com.stardew.craft.player.ProfessionData;
import com.stardew.craft.player.RecipeIdNormalizer;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.player.UnlockSourceData;
import com.stardew.craft.quest.QuestDataLoader;
import com.stardew.craft.quest.data.DailyQuestPoolRegistry;
import com.stardew.craft.secretnote.SecretNoteRegistry;
import com.stardew.craft.shop.GeodeDropData;
import com.stardew.craft.shop.PrizeTicketRewardData;
import com.stardew.craft.shop.ShopDataLoader;
import com.stardew.craft.shop.MonsterSlayerGoalRegistry;
import com.stardew.craft.specialorder.SpecialOrderDataLoader;
import com.stardew.craft.specialorder.SpecialOrderDefinition;
import com.stardew.craft.api.v1.farm.StardewFarmLayouts;
import com.stardew.craft.fishing.data.FishingTreasurePoolData;
import com.stardew.craft.world.data.ForageZoneData;
import com.stardew.craft.world.data.WorldLootPoolData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Internal two-pass projector and resolver for cross-system content. */
public final class StardewContentRegistry {
    private static final int MAX_LOGGED_ISSUES = 50;
    private static final int MAX_LOGGED_NODES = 50;
    private static final int MAX_LOGGED_REFERENCES_PER_NODE = 8;
    private static final int MAX_CONTENT_ALIASES = 4096;
    private static final ResourceLocation EXTENSION_POINT =
            id("content/providers");
    private static final ResourceLocation ALIAS_EXTENSION_POINT =
            id("content/alias_providers");
    private static final ResourceLocation ITEM_REGISTRY_SOURCE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "item");
    private static final ResourceLocation BLOCK_REGISTRY_SOURCE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block");
    private static final ResourceLocation ENTITY_TYPE_REGISTRY_SOURCE =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft", "entity_type");
    private static final Set<String> VIRTUAL_PRODUCT_NAMESPACES = Set.of(
            "recipe", "wallpaper", "flooring", "random");
    private static final OrderedExtensionRegistry<StardewContentProvider>
            PROVIDERS = new OrderedExtensionRegistry<>(EXTENSION_POINT);
    private static final OrderedExtensionRegistry<StardewContentAliasProvider>
            ALIAS_PROVIDERS =
            new OrderedExtensionRegistry<>(ALIAS_EXTENSION_POINT);
    private static final Comparator<StardewContentKey> KEY_ORDER =
            Comparator.comparing((StardewContentKey key) ->
                            key.type().toString())
                    .thenComparing(key -> key.id().toString());
    private static volatile Catalog cachedCatalog;

    private StardewContentRegistry() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            StardewContentProvider provider
    ) {
        PROVIDERS.register(registrationId, priority, provider);
        invalidate();
    }

    public static void registerAliases(
            ResourceLocation registrationId,
            int priority,
            StardewContentAliasProvider provider
    ) {
        ALIAS_PROVIDERS.register(registrationId, priority, provider);
        invalidate();
    }

    public static StardewContentCatalogSnapshot snapshot() {
        return catalog().snapshot();
    }

    private static Catalog catalog() {
        Catalog cached = cachedCatalog;
        if (cached != null) {
            return cached;
        }
        return rebuildCatalog();
    }

    private static synchronized Catalog rebuildCatalog() {
        Catalog cached = cachedCatalog;
        if (cached != null) {
            return cached;
        }
        LinkedHashMap<StardewContentKey, StardewContentDefinition>
                definitions = new LinkedHashMap<>();
        ArrayList<StardewContentIssue> issues = new ArrayList<>();
        addBuiltins(definitions, issues);
        for (var registration : PROVIDERS.entries()) {
            addProvider(
                    registration.id(),
                    registration.extension(),
                    definitions,
                    issues);
        }

        StardewContentAliasResolver.Result aliases =
                buildAliases(definitions, issues);
        issues.addAll(aliases.issues());
        List<StardewContentNodeSnapshot> nodes =
                definitions.values().stream()
                        .sorted(Comparator.comparing(
                                StardewContentDefinition::key,
                                KEY_ORDER))
                        .map(definition -> resolve(
                                definition,
                                definitions,
                                aliases.canonical()))
                        .toList();
        StardewContentCatalogSnapshot rebuilt =
                new StardewContentCatalogSnapshot(nodes, issues);
        Catalog catalog = new Catalog(rebuilt, aliases.snapshots());
        cachedCatalog = catalog;
        return catalog;
    }

    public static synchronized void invalidate() {
        cachedCatalog = null;
    }

    public static Optional<StardewContentNodeSnapshot> find(
            StardewContentKey key
    ) {
        Objects.requireNonNull(key, "key");
        Catalog current = catalog();
        StardewContentKey resolved =
                resolveKey(current, key).orElse(key);
        return current.snapshot().nodes().stream()
                .filter(node -> node.key().equals(resolved))
                .findFirst();
    }

    public static List<StardewContentAliasSnapshot> aliases() {
        return catalog().aliases();
    }

    public static Optional<StardewContentKey> resolveKey(
            StardewContentKey key
    ) {
        Objects.requireNonNull(key, "key");
        return resolveKey(catalog(), key);
    }

    private static Optional<StardewContentKey> resolveKey(
            Catalog catalog,
            StardewContentKey key
    ) {
        if (catalog.snapshot().nodes().stream()
                .anyMatch(node -> node.key().equals(key))) {
            return Optional.of(key);
        }
        return catalog.aliases().stream()
                .filter(alias -> alias.alias().equals(key)
                        && alias.resolved())
                .map(StardewContentAliasSnapshot::canonicalTarget)
                .findFirst();
    }

    private record Catalog(
            StardewContentCatalogSnapshot snapshot,
            List<StardewContentAliasSnapshot> aliases
    ) {
        private Catalog {
            aliases = List.copyOf(aliases);
        }
    }

    /**
     * Runs after a completed datapack reload. This is deliberately advisory:
     * domain snapshots have already accepted or rejected their own candidates.
     */
    public static void validateAndLog() {
        invalidate();
        StardewContentCatalogSnapshot snapshot = snapshot();
        List<StardewContentReferenceSnapshot> unresolved =
                snapshot.unresolvedReferences();
        long requiredMissing = unresolved.stream()
                .filter(StardewContentReferenceSnapshot::required)
                .count();
        long optionalMissing = unresolved.size() - requiredMissing;
        if (snapshot.issues().isEmpty()
                && requiredMissing == 0L) {
            StardewCraft.LOGGER.info(
                    "[Content] Validated {} nodes; no required "
                            + "cross-system references are missing "
                            + "({} optional unresolved)",
                    snapshot.nodes().size(),
                    optionalMissing);
            return;
        }

        StardewCraft.LOGGER.warn(
                "[Content] Validation found {} catalog issue(s), "
                        + "{} unhealthy node(s), {} required and {} "
                        + "optional unresolved reference(s)",
                snapshot.issues().size(),
                snapshot.unhealthyNodes().size(),
                requiredMissing,
                optionalMissing);
        snapshot.issues().stream()
                .limit(MAX_LOGGED_ISSUES)
                .forEach(issue -> {
                    if (issue.severity()
                            == StardewContentIssue.Severity.ERROR) {
                        StardewCraft.LOGGER.error(
                                "[Content] source={} key={} {}",
                                issue.source(),
                                issue.key(),
                                issue.message());
                    } else {
                        StardewCraft.LOGGER.warn(
                                "[Content] source={} key={} {}",
                                issue.source(),
                                issue.key(),
                                issue.message());
                    }
                });
        if (snapshot.issues().size() > MAX_LOGGED_ISSUES) {
            StardewCraft.LOGGER.warn(
                    "[Content] {} additional catalog issue(s) omitted",
                    snapshot.issues().size() - MAX_LOGGED_ISSUES);
        }

        List<StardewContentNodeSnapshot> unhealthy =
                snapshot.unhealthyNodes();
        unhealthy.stream()
                .limit(MAX_LOGGED_NODES)
                .forEach(node -> {
                    StardewCraft.LOGGER.warn(
                            "[Content] Unhealthy {} source={} issues={}",
                            node.key(), node.source(), node.issues());
                    List<StardewContentReferenceSnapshot> missing =
                            node.references().stream()
                                    .filter(reference ->
                                            reference.required()
                                                    && !reference.resolved())
                                    .toList();
                    missing.stream()
                            .limit(MAX_LOGGED_REFERENCES_PER_NODE)
                            .forEach(reference ->
                                    StardewCraft.LOGGER.warn(
                                            "[Content]   {} -> {} ({})",
                                            reference.owner(),
                                            reference.target(),
                                            reference.role()));
                    if (missing.size()
                            > MAX_LOGGED_REFERENCES_PER_NODE) {
                        StardewCraft.LOGGER.warn(
                                "[Content]   {} additional missing "
                                        + "reference(s) omitted",
                                missing.size()
                                        - MAX_LOGGED_REFERENCES_PER_NODE);
                    }
                });
        if (unhealthy.size() > MAX_LOGGED_NODES) {
            StardewCraft.LOGGER.warn(
                    "[Content] {} additional unhealthy node(s) omitted",
                    unhealthy.size() - MAX_LOGGED_NODES);
        }
    }

    private static void addBuiltins(
            Map<StardewContentKey, StardewContentDefinition> output,
            List<StardewContentIssue> issues
    ) {
        addBuiltinDomain(
                "locations", output, issues,
                StardewContentRegistry::addLocations);
        addBuiltinDomain(
                "regions", output, issues,
                StardewContentRegistry::addRegions);
        addBuiltinDomain(
                "world_anchors", output, issues,
                StardewContentRegistry::addWorldAnchors);
        addBuiltinDomain(
                "portals", output, issues,
                StardewContentRegistry::addPortals);
        addBuiltinDomain(
                "forage_zones", output, issues,
                StardewContentRegistry::addForageZones);
        addBuiltinDomain(
                "mine_themes", output, issues,
                StardewContentRegistry::addMineThemes);
        addBuiltinDomain(
                "crop_types", output, issues,
                StardewContentRegistry::addCropTypes);
        addBuiltinDomain(
                "tree_types", output, issues,
                StardewContentRegistry::addTreeTypes);
        addBuiltinDomain(
                "animal_types", output, issues,
                StardewContentRegistry::addAnimalTypes);
        addBuiltinDomain(
                "festival_map_overlays", output, issues,
                StardewContentRegistry::addFestivalMapOverlays);
        addBuiltinDomain(
                "shops", output, issues,
                StardewContentRegistry::addShops);
        addBuiltinDomain(
                "npcs", output, issues,
                StardewContentRegistry::addNpcs);
        addBuiltinDomain(
                "npc_gift_taste_patches", output, issues,
                StardewContentRegistry::addNpcGiftTastePatches);
        addBuiltinDomain(
                "festivals", output, issues,
                StardewContentRegistry::addFestivals);
        addBuiltinDomain(
                "quests", output, issues,
                StardewContentRegistry::addQuests);
        addBuiltinDomain(
                "cutscene_events", output, issues,
                StardewContentRegistry::addCutsceneEvents);
        addBuiltinDomain(
                "daily_quest_pools", output, issues,
                StardewContentRegistry::addDailyQuestPools);
        addBuiltinDomain(
                "skills", output, issues,
                StardewContentRegistry::addSkills);
        addBuiltinDomain(
                "professions", output, issues,
                StardewContentRegistry::addProfessions);
        addBuiltinDomain(
                "mastery_rewards", output, issues,
                StardewContentRegistry::addMasteryRewards);
        addBuiltinDomain(
                "unlock_sources", output, issues,
                StardewContentRegistry::addUnlockSources);
        addBuiltinDomain(
                "secret_notes", output, issues,
                StardewContentRegistry::addSecretNotes);
        addBuiltinDomain(
                "lost_books", output, issues,
                StardewContentRegistry::addLostBooks);
        addBuiltinDomain(
                "mail", output, issues,
                StardewContentRegistry::addMail);
        addBuiltinDomain(
                "special_orders", output, issues,
                StardewContentRegistry::addSpecialOrders);
        addBuiltinDomain(
                "building_blueprints", output, issues,
                StardewContentRegistry::addBuildingBlueprints);
        addBuiltinDomain(
                "farm_layouts", output, issues,
                StardewContentRegistry::addFarmLayouts);
        addBuiltinDomain(
                "community_bundles", output, issues,
                StardewContentRegistry::addCommunityBundles);
        addBuiltinDomain(
                "machine_recipes", output, issues,
                StardewContentRegistry::addMachineRecipes);
        addBuiltinDomain(
                "crafting_recipes", output, issues,
                StardewContentRegistry::addCraftingRecipes);
        addBuiltinDomain(
                "cooking_recipes", output, issues,
                StardewContentRegistry::addCookingRecipes);
        addBuiltinDomain(
                "currencies", output, issues,
                StardewContentRegistry::addCurrencies);
        addBuiltinDomain(
                "geode_drops", output, issues,
                StardewContentRegistry::addGeodeDrops);
        addBuiltinDomain(
                "prize_ticket_rewards", output, issues,
                StardewContentRegistry::addPrizeTicketRewards);
        addBuiltinDomain(
                "mine_chest_rewards", output, issues,
                StardewContentRegistry::addMineChestRewards);
        addBuiltinDomain(
                "world_loot_pools", output, issues,
                StardewContentRegistry::addWorldLootPools);
        addBuiltinDomain(
                "fishing_treasure_pools", output, issues,
                StardewContentRegistry::addFishingTreasurePools);
        addBuiltinDomain(
                "fishing_pools", output, issues,
                StardewContentRegistry::addFishingPools);
        addBuiltinDomain(
                "fish_pond_rules", output, issues,
                StardewContentRegistry::addFishPondRules);
        addBuiltinDomain(
                "museum_rewards", output, issues,
                StardewContentRegistry::addMuseumRewards);
        addBuiltinDomain(
                "mine_monster_profiles", output, issues,
                StardewContentRegistry::addMineMonsterProfiles);
        addBuiltinDomain(
                "mine_monster_spawn_tables", output, issues,
                StardewContentRegistry::addMineMonsterSpawnTables);
        addBuiltinDomain(
                "monster_slayer_goals", output, issues,
                StardewContentRegistry::addMonsterSlayerGoals);
        addBuiltinDomain(
                "artifact_spot_pools", output, issues,
                StardewContentRegistry::addArtifactSpotPools);
        addBuiltinDomain(
                "artifact_spot_drop_providers", output, issues,
                StardewContentRegistry::addArtifactSpotDropProviders);
    }

    private static void addBuiltinDomain(
            String domain,
            Map<StardewContentKey, StardewContentDefinition> output,
            List<StardewContentIssue> issues,
            Consumer<Map<StardewContentKey, StardewContentDefinition>>
                    projector
    ) {
        LinkedHashMap<StardewContentKey, StardewContentDefinition>
                candidate = new LinkedHashMap<>();
        ResourceLocation source = id("content/projection/" + domain);
        try {
            projector.accept(candidate);
            candidate.values().forEach(
                    definition -> putBuiltin(output, definition));
        } catch (RuntimeException exception) {
            issues.add(new StardewContentIssue(
                    StardewContentIssue.Severity.ERROR,
                    source,
                    null,
                    "Built-in content projection failed: "
                            + exception.getClass().getSimpleName()
                            + ": " + exception.getMessage()));
        }
    }

    private static void addLocations(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var location : StardewLocations.all()) {
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            if (location.parentId() != null) {
                references.add(StardewContentReference.required(
                        StardewContentReferenceRoles.PARENT_LOCATION,
                        key(StardewContentTypes.LOCATION,
                                location.parentId())));
            }
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.LOCATION, location.id()),
                    location.id(),
                    references));
        }
    }

    private static void addRegions(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var region : StardewRegions.all()) {
            List<StardewContentReference> references =
                    region.locationId() == null
                            ? List.of()
                            : List.of(StardewContentReference.required(
                                    StardewContentReferenceRoles.LOCATION,
                                    key(StardewContentTypes.LOCATION,
                                            region.locationId())));
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.REGION, region.id()),
                    region.id(),
                    references));
        }
    }

    private static void addPortals(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        InteriorPortalRegistry.snapshot().definitions().keySet()
                .forEach(portalId ->
                        putBuiltin(
                                output,
                                new StardewContentDefinition(
                                        key(StardewContentTypes.PORTAL,
                                                portalId),
                                        portalId,
                                        List.of())));
    }

    private static void addForageZones(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : ForageZoneData.snapshot()
                .definitions().entrySet()) {
            StardewContentKey owner = key(
                    StardewContentTypes.FORAGE_ZONE, entry.getKey());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            references.add(StardewContentReference.optional(
                    StardewContentReferenceRoles.REGION,
                    key(StardewContentTypes.REGION, entry.getKey())));
            addConditionReferences(
                    owner,
                    entry.getValue().availableWhen(),
                    references,
                    nodeIssues);
            entry.getValue().entries().forEach(forage ->
                    addRegisteredBlockReference(
                            output,
                            references,
                            StardewContentReferenceRoles.FORAGE_BLOCK,
                            forage.block()));
            putBuiltin(output, new StardewContentDefinition(
                    owner, entry.getKey(), references, nodeIssues));
        }
    }

    private static void addMineThemes(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : MineThemeData.snapshot()
                .definitions().entrySet()) {
            StardewContentKey owner = key(
                    StardewContentTypes.MINE_THEME, entry.getKey());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            var theme = entry.getValue();
            addRegisteredBlockReference(
                    output, references,
                    StardewContentReferenceRoles.TERRAIN_BLOCK,
                    theme.mainStone());
            addRegisteredBlockReference(
                    output, references,
                    StardewContentReferenceRoles.TERRAIN_BLOCK,
                    theme.darkStone());
            theme.decorA().forEach(block ->
                    addRegisteredBlockReference(
                            output, references,
                            StardewContentReferenceRoles.TERRAIN_BLOCK,
                            block));
            theme.decorB().forEach(block ->
                    addRegisteredBlockReference(
                            output, references,
                            StardewContentReferenceRoles.TERRAIN_BLOCK,
                            block));
            theme.decorativeStones().forEach(block ->
                    addRegisteredBlockReference(
                            output, references,
                            StardewContentReferenceRoles.TERRAIN_BLOCK,
                            block));
            theme.vanillaAccents().forEach(block ->
                    addRegisteredBlockReference(
                            output, references,
                            StardewContentReferenceRoles.TERRAIN_BLOCK,
                            block));
            theme.caveDecorations().forEach(block ->
                    addRegisteredBlockReference(
                            output, references,
                            StardewContentReferenceRoles.TERRAIN_BLOCK,
                            block));
            theme.ores().values().forEach(block ->
                    addRegisteredBlockReference(
                            output, references,
                            StardewContentReferenceRoles.TERRAIN_BLOCK,
                            block));
            putBuiltin(output, new StardewContentDefinition(
                    owner, entry.getKey(), references));
        }
    }

    private static void addCropTypes(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (ResourceLocation blockId :
                BuiltInRegistries.BLOCK.keySet()) {
            var block = BuiltInRegistries.BLOCK.get(blockId);
            if (!(block instanceof StardewCropBlock)) {
                continue;
            }
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            addRegisteredBlockReference(
                    output,
                    references,
                    StardewContentReferenceRoles.CROP_BLOCK,
                    blockId);
            var data = StardewAgricultureDataApi.crop(
                    block.defaultBlockState());
            if (data != null) {
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.PRODUCE_ITEM,
                        data.produce());
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.SEED_ITEM,
                        data.seed());
            }
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.CROP_TYPE, blockId),
                    blockId,
                    references));
        }
        for (var crop : StardewCropTypes.definitions()) {
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            crop.blockIds().forEach(block ->
                    addRegisteredBlockReference(
                            output,
                            references,
                            StardewContentReferenceRoles.CROP_BLOCK,
                            block));
            if (crop.data() != null) {
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.PRODUCE_ITEM,
                        crop.data().produce());
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.SEED_ITEM,
                        crop.data().seed());
            }
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.CROP_TYPE, crop.id()),
                    crop.id(),
                    references));
        }
    }

    private static void addTreeTypes(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var tree : StardewTreeTypes.definitions()) {
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.TREE_TYPE, tree.id()),
                    tree.id(),
                    List.of()));
        }
    }

    private static void addAnimalTypes(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var animal : FarmAnimalDefinitions.all()) {
            ResourceLocation animalId = animalContentId(
                    animal.id(), animal.dataId().getNamespace());
            if (animalId == null) {
                continue;
            }
            StardewContentKey owner = key(
                    StardewContentTypes.ANIMAL_TYPE, animalId);
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            if (animal.unlockCondition() != null) {
                addConditionReferences(
                        owner,
                        List.of(animal.unlockCondition()),
                        references,
                        nodeIssues);
            }
            animal.eggItemIds().forEach(item ->
                    addRegisteredItemReference(
                            output,
                            references,
                            StardewContentReferenceRoles.PRODUCE_ITEM,
                            item));
            if (animal.harvestTool() != null) {
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.HARVEST_TOOL,
                        animal.harvestTool());
            }
            animal.produce().forEach(produce -> {
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.PRODUCE_ITEM,
                        produce.itemId());
                if (produce.condition() != null) {
                    addConditionReferences(
                            owner,
                            List.of(produce.condition()),
                            references,
                            nodeIssues);
                }
            });
            animal.deluxeProduce().forEach(produce -> {
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.PRODUCE_ITEM,
                        produce.itemId());
                if (produce.condition() != null) {
                    addConditionReferences(
                            owner,
                            List.of(produce.condition()),
                            references,
                            nodeIssues);
                }
            });
            addRegisteredEntityTypeReference(
                    output,
                    references,
                    StardewContentReferenceRoles.ANIMAL_ENTITY,
                    animal.entityTypeId());
            animal.alternatePurchaseTypes().forEach(alternate -> {
                if (alternate.condition() != null) {
                    addConditionReferences(
                            owner,
                            List.of(alternate.condition()),
                            references,
                            nodeIssues);
                }
                alternate.animalTypeIds().forEach(target -> {
                    ResourceLocation targetId = animalContentId(
                            target, animal.dataId().getNamespace());
                    if (targetId == null) {
                        nodeIssues.add(
                                "invalid_alternate_animal:" + target);
                    } else {
                        references.add(
                                StardewContentReference.required(
                                        StardewContentReferenceRoles
                                                .ALTERNATE_ANIMAL,
                                        key(StardewContentTypes.ANIMAL_TYPE,
                                                targetId)));
                    }
                });
            });
            putBuiltin(output, new StardewContentDefinition(
                    owner,
                    animal.dataId(),
                    references,
                    nodeIssues));
        }
        for (String rawId : StardewAnimalTypes.registeredTypeIds()) {
            var animal = StardewAnimalTypes.definition(rawId);
            if (animal == null) {
                continue;
            }
            ResourceLocation animalId = animalContentId(
                    animal.animalTypeId(),
                    animal.registrationId().getNamespace());
            if (animalId != null) {
                putBuiltin(output, new StardewContentDefinition(
                        key(StardewContentTypes.ANIMAL_TYPE, animalId),
                        animal.registrationId(),
                        List.of()));
            }
        }
    }

    private static void addWorldAnchors(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var anchor : StardewWorldAnchors.all()) {
            List<StardewContentReference> references =
                    anchor.locationId() == null
                            ? List.of()
                            : List.of(StardewContentReference.required(
                                    StardewContentReferenceRoles.LOCATION,
                                    key(StardewContentTypes.LOCATION,
                                            anchor.locationId())));
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.WORLD_ANCHOR,
                            anchor.id()),
                    anchor.id(),
                    references));
        }
    }

    private static void addFestivalMapOverlays(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (FestivalMapOverlayDefinition overlay
                : FestivalMapOverlayRegistry.all()) {
            ResourceLocation overlayId = legacyOverlayId(
                    overlay.overlayId());
            ResourceLocation locationId = locationReference(
                    overlay.regionKey(), StardewCraft.MODID);
            List<String> nodeIssues =
                    locationId == null && !overlay.regionKey().isBlank()
                            ? List.of("invalid_location_reference:"
                                    + overlay.regionKey())
                            : List.of();
            List<StardewContentReference> references =
                    locationId == null
                            ? List.of()
                            : List.of(StardewContentReference.required(
                                    StardewContentReferenceRoles.LOCATION,
                                    key(StardewContentTypes.LOCATION,
                                            locationId)));
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.FESTIVAL_MAP_OVERLAY,
                            overlayId),
                    overlayId,
                    references,
                    nodeIssues));
        }
        for (StardewFestivalMapOverlay overlay
                : FestivalMapOverlayRegistry.addonOverlays()) {
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.FESTIVAL_MAP_OVERLAY,
                            overlay.id()),
                    overlay.id(),
                    List.of(
                            StardewContentReference.required(
                                    StardewContentReferenceRoles.LOCATION,
                                    key(StardewContentTypes.LOCATION,
                                            overlay.locationId())),
                            StardewContentReference.required(
                                    StardewContentReferenceRoles.ORIGIN_ANCHOR,
                                    key(StardewContentTypes.WORLD_ANCHOR,
                                            overlay.originAnchor())))));
        }
    }

    private static void addShops(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (ResourceLocation providerId
                : StardewShopInventoryProviders.registeredIds()) {
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.SHOP_INVENTORY_PROVIDER,
                            providerId),
                    providerId,
                    List.of()));
        }
        for (var entry : ShopDataLoader.snapshot()
                .definitions().entrySet()) {
            ResourceLocation shopId = entry.getKey();
            StardewShopDefinition shop = entry.getValue();
            StardewContentKey shopKey = key(
                    StardewContentTypes.SHOP, shopId);
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            ResourceLocation owner = StardewNpcInteractions.normalizeNpcId(
                    shop.ownerNpc());
            if (owner != null) {
                references.add(StardewContentReference.required(
                        StardewContentReferenceRoles.OWNER_NPC,
                        key(StardewContentTypes.NPC, owner)));
            } else if (!shop.ownerNpc().isBlank()) {
                nodeIssues.add("invalid_owner_npc:"
                        + shop.ownerNpc());
            }
            for (ResourceLocation providerId
                    : shop.inventoryProviders()) {
                references.add(StardewContentReference.required(
                        StardewContentReferenceRoles.INVENTORY_PROVIDER,
                        key(StardewContentTypes.SHOP_INVENTORY_PROVIDER,
                                providerId)));
            }
            for (StardewShopEntry shopEntry : shop.entries()) {
                addConditionReferences(
                        shopKey,
                        shopEntry.availableWhen(),
                        references,
                        nodeIssues);
                if (!addItemReference(
                        output, references,
                        StardewContentReferenceRoles.PRODUCT_ITEM,
                        shopEntry.item())) {
                    nodeIssues.add("invalid_product_item:"
                            + shopEntry.item());
                }
                shopEntry.tradeItem().ifPresent(raw ->
                {
                    if (!addItemReference(
                            output, references,
                            StardewContentReferenceRoles.TRADE_ITEM,
                            raw)) {
                        nodeIssues.add("invalid_trade_item:" + raw);
                    }
                });
            }
            putBuiltin(output, new StardewContentDefinition(
                    shopKey,
                    shopId,
                    references,
                    nodeIssues));
        }
    }

    private static void addNpcs(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (ResourceLocation npcId : StardewNpcContents.ids()) {
            try {
                var npc = StardewNpcContents.inspect(npcId);
                ArrayList<StardewContentReference> references =
                        new ArrayList<>();
                npc.shops().stream()
                        .map(shop ->
                                StardewContentReference.required(
                                        StardewContentReferenceRoles.SHOP,
                                        key(StardewContentTypes.SHOP,
                                                shop)))
                        .forEach(references::add);
                String tasteKey = npcId.getNamespace()
                        .equals(StardewCraft.MODID)
                        ? npcId.getPath()
                        : npcId.toString();
                JsonObject tastes =
                        NpcDataRegistry.tastes().get(tasteKey);
                if (tastes != null) {
                    addNpcTasteItemReferences(
                            output, references, tastes);
                }
                putBuiltin(output, new StardewContentDefinition(
                        key(StardewContentTypes.NPC, npcId),
                        npcId,
                        references,
                        npc.issues()));
            } catch (RuntimeException exception) {
                putBuiltin(output, new StardewContentDefinition(
                        key(StardewContentTypes.NPC, npcId),
                        npcId,
                        List.of(),
                        List.of("projection_failed:"
                                + exception.getClass().getSimpleName()
                                + ":" + exception.getMessage())));
            }
        }
    }

    private static void addNpcGiftTastePatches(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : NpcGiftTastePatchData.snapshot()
                .definitions().entrySet()) {
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            if (!entry.getValue().npc().equals(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "universal"))) {
                StardewContentKey target = key(
                        StardewContentTypes.NPC,
                        entry.getValue().npc());
                references.add(entry.getValue().required()
                        ? StardewContentReference.required(
                                StardewContentReferenceRoles.TARGET_NPC,
                                target)
                        : StardewContentReference.optional(
                                StardewContentReferenceRoles.TARGET_NPC,
                                target));
            }
            entry.getValue().add().values().forEach(items ->
                    items.forEach(item ->
                            addNpcTastePatchItemReference(
                                    output,
                                    references,
                                    item,
                                    entry.getValue().required())));
            entry.getValue().remove().values().forEach(items ->
                    items.forEach(item ->
                            addNpcTastePatchItemReference(
                                    output,
                                    references,
                                    item,
                                    entry.getValue().required())));
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.NPC_GIFT_TASTE_PATCH,
                            entry.getKey()),
                    entry.getKey(),
                    references));
        }
    }

    private static void addNpcTastePatchItemReference(
            Map<StardewContentKey, StardewContentDefinition> output,
            List<StardewContentReference> references,
            ResourceLocation itemId,
            boolean required
    ) {
        StardewContentKey itemKey = key(
                StardewContentTypes.ITEM, itemId);
        references.add(required
                ? StardewContentReference.required(
                        StardewContentReferenceRoles.GIFT_ITEM,
                        itemKey)
                : StardewContentReference.optional(
                        StardewContentReferenceRoles.GIFT_ITEM,
                        itemKey));
        if (registeredItem(itemId)) {
            putBuiltin(output, new StardewContentDefinition(
                    itemKey, ITEM_REGISTRY_SOURCE, List.of()));
        }
    }

    private static void addNpcTasteItemReferences(
            Map<StardewContentKey, StardewContentDefinition> output,
            List<StardewContentReference> references,
            JsonObject tastes
    ) {
        for (String category : com.stardew.craft.api.v1.npc
                .StardewNpcGiftTastePatchDefinition.CATEGORIES) {
            if (!tastes.has(category)
                    || !tastes.get(category).isJsonArray()) {
                continue;
            }
            for (var raw : tastes.getAsJsonArray(category)) {
                if (!raw.isJsonPrimitive()) {
                    continue;
                }
                ResourceLocation item =
                        ResourceLocation.tryParse(raw.getAsString());
                if (item != null) {
                    addRegisteredItemReference(
                            output,
                            references,
                            StardewContentReferenceRoles.GIFT_ITEM,
                            item);
                }
            }
        }
    }

    private static void addFestivals(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (FestivalDefinition festival : FestivalRegistry.all()) {
            StardewContentKey festivalKey = key(
                    StardewContentTypes.FESTIVAL,
                    festival.resourceId());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            addConditionReferences(
                    festivalKey,
                    festival.availableWhen(),
                    references,
                    nodeIssues);
            ResourceLocation location = locationReference(
                    festival.locationKey(),
                    festival.resourceId().getNamespace());
            if (location != null) {
                references.add(StardewContentReference.required(
                        StardewContentReferenceRoles.LOCATION,
                        key(StardewContentTypes.LOCATION, location)));
            } else if (!festival.locationKey().isBlank()) {
                nodeIssues.add("invalid_location_reference:"
                        + festival.locationKey());
            }
            if (!festival.mapOverlayId().isBlank()) {
                ResourceLocation overlay = overlayReference(
                        festival.mapOverlayId(),
                        festival.resourceId().getNamespace());
                if (overlay == null) {
                    nodeIssues.add("invalid_map_overlay_reference:"
                            + festival.mapOverlayId());
                } else {
                    references.add(StardewContentReference.required(
                            StardewContentReferenceRoles.MAP_OVERLAY,
                            key(StardewContentTypes.FESTIVAL_MAP_OVERLAY,
                                    overlay)));
                }
            }
            for (String rawShop : festival.shopIds()) {
                ResourceLocation shop = shopReference(
                        rawShop,
                        festival.resourceId().getNamespace());
                if (shop != null) {
                    references.add(StardewContentReference.required(
                            StardewContentReferenceRoles.SHOP,
                            key(StardewContentTypes.SHOP, shop)));
                } else if (!rawShop.isBlank()) {
                    nodeIssues.add("invalid_shop_reference:"
                            + rawShop);
                }
            }
            putBuiltin(output, new StardewContentDefinition(
                    festivalKey,
                    festival.resourceId(),
                    references,
                    nodeIssues));
        }
    }

    private static void addQuests(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : QuestDataLoader.snapshot()
                .definitions().entrySet()) {
            StardewContentKey questKey = key(
                    StardewContentTypes.QUEST, entry.getKey());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            entry.getValue().nextQuests().stream()
                    .map(quest -> StardewContentReference.required(
                            StardewContentReferenceRoles.NEXT_QUEST,
                            key(StardewContentTypes.QUEST, quest)))
                    .forEach(references::add);
            addConditionReferences(
                    questKey,
                    entry.getValue().availableWhen(),
                    references,
                    nodeIssues);
            addActionReferences(
                    questKey,
                    entry.getValue().onAccept(),
                    references,
                    nodeIssues);
            addActionReferences(
                    questKey,
                    entry.getValue().onComplete(),
                    references,
                    nodeIssues);
            StardewQuestObjectives.contentReferences(
                            questKey,
                            entry.getValue().objective())
                    .resultOrPartial(message -> nodeIssues.add(
                            "objective_references:" + message))
                    .ifPresent(references::addAll);
            putBuiltin(output, new StardewContentDefinition(
                    questKey,
                    entry.getKey(),
                    references,
                    nodeIssues));
        }
    }

    private static void addCutsceneEvents(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : EventRegistry.snapshot()
                .definitions().entrySet()) {
            StardewContentKey owner = key(
                    StardewContentTypes.CUTSCENE_EVENT,
                    entry.getKey());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            var event = entry.getValue();
            String triggerLocation = event.trigger().location();
            if (triggerLocation != null
                    && !triggerLocation.isBlank()) {
                ResourceLocation location = locationReference(
                        triggerLocation,
                        entry.getKey().getNamespace());
                if (location == null) {
                    nodeIssues.add("invalid_trigger_location:"
                            + triggerLocation);
                } else {
                    references.add(StardewContentReference.required(
                            StardewContentReferenceRoles.LOCATION,
                            key(StardewContentTypes.LOCATION,
                                    location)));
                }
            }
            String triggerNpc = event.trigger().npc();
            if (triggerNpc != null && !triggerNpc.isBlank()) {
                addNpcReference(
                        references,
                        nodeIssues,
                        StardewContentReferenceRoles.TARGET_NPC,
                        triggerNpc,
                        entry.getKey().getNamespace(),
                        "invalid_trigger_npc:");
            }
            ResourceLocation customTrigger =
                    event.trigger().type().indexOf(':') > 0
                            ? ResourceLocation.tryParse(
                                    event.trigger().type())
                            : null;
            if (customTrigger != null) {
                StardewCutsceneTriggers.contentReferences(
                                owner,
                                customTrigger,
                                event.trigger().raw())
                        .resultOrPartial(message -> nodeIssues.add(
                                "trigger_references:" + message))
                        .ifPresent(references::addAll);
            }
            for (var precondition : event.preconditions()) {
                switch (precondition.type()) {
                    case "friendship" -> addNpcReference(
                            references,
                            nodeIssues,
                            StardewContentReferenceRoles.TARGET_NPC,
                            precondition.getString("npc"),
                            entry.getKey().getNamespace(),
                            "invalid_friendship_npc:");
                    case "has_item" -> {
                        String rawItem =
                                precondition.getString("item");
                        if (!addItemReference(
                                output,
                                references,
                                StardewContentReferenceRoles
                                        .CONDITION_ITEM,
                                rawItem)) {
                            nodeIssues.add(
                                    "invalid_precondition_item:"
                                            + rawItem);
                        }
                    }
                    case "mail", "not_mail" ->
                            addMailReference(
                                    references,
                                    nodeIssues,
                                    StardewContentReferenceRoles.MAIL,
                                    precondition.getString("id"),
                                    entry.getKey().getNamespace());
                    case "has_secret_note" -> {
                        String rawNote =
                                precondition.getString("id");
                        ResourceLocation note = rawNote == null
                                ? null
                                : ownedReference(
                                        rawNote,
                                        entry.getKey().getNamespace());
                        if (note == null) {
                            nodeIssues.add(
                                    "invalid_secret_note_reference:"
                                            + rawNote);
                        } else {
                            references.add(
                                    StardewContentReference.required(
                                            StardewContentReferenceRoles
                                                    .SECRET_NOTE,
                                            key(StardewContentTypes
                                                            .SECRET_NOTE,
                                                    note)));
                        }
                    }
                    case "saw_event", "not_saw_event" -> {
                        String rawEvent =
                                precondition.getString("id");
                        ResourceLocation eventId = rawEvent == null
                                ? null
                                : ownedReference(
                                        rawEvent,
                                        entry.getKey().getNamespace());
                        if (eventId != null) {
                            references.add(
                                    StardewContentReference.optional(
                                            StardewContentReferenceRoles
                                                    .EVENT_HISTORY,
                                            key(StardewContentTypes
                                                            .CUTSCENE_EVENT,
                                                    eventId)));
                        }
                    }
                    default -> {
                    }
                }
            }
            for (JsonObject command : event.rawCommands()) {
                addCutsceneCommandReferences(
                        owner,
                        command,
                        output,
                        references,
                        nodeIssues,
                        entry.getKey().getNamespace());
            }
            putBuiltin(output, new StardewContentDefinition(
                    owner,
                    entry.getKey(),
                    references,
                    nodeIssues));
        }
    }

    private static void addCutsceneCommandReferences(
            StardewContentKey owner,
            JsonObject command,
            Map<StardewContentKey, StardewContentDefinition> output,
            List<StardewContentReference> references,
            List<String> issues,
            String ownerNamespace
    ) {
        String type = jsonString(command, "cmd");
        if (type == null || type.isBlank()) {
            issues.add("invalid_cutscene_command_type:" + type);
            return;
        }
        if (type.indexOf(':') > 0) {
            ResourceLocation commandType =
                    ResourceLocation.tryParse(type);
            if (commandType == null) {
                issues.add("invalid_cutscene_command_type:" + type);
                return;
            }
            JsonObject data = command.deepCopy();
            data.remove("cmd");
            StardewCutsceneCommands.contentReferences(
                            owner, commandType, data)
                    .resultOrPartial(message -> issues.add(
                            "command_references:" + message))
                    .ifPresent(references::addAll);
            return;
        }
        switch (type) {
            case "spawn_actor", "hide_npc", "show_npc", "speak",
                 "egg_festival_speak", "ice_fishing_speak" ->
                    addOptionalNpcReference(
                            references,
                            jsonString(command, "npc_id"),
                            ownerNamespace);
            case "add_friendship" -> addNpcReference(
                    references,
                    issues,
                    StardewContentReferenceRoles.TARGET_NPC,
                    jsonString(command, "npc"),
                    ownerNamespace,
                    "invalid_command_npc:");
            case "add_item", "remove_item" -> {
                String rawItem = jsonString(command, "item");
                if (!addItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.ACTION_ITEM,
                        rawItem)) {
                    issues.add("invalid_command_item:" + rawItem);
                }
            }
            case "hold_item", "ground_item" -> {
                String rawItem = jsonString(command, "item");
                if (!addItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.DISPLAY_ITEM,
                        rawItem)) {
                    issues.add("invalid_display_item:" + rawItem);
                }
            }
            case "temporary_block" -> {
                String rawBlock = jsonString(command, "block");
                ResourceLocation block = itemReference(rawBlock);
                if (block == null) {
                    issues.add("invalid_display_block:" + rawBlock);
                } else {
                    addRegisteredBlockReference(
                            output,
                            references,
                            StardewContentReferenceRoles.DISPLAY_BLOCK,
                            block);
                }
            }
            case "spawn_entity" -> {
                String rawEntity =
                        jsonString(command, "entity_type");
                ResourceLocation entity = itemReference(rawEntity);
                if (entity == null) {
                    issues.add("invalid_spawn_entity:" + rawEntity);
                } else {
                    addRegisteredEntityTypeReference(
                            output,
                            references,
                            StardewContentReferenceRoles.SPAWN_ENTITY,
                            entity);
                }
            }
            case "add_quest", "remove_quest" -> {
                String rawQuest =
                        jsonString(command, "quest_id");
                ResourceLocation quest =
                        QuestDataLoader.normalizeId(rawQuest);
                if (quest == null) {
                    issues.add("invalid_command_quest:" + rawQuest);
                } else {
                    references.add(StardewContentReference.required(
                            StardewContentReferenceRoles.QUEST,
                            key(StardewContentTypes.QUEST, quest)));
                }
            }
            case "add_mail", "add_mail_now",
                 "add_mail_for_tomorrow" -> addMailReference(
                    references,
                    issues,
                    StardewContentReferenceRoles.MAIL,
                    jsonString(command, "id"),
                    ownerNamespace);
            case "add_recipe" -> addRecipeReference(
                    references,
                    issues,
                    jsonString(command, "recipe"));
            case "apply_unlock_source" -> {
                String rawSource =
                        jsonString(command, "source");
                ResourceLocation source =
                        UnlockSourceData.normalizeSourceId(rawSource);
                if (source == null) {
                    issues.add("invalid_unlock_source:" + rawSource);
                } else {
                    references.add(StardewContentReference.required(
                            StardewContentReferenceRoles.UNLOCK_SOURCE,
                            key(StardewContentTypes.UNLOCK_SOURCE,
                                    source)));
                }
            }
            case "simultaneous" -> addNestedCutsceneCommands(
                    owner,
                    command.getAsJsonArray("commands"),
                    output,
                    references,
                    issues,
                    ownerNamespace);
            case "question" -> {
                JsonArray choices =
                        command.getAsJsonArray("choices");
                if (choices == null) {
                    issues.add("invalid_question_choices");
                    return;
                }
                for (var choice : choices) {
                    if (!choice.isJsonObject()) {
                        issues.add("invalid_question_choice");
                        continue;
                    }
                    addNestedCutsceneCommands(
                            owner,
                            choice.getAsJsonObject()
                                    .getAsJsonArray("commands"),
                            output,
                            references,
                            issues,
                            ownerNamespace);
                }
            }
            default -> {
            }
        }
    }

    private static void addNestedCutsceneCommands(
            StardewContentKey owner,
            JsonArray commands,
            Map<StardewContentKey, StardewContentDefinition> output,
            List<StardewContentReference> references,
            List<String> issues,
            String ownerNamespace
    ) {
        if (commands == null) {
            issues.add("invalid_nested_cutscene_commands");
            return;
        }
        for (var command : commands) {
            if (!command.isJsonObject()) {
                issues.add("invalid_nested_cutscene_command");
                continue;
            }
            addCutsceneCommandReferences(
                    owner,
                    command.getAsJsonObject(),
                    output,
                    references,
                    issues,
                    ownerNamespace);
        }
    }

    private static String jsonString(
            JsonObject object,
            String key
    ) {
        return object.has(key)
                && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString()
                : null;
    }

    private static void addDailyQuestPools(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : DailyQuestPoolRegistry.snapshot()
                .definitions().entrySet()) {
            StardewContentKey owner = key(
                    StardewContentTypes.DAILY_QUEST_POOL,
                    entry.getKey());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            String namespace = entry.getKey().getNamespace();
            entry.getValue().deliveryNpcs().forEach(npc ->
                    addNpcReference(
                            references,
                            nodeIssues,
                            StardewContentReferenceRoles.TARGET_NPC,
                            npc,
                            namespace,
                            "invalid_delivery_npc:"));
            entry.getValue().deliveryItemsBySeason().stream()
                    .flatMap(Collection::stream)
                    .forEach(item -> addRegisteredItemReference(
                            output,
                            references,
                            StardewContentReferenceRoles.OBJECTIVE_ITEM,
                            item));
            entry.getValue().fishBySeason().stream()
                    .flatMap(Collection::stream)
                    .forEach(item -> addRegisteredItemReference(
                            output,
                            references,
                            StardewContentReferenceRoles.OBJECTIVE_ITEM,
                            item));
            entry.getValue().resources().forEach(resource -> {
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.OBJECTIVE_ITEM,
                        resource.item());
                addNpcReference(
                        references,
                        nodeIssues,
                        StardewContentReferenceRoles.TARGET_NPC,
                        resource.targetNpc(),
                        namespace,
                        "invalid_resource_npc:");
            });
            entry.getValue().monsters().forEach(monster -> {
                    addNpcReference(
                            references,
                            nodeIssues,
                            StardewContentReferenceRoles.TARGET_NPC,
                            monster.targetNpc(),
                            namespace,
                            "invalid_monster_npc:");
                addMonsterProfileReferences(
                        owner,
                        monster.type(),
                        references,
                        nodeIssues,
                        "unmapped_daily_quest_monster:");
            });
            putBuiltin(output, new StardewContentDefinition(
                    owner,
                    entry.getKey(),
                    references,
                    nodeIssues));
        }
    }

    private static void addSkills(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (SkillType skill : SkillType.values()) {
            ResourceLocation skillId = id(skill.getName());
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.SKILL, skillId),
                    skillId,
                    List.of()));
        }
    }

    private static void addProfessions(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : ProfessionData.snapshot()
                .definitions().entrySet()) {
            StardewContentKey owner = key(
                    StardewContentTypes.PROFESSION, entry.getKey());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            references.add(StardewContentReference.required(
                    StardewContentReferenceRoles.SKILL,
                    key(StardewContentTypes.SKILL,
                            entry.getValue().skill())));
            entry.getValue().parent().ifPresent(parent ->
                    references.add(StardewContentReference.required(
                            StardewContentReferenceRoles.PARENT_PROFESSION,
                            key(StardewContentTypes.PROFESSION,
                                    parent))));
            entry.getValue().effectHandler().ifPresent(handler -> {
                StardewContentKey handlerKey = key(
                        StardewContentTypes.PROFESSION_EFFECT_HANDLER,
                        handler);
                references.add(StardewContentReference.optional(
                        StardewContentReferenceRoles.EFFECT_HANDLER,
                        handlerKey));
                if (StardewProfessionEffectHandlers.get(handler)
                        .isPresent()) {
                    putBuiltin(output, new StardewContentDefinition(
                            handlerKey, handler, List.of()));
                }
            });
            putBuiltin(output, new StardewContentDefinition(
                    owner, entry.getKey(), references));
        }
    }

    private static void addMasteryRewards(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : MasteryRewardRegistry.snapshot()
                .definitions().entrySet()) {
            StardewContentKey owner = key(
                    StardewContentTypes.MASTERY_REWARD,
                    entry.getKey());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            references.add(StardewContentReference.required(
                    StardewContentReferenceRoles.SKILL,
                    key(StardewContentTypes.SKILL,
                            entry.getValue().skill())));
            entry.getValue().entries().forEach(reward -> {
                reward.item().ifPresent(item ->
                        addRegisteredItemReference(
                                output,
                                references,
                                StardewContentReferenceRoles.REWARD_ITEM,
                                item));
                if (reward.kind()
                        == StardewMasteryRewardDefinition.Kind.RECIPE) {
                    addRecipeReference(
                            references,
                            nodeIssues,
                            reward.recipeId());
                }
            });
            putBuiltin(output, new StardewContentDefinition(
                    owner, entry.getKey(), references, nodeIssues));
        }
    }

    private static void addUnlockSources(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : UnlockSourceData.snapshot()
                .definitions().entrySet()) {
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            entry.getValue().recipes().forEach(recipe ->
                    addRecipeReference(
                            references, nodeIssues, recipe));
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.UNLOCK_SOURCE,
                            entry.getKey()),
                    entry.getKey(),
                    references,
                    nodeIssues));
        }
    }

    private static void addSecretNotes(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : SecretNoteRegistry.snapshot()
                .definitions().entrySet()) {
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            for (var reveal : entry.getValue().giftReveals()) {
                ResourceLocation npc = npcReference(
                        reveal.npc(),
                        entry.getKey().getNamespace());
                if (npc == null) {
                    nodeIssues.add("invalid_gift_reveal_npc:"
                            + reveal.npc());
                    continue;
                }
                references.add(StardewContentReference.required(
                        StardewContentReferenceRoles.TARGET_NPC,
                        key(StardewContentTypes.NPC, npc)));
            }
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.SECRET_NOTE,
                            entry.getKey()),
                    entry.getKey(),
                    references,
                    nodeIssues));
        }
    }

    private static void addLostBooks(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : LostBookRegistry.snapshot()
                .definitions().entrySet()) {
            StardewContentKey owner = key(
                    StardewContentTypes.LOST_BOOK, entry.getKey());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            addConditionReferences(
                    owner,
                    entry.getValue().availableWhen(),
                    references,
                    nodeIssues);
            putBuiltin(output, new StardewContentDefinition(
                    owner, entry.getKey(), references, nodeIssues));
        }
    }

    private static void addMail(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : MailRegistry.snapshot().definitions().entrySet()) {
            ResourceLocation mailId = entry.getKey();
            var mail = entry.getValue();
            StardewContentKey mailKey = key(
                    StardewContentTypes.MAIL, mailId);
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            addConditionReferences(
                    mailKey,
                    mail.availableWhen(),
                    references,
                    nodeIssues);
            addActionReferences(
                    mailKey,
                    mail.onDelivery(),
                    references,
                    nodeIssues);
            addActionReferences(
                    mailKey,
                    mail.onRead(),
                    references,
                    nodeIssues);
            for (var attached : mail.attachedItems()) {
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.ATTACHED_ITEM,
                        attached.item());
            }
            mail.quest().ifPresent(quest ->
                    references.add(StardewContentReference.required(
                            StardewContentReferenceRoles.QUEST,
                            key(StardewContentTypes.QUEST, quest))));
            mail.specialOrder().ifPresent(order ->
                    references.add(StardewContentReference.required(
                            StardewContentReferenceRoles.SPECIAL_ORDER,
                            key(StardewContentTypes.SPECIAL_ORDER,
                                    order))));
            mail.learnedRecipe().ifPresent(raw -> {
                ResourceLocation recipe = ownedReference(
                        raw, mailId.getNamespace());
                if (recipe != null) {
                    references.add(StardewContentReference.required(
                            StardewContentReferenceRoles.LEARNED_RECIPE,
                            key(mail.recipeIsCooking()
                                            ? StardewContentTypes.COOKING_RECIPE
                                            : StardewContentTypes.CRAFTING_RECIPE,
                                    recipe)));
                }
            });
            putBuiltin(output, new StardewContentDefinition(
                    mailKey,
                    mailId,
                    references,
                    nodeIssues));
        }
    }

    private static void addSpecialOrders(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : SpecialOrderDataLoader.snapshot()
                .definitions().entrySet()) {
            ResourceLocation orderId = entry.getKey();
            SpecialOrderDefinition order = entry.getValue();
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            ResourceLocation requester = npcReference(
                    order.requester(), orderId.getNamespace());
            if (requester == null) {
                nodeIssues.add("invalid_requester_npc:"
                        + order.requester());
            } else {
                references.add(StardewContentReference.required(
                        StardewContentReferenceRoles.REQUESTER_NPC,
                        key(StardewContentTypes.NPC, requester)));
            }
            for (SpecialOrderDefinition.ObjectiveDefinition objective
                    : order.objectives()) {
                if (objective.type()
                        != SpecialOrderDefinition.ObjectiveType.DELIVER
                        || objective.targetName().isBlank()
                        || isDynamicToken(objective.targetName())) {
                    continue;
                }
                ResourceLocation target = npcReference(
                        objective.targetName(),
                        orderId.getNamespace());
                if (target == null) {
                    nodeIssues.add("invalid_target_npc:"
                            + objective.targetName());
                } else {
                    references.add(StardewContentReference.required(
                            StardewContentReferenceRoles.TARGET_NPC,
                            key(StardewContentTypes.NPC, target)));
                }
            }
            for (SpecialOrderDefinition.RewardDefinition reward
                    : order.rewards()) {
                if (reward.type()
                        != SpecialOrderDefinition.RewardType.MAIL
                        || reward.mailId().isBlank()
                        || isDynamicToken(reward.mailId())) {
                    continue;
                }
                addMailReference(
                        references,
                        nodeIssues,
                        StardewContentReferenceRoles.REWARD_MAIL,
                        reward.mailId(),
                        orderId.getNamespace());
            }
            if (order.itemToRemoveOnEnd() != null
                    && !order.itemToRemoveOnEnd().isBlank()
                    && !isDynamicToken(order.itemToRemoveOnEnd())) {
                if (!addItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.CLEANUP_ITEM,
                        order.itemToRemoveOnEnd())) {
                    nodeIssues.add("invalid_cleanup_item:"
                            + order.itemToRemoveOnEnd());
                }
            }
            if (order.mailToRemoveOnEnd() != null
                    && !order.mailToRemoveOnEnd().isBlank()
                    && !isDynamicToken(order.mailToRemoveOnEnd())) {
                addMailReference(
                        references,
                        nodeIssues,
                        StardewContentReferenceRoles.CLEANUP_MAIL,
                        order.mailToRemoveOnEnd(),
                        orderId.getNamespace());
            }
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.SPECIAL_ORDER, orderId),
                    orderId,
                    references,
                    nodeIssues));
        }
    }

    private static void addBuildingBlueprints(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var blueprint : BuildingBlueprintRegistry.all()) {
            StardewContentKey blueprintKey = key(
                    StardewContentTypes.BUILDING_BLUEPRINT,
                    blueprint.id());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            addConditionReferences(
                    blueprintKey,
                    blueprint.definition().availableWhen(),
                    references,
                    nodeIssues);
            for (var material
                    : blueprint.definition().materials()) {
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.MATERIAL_ITEM,
                        material.item());
            }
            addRegisteredItemReference(
                    output,
                    references,
                    StardewContentReferenceRoles.RESULT_ITEM,
                    blueprint.definition().resultItem());
            putBuiltin(output, new StardewContentDefinition(
                    blueprintKey,
                    blueprint.id(),
                    references,
                    nodeIssues));
        }
    }

    private static void addFarmLayouts(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var registration : StardewFarmLayouts.allRegistrations()) {
            ResourceLocation layoutId = registration.layout().id();
            StardewContentKey layoutKey = key(
                    StardewContentTypes.FARM_LAYOUT, layoutId);
            putBuiltin(output, new StardewContentDefinition(
                    layoutKey, layoutId, List.of()));
            for (var attachment : registration.attachments()) {
                ResourceLocation attachmentId =
                        layoutAttachmentId(layoutId, attachment.id());
                putBuiltin(output, new StardewContentDefinition(
                        key(StardewContentTypes.FARM_LAYOUT_ATTACHMENT,
                                attachmentId),
                        layoutId,
                        List.of(StardewContentReference.required(
                                StardewContentReferenceRoles.PARENT_LAYOUT,
                                layoutKey))));
            }
        }
    }

    private static void addCommunityBundles(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var bundle : BundleDataManager.getAllBundles()) {
            ResourceLocation bundleId =
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID,
                            "bundle/" + bundle.bundleId());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            for (var ingredient : bundle.ingredients()) {
                if (ingredient.isMoneyIngredient()
                        || ingredient.itemId() == null) {
                    continue;
                }
                if (!addItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.BUNDLE_ITEM,
                        ingredient.itemId())) {
                    nodeIssues.add("invalid_bundle_item:"
                            + ingredient.itemId());
                }
            }
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.COMMUNITY_BUNDLE,
                            bundleId),
                    bundleId,
                    references,
                    nodeIssues));
        }
    }

    private static void addMachineRecipes(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (ResourceLocation machineId
                : StardewMachineTypeRegistry.ids()) {
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.MACHINE, machineId),
                    machineId,
                    List.of()));
        }
        for (var machine
                : StardewMachineTypeRegistry.definitions()) {
            StardewContentKey machineKey = key(
                    StardewContentTypes.MACHINE, machine.id());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            addRegisteredItemReference(
                    output,
                    references,
                    StardewContentReferenceRoles.RESULT_ITEM,
                    machine.itemId());
            for (var auxiliary : machine.auxiliaryInputs()) {
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.INPUT_ITEM,
                        auxiliary.itemId());
            }
            putBuiltin(output, new StardewContentDefinition(
                    machineKey,
                    machine.id(),
                    references));
        }
        for (var entry : ArtisanRecipeDataManager.snapshot()
                .definitions().entrySet()) {
            var recipe = entry.getValue();
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            references.add(StardewContentReference.required(
                    StardewContentReferenceRoles.MACHINE,
                    key(StardewContentTypes.MACHINE,
                            recipe.machine())));
            if (recipe.inputId() != null) {
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.INPUT_ITEM,
                        recipe.inputId());
            }
            if (recipe.outputId() != null) {
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.OUTPUT_ITEM,
                        recipe.outputId());
            }
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.ARTISAN_RECIPE,
                            entry.getKey()),
                    entry.getKey(),
                    references));
        }
    }

    private static void addCraftingRecipes(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : StardewCraftingRecipeData.snapshot()
                .definitions().entrySet()) {
            var recipe = entry.getValue();
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            if (recipe.output() != null
                    && !addItemReference(
                            output,
                            references,
                            StardewContentReferenceRoles.OUTPUT_ITEM,
                            recipe.output().item())) {
                nodeIssues.add("invalid_output_item:"
                        + recipe.output().item());
            }
            for (var ingredient : recipe.ingredients()) {
                if (ingredient.item() == null
                        || ingredient.item().isBlank()) {
                    continue;
                }
                if (!addItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.INGREDIENT_ITEM,
                        ingredient.item())) {
                    nodeIssues.add("invalid_ingredient_item:"
                            + ingredient.item());
                }
            }
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.CRAFTING_RECIPE,
                            entry.getKey()),
                    entry.getKey(),
                    references,
                    nodeIssues));
        }
    }

    private static void addCookingRecipes(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : VanillaCookingRecipeData.snapshot()
                .definitions().entrySet()) {
            var recipe = entry.getValue();
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            addRegisteredItemReference(
                    output,
                    references,
                    StardewContentReferenceRoles.OUTPUT_ITEM,
                    recipe.output());
            for (var ingredient : recipe.ingredients()) {
                ingredient.item().ifPresent(item ->
                        addRegisteredItemReference(
                                output,
                                references,
                                StardewContentReferenceRoles.INGREDIENT_ITEM,
                                item));
            }
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.COOKING_RECIPE,
                            entry.getKey()),
                    entry.getKey(),
                    references));
        }
    }

    private static void addCurrencies(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var currency : StardewCurrencies.definitions()) {
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ResourceLocation iconItem = BuiltInRegistries.ITEM.getKey(
                    currency.icon().getItem());
            if (iconItem != null
                    && currency.icon().getItem() != Items.AIR) {
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.RESULT_ITEM,
                        iconItem);
            }
            putBuiltin(output, new StardewContentDefinition(
                    key(StardewContentTypes.CURRENCY,
                            currency.id()),
                    currency.id(),
                    references));
        }
    }

    private static void addGeodeDrops(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : GeodeDropData.snapshot()
                .definitions().entrySet()) {
            StardewContentKey owner = key(
                    StardewContentTypes.GEODE_DROP, entry.getKey());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            for (ResourceLocation input : entry.getValue().inputs()) {
                addRegisteredItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.INPUT_ITEM,
                        input);
            }
            addConditionReferences(
                    owner,
                    entry.getValue().availableWhen(),
                    references,
                    nodeIssues);
            entry.getValue().entries().forEach(drop ->
                    addItemQueryReferences(
                            owner,
                            drop.query(),
                            references,
                            nodeIssues));
            putBuiltin(output, new StardewContentDefinition(
                    owner, entry.getKey(), references, nodeIssues));
        }
    }

    private static void addPrizeTicketRewards(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : PrizeTicketRewardData.snapshot()
                .definitions().entrySet()) {
            StardewContentKey owner = key(
                    StardewContentTypes.PRIZE_TICKET_REWARD,
                    entry.getKey());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            addConditionReferences(
                    owner,
                    entry.getValue().availableWhen(),
                    references,
                    nodeIssues);
            addItemQueryReferences(
                    owner,
                    entry.getValue().reward(),
                    references,
                    nodeIssues);
            putBuiltin(output, new StardewContentDefinition(
                    owner, entry.getKey(), references, nodeIssues));
        }
    }

    private static void addMineChestRewards(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : MineChestRewardData.snapshot()
                .definitions().entrySet()) {
            StardewContentKey owner = key(
                    StardewContentTypes.MINE_CHEST_REWARD,
                    entry.getKey());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            addConditionReferences(
                    owner,
                    entry.getValue().availableWhen(),
                    references,
                    nodeIssues);
            addItemQueryReferences(
                    owner,
                    entry.getValue().reward(),
                    references,
                    nodeIssues);
            putBuiltin(output, new StardewContentDefinition(
                    owner, entry.getKey(), references, nodeIssues));
        }
    }

    private static void addWorldLootPools(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : WorldLootPoolData.snapshot()
                .definitions().entrySet()) {
            StardewContentKey owner = key(
                    StardewContentTypes.WORLD_LOOT_POOL,
                    entry.getKey());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            addConditionReferences(
                    owner,
                    entry.getValue().availableWhen(),
                    references,
                    nodeIssues);
            entry.getValue().entries().forEach(loot -> {
                addConditionReferences(
                        owner,
                        loot.availableWhen(),
                        references,
                        nodeIssues);
                addItemQueryReferences(
                        owner,
                        loot.query(),
                        references,
                        nodeIssues);
            });
            putBuiltin(output, new StardewContentDefinition(
                    owner, entry.getKey(), references, nodeIssues));
        }
    }

    private static void addFishingTreasurePools(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : FishingTreasurePoolData.snapshot()
                .definitions().entrySet()) {
            StardewContentKey owner = key(
                    StardewContentTypes.FISHING_TREASURE_POOL,
                    entry.getKey());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            addConditionReferences(
                    owner,
                    entry.getValue().availableWhen(),
                    references,
                    nodeIssues);
            entry.getValue().entries().forEach(loot ->
                    addItemQueryReferences(
                            owner,
                            loot.query(),
                            references,
                            nodeIssues));
            putBuiltin(output, new StardewContentDefinition(
                    owner, entry.getKey(), references, nodeIssues));
        }
    }

    private static void addFishingPools(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        FishingDataManager.get().getLocationDataSnapshot()
                .forEach((locationKey, data) -> {
                    ResourceLocation poolId =
                            legacyOrNamespacedContentId(locationKey);
                    StardewContentKey owner = key(
                            StardewContentTypes.FISHING_POOL, poolId);
                    ArrayList<StardewContentReference> references =
                            new ArrayList<>();
                    ArrayList<String> nodeIssues = new ArrayList<>();
                    StardewLocations.resolveId(locationKey)
                            .ifPresent(location -> references.add(
                                    StardewContentReference.optional(
                                            StardewContentReferenceRoles
                                                    .LOCATION,
                                            key(StardewContentTypes.LOCATION,
                                                    location))));
                    data.fish().forEach(rule -> {
                        if (!addItemReference(
                                output,
                                references,
                                StardewContentReferenceRoles.CATCH_ITEM,
                                rule.itemId())) {
                            nodeIssues.add(
                                    "invalid_catch_item:" + rule.itemId());
                        }
                        rule.randomItemIds().forEach(item -> {
                            if (!addItemReference(
                                    output,
                                    references,
                                    StardewContentReferenceRoles.CATCH_ITEM,
                                    item)) {
                                nodeIssues.add(
                                        "invalid_random_catch_item:" + item);
                            }
                        });
                    });
                    putBuiltin(output, new StardewContentDefinition(
                            owner, poolId, references, nodeIssues));
                });
    }

    private static void addFishPondRules(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var rule : FishPondDataService.get().snapshot()) {
            ResourceLocation ruleId =
                    legacyOrNamespacedContentId(rule.id());
            StardewContentKey owner = key(
                    StardewContentTypes.FISH_POND_RULE, ruleId);
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            rule.requiredTags().stream()
                    .filter(tag -> tag.startsWith("item_id:"))
                    .map(tag -> tag.substring("item_id:".length()))
                    .forEach(item -> {
                        if (!addItemReference(
                                output,
                                references,
                                StardewContentReferenceRoles.POND_FISH,
                                item)) {
                            nodeIssues.add(
                                    "invalid_pond_fish:" + item);
                        }
                    });
            rule.producedItems().forEach(produced -> {
                if (!addQualifiedItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.POND_PRODUCT,
                        produced.itemId())) {
                    nodeIssues.add(
                            "invalid_pond_product:" + produced.itemId());
                }
            });
            rule.populationGates().values().forEach(choices ->
                    choices.forEach(choice -> {
                        String item = choice.trim().split("\\s+")[0];
                        if (!addQualifiedItemReference(
                                output,
                                references,
                                StardewContentReferenceRoles
                                        .POPULATION_GATE_ITEM,
                                item)) {
                            nodeIssues.add(
                                    "invalid_population_gate_item:" + item);
                        }
                    }));
            putBuiltin(output, new StardewContentDefinition(
                    owner, ruleId, references, nodeIssues));
        }
    }

    private static void addMuseumRewards(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var reward : MuseumRewardRegistry.getAllRewards()) {
            ResourceLocation rewardId =
                    legacyOrNamespacedContentId(reward.id());
            StardewContentKey owner = key(
                    StardewContentTypes.MUSEUM_REWARD, rewardId);
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            reward.requiredIds().forEach(item -> {
                if (!addItemReference(
                        output,
                        references,
                        StardewContentReferenceRoles.REQUIRED_ITEM,
                        item)) {
                    nodeIssues.add(
                            "invalid_required_item:" + item);
                }
            });
            addActionReferences(
                    owner, reward.actions(), references, nodeIssues);
            putBuiltin(output, new StardewContentDefinition(
                    owner, rewardId, references, nodeIssues));
        }
    }

    private static void addMonsterSlayerGoals(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var goal : MonsterSlayerGoalRegistry.getAllGoals()) {
            ResourceLocation goalId =
                    legacyOrNamespacedContentId(goal.goalKey());
            StardewContentKey owner = key(
                    StardewContentTypes.MONSTER_SLAYER_GOAL, goalId);
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            goal.monsterTags().forEach(tag ->
                    addMonsterProfileReferences(
                            owner,
                            tag,
                            references,
                            nodeIssues,
                            "unmapped_slayer_monster:"));
            addActionReferences(
                    owner, goal.rewards(), references, nodeIssues);
            putBuiltin(output, new StardewContentDefinition(
                    owner, goalId, references, nodeIssues));
        }
    }

    private static void addMineMonsterProfiles(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        MineMonsterSpawnHandler.ensureProfilesRegistered();
        for (var profile : StardewMineMonsterProfiles.all()) {
            StardewContentKey owner = key(
                    StardewContentTypes.MINE_MONSTER_PROFILE,
                    profile.id());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            addRegisteredEntityTypeReference(
                    output,
                    references,
                    StardewContentReferenceRoles.SPAWN_ENTITY,
                    BuiltInRegistries.ENTITY_TYPE.getKey(
                            profile.entityType()));
            putBuiltin(output, new StardewContentDefinition(
                    owner, profile.id(), references));
        }
    }

    private static void addMineMonsterSpawnTables(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var entry : MineMonsterSpawnTableData.snapshot()
                .definitions().entrySet()) {
            StardewContentKey owner = key(
                    StardewContentTypes.MINE_MONSTER_SPAWN_TABLE,
                    entry.getKey());
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            entry.getValue().themes().forEach(theme ->
                    references.add(StardewContentReference.required(
                            StardewContentReferenceRoles.MINE_THEME,
                            key(StardewContentTypes.MINE_THEME, theme))));
            entry.getValue().mechanics().forEach(mechanic ->
                    MineThemeData.snapshot().definitions().entrySet()
                            .stream()
                            .filter(theme -> theme.getValue().mechanicId()
                                    .equals(mechanic))
                            .forEach(theme -> references.add(
                                    StardewContentReference.required(
                                            StardewContentReferenceRoles
                                                    .MINE_THEME,
                                            key(StardewContentTypes.MINE_THEME,
                                                    theme.getKey())))));
            entry.getValue().entries().forEach(spawn ->
                    references.add(StardewContentReference.required(
                            StardewContentReferenceRoles.MONSTER_PROFILE,
                            key(StardewContentTypes.MINE_MONSTER_PROFILE,
                                    spawn.profile()))));
            putBuiltin(output, new StardewContentDefinition(
                    owner, entry.getKey(), references));
        }
    }

    private static void addMonsterProfileReferences(
            StardewContentKey owner,
            String progressTag,
            List<StardewContentReference> references,
            List<String> issues,
            String issuePrefix
    ) {
        MineMonsterSpawnHandler.ensureProfilesRegistered();
        List<com.stardew.craft.api.v1.mining
                .StardewMineMonsterProfile> matches =
                StardewMineMonsterProfiles.all().stream()
                        .filter(profile -> profile.progressTags()
                                .contains(progressTag))
                        .toList();
        if (matches.isEmpty()) {
            issues.add(issuePrefix + progressTag);
            return;
        }
        matches.forEach(profile -> references.add(
                StardewContentReference.required(
                        StardewContentReferenceRoles.MONSTER_PROFILE,
                        key(StardewContentTypes.MINE_MONSTER_PROFILE,
                                profile.id()))));
    }

    private static void addArtifactSpotPools(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (var pool : StardewArtifactSpotDrops.snapshot()) {
            ResourceLocation poolId =
                    legacyOrNamespacedContentId(pool.group());
            StardewContentKey owner = key(
                    StardewContentTypes.ARTIFACT_SPOT_POOL, poolId);
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            StardewLocations.resolveId(pool.group())
                    .ifPresent(location -> references.add(
                            StardewContentReference.optional(
                                    StardewContentReferenceRoles.LOCATION,
                                    key(StardewContentTypes.LOCATION,
                                            location))));
            pool.entries().forEach(entry ->
                    entry.items().forEach(item ->
                            addRegisteredItemReference(
                                    output,
                                    references,
                                    StardewContentReferenceRoles.DROP_ITEM,
                                    item)));
            putBuiltin(output, new StardewContentDefinition(
                    owner, poolId, references));
        }
    }

    private static void addArtifactSpotDropProviders(
            Map<StardewContentKey, StardewContentDefinition> output
    ) {
        for (ResourceLocation providerId :
                StardewArtifactSpotDropRegistry.registeredIds()) {
            StardewContentKey owner = key(
                    StardewContentTypes.ARTIFACT_SPOT_DROP_PROVIDER,
                    providerId);
            ArrayList<StardewContentReference> references =
                    new ArrayList<>();
            ArrayList<String> nodeIssues = new ArrayList<>();
            StardewArtifactSpotDropRegistry.contentReferences(
                            providerId, owner)
                    .resultOrPartial(message -> nodeIssues.add(
                            "provider_references:" + message))
                    .ifPresent(references::addAll);
            putBuiltin(output, new StardewContentDefinition(
                    owner, providerId, references, nodeIssues));
        }
    }

    private static void addConditionReferences(
            StardewContentKey owner,
            Collection<com.stardew.craft.api.v1.condition
                    .StardewCondition> conditions,
            List<StardewContentReference> references,
            List<String> issues
    ) {
        for (var condition : conditions) {
            StardewConditions.contentReferences(owner, condition)
                    .resultOrPartial(message -> issues.add(
                            "condition_references:" + message))
                    .ifPresent(references::addAll);
        }
    }

    private static void addItemQueryReferences(
            StardewContentKey owner,
            StardewItemQuery query,
            List<StardewContentReference> references,
            List<String> issues
    ) {
        StardewItemQueries.contentReferences(owner, query)
                .resultOrPartial(message -> issues.add(
                        "item_query_references:" + message))
                .ifPresent(references::addAll);
    }

    private static void addActionReferences(
            StardewContentKey owner,
            Collection<com.stardew.craft.api.v1.action
                    .StardewAction> actions,
            List<StardewContentReference> references,
            List<String> issues
    ) {
        for (var action : actions) {
            StardewActions.contentReferences(owner, action)
                    .resultOrPartial(message -> issues.add(
                            "action_references:" + message))
                    .ifPresent(references::addAll);
        }
    }

    private static boolean addItemReference(
            Map<StardewContentKey, StardewContentDefinition> output,
            List<StardewContentReference> references,
            ResourceLocation role,
            String raw
    ) {
        if (isVirtualProduct(raw)) {
            return true;
        }
        ResourceLocation itemId = itemReference(raw);
        if (itemId == null) {
            return false;
        }
        StardewContentKey itemKey = key(
                StardewContentTypes.ITEM, itemId);
        references.add(StardewContentReference.required(
                role, itemKey));
        if (registeredItem(itemId)) {
            putBuiltin(output, new StardewContentDefinition(
                    itemKey, ITEM_REGISTRY_SOURCE, List.of()));
        }
        return true;
    }

    private static boolean addQualifiedItemReference(
            Map<StardewContentKey, StardewContentDefinition> output,
            List<StardewContentReference> references,
            ResourceLocation role,
            String raw
    ) {
        ResourceLocation itemId = FishPondQualifiedItemService.resolve(raw)
                .map(FishPondQualifiedItemService.ResolvedItem::registryId)
                .orElse(null);
        if (itemId == null) {
            return false;
        }
        addRegisteredItemReference(
                output, references, role, itemId);
        return true;
    }

    private static void addRegisteredItemReference(
            Map<StardewContentKey, StardewContentDefinition> output,
            List<StardewContentReference> references,
            ResourceLocation role,
            ResourceLocation itemId
    ) {
        StardewContentKey itemKey = key(
                StardewContentTypes.ITEM, itemId);
        references.add(StardewContentReference.required(
                role, itemKey));
        if (registeredItem(itemId)) {
            putBuiltin(output, new StardewContentDefinition(
                    itemKey, ITEM_REGISTRY_SOURCE, List.of()));
        }
    }

    private static void addRegisteredBlockReference(
            Map<StardewContentKey, StardewContentDefinition> output,
            List<StardewContentReference> references,
            ResourceLocation role,
            ResourceLocation blockId
    ) {
        StardewContentKey blockKey = key(
                StardewContentTypes.BLOCK, blockId);
        references.add(StardewContentReference.required(
                role, blockKey));
        if (registeredBlock(blockId)) {
            putBuiltin(output, new StardewContentDefinition(
                    blockKey, BLOCK_REGISTRY_SOURCE, List.of()));
        }
    }

    private static void addRegisteredEntityTypeReference(
            Map<StardewContentKey, StardewContentDefinition> output,
            List<StardewContentReference> references,
            ResourceLocation role,
            ResourceLocation entityTypeId
    ) {
        StardewContentKey entityTypeKey = key(
                StardewContentTypes.ENTITY_TYPE, entityTypeId);
        references.add(StardewContentReference.required(
                role, entityTypeKey));
        if (registeredEntityType(entityTypeId)) {
            putBuiltin(output, new StardewContentDefinition(
                    entityTypeKey,
                    ENTITY_TYPE_REGISTRY_SOURCE,
                    List.of()));
        }
    }

    private static void addRecipeReference(
            List<StardewContentReference> references,
            List<String> issues,
            String raw
    ) {
        ResourceLocation recipeId =
                RecipeIdNormalizer.definitionId(raw);
        if (recipeId == null) {
            issues.add("invalid_recipe_reference:" + raw);
            return;
        }
        ResourceLocation recipeType =
                VanillaCookingRecipeData.snapshot().definitions()
                        .containsKey(recipeId)
                        ? StardewContentTypes.COOKING_RECIPE
                        : StardewContentTypes.CRAFTING_RECIPE;
        references.add(StardewContentReference.required(
                StardewContentReferenceRoles.LEARNED_RECIPE,
                key(recipeType, recipeId)));
    }

    private static void addMailReference(
            List<StardewContentReference> references,
            List<String> issues,
            ResourceLocation role,
            String raw,
            String ownerNamespace
    ) {
        ResourceLocation mail = raw == null
                ? null
                : raw.indexOf(':') >= 0
                        ? ResourceLocation.tryParse(raw)
                        : StardewCraft.MODID.equals(ownerNamespace)
                                ? MailRegistry.normalizeId(raw)
                                : ResourceLocation.tryBuild(
                                        ownerNamespace, raw);
        if (mail == null) {
            issues.add("invalid_mail_reference:" + raw);
            return;
        }
        references.add(StardewContentReference.required(
                role,
                key(StardewContentTypes.MAIL, mail)));
    }

    private static void addNpcReference(
            List<StardewContentReference> references,
            List<String> issues,
            ResourceLocation role,
            String raw,
            String ownerNamespace,
            String issuePrefix
    ) {
        ResourceLocation npc = npcReference(raw, ownerNamespace);
        if (npc == null) {
            issues.add(issuePrefix + raw);
            return;
        }
        references.add(StardewContentReference.required(
                role,
                key(StardewContentTypes.NPC, npc)));
    }

    private static void addOptionalNpcReference(
            List<StardewContentReference> references,
            String raw,
            String ownerNamespace
    ) {
        if ("player".equalsIgnoreCase(raw)
                || isDynamicToken(raw)) {
            return;
        }
        ResourceLocation npc = npcReference(raw, ownerNamespace);
        if (npc != null) {
            references.add(StardewContentReference.optional(
                    StardewContentReferenceRoles.TARGET_NPC,
                    key(StardewContentTypes.NPC, npc)));
        }
    }

    private static void addProvider(
            ResourceLocation registrationId,
            StardewContentProvider provider,
            Map<StardewContentKey, StardewContentDefinition> output,
            List<StardewContentIssue> issues
    ) {
        Collection<StardewContentDefinition> provided;
        try {
            provided = provider.definitions();
        } catch (RuntimeException exception) {
            issues.add(new StardewContentIssue(
                    StardewContentIssue.Severity.ERROR,
                    registrationId,
                    null,
                    "Content provider failed: "
                            + exception.getClass().getSimpleName()
                            + ": " + exception.getMessage()));
            return;
        }
        if (provided == null) {
            issues.add(new StardewContentIssue(
                    StardewContentIssue.Severity.ERROR,
                    registrationId,
                    null,
                    "Content provider returned null"));
            return;
        }
        for (StardewContentDefinition definition : provided) {
            if (definition == null) {
                issues.add(new StardewContentIssue(
                        StardewContentIssue.Severity.ERROR,
                        registrationId,
                        null,
                        "Content provider returned a null definition"));
                continue;
            }
            StardewContentDefinition previous =
                    output.putIfAbsent(
                            definition.key(), definition);
            if (previous != null) {
                issues.add(new StardewContentIssue(
                        StardewContentIssue.Severity.ERROR,
                        registrationId,
                        definition.key(),
                        "Content projection is already owned by "
                                + previous.source()));
            }
        }
    }

    private static StardewContentNodeSnapshot resolve(
            StardewContentDefinition definition,
            Map<StardewContentKey, StardewContentDefinition> definitions,
            Map<StardewContentKey, StardewContentKey> aliases
    ) {
        List<StardewContentReferenceSnapshot> references =
                definition.references().stream()
                        .sorted(Comparator
                                .comparing((StardewContentReference value) ->
                                        value.role().toString())
                                .thenComparing(
                                        StardewContentReference::target,
                                        KEY_ORDER))
                        .map(reference ->
                                new StardewContentReferenceSnapshot(
                                        definition.key(),
                                        reference.role(),
                                        reference.target(),
                                        reference.required(),
                                        isResolved(
                                                reference.target(),
                                                definitions,
                                                aliases)))
                        .toList();
        return new StardewContentNodeSnapshot(
                definition.key(),
                definition.source(),
                references,
                definition.issues());
    }

    private static boolean isResolved(
            StardewContentKey target,
            Map<StardewContentKey, StardewContentDefinition> definitions,
            Map<StardewContentKey, StardewContentKey> aliases
    ) {
        return definitions.containsKey(target)
                || aliases.containsKey(target)
                || (target.type().equals(StardewContentTypes.ITEM)
                && registeredItem(target.id()))
                || (target.type().equals(StardewContentTypes.BLOCK)
                && registeredBlock(target.id()))
                || (target.type().equals(StardewContentTypes.ENTITY_TYPE)
                && registeredEntityType(target.id()));
    }

    private static StardewContentAliasResolver.Result buildAliases(
            Map<StardewContentKey, StardewContentDefinition> definitions,
            List<StardewContentIssue> issues
    ) {
        ArrayList<StardewContentAliasResolver.Declaration>
                declarations = new ArrayList<>();
        for (var registration : ALIAS_PROVIDERS.entries()) {
            Collection<StardewContentAlias> provided;
            try {
                provided = ALIAS_PROVIDERS.invoke(
                        registration,
                        StardewContentAliasProvider::aliases);
            } catch (RuntimeException | Error exception) {
                issues.add(new StardewContentIssue(
                        StardewContentIssue.Severity.ERROR,
                        registration.id(),
                        null,
                        "Content alias provider failed: "
                                + exception.getClass().getSimpleName()
                                + ": " + exception.getMessage()));
                continue;
            }
            if (provided == null) {
                issues.add(new StardewContentIssue(
                        StardewContentIssue.Severity.ERROR,
                        registration.id(),
                        null,
                        "Content alias provider returned null"));
                continue;
            }
            for (StardewContentAlias alias : provided) {
                if (declarations.size() >= MAX_CONTENT_ALIASES) {
                    issues.add(new StardewContentIssue(
                            StardewContentIssue.Severity.ERROR,
                            registration.id(),
                            null,
                            "Content alias count exceeds "
                                    + MAX_CONTENT_ALIASES));
                    return StardewContentAliasResolver.resolve(
                            declarations,
                            key -> isResolved(
                                    key, definitions, Map.of()));
                }
                if (alias == null) {
                    issues.add(new StardewContentIssue(
                            StardewContentIssue.Severity.ERROR,
                            registration.id(),
                            null,
                            "Content alias provider returned a null alias"));
                    continue;
                }
                declarations.add(
                        new StardewContentAliasResolver.Declaration(
                                registration.id(), alias));
            }
        }
        return StardewContentAliasResolver.resolve(
                declarations,
                key -> isResolved(key, definitions, Map.of()));
    }

    private static void putBuiltin(
            Map<StardewContentKey, StardewContentDefinition> output,
            StardewContentDefinition definition
    ) {
        output.putIfAbsent(definition.key(), definition);
    }

    private static ResourceLocation locationReference(
            String raw,
            String ownerNamespace
    ) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return StardewLocations.resolveId(raw)
                .orElseGet(() -> ownedReference(
                        raw, ownerNamespace));
    }

    private static ResourceLocation npcReference(
            String raw,
            String ownerNamespace
    ) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return StardewCraft.MODID.equals(ownerNamespace)
                ? StardewNpcInteractions.normalizeNpcId(raw)
                : ownedReference(raw, ownerNamespace);
    }

    private static ResourceLocation shopReference(
            String raw,
            String ownerNamespace
    ) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation direct = ResourceLocation.tryParse(raw);
        if (direct != null
                && ShopDataLoader.getDefinition(direct) != null) {
            return direct;
        }
        ResourceLocation legacy =
                ShopDataLoader.snapshot().definitions().entrySet()
                        .stream()
                        .filter(entry -> entry.getValue().legacyId()
                                .equals(raw))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
        return legacy != null
                ? legacy
                : ownedReference(raw, ownerNamespace);
    }

    private static ResourceLocation overlayReference(
            String raw,
            String ownerNamespace
    ) {
        ResourceLocation direct = ResourceLocation.tryParse(raw);
        if (direct != null
                && FestivalMapOverlayRegistry.findAddon(direct)
                        .isPresent()) {
            return direct;
        }
        if (FestivalMapOverlayRegistry.get(raw).isPresent()) {
            return legacyOverlayId(raw);
        }
        return ownedReference(raw, ownerNamespace);
    }

    private static ResourceLocation legacyOverlayId(String raw) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID,
                "legacy/" + safePath(raw));
    }

    private static ResourceLocation legacyOrNamespacedContentId(
            String raw
    ) {
        ResourceLocation direct = raw.indexOf(':') >= 0
                ? ResourceLocation.tryParse(raw.toLowerCase(Locale.ROOT))
                : null;
        return direct != null
                ? direct
                : ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID,
                        "legacy/" + safePath(raw));
    }

    private static ResourceLocation itemReference(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(':');
        return separator >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(
                        StardewCraft.MODID, normalized);
    }

    private static boolean isVirtualProduct(String raw) {
        if (raw == null) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(':');
        return separator > 0 && VIRTUAL_PRODUCT_NAMESPACES.contains(
                normalized.substring(0, separator));
    }

    private static boolean isDynamicToken(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        return normalized.startsWith("{")
                && normalized.endsWith("}");
    }

    private static boolean registeredItem(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id)
                && BuiltInRegistries.ITEM.get(id) != Items.AIR;
    }

    private static boolean registeredBlock(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.containsKey(id)
                && BuiltInRegistries.BLOCK.get(id) != Blocks.AIR;
    }

    private static boolean registeredEntityType(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }

    private static ResourceLocation ownedReference(
            String raw,
            String ownerNamespace
    ) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(
                        ownerNamespace, normalized);
    }

    private static ResourceLocation animalContentId(
            String raw,
            String ownerNamespace
    ) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(
                        ownerNamespace, safePath(normalized));
    }

    private static String safePath(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            result.append((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_' || character == '-'
                    || character == '.' || character == '/'
                    ? character : '_');
        }
        return result.toString();
    }

    private static ResourceLocation layoutAttachmentId(
            ResourceLocation layoutId,
            ResourceLocation attachmentId
    ) {
        return ResourceLocation.fromNamespaceAndPath(
                layoutId.getNamespace(),
                "layout_attachment/" + layoutId.getPath()
                        + "/" + attachmentId.getNamespace()
                        + "/" + attachmentId.getPath());
    }

    private static StardewContentKey key(
            ResourceLocation type,
            ResourceLocation id
    ) {
        return new StardewContentKey(type, id);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, path);
    }
}
