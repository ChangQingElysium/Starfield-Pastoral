package com.stardew.craft.entity.bomb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewBombOwnerPersistenceTest {
    @Test
    void ownerUuidSurvivesTheBombSaveRoundTrip() {
        UUID ownerUuid = UUID.fromString(
                "5f491942-2cf8-4e8f-bcc8-a5a0d5af838e"
        );
        CompoundTag saved = new CompoundTag();

        StardewBombEntity.writeOwnerUuid(saved, ownerUuid);

        assertTrue(saved.hasUUID("Owner"));
        assertEquals(
                ownerUuid,
                StardewBombEntity.readOwnerUuid(saved)
        );
    }

    @Test
    void missingOwnerRemainsASafeOwnerlessBomb() {
        CompoundTag saved = new CompoundTag();

        StardewBombEntity.writeOwnerUuid(saved, null);

        assertFalse(saved.contains("Owner"));
        assertNull(StardewBombEntity.readOwnerUuid(saved));
    }

    @Test
    void reloadResolutionPrefersPlayersThenCurrentLevelLivingEntities()
            throws IOException {
        String bomb = source(Path.of(
                "entity", "bomb", "StardewBombEntity.java"
        ));
        String resolution = method(bomb, "public LivingEntity getOwner()");

        assertOrdered(
                resolution,
                "getPlayerList()",
                "getPlayer(this.ownerUuid)",
                "if (player != null)",
                "serverLevel.getEntity(this.ownerUuid)",
                "entity instanceof LivingEntity livingOwner",
                "this.owner = null;",
                "return null;"
        );
    }

    @Test
    void oneResolvedOwnerDrivesBlocksExplosionAndDamageAttribution()
            throws IOException {
        String bomb = source(Path.of(
                "entity", "bomb", "StardewBombEntity.java"
        ));
        String explode = method(bomb, "private void explode()");
        String damage = method(
                bomb,
                "private void damageEntitiesInRadius("
        );

        assertOrdered(
                explode,
                "LivingEntity resolvedOwner = getOwner();",
                "destroyBlocksInCircle(",
                "resolvedOwner",
                "damageEntitiesInRadius(serverLevel, type, resolvedOwner);"
        );
        String blocks = method(
                bomb,
                "private void destroyBlocksInCircle("
        );
        assertTrue(blocks.contains("resolvedOwner"));
        assertFalse(blocks.contains("if (owner instanceof"));
        assertTrue(damage.contains(
                "level.damageSources().explosion(\n"
                        + "                    this,\n"
                        + "                    resolvedOwner"
        ));
        assertFalse(damage.contains("explosion(this, owner)"));
    }

    @Test
    void attributedBombSelfDamageKeepsDirectEntitySourceKind()
            throws IOException {
        String playerEvents = source(Path.of(
                "player", "PlayerDataEventHandler.java"
        ));
        int source = playerEvents.indexOf(
                "net.minecraft.world.entity.Entity dmgSourceEntity = "
                        + "event.getSource().getEntity();"
        );
        int end = playerEvents.indexOf(
                "float authoritativeMonsterDamage",
                source
        );
        String mapping = playerEvents.substring(source, end);

        assertOrdered(
                mapping,
                "dmgSourceEntity instanceof net.minecraft.world.entity.Mob",
                "DamageRequest.SourceKind.MONSTER_ATTACK",
                "dmgSourceEntity != null",
                "DamageRequest.SourceKind.DIRECT_ENTITY",
                "DamageRequest.SourceKind.ENVIRONMENT"
        );
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature);
        int openingBrace = source.indexOf('{', start);
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

    private static void assertOrdered(String source, String... needles) {
        int previous = -1;
        for (String needle : needles) {
            int current = source.indexOf(needle, previous + 1);
            assertTrue(current > previous, "Missing or unordered: " + needle);
            previous = current;
        }
    }

    private static String source(Path relative) throws IOException {
        Path sourceRoot = Path.of(
                "src", "main", "java", "com", "stardew", "craft"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(sourceRoot).resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}
