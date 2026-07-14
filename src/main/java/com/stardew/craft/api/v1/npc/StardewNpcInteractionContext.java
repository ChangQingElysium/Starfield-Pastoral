package com.stardew.craft.api.v1.npc;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import java.util.Objects;

/** Server-authoritative context passed to addon NPC interaction providers. */
public record StardewNpcInteractionContext(
        ServerPlayer player,
        StardewNpcEntity npc,
        ResourceLocation npcId,
        InteractionHand hand
) {
    public StardewNpcInteractionContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(npc, "npc");
        Objects.requireNonNull(npcId, "npcId");
        Objects.requireNonNull(hand, "hand");
    }
}
