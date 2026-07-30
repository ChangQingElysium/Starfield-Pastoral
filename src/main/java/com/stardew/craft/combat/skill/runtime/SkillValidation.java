package com.stardew.craft.combat.skill.runtime;

import java.util.Objects;

public record SkillValidation(boolean accepted, RejectionReason rejectionReason) {
    public SkillValidation {
        if (accepted && rejectionReason != RejectionReason.NONE) {
            throw new IllegalArgumentException("Accepted validation cannot have a rejection reason");
        }
        if (!accepted && rejectionReason == RejectionReason.NONE) {
            throw new IllegalArgumentException("Rejected validation must have a reason");
        }
        Objects.requireNonNull(rejectionReason, "rejectionReason");
    }

    public static SkillValidation accept() {
        return new SkillValidation(true, RejectionReason.NONE);
    }

    public static SkillValidation reject(RejectionReason reason) {
        return new SkillValidation(false, reason);
    }

    public enum RejectionReason {
        NONE,
        COOLDOWN,
        INVALID_WEAPON,
        INVALID_STATE
    }
}
