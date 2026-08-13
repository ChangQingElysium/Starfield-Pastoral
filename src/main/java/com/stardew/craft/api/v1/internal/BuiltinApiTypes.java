package com.stardew.craft.api.v1.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.action.StardewActionResult;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.content.StardewContentKey;
import com.stardew.craft.api.v1.content.StardewContentReference;
import com.stardew.craft.api.v1.content.StardewContentReferenceRoles;
import com.stardew.craft.api.v1.content.StardewContentTypes;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.query.StardewItemQuery;
import com.stardew.craft.quest.data.BuiltinQuestObjectiveTypes;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.quest.QuestDataLoader;
import com.stardew.craft.quest.QuestManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Registers the built-in v1 condition, query and action types. */
public final class BuiltinApiTypes {
    private static boolean bootstrapped;

    private BuiltinApiTypes() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        registerConditions();
        registerItemQueries();
        registerActions();
        BuiltinQuestObjectiveTypes.registerAll();
        bootstrapped = true;
    }

    private static void registerConditions() {
        StardewConditions.register(id("always"), AlwaysCondition.CODEC,
                (context, data) -> data.value());

        StardewConditions.register(id("has_item"), HasItemCondition.CODEC, (context, data) -> {
            if (context.player() == null || !BuiltInRegistries.ITEM.containsKey(data.item())) {
                return false;
            }
            Item item = BuiltInRegistries.ITEM.get(data.item());
            return context.player().getInventory().countItem(item) >= data.count();
        }, (owner, data) -> List.of(reference(
                StardewContentReferenceRoles.CONDITION_ITEM,
                StardewContentTypes.ITEM,
                data.item())));

        StardewConditions.register(id("lacks_item"), HasItemCondition.CODEC, (context, data) -> {
            if (context.player() == null || !BuiltInRegistries.ITEM.containsKey(data.item())) {
                return false;
            }
            Item item = BuiltInRegistries.ITEM.get(data.item());
            return context.player().getInventory().countItem(item) < data.count();
        }, (owner, data) -> List.of(reference(
                StardewContentReferenceRoles.CONDITION_ITEM,
                StardewContentTypes.ITEM,
                data.item())));

        StardewConditions.register(id("money"), MoneyCondition.CODEC, (context, data) -> {
            if (context.player() == null) {
                return false;
            }
            int money = PlayerStardewDataAPI.getMoney(context.player());
            return money >= data.min() && money <= data.max();
        });

        StardewConditions.register(id("flag"), FlagCondition.CODEC, (context, data) -> {
            if (context.player() == null) {
                return false;
            }
            PlayerStardewData playerData = PlayerDataManager.getPlayerData(context.player());
            return playerData.hasMailFlag(data.id()) == data.present();
        });

        StardewConditions.register(id("skill"), SkillCondition.CODEC, (context, data) -> {
            if (context.player() == null) return false;
            com.stardew.craft.player.SkillType skill =
                    com.stardew.craft.player.SkillType.fromName(data.skill());
            return skill != null
                    && PlayerDataManager.getPlayerData(context.player()).getRawSkillLevel(skill) >= data.level();
        });

        StardewConditions.register(id("season"), SeasonCondition.CODEC, (context, data) -> {
            int current = com.stardew.craft.time.StardewTimeManager.get().getCurrentSeason();
            String season = switch (current) {
                case 1 -> "summer";
                case 2 -> "fall";
                case 3 -> "winter";
                default -> "spring";
            };
            return data.seasons().contains(season);
        });

        StardewConditions.register(id("time"), TimeCondition.CODEC,
                (context, data) -> data.includes(
                        com.stardew.craft.time.StardewTimeManager
                                .get().getCurrentTime()));

        StardewConditions.register(id("seen_event"), SeenEventCondition.CODEC, (context, data) -> {
            if (context.player() == null) {
                return false;
            }
            java.util.UUID playerId = context.player().getUUID();
            if ("host".equals(data.scope())) {
                java.util.UUID owner =
                        com.stardew.craft.farm.FarmInstanceRegistry.get(
                                        context.level().getServer())
                                .getOwnerForPlayer(playerId);
                if (owner != null) {
                    playerId = owner;
                }
            }
            return com.stardew.craft.cutscene.server.EventSeenData.get(context.level())
                    .hasSeen(playerId, data.id());
        });

        StardewConditions.register(
                id("location"), LocationCondition.CODEC,
                (context, data) -> {
                    if (context.player() == null) {
                        return false;
                    }
                    var location = com.stardew.craft.api.v1.world
                            .StardewLocations.find(
                                    context.level(),
                                    context.player().blockPosition())
                            .orElse(null);
                    if (location == null) {
                        return false;
                    }
                    if (!data.locations().isEmpty()
                            && data.locations().stream().noneMatch(
                                    expected -> com.stardew.craft.api.v1
                                            .world.StardewLocations.isWithin(
                                                    location.id(),
                                                    expected))) {
                        return false;
                    }
                    if (!location.tags().containsAll(
                            data.requiredTags())) {
                        return false;
                    }
                    if (data.excludedTags().stream()
                            .anyMatch(location.tags()::contains)) {
                        return false;
                    }
                    return data.properties().entrySet().stream()
                            .allMatch(entry -> entry.getValue().equals(
                                    location.properties().get(
                                            entry.getKey())));
                },
                (owner, data) -> data.locations().stream()
                        .map(location -> reference(
                                StardewContentReferenceRoles.LOCATION,
                                StardewContentTypes.LOCATION,
                                location))
                        .toList());
    }

    private static void registerItemQueries() {
        StardewItemQueries.register(id("item"), DirectItemQuery.CODEC, (context, data) -> {
            if (!BuiltInRegistries.ITEM.containsKey(data.item())) {
                return List.of();
            }
            return List.of(new ItemStack(BuiltInRegistries.ITEM.get(data.item()), data.count()));
        }, (owner, data) -> List.of(reference(
                StardewContentReferenceRoles.QUERY_ITEM,
                StardewContentTypes.ITEM,
                data.item())));

        StardewItemQueries.register(id("random_tag"), RandomTagQuery.CODEC, (context, data) -> {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, data.tag());
            var holders = BuiltInRegistries.ITEM.getTag(tag).orElse(null);
            if (holders == null || holders.size() == 0) {
                return List.of();
            }
            Item item = holders.get(context.random().nextInt(holders.size())).value();
            return List.of(new ItemStack(item, data.count()));
        });

        StardewItemQueries.register(id("one_of"), OneOfItemQuery.CODEC, (context, data) -> {
            ArrayList<Item> candidates = new ArrayList<>();
            for (ResourceLocation itemId : data.items()) {
                if (BuiltInRegistries.ITEM.containsKey(itemId)) {
                    candidates.add(BuiltInRegistries.ITEM.get(itemId));
                }
            }
            if (candidates.isEmpty()) {
                return List.of();
            }
            Item item = candidates.get(context.random().nextInt(candidates.size()));
            return List.of(new ItemStack(item, data.count()));
        }, (owner, data) -> data.items().stream()
                .map(item -> reference(
                        StardewContentReferenceRoles.QUERY_ITEM,
                        StardewContentTypes.ITEM,
                        item))
                .toList());

        StardewItemQueries.register(id("random_count"), RandomCountItemQuery.CODEC, (context, data) -> {
            int count = data.minCount() == data.maxCount()
                    ? data.minCount()
                    : data.minCount() + context.random().nextInt(data.maxCount() - data.minCount() + 1);
            return StardewItemQueries.resolve(data.query(), context).result().orElse(List.of()).stream()
                    .map(stack -> {
                        ItemStack copy = stack.copy();
                        copy.setCount(count);
                        return copy;
                    })
                    .toList();
        }, (owner, data) -> StardewItemQueries.contentReferences(
                owner, data.query()).getOrThrow());

        StardewItemQueries.register(id("one_of_queries"), OneOfQueriesItemQuery.CODEC, (context, data) -> {
            StardewItemQuery selected = data.queries().get(context.random().nextInt(data.queries().size()));
            return StardewItemQueries.resolve(selected, context).result().orElse(List.of());
        }, (owner, data) -> data.queries().stream()
                .flatMap(query -> StardewItemQueries.contentReferences(
                        owner, query).getOrThrow().stream())
                .toList());

        StardewItemQueries.register(id("weighted"), WeightedItemQuery.CODEC, (context, data) -> {
            long totalWeight = data.entries().stream().mapToLong(WeightedQueryEntry::weight).sum();
            long roll = context.random().nextLong(totalWeight);
            for (WeightedQueryEntry entry : data.entries()) {
                roll -= entry.weight();
                if (roll < 0) {
                    return StardewItemQueries.resolve(entry.query(), context).result().orElse(List.of());
                }
            }
            return List.of();
        }, (owner, data) -> data.entries().stream()
                .flatMap(entry -> StardewItemQueries.contentReferences(
                        owner, entry.query()).getOrThrow().stream())
                .toList());
    }

    private static void registerActions() {
        StardewActions.register(id("set_flag"), SetFlagAction.CODEC, (context, data) -> {
            PlayerStardewData playerData = PlayerDataManager.getPlayerData(context.player());
            playerData.addMailFlag(data.id());
            if ("wizardFirstMet".equals(data.id())) {
                playerData.setWizardFirstMet(true);
            }
            PlayerDataEventHandler.syncPlayerData(context.player(), playerData);
            if ("canReadJunimoText".equals(data.id())) {
                com.stardew.craft.communitycenter.network.BundleSyncPayload.sendFullSync(context.player());
            }
            if (com.stardew.craft.specialorder.SpecialOrderManager.BOARD_UNLOCK_FLAG.equals(data.id())) {
                var stardewLevel = context.player().server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
                if (stardewLevel != null) {
                    com.stardew.craft.specialorder.SpecialOrderBoardInstaller.get(stardewLevel)
                            .ensurePlaced(stardewLevel);
                }
                com.stardew.craft.specialorder.SpecialOrderManager.syncState(context.player());
            }
            return StardewActionResult.ok();
        });

        StardewActions.register(id("add_money"), AddMoneyAction.CODEC, (context, data) -> {
            PlayerStardewDataAPI.addMoney(context.player(), data.amount());
            return StardewActionResult.ok();
        });

        StardewActions.register(id("add_item"), AddItemAction.CODEC, (context, data) -> {
            if (!BuiltInRegistries.ITEM.containsKey(data.item())) {
                return StardewActionResult.failure("Unknown item: " + data.item());
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(data.item()), data.count());
            if (!context.player().getInventory().add(stack)) {
                context.player().drop(stack, false);
            }
            return StardewActionResult.ok();
        }, (owner, data) -> List.of(reference(
                StardewContentReferenceRoles.ACTION_ITEM,
                StardewContentTypes.ITEM,
                data.item())));

        StardewActions.register(id("remove_item"), RemoveItemAction.CODEC, (context, data) -> {
            if (!BuiltInRegistries.ITEM.containsKey(data.item())) {
                return StardewActionResult.failure("Unknown item: " + data.item());
            }
            Item item = BuiltInRegistries.ITEM.get(data.item());
            if (context.player().getInventory().countItem(item) < data.count()) {
                return StardewActionResult.failure("Player lacks " + data.count() + "x " + data.item());
            }
            int remaining = data.count();
            for (int slot = 0;
                 slot < context.player().getInventory().getContainerSize() && remaining > 0;
                 slot++) {
                ItemStack stack = context.player().getInventory().getItem(slot);
                if (!stack.is(item)) {
                    continue;
                }
                int removed = Math.min(stack.getCount(), remaining);
                stack.shrink(removed);
                remaining -= removed;
            }
            context.player().inventoryMenu.broadcastChanges();
            return StardewActionResult.ok();
        }, (owner, data) -> List.of(reference(
                StardewContentReferenceRoles.ACTION_ITEM,
                StardewContentTypes.ITEM,
                data.item())));

        StardewActions.register(id("start_quest"), StartQuestAction.CODEC, (context, data) -> {
            if (QuestDataLoader.createQuest(data.quest()) == null) {
                return StardewActionResult.failure("Unknown quest: " + data.quest());
            }
            QuestManager manager = QuestManager.of(context.player());
            if (manager == null) {
                return StardewActionResult.failure("Player quest manager is unavailable");
            }
            manager.acceptQuest(data.quest(), context.player());
            return StardewActionResult.ok();
        }, (owner, data) -> questReference(
                owner, data.quest()));

        StardewActions.register(id("remove_quest"), RemoveQuestAction.CODEC, (context, data) -> {
            QuestManager manager = QuestManager.of(context.player());
            if (manager == null) {
                return StardewActionResult.failure("Player quest manager is unavailable");
            }
            manager.removeQuest(data.quest(), context.player());
            return StardewActionResult.ok();
        }, (owner, data) -> questReference(
                owner, data.quest()));

        StardewActions.register(id("apply_unlock_source"), ApplyUnlockSourceAction.CODEC, (context, data) -> {
            if (!com.stardew.craft.player.UnlockSourceData.hasSource(data.source())) {
                return StardewActionResult.failure("Unknown unlock source: " + data.source());
            }
            PlayerStardewDataAPI.applyUnlockSource(context.player(), data.source());
            return StardewActionResult.ok();
        });
    }

    private static StardewContentReference reference(
            ResourceLocation role,
            ResourceLocation type,
            ResourceLocation target
    ) {
        return StardewContentReference.required(
                role, new StardewContentKey(type, target));
    }

    private static List<StardewContentReference> questReference(
            StardewContentKey owner,
            String rawQuest
    ) {
        ResourceLocation quest = ownedReference(owner, rawQuest);
        return quest == null
                ? List.of()
                : List.of(reference(
                        StardewContentReferenceRoles.QUEST,
                        StardewContentTypes.QUEST,
                        quest));
    }

    private static ResourceLocation ownedReference(
            StardewContentKey owner,
            String raw
    ) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(
                java.util.Locale.ROOT);
        return normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(
                        owner.id().getNamespace(), normalized);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
    }

    private record AlwaysCondition(boolean value) {
        private static final Codec<AlwaysCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("value", true).forGetter(AlwaysCondition::value)
        ).apply(instance, AlwaysCondition::new));
    }

    private record HasItemCondition(ResourceLocation item, int count) {
        private static final Codec<HasItemCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(HasItemCondition::item),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(HasItemCondition::count)
        ).apply(instance, HasItemCondition::new));
    }

    private record MoneyCondition(int min, int max) {
        private static final Codec<MoneyCondition> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("min", Integer.MIN_VALUE).forGetter(MoneyCondition::min),
                Codec.INT.optionalFieldOf("max", Integer.MAX_VALUE).forGetter(MoneyCondition::max)
        ).apply(instance, MoneyCondition::new));
        private static final Codec<MoneyCondition> CODEC = BASE_CODEC.validate(data -> data.max() < data.min()
                ? com.mojang.serialization.DataResult.error(() -> "money.max must be >= money.min")
                : com.mojang.serialization.DataResult.success(data));
    }

    private record FlagCondition(String id, boolean present) {
        private static final Codec<FlagCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(FlagCondition::id),
                Codec.BOOL.optionalFieldOf("present", true).forGetter(FlagCondition::present)
        ).apply(instance, FlagCondition::new));
    }

    private record SkillCondition(String skill, int level) {
        private static final Codec<SkillCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("skill").forGetter(SkillCondition::skill),
                Codec.intRange(0, 100).fieldOf("level").forGetter(SkillCondition::level)
        ).apply(instance, SkillCondition::new));
    }

    private record SeasonCondition(List<String> seasons) {
        private static final Codec<SeasonCondition> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().fieldOf("seasons").forGetter(SeasonCondition::seasons)
        ).apply(instance, SeasonCondition::new));
        private static final Codec<SeasonCondition> CODEC = BASE_CODEC.validate(data -> {
            if (data.seasons().isEmpty()) {
                return com.mojang.serialization.DataResult.error(() -> "season.seasons must not be empty");
            }
            for (String season : data.seasons()) {
                if (!List.of("spring", "summer", "fall", "winter").contains(season)) {
                    return com.mojang.serialization.DataResult.error(() -> "Unknown season: " + season);
                }
            }
            return com.mojang.serialization.DataResult.success(data);
        });
    }

    private record TimeCondition(int start, int end) {
        private static final Codec<TimeCondition> CODEC =
                RecordCodecBuilder.<TimeCondition>create(
                        instance -> instance.group(
                        Codec.intRange(0, 2600).optionalFieldOf(
                                        "start", 0)
                                .forGetter(TimeCondition::start),
                        Codec.intRange(0, 2600).optionalFieldOf(
                                        "end", 2600)
                                .forGetter(TimeCondition::end)
                ).apply(instance, TimeCondition::new))
                        .validate(value -> validHhmm(value.start())
                                        && validHhmm(value.end())
                                ? com.mojang.serialization.DataResult
                                        .success(value)
                                : com.mojang.serialization.DataResult
                                        .error(() ->
                                                "time values must use HHMM with minutes below 60"));

        private boolean includes(int currentMinutes) {
            int startMinutes = toMinutes(start);
            int endMinutes = toMinutes(end);
            if (startMinutes == endMinutes) {
                return true;
            }
            return startMinutes < endMinutes
                    ? currentMinutes >= startMinutes
                            && currentMinutes < endMinutes
                    : currentMinutes >= startMinutes
                            || currentMinutes < endMinutes;
        }

        private static boolean validHhmm(int value) {
            return value / 100 <= 26 && value % 100 < 60;
        }

        private static int toMinutes(int value) {
            return value / 100 * 60 + value % 100;
        }
    }

    private record SeenEventCondition(String id, String scope) {
        private static final Codec<SeenEventCondition> CODEC =
                RecordCodecBuilder.<SeenEventCondition>create(instance -> instance.group(
                        Codec.STRING.fieldOf("id").forGetter(SeenEventCondition::id),
                        Codec.STRING.optionalFieldOf("scope", "current")
                                .forGetter(SeenEventCondition::scope)
                ).apply(instance, SeenEventCondition::new))
                        .validate(value -> List.of("current", "host")
                                        .contains(value.scope())
                                ? com.mojang.serialization.DataResult.success(value)
                                : com.mojang.serialization.DataResult.error(
                                        () -> "seen_event.scope must be current or host"));
    }

    private record LocationCondition(
            List<ResourceLocation> locations,
            List<ResourceLocation> requiredTags,
            List<ResourceLocation> excludedTags,
            Map<ResourceLocation, String> properties
    ) {
        private static final Codec<LocationCondition> BASE_CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        ResourceLocation.CODEC.listOf()
                                .optionalFieldOf(
                                        "locations", List.of())
                                .forGetter(LocationCondition::locations),
                        ResourceLocation.CODEC.listOf()
                                .optionalFieldOf(
                                        "required_tags", List.of())
                                .forGetter(
                                        LocationCondition::requiredTags),
                        ResourceLocation.CODEC.listOf()
                                .optionalFieldOf(
                                        "excluded_tags", List.of())
                                .forGetter(
                                        LocationCondition::excludedTags),
                        Codec.unboundedMap(
                                        ResourceLocation.CODEC,
                                        Codec.STRING)
                                .optionalFieldOf(
                                        "properties", Map.of())
                                .forGetter(LocationCondition::properties)
                ).apply(instance, LocationCondition::new));
        private static final Codec<LocationCondition> CODEC =
                BASE_CODEC.validate(data -> {
                    if (data.locations().isEmpty()
                            && data.requiredTags().isEmpty()
                            && data.excludedTags().isEmpty()
                            && data.properties().isEmpty()) {
                        return com.mojang.serialization.DataResult.error(
                                () -> "location condition must declare "
                                        + "at least one criterion");
                    }
                    if (new LinkedHashSet<>(data.requiredTags()).stream()
                            .anyMatch(new LinkedHashSet<>(
                                    data.excludedTags())::contains)) {
                        return com.mojang.serialization.DataResult.error(
                                () -> "location condition cannot require "
                                        + "and exclude the same tag");
                    }
                    if (data.properties().entrySet().stream()
                            .anyMatch(entry ->
                                    entry.getValue().isBlank()
                                            || entry.getValue().length()
                                            > 256)) {
                        return com.mojang.serialization.DataResult.error(
                                () -> "location condition property values "
                                        + "must contain 1-256 characters");
                    }
                    return com.mojang.serialization.DataResult.success(
                            data);
                });

        private LocationCondition {
            locations = List.copyOf(new LinkedHashSet<>(locations));
            requiredTags = List.copyOf(
                    new LinkedHashSet<>(requiredTags));
            excludedTags = List.copyOf(
                    new LinkedHashSet<>(excludedTags));
            properties = java.util.Collections.unmodifiableMap(
                    new java.util.LinkedHashMap<>(properties));
        }
    }

    private record DirectItemQuery(ResourceLocation item, int count) {
        private static final Codec<DirectItemQuery> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(DirectItemQuery::item),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(DirectItemQuery::count)
        ).apply(instance, DirectItemQuery::new));
    }

    private record RandomTagQuery(ResourceLocation tag, int count) {
        private static final Codec<RandomTagQuery> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("tag").forGetter(RandomTagQuery::tag),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(RandomTagQuery::count)
        ).apply(instance, RandomTagQuery::new));
    }

    private record OneOfItemQuery(List<ResourceLocation> items, int count) {
        private static final Codec<OneOfItemQuery> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.listOf().fieldOf("items").forGetter(OneOfItemQuery::items),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(OneOfItemQuery::count)
        ).apply(instance, OneOfItemQuery::new));
        private static final Codec<OneOfItemQuery> CODEC = BASE_CODEC.validate(data -> data.items().isEmpty()
                ? com.mojang.serialization.DataResult.error(() -> "one_of.items must not be empty")
                : com.mojang.serialization.DataResult.success(data));
    }

    private record RandomCountItemQuery(StardewItemQuery query, int minCount, int maxCount) {
        private static final Codec<RandomCountItemQuery> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StardewItemQueries.CODEC.fieldOf("query").forGetter(RandomCountItemQuery::query),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("min_count").forGetter(RandomCountItemQuery::minCount),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("max_count").forGetter(RandomCountItemQuery::maxCount)
        ).apply(instance, RandomCountItemQuery::new));
        private static final Codec<RandomCountItemQuery> CODEC = BASE_CODEC.validate(data ->
                data.maxCount() < data.minCount()
                        ? com.mojang.serialization.DataResult.error(() ->
                                "random_count.max_count must be >= min_count")
                        : com.mojang.serialization.DataResult.success(data));
    }

    private record OneOfQueriesItemQuery(List<StardewItemQuery> queries) {
        private static final Codec<OneOfQueriesItemQuery> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StardewItemQueries.CODEC.listOf().fieldOf("queries").forGetter(OneOfQueriesItemQuery::queries)
        ).apply(instance, OneOfQueriesItemQuery::new));
        private static final Codec<OneOfQueriesItemQuery> CODEC = BASE_CODEC.validate(data ->
                data.queries().isEmpty()
                        ? com.mojang.serialization.DataResult.error(() ->
                                "one_of_queries.queries must not be empty")
                        : com.mojang.serialization.DataResult.success(data));
    }

    private record WeightedQueryEntry(StardewItemQuery query, int weight) {
        private static final Codec<WeightedQueryEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StardewItemQueries.CODEC.fieldOf("query").forGetter(WeightedQueryEntry::query),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("weight", 1)
                        .forGetter(WeightedQueryEntry::weight)
        ).apply(instance, WeightedQueryEntry::new));
    }

    private record WeightedItemQuery(List<WeightedQueryEntry> entries) {
        private static final Codec<WeightedItemQuery> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                WeightedQueryEntry.CODEC.listOf().fieldOf("entries").forGetter(WeightedItemQuery::entries)
        ).apply(instance, WeightedItemQuery::new));
        private static final Codec<WeightedItemQuery> CODEC = BASE_CODEC.validate(data ->
                data.entries().isEmpty()
                        ? com.mojang.serialization.DataResult.error(() ->
                                "weighted.entries must not be empty")
                        : com.mojang.serialization.DataResult.success(data));
    }

    private record SetFlagAction(String id) {
        private static final Codec<SetFlagAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(SetFlagAction::id)
        ).apply(instance, SetFlagAction::new));
    }

    private record AddMoneyAction(int amount) {
        private static final Codec<AddMoneyAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("amount").forGetter(AddMoneyAction::amount)
        ).apply(instance, AddMoneyAction::new));
    }

    private record AddItemAction(ResourceLocation item, int count) {
        private static final Codec<AddItemAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(AddItemAction::item),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(AddItemAction::count)
        ).apply(instance, AddItemAction::new));
    }

    private record RemoveItemAction(ResourceLocation item, int count) {
        private static final Codec<RemoveItemAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(RemoveItemAction::item),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(RemoveItemAction::count)
        ).apply(instance, RemoveItemAction::new));
    }

    private record StartQuestAction(String quest) {
        private static final Codec<StartQuestAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("quest").forGetter(StartQuestAction::quest)
        ).apply(instance, StartQuestAction::new));
    }

    private record RemoveQuestAction(String quest) {
        private static final Codec<RemoveQuestAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("quest").forGetter(RemoveQuestAction::quest)
        ).apply(instance, RemoveQuestAction::new));
    }

    private record ApplyUnlockSourceAction(String source) {
        private static final Codec<ApplyUnlockSourceAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("source").forGetter(ApplyUnlockSourceAction::source)
        ).apply(instance, ApplyUnlockSourceAction::new));
    }
}
