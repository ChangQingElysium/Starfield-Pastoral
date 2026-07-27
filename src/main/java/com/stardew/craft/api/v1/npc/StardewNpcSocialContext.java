package com.stardew.craft.api.v1.npc;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Objects;

/** Immutable context for one NPC social-rule decision. */
public record StardewNpcSocialContext(
        ResourceLocation npcId,
        @Nullable ServerPlayer player,
        @Nullable StardewNpcProfile profile,
        @Nullable StardewNpcFriendshipSnapshot friendship
) {
    public StardewNpcSocialContext {
        npcId = Objects.requireNonNull(npcId, "npcId");
    }
}
