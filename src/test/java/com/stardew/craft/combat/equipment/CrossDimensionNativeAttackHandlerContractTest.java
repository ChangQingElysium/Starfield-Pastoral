package com.stardew.craft.combat.equipment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CrossDimensionNativeAttackHandlerContractTest {
    @Test
    void nativeProjectionExcludesTheStardewDamagePipeline()
            throws IOException {
        String handler = source(
                "combat/equipment/CrossDimensionNativeAttackHandler.java"
        );
        String attributes = source(
                "combat/equipment/EquipmentPlayerAttributes.java"
        );
        String normalizedAttributes = attributes.replaceAll("\\s+", " ");

        assertTrue(handler.contains("CriticalHitEvent"));
        assertTrue(handler.contains(
                "DimensionDamageMapper.isInStardewDimension(player)"
        ));
        assertTrue(handler.contains(
                "WeaponCombatIdentity.isWeapon(weapon)"
        ));
        assertTrue(handler.contains(
                "CrossDimensionAttributeRules.minecraftCriticalChance("
        ));
        assertTrue(handler.contains(
                "CrossDimensionAttributeRules.minecraftCriticalMultiplier("
        ));
        assertTrue(handler.contains(
                "playerData.hasProfession(ProfessionType.SCOUT)"
        ));
        assertTrue(handler.contains(
                "playerData.hasProfession(ProfessionType.DESPERADO)"
        ));
        assertTrue(handler.contains(
                "player.hasEffect(ModMobEffects.STATUE_OF_BLESSINGS_5)"
        ));
        assertTrue(handler.contains("LivingDamageEvent.Post"));
        assertTrue(handler.contains("DamageTypes.PLAYER_ATTACK"));
        assertTrue(handler.contains(
                "@SubscribeEvent(priority = EventPriority.HIGHEST)\n"
                        + "    public static void onDamagePre("
        ));
        assertTrue(handler.contains(
                "WeaponSkillContextStore.hasPending(player, nowTick)"
        ));
        assertTrue(handler.contains("NATIVE_HITS.bind("));
        assertTrue(handler.contains("NATIVE_HITS.consume("));
        assertTrue(handler.contains(
                "TrinketEffectHandler.onDamageMonster("
        ));
        assertTrue(handler.contains("consumeCritical("));
        assertTrue(attributes.contains("Attributes.ATTACK_KNOCKBACK"));
        assertTrue(attributes.contains(
                "WeaponCombatIdentity.isWeapon("
        ));

        int postStart = handler.indexOf(
                "public static void onDamagePost(LivingDamageEvent.Post event)"
        );
        int postEnd = handler.indexOf(
                "static boolean shouldBindNativeHit(",
                postStart
        );
        String post = handler.substring(postStart, postEnd);
        assertFalse(post.contains("getMainHandItem()"));
        assertTrue(post.indexOf("NATIVE_HITS.consume(")
                < post.indexOf("TrinketEffectHandler.onDamageMonster("));

        int attackStart = handler.indexOf(
                "public static void onAttackEntity(AttackEntityEvent event)"
        );
        int attackEnd = handler.indexOf(
                "public static void onCriticalHit(CriticalHitEvent event)",
                attackStart
        );
        String attack = handler.substring(attackStart, attackEnd);
        assertTrue(attack.contains("clearCritical(player.getUUID())"));
        assertFalse(attack.contains("clear(player.getUUID())"));

        int clearStart = handler.indexOf(
                "public static void clear(UUID playerId)"
        );
        int clearEnd = handler.indexOf(
                "private static void clearCritical(UUID playerId)",
                clearStart
        );
        String cleanup = handler.substring(clearStart, clearEnd);
        assertTrue(cleanup.contains("clearCritical(playerId)"));
        assertTrue(cleanup.contains("NATIVE_HITS.clear(playerId)"));

        int nativeGate = attributes.indexOf(
                "boolean usesNativeAttack = !WeaponCombatIdentity.isWeapon("
        );
        int attackFlat = attributes.indexOf("ATTACK_FLAT_ID", nativeGate);
        int attackMultiplier = attributes.indexOf(
                "ATTACK_MULTIPLIER_ID",
                attackFlat
        );
        int knockback = attributes.indexOf(
                "ATTACK_KNOCKBACK_ID",
                attackMultiplier
        );
        assertTrue(nativeGate >= 0);
        assertTrue(attackFlat > nativeGate);
        assertTrue(attackMultiplier > attackFlat);
        assertTrue(knockback > attackMultiplier);

        String nativeProjection = normalizedAttributes.substring(
                normalizedAttributes.indexOf(
                        "boolean usesNativeAttack = "
                ),
                normalizedAttributes.indexOf(
                        "ATTACK_KNOCKBACK_ID",
                        normalizedAttributes.indexOf(
                                "boolean usesNativeAttack = "
                        )
                )
        );
        String rawNativeProjection = attributes.substring(
                nativeGate,
                knockback
        );
        assertTrue(nativeProjection.contains(
                "usesNativeAttack "
                        + "? CrossDimensionAttributeRules.minecraftAttackDamage("
        ));
        assertTrue(nativeProjection.contains(
                "usesNativeAttack ? CrossDimensionAttributeRules "
                        + ".minecraftAttackMultiplier("
        ));
        assertTrue(nativeProjection.contains("ProfessionType.FIGHTER"));
        assertTrue(nativeProjection.contains("ProfessionType.BRUTE"));
        assertFalse(rawNativeProjection.contains(
                "ATTACK_FLAT_ID,\n                "
                        + "CrossDimensionAttributeRules.minecraftAttackDamage("
        ));
    }

    @Test
    void canceledDeathsCannotAwardRingEffects() throws IOException {
        String rings = source(
                "combat/equipment/RingEffectHandler.java"
        );

        assertTrue(rings.contains("receiveCanceled = true"));
        assertTrue(rings.contains("event.isCanceled()"));
    }

    private static String source(String relativeSource) throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        ).resolve(relativeSource);
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
