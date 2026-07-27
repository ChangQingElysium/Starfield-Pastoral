package com.stardew.craft.api.v1.npc;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Immutable NPC capability data exposed without leaking the internal data model. */
public record StardewNpcProfile(
        ResourceLocation npcId,
        boolean implemented,
        boolean pathingEnabled,
        String animationProfile,
        int age,
        int manners,
        int socialAnxiety,
        int optimism,
        int gender,
        boolean datable
) {
    public StardewNpcProfile {
        npcId = Objects.requireNonNull(npcId, "npcId");
        animationProfile = Objects.requireNonNull(animationProfile, "animationProfile");
    }
}
