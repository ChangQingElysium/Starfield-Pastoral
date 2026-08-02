package com.stardew.craft.item.trinket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrinketEventBoundaryContractTest {
    @Test
    void canceledDeathsCannotAwardParrotCoins() throws IOException {
        String source = source("item/trinket/TrinketEffectHandler.java");
        String method = method(source, "public static void onMobKilled(");

        assertTrue(method.contains("receiveCanceled = true"));
        assertTrue(method.contains("event.isCanceled()"));
    }

    @Test
    void basiliskBlocksStatusDamageButNotDirectEnvironmentalOrMagicDamage()
            throws IOException {
        String source = source("item/trinket/TrinketEffectHandler.java");
        String method = method(
                source,
                "public static boolean cancelBasiliskDamage("
        );

        assertTrue(method.contains("DamageTypes.ON_FIRE"));
        assertTrue(method.contains("DamageTypes.FREEZE"));
        assertTrue(method.contains("DamageTypes.WITHER"));
        assertTrue(method.contains("Tags.DamageTypes.IS_POISON"));
        assertFalse(method.contains("DamageTypes.IN_FIRE"));
        assertFalse(method.contains("DamageTypes.LAVA"));
        assertFalse(method.contains("DamageTypes.HOT_FLOOR"));
        assertFalse(method.contains("DamageTypes.MAGIC"));
    }

    @Test
    void magicQuiverRestoresAuthoredDamageBeforeMitigation()
            throws IOException {
        String source = source("item/trinket/TrinketEffectHandler.java");
        String incoming = method(
                source,
                "public static void onMagicQuiverIncomingDamage("
        );
        String tick = method(source, "private static void tickMagicQuiver(");

        assertTrue(incoming.contains("LivingIncomingDamageEvent"));
        assertTrue(incoming.contains("event.setAmount("));
        assertTrue(tick.contains("arrow.setBaseDamage(1.0D)"));
        assertTrue(tick.contains("TAG_MAGIC_QUIVER_DAMAGE"));
        assertFalse(tick.contains("arrow.setBaseDamage(actualDamage)"));
    }

    @Test
    void frogConsumptionIntentionallyBypassesTheDeathRewardChain()
            throws IOException {
        String source = source("item/trinket/TrinketEffectHandler.java");
        String method = method(source, "private static void tickFrogEgg(");

        assertTrue(method.contains("Entity.RemovalReason.KILLED"));
        assertFalse(method.contains(".hurt("));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("Missing method: " + signature);
        }
        int annotation = source.lastIndexOf("\n    @SubscribeEvent", start);
        if (annotation >= 0 && start - annotation < 300) {
            start = annotation;
        }
        int next = source.indexOf("\n    private static", start + 1);
        if (next < 0) {
            next = source.length();
        }
        return source.substring(start, next);
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
