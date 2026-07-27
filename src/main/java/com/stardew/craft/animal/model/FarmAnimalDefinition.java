package com.stardew.craft.animal.model;

import com.stardew.craft.api.v1.condition.StardewCondition;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable runtime view of one data-pack farm-animal definition.
 *
 * <p>The gameplay fields intentionally mirror Stardew Valley 1.6's
 * {@code Data/FarmAnimals}. Minecraft-only adaptation fields (managed entity type, translated
 * shop text and the effective building family) are kept alongside, but are named separately so
 * source values and project overrides cannot be confused.
 */
public record FarmAnimalDefinition(
        ResourceLocation dataId,
        boolean replacesExisting,
        String id,
        String sourceKey,
        String sourceHouse,
        String family,
        String gender,
        int purchasePrice,
        int sellPrice,
        @Nullable String requiredBuilding,
        @Nullable StardewCondition unlockCondition,
        List<AlternatePurchaseType> alternatePurchaseTypes,
        List<ResourceLocation> eggItemIds,
        int incubationTime,
        int incubatorParentSheetOffset,
        String birthText,
        int daysToMature,
        boolean canGetPregnant,
        int daysToProduce,
        HarvestType harvestType,
        @Nullable ResourceLocation harvestTool,
        List<ProduceEntry> produce,
        List<ProduceEntry> deluxeProduce,
        boolean produceOnMature,
        int friendshipForFasterProduce,
        int deluxeProduceMinimumFriendship,
        double deluxeProduceCareDivisor,
        double deluxeProduceLuckMultiplier,
        boolean canEatGoldenCrackers,
        int professionForHappinessBoost,
        int professionForQualityBoost,
        int professionForFasterProduce,
        boolean canSwim,
        boolean babiesFollowAdults,
        int grassEatAmount,
        int happinessDrain,
        ResourceLocation entityTypeId,
        @Nullable ResourceLocation soundEventId,
        List<ProduceStat> produceStats,
        int requiredBuildingTier,
        int shopOrder,
        String defaultName,
        String displayNameKey,
        @Nullable ResourceLocation shopTextureId,
        int shopTextureWidth,
        int shopTextureHeight,
        String shopDescriptionKey,
        String shopLockReasonKey,
        @Nullable String projectOverrideReason
) {
    public FarmAnimalDefinition {
        Objects.requireNonNull(dataId, "dataId");
        id = normalize(id, "animal_type_id");
        sourceKey = requireText(sourceKey, "source_key");
        sourceHouse = normalize(sourceHouse, "source_house");
        family = normalize(family, "family");
        gender = requireText(gender, "gender");
        if (!family.equals("coop") && !family.equals("barn")) {
            throw new IllegalArgumentException("family must be coop or barn: " + family);
        }
        if (purchasePrice < -1 || sellPrice < 0) {
            throw new IllegalArgumentException("invalid economy values for " + id);
        }
        if (requiredBuilding != null && requiredBuilding.isBlank()) {
            requiredBuilding = null;
        }
        alternatePurchaseTypes = List.copyOf(alternatePurchaseTypes);
        eggItemIds = List.copyOf(eggItemIds);
        if (incubationTime < -1 || incubatorParentSheetOffset < 0) {
            throw new IllegalArgumentException("invalid incubation metadata for " + id);
        }
        birthText = Objects.requireNonNullElse(birthText, "");
        if (daysToMature < 0 || daysToProduce < 1) {
            throw new IllegalArgumentException("invalid maturity/production days for " + id);
        }
        Objects.requireNonNull(harvestType, "harvestType");
        produce = List.copyOf(produce);
        deluxeProduce = List.copyOf(deluxeProduce);
        if (produce.isEmpty()) {
            throw new IllegalArgumentException("produce must contain at least one entry for " + id);
        }
        if (grassEatAmount < 0 || happinessDrain < 0) {
            throw new IllegalArgumentException("invalid care values for " + id);
        }
        Objects.requireNonNull(entityTypeId, "entityTypeId");
        produceStats = List.copyOf(produceStats);
        if (requiredBuildingTier < 0) {
            throw new IllegalArgumentException("required_building_tier must be non-negative");
        }
        defaultName = requireText(defaultName, "default_name");
        displayNameKey = requireText(displayNameKey, "display_name_key");
        if (shopTextureId != null
                && (shopTextureWidth <= 0
                || shopTextureHeight <= 0)) {
            throw new IllegalArgumentException(
                    "shop texture dimensions must be positive for "
                            + id);
        }
        shopDescriptionKey = Objects.requireNonNullElse(shopDescriptionKey, "");
        shopLockReasonKey = Objects.requireNonNullElse(shopLockReasonKey, "");
        if (projectOverrideReason != null && projectOverrideReason.isBlank()) {
            projectOverrideReason = null;
        }
    }

    public boolean soldByAnimalShop() {
        return purchasePrice >= 0;
    }

    public boolean hasProjectOverride() {
        return projectOverrideReason != null;
    }

    /**
     * Compatibility accessor for the old single-stat schema.
     *
     * <p>New consumers must use {@link #produceStats()} so source item/tag filters aren't lost.
     */
    @Nullable
    public String produceStatKey() {
        return produceStats.size() == 1
                && produceStats.getFirst().requiredItems().isEmpty()
                && produceStats.getFirst().requiredTags().isEmpty()
                ? produceStats.getFirst().statName()
                : null;
    }

    /** Compatibility accessor for consumers which currently use the first eligible source entry. */
    public ResourceLocation produceItemId() {
        return produce.getFirst().itemId();
    }

    /** Compatibility accessor for existing deluxe-production logic. */
    @Nullable
    public ResourceLocation deluxeProduceItemId() {
        return deluxeProduce.isEmpty() ? null : deluxeProduce.getFirst().itemId();
    }

    /**
     * Returns every source-ordered entry whose friendship threshold and optional condition match.
     *
     * <p>{@code FarmAnimal.GetProduceID} chooses uniformly from this eligible set. Keeping
     * filtering separate from selection lets the pure daily reducer own the exact RNG order.
     */
    public List<ProduceEntry> eligibleProduce(
            boolean deluxe,
            int friendship,
            ConditionEvaluator conditionEvaluator
    ) {
        List<ProduceEntry> entries = deluxe ? deluxeProduce : produce;
        return entries.stream()
                .filter(entry -> friendship >= entry.minimumFriendship())
                .filter(entry -> entry.condition() == null
                        || conditionEvaluator.test(entry.condition()))
                .toList();
    }

    /** Compatibility helper for older consumers which use the first eligible entry. */
    @Nullable
    public ProduceEntry selectProduce(
            boolean deluxe,
            int friendship,
            ConditionEvaluator conditionEvaluator
    ) {
        List<ProduceEntry> eligible = eligibleProduce(
                deluxe,
                friendship,
                conditionEvaluator
        );
        return eligible.isEmpty() ? null : eligible.getFirst();
    }

    public int sellPriceAtFriendship(int friendship) {
        double friendshipRatio = Math.max(0.0, Math.min(1.0, friendship / 1000.0));
        return (int) Math.floor(sellPrice * (friendshipRatio + 0.3));
    }

    private static String normalize(String value, String field) {
        return requireText(value, field).toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    public enum HarvestType {
        DROP_OVERNIGHT,
        HARVEST_WITH_TOOL,
        DIG_UP
    }

    public record ProduceEntry(
            String id,
            @Nullable StardewCondition condition,
            @Nullable String sourceCondition,
            int minimumFriendship,
            @Nullable String sourceItemId,
            ResourceLocation itemId
    ) {
        public ProduceEntry {
            id = requireText(id, "produce.id");
            if (sourceCondition != null && sourceCondition.isBlank()) {
                sourceCondition = null;
            }
            if (minimumFriendship < 0) {
                throw new IllegalArgumentException("produce.minimum_friendship must be non-negative");
            }
            if (sourceItemId != null && sourceItemId.isBlank()) {
                sourceItemId = null;
            }
            Objects.requireNonNull(itemId, "itemId");
        }
    }

    public record AlternatePurchaseType(
            String id,
            @Nullable StardewCondition condition,
            @Nullable String sourceCondition,
            double chance,
            List<String> animalTypeIds
    ) {
        public AlternatePurchaseType {
            id = requireText(id, "alternate_purchase_types.id");
            if (sourceCondition != null && sourceCondition.isBlank()) {
                sourceCondition = null;
            }
            if (chance < 0.0 || chance > 1.0) {
                throw new IllegalArgumentException(
                        "alternate_purchase_types.chance must be between 0 and 1");
            }
            animalTypeIds = List.copyOf(animalTypeIds);
            if (animalTypeIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "alternate_purchase_types.animal_type_ids must not be empty");
            }
        }
    }

    public record ProduceStat(
            String id,
            String statName,
            @Nullable String sourceRequiredItemId,
            List<String> sourceRequiredTags,
            List<ResourceLocation> requiredItems,
            List<ResourceLocation> requiredTags
    ) {
        public ProduceStat {
            id = requireText(id, "produce_stats.id");
            statName = requireText(
                    statName, "produce_stats.stat_name");
            if (sourceRequiredItemId != null
                    && sourceRequiredItemId.isBlank()) {
                sourceRequiredItemId = null;
            }
            sourceRequiredTags =
                    List.copyOf(sourceRequiredTags);
            requiredItems = List.copyOf(requiredItems);
            requiredTags = List.copyOf(requiredTags);
        }
    }

    @FunctionalInterface
    public interface ConditionEvaluator {
        boolean test(StardewCondition condition);
    }
}
