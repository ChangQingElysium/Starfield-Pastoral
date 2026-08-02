package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplarJudgementShareAppliedPostContractTest {
    @Test
    void directMagicShareOwnsAnExactNonWeaponPostFrame()
            throws IOException {
        String handler = source(
                "combat/skill/TemplarJudgementHandler.java"
        );
        String store = source(
                "combat/AuthoredDirectDamageContextStore.java"
        );
        String events = source("combat/AuthoredDirectDamageEvents.java");
        String playerEvents = source(
                "player/PlayerDataEventHandler.java"
        );
        String weaponEvents = source("combat/WeaponCombatEvents.java");
        String cleanup = source("combat/CombatTrackerCleanup.java");

        String applied = method(
                handler,
                "public static void onAppliedPlayerDamage("
        );
        assertOrdered(
                applied,
                "AuthoredDirectDamageContextStore.isBound(",
                "TemplarJudgementSkillHandler.isActive(",
                "TemplarJudgementSkillHandler.cappedSharedDamage(",
                "authoredMagicSource(player)",
                "AuthoredDirectDamageContextStore.bind(",
                "target.hurt(source, share)",
                "AuthoredDirectDamageContextStore.discard(target, source)"
        );
        assertTrue(applied.contains("appliedDamage"));
        assertTrue(applied.contains("SHARE_DAMAGE_ID"));
        assertTrue(applied.contains("try {"));
        assertTrue(applied.contains("finally {"));
        assertFalse(applied.contains("new DamageNumberPayload("));
        assertFalse(applied.contains("TemplarJudgementImpactPayload"));
        assertFalse(handler.contains("LivingIncomingDamageEvent"));
        assertFalse(handler.contains("event.getAmount()"));
        assertFalse(handler.contains("event.setAmount("));

        String magicSource = method(
                handler,
                "private static DamageSource authoredMagicSource("
        );
        assertOrdered(
                magicSource,
                "player.level().damageSources().magic()",
                "new DamageSource(",
                "magic.typeHolder()",
                "player,",
                "player",
                "HitCooldownDamageSource.bypassVanillaCooldown("
        );

        String post = method(events, "public static void onDamagePost(");
        assertTrue(post.contains("onAppliedDamage("));
        assertTrue(post.contains("event.getEntity()"));
        assertTrue(post.contains("event.getSource()"));
        assertTrue(post.contains("event.getNewDamage()"));

        String router = method(events, "public static void onAppliedDamage(");
        assertOrdered(
                router,
                "AuthoredDirectDamageContextStore.consume(",
                "frame.authoredId()",
                "TemplarJudgementHandler.emitAppliedShare(",
                "return;",
                "TemplarJudgementHandler.onAppliedPlayerDamage("
        );
        assertTrue(router.contains("appliedDamage > 0.0F"));

        String customHealth = method(
                playerEvents,
                "public static void onPlayerHurt("
        );
        assertOrdered(
                customHealth,
                "event.getSource().getEntity()",
                "else if (dmgSourceEntity != null)",
                "DamageRequest.SourceKind.DIRECT_ENTITY",
                "DamageRequest.SourceKind.ENVIRONMENT",
                "int oldSdHealth = data.getHealth();",
                "int newSdHealth = Math.max(0, oldSdHealth - sdDamage);",
                "data.setHealth(newSdHealth);",
                "AuthoredDirectDamageEvents.onAppliedDamage(",
                "event.getSource()",
                "oldSdHealth - newSdHealth"
        );

        String weaponProvenance = method(
                weaponEvents,
                "private static WeaponDamageAdmission "
                        + "classifyWeaponDamageProvenance("
        );
        assertTrue(weaponProvenance.contains(
                "source.is(DamageTypes.PLAYER_ATTACK)"
        ));
        assertTrue(weaponProvenance.contains(
                "return admission(WeaponDamageProvenance.OTHER);"
        ));
        assertFalse(magicSource.contains("DamageTypes.PLAYER_ATTACK"));

        assertTrue(store.contains("source == candidate"));
        assertTrue(store.contains("targetId.equals(candidateTargetId)"));
        assertTrue(store.contains("Deque<BoundFrame> frames"));
        assertTrue(store.contains("frames.push("));
        assertTrue(store.contains("frames.peek()"));
        assertFalse(store.contains("WeaponSkillDamage"));
        assertFalse(store.contains("DamageNumberContextStore.bind("));
        assertFalse(events.contains("ResolvedWeaponHit"));
        assertFalse(events.contains("WeaponSkillDamage"));
        assertTrue(cleanup.contains(
                "AuthoredDirectDamageContextStore.clear(player)"
        ));
    }

    @Test
    void sharePresentationUsesOnlyPositiveActualPostDamage()
            throws IOException {
        String handler = source(
                "combat/skill/TemplarJudgementHandler.java"
        );
        String presentation = method(
                handler,
                "public static void emitAppliedShare("
        );

        assertTrue(presentation.contains("appliedDamage <= 0.0F"));
        assertTrue(presentation.contains("Math.round(appliedDamage)"));
        assertFalse(presentation.contains("Math.round(share)"));
        assertTrue(presentation.contains("new DamageNumberPayload("));
        assertTrue(presentation.contains("SHARE_DAMAGE_ID"));
        assertTrue(presentation.contains(
                "new TemplarJudgementImpactPayload(target.getId())"
        ));
    }

    private static void assertOrdered(String source, String... needles) {
        int previous = -1;
        for (String needle : needles) {
            int current = source.indexOf(needle, previous + 1);
            assertTrue(current > previous, "Missing or unordered: " + needle);
            previous = current;
        }
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method " + signature);
        int openingBrace = source.indexOf('{', start);
        assertTrue(openingBrace > start, "Missing body " + signature);

        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        throw new AssertionError("Unclosed method " + signature);
    }

    private static String source(String relativeFile) throws IOException {
        Path relative = Path.of(
                "src", "main", "java", "com", "stardew", "craft"
        ).resolve(relativeFile);
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}
