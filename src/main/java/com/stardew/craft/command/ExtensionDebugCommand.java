package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.stardew.craft.api.v1.extension.StardewExtensionPointSnapshot;
import com.stardew.craft.api.v1.extension.StardewExtensions;
import com.stardew.craft.api.v1.extension.StardewStateDiagnostics;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntime;
import com.stardew.craft.api.v1.content.StardewContentKey;
import com.stardew.craft.api.v1.content.StardewContents;
import com.stardew.craft.api.v1.festival.StardewFestivalMechanics;
import com.stardew.craft.api.v1.festival.StardewFestivalActivities;
import com.stardew.craft.api.v1.farm.StardewFarmLayouts;
import com.stardew.craft.api.v1.npc.StardewNpcContents;
import com.stardew.craft.api.v1.npc.StardewNpcInteractions;
import com.stardew.craft.api.v1.machine.StardewTimedProduction;
import com.stardew.craft.api.v1.world.StardewLocations;
import com.stardew.craft.api.v1.world.StardewMapSlots;
import com.stardew.craft.api.v1.world.StardewRegions;
import com.stardew.craft.api.v1.world.StardewWorldAnchors;
import com.stardew.craft.farm.FarmLayoutDataRegistry;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.building.BuildingBlueprintRegistry;
import com.stardew.craft.shop.ShopInteractionBindings;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.world.LocationMusicEnvironment;
import com.stardew.craft.world.WorldAnchorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Read-only operator diagnostics for shared extension registries. */
public final class ExtensionDebugCommand {
    private ExtensionDebugCommand() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(Commands.literal("stardew")
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("extensions")
                                .executes(context -> list(
                                        context.getSource()))
                                .then(Commands.literal("list")
                                        .executes(context -> list(
                                                context.getSource())))
                                .then(Commands.literal("state-keys")
                                        .executes(context -> stateKeys(
                                                context.getSource())))
                                .then(Commands.literal("explain")
                                        .then(Commands.argument(
                                                        "extensionPoint",
                                                        StringArgumentType.word())
                                                .suggests((context, builder) ->
                                                        SharedSuggestionProvider.suggest(
                                                                StardewExtensions.snapshot()
                                                                        .stream()
                                                                        .map(snapshot -> snapshot.id()
                                                                                .toString()),
                                                                builder))
                                                .executes(context -> explain(
                                                        context.getSource(),
                                                        StringArgumentType.getString(
                                                                context,
                                                                "extensionPoint"))))))
                        .then(Commands.literal("npc")
                                .executes(context -> npcs(
                                        context.getSource()))
                                .then(Commands.argument(
                                                "npcId",
                                                StringArgumentType.word())
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(
                                                        StardewNpcContents.ids()
                                                                .stream()
                                                                .map(ResourceLocation::toString),
                                                        builder))
                                        .executes(context -> npc(
                                                context.getSource(),
                                                StringArgumentType.getString(
                                                        context,
                                                        "npcId")))))
                        .then(Commands.literal("festival")
                                .executes(context -> festivals(
                                        context.getSource()))
                                .then(Commands.argument(
                                                "festivalId",
                                                StringArgumentType.word())
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(
                                                        FestivalRegistry.snapshot()
                                                                .definitions()
                                                                .keySet()
                                                                .stream()
                                                                .map(ResourceLocation::toString),
                                                        builder))
                                        .executes(context -> festival(
                                                context.getSource(),
                                                StringArgumentType.getString(
                                                        context,
                                                        "festivalId")))))
                        .then(Commands.literal("content")
                                .executes(context -> content(
                                        context.getSource()))
                                .then(Commands.argument(
                                                "contentType",
                                                StringArgumentType.word())
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(
                                                        StardewContents.snapshot()
                                                                .nodes()
                                                                .stream()
                                                                .map(node -> node.key()
                                                                        .type()
                                                                        .toString())
                                                                .distinct(),
                                                        builder))
                                        .executes(context -> contentType(
                                                context.getSource(),
                                                StringArgumentType.getString(
                                                        context,
                                                        "contentType")))
                                        .then(Commands.argument(
                                                        "contentId",
                                                        StringArgumentType.word())
                                                .suggests((context, builder) ->
                                                        contentIdSuggestions(
                                                                context,
                                                                builder))
                                                .executes(context -> contentNode(
                                                        context.getSource(),
                                                        StringArgumentType.getString(
                                                                context,
                                                                "contentType"),
                                                        StringArgumentType.getString(
                                                                context,
                                                                "contentId"))))))
                        .then(Commands.literal("farm-layouts")
                                .executes(context -> farmLayouts(
                                        context.getSource())))
                        .then(Commands.literal("building-blueprints")
                                .executes(context -> buildingBlueprints(
                                        context.getSource())))
                        .then(Commands.literal("world")
                                .then(Commands.literal("here")
                                        .executes(context -> worldHere(
                                                context.getSource())))
                                .then(Commands.literal("locations")
                                        .executes(context -> locations(
                                                context.getSource()))
                                        .then(Commands.argument(
                                                        "location",
                                                        StringArgumentType.word())
                                                .suggests((context, builder) ->
                                                        SharedSuggestionProvider.suggest(
                                                                StardewLocations.all()
                                                                        .stream()
                                                                        .map(location -> location.id()
                                                                                .toString()),
                                                                builder))
                                                .executes(context -> location(
                                                        context.getSource(),
                                                        StringArgumentType.getString(
                                                                context,
                                                                "location")))))
                                .then(Commands.literal("anchors")
                                        .executes(context -> anchors(
                                                context.getSource()))
                                        .then(Commands.argument(
                                                        "anchor",
                                                        StringArgumentType.word())
                                                .suggests((context, builder) ->
                                                        SharedSuggestionProvider.suggest(
                                                                StardewWorldAnchors.all()
                                                                        .stream()
                                                                        .map(anchor -> anchor.id()
                                                                                .toString()),
                                                                builder))
                                                .executes(context -> anchor(
                                                        context.getSource(),
                                                        StringArgumentType.getString(
                                                                context,
                                                                "anchor")))))
                                .then(Commands.literal("slots")
                                        .executes(context -> mapSlots(
                                                context.getSource())))
                                .then(Commands.literal("regions")
                                        .executes(context -> regions(
                                                context.getSource()))
                                        .then(Commands.argument(
                                                        "region",
                                                        StringArgumentType.word())
                                                .suggests((context, builder) ->
                                                        SharedSuggestionProvider.suggest(
                                                                StardewRegions.all()
                                                                        .stream()
                                                                        .map(region -> region.id()
                                                                                .toString()),
                                                                builder))
                                                .executes(context -> region(
                                                        context.getSource(),
                                                        StringArgumentType.getString(
                                                                context,
                                                                "region"))))))));
    }

    private static int list(CommandSourceStack source) {
        List<StardewExtensionPointSnapshot> snapshots =
                StardewExtensions.snapshot();
        source.sendSuccess(() -> Component.literal(
                "Extension points: " + snapshots.size()), false);
        for (StardewExtensionPointSnapshot snapshot : snapshots) {
            source.sendSuccess(() -> Component.literal(
                    "- " + snapshot.id()
                            + " revision=" + snapshot.revision()
                            + " lifecycle=" + snapshot.lifecycle()
                            + " registrations="
                            + snapshot.registrations().size()
                            + " issues=" + snapshot.issues().size()), false);
        }
        return snapshots.size();
    }

    private static int explain(
            CommandSourceStack source,
            String rawId
    ) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            source.sendFailure(Component.literal(
                    "Invalid extension point ID: " + rawId));
            return 0;
        }
        StardewExtensionPointSnapshot snapshot =
                StardewExtensions.find(id).orElse(null);
        if (snapshot == null) {
            source.sendFailure(Component.literal(
                    "Unknown extension point: " + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                snapshot.id() + " revision=" + snapshot.revision()
                        + " lifecycle=" + snapshot.lifecycle()
                        + " issues=" + snapshot.issues().size()), false);
        for (var issue : snapshot.issues()) {
            source.sendSuccess(() -> Component.literal(
                    "  ! " + issue.kind()
                            + " id=" + issue.registrationId()
                            + " " + issue.message()), false);
        }
        if (snapshot.registrations().isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "  (no registrations)"), false);
            return 1;
        }
        for (int index = 0;
             index < snapshot.registrations().size();
             index++) {
            int order = index + 1;
            var registration = snapshot.registrations().get(index);
            source.sendSuccess(() -> Component.literal(
                    "  " + order + ". " + registration.id()
                            + " priority=" + registration.priority()
                            + " calls=" + registration.invocationCount()
                            + " failures=" + registration.failureCount()
                            + " slow=" + registration.slowInvocationCount()
                            + " maxMs="
                            + String.format(
                                    java.util.Locale.ROOT,
                                    "%.3f",
                                    registration.maxNanos()
                                            / 1_000_000.0D)), false);
            registration.lastFailure().ifPresent(failure ->
                    source.sendSuccess(() -> Component.literal(
                            "      lastFailure="
                                    + failure.exceptionType()
                                    + (failure.message().isBlank()
                                    ? ""
                                    : ": " + failure.message())), false));
        }
        return snapshot.registrations().size();
    }

    private static int stateKeys(CommandSourceStack source) {
        var keys = StardewStateDiagnostics.registeredKeys();
        source.sendSuccess(() -> Component.literal(
                "Namespaced state keys: " + keys.size()), false);
        for (var key : keys) {
            source.sendSuccess(() -> Component.literal(
                    "- scope=" + key.scope()
                            + " id=" + key.id()
                            + " version=" + key.currentVersion()), false);
        }
        return keys.size();
    }

    private static int content(CommandSourceStack source) {
        var snapshot = StardewContents.snapshot();
        var aliases = StardewContents.aliases();
        source.sendSuccess(() -> Component.literal(
                "Content nodes: " + snapshot.nodes().size()
                        + " aliases=" + aliases.size()
                        + " unresolvedAliases="
                        + aliases.stream()
                                .filter(alias -> !alias.resolved())
                                .count()
                        + " unhealthy="
                        + snapshot.unhealthyNodes().size()
                        + " catalogIssues="
                        + snapshot.issues().size()), false);
        for (var issue : snapshot.issues()) {
            source.sendSuccess(() -> Component.literal(
                    "- " + issue.severity()
                            + " source=" + issue.source()
                            + " key=" + issue.key()
                            + " " + issue.message()), false);
        }
        for (var node : snapshot.unhealthyNodes()) {
            long missing = node.references().stream()
                    .filter(reference -> reference.required()
                            && !reference.resolved())
                    .count();
            source.sendSuccess(() -> Component.literal(
                    "- " + node.key()
                            + " source=" + node.source()
                            + " missing=" + missing
                            + " issues=" + node.issues()), false);
        }
        return snapshot.nodes().size();
    }

    private static int contentType(
            CommandSourceStack source,
            String rawType
    ) {
        ResourceLocation type = ResourceLocation.tryParse(rawType);
        if (type == null) {
            source.sendFailure(Component.literal(
                    "Invalid content type: " + rawType));
            return 0;
        }
        var nodes = StardewContents.snapshot().nodes().stream()
                .filter(node -> node.key().type().equals(type))
                .toList();
        source.sendSuccess(() -> Component.literal(
                "Content type " + type + ": " + nodes.size()), false);
        for (var node : nodes) {
            source.sendSuccess(() -> Component.literal(
                    "- " + node.key().id()
                            + " source=" + node.source()
                            + " references=" + node.references().size()
                            + " status=" + (node.healthy()
                                    ? "healthy" : "UNHEALTHY")), false);
        }
        return nodes.size();
    }

    private static int contentNode(
            CommandSourceStack source,
            String rawType,
            String rawId
    ) {
        ResourceLocation type = ResourceLocation.tryParse(rawType);
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (type == null || id == null) {
            source.sendFailure(Component.literal(
                    "Invalid content key: " + rawType + "/" + rawId));
            return 0;
        }
        var node = StardewContents.find(
                new StardewContentKey(type, id)).orElse(null);
        if (node == null) {
            source.sendFailure(Component.literal(
                    "Unknown content key: " + type + "/" + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                node.key() + " source=" + node.source()
                        + " status=" + (node.healthy()
                                ? "healthy" : "UNHEALTHY")), false);
        for (var reference : node.references()) {
            source.sendSuccess(() -> Component.literal(
                    "  " + reference.role()
                            + " -> " + reference.target()
                            + " required=" + reference.required()
                            + " status=" + (reference.resolved()
                                    ? "resolved" : "MISSING")), false);
        }
        for (String issue : node.issues()) {
            source.sendSuccess(() -> Component.literal(
                    "  issue: " + issue), false);
        }
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<
            com.mojang.brigadier.suggestion.Suggestions>
    contentIdSuggestions(
            com.mojang.brigadier.context.CommandContext<
                    CommandSourceStack> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        ResourceLocation type = ResourceLocation.tryParse(
                StringArgumentType.getString(context, "contentType"));
        if (type == null) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(
                StardewContents.snapshot().nodes().stream()
                        .filter(node -> node.key().type().equals(type))
                        .map(node -> node.key().id().toString()),
                builder);
    }

    private static int npcs(CommandSourceStack source) {
        var ids = StardewNpcContents.ids();
        int issueCount = 0;
        source.sendSuccess(() -> Component.literal(
                "NPC content identities: " + ids.size()), false);
        for (ResourceLocation id : ids) {
            var snapshot = StardewNpcContents.inspect(id);
            if (!snapshot.valid()) {
                issueCount++;
            }
            source.sendSuccess(() -> Component.literal(
                    "- " + id
                            + " profile=" + snapshot.hasProfile()
                            + " dialogue=" + snapshot.hasDialogue()
                            + " schedule=" + snapshot.hasSchedule()
                            + " tastes=" + snapshot.hasGiftTastes()
                            + " shops=" + snapshot.shops().size()
                            + (snapshot.valid()
                                    ? ""
                                    : " issues=" + snapshot.issues())), false);
        }
        int finalIssueCount = issueCount;
        source.sendSuccess(() -> Component.literal(
                "NPC identities with issues: " + finalIssueCount), false);
        return ids.size();
    }

    private static int npc(
            CommandSourceStack source,
            String rawId
    ) {
        ResourceLocation id = StardewNpcInteractions.normalizeNpcId(rawId);
        if (id == null) {
            source.sendFailure(Component.literal(
                    "Invalid NPC ID: " + rawId));
            return 0;
        }
        var snapshot = StardewNpcContents.inspect(source.getLevel(), id);
        var content = snapshot.content();
        if (!content.hasContent()) {
            source.sendFailure(Component.literal(
                    "Unknown NPC content identity: " + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                id + " dimension=" + snapshot.dimension()
                        + " entity=" + snapshot.entityUuid()
                                .map(Object::toString)
                                .orElse("(not resolved)")), false);
        source.sendSuccess(() -> Component.literal(
                "  profile=" + content.hasProfile()
                        + " dialogue=" + content.hasDialogue()
                        + " schedule=" + content.hasSchedule()
                        + " tastes=" + content.hasGiftTastes()), false);
        source.sendSuccess(() -> Component.literal(
                "  shopBindings=" + content.shopBindings()
                        + " shops=" + content.shops()), false);
        source.sendSuccess(() -> Component.literal(
                "  issues=" + (content.issues().isEmpty()
                        ? "(none)" : content.issues())), false);
        return 1;
    }

    private static int festivals(CommandSourceStack source) {
        var ids = FestivalRegistry.snapshot().definitions().keySet();
        int issueCount = 0;
        source.sendSuccess(() -> Component.literal(
                "Festival definitions: " + ids.size()), false);
        for (ResourceLocation id : ids) {
            var snapshot = StardewFestivalMechanics.inspect(id)
                    .orElse(null);
            if (snapshot == null) {
                continue;
            }
            if (!snapshot.valid()) {
                issueCount++;
            }
            source.sendSuccess(() -> Component.literal(
                    "- " + id
                            + " mechanic=" + snapshot.mechanicId()
                            + " layers="
                            + snapshot.contributions().size()
                            + " legacy="
                            + snapshot.legacyHandlerAvailable()
                            + (snapshot.valid()
                                    ? ""
                                    : " issues=" + snapshot.issues())), false);
        }
        int finalIssueCount = issueCount;
        source.sendSuccess(() -> Component.literal(
                "Festival definitions with mechanic issues: "
                        + finalIssueCount), false);
        return ids.size();
    }

    private static int festival(
            CommandSourceStack source,
            String rawId
    ) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            source.sendFailure(Component.literal(
                    "Invalid festival ID: " + rawId));
            return 0;
        }
        var snapshot = StardewFestivalMechanics.inspect(id).orElse(null);
        if (snapshot == null) {
            source.sendFailure(Component.literal(
                    "Unknown festival: " + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                id + " kind=" + snapshot.kind()
                        + " mechanic=" + snapshot.mechanicId()), false);
        source.sendSuccess(() -> Component.literal(
                "  capabilities=" + snapshot.capabilities()), false);
        source.sendSuccess(() -> Component.literal(
                "  legacyHandler="
                        + snapshot.legacyHandlerAvailable()
                        + " contributions="
                        + snapshot.contributions()), false);
        source.sendSuccess(() -> Component.literal(
                "  activities="
                        + StardewFestivalActivities.registrations(
                                snapshot.mechanicId())), false);
        source.sendSuccess(() -> Component.literal(
                "  issues=" + (snapshot.issues().isEmpty()
                        ? "(none)" : snapshot.issues())), false);
        return 1;
    }

    private static int farmLayouts(CommandSourceStack source) {
        var registrations = StardewFarmLayouts.allRegistrations();
        var reload = FarmLayoutDataRegistry.lastReport();
        source.sendSuccess(() -> Component.literal(
                "Farm layouts: " + registrations.size()), false);
        source.sendSuccess(() -> Component.literal(
                "Data reload: revision=" + reload.revision()
                        + " active=" + reload.activeCount()
                        + " status="
                        + (reload.rejected() ? "REJECTED" : "applied")),
                false);
        for (String error : reload.errors()) {
            source.sendSuccess(() -> Component.literal(
                    "  reload error: " + error), false);
        }
        for (var registration : registrations) {
            var layout = registration.layout();
            source.sendSuccess(() -> Component.literal(
                    "- " + layout.id()
                            + " version=" + registration.version()
                            + " selectable=" + layout.selectable()
                            + " dimensions=" + layout.width()
                            + "x" + layout.height()
                            + "x" + layout.length()
                            + " options="
                            + registration.configurationFields().stream()
                                    .map(field -> field.id().toString())
                                    .toList()
                            + " attachments="
                            + registration.attachments().stream()
                                    .map(attachment ->
                                            attachment.id().toString())
                                    .toList()), false);
        }
        return registrations.size();
    }

    private static int buildingBlueprints(
            CommandSourceStack source
    ) {
        var blueprints = BuildingBlueprintRegistry.all();
        var reload = BuildingBlueprintRegistry.lastReport();
        source.sendSuccess(() -> Component.literal(
                "Building blueprints: " + blueprints.size()), false);
        source.sendSuccess(() -> Component.literal(
                "Catalog revision=" + BuildingBlueprintRegistry.revision()
                        + " dataActive=" + reload.activeDataCount()
                        + " status=" + (reload.rejected()
                                ? "REJECTED" : "applied")), false);
        for (String error : reload.errors()) {
            source.sendSuccess(() -> Component.literal(
                    "  reload error: " + error), false);
        }
        for (var blueprint : blueprints) {
            var definition = blueprint.definition();
            source.sendSuccess(() -> Component.literal(
                    "- " + blueprint.id()
                            + " builder=" + definition.builder()
                            + " order=" + definition.order()
                            + " money=" + definition.money()
                            + " result=" + definition.resultCount()
                            + "x " + definition.resultItem()
                            + " materials="
                            + definition.materials().size()
                            + " conditions="
                            + definition.availableWhen().size()
                            + " tags=" + definition.tags()), false);
        }
        return blueprints.size();
    }

    private static int worldHere(CommandSourceStack source) {
        BlockPos position = BlockPos.containing(source.getPosition());
        ResourceLocation dimension =
                source.getLevel().dimension().location();
        var location = StardewLocations.find(dimension, position);
        var regions = StardewRegions.findAll(dimension, position);
        source.sendSuccess(() -> Component.literal(
                "World position: dimension=" + dimension
                        + " block=" + position.toShortString()), false);
        source.sendSuccess(() -> Component.literal(
                "Location: " + location.map(value ->
                                value.id().toString())
                        .orElse("(none)")), false);
        location.ifPresent(value -> {
            var music = LocationMusicEnvironment.resolve(
                    value, StardewTimeManager.get().getCurrentTime());
            source.sendSuccess(() -> Component.literal(
                    "Music: " + music.decision()
                            + (music.track() == null
                                    ? ""
                                    : " " + music.track())), false);
        });
        source.sendSuccess(() -> Component.literal(
                "Regions: " + (regions.isEmpty()
                        ? "(none)"
                        : regions.stream().map(value ->
                                        value.id().toString())
                                .toList())), false);
        var player = source.getPlayer();
        if (player != null) {
            var bindings = ShopInteractionBindings.inspectAt(
                    player, position);
            source.sendSuccess(() -> Component.literal(
                    "Shop bindings here: " + (bindings.isEmpty()
                            ? "(none)"
                            : bindings.stream().map(binding ->
                                            binding.id()
                                                    + " shop="
                                                    + binding.shop()
                                                    + " npc="
                                                    + binding.npc()
                                                            .orElse("(block)")
                                                    + " available="
                                                    + binding.available())
                                    .toList())), false);
        }
        var blockEntity = source.getLevel()
                .getBlockEntity(position);
        if (blockEntity instanceof StardewTimedProduction machine) {
            var typeId = BuiltInRegistries.BLOCK_ENTITY_TYPE
                    .getKey(blockEntity.getType());
            source.sendSuccess(() -> Component.literal(
                    "Machine: id=" + typeId
                            + " kind=" + machine.stardewCycleKind()
                            + " automation="
                            + machine.stardewAutomationStarted()
                            + " remaining="
                            + machine.stardewRemainingMinutes()
                            + " readyAt="
                            + machine.stardewReadyAtAbsoluteMinute()
                            + " input=" + machine.stardewInput()
                            + " output=" + machine.stardewOutput()),
                    false);
        }
        var crop = StardewCropRuntime.inspect(source.getLevel(), position);
        if (crop != null) {
            boolean tracked = com.stardew.craft.manager.CropGrowthManager
                    .get(source.getLevel()).getAllCropPositions().stream()
                    .anyMatch(value -> value.dimension()
                                    == source.getLevel().dimension()
                            && value.pos().equals(crop.root()));
            source.sendSuccess(() -> Component.literal(
                    "Crop: id=" + crop.typeId()
                            + " root=" + crop.root().toShortString()
                            + " part=" + crop.part()
                            + " stage=" + crop.visualStage()
                            + " mature=" + crop.mature()
                            + " soils=" + crop.soilPositions()
                            + " dailyTracked=" + tracked),
                    false);
        }
        return 1;
    }

    private static int locations(CommandSourceStack source) {
        var locations = StardewLocations.all();
        source.sendSuccess(() -> Component.literal(
                "Logical locations: " + locations.size()), false);
        for (var location : locations) {
            int regionCount = StardewRegions.forLocation(
                    location.id()).size();
            source.sendSuccess(() -> Component.literal(
                    "- " + location.id()
                            + " dimension=" + location.dimension()
                            + " priority=" + location.priority()
                            + " parent=" + (location.parentId() == null
                                    ? "(none)" : location.parentId())
                            + " geometry=" + (regionCount == 0
                                    ? "legacy-box"
                                    : regionCount + " region(s)")), false);
        }
        return locations.size();
    }

    private static int location(
            CommandSourceStack source,
            String rawId
    ) {
        ResourceLocation id = resolveLocation(rawId);
        var location = id == null
                ? null : StardewLocations.get(id).orElse(null);
        if (location == null) {
            source.sendFailure(Component.literal(
                    "Unknown logical location: " + rawId));
            return 0;
        }
        var regions = StardewRegions.forLocation(location.id());
        source.sendSuccess(() -> Component.literal(
                location.id() + " dimension=" + location.dimension()
                        + " priority=" + location.priority()), false);
        source.sendSuccess(() -> Component.literal(
                "  coarse bounds=" + location.min().toShortString()
                        + " -> " + location.max().toShortString()), false);
        source.sendSuccess(() -> Component.literal(
                "  aliases=" + location.aliases()), false);
        source.sendSuccess(() -> Component.literal(
                "  display=" + location.displayName().getString()
                        + " icon=" + (location.iconTexture() == null
                                ? "(none)" : location.iconTexture())),
                false);
        source.sendSuccess(() -> Component.literal(
                "  hierarchy=" + StardewLocations
                        .hierarchy(location.id()).stream()
                        .map(value -> value.id().toString())
                        .toList()), false);
        source.sendSuccess(() -> Component.literal(
                "  tags=" + location.tags()
                        + " properties=" + location.properties()),
                false);
        source.sendSuccess(() -> Component.literal(
                "  geometry=" + (regions.isEmpty()
                        ? "legacy box"
                        : regions.stream().map(value ->
                                        value.id().toString())
                                .toList())), false);
        return 1;
    }

    private static int anchors(CommandSourceStack source) {
        var anchors = StardewWorldAnchors.all();
        var reload = WorldAnchorRegistry.lastReport();
        source.sendSuccess(() -> Component.literal(
                "World anchors: " + anchors.size()), false);
        source.sendSuccess(() -> Component.literal(
                "Data reload: revision=" + reload.revision()
                        + " active=" + reload.activeDataCount()
                        + " status=" + (reload.rejected()
                                ? "REJECTED" : "applied")), false);
        for (String error : reload.errors()) {
            source.sendSuccess(() -> Component.literal(
                    "  reload error: " + error), false);
        }
        for (var anchor : anchors) {
            source.sendSuccess(() -> Component.literal(
                    "- " + anchor.id()
                            + " dimension=" + anchor.dimension()
                            + " position=" + anchor.position()), false);
        }
        return anchors.size();
    }

    private static int mapSlots(CommandSourceStack source) {
        var slots = StardewMapSlots.all(source.getServer());
        source.sendSuccess(() -> Component.literal(
                "Resolved map slots: " + slots.size()), false);
        for (var slot : slots) {
            source.sendSuccess(() -> Component.literal(
                    "- " + slot.scopeType() + "/" + slot.scopeId()
                            + "/" + slot.id()
                            + " dimension=" + slot.dimension()
                            + " position=" + slot.position()
                            + " indoor=" + slot.indoor()
                            + " groundHeight="
                            + slot.useGroundHeight()
                            + " container=" + (slot.containerId() == null
                                    ? "(none)" : slot.containerId())
                            + " roles=" + slot.roles()), false);
        }
        return slots.size();
    }

    private static int anchor(
            CommandSourceStack source,
            String rawId
    ) {
        var anchor = StardewWorldAnchors.resolve(rawId).orElse(null);
        if (anchor == null) {
            source.sendFailure(Component.literal(
                    "Unknown world anchor: " + rawId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                anchor.id() + " dimension=" + anchor.dimension()
                        + " position=" + anchor.position()
                        + " yaw=" + anchor.yaw()), false);
        source.sendSuccess(() -> Component.literal(
                "  indoor=" + anchor.indoor()
                        + " useGroundHeight="
                        + anchor.useGroundHeight()
                        + " location=" + anchor.locationId()), false);
        source.sendSuccess(() -> Component.literal(
                "  roles=" + anchor.roles()), false);
        return 1;
    }

    private static int regions(CommandSourceStack source) {
        var regions = StardewRegions.all();
        source.sendSuccess(() -> Component.literal(
                "World regions: " + regions.size()), false);
        for (var region : regions) {
            source.sendSuccess(() -> Component.literal(
                    "- " + region.id()
                            + " dimension=" + region.dimension()
                            + " priority=" + region.priority()
                            + " location=" + region.locationId()), false);
        }
        return regions.size();
    }

    private static int region(
            CommandSourceStack source,
            String rawId
    ) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        var region = id == null
                ? null : StardewRegions.get(id).orElse(null);
        if (region == null) {
            source.sendFailure(Component.literal(
                    "Unknown world region: " + rawId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                region.id() + " dimension=" + region.dimension()
                        + " priority=" + region.priority()
                        + " location=" + region.locationId()), false);
        source.sendSuccess(() -> Component.literal(
                "  include boxes=" + region.includes().size()
                        + " exclude boxes=" + region.excludes().size()),
                false);
        source.sendSuccess(() -> Component.literal(
                "  tags=" + region.tags()), false);
        for (int index = 0; index < region.includes().size(); index++) {
            int current = index + 1;
            var box = region.includes().get(index);
            source.sendSuccess(() -> Component.literal(
                    "  include " + current + ": "
                            + box.min().toShortString() + " -> "
                            + box.max().toShortString()), false);
        }
        return 1;
    }

    private static ResourceLocation resolveLocation(String rawId) {
        ResourceLocation direct = ResourceLocation.tryParse(rawId);
        if (direct != null && StardewLocations.get(direct).isPresent()) {
            return direct;
        }
        return StardewLocations.resolveId(rawId).orElse(null);
    }
}
