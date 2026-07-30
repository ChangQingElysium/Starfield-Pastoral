package com.stardew.craft.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The only arithmetic implementation for Stardew combat damage.
 */
public final class DamagePipeline {
    private DamagePipeline() {}

    public static DamageOutcome evaluate(DamageRequest request) {
        return evaluate(request, DamageRandomSource.threadLocal());
    }

    public static DamageOutcome evaluate(DamageRequest request, DamageRandomSource random) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(random, "random");

        List<DamageOutcome.Stage> stages = new ArrayList<>();
        float baseRoll = rollBaseDamage(request, nextUnit(random));
        stages.add(stage(DamageOutcome.Phase.BASE_ROLL, "weapon", 0.0f, baseRoll, "rolled"));

        float damage = baseRoll;
        for (DamageAdjustment adjustment : request.baseAdjustments()) {
            float before = damage;
            damage = apply(adjustment, damage, baseRoll);
            stages.add(adjustmentStage(DamageOutcome.Phase.BASE_FLAT, adjustment, before, damage));
        }
        float resolvedBaseDamage = damage;

        float criticalChance = clamp(request.criticalChance(), 0.0f, 1.0f);
        boolean critical = request.guaranteedCritical() || nextUnit(random) < criticalChance;
        if (critical) {
            float before = damage;
            damage *= Math.max(0.0f, request.criticalMultiplier());
            if (request.sourceKind() == DamageRequest.SourceKind.PLAYER_WEAPON) {
                // SDV GameLocation.damageMonster casts the critical result to int.
                damage = (float) (int) damage;
            }
            stages.add(stage(
                    DamageOutcome.Phase.CRITICAL,
                    request.guaranteedCritical() ? "guaranteed" : "roll",
                    before,
                    damage,
                    formatMultiplier(request.criticalMultiplier())
            ));
        } else {
            stages.add(stage(DamageOutcome.Phase.CRITICAL, "roll", damage, damage, "not_critical"));
        }

        for (DamageAdjustment adjustment : request.preDefenseAdjustments()) {
            float before = damage;
            damage = apply(adjustment, damage, resolvedBaseDamage);
            stages.add(adjustmentStage(DamageOutcome.Phase.PRE_DEFENSE, adjustment, before, damage));
        }

        float variance = range(
                request.varianceMinimum(),
                request.varianceMaximum(),
                nextUnit(random)
        );
        float beforeVariance = damage;
        damage *= variance;
        stages.add(stage(
                DamageOutcome.Phase.VARIANCE,
                "random",
                beforeVariance,
                damage,
                formatMultiplier(variance)
        ));

        float defenseReduction = 0.0f;
        if (!request.ignoreDefense()) {
            float defenseRoll = 0.0f;
            if (request.defenseRule() == DamageRequest.DefenseRule.STARDEW_PLAYER_DEFENSE
                    && request.defense() >= damage * 0.5f) {
                defenseRoll = nextUnit(random);
            }
            defenseReduction = calculateDefenseReduction(
                    damage,
                    request.defense(),
                    request.defenseRule(),
                    defenseRoll
            );
        }
        float beforeDefense = damage;
        damage -= defenseReduction;
        stages.add(stage(
                DamageOutcome.Phase.DEFENSE,
                defenseStageId(request),
                beforeDefense,
                damage,
                String.format(Locale.ROOT, "-%.3f", defenseReduction)
        ));

        float beforeMinimum = damage;
        damage = Math.max(request.minimumFinalDamage(), damage);
        stages.add(stage(
                DamageOutcome.Phase.MINIMUM,
                "floor",
                beforeMinimum,
                damage,
                String.format(Locale.ROOT, "min=%.3f", request.minimumFinalDamage())
        ));

        for (DamageAdjustment adjustment : request.postDefenseAdjustments()) {
            float before = damage;
            damage = apply(adjustment, damage, resolvedBaseDamage);
            stages.add(adjustmentStage(DamageOutcome.Phase.POST_DEFENSE, adjustment, before, damage));
        }

        float beforeFinalize = damage;
        damage = Math.max(0.0f, damage);
        stages.add(stage(
                DamageOutcome.Phase.FINALIZE,
                "non_negative",
                beforeFinalize,
                damage,
                "min=0"
        ));

        float actualMissChance = Math.max(
                0.0f,
                request.missChance() * (1.0f - request.addedPrecision() / 10.0f)
        );
        actualMissChance = clamp(actualMissChance, 0.0f, 1.0f);
        boolean dodged = nextUnit(random) < actualMissChance;
        float beforeDodge = damage;
        if (dodged) {
            damage = 0.0f;
        }
        stages.add(stage(
                DamageOutcome.Phase.DODGE,
                "accuracy",
                beforeDodge,
                damage,
                String.format(Locale.ROOT, "chance=%.4f", actualMissChance)
        ));

        return new DamageOutcome(
                request.sourceId(),
                request.sourceKind(),
                request.skillId(),
                resolvedBaseDamage,
                criticalChance,
                critical,
                request.criticalMultiplier(),
                defenseReduction,
                damage,
                dodged,
                request.inStardewDimension(),
                stages
        );
    }

    static float calculateDefenseReduction(
            float damage,
            float defense,
            DamageRequest.DefenseRule rule,
            float decayRoll
    ) {
        if (damage <= 0.0f || defense <= 0.0f) {
            return 0.0f;
        }
        if (rule == DamageRequest.DefenseRule.FIXED_RESILIENCE
                || defense < damage * 0.5f) {
            return defense;
        }
        int decayStep = Math.min(2, (int) (nextUnit(decayRoll) * 3.0f));
        return defense - defense * decayStep / 10.0f;
    }

    private static float apply(DamageAdjustment adjustment, float current, float baseDamage) {
        float result = switch (adjustment.operation()) {
            case ADD -> current + adjustment.value();
            case MULTIPLY -> current * adjustment.value();
            case REPLACE_WITH_BASE_MULTIPLIER -> baseDamage * adjustment.value();
        };
        return switch (adjustment.rounding()) {
            case NONE -> result;
            case FLOOR -> (float) Math.floor(result);
            case CEILING -> (float) Math.ceil(result);
        };
    }

    private static DamageOutcome.Stage adjustmentStage(
            DamageOutcome.Phase phase,
            DamageAdjustment adjustment,
            float before,
            float after
    ) {
        String note = switch (adjustment.operation()) {
            case ADD -> String.format(Locale.ROOT, "%+.3f", adjustment.value());
            case MULTIPLY, REPLACE_WITH_BASE_MULTIPLIER -> formatMultiplier(adjustment.value());
        };
        if (adjustment.rounding() != DamageAdjustment.Rounding.NONE) {
            note += " " + adjustment.rounding().name().toLowerCase(Locale.ROOT);
        }
        return stage(phase, adjustment.id(), before, after, note);
    }

    private static String defenseStageId(DamageRequest request) {
        if (request.ignoreDefense()) {
            return "ignored";
        }
        return request.defenseRule() == DamageRequest.DefenseRule.STARDEW_PLAYER_DEFENSE
                ? "player_defense"
                : "resilience";
    }

    private static DamageOutcome.Stage stage(
            DamageOutcome.Phase phase,
            String id,
            float before,
            float after,
            String note
    ) {
        return new DamageOutcome.Stage(phase, id, before, after, note);
    }

    private static float range(float minimum, float maximum, float unit) {
        return minimum + unit * (maximum - minimum);
    }

    private static float rollBaseDamage(DamageRequest request, float unit) {
        float minimum = request.minimumBaseDamage();
        float maximum = request.maximumBaseDamage();
        if ((request.sourceKind() == DamageRequest.SourceKind.PLAYER_WEAPON
                || request.sourceKind() == DamageRequest.SourceKind.MONSTER_ATTACK)
                && isWholeNumber(minimum)
                && isWholeNumber(maximum)) {
            int minimumInt = (int) minimum;
            int maximumInt = (int) maximum;
            int possibleValues = maximumInt - minimumInt + 1;
            return minimumInt + Math.min(possibleValues - 1, (int) (unit * possibleValues));
        }
        return range(minimum, maximum, unit);
    }

    private static boolean isWholeNumber(float value) {
        return value == Math.rint(value);
    }

    private static float nextUnit(DamageRandomSource random) {
        return nextUnit(random.nextFloat());
    }

    private static float nextUnit(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Damage random source returned a non-finite value");
        }
        return clamp(value, 0.0f, Math.nextDown(1.0f));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String formatMultiplier(float value) {
        return String.format(Locale.ROOT, "x%.4f", value);
    }
}
