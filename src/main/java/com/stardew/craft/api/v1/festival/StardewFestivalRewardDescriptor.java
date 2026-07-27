package com.stardew.craft.api.v1.festival;

import com.stardew.craft.api.v1.reward.StardewRewardComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * Explicit, display-only catalog entry for one handler-owned festival reward.
 *
 * <p>The preview is not an authorization result. In particular,
 * {@code previewExhaustive} only describes whether the preview fully lists the
 * successful grant; it does not imply that the current player can claim it.
 */
public record StardewFestivalRewardDescriptor(
        ResourceLocation festivalId,
        ResourceLocation rewardId,
        List<StardewRewardComponent> preview,
        boolean previewExhaustive
) {
    public StardewFestivalRewardDescriptor {
        Objects.requireNonNull(festivalId, "festivalId");
        Objects.requireNonNull(rewardId, "rewardId");
        preview = List.copyOf(preview);
    }
}
