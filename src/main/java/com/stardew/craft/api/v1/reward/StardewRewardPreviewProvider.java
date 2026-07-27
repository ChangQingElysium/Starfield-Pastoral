package com.stardew.craft.api.v1.reward;

import com.stardew.craft.api.v1.progress.StardewProgressKey;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/** Composes or supplies a display-only reward preview without granting it. */
@FunctionalInterface
public interface StardewRewardPreviewProvider {
    /**
     * Returns a replacement preview, or {@code null} to preserve the proposed value.
     */
    @Nullable
    StardewRewardPreview preview(
            ServerPlayer player,
            StardewProgressKey progress,
            @Nullable StardewRewardPreview proposed
    );
}
