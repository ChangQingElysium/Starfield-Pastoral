package com.stardew.craft.farm;

import com.stardew.craft.api.v1.farm.StardewFarmLayout;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutConfigField;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutAttachment;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutAttachmentKeys;
import com.stardew.craft.api.v1.farm.StardewFarmLayouts;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.api.v1.internal.farm.StardewFarmSnapshots;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FarmLayoutExtensionTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void builtinLayoutsRetainLegacyOrderAndSelectionState() {
        List<StardewFarmLayout> builtins = StardewFarmLayouts.all().stream()
                .filter(layout -> layout.id().getNamespace().equals("stardewcraft"))
                .toList();

        assertEquals(
                List.of(FarmType.values()).stream()
                        .map(StardewFarmLayoutRegistry::builtinId)
                        .toList(),
                builtins.stream().map(StardewFarmLayout::id).toList());
        assertEquals(
                List.of(FarmType.STANDARD, FarmType.FOREST).stream()
                        .map(StardewFarmLayoutRegistry::builtinId)
                        .toList(),
                StardewFarmLayouts.selectable().stream()
                        .filter(layout -> layout.id().getNamespace()
                                .equals("stardewcraft"))
                        .map(StardewFarmLayout::id)
                        .toList());
    }

    @Test
    void addonLayoutFlowsThroughCreationPersistenceAndTransfer() {
        ResourceLocation id = id("layout_" + IDS.incrementAndGet());
        StardewFarmLayout layout = testLayout(id);
        StardewFarmLayouts.register(layout);

        FarmInstanceRegistry registry = new FarmInstanceRegistry();
        UUID owner = UUID.randomUUID();
        UUID nextOwner = UUID.randomUUID();
        FarmInstance created = registry.createFarmAtDate(
                owner, "Owner", "Addon Farm", id, 17, 1);

        assertEquals(id, created.getFarmLayoutId());
        assertEquals(layout.originY(), created.getOrigin().getY());
        assertEquals(created.getOrigin().offset(layout.spawnOffset()),
                created.getSpawnPoint());
        assertEquals(layout.boundsMax(),
                created.getFarmBoundsMax().subtract(created.getOrigin()));

        FarmInstance loaded = FarmInstance.load(created.save());
        assertEquals(id, loaded.getFarmLayoutId());
        assertEquals(layout.schematic(), loaded.getFarmLayout().schematic());
        assertEquals(layout.entryWest(), loaded.getFarmLayout().entryWest());

        assertEquals(true, registry.transferFarm(
                owner, nextOwner, "Next Owner"));
        FarmInstance transferred = registry.getFarm(nextOwner);
        assertNotNull(transferred);
        assertEquals(id, transferred.getFarmLayoutId());
        assertEquals(layout.boundsMax(),
                transferred.getFarmLayout().boundsMax());
    }

    @Test
    void savedGeometrySurvivesMissingAddonRegistration() {
        ResourceLocation registeredId =
                id("snapshot_source_" + IDS.incrementAndGet());
        StardewFarmLayout layout = testLayout(registeredId);
        StardewFarmLayouts.register(layout);
        FarmInstance source = new FarmInstance(
                UUID.randomUUID(), "Owner", "Snapshot Farm", 4,
                FarmInstanceAllocator.getFarmOrigin(4, layout),
                registeredId);
        CompoundTag saved = source.save();
        ResourceLocation missingId =
                id("missing_" + IDS.incrementAndGet());
        saved.putString("FarmLayoutId", missingId.toString());

        FarmInstance loaded = FarmInstance.load(saved);

        assertEquals(missingId, loaded.getFarmLayoutId());
        assertEquals(layout.width(), loaded.getFarmLayout().width());
        assertEquals(layout.spawnOffset(),
                loaded.getFarmLayout().spawnOffset());
        assertEquals(layout.schematic(),
                loaded.getFarmLayout().schematic());
        assertEquals(loaded.getSpawnPoint(),
                loaded.findFarmLayoutAttachment(
                                StardewFarmLayoutAttachmentKeys.SPAWN)
                        .orElseThrow().resolve(loaded.getOrigin()));
    }

    @Test
    void duplicateLayoutIdsAreRejected() {
        ResourceLocation id = id("duplicate_" + IDS.incrementAndGet());
        StardewFarmLayouts.register(testLayout(id));
        assertThrows(IllegalStateException.class,
                () -> StardewFarmLayouts.register(testLayout(id)));
    }

    @Test
    void typedConfigurationIsValidatedPersistedAndTransferred() {
        ResourceLocation id = id("configured_" + IDS.incrementAndGet());
        ResourceLocation rain = id("rain");
        ResourceLocation cabins = id("cabins");
        ResourceLocation theme = id("theme");
        ResourceLocation festivalStage = id("festival_stage");
        StardewFarmLayouts.register(
                testLayout(id),
                4,
                List.of(
                        StardewFarmLayoutConfigField.bool(
                                rain, Component.literal("Rain"),
                                Component.empty(), true),
                        StardewFarmLayoutConfigField.integer(
                                cabins, Component.literal("Cabins"),
                                Component.empty(), 2, 0, 4),
                        StardewFarmLayoutConfigField.choice(
                                theme, Component.literal("Theme"),
                                Component.empty(), "spring",
                                List.of("spring", "autumn"))),
                List.of(new StardewFarmLayoutAttachment(
                        festivalStage,
                        new BlockPos(7, 5, 9),
                        180.0F,
                        java.util.Set.of(
                                StardewFarmLayoutAttachmentKeys.FESTIVAL))));

        FarmInstanceRegistry registry = new FarmInstanceRegistry();
        UUID owner = UUID.randomUUID();
        UUID nextOwner = UUID.randomUUID();
        FarmInstance created = registry.createFarmAtDate(
                owner, "Owner", "Configured Farm", id,
                Map.of(rain, "FALSE", cabins, "4", theme, "autumn"),
                8, 0);

        assertEquals(4, created.getFarmLayoutVersion());
        assertEquals("false",
                created.getFarmLayoutConfiguration().find(rain).orElseThrow());
        assertEquals(4,
                created.getFarmLayoutConfiguration().integerValue(cabins, -1));
        assertEquals("autumn",
                created.getFarmLayoutConfiguration().find(theme).orElseThrow());
        assertEquals(created.getOrigin().offset(7, 5, 9),
                created.findFarmLayoutAttachment(festivalStage)
                        .orElseThrow().resolve(created.getOrigin()));
        assertEquals(created.getSpawnPoint(),
                created.findFarmLayoutAttachment(
                                StardewFarmLayoutAttachmentKeys.SPAWN)
                        .orElseThrow().resolve(created.getOrigin()));
        assertEquals(4,
                StardewFarmSnapshots.from(created).farmLayoutVersion());
        assertEquals(created.getFarmLayoutConfiguration(),
                StardewFarmSnapshots.from(created).farmLayoutConfiguration());
        assertEquals(created.getOrigin().offset(7, 5, 9),
                StardewFarmSnapshots.from(created)
                        .resolveLayoutAttachment(festivalStage)
                        .orElseThrow());

        FarmInstance loaded = FarmInstance.load(created.save());
        assertEquals(4, loaded.getFarmLayoutVersion());
        assertEquals(created.getFarmLayoutConfiguration(),
                loaded.getFarmLayoutConfiguration());
        assertEquals(java.util.Set.copyOf(
                        created.getFarmLayoutAttachments()),
                java.util.Set.copyOf(
                        loaded.getFarmLayoutAttachments()));

        assertEquals(true, registry.transferFarm(
                owner, nextOwner, "Next Owner"));
        FarmInstance transferred = registry.getFarm(nextOwner);
        assertNotNull(transferred);
        assertEquals(4, transferred.getFarmLayoutVersion());
        assertEquals(created.getFarmLayoutConfiguration(),
                transferred.getFarmLayoutConfiguration());
        assertEquals(java.util.Set.copyOf(
                        created.getFarmLayoutAttachments()),
                java.util.Set.copyOf(
                        transferred.getFarmLayoutAttachments()));
    }

    @Test
    void typedConfigurationRejectsUnknownAndInvalidClientValues() {
        ResourceLocation id = id("validated_" + IDS.incrementAndGet());
        ResourceLocation cabins = id("validated_cabins");
        StardewFarmLayouts.register(
                testLayout(id),
                1,
                List.of(StardewFarmLayoutConfigField.integer(
                        cabins, Component.literal("Cabins"),
                        Component.empty(), 1, 0, 3)));
        FarmInstanceRegistry registry = new FarmInstanceRegistry();

        assertThrows(IllegalArgumentException.class,
                () -> registry.createFarmAtDate(
                        UUID.randomUUID(), "Owner", "Invalid", id,
                        Map.of(cabins, "9"), 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> registry.createFarmAtDate(
                        UUID.randomUUID(), "Owner", "Unknown", id,
                        Map.of(id("unknown"), "true"), 1, 0));
    }

    private static StardewFarmLayout testLayout(ResourceLocation id) {
        StardewFarmLayout.Entry south = new StardewFarmLayout.Entry(
                new BlockPos(20, 4, 30), 90.0f,
                new BlockPos(19, 4, 31), new BlockPos(21, 6, 31));
        StardewFarmLayout.Entry east = new StardewFarmLayout.Entry(
                new BlockPos(2, 4, 15), -90.0f,
                new BlockPos(1, 4, 14), new BlockPos(1, 6, 16));
        StardewFarmLayout.Entry west = new StardewFarmLayout.Entry(
                new BlockPos(10, 4, 38), 180.0f,
                new BlockPos(9, 4, 39), new BlockPos(11, 6, 39));
        return new StardewFarmLayout(
                id,
                true,
                Component.literal("Test Layout"),
                Component.literal("A registered test layout"),
                ResourceLocation.fromNamespaceAndPath(
                        id.getNamespace(), "textures/gui/layout.png"),
                ResourceLocation.fromNamespaceAndPath(
                        id.getNamespace(), "farm/layout.schem"),
                12,
                40,
                16,
                48,
                new BlockPos(15, 4, 18),
                45.0f,
                new BlockPos(12, 4, 9),
                new BlockPos(18, 4, 16),
                south,
                east,
                west,
                "minecraft:plains",
                new BlockPos(3, 4, 3),
                new BlockPos(8, 6, 8),
                new StardewFarmLayout.Region(
                        new BlockPos(30, 4, 10),
                        new BlockPos(30, 7, 13)),
                new StardewFarmLayout.Region(
                        new BlockPos(29, 4, 10),
                        new BlockPos(29, 7, 13)),
                new StardewFarmLayout.Region(
                        new BlockPos(27, 4, 10),
                        new BlockPos(29, 7, 13)),
                new BlockPos(27, 4, 12),
                -90.0f);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "farm_layout_test", path);
    }
}
