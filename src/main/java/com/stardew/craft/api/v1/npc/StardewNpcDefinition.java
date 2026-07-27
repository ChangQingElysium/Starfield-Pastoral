package com.stardew.craft.api.v1.npc;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Immutable NPC identity shared by client presentation and server-side social rules.
 *
 * <p>Runtime concerns such as entity lookup, dialogue selection and conditional social rules
 * remain composable through their dedicated APIs. This definition supplies the common identity
 * and capability data those systems use.
 */
public record StardewNpcDefinition(
        ResourceLocation npcId,
        StardewNpcProfile profile,
        StardewNpcDisplay display
) {
    public StardewNpcDefinition {
        npcId = Objects.requireNonNull(npcId, "npcId");
        profile = Objects.requireNonNull(profile, "profile");
        display = Objects.requireNonNull(display, "display");
        if (!npcId.equals(profile.npcId())) {
            throw new IllegalArgumentException("profile npcId must match definition npcId");
        }
        if (!npcId.equals(display.npcId())) {
            throw new IllegalArgumentException("display npcId must match definition npcId");
        }
        if (profile.datable() != display.datable()) {
            throw new IllegalArgumentException(
                    "profile and display must agree on datable");
        }
    }
}
