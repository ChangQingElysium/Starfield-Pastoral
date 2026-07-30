package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingContextCleanupContractTest {
    private static final List<String> CENTRALIZED_TRACKER_SOURCES = List.of(
            "combat/skill/BrokenTridentThrustTracker.java",
            "combat/skill/CarvingKnifeThrustTracker.java",
            "combat/skill/DwarfDaggerThrustTracker.java",
            "combat/skill/IridiumNeedleThrustTracker.java",
            "combat/skill/GalaxyDaggerThrustTracker.java",
            "combat/skill/InfinityDaggerThrustTracker.java",
            "combat/skill/ClaymoreFoldbackTracker.java",
            "combat/skill/ObsidianCrackTracker.java",
            "combat/skill/DarkSwordBloodMoonTracker.java",
            "combat/skill/DwarfFortressTracker.java",
            "combat/skill/HolyBladeSanctuaryTracker.java",
            "combat/skill/LavaKatanaMarkTracker.java",
            "combat/skill/LavaKatanaReverbTracker.java",
            "combat/skill/ObsidianResonanceTracker.java",
            "combat/skill/OssifiedExecutionTracker.java",
            "combat/skill/SteelFalchionLineTracker.java",
            "combat/skill/TemperedFireRingTracker.java",
            "combat/skill/TemplarJudgementTracker.java",
            "combat/skill/TemplarVowHandler.java",
            "combat/skill/TemplarVowTracker.java",
            "combat/skill/WickedKrisPoisonTracker.java",
            "entity/effect/IceSpineEffectEntity.java",
            "entity/projectile/ElfBladeLeafEntity.java",
            "entity/projectile/TemperedBilletProjectileEntity.java",
            "entity/projectile/TideAnchorProjectileEntity.java"
    );

    @Test
    void migratedTrackersDelegateBindingAndCleanupToWeaponSkillDamage()
            throws IOException {
        Path sourceRoot = findJavaSourceRoot();
        for (String relative : CENTRALIZED_TRACKER_SOURCES) {
            String source = Files.readString(sourceRoot.resolve(relative));
            assertTrue(
                    source.contains("WeaponSkillDamage.apply("),
                    relative + " must use the centralized damage entry"
            );
            assertFalse(
                    source.contains("WeaponSkillContextStore.setPending("),
                    relative + " must not bind pending context locally"
            );
            assertFalse(
                    source.matches(
                            "(?s).*\\.hurt\\(.{0,240}?"
                                    + "1\\.0F\\s*\\).*"
                    ),
                    relative + " must not retain a raw sentinel hit"
            );
        }
    }

    @Test
    void steelFalchionDotAndBurstKeepTheirReleaseSnapshotOwnership()
            throws IOException {
        Path source = findJavaSourceRoot().resolve(
                "combat/skill/SteelFalchionLineTracker.java"
        );
        String contents = Files.readString(source);
        assertTrue(
                contents.contains("dot.weaponSnapshot"),
                source.toString()
        );
        assertTrue(
                contents.contains("line.weaponSnapshot"),
                source.toString()
        );
        assertTrue(
                occurrences(
                        contents,
                        "WeaponSkillDamage.apply("
                ) >= 2,
                source.toString()
        );
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            count++;
            index = source.indexOf(token, index + token.length());
        }
        return count;
    }

    private static Path findJavaSourceRoot() throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException(
                "Cannot locate Java sources from "
                        + Path.of("").toAbsolutePath()
        );
    }
}
