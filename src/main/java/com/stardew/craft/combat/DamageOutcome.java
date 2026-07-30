package com.stardew.craft.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable result and complete arithmetic trace for one damage request.
 */
public final class DamageOutcome {
    private final String sourceId;
    private final DamageRequest.SourceKind sourceKind;
    private final String skillId;
    private final float baseDamage;
    private final float criticalChance;
    private final boolean critical;
    private final float criticalMultiplier;
    private final float defenseReduction;
    private final float finalDamage;
    private final boolean dodged;
    private final boolean inStardewDimension;
    private final List<Stage> stages;

    DamageOutcome(
            String sourceId,
            DamageRequest.SourceKind sourceKind,
            String skillId,
            float baseDamage,
            float criticalChance,
            boolean critical,
            float criticalMultiplier,
            float defenseReduction,
            float finalDamage,
            boolean dodged,
            boolean inStardewDimension,
            List<Stage> stages
    ) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.sourceKind = Objects.requireNonNull(sourceKind, "sourceKind");
        this.skillId = Objects.requireNonNull(skillId, "skillId");
        this.baseDamage = baseDamage;
        this.criticalChance = criticalChance;
        this.critical = critical;
        this.criticalMultiplier = criticalMultiplier;
        this.defenseReduction = defenseReduction;
        this.finalDamage = finalDamage;
        this.dodged = dodged;
        this.inStardewDimension = inStardewDimension;
        this.stages = List.copyOf(stages);
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSkillId() {
        return skillId;
    }

    public DamageRequest.SourceKind getSourceKind() {
        return sourceKind;
    }

    public float getBaseDamage() {
        return baseDamage;
    }

    public float getCriticalChance() {
        return criticalChance;
    }

    public boolean isCrit() {
        return critical;
    }

    public float getCritMultiplier() {
        return criticalMultiplier;
    }

    public float getDefenseReduction() {
        return defenseReduction;
    }

    public float getFinalDamage() {
        return finalDamage;
    }

    public int getFinalDamageInt() {
        return Math.round(finalDamage);
    }

    public boolean isDodged() {
        return dodged;
    }

    public boolean isInStardewDimension() {
        return inStardewDimension;
    }

    public List<Stage> getStages() {
        return stages;
    }

    public List<String> toExplainLines() {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(
                Locale.ROOT,
                "source=%s kind=%s skill=%s dimension=%s crit=%s(%.2f%% x%.2f) dodged=%s",
                sourceId,
                sourceKind.name().toLowerCase(Locale.ROOT),
                skillId,
                inStardewDimension ? "stardew" : "other",
                critical,
                criticalChance * 100.0f,
                criticalMultiplier,
                dodged
        ));
        for (Stage stage : stages) {
            lines.add(String.format(
                    Locale.ROOT,
                    "%s/%s: %.3f -> %.3f%s",
                    stage.phase().name().toLowerCase(Locale.ROOT),
                    stage.id(),
                    stage.before(),
                    stage.after(),
                    stage.note().isBlank() ? "" : " (" + stage.note() + ")"
            ));
        }
        lines.add(String.format(Locale.ROOT, "final=%.3f", finalDamage));
        return List.copyOf(lines);
    }

    public String toDetailString() {
        return String.join(" | ", toExplainLines());
    }

    public record Stage(Phase phase, String id, float before, float after, String note) {
        public Stage {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(note, "note");
        }
    }

    public enum Phase {
        BASE_ROLL,
        BASE_FLAT,
        CRITICAL,
        PRE_DEFENSE,
        VARIANCE,
        DEFENSE,
        MINIMUM,
        POST_DEFENSE,
        FINALIZE,
        DODGE
    }
}
