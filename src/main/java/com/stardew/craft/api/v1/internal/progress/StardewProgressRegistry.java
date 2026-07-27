package com.stardew.craft.api.v1.internal.progress;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.progress.StardewProgressDomains;
import com.stardew.craft.api.v1.progress.StardewProgressEvent;
import com.stardew.craft.api.v1.progress.StardewProgressEventType;
import com.stardew.craft.api.v1.progress.StardewProgressKey;
import com.stardew.craft.api.v1.progress.StardewProgressListener;
import com.stardew.craft.api.v1.progress.StardewProgressMetric;
import com.stardew.craft.api.v1.progress.StardewProgressPhase;
import com.stardew.craft.api.v1.progress.StardewProgressProvider;
import com.stardew.craft.api.v1.progress.StardewProgressScope;
import com.stardew.craft.api.v1.progress.StardewProgressSnapshot;
import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterVariantRegistry;
import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionEvent;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionEventType;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionSnapshot;
import com.stardew.craft.api.v1.festival.StardewFestivalSessions;
import com.stardew.craft.api.v1.festival.StardewFestivalActivities;
import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.EggFestivalService;
import com.stardew.craft.api.v1.internal.festival.StardewFestivalRewardRegistry;
import com.stardew.craft.mail.MailRegistry;
import com.stardew.craft.museum.MuseumDonationData;
import com.stardew.craft.museum.MuseumRewardRegistry;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.quest.QuestDataLoader;
import com.stardew.craft.quest.QuestManager;
import com.stardew.craft.quest.StardewQuest;
import com.stardew.craft.specialorder.SpecialOrderDataLoader;
import com.stardew.craft.specialorder.SpecialOrderDefinition;
import com.stardew.craft.specialorder.SpecialOrderInstance;
import com.stardew.craft.specialorder.SpecialOrderWorldData;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Shared registry and built-in read-only projections for the public progress facade. */
public final class StardewProgressRegistry {
    private static final ResourceLocation OBJECTIVE_METRIC =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "objective");
    private static final ResourceLocation BUNDLES_METRIC =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "bundles");
    private static final ResourceLocation DONATIONS_METRIC =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "donations");
    private static final ResourceLocation MINERALS_METRIC =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "minerals");
    private static final ResourceLocation ARTIFACTS_METRIC =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "artifacts");
    private static final ResourceLocation REQUIRED_ITEMS_METRIC =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "required_items");
    private static final ResourceLocation MUSEUM_COLLECTION_ID =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "collection");
    private static final ResourceLocation EGG_FESTIVAL_ID =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "spring13");
    private static final ResourceLocation EGG_HUNT_ACTIVITY_ID =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "egg_hunt");
    private static final ResourceLocation EGGS_METRIC =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "eggs");
    private static final OrderedExtensionRegistry<ProviderEntry> PROVIDERS =
            new OrderedExtensionRegistry<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "progress/provider"));
    private static final OrderedExtensionRegistry<StardewProgressListener> LISTENERS =
            new OrderedExtensionRegistry<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "progress/listener"));

    private StardewProgressRegistry() {
    }

    public static void registerProvider(
            ResourceLocation registrationId,
            int priority,
            ResourceLocation domain,
            StardewProgressProvider provider
    ) {
        PROVIDERS.register(
                Objects.requireNonNull(registrationId, "registrationId"),
                priority,
                new ProviderEntry(
                        Objects.requireNonNull(domain, "domain"),
                        Objects.requireNonNull(provider, "provider")));
    }

    public static void registerListener(
            ResourceLocation registrationId,
            int priority,
            StardewProgressListener listener
    ) {
        LISTENERS.register(
                Objects.requireNonNull(registrationId, "registrationId"),
                priority,
                Objects.requireNonNull(listener, "listener"));
    }

    @Nullable
    public static StardewProgressSnapshot inspect(
            ServerPlayer player,
            StardewProgressKey key
    ) {
        StardewProgressSnapshot builtin = inspectBuiltin(player, key);
        if (builtin != null) {
            return builtin;
        }
        for (var entry : PROVIDERS.entries()) {
            if (!entry.extension().domain().equals(key.domain())) {
                continue;
            }
            try {
                StardewProgressSnapshot snapshot =
                        PROVIDERS.invoke(
                                entry,
                                provider -> provider.provider()
                                        .inspect(player, key.id()));
                if (snapshot == null) {
                    continue;
                }
                if (!snapshot.key().equals(key)) {
                    StardewCraft.LOGGER.error(
                            "Progress provider {} returned key {} while resolving {}",
                            entry.id(), snapshot.key(), key);
                    continue;
                }
                return snapshot;
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Progress provider {} failed while resolving {}",
                        entry.id(), key, exception);
            }
        }
        return null;
    }

    public static List<StardewProgressSnapshot> list(
            ServerPlayer player,
            ResourceLocation domain
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(domain, "domain");
        TreeSet<ResourceLocation> ids = new TreeSet<>(
                java.util.Comparator.comparing(ResourceLocation::toString));
        collectBuiltinIds(player, domain, ids);
        for (var entry : PROVIDERS.entries()) {
            if (!entry.extension().domain().equals(domain)) {
                continue;
            }
            try {
                Collection<ResourceLocation> advertised =
                        PROVIDERS.invoke(
                                entry,
                                provider -> provider.provider()
                                        .entries(player));
                if (advertised != null) {
                    advertised.stream()
                            .filter(Objects::nonNull)
                            .forEach(ids::add);
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Progress provider {} failed while listing domain {}",
                        entry.id(), domain, exception);
            }
        }
        ArrayList<StardewProgressSnapshot> snapshots = new ArrayList<>();
        for (ResourceLocation id : ids) {
            StardewProgressSnapshot snapshot = inspect(
                    player, new StardewProgressKey(domain, id));
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        return List.copyOf(snapshots);
    }

    public static void dispatch(StardewProgressEvent event) {
        Objects.requireNonNull(event, "event");
        for (var entry : LISTENERS.entries()) {
            try {
                LISTENERS.invokeVoid(
                        entry,
                        listener -> listener.onTransition(event));
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Progress listener {} failed for {} {}",
                        entry.id(), event.after().key(), event.type(), exception);
            }
        }
    }

    /**
     * Publishes the meaningful lifecycle facts between two committed snapshots.
     * Owning systems call this only after their existing persistence mutation succeeds.
     */
    public static void dispatchChanges(
            ServerPlayer player,
            StardewProgressSnapshot before,
            StardewProgressSnapshot after,
            ResourceLocation cause
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(cause, "cause");
        if (!before.key().equals(after.key())) {
            throw new IllegalArgumentException(
                    "Progress transition snapshots must use the same key");
        }
        if (before.equals(after)) {
            return;
        }
        if (!before.metrics().equals(after.metrics())) {
            dispatchTransition(
                    StardewProgressEventType.PROGRESSED,
                    player, before, after, cause);
        }
        if (!before.rewardClaimable() && after.rewardClaimable()) {
            dispatchTransition(
                    StardewProgressEventType.REWARD_BECAME_AVAILABLE,
                    player, before, after, cause);
        } else if (before.rewardClaimable()
                && !after.rewardClaimable()) {
            dispatchTransition(
                    StardewProgressEventType.REWARD_CLAIMED,
                    player, before, after, cause);
        } else if (before.phase() != StardewProgressPhase.COMPLETED
                && after.phase() == StardewProgressPhase.COMPLETED
                && before.metrics().equals(after.metrics())) {
            dispatchTransition(
                    StardewProgressEventType.COMPLETED,
                    player, before, after, cause);
        }
    }

    private static void dispatchTransition(
            StardewProgressEventType type,
            ServerPlayer player,
            StardewProgressSnapshot before,
            StardewProgressSnapshot after,
            ResourceLocation cause
    ) {
        dispatch(new StardewProgressEvent(
                type,
                player.serverLevel(),
                Optional.of(player.getUUID()),
                Optional.of(before),
                after,
                cause));
    }

    public static StardewProgressKey questKey(StardewQuest quest) {
        ResourceLocation id = quest.getDefinitionId();
        if (id == null) {
            id = QuestDataLoader.normalizeId(quest.getId());
        }
        if (id == null) {
            throw new IllegalArgumentException("Quest has no valid stable id: " + quest.getId());
        }
        return new StardewProgressKey(StardewProgressDomains.QUEST, id);
    }

    public static StardewProgressSnapshot questSnapshot(
            StardewQuest quest,
            StardewProgressPhase forcedPhase
    ) {
        boolean definitionAvailable = quest.getDefinitionId() == null
                || QuestDataLoader.getDefinition(quest.getDefinitionId()) != null;
        StardewProgressPhase phase = forcedPhase != null
                ? forcedPhase : questPhase(quest, definitionAvailable);
        int current = quest.getCurrentObjectiveCount();
        int target = quest.getTotalObjectiveCount();
        List<StardewProgressMetric> metrics = current >= 0 && target > 0
                ? List.of(new StardewProgressMetric(
                        OBJECTIVE_METRIC, Math.min(current, target), target))
                : List.of();
        return new StardewProgressSnapshot(
                questKey(quest),
                StardewProgressScope.PLAYER,
                phase,
                metrics,
                definitionAvailable,
                phase == StardewProgressPhase.REWARD_AVAILABLE && quest.hasMoneyReward(),
                quest.getDaysLeft() > 0
                        ? OptionalInt.of(quest.getDaysLeft()) : OptionalInt.empty());
    }

    public static StardewProgressKey specialOrderKey(String runtimeId) {
        ResourceLocation id = specialOrderDefinitionId(runtimeId);
        if (id == null) {
            id = runtimeId.indexOf(':') >= 0
                    ? ResourceLocation.tryParse(runtimeId)
                    : ResourceLocation.tryBuild(
                            StardewCraft.MODID, runtimeId.toLowerCase(java.util.Locale.ROOT));
        }
        if (id == null) {
            throw new IllegalArgumentException("Special order has no valid stable id: " + runtimeId);
        }
        return new StardewProgressKey(StardewProgressDomains.SPECIAL_ORDER, id);
    }

    public static StardewProgressSnapshot specialOrderSnapshot(
            ServerPlayer player,
            SpecialOrderInstance order,
            StardewProgressPhase forcedPhase
    ) {
        return specialOrderSnapshot(
                player.getUUID(), order, forcedPhase);
    }

    public static StardewProgressSnapshot specialOrderSnapshot(
            @Nullable java.util.UUID playerId,
            SpecialOrderInstance order,
            StardewProgressPhase forcedPhase
    ) {
        boolean definitionAvailable = SpecialOrderDataLoader.get(order.orderId()) != null;
        StardewProgressPhase phase = forcedPhase != null
                ? forcedPhase : specialOrderPhase(playerId, order);
        List<StardewProgressMetric> metrics = new ArrayList<>();
        for (int index = 0; index < order.objectives().size(); index++) {
            SpecialOrderInstance.ObjectiveState objective = order.objectives().get(index);
            metrics.add(new StardewProgressMetric(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "objective/" + index),
                    Math.min(objective.progress(), objective.requiredCount()),
                    objective.requiredCount()));
        }
        OptionalInt remainingDays = OptionalInt.empty();
        if (!phase.terminal()) {
            try {
                remainingDays = OptionalInt.of(order.daysLeft(
                        StardewTimeManager.get().getAbsoluteDay()));
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.debug(
                        "Could not resolve remaining days for special order {}",
                        order.orderId(), exception);
            }
        }
        return new StardewProgressSnapshot(
                specialOrderKey(order.orderId()),
                StardewProgressScope.TEAM,
                phase,
                metrics,
                definitionAvailable,
                phase == StardewProgressPhase.REWARD_AVAILABLE,
                remainingDays);
    }

    public static StardewProgressKey mailKey(String mailId) {
        ResourceLocation id = MailRegistry.normalizeId(mailId);
        if (id == null) {
            throw new IllegalArgumentException("Mail has no valid stable id: " + mailId);
        }
        return new StardewProgressKey(StardewProgressDomains.MAIL, id);
    }

    public static StardewProgressSnapshot mailSnapshot(
            String mailId,
            StardewProgressPhase phase
    ) {
        ResourceLocation id = MailRegistry.normalizeId(mailId);
        return new StardewProgressSnapshot(
                mailKey(mailId),
                StardewProgressScope.PLAYER,
                phase,
                List.of(),
                id != null && MailRegistry.getDefinition(id) != null,
                false,
                OptionalInt.empty());
    }

    public static StardewProgressKey communityCenterBundleKey(int bundleId) {
        if (bundleId < 0) {
            throw new IllegalArgumentException("Bundle ID cannot be negative");
        }
        return new StardewProgressKey(
                StardewProgressDomains.COMMUNITY_CENTER,
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "bundle/" + bundleId));
    }

    public static StardewProgressKey communityCenterAreaKey(int areaId) {
        if (areaId < 0) {
            throw new IllegalArgumentException("Area ID cannot be negative");
        }
        return new StardewProgressKey(
                StardewProgressDomains.COMMUNITY_CENTER,
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "area/" + areaId));
    }

    @Nullable
    public static StardewProgressSnapshot communityCenterBundleSnapshot(
            ServerPlayer player,
            int bundleId
    ) {
        return communityCenterBundleSnapshot(
                player.serverLevel(), player.getUUID(), bundleId);
    }

    @Nullable
    public static StardewProgressSnapshot communityCenterBundleSnapshot(
            net.minecraft.server.level.ServerLevel level,
            UUID playerId,
            int bundleId
    ) {
        CommunityCenterSavedData data = CommunityCenterSavedData.get(level);
        BundleDefinition definition =
                StardewCommunityCenterVariantRegistry.bundle(playerId, bundleId);
        boolean tracked = data.getBundleSlotsView(playerId).containsKey(bundleId);
        if (definition == null && !tracked) {
            return null;
        }
        int current = data.countFilledSlots(playerId, bundleId);
        int target = definition == null
                ? data.getBundleSlotsView(playerId).get(bundleId).length
                : definition.requiredCount();
        boolean complete = definition != null
                && data.isBundleComplete(playerId, bundleId);
        boolean rewardAvailable =
                data.isRewardAvailable(playerId, bundleId);
        StardewProgressPhase phase;
        if (definition == null) {
            phase = StardewProgressPhase.UNAVAILABLE;
        } else if (complete && rewardAvailable) {
            phase = StardewProgressPhase.REWARD_AVAILABLE;
        } else if (complete) {
            phase = StardewProgressPhase.COMPLETED;
        } else if (current > 0) {
            phase = StardewProgressPhase.ACTIVE;
        } else {
            phase = StardewProgressPhase.NOT_STARTED;
        }
        return new StardewProgressSnapshot(
                communityCenterBundleKey(bundleId),
                StardewProgressScope.PLAYER,
                phase,
                target > 0
                        ? List.of(new StardewProgressMetric(
                                OBJECTIVE_METRIC,
                                Math.min(current, target),
                                target))
                        : List.of(),
                definition != null,
                phase == StardewProgressPhase.REWARD_AVAILABLE,
                OptionalInt.empty());
    }

    @Nullable
    public static StardewProgressSnapshot communityCenterAreaSnapshot(
            ServerPlayer player,
            int areaId
    ) {
        CommunityCenterSavedData data =
                CommunityCenterSavedData.get(player.serverLevel());
        Collection<BundleDefinition> definitions =
                StardewCommunityCenterVariantRegistry.area(
                        player.getUUID(), areaId);
        boolean completed =
                data.isAreaComplete(player.getUUID(), areaId);
        if (definitions.isEmpty() && !completed) {
            return null;
        }
        int completeBundles = 0;
        for (BundleDefinition definition : definitions) {
            if (data.isBundleComplete(
                    player.getUUID(), definition.bundleId())) {
                completeBundles++;
            }
        }
        StardewProgressPhase phase = completed
                ? StardewProgressPhase.COMPLETED
                : completeBundles > 0
                        ? StardewProgressPhase.ACTIVE
                        : definitions.isEmpty()
                                ? StardewProgressPhase.UNAVAILABLE
                                : StardewProgressPhase.NOT_STARTED;
        return new StardewProgressSnapshot(
                communityCenterAreaKey(areaId),
                StardewProgressScope.PLAYER,
                phase,
                definitions.isEmpty()
                        ? List.of()
                        : List.of(new StardewProgressMetric(
                                BUNDLES_METRIC,
                                completeBundles,
                                definitions.size())),
                !definitions.isEmpty(),
                false,
                OptionalInt.empty());
    }

    public static StardewProgressKey museumCollectionKey() {
        return new StardewProgressKey(
                StardewProgressDomains.MUSEUM, MUSEUM_COLLECTION_ID);
    }

    public static StardewProgressKey museumRewardKey(String rewardId) {
        ResourceLocation parsed = rewardId.indexOf(':') >= 0
                ? ResourceLocation.tryParse(rewardId) : null;
        String namespace = parsed == null
                ? StardewCraft.MODID : parsed.getNamespace();
        String path = parsed == null ? rewardId : parsed.getPath();
        ResourceLocation id = ResourceLocation.tryBuild(
                namespace,
                "reward/" + path.toLowerCase(java.util.Locale.ROOT));
        if (id == null) {
            throw new IllegalArgumentException(
                    "Museum reward has no valid stable id: " + rewardId);
        }
        return new StardewProgressKey(StardewProgressDomains.MUSEUM, id);
    }

    public static StardewProgressKey festivalKey(
            ResourceLocation festivalId
    ) {
        return new StardewProgressKey(
                StardewProgressDomains.FESTIVAL, festivalId);
    }

    public static StardewProgressSnapshot festivalSessionSnapshot(
            StardewFestivalSessionSnapshot session,
            boolean definitionAvailable
    ) {
        return new StardewProgressSnapshot(
                festivalKey(session.festivalId()),
                StardewProgressScope.WORLD,
                switch (session.phase()) {
                    case SCHEDULED, PREPARING_MAP ->
                            StardewProgressPhase.SCHEDULED;
                    case OPEN -> StardewProgressPhase.AVAILABLE;
                    case MAIN_EVENT, ENDING, RESTORING_MAP ->
                            StardewProgressPhase.ACTIVE;
                    case CLOSED -> StardewProgressPhase.COMPLETED;
                },
                List.of(),
                definitionAvailable,
                false,
                OptionalInt.empty());
    }

    /** Bridges committed festival phase changes into the cross-system lifecycle. */
    public static void onFestivalSessionEvent(
            StardewFestivalSessionEvent event
    ) {
        if (event.type()
                != StardewFestivalSessionEventType.PHASE_CHANGED) {
            return;
        }
        StardewFestivalSessionSnapshot current = event.session();
        StardewFestivalSessionSnapshot previous =
                new StardewFestivalSessionSnapshot(
                        current.festivalId(),
                        current.runtimeId(),
                        current.year(),
                        current.season(),
                        current.day(),
                        event.previousPhase().orElseThrow(),
                        current.mapPhase(),
                        current.participants());
        boolean definitionAvailable =
                FestivalRegistry.get(current.festivalId()).isPresent();
        StardewProgressSnapshot before = festivalSessionSnapshot(
                previous, definitionAvailable);
        StardewProgressSnapshot after = festivalSessionSnapshot(
                current, definitionAvailable);
        if (before.equals(after)) {
            return;
        }
        StardewProgressEventType type = switch (after.phase()) {
            case SCHEDULED -> StardewProgressEventType.SCHEDULED;
            case AVAILABLE -> StardewProgressEventType.MADE_AVAILABLE;
            case ACTIVE -> StardewProgressEventType.PROGRESSED;
            case COMPLETED -> StardewProgressEventType.COMPLETED;
            case FAILED -> StardewProgressEventType.FAILED;
            case EXPIRED -> StardewProgressEventType.EXPIRED;
            case CANCELLED -> StardewProgressEventType.CANCELLED;
            case UNAVAILABLE ->
                    StardewProgressEventType.BECAME_UNAVAILABLE;
            case REWARD_AVAILABLE ->
                    StardewProgressEventType.REWARD_BECAME_AVAILABLE;
            case NOT_STARTED -> StardewProgressEventType.PROGRESSED;
        };
        dispatch(new StardewProgressEvent(
                type,
                event.level(),
                Optional.empty(),
                Optional.of(before),
                after,
                com.stardew.craft.api.v1.progress
                        .StardewProgressCauses.FESTIVAL_SESSION));
    }

    public static StardewProgressSnapshot museumCollectionSnapshot(
            ServerPlayer player
    ) {
        MuseumDonationData data =
                MuseumDonationData.get(player.serverLevel());
        MuseumRewardRegistry.DonationCounts counts =
                MuseumRewardRegistry.countDonations(
                        data.getDonatedItems(player.getUUID()));
        List<MuseumRewardRegistry.MuseumReward> rewards =
                MuseumRewardRegistry.getAllRewards();
        Set<String> claimed =
                data.getClaimedMuseumRewards(player.getUUID());
        boolean rewardClaimable = !MuseumRewardRegistry
                .getClaimableRewards(data, player.getUUID(), claimed)
                .isEmpty();
        ArrayList<StardewProgressMetric> metrics = new ArrayList<>();
        addMetric(metrics, DONATIONS_METRIC, counts.total(),
                maximumThreshold(rewards, "total_count"));
        addMetric(metrics, MINERALS_METRIC, counts.minerals(),
                maximumThreshold(rewards, "mineral_count"));
        addMetric(metrics, ARTIFACTS_METRIC, counts.artifacts(),
                maximumThreshold(rewards, "artifact_count"));
        StardewProgressPhase phase = rewardClaimable
                ? StardewProgressPhase.REWARD_AVAILABLE
                : !rewards.isEmpty() && claimed.containsAll(
                        rewards.stream().map(
                                MuseumRewardRegistry.MuseumReward::id).toList())
                        ? StardewProgressPhase.COMPLETED
                        : counts.total() > 0
                                ? StardewProgressPhase.ACTIVE
                                : StardewProgressPhase.NOT_STARTED;
        return new StardewProgressSnapshot(
                museumCollectionKey(),
                StardewProgressScope.PLAYER,
                phase,
                metrics,
                true,
                rewardClaimable,
                OptionalInt.empty());
    }

    public static StardewProgressSnapshot museumRewardSnapshot(
            ServerPlayer player,
            MuseumRewardRegistry.MuseumReward reward
    ) {
        MuseumDonationData data =
                MuseumDonationData.get(player.serverLevel());
        Set<String> donated = data.getDonatedItems(player.getUUID());
        MuseumRewardRegistry.RewardProgress progress =
                MuseumRewardRegistry.progress(reward, donated);
        int current = progress.current();
        int target = progress.target();
        boolean claimed = data.isRewardClaimed(
                player.getUUID(), reward.id());
        StardewProgressPhase phase = claimed
                ? StardewProgressPhase.COMPLETED
                : progress.qualified()
                        ? StardewProgressPhase.REWARD_AVAILABLE
                        : current > 0
                                ? StardewProgressPhase.ACTIVE
                                : StardewProgressPhase.NOT_STARTED;
        return new StardewProgressSnapshot(
                museumRewardKey(reward.id()),
                StardewProgressScope.PLAYER,
                phase,
                target > 0
                        ? List.of(new StardewProgressMetric(
                                "specific_items".equals(reward.condition())
                                        ? REQUIRED_ITEMS_METRIC
                                        : metricForCondition(reward.condition()),
                                Math.min(current, target),
                                target))
                        : List.of(),
                true,
                phase == StardewProgressPhase.REWARD_AVAILABLE,
                OptionalInt.empty());
    }

    @Nullable
    private static StardewProgressSnapshot inspectBuiltin(
            ServerPlayer player,
            StardewProgressKey key
    ) {
        if (key.domain().equals(StardewProgressDomains.QUEST)) {
            return inspectQuest(player, key.id());
        }
        if (key.domain().equals(StardewProgressDomains.SPECIAL_ORDER)) {
            return inspectSpecialOrder(player, key.id());
        }
        if (key.domain().equals(StardewProgressDomains.MAIL)) {
            return inspectMail(player, key.id());
        }
        if (key.domain().equals(StardewProgressDomains.COMMUNITY_CENTER)) {
            return inspectCommunityCenter(player, key.id());
        }
        if (key.domain().equals(StardewProgressDomains.MUSEUM)) {
            return inspectMuseum(player, key.id());
        }
        if (key.domain().equals(StardewProgressDomains.FESTIVAL)) {
            return inspectFestival(player, key.id());
        }
        if (key.equals(StardewFestivalActivities.progressKey(
                EGG_FESTIVAL_ID, EGG_HUNT_ACTIVITY_ID))) {
            return eggHuntSnapshot(player);
        }
        var festivalReward =
                StardewFestivalRewardRegistry.descriptor(key);
        if (festivalReward != null) {
            return StardewFestivalRewardRegistry.progressSnapshot(
                    player, festivalReward);
        }
        return null;
    }

    private static void collectBuiltinIds(
            ServerPlayer player,
            ResourceLocation domain,
            Set<ResourceLocation> ids
    ) {
        if (domain.equals(StardewProgressDomains.QUEST)) {
            ids.addAll(QuestDataLoader.getAllDefinitionIds());
            QuestManager manager = QuestManager.of(player);
            if (manager != null) {
                for (StardewQuest quest : manager.getQuestLog()) {
                    ids.add(questKey(quest).id());
                }
                for (String completed : manager.getCompletedQuestIds()) {
                    ResourceLocation id =
                            QuestDataLoader.normalizeId(completed);
                    if (id != null) {
                        ids.add(id);
                    }
                }
            }
            return;
        }
        if (domain.equals(StardewProgressDomains.SPECIAL_ORDER)) {
            ids.addAll(SpecialOrderDataLoader.snapshot()
                    .definitions().keySet());
            SpecialOrderWorldData data =
                    SpecialOrderWorldData.get(player.serverLevel());
            for (SpecialOrderInstance order : data.active()) {
                ids.add(specialOrderKey(order.orderId()).id());
            }
            for (SpecialOrderInstance order : data.available()) {
                ids.add(specialOrderKey(order.orderId()).id());
            }
            return;
        }
        if (domain.equals(StardewProgressDomains.MAIL)) {
            ids.addAll(MailRegistry.snapshot().definitions().keySet());
            PlayerStardewData data =
                    PlayerDataManager.getPlayerData(player);
            if (data != null) {
                addNormalizedMailIds(ids, data.getMailbox());
                addNormalizedMailIds(ids, data.getMailForTomorrow());
            }
            return;
        }
        if (domain.equals(StardewProgressDomains.COMMUNITY_CENTER)) {
            Set<Integer> areaIds = new TreeSet<>();
            for (BundleDefinition definition
                    : StardewCommunityCenterVariantRegistry.all(
                            player.getUUID())) {
                ids.add(communityCenterBundleKey(
                        definition.bundleId()).id());
                areaIds.add(definition.areaId());
            }
            CommunityCenterSavedData data =
                    CommunityCenterSavedData.get(player.serverLevel());
            for (int bundleId : data.getBundleSlotsView(
                    player.getUUID()).keySet()) {
                ids.add(communityCenterBundleKey(bundleId).id());
            }
            for (int areaId : areaIds) {
                ids.add(communityCenterAreaKey(areaId).id());
            }
            return;
        }
        if (domain.equals(StardewProgressDomains.MUSEUM)) {
            ids.add(MUSEUM_COLLECTION_ID);
            for (MuseumRewardRegistry.MuseumReward reward
                    : MuseumRewardRegistry.getAllRewards()) {
                ids.add(museumRewardKey(reward.id()).id());
            }
            return;
        }
        if (domain.equals(StardewProgressDomains.FESTIVAL)) {
            for (FestivalDefinition definition : FestivalRegistry.all()) {
                ids.add(definition.resourceId());
            }
            return;
        }
        if (domain.equals(StardewFestivalActivities.progressDomain(
                EGG_FESTIVAL_ID))) {
            ids.add(EGG_HUNT_ACTIVITY_ID);
            return;
        }
        ids.addAll(StardewFestivalRewardRegistry.entries(domain));
    }

    private static void addNormalizedMailIds(
            Set<ResourceLocation> ids,
            Collection<String> rawIds
    ) {
        for (String rawId : rawIds) {
            ResourceLocation id = MailRegistry.normalizeId(rawId);
            if (id != null) {
                ids.add(id);
            }
        }
    }

    @Nullable
    private static StardewProgressSnapshot inspectCommunityCenter(
            ServerPlayer player,
            ResourceLocation id
    ) {
        if (!id.getNamespace().equals(StardewCraft.MODID)) {
            return null;
        }
        Integer bundleId = numericSuffix(id.getPath(), "bundle/");
        if (bundleId != null) {
            return communityCenterBundleSnapshot(player, bundleId);
        }
        Integer areaId = numericSuffix(id.getPath(), "area/");
        return areaId == null
                ? null : communityCenterAreaSnapshot(player, areaId);
    }

    @Nullable
    private static StardewProgressSnapshot inspectMuseum(
            ServerPlayer player,
            ResourceLocation id
    ) {
        if (id.equals(MUSEUM_COLLECTION_ID)) {
            return museumCollectionSnapshot(player);
        }
        for (MuseumRewardRegistry.MuseumReward reward
                : MuseumRewardRegistry.getAllRewards()) {
            if (museumRewardKey(reward.id()).id().equals(id)) {
                return museumRewardSnapshot(player, reward);
            }
        }
        return null;
    }

    @Nullable
    private static StardewProgressSnapshot inspectFestival(
            ServerPlayer player,
            ResourceLocation id
    ) {
        FestivalDefinition definition = FestivalRegistry.get(id)
                .orElse(null);
        var session = StardewFestivalSessions.find(
                player.serverLevel(), id).orElse(null);
        if (session != null) {
            return festivalSessionSnapshot(
                    session, definition != null);
        }
        return definition == null
                ? null
                : emptySnapshot(
                        festivalKey(id),
                        StardewProgressScope.WORLD,
                        StardewProgressPhase.NOT_STARTED,
                        true);
    }

    private static StardewProgressSnapshot eggHuntSnapshot(
            ServerPlayer player
    ) {
        boolean definitionAvailable =
                FestivalRegistry.get(EGG_FESTIVAL_ID).isPresent();
        var session = StardewFestivalSessions.find(
                player.serverLevel(), EGG_FESTIVAL_ID).orElse(null);
        StardewProgressPhase phase;
        if (!definitionAvailable) {
            phase = StardewProgressPhase.UNAVAILABLE;
        } else if (session == null) {
            phase = StardewProgressPhase.NOT_STARTED;
        } else if (session.phase()
                == StardewFestivalSessionSnapshot.Phase.CLOSED
                || EggFestivalService.isEggHuntFinished()) {
            phase = StardewProgressPhase.COMPLETED;
        } else if (EggFestivalService.hasEggHuntStarted()) {
            phase = StardewProgressPhase.ACTIVE;
        } else if (session.phase()
                == StardewFestivalSessionSnapshot.Phase.OPEN
                || session.phase()
                == StardewFestivalSessionSnapshot.Phase.MAIN_EVENT) {
            phase = StardewProgressPhase.AVAILABLE;
        } else {
            phase = StardewProgressPhase.NOT_STARTED;
        }
        List<StardewProgressMetric> metrics =
                phase == StardewProgressPhase.ACTIVE
                        || EggFestivalService.isEggHuntFinished()
                ? List.of(new StardewProgressMetric(
                        EGGS_METRIC,
                        Math.min(
                                EggFestivalService.eggHuntScore(
                                        player.getUUID()),
                                EggFestivalService.eggHuntTarget(
                                        player.serverLevel())),
                        EggFestivalService.eggHuntTarget(
                                player.serverLevel())))
                : List.of();
        return new StardewProgressSnapshot(
                StardewFestivalActivities.progressKey(
                        EGG_FESTIVAL_ID, EGG_HUNT_ACTIVITY_ID),
                StardewProgressScope.PLAYER,
                phase,
                metrics,
                definitionAvailable,
                false,
                OptionalInt.empty());
    }

    @Nullable
    private static StardewProgressSnapshot inspectQuest(
            ServerPlayer player,
            ResourceLocation id
    ) {
        QuestManager manager = QuestManager.of(player);
        if (manager == null) {
            return null;
        }
        StardewQuest active = manager.getQuest(id.toString());
        if (active != null) {
            return questSnapshot(active, null);
        }
        StardewProgressKey key = new StardewProgressKey(
                StardewProgressDomains.QUEST, id);
        if (manager.isQuestCompleted(id.toString())) {
            return emptySnapshot(
                    key, StardewProgressScope.PLAYER,
                    StardewProgressPhase.COMPLETED,
                    QuestDataLoader.getDefinition(id) != null);
        }
        if (QuestDataLoader.getDefinition(id) != null) {
            return emptySnapshot(
                    key, StardewProgressScope.PLAYER,
                    StardewProgressPhase.NOT_STARTED, true);
        }
        return null;
    }

    @Nullable
    private static StardewProgressSnapshot inspectSpecialOrder(
            ServerPlayer player,
            ResourceLocation id
    ) {
        SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
        for (SpecialOrderInstance order : data.active()) {
            if (specialOrderKey(order.orderId()).id().equals(id)) {
                return specialOrderSnapshot(player, order, null);
            }
        }
        for (SpecialOrderInstance order : data.available()) {
            if (specialOrderKey(order.orderId()).id().equals(id)) {
                return specialOrderSnapshot(
                        player, order, StardewProgressPhase.AVAILABLE);
            }
        }
        StardewProgressKey key = new StardewProgressKey(
                StardewProgressDomains.SPECIAL_ORDER, id);
        boolean known = SpecialOrderDataLoader.snapshot().definitions().containsKey(id);
        SpecialOrderDefinition definition =
                known ? SpecialOrderDataLoader.snapshot().definitions().get(id) : null;
        if (definition != null && data.completedOrderIds().contains(definition.id())) {
            return emptySnapshot(
                    key, StardewProgressScope.TEAM,
                    StardewProgressPhase.COMPLETED, true);
        }
        if (known) {
            return emptySnapshot(
                    key, StardewProgressScope.TEAM,
                    StardewProgressPhase.NOT_STARTED, true);
        }
        return null;
    }

    @Nullable
    private static StardewProgressSnapshot inspectMail(
            ServerPlayer player,
            ResourceLocation id
    ) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data == null) {
            return null;
        }
        String displayId = MailRegistry.get(id.toString()) == null
                ? id.toString() : MailRegistry.get(id.toString()).getId();
        StardewProgressPhase phase;
        if (data.hasMailFlag(displayId) || data.hasMailFlag(id.toString())) {
            phase = StardewProgressPhase.COMPLETED;
        } else if (data.getMailbox().contains(displayId)
                || data.getMailbox().contains(id.toString())) {
            phase = StardewProgressPhase.AVAILABLE;
        } else if (data.getMailForTomorrow().contains(displayId)
                || data.getMailForTomorrow().contains(id.toString())) {
            phase = StardewProgressPhase.SCHEDULED;
        } else if (MailRegistry.getDefinition(id) != null) {
            phase = StardewProgressPhase.NOT_STARTED;
        } else {
            return null;
        }
        return mailSnapshot(id.toString(), phase);
    }

    private static StardewProgressPhase questPhase(
            StardewQuest quest,
            boolean definitionAvailable
    ) {
        if (!definitionAvailable) {
            return StardewProgressPhase.UNAVAILABLE;
        }
        if (quest.isDestroy() && !quest.isCompleted()) {
            return StardewProgressPhase.EXPIRED;
        }
        if (quest.isCompleted()) {
            return quest.hasMoneyReward() && !quest.isDestroy()
                    ? StardewProgressPhase.REWARD_AVAILABLE
                    : StardewProgressPhase.COMPLETED;
        }
        return quest.isAccepted()
                ? StardewProgressPhase.ACTIVE : StardewProgressPhase.AVAILABLE;
    }

    private static StardewProgressPhase specialOrderPhase(
            @Nullable java.util.UUID playerId,
            SpecialOrderInstance order
    ) {
        if (order.failed()) {
            return StardewProgressPhase.FAILED;
        }
        if (order.complete()) {
            return playerId != null && order.hasUnclaimedReward(playerId)
                    ? StardewProgressPhase.REWARD_AVAILABLE
                    : StardewProgressPhase.COMPLETED;
        }
        return order.accepted()
                ? StardewProgressPhase.ACTIVE : StardewProgressPhase.AVAILABLE;
    }

    @Nullable
    private static ResourceLocation specialOrderDefinitionId(String runtimeId) {
        return SpecialOrderDataLoader.snapshot().definitions().entrySet().stream()
                .filter(entry -> entry.getValue().id().equals(runtimeId))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static StardewProgressSnapshot emptySnapshot(
            StardewProgressKey key,
            StardewProgressScope scope,
            StardewProgressPhase phase,
            boolean definitionAvailable
    ) {
        return new StardewProgressSnapshot(
                key, scope, phase, List.of(), definitionAvailable,
                false, OptionalInt.empty());
    }

    private static Integer numericSuffix(String path, String prefix) {
        if (!path.startsWith(prefix)) {
            return null;
        }
        try {
            int value = Integer.parseInt(path.substring(prefix.length()));
            return value < 0 ? null : value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int maximumThreshold(
            List<MuseumRewardRegistry.MuseumReward> rewards,
            String condition
    ) {
        return rewards.stream()
                .filter(reward -> condition.equals(reward.condition()))
                .mapToInt(MuseumRewardRegistry.MuseumReward::threshold)
                .max()
                .orElse(0);
    }

    private static void addMetric(
            List<StardewProgressMetric> metrics,
            ResourceLocation id,
            int current,
            int target
    ) {
        if (target > 0) {
            metrics.add(new StardewProgressMetric(
                    id, Math.min(current, target), target));
        }
    }

    private static ResourceLocation metricForCondition(String condition) {
        return switch (condition) {
            case "mineral_count" -> MINERALS_METRIC;
            case "artifact_count" -> ARTIFACTS_METRIC;
            default -> DONATIONS_METRIC;
        };
    }

    private record ProviderEntry(
            ResourceLocation domain,
            StardewProgressProvider provider
    ) {
    }

}
