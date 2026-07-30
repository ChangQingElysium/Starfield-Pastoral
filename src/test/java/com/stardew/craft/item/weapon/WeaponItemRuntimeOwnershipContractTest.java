package com.stardew.craft.item.weapon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponItemRuntimeOwnershipContractTest {
    private static final List<String> ITEM_SOURCES = List.of(
            "StardewWeaponItem.java",
            "StardewDaggerItem.java",
            "StardewClubItem.java"
    );
    private static final List<String> FORBIDDEN_EXECUTION_TOKENS = List.of(
            "WeaponSkillData",
            "WeaponSkillCooldown",
            "SkillContext",
            "WeaponSkillAnimation",
            "PacketDistributor",
            "SkillEffectsClient",
            "player.attack(",
            "findTarget",
            "findTargets",
            "Tracker"
    );
    private static final Pattern USE_SKILL_METHOD = Pattern.compile(
            "public\\s+InteractionResultHolder<ItemStack>\\s+useSkill"
                    + "\\s*\\([^)]*\\)\\s*\\{(?<body>.*?)\\n\\s*}",
            Pattern.DOTALL
    );

    @Test
    void itemClassesDelegateAllSkillExecutionToRuntime() throws IOException {
        Path sourceRoot = findWeaponSourceRoot();
        for (String sourceName : ITEM_SOURCES) {
            String source = Files.readString(sourceRoot.resolve(sourceName));
            Matcher matcher = USE_SKILL_METHOD.matcher(source);

            assertTrue(matcher.find(), () ->
                    sourceName + " must retain the dispatcher fallback method");
            String body = matcher.group("body");
            assertTrue(body.contains(
                    "ItemStack stack = player.getItemInHand(hand);"
            ), sourceName);
            assertTrue(body.contains(
                    "return InteractionResultHolder.pass(stack);"
            ), sourceName);
            assertFalse(matcher.find(), () ->
                    sourceName + " declares more than one useSkill method");

            for (String forbidden : FORBIDDEN_EXECUTION_TOKENS) {
                assertFalse(source.contains(forbidden), () ->
                        sourceName + " still owns skill execution token "
                                + forbidden);
            }
        }
    }

    @Test
    void cleanupPreservesItemPresentationAndClubChargeContracts()
            throws IOException {
        Path sourceRoot = findWeaponSourceRoot();
        String sword = Files.readString(
                sourceRoot.resolve("StardewWeaponItem.java")
        );
        String dagger = Files.readString(
                sourceRoot.resolve("StardewDaggerItem.java")
        );
        String club = Files.readString(
                sourceRoot.resolve("StardewClubItem.java")
        );

        for (String source : List.of(sword, dagger, club)) {
            assertTrue(source.contains(
                    "WeaponItemSupport.createAttributeModifiers"
            ));
            assertTrue(source.contains("WeaponTooltipBuilder"));
        }
        assertTrue(sword.contains("class StardewWeaponTier implements Tier"));
        assertTrue(sword.contains("super(createTier(weaponId), properties)"));
        assertTrue(club.contains("private static final int CHARGE_TICKS = 20"));
        assertTrue(club.contains("return CHARGE_TICKS;"));
        assertTrue(club.contains("return UseAnim.BOW;"));
    }

    private static Path findWeaponSourceRoot() throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "item",
                "weapon"
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
                "Cannot locate weapon sources from "
                        + Path.of("").toAbsolutePath()
        );
    }
}
