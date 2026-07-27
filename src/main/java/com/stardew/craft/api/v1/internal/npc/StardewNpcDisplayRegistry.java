package com.stardew.craft.api.v1.internal.npc;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.npc.StardewNpcDisplay;
import com.stardew.craft.api.v1.npc.StardewNpcDisplays;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Internal NPC display metadata dispatch. */
public final class StardewNpcDisplayRegistry {
    private static final OrderedExtensionRegistry<
            StardewNpcDisplays.Provider> PROVIDERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "npc/display"));

    private StardewNpcDisplayRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewNpcDisplays.Provider provider
    ) {
        PROVIDERS.register(id, priority, provider);
    }

    public static StardewNpcDisplay resolve(ResourceLocation npcId) {
        Objects.requireNonNull(npcId, "npcId");
        for (var registered : PROVIDERS.entries()) {
            try {
                StardewNpcDisplay candidate =
                        PROVIDERS.invoke(
                                registered,
                                provider -> provider.resolve(npcId));
                if (candidate != null) {
                    if (!npcId.equals(candidate.npcId())) {
                        StardewCraft.LOGGER.warn(
                                "NPC display provider {} returned {} for requested {}",
                                registered.id(), candidate.npcId(), npcId);
                        continue;
                    }
                    return alignWithProfile(npcId, candidate);
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "NPC display provider {} failed for {}",
                        registered.id(), npcId, exception);
            }
        }
        StardewNpcDisplay profileDisplay = profileDisplay(npcId);
        if (profileDisplay != null) {
            return profileDisplay;
        }
        return fallback(npcId);
    }

    private static StardewNpcDisplay profileDisplay(ResourceLocation npcId) {
        var definition = StardewNpcProfileRegistry.resolveRegistered(npcId);
        return definition == null ? null : definition.display();
    }

    private static StardewNpcDisplay fallback(ResourceLocation npcId) {
        String namespace = npcId.getNamespace();
        String path = npcId.getPath();
        boolean core = StardewCraft.MODID.equals(namespace);
        Boolean datable = authoritativeDatable(npcId);
        String nameKey = core
                ? "entity.stardewcraft.npc." + path
                : "entity." + namespace + ".npc." + path;
        ResourceLocation portrait = ResourceLocation.fromNamespaceAndPath(
                namespace, "textures/portraits/" + path + ".png");
        ResourceLocation mugshot = ResourceLocation.fromNamespaceAndPath(
                namespace, "textures/mugshots/" + path + ".png");
        return new StardewNpcDisplay(
                npcId,
                nameKey,
                portrait,
                128,
                320,
                mugshot,
                16,
                24,
                "stardewcraft.social.relationship.friend",
                datable != null && datable
        );
    }

    /**
     * The unified profile is authoritative for registered NPCs, followed by capability data
     * for legacy projections. A display provider may customize presentation, but cannot
     * silently change gameplay relationship rules or make API views inconsistent.
     */
    private static StardewNpcDisplay alignWithProfile(
            ResourceLocation npcId,
            StardewNpcDisplay display
    ) {
        Boolean datable = authoritativeDatable(npcId);
        if (datable == null || datable == display.datable()) {
            return display;
        }
        return new StardewNpcDisplay(
                display.npcId(),
                display.nameTranslationKey(),
                display.portraitTexture(),
                display.portraitSheetWidth(),
                display.portraitSheetHeight(),
                display.mugshotTexture(),
                display.mugshotSheetWidth(),
                display.mugshotSheetHeight(),
                display.relationshipTranslationKey(),
                datable
        );
    }

    private static Boolean authoritativeDatable(ResourceLocation npcId) {
        var definition = StardewNpcProfileRegistry.resolveRegistered(npcId);
        if (definition != null) {
            return definition.profile().datable();
        }
        NpcCapabilityProfile capability = capability(npcId);
        return capability == null ? null : capability.datable();
    }

    private static NpcCapabilityProfile capability(ResourceLocation npcId) {
        String key = StardewCraft.MODID.equals(npcId.getNamespace())
                ? npcId.getPath()
                : npcId.toString();
        return NpcDataRegistry.capabilities().get(key);
    }

}
