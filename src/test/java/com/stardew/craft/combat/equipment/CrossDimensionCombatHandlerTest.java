package com.stardew.craft.combat.equipment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossDimensionCombatHandlerTest {
    @Test
    void fairyBoxAccumulatesFinalDamageOnTheStardewHealthScale() {
        assertEquals(
                0,
                CrossDimensionCombatHandler.fairyDamageOnStardewScale(0.0F)
        );
        assertEquals(
                1,
                CrossDimensionCombatHandler.fairyDamageOnStardewScale(0.1F)
        );
        assertEquals(
                20,
                CrossDimensionCombatHandler.fairyDamageOnStardewScale(4.0F)
        );
        assertEquals(
                21,
                CrossDimensionCombatHandler.fairyDamageOnStardewScale(4.01F)
        );
    }

    @Test
    void reactiveWeaponSkillsUseFinalNativeDamageOneToOne() {
        assertEquals(0, CrossDimensionCombatHandler.reactiveSkillDamage(0.0F));
        assertEquals(1, CrossDimensionCombatHandler.reactiveSkillDamage(0.1F));
        assertEquals(4, CrossDimensionCombatHandler.reactiveSkillDamage(4.0F));
        assertEquals(5, CrossDimensionCombatHandler.reactiveSkillDamage(4.01F));
    }

    @Test
    void phoenixOnlyTriggersForDamageLethalAfterAbsorption() {
        assertFalse(CrossDimensionCombatHandler.isLethalAfterAbsorption(
                12.0F,
                10.0F,
                3.0F
        ));
        assertTrue(CrossDimensionCombatHandler.isLethalAfterAbsorption(
                12.0F,
                10.0F,
                2.0F
        ));
        assertTrue(CrossDimensionCombatHandler.isLethalAfterAbsorption(
                12.0F,
                10.0F,
                0.0F
        ));
        assertFalse(CrossDimensionCombatHandler.isLethalAfterAbsorption(
                0.0F,
                10.0F,
                0.0F
        ));
    }

    @Test
    void thornsUsesTheDamageThatActuallyEnteredNativeProtection() {
        assertEquals(
                20.0F,
                CrossDimensionCombatHandler.damageEnteringNativeProtection(
                        8.0F,
                        6.0F,
                        4.0F,
                        2.0F
                )
        );
        assertEquals(
                8.0F,
                CrossDimensionCombatHandler.damageEnteringNativeProtection(
                        8.0F,
                        -2.0F,
                        0.0F,
                        0.0F
                )
        );
    }

    @Test
    void crossDimensionHealthAndLuckUseTheirAuthoritativeEntrypoints()
            throws IOException {
        String ring = source(
                "combat/equipment/RingEffectHandler.java"
        );
        String attributes = source(
                "combat/equipment/EquipmentPlayerAttributes.java"
        );
        String crossDimension = source(
                "combat/equipment/CrossDimensionCombatHandler.java"
        );
        String playerEvents = source(
                "player/PlayerDataEventHandler.java"
        );

        assertTrue(ring.contains("CombatHealing.heal(player, 2.0F)"));
        assertFalse(ring.contains("data.setHealth(newHealth)"));
        assertTrue(attributes.contains(
                "PlayerStardewDataAPI.getDailyLuck(player)"
        ));
        assertTrue(attributes.contains(
                "CrossDimensionAttributeRules.minecraftMaximumHealthBonus("
        ));
        assertTrue(crossDimension.contains(
                "fairyDamageOnStardewScale(event.getNewDamage())"
        ));
        assertTrue(crossDimension.contains(
                "reactiveSkillDamage(event.getNewDamage())"
        ));
        assertTrue(crossDimension.contains(
                "SteelSpineFurySkillHandler.onDamageTaken("
        ));
        assertTrue(crossDimension.contains(
                "DwarfFortressSkillHandler.onDamageTaken(player, nowTick)"
        ));
        assertTrue(crossDimension.contains(
                "damageEnteringNativeProtection("
        ));
        assertFalse(crossDimension.contains("event.getOriginalDamage()"));
        int outerDimension = playerEvents.indexOf(
                "player.level().dimension() != ModDimensions.STARDEW_VALLEY"
        );
        int syncAttributes = playerEvents.indexOf(
                ".EquipmentPlayerAttributes\n                    .sync(player);",
                outerDimension
        );
        int blockIncoming = playerEvents.indexOf(
                ".tryBlockIncoming(player, event)",
                syncAttributes
        );
        assertTrue(outerDimension >= 0);
        assertTrue(syncAttributes > outerDimension);
        assertTrue(blockIncoming > syncAttributes);
        assertFalse(playerEvents.contains(
                "Math.ceil(amount * "
                        + "com.stardew.craft.combat.DimensionDamageMapper"
                        + ".getHealthRatio())"
        ));
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
