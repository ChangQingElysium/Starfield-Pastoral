package com.stardew.craft.api.v1.npc;

import com.stardew.craft.api.v1.internal.npc.StardewNpcSocialRuleRegistry;
import net.minecraft.resources.ResourceLocation;

/** Ordered overrides for social eligibility, gifting and social-page membership. */
public final class StardewNpcSocialRules {
    private StardewNpcSocialRules() {
    }

    public static void register(ResourceLocation id, int priority, Provider provider) {
        StardewNpcSocialRuleRegistry.register(id, priority, provider);
    }

    public enum Rule {
        CAN_SOCIALIZE,
        CAN_RECEIVE_GIFTS,
        SHOW_ON_SOCIAL_PAGE,
        CREATE_FRIENDSHIP_FOR_SOCIAL_PAGE,
        INCLUDE_IN_INTRODUCTIONS
    }

    public enum Decision {
        PASS,
        ALLOW,
        DENY
    }

    @FunctionalInterface
    public interface Provider {
        /**
         * Returns the first decisive override for this rule.
         *
         * @param proposedResult the result produced by StardewCraft's built-in rules
         */
        Decision decide(
                StardewNpcSocialContext context,
                Rule rule,
                boolean proposedResult
        );
    }
}
