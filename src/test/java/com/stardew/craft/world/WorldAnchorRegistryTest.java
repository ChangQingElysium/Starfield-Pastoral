package com.stardew.craft.world;

import com.google.gson.JsonParser;
import com.stardew.craft.api.v1.festival.StardewFestivalMapOverlay;
import com.stardew.craft.api.v1.festival.StardewFestivalMapOverlays;
import com.stardew.craft.api.v1.world.StardewWorldAnchor;
import com.stardew.craft.api.v1.world.StardewWorldAnchors;
import com.stardew.craft.api.v1.world.StardewMapSlots;
import com.stardew.craft.api.v1.world.StardewMapSlotScopes;
import com.stardew.craft.festival.FestivalMapOverlayRegistry;
import com.stardew.craft.interior.InteriorPortalRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldAnchorRegistryTest {
    @Test
    void anchorsResolveFullAndLegacyIdsAndFilterByRole() {
        Map<ResourceLocation, StardewWorldAnchor> previous =
                new LinkedHashMap<>();
        StardewWorldAnchors.all().forEach(
                anchor -> previous.put(anchor.id(), anchor));
        ResourceLocation scheduleRole = id("npc_schedule");
        StardewWorldAnchor legacy = anchor(
                id("legacy_point"), new Vec3(1.5, 64, 2.5),
                Set.of(scheduleRole));
        StardewWorldAnchor addon = anchor(
                ResourceLocation.fromNamespaceAndPath(
                        "anchor_test", "orchard_stage"),
                new Vec3(8.5, 70, 9.5),
                Set.of(scheduleRole, id("festival_stage")));
        try {
            WorldAnchorRegistry.publish(Map.of(
                    addon.id(), addon,
                    legacy.id(), legacy));

            assertEquals(legacy,
                    StardewWorldAnchors.resolve("legacy_point").orElseThrow());
            assertEquals(addon,
                    StardewWorldAnchors.resolve(
                            "anchor_test:orchard_stage").orElseThrow());
            var unified = StardewMapSlots.resolveWorldAnchor(
                    "anchor_test:orchard_stage").orElseThrow();
            assertEquals(addon.id(), unified.id());
            assertEquals(addon.position(), unified.position());
            assertEquals(StardewMapSlotScopes.WORLD,
                    unified.scopeType());
            assertEquals("global", unified.scopeId());
            assertEquals(
                    java.util.List.of(addon.id(), legacy.id()),
                    StardewWorldAnchors.withRole(scheduleRole).stream()
                            .map(StardewWorldAnchor::id)
                            .toList());
            InteriorPortalRegistry.PortalTarget portal =
                    InteriorPortalRegistry.resolve(
                            "anchor_test:orchard_stage",
                            id("stardew_valley")).orElseThrow();
            assertEquals(addon.position().x, portal.x());
            assertEquals(addon.position().y, portal.y());
            assertEquals(addon.position().z, portal.z());
            assertEquals(addon.yaw(), portal.yaw());
            assertTrue(InteriorPortalRegistry.resolve(
                    "anchor_test:orchard_stage",
                    ResourceLocation.fromNamespaceAndPath(
                            "minecraft", "overworld")).isEmpty());

            ResourceLocation overlayId =
                    ResourceLocation.fromNamespaceAndPath(
                            "anchor_test", "orchard_festival");
            StardewFestivalMapOverlays.register(
                    new StardewFestivalMapOverlay(
                            overlayId,
                            ResourceLocation.fromNamespaceAndPath(
                                    "anchor_test", "orchard"),
                            addon.id(),
                            null,
                            ResourceLocation.fromNamespaceAndPath(
                                    "anchor_test",
                                    "festivals/orchard.schem"),
                            new BlockPos(-2, 0, -3),
                            new BlockPos(5, 4, 6),
                            java.util.List.of(new BlockPos(1, 0, 1)),
                            true,
                            true,
                            true,
                            StardewFestivalMapOverlay.TreeClearance.NONE));
            var resolvedOverlay = FestivalMapOverlayRegistry.get(
                    overlayId.toString()).orElseThrow();
            BlockPos overlayOrigin =
                    BlockPos.containing(addon.position());
            assertEquals(overlayOrigin, resolvedOverlay.origin());
            assertEquals(overlayOrigin.offset(-2, 0, -3),
                    resolvedOverlay.boundsMin());
            assertEquals(
                    "data/anchor_test/structures/festivals/orchard.schem",
                    resolvedOverlay.festivalSchematicPath());
            assertEquals(
                    java.util.List.of(overlayOrigin.offset(1, 0, 1)),
                    resolvedOverlay.safePositions());
        } finally {
            WorldAnchorRegistry.publish(previous);
        }
    }

    @Test
    void anchorsRejectNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class, () ->
                anchor(id("invalid"),
                        new Vec3(Double.NaN, 0, 0), Set.of()));
    }

    @Test
    void dataDecoderRejectsUnknownFieldsAndPreservesSharedRoles() {
        ResourceLocation anchorId =
                ResourceLocation.fromNamespaceAndPath(
                        "anchor_test", "stage");
        var decoded = WorldAnchorRegistry.decode(
                anchorId,
                JsonParser.parseString("""
                        {
                          "dimension": "stardewcraft:stardew_valley",
                          "position": [1.5, 64, 2.5],
                          "roles": [
                            "stardewcraft:npc",
                            "stardewcraft:festival"
                          ]
                        }
                        """));
        assertTrue(decoded.hasRole(
                com.stardew.craft.api.v1.world
                        .StardewMapSlotRoles.NPC));
        assertTrue(decoded.hasRole(
                com.stardew.craft.api.v1.world
                        .StardewMapSlotRoles.FESTIVAL));
        assertThrows(IllegalArgumentException.class,
                () -> WorldAnchorRegistry.decode(
                        anchorId,
                        JsonParser.parseString("""
                                {
                                  "position": [1, 2, 3],
                                  "postion": [1, 2, 3]
                                }
                                """)));
    }

    @Test
    void anchorIndexesPublishAsOneCoherentSnapshot() throws Exception {
        WorldAnchorRegistry.Catalog before =
                WorldAnchorRegistry.catalog();
        StardewWorldAnchor first = anchor(
                id("coherent_first"),
                new Vec3(1, 2, 3),
                Set.of(id("npc")));
        StardewWorldAnchor second = anchor(
                id("coherent_second"),
                new Vec3(4, 5, 6),
                Set.of(id("festival")));
        Thread writer = null;
        try {
            WorldAnchorRegistry.publish(Map.of(first.id(), first));
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            writer = new Thread(() -> {
                try {
                    for (int index = 0; index < 2_000; index++) {
                        StardewWorldAnchor value =
                                (index & 1) == 0 ? second : first;
                        WorldAnchorRegistry.publish(
                                Map.of(value.id(), value));
                    }
                } catch (Throwable throwable) {
                    failure.set(throwable);
                } finally {
                    running.set(false);
                }
            }, "world-anchor-catalog-writer");
            writer.start();

            while (running.get()) {
                assertCoherent(WorldAnchorRegistry.catalog());
            }
            writer.join();

            assertNull(failure.get());
            assertCoherent(WorldAnchorRegistry.catalog());
        } finally {
            if (writer != null) {
                writer.join();
            }
            WorldAnchorRegistry.publish(before.byId());
        }
    }

    private static void assertCoherent(
            WorldAnchorRegistry.Catalog catalog
    ) {
        assertTrue(catalog.revision() > 0);
        assertEquals(
                catalog.byId().keySet(),
                catalog.ordered().stream()
                        .map(StardewWorldAnchor::id)
                        .collect(java.util.stream.Collectors.toSet()));
        for (StardewWorldAnchor anchor : catalog.ordered()) {
            assertEquals(anchor,
                    catalog.byId().get(anchor.id()));
        }
    }

    private static StardewWorldAnchor anchor(
            ResourceLocation id,
            Vec3 position,
            Set<ResourceLocation> roles
    ) {
        return new StardewWorldAnchor(
                id,
                id("stardew_valley"),
                position,
                90.0F,
                false,
                true,
                null,
                roles);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "stardewcraft", path);
    }
}
