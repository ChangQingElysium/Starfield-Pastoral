package com.stardew.craft.api.v1.internal.npc;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.npc.StardewNpcDefinition;
import com.stardew.craft.api.v1.npc.StardewNpcDisplay;
import com.stardew.craft.api.v1.npc.StardewNpcDisplays;
import com.stardew.craft.api.v1.npc.StardewNpcProfile;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Internal unified NPC profile dispatch and core-data projection. */
public final class StardewNpcProfileRegistry {
    private static final OrderedExtensionRegistry<StardewNpcDefinition> DEFINITIONS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "npc/profile"));

    private StardewNpcProfileRegistry() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            StardewNpcDefinition definition
    ) {
        DEFINITIONS.register(registrationId, priority, definition);
    }

    public static StardewNpcDefinition resolve(ResourceLocation npcId) {
        Objects.requireNonNull(npcId, "npcId");
        StardewNpcDefinition registered = resolveRegistered(npcId);
        if (registered != null) {
            return registered;
        }
        NpcCapabilityProfile core = coreProfile(npcId);
        if (core == null) {
            return null;
        }
        return new StardewNpcDefinition(
                npcId,
                toPublic(npcId, core),
                StardewNpcDisplays.resolve(npcId));
    }

    public static StardewNpcDefinition resolveRegistered(ResourceLocation npcId) {
        Objects.requireNonNull(npcId, "npcId");
        for (var registered : DEFINITIONS.entries()) {
            StardewNpcDefinition definition = registered.extension();
            if (npcId.equals(definition.npcId())) {
                return definition;
            }
        }
        return null;
    }

    public static List<ResourceLocation> ids() {
        TreeSet<ResourceLocation> ids = new TreeSet<>();
        for (String rawId : NpcDataRegistry.capabilities().keySet()) {
            ResourceLocation id = com.stardew.craft.api.v1.npc.StardewNpcInteractions
                    .normalizeNpcId(rawId);
            if (id != null) {
                ids.add(id);
            }
        }
        for (var registered : DEFINITIONS.entries()) {
            ids.add(registered.extension().npcId());
        }
        return List.copyOf(ids);
    }

    public static StardewNpcProfile publicProfile(
            ResourceLocation npcId,
            NpcCapabilityProfile coreProfile
    ) {
        StardewNpcDefinition registered = resolveRegistered(npcId);
        if (registered != null) {
            return registered.profile();
        }
        if (coreProfile != null) {
            return toPublic(npcId, coreProfile);
        }
        return null;
    }

    private static NpcCapabilityProfile coreProfile(ResourceLocation npcId) {
        String key = StardewCraft.MODID.equals(npcId.getNamespace())
                ? npcId.getPath()
                : npcId.toString();
        return NpcDataRegistry.capabilities().get(key);
    }

    private static StardewNpcProfile toPublic(
            ResourceLocation npcId,
            NpcCapabilityProfile profile
    ) {
        return new StardewNpcProfile(
                npcId,
                profile.implemented(),
                profile.pathingEnabled(),
                profile.animationProfile(),
                profile.age(),
                profile.manners(),
                profile.socialAnxiety(),
                profile.optimism(),
                profile.gender(),
                profile.datable());
    }
}
