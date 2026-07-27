package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.api.v1.communitycenter.StardewCommunityCenterPersistentData;
import com.stardew.craft.api.v1.extension.StardewStateContainerSnapshot;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionPersistentData;
import com.stardew.craft.api.v1.internal.state.NamespacedStateMaintenance;
import com.stardew.craft.api.v1.internal.state.NamespacedStateRemovalPreview;
import com.stardew.craft.api.v1.internal.state.NamespacedStateRemovalResult;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.FestivalSessionState;
import com.stardew.craft.festival.FestivalWorldData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Operator-only export tools for namespaced persistent state. */
public final class StateMaintenanceCommand {
    private static final int EXPORT_FORMAT_VERSION = 1;
    private static final String EXPORT_DIRECTORY =
            "stardewcraft-state-exports";

    private StateMaintenanceCommand() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(Commands.literal("stardew")
                .then(Commands.literal("state")
                        .requires(source -> source.hasPermission(3))
                        .then(exportCommands())
                        .then(repairCommands())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack>
    exportCommands() {
        return Commands.literal("export")
                .then(Commands.literal("farm")
                        .then(Commands.argument(
                                        "owner", UuidArgument.uuid())
                                .executes(context -> exportFarm(
                                        context.getSource(),
                                        UuidArgument.getUuid(
                                                context, "owner")))))
                .then(Commands.literal("animal")
                        .then(Commands.argument(
                                        "animalId",
                                        LongArgumentType.longArg(0L))
                                .executes(context -> exportAnimal(
                                        context.getSource(),
                                        LongArgumentType.getLong(
                                                context, "animalId")))))
                .then(Commands.literal("festival")
                        .then(Commands.argument(
                                        "festivalId",
                                        ResourceLocationArgument.id())
                                .executes(context -> exportFestival(
                                        context.getSource(),
                                        ResourceLocationArgument.getId(
                                                context,
                                                "festivalId")))))
                .then(Commands.literal("community-center")
                        .then(Commands.argument(
                                        "player", UuidArgument.uuid())
                                .executes(context ->
                                        exportCommunityCenter(
                                                context.getSource(),
                                                UuidArgument.getUuid(
                                                        context,
                                                        "player")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack>
    repairCommands() {
        return Commands.literal("repair")
                .then(Commands.literal("farm")
                        .then(Commands.argument(
                                        "owner", UuidArgument.uuid())
                                .then(Commands.argument(
                                                "entry",
                                                StringArgumentType.word())
                                        .then(Commands.literal("preview")
                                                .executes(context ->
                                                        previewFarmRemoval(
                                                                context.getSource(),
                                                                UuidArgument.getUuid(
                                                                        context,
                                                                        "owner"),
                                                                StringArgumentType.getString(
                                                                        context,
                                                                        "entry"))))
                                        .then(Commands.literal("confirm")
                                                .then(Commands.argument(
                                                                "token",
                                                                StringArgumentType.word())
                                                        .executes(context ->
                                                                confirmFarmRemoval(
                                                                        context.getSource(),
                                                                        UuidArgument.getUuid(
                                                                                context,
                                                                                "owner"),
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "entry"),
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "token"))))))))
                .then(Commands.literal("animal")
                        .then(Commands.argument(
                                        "animalId",
                                        LongArgumentType.longArg(0L))
                                .then(Commands.argument(
                                                "entry",
                                                StringArgumentType.word())
                                        .then(Commands.literal("preview")
                                                .executes(context ->
                                                        previewAnimalRemoval(
                                                                context.getSource(),
                                                                LongArgumentType.getLong(
                                                                        context,
                                                                        "animalId"),
                                                                StringArgumentType.getString(
                                                                        context,
                                                                        "entry"))))
                                        .then(Commands.literal("confirm")
                                                .then(Commands.argument(
                                                                "token",
                                                                StringArgumentType.word())
                                                        .executes(context ->
                                                                confirmAnimalRemoval(
                                                                        context.getSource(),
                                                                        LongArgumentType.getLong(
                                                                                context,
                                                                                "animalId"),
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "entry"),
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "token"))))))))
                .then(Commands.literal("festival")
                        .then(Commands.argument(
                                        "festivalId",
                                        ResourceLocationArgument.id())
                                .then(Commands.argument(
                                                "entry",
                                                StringArgumentType.word())
                                        .then(Commands.literal("preview")
                                                .executes(context ->
                                                        previewFestivalRemoval(
                                                                context.getSource(),
                                                                ResourceLocationArgument.getId(
                                                                        context,
                                                                        "festivalId"),
                                                                StringArgumentType.getString(
                                                                        context,
                                                                        "entry"))))
                                        .then(Commands.literal("confirm")
                                                .then(Commands.argument(
                                                                "token",
                                                                StringArgumentType.word())
                                                        .executes(context ->
                                                                confirmFestivalRemoval(
                                                                        context.getSource(),
                                                                        ResourceLocationArgument.getId(
                                                                                context,
                                                                                "festivalId"),
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "entry"),
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "token"))))))))
                .then(Commands.literal("community-center")
                        .then(Commands.argument(
                                        "player", UuidArgument.uuid())
                                .then(Commands.argument(
                                                "entry",
                                                StringArgumentType.word())
                                        .then(Commands.literal("preview")
                                                .executes(context ->
                                                        previewCommunityCenterRemoval(
                                                                context.getSource(),
                                                                UuidArgument.getUuid(
                                                                        context,
                                                                        "player"),
                                                                StringArgumentType.getString(
                                                                        context,
                                                                        "entry"))))
                                        .then(Commands.literal("confirm")
                                                .then(Commands.argument(
                                                                "token",
                                                                StringArgumentType.word())
                                                        .executes(context ->
                                                                confirmCommunityCenterRemoval(
                                                                        context.getSource(),
                                                                        UuidArgument.getUuid(
                                                                                context,
                                                                                "player"),
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "entry"),
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "token"))))))));
    }

    private static int exportFarm(
            CommandSourceStack source,
            UUID owner
    ) {
        FarmInstance farm = FarmInstanceRegistry.get(source.getServer())
                .getFarm(owner);
        if (farm == null) {
            source.sendFailure(Component.literal(
                    "No farm exists for " + owner));
            return 0;
        }
        return export(
                source,
                "farm",
                owner.toString(),
                farm.persistentData().diagnostics(),
                farm.persistentData().toTag());
    }

    private static int exportAnimal(
            CommandSourceStack source,
            long animalId
    ) {
        var animal = AnimalWorldData.get(source.getLevel())
                .getAnimal(animalId)
                .orElse(null);
        if (animal == null) {
            source.sendFailure(Component.literal(
                    "Unknown managed animal: " + animalId));
            return 0;
        }
        return export(
                source,
                "animal",
                Long.toString(animalId),
                animal.persistentData().diagnostics(),
                animal.persistentData().toTag());
    }

    private static int exportFestival(
            CommandSourceStack source,
            ResourceLocation festivalId
    ) {
        String runtimeId = FestivalRegistry.get(festivalId)
                .map(FestivalDefinition::id)
                .orElse(festivalId.toString());
        FestivalSessionState session =
                FestivalWorldData.get(source.getLevel())
                        .getSession(runtimeId)
                        .orElse(null);
        if (session == null) {
            source.sendFailure(Component.literal(
                    "No active or persisted festival session: "
                            + festivalId));
            return 0;
        }
        StardewFestivalSessionPersistentData data =
                session.persistentData();
        return export(
                source,
                "festival",
                festivalId.toString(),
                data.diagnostics(),
                data.toTag());
    }

    private static int exportCommunityCenter(
            CommandSourceStack source,
            UUID player
    ) {
        CompoundTag entries =
                CommunityCenterSavedData.get(source.getLevel())
                        .getAddonData(player);
        StardewStateContainerSnapshot snapshot =
                com.stardew.craft.api.v1.communitycenter
                        .StardewCommunityCenterPersistentData
                        .diagnostics(source.getLevel(), player);
        return export(
                source,
                "community_center",
                player.toString(),
                snapshot,
                entries);
    }

    private static int previewFarmRemoval(
            CommandSourceStack source,
            UUID owner,
            String entryName
    ) {
        FarmInstance farm = FarmInstanceRegistry.get(source.getServer())
                .getFarm(owner);
        if (farm == null) {
            return missing(source, "No farm exists for " + owner);
        }
        var authority = NamespacedStateMaintenance.authorize(source);
        return showRemovalPreview(
                source,
                farm.persistentData()
                        .previewRemovalForAdministration(
                                authority, entryName));
    }

    private static int confirmFarmRemoval(
            CommandSourceStack source,
            UUID owner,
            String entryName,
            String token
    ) {
        FarmInstanceRegistry registry =
                FarmInstanceRegistry.get(source.getServer());
        FarmInstance farm = registry.getFarm(owner);
        if (farm == null) {
            return missing(source, "No farm exists for " + owner);
        }
        var authority = NamespacedStateMaintenance.authorize(source);
        Optional<NamespacedStateRemovalPreview> preview =
                farm.persistentData()
                        .previewRemovalForAdministration(
                                authority, entryName);
        return confirmRemoval(
                source,
                token,
                preview,
                () -> export(
                        source,
                        "farm",
                        owner.toString(),
                        farm.persistentData().diagnostics(),
                        farm.persistentData().toTag()),
                () -> farm.persistentData()
                        .applyRemovalForAdministration(
                                authority, preview.orElseThrow()),
                registry::setDirty);
    }

    private static int previewAnimalRemoval(
            CommandSourceStack source,
            long animalId,
            String entryName
    ) {
        var animal = AnimalWorldData.get(source.getLevel())
                .getAnimal(animalId)
                .orElse(null);
        if (animal == null) {
            return missing(
                    source, "Unknown managed animal: " + animalId);
        }
        var authority = NamespacedStateMaintenance.authorize(source);
        return showRemovalPreview(
                source,
                animal.persistentData()
                        .previewRemovalForAdministration(
                                authority, entryName));
    }

    private static int confirmAnimalRemoval(
            CommandSourceStack source,
            long animalId,
            String entryName,
            String token
    ) {
        AnimalWorldData data = AnimalWorldData.get(source.getLevel());
        var animal = data.getAnimal(animalId).orElse(null);
        if (animal == null) {
            return missing(
                    source, "Unknown managed animal: " + animalId);
        }
        var authority = NamespacedStateMaintenance.authorize(source);
        Optional<NamespacedStateRemovalPreview> preview =
                animal.persistentData()
                        .previewRemovalForAdministration(
                                authority, entryName);
        return confirmRemoval(
                source,
                token,
                preview,
                () -> export(
                        source,
                        "animal",
                        Long.toString(animalId),
                        animal.persistentData().diagnostics(),
                        animal.persistentData().toTag()),
                () -> animal.persistentData()
                        .applyRemovalForAdministration(
                                authority, preview.orElseThrow()),
                data::markChanged);
    }

    private static int previewFestivalRemoval(
            CommandSourceStack source,
            ResourceLocation festivalId,
            String entryName
    ) {
        FestivalSessionState session = festivalSession(
                source, festivalId);
        if (session == null) {
            return missing(
                    source,
                    "No active or persisted festival session: "
                            + festivalId);
        }
        var authority = NamespacedStateMaintenance.authorize(source);
        return showRemovalPreview(
                source,
                session.persistentData()
                        .previewRemovalForAdministration(
                                authority, entryName));
    }

    private static int confirmFestivalRemoval(
            CommandSourceStack source,
            ResourceLocation festivalId,
            String entryName,
            String token
    ) {
        FestivalWorldData data =
                FestivalWorldData.get(source.getLevel());
        FestivalSessionState session = festivalSession(
                source, festivalId);
        if (session == null) {
            return missing(
                    source,
                    "No active or persisted festival session: "
                            + festivalId);
        }
        var authority = NamespacedStateMaintenance.authorize(source);
        Optional<NamespacedStateRemovalPreview> preview =
                session.persistentData()
                        .previewRemovalForAdministration(
                                authority, entryName);
        return confirmRemoval(
                source,
                token,
                preview,
                () -> export(
                        source,
                        "festival",
                        festivalId.toString(),
                        session.persistentData().diagnostics(),
                        session.persistentData().toTag()),
                () -> session.persistentData()
                        .applyRemovalForAdministration(
                                authority, preview.orElseThrow()),
                data::setDirty);
    }

    private static int previewCommunityCenterRemoval(
            CommandSourceStack source,
            UUID player,
            String entryName
    ) {
        var authority = NamespacedStateMaintenance.authorize(source);
        return showRemovalPreview(
                source,
                StardewCommunityCenterPersistentData
                        .previewRemovalForAdministration(
                                source.getLevel(),
                                player,
                                authority,
                                entryName));
    }

    private static int confirmCommunityCenterRemoval(
            CommandSourceStack source,
            UUID player,
            String entryName,
            String token
    ) {
        var authority = NamespacedStateMaintenance.authorize(source);
        Optional<NamespacedStateRemovalPreview> preview =
                StardewCommunityCenterPersistentData
                        .previewRemovalForAdministration(
                                source.getLevel(),
                                player,
                                authority,
                                entryName);
        return confirmRemoval(
                source,
                token,
                preview,
                () -> exportCommunityCenter(source, player),
                () -> StardewCommunityCenterPersistentData
                        .applyRemovalForAdministration(
                                source.getLevel(),
                                player,
                                authority,
                                preview.orElseThrow()),
                () -> {
                });
    }

    private static FestivalSessionState festivalSession(
            CommandSourceStack source,
            ResourceLocation festivalId
    ) {
        String runtimeId = FestivalRegistry.get(festivalId)
                .map(FestivalDefinition::id)
                .orElse(festivalId.toString());
        return FestivalWorldData.get(source.getLevel())
                .getSession(runtimeId)
                .orElse(null);
    }

    private static int showRemovalPreview(
            CommandSourceStack source,
            Optional<NamespacedStateRemovalPreview> preview
    ) {
        if (preview.isEmpty()) {
            return missing(
                    source,
                    "Entry is missing or is not eligible for removal");
        }
        NamespacedStateRemovalPreview value = preview.orElseThrow();
        source.sendSuccess(() -> Component.literal(
                "Removal preview scope=" + value.scope()
                        + " entry=" + value.entryName()
                        + " issues=" + value.issues()
                        + " token=" + value.confirmationToken()), false);
        source.sendSuccess(() -> Component.literal(
                "No state changed. Re-run with confirm and this token; "
                        + "a backup export will be written first."), false);
        return 1;
    }

    private static int confirmRemoval(
            CommandSourceStack source,
            String suppliedToken,
            Optional<NamespacedStateRemovalPreview> preview,
            IntSupplier exportAction,
            Supplier<NamespacedStateRemovalResult> applyAction,
            Runnable markDirty
    ) {
        if (preview.isEmpty()) {
            return missing(
                    source,
                    "Entry is missing or is no longer eligible");
        }
        NamespacedStateRemovalPreview value = preview.orElseThrow();
        if (!value.confirmationToken().equals(suppliedToken)) {
            return missing(
                    source,
                    "Confirmation token is stale or incorrect; "
                            + "run preview again");
        }
        if (exportAction.getAsInt() == 0) {
            return missing(
                    source,
                    "Backup export failed; state was not changed");
        }
        NamespacedStateRemovalResult result = applyAction.get();
        if (result != NamespacedStateRemovalResult.APPLIED) {
            return missing(
                    source,
                    "Removal was not applied: " + result);
        }
        markDirty.run();
        source.sendSuccess(() -> Component.literal(
                "Removed unhealthy namespaced-state entry "
                        + value.entryName()
                        + " after backup export"), true);
        return 1;
    }

    private static int missing(
            CommandSourceStack source,
            String message
    ) {
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int export(
            CommandSourceStack source,
            String subjectType,
            String subjectId,
            StardewStateContainerSnapshot snapshot,
            CompoundTag entries
    ) {
        long exportedAt = Instant.now().toEpochMilli();
        CompoundTag export = createExportTag(
                subjectType,
                subjectId,
                exportedAt,
                snapshot,
                entries);
        Path directory = source.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve(EXPORT_DIRECTORY);
        String fileName = safeFileComponent(subjectType)
                + "-" + safeFileComponent(subjectId)
                + "-" + exportedAt + ".nbt";
        Path output = directory.resolve(fileName);
        try {
            Files.createDirectories(directory);
            NbtIo.writeCompressed(export, output);
        } catch (IOException exception) {
            source.sendFailure(Component.literal(
                    "Failed to export namespaced state: "
                            + exception.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Exported namespaced state to "
                        + output.toAbsolutePath()), false);
        return 1;
    }

    static CompoundTag createExportTag(
            String subjectType,
            String subjectId,
            long exportedAt,
            StardewStateContainerSnapshot snapshot,
            CompoundTag entries
    ) {
        CompoundTag export = new CompoundTag();
        export.putInt("formatVersion", EXPORT_FORMAT_VERSION);
        export.putString("scope", snapshot.scope().toString());
        export.putString("subjectType", subjectType);
        export.putString("subjectId", subjectId);
        export.putLong("exportedAtEpochMillis", exportedAt);
        export.put("storedIds", stringList(snapshot.storedIds()));
        export.put("registeredIds", stringList(
                snapshot.registeredIds()));
        export.put("orphanedIds", stringList(
                snapshot.orphanedIds()));
        export.put("malformedIds", stringList(
                snapshot.malformedIds()));
        export.put("legacyVersionIds", stringList(
                snapshot.legacyVersionIds()));
        export.put("futureVersionIds", stringList(
                snapshot.futureVersionIds()));
        export.put("invalidEntryNames", stringList(
                snapshot.invalidEntryNames()));
        export.put("entries", entries.copy());
        return export;
    }

    static String safeFileComponent(String value) {
        String safe = value
                .replaceAll("[^A-Za-z0-9._-]+", "_")
                .replaceAll("^_+$", "");
        return safe.isBlank() ? "state" : safe;
    }

    private static ListTag stringList(Collection<?> values) {
        ListTag list = new ListTag();
        values.stream()
                .map(Object::toString)
                .sorted()
                .map(StringTag::valueOf)
                .forEach(list::add);
        return list;
    }
}
