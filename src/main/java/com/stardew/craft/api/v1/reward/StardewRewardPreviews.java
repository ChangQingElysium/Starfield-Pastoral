package com.stardew.craft.api.v1.reward;

import com.stardew.craft.api.v1.internal.reward.StardewRewardPreviewRegistry;
import com.stardew.craft.api.v1.progress.StardewProgressKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Objects;

/** Display-only reward lookup; granting remains owned by each server-authoritative system. */
public final class StardewRewardPreviews {
    private StardewRewardPreviews() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            StardewRewardPreviewProvider provider
    ) {
        StardewRewardPreviewRegistry.register(
                registrationId, priority, provider);
    }

    @Nullable
    public static StardewRewardPreview preview(
            ServerPlayer player,
            StardewProgressKey progress
    ) {
        return StardewRewardPreviewRegistry.preview(
                Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(progress, "progress"));
    }
}
