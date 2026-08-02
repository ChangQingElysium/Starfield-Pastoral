package com.stardew.craft.combat.equipment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossDimensionBurglarRingContractTest {
    @Test
    void ordinaryMinecraftMonstersRerollTheirOwnLootTable() throws IOException {
        String source = source("event/MineMonsterDropHandler.java");

        assertTrue(source.contains("if (!stardewMonster)"));
        assertTrue(source.contains("addExternalBurglarReroll(event, serverLevel)"));
        assertTrue(source.contains(".hasBurglar(player)"));
        assertTrue(source.contains("getLootTable(entity.getLootTable())"));
        assertTrue(source.contains("LootContextParams.LAST_DAMAGE_PLAYER"));
        assertTrue(source.contains("getRandomItems(params)"));
        assertFalse(source.contains("getRandomItems(params, entity.getLootTableSeed())"));
        assertTrue(source.contains("addDrop(event.getDrops(), entity, stack)"));
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
