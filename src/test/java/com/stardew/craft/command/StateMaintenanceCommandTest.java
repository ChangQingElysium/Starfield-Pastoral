package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.stardew.craft.api.v1.extension.StardewStateContainerSnapshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StateMaintenanceCommandTest {
    @Test
    void registersExportAndTwoStepRepairPaths() {
        CommandDispatcher<CommandSourceStack> dispatcher =
                new CommandDispatcher<>();

        StateMaintenanceCommand.register(dispatcher);

        var state = dispatcher.getRoot()
                .getChild("stardew")
                .getChild("state");
        assertNotNull(state.getChild("export")
                .getChild("farm")
                .getChild("owner"));
        var repairEntry = state.getChild("repair")
                .getChild("farm")
                .getChild("owner")
                .getChild("entry");
        assertNotNull(repairEntry.getChild("preview"));
        assertNotNull(repairEntry.getChild("confirm")
                .getChild("token"));
    }

    @Test
    void exportEnvelopeIncludesDiagnosticsAndDefensivelyCopiesEntries() {
        ResourceLocation scope = id("state/farm");
        ResourceLocation stored = id("stored");
        ResourceLocation orphan = ResourceLocation.fromNamespaceAndPath(
                "missing_addon", "orphan");
        CompoundTag entries = new CompoundTag();
        CompoundTag value = new CompoundTag();
        value.putString("opaque", "keep");
        entries.put(orphan.toString(), value);
        StardewStateContainerSnapshot snapshot =
                new StardewStateContainerSnapshot(
                        scope,
                        Set.of(stored, orphan),
                        Set.of(stored),
                        Set.of(orphan),
                        Set.of(),
                        Set.of(stored),
                        Set.of(),
                        List.of("invalid entry"));

        CompoundTag export = StateMaintenanceCommand.createExportTag(
                "farm",
                "owner",
                1234L,
                snapshot,
                entries);
        entries.getCompound(orphan.toString())
                .putString("opaque", "changed");

        assertEquals(1, export.getInt("formatVersion"));
        assertEquals(scope.toString(), export.getString("scope"));
        assertEquals("farm", export.getString("subjectType"));
        assertEquals("owner", export.getString("subjectId"));
        assertEquals(1234L, export.getLong("exportedAtEpochMillis"));
        assertEquals("keep", export.getCompound("entries")
                .getCompound(orphan.toString())
                .getString("opaque"));
        assertFalse(export.getList("orphanedIds", 8).isEmpty());
        assertFalse(export.getList("legacyVersionIds", 8).isEmpty());
        assertFalse(export.getList("invalidEntryNames", 8).isEmpty());
    }

    @Test
    void exportFileComponentsCannotEscapeTheFixedDirectory() {
        assertEquals(
                "missing_addon_orphan_name",
                StateMaintenanceCommand.safeFileComponent(
                        "missing_addon:orphan/name"));
        assertEquals(
                "state",
                StateMaintenanceCommand.safeFileComponent("///"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_test", path);
    }
}
