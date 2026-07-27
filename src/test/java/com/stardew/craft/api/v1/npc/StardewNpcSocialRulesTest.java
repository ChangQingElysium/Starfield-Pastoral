package com.stardew.craft.api.v1.npc;

import com.stardew.craft.api.v1.internal.npc.StardewNpcSocialRuleRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewNpcSocialRulesTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void providersAreOrderedAndFailuresFallThrough() {
        int suffix = IDS.incrementAndGet();
        ResourceLocation npcId = id("npc_" + suffix);
        List<String> calls = new ArrayList<>();

        StardewNpcSocialRules.register(id("throwing_" + suffix), 300,
                (context, rule, proposed) -> {
                    if (!context.npcId().equals(npcId)) {
                        return StardewNpcSocialRules.Decision.PASS;
                    }
                    calls.add("throwing");
                    throw new IllegalStateException("expected test failure");
                });
        StardewNpcSocialRules.register(id("allow_" + suffix), 200,
                (context, rule, proposed) -> {
                    if (!context.npcId().equals(npcId)) {
                        return StardewNpcSocialRules.Decision.PASS;
                    }
                    calls.add("allow");
                    assertFalse(proposed);
                    return StardewNpcSocialRules.Decision.ALLOW;
                });
        StardewNpcSocialRules.register(id("late_deny_" + suffix), 100,
                (context, rule, proposed) -> {
                    if (!context.npcId().equals(npcId)) {
                        return StardewNpcSocialRules.Decision.PASS;
                    }
                    calls.add("late_deny");
                    return StardewNpcSocialRules.Decision.DENY;
                });

        boolean result = StardewNpcSocialRuleRegistry.evaluate(
                new StardewNpcSocialContext(npcId, null, null, null),
                StardewNpcSocialRules.Rule.CAN_SOCIALIZE,
                false);

        assertTrue(result);
        assertEquals(List.of("throwing", "allow"), calls);
    }

    @Test
    void duplicateProviderIdsAndInvalidSnapshotsAreRejected() {
        int suffix = IDS.incrementAndGet();
        ResourceLocation duplicate = id("duplicate_" + suffix);
        StardewNpcSocialRules.register(
                duplicate, 0, (context, rule, proposed) ->
                        StardewNpcSocialRules.Decision.PASS);
        assertThrows(IllegalStateException.class, () ->
                StardewNpcSocialRules.register(
                        duplicate, 0, (context, rule, proposed) ->
                                StardewNpcSocialRules.Decision.PASS));
        assertThrows(IllegalArgumentException.class, () ->
                new StardewNpcFriendshipSnapshot(
                        -1, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void outcomeSeparatesRepeatableAndOneShotRewards() {
        assertTrue(StardewNpcFriendshipRewards.Outcome.CHANGED.changed());
        assertFalse(StardewNpcFriendshipRewards.Outcome.CHANGED.complete());
        assertTrue(StardewNpcFriendshipRewards.Outcome.COMPLETE.changed());
        assertTrue(StardewNpcFriendshipRewards.Outcome.COMPLETE.complete());
        assertFalse(
                StardewNpcFriendshipRewards.Outcome.COMPLETE_WITHOUT_REWARD.changed());
        assertTrue(
                StardewNpcFriendshipRewards.Outcome.COMPLETE_WITHOUT_REWARD.complete());
    }

    @Test
    void displayProvidersValidateIdentityAndFallThrough() {
        int suffix = IDS.incrementAndGet();
        ResourceLocation npcId = id("display_npc_" + suffix);
        StardewNpcDisplays.register(id("display_wrong_" + suffix), 200,
                requested -> requested.equals(npcId)
                        ? display(id("different_" + suffix))
                        : null);
        StardewNpcDisplays.register(id("display_selected_" + suffix), 100,
                requested -> requested.equals(npcId) ? display(npcId) : null);

        StardewNpcDisplay resolved = StardewNpcDisplays.resolve(npcId);
        assertEquals(npcId, resolved.npcId());
        assertEquals("entity.npc_social_test.npc." + npcId.getPath(),
                resolved.nameTranslationKey());
    }

    @Test
    void unifiedProfilesFeedCatalogDisplayAndSocialRules() {
        int suffix = IDS.incrementAndGet();
        ResourceLocation npcId = id("profile_npc_" + suffix);
        StardewNpcProfile profile = new StardewNpcProfile(
                npcId, true, false, "idle_only",
                2, 1, 1, 0, 1, false);
        StardewNpcDisplay display = display(npcId);

        StardewNpcProfiles.register(
                id("profile_registration_" + suffix),
                100,
                new StardewNpcDefinition(npcId, profile, display));

        StardewNpcDefinition resolved = StardewNpcProfiles.resolve(npcId).orElseThrow();
        assertEquals(profile, resolved.profile());
        assertEquals(display, StardewNpcDisplays.resolve(npcId));
        assertTrue(StardewNpcProfiles.ids().contains(npcId));
        assertTrue(com.stardew.craft.npc.data.NpcSocialRules.shouldShowOnSocialPage(
                npcId.toString(), null, null, null));
        assertNotNull(resolved.display());
    }

    @Test
    void unifiedProfilesRejectMismatchedIdentity() {
        int suffix = IDS.incrementAndGet();
        ResourceLocation npcId = id("profile_expected_" + suffix);
        ResourceLocation otherId = id("profile_other_" + suffix);
        StardewNpcProfile profile = new StardewNpcProfile(
                otherId, true, false, "idle_only",
                0, 0, 0, 0, 0, false);
        assertThrows(IllegalArgumentException.class, () ->
                new StardewNpcDefinition(npcId, profile, display(npcId)));
    }

    @Test
    void giftRegistrationsRejectDuplicatesWithinEachHookKind() {
        int suffix = IDS.incrementAndGet();
        ResourceLocation confirmationId = id("gift_confirmation_" + suffix);
        StardewNpcGifts.registerConfirmationPolicy(
                confirmationId,
                0,
                context -> StardewNpcGifts.Confirmation.PASS);
        assertThrows(IllegalStateException.class, () ->
                StardewNpcGifts.registerConfirmationPolicy(
                        confirmationId,
                        0,
                        context -> StardewNpcGifts.Confirmation.PASS));

        ResourceLocation beforeId = id("gift_before_" + suffix);
        StardewNpcGifts.registerBeforeHook(
                beforeId,
                0,
                context -> StardewNpcGifts.BeforeDecision.PASS);
        assertThrows(IllegalStateException.class, () ->
                StardewNpcGifts.registerBeforeHook(
                        beforeId,
                        0,
                        context -> StardewNpcGifts.BeforeDecision.PASS));
    }

    private static StardewNpcDisplay display(ResourceLocation npcId) {
        return new StardewNpcDisplay(
                npcId,
                "entity.npc_social_test.npc." + npcId.getPath(),
                id("textures/portraits/" + npcId.getPath() + ".png"),
                128,
                320,
                id("textures/mugshots/" + npcId.getPath() + ".png"),
                16,
                24,
                "npc_social_test.relationship.friend",
                false
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("npc_social_test", path);
    }
}
