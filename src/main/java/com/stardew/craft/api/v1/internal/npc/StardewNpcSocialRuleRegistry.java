package com.stardew.craft.api.v1.internal.npc;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.npc.StardewNpcSocialContext;
import com.stardew.craft.api.v1.npc.StardewNpcSocialRules;
import net.minecraft.resources.ResourceLocation;

/** Internal social-rule dispatch. */
public final class StardewNpcSocialRuleRegistry {
    private static final OrderedExtensionRegistry<
            StardewNpcSocialRules.Provider> PROVIDERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "npc/social_rule"));

    private StardewNpcSocialRuleRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewNpcSocialRules.Provider provider
    ) {
        PROVIDERS.register(id, priority, provider);
    }

    public static boolean evaluate(
            StardewNpcSocialContext context,
            StardewNpcSocialRules.Rule rule,
            boolean proposedResult
    ) {
        for (var registered : PROVIDERS.entries()) {
            try {
                StardewNpcSocialRules.Decision decision =
                        PROVIDERS.invoke(
                                registered,
                                provider -> provider.decide(
                                        context, rule, proposedResult));
                if (decision == StardewNpcSocialRules.Decision.ALLOW) {
                    return true;
                }
                if (decision == StardewNpcSocialRules.Decision.DENY) {
                    return false;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "NPC social rule provider {} failed for {} rule {}",
                        registered.id(), context.npcId(), rule, exception);
            }
        }
        return proposedResult;
    }
}
