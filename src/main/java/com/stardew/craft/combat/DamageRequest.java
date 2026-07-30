package com.stardew.craft.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable input for one damage calculation.
 */
public record DamageRequest(
        String sourceId,
        SourceKind sourceKind,
        String skillId,
        float minimumBaseDamage,
        float maximumBaseDamage,
        List<DamageAdjustment> baseAdjustments,
        float criticalChance,
        float criticalMultiplier,
        boolean guaranteedCritical,
        List<DamageAdjustment> preDefenseAdjustments,
        float varianceMinimum,
        float varianceMaximum,
        float defense,
        boolean ignoreDefense,
        DefenseRule defenseRule,
        float minimumFinalDamage,
        List<DamageAdjustment> postDefenseAdjustments,
        float missChance,
        float addedPrecision,
        boolean inStardewDimension
) {
    public DamageRequest {
        sourceId = requireId(sourceId, "sourceId");
        Objects.requireNonNull(sourceKind, "sourceKind");
        skillId = requireId(skillId, "skillId");
        requireFinite(minimumBaseDamage, "minimumBaseDamage");
        requireFinite(maximumBaseDamage, "maximumBaseDamage");
        requireFinite(criticalChance, "criticalChance");
        requireFinite(criticalMultiplier, "criticalMultiplier");
        requireFinite(varianceMinimum, "varianceMinimum");
        requireFinite(varianceMaximum, "varianceMaximum");
        requireFinite(defense, "defense");
        Objects.requireNonNull(defenseRule, "defenseRule");
        requireFinite(minimumFinalDamage, "minimumFinalDamage");
        requireFinite(missChance, "missChance");
        requireFinite(addedPrecision, "addedPrecision");
        if (maximumBaseDamage < minimumBaseDamage) {
            throw new IllegalArgumentException("maximumBaseDamage cannot be less than minimumBaseDamage");
        }
        if (varianceMaximum < varianceMinimum) {
            throw new IllegalArgumentException("varianceMaximum cannot be less than varianceMinimum");
        }
        baseAdjustments = List.copyOf(baseAdjustments);
        preDefenseAdjustments = List.copyOf(preDefenseAdjustments);
        postDefenseAdjustments = List.copyOf(postDefenseAdjustments);
    }

    public static Builder builder(String sourceId) {
        return new Builder(sourceId);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public enum SourceKind {
        PLAYER_WEAPON,
        MONSTER_ATTACK,
        DIRECT_ENTITY,
        ENVIRONMENT
    }

    /**
     * Stardew uses two different defense rules:
     * monster resilience is always subtracted in full, while a farmer's
     * defense can lose 0%, 10%, or 20% when it is at least half the hit.
     */
    public enum DefenseRule {
        FIXED_RESILIENCE,
        STARDEW_PLAYER_DEFENSE
    }

    private static String requireId(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }

    private static void requireFinite(float value, String field) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }

    public static final class Builder {
        private final String sourceId;
        private SourceKind sourceKind = SourceKind.PLAYER_WEAPON;
        private String skillId = "normal";
        private float minimumBaseDamage;
        private float maximumBaseDamage;
        private final List<DamageAdjustment> baseAdjustments = new ArrayList<>();
        private float criticalChance;
        private float criticalMultiplier = 1.0f;
        private boolean guaranteedCritical;
        private final List<DamageAdjustment> preDefenseAdjustments = new ArrayList<>();
        private float varianceMinimum = 1.0f;
        private float varianceMaximum = 1.0f;
        private float defense;
        private boolean ignoreDefense;
        private DefenseRule defenseRule = DefenseRule.FIXED_RESILIENCE;
        private float minimumFinalDamage = 1.0f;
        private final List<DamageAdjustment> postDefenseAdjustments = new ArrayList<>();
        private float missChance;
        private float addedPrecision;
        private boolean inStardewDimension;

        private Builder(String sourceId) {
            this.sourceId = sourceId;
        }

        private Builder(DamageRequest request) {
            this.sourceId = request.sourceId;
            this.sourceKind = request.sourceKind;
            this.skillId = request.skillId;
            this.minimumBaseDamage = request.minimumBaseDamage;
            this.maximumBaseDamage = request.maximumBaseDamage;
            this.baseAdjustments.addAll(request.baseAdjustments);
            this.criticalChance = request.criticalChance;
            this.criticalMultiplier = request.criticalMultiplier;
            this.guaranteedCritical = request.guaranteedCritical;
            this.preDefenseAdjustments.addAll(request.preDefenseAdjustments);
            this.varianceMinimum = request.varianceMinimum;
            this.varianceMaximum = request.varianceMaximum;
            this.defense = request.defense;
            this.ignoreDefense = request.ignoreDefense;
            this.defenseRule = request.defenseRule;
            this.minimumFinalDamage = request.minimumFinalDamage;
            this.postDefenseAdjustments.addAll(request.postDefenseAdjustments);
            this.missChance = request.missChance;
            this.addedPrecision = request.addedPrecision;
            this.inStardewDimension = request.inStardewDimension;
        }

        public Builder skillId(String value) {
            this.skillId = value;
            return this;
        }

        public Builder sourceKind(SourceKind value) {
            this.sourceKind = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder baseDamage(float minimum, float maximum) {
            this.minimumBaseDamage = minimum;
            this.maximumBaseDamage = maximum;
            return this;
        }

        public Builder addBaseAdjustment(DamageAdjustment adjustment) {
            this.baseAdjustments.add(Objects.requireNonNull(adjustment, "adjustment"));
            return this;
        }

        public Builder critical(float chance, float multiplier, boolean guaranteed) {
            this.criticalChance = chance;
            this.criticalMultiplier = multiplier;
            this.guaranteedCritical = guaranteed;
            return this;
        }

        public Builder addPreDefenseAdjustment(DamageAdjustment adjustment) {
            this.preDefenseAdjustments.add(Objects.requireNonNull(adjustment, "adjustment"));
            return this;
        }

        public Builder variance(float minimum, float maximum) {
            this.varianceMinimum = minimum;
            this.varianceMaximum = maximum;
            return this;
        }

        public Builder defense(float value, boolean ignored) {
            this.defense = value;
            this.ignoreDefense = ignored;
            return this;
        }

        public Builder defenseRule(DefenseRule value) {
            this.defenseRule = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder minimumFinalDamage(float value) {
            this.minimumFinalDamage = value;
            return this;
        }

        public Builder addPostDefenseAdjustment(DamageAdjustment adjustment) {
            this.postDefenseAdjustments.add(Objects.requireNonNull(adjustment, "adjustment"));
            return this;
        }

        public Builder accuracy(float targetMissChance, float weaponAddedPrecision) {
            this.missChance = targetMissChance;
            this.addedPrecision = weaponAddedPrecision;
            return this;
        }

        public Builder inStardewDimension(boolean value) {
            this.inStardewDimension = value;
            return this;
        }

        public DamageRequest build() {
            return new DamageRequest(
                    sourceId,
                    sourceKind,
                    skillId,
                    minimumBaseDamage,
                    maximumBaseDamage,
                    baseAdjustments,
                    criticalChance,
                    criticalMultiplier,
                    guaranteedCritical,
                    preDefenseAdjustments,
                    varianceMinimum,
                    varianceMaximum,
                    defense,
                    ignoreDefense,
                    defenseRule,
                    minimumFinalDamage,
                    postDefenseAdjustments,
                    missChance,
                    addedPrecision,
                    inStardewDimension
            );
        }
    }
}
