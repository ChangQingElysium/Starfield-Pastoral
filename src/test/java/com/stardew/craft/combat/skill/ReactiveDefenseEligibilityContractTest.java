package com.stardew.craft.combat.skill;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveDefenseEligibilityContractTest {
    @Test
    void customHealthRejectionGatesPrecedeCounterConsumption()
            throws Exception {
        String source = normalize(playerDamageHandler());
        int slime = source.indexOf("eqStats.hasSlimeCharmer()");
        int yoba = source.indexOf("eqStats.hasYobaProtection()");
        int vow = source.indexOf("TemplarVowHandler.onPlayerHurt(event)", yoba);
        int light = source.indexOf(
                "LightCounterParryHandler .onPlayerHurt(event)", vow
        );
        int evaluation = source.indexOf(
                "WeaponCombatEvents.evaluateCustomHealthWeaponHit(",
                light
        );

        assertTrue(slime >= 0 && yoba > slime);
        assertTrue(vow > yoba && light > vow);
        assertTrue(evaluation > light);
    }

    @Test
    void reactiveHandlersAreOwnedByThePlayerDamageAuthority()
            throws Exception {
        String vow = source("combat/skill/TemplarVowHandler.java");
        String light = source("combat/skill/LightCounterParryHandler.java");
        assertFalse(vow.contains("@SubscribeEvent"));
        assertFalse(light.contains("@SubscribeEvent"));
        assertTrue(normalize(playerDamageHandler()).contains(
                "CrossDimensionCombatHandler .tryBlockIncoming(player, event)"
        ));
    }

    private static String playerDamageHandler() throws Exception {
        return source("player/PlayerDataEventHandler.java");
    }

    private static String source(String relative) throws Exception {
        Path project = Path.of(System.getProperty(
                "stardewcraft.projectDir", "."
        ));
        return Files.readString(project.resolve(Path.of(
                "src", "main", "java", "com", "stardew", "craft"
        ).resolve(relative)));
    }

    private static String normalize(String source) {
        return source.replaceAll("\\s+", " ");
    }
}
