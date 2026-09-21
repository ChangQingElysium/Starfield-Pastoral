package com.stardew.craft.api.v1.agriculture;

import java.util.Objects;

/** Result returned after an authoritative crop harvest attempt. */
public record StardewCropHarvestResult(
        Status status,
        int farmingExperience
) {
    public StardewCropHarvestResult {
        status = Objects.requireNonNull(status, "status");
        if (farmingExperience < 0) {
            throw new IllegalArgumentException("Farming experience cannot be negative");
        }
        if (status != Status.HARVESTED && farmingExperience != 0) {
            throw new IllegalArgumentException(
                    "Only a successful harvest can award farming experience");
        }
    }

    public static StardewCropHarvestResult pass() {
        return new StardewCropHarvestResult(Status.PASS, 0);
    }

    public static StardewCropHarvestResult notReady() {
        return new StardewCropHarvestResult(Status.NOT_READY, 0);
    }

    public static StardewCropHarvestResult wrongTool() {
        return new StardewCropHarvestResult(Status.WRONG_TOOL, 0);
    }

    public static StardewCropHarvestResult harvested(int farmingExperience) {
        return new StardewCropHarvestResult(Status.HARVESTED, farmingExperience);
    }

    public boolean harvested() {
        return status == Status.HARVESTED;
    }

    public enum Status {
        PASS,
        NOT_READY,
        WRONG_TOOL,
        HARVESTED
    }
}
