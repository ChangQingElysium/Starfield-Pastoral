package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WickedKrisPoisonAuthorityContractTest {
    @Test
    void entityNbtIsTheOnlyGameplayAuthority() throws IOException {
        String source = tracker();

        assertTrue(source.contains(
                "private static final String TAG_POISONS_V2"
        ));
        assertTrue(source.contains(
                "ListTag serialized = root.getList("
        ));
        assertTrue(source.contains(
                "root.put(TAG_POISONS_V2, serialized)"
        ));
        assertFalse(source.contains("DETONATIONS"));
        assertFalse(source.contains("ServerTickEvent"));
        assertFalse(source.contains("ConcurrentHashMap"));
        assertFalse(source.contains("ResourceKey<Level>"));
        assertFalse(source.contains("lastPos"));
    }

    @Test
    void ownerPoolsAreIsolatedAndReapplicationPreservesOnlyThatFuse()
            throws IOException {
        String source = tracker();
        String apply = method(
                source,
                "private static void applyPoisonInternal("
        );
        String removeOwner = method(
                source,
                "private static PoisonEntry removeOwner("
        );
        String encode = method(
                source,
                "private static CompoundTag encodeEntry("
        );
        String decode = method(
                source,
                "private static PoisonEntry decodeEntry("
        );

        assertTrue(apply.contains("readEntries(target)"));
        assertTrue(apply.contains(
                "removeOwner(entries, owner.getUUID())"
        ));
        assertTrue(removeOwner.contains(
                "entry.ownerId.equals(ownerId)"
        ));
        assertTrue(apply.contains(
                "previous == null ? null : previous.detonateTick"
        ));
        assertTrue(apply.contains(
                "previous == null ? null : previous.detonationSnapshot"
        ));
        assertTrue(apply.contains(
                "replacement.detonateTick = nowTick + delay;"
        ));
        assertTrue(apply.contains(
                "replacement.detonationSnapshot = weaponSnapshot;"
        ));
        assertTrue(apply.contains("entries.add(replacement)"));
        assertFalse(apply.contains("entries.clear()"));
        assertTrue(encode.contains(
                "tag.putUUID(ENTRY_OWNER, entry.ownerId)"
        ));
        assertTrue(decode.contains("tag.hasUUID(ENTRY_OWNER)"));
        assertTrue(decode.contains("tag.getUUID(ENTRY_OWNER)"));
    }

    @Test
    void dotAndDetonationSnapshotsPersistSeparatelyAndNeverUseCurrentHand()
            throws IOException {
        String source = tracker();
        String encode = method(source, "private static CompoundTag encodeEntry(");
        String decode = method(source, "private static PoisonEntry decodeEntry(");
        String dot = method(source, "private static void applyDotTick(");
        String detonate = method(source, "private static void detonate(");

        assertTrue(source.contains("ENTRY_DOT_WEAPON_ID"));
        assertTrue(source.contains("ENTRY_DOT_WEAPON"));
        assertTrue(source.contains("ENTRY_DETONATION_WEAPON_ID"));
        assertTrue(source.contains("ENTRY_DETONATION_WEAPON"));
        assertEquals(2, occurrences(encode, "writeSnapshot("));
        assertEquals(2, occurrences(decode, "readSnapshot("));
        assertTrue(dot.contains("entry.dotSnapshot"));
        assertFalse(dot.contains("entry.detonationSnapshot"));
        assertTrue(detonate.contains("entry.detonationSnapshot"));
        assertFalse(source.contains("getMainHandItem("));

        String legacy = method(
                source,
                "public static void applyPoison(\n"
                        + "            LivingEntity target,\n"
                        + "            ServerPlayer owner,\n"
                        + "            long nowTick,\n"
                        + "            int durationTicks,\n"
                        + "            int stacks,\n"
                        + "            boolean scheduleDetonation\n"
                        + "    )"
        );
        assertFalse(legacy.contains("WeaponSkillDamage.apply("));
        assertFalse(legacy.contains("applyPoisonInternal("));
    }

    @Test
    void legacyStateMigratesOnceWithoutInventingAReleaseWeapon()
            throws IOException {
        String source = tracker();
        String migration = method(source, "private static void migrateLegacy(");

        assertTrue(migration.contains(
                "root.contains(TAG_POISONS_V2, Tag.TAG_LIST)"
        ));
        assertTrue(migration.contains("isValidSnapshot(snapshot)"));
        assertTrue(migration.contains("writeEntries(target, List.of(entry))"));
        assertTrue(migration.contains("clearLegacy(root)"));
        assertFalse(migration.contains("getMainHandItem("));
    }

    @Test
    void tickConsumesOnlyTheResolvedOwnerEntryAndKeepsOfflineState()
            throws IOException {
        String source = tracker();
        String tick = method(
                source,
                "public static void onLivingTick("
        );
        String resolveOwner = method(
                source,
                "private static ServerPlayer resolveOwner("
        );
        String logout = method(
                source,
                "public static void removePlayer("
        );

        assertTrue(tick.contains("for (PoisonEntry entry : entries)"));
        assertTrue(tick.contains("List<PoisonEntry> survivors"));
        assertTrue(tick.contains("survivors.add(entry)"));
        assertTrue(tick.contains("writeEntries(target, survivors)"));
        assertTrue(tick.contains(
                "entry.detonateTick != null"
        ));
        assertTrue(tick.contains("owner != null"));
        assertTrue(resolveOwner.contains("getPlayer(ownerId)"));
        assertTrue(resolveOwner.contains("owner.level() == level"));
        assertFalse(logout.contains("writeEntries("));
        assertFalse(logout.contains("removeIf("));
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

    private static String tracker() throws IOException {
        Path relative = Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "combat", "skill", "WickedKrisPoisonTracker.java"
        );
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
