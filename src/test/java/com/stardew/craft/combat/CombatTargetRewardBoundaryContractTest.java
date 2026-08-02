package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTargetRewardBoundaryContractTest {
    @Test
    void allThreeCombatRewardEntrypointsUseTheSharedTargetRule()
            throws IOException {
        String common = method(
                source("combat/CommonWeaponAppliedHitRules.java"),
                "static void applyKillRewards("
        );
        String trinket = method(
                source("item/trinket/TrinketEffectHandler.java"),
                "public static void onDamageMonster("
        );
        String rings = method(
                source("combat/equipment/RingEffectHandler.java"),
                "public static void onMobKilled("
        );

        assertBefore(
                common,
                "CombatTargetRules.isCombatMonster(target)",
                "PlayerStardewDataAPI.addExperience("
        );
        assertBefore(
                trinket,
                "CombatTargetRules.isCombatMonster(target)",
                "recordFairyCombatDamage(player, stardewDamage)"
        );
        assertBefore(
                trinket,
                "CombatTargetRules.isCombatMonster(target)",
                "player.addEffect("
        );
        assertBefore(
                rings,
                "CombatTargetRules.isCombatMonster(killed)",
                "applyOnKillEffect(player, data, ring, killed)"
        );
    }

    @Test
    void rewardEntrypointsDoNotReimplementMonsterClassification()
            throws IOException {
        String common = method(
                source("combat/CommonWeaponAppliedHitRules.java"),
                "static void applyKillRewards("
        );
        String trinket = method(
                source("item/trinket/TrinketEffectHandler.java"),
                "public static void onDamageMonster("
        );
        String rings = method(
                source("combat/equipment/RingEffectHandler.java"),
                "public static void onMobKilled("
        );

        for (String rewardMethod : new String[] {common, trinket, rings}) {
            assertFalse(rewardMethod.contains("instanceof Enemy"));
            assertFalse(rewardMethod.contains("startsWith(\"sd_mob_\")"));
        }
    }

    private static void assertBefore(
            String source,
            String gate,
            String reward
    ) {
        int gateIndex = source.indexOf(gate);
        int rewardIndex = source.indexOf(reward);
        assertTrue(gateIndex >= 0, gate);
        assertTrue(rewardIndex > gateIndex, reward);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("Missing method: " + signature);
        }
        int next = source.indexOf("\n    static void ", start + 1);
        int publicNext = source.indexOf("\n    public static ", start + 1);
        int privateNext = source.indexOf("\n    private static ", start + 1);
        for (int candidate : new int[] {publicNext, privateNext}) {
            if (candidate >= 0 && (next < 0 || candidate < next)) {
                next = candidate;
            }
        }
        return source.substring(start, next < 0 ? source.length() : next);
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
