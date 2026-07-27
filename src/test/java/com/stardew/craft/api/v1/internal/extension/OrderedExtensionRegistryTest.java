package com.stardew.craft.api.v1.internal.extension;

import com.stardew.craft.api.v1.extension.StardewExtensionRegistration;
import com.stardew.craft.api.v1.extension.StardewExtensionIssue;
import com.stardew.craft.api.v1.extension.StardewExtensionLifecycle;
import com.stardew.craft.api.v1.extension.StardewExtensions;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderedExtensionRegistryTest {
    @Test
    void ordersByPriorityThenFullIdAndPublishesRevisionedSnapshots() {
        OrderedExtensionRegistry<String> registry =
                new OrderedExtensionRegistry<>(id("test/ordered"));
        var empty = registry.snapshot();

        registry.register(id("z"), 10, "z");
        registry.register(
                ResourceLocation.fromNamespaceAndPath("another", "a"),
                10,
                "a");
        registry.register(id("highest"), 20, "highest");

        assertEquals(0L, empty.revision());
        assertEquals(List.of(), empty.entries());
        assertEquals(3L, registry.snapshot().revision());
        assertEquals(
                List.of("highest", "a", "z"),
                registry.entries().stream()
                        .map(OrderedExtensionRegistry.Entry::extension)
                        .toList());
        assertEquals(
                List.of(id("highest"),
                        ResourceLocation.fromNamespaceAndPath(
                                "another", "a"),
                        id("z")),
                StardewExtensions.find(id("test/ordered"))
                        .orElseThrow()
                        .registrations().stream()
                        .map(registration -> registration.id())
                        .toList());
    }

    @Test
    void rejectsDuplicateIdsWithoutChangingThePublishedSnapshot() {
        OrderedExtensionRegistry<String> registry =
                new OrderedExtensionRegistry<>(id("test/duplicates"));
        registry.register(id("entry"), 1, "first");
        var before = registry.snapshot();

        assertThrows(IllegalStateException.class,
                () -> registry.register(id("entry"), 2, "second"));
        assertEquals(before.entries(), registry.snapshot().entries());
        assertEquals(
                StardewExtensionIssue.Kind.DUPLICATE_ID,
                registry.diagnosticSnapshot()
                        .issues()
                        .getFirst()
                        .kind());
    }

    @Test
    void freezesRegistrationsAndExplainsLateAttempts() {
        OrderedExtensionRegistry<String> registry =
                new OrderedExtensionRegistry<>(id("test/freeze"));
        registry.register(id("first"), 1, "first");

        registry.freeze();

        assertEquals(
                StardewExtensionLifecycle.FROZEN,
                registry.diagnosticSnapshot().lifecycle());
        assertThrows(
                IllegalStateException.class,
                () -> registry.register(id("late"), 2, "late"));
        assertEquals(List.of("first"), registry.entries().stream()
                .map(OrderedExtensionRegistry.Entry::extension)
                .toList());
        assertEquals(
                StardewExtensionIssue.Kind.LATE_REGISTRATION,
                registry.diagnosticSnapshot()
                        .issues()
                        .getFirst()
                        .kind());
    }

    @Test
    void invocationMetricsRecordSuccessFailureAndLastFailure() {
        OrderedExtensionRegistry<Supplier<String>> registry =
                new OrderedExtensionRegistry<>(id("test/metrics"));
        registry.register(id("provider"), 10, () -> "ok");
        var entry = registry.entries().getFirst();

        assertEquals("ok", registry.invoke(entry, Supplier::get));
        assertThrows(
                IllegalStateException.class,
                () -> registry.invoke(
                        entry,
                        ignored -> {
                            throw new IllegalStateException("expected");
                        }));

        StardewExtensionRegistration snapshot =
                registry.diagnosticSnapshot().registrations().getFirst();
        assertEquals(2L, snapshot.invocationCount());
        assertEquals(1L, snapshot.failureCount());
        assertTrue(snapshot.totalNanos() >= snapshot.maxNanos());
        assertEquals(
                IllegalStateException.class.getName(),
                snapshot.lastFailure().orElseThrow().exceptionType());
        assertEquals(
                "expected",
                snapshot.lastFailure().orElseThrow().message());
    }

    @Test
    void legacyDiagnosticConstructorStartsWithEmptyMetrics() {
        StardewExtensionRegistration registration =
                new StardewExtensionRegistration(id("legacy"), 5);

        assertEquals(0L, registration.invocationCount());
        assertEquals(0L, registration.failureCount());
        assertTrue(registration.lastFailure().isEmpty());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_gametest", path);
    }
}
