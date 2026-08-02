package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeProtectionMappingTest {
    @Test
    void externalWeaponRollEntersNativeProtectionAtIncoming()
            throws IOException {
        String events = readEvents();
        String incoming = method(
                events,
                "public static void onLivingIncomingDamage(",
                "public static void onLivingIncomingDamageFinal("
        );
        String pre = method(
                events,
                "public static void onLivingHurt(LivingDamageEvent.Pre event)",
                "public static CustomHealthWeaponResolution "
                        + "evaluateCustomHealthWeaponHit("
        );
        String assembly = readAssemblyRules();

        assertOrdered(
                incoming,
                "if (DimensionDamageMapper.isInStardewDimension(target)) "
                        + "return;",
                "IncomingWeaponResolution resolution = evaluateWeaponHit(",
                "event.setAmount(resolution.authoritativeDamage());",
                "WeaponIncomingHitStore.bind(",
                "event.getContainer(),"
        );
        assertOrdered(
                pre,
                "} else {",
                "hit = WeaponIncomingHitStore.consume(",
                "event.getContainer(),",
                "hit.preparationReservation().commit();",
                "WeaponEvaluatedHitCoordinator.apply(hit);"
        );
        assertTrue(assembly.contains("if (!inStardewDimension) {"));
        assertTrue(assembly.contains("request.defense(0.0F, false);"));
        assertFalse(assembly.contains("minecraft_native_protection"));
        assertFalse(assembly.contains("originalNativeDamage"));
        assertFalse(assembly.contains("damageAfterNativeProtection"));
        assertFalse(events.contains("nativeProtectionRatio("));
        assertFalse(events.contains("event.getOriginalDamage()"));
    }

    @Test
    void incomingRollIsStagedByExactDamageContainerIdentity()
            throws IOException {
        String events = readEvents();
        String store = readCombatSource("WeaponIncomingHitStore.java");

        assertTrue(store.contains(
                "IdentityHashMap<DamageContainer, BoundHit> ACTIVE"
        ));
        assertTrue(store.contains("ACTIVE.put("));
        assertTrue(store.contains("ACTIVE.remove(container)"));
        assertTrue(store.contains(
                "bound.hit().preparationReservation().release();"
        ));
        assertOrdered(
                events,
                "public static void onLivingIncomingDamageFinal(",
                "if (event.isCanceled()) {",
                "WeaponIncomingHitStore.discard(event.getContainer());"
        );
    }

    private static String readEvents() throws IOException {
        Path relative = Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "combat", "WeaponCombatEvents.java"
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

    private static String readAssemblyRules() throws IOException {
        return readCombatSource("WeaponDamageAssemblyRules.java");
    }

    private static String readCombatSource(String fileName)
            throws IOException {
        Path relative = Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "combat", fileName
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

    private static String method(
            String source,
            String startToken,
            String endToken
    ) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue(start >= 0 && end > start, startToken);
        return source.substring(start, end);
    }

    private static void assertOrdered(String source, String... needles) {
        int previous = -1;
        for (String needle : needles) {
            int current = source.indexOf(needle, previous + 1);
            assertTrue(current > previous, "Missing or unordered: " + needle);
            previous = current;
        }
    }
}
