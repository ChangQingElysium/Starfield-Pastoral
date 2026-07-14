package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingType;
import com.stardew.craft.animal.model.AnimalTypeCatalog;
import com.stardew.craft.animal.service.AnimalAcquireService;
import com.stardew.craft.animal.service.AnimalDoorStateService;
import com.stardew.craft.animal.service.AnimalEntitySyncService;
import com.stardew.craft.animal.service.AnimalShopService;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class AnimalDebugCommand {

    @SuppressWarnings("null")
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("stardew")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("building")
                    .then(Commands.literal("create")
                        .then(Commands.literal("silo")
                            .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("range", IntegerArgumentType.integer(2, 64))
                                    .executes(AnimalDebugCommand::createSilo)
                                )
                            )
                        )
                        .then(Commands.literal("coop")
                            .then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
                                .then(Commands.argument("name", StringArgumentType.string())
                                    .then(Commands.argument("range", IntegerArgumentType.integer(2, 64))
                                        .executes(AnimalDebugCommand::createCoop)
                                    )
                                )
                            )
                        )
                        .then(Commands.literal("barn")
                            .then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
                                .then(Commands.argument("name", StringArgumentType.string())
                                    .then(Commands.argument("range", IntegerArgumentType.integer(2, 64))
                                        .executes(AnimalDebugCommand::createBarn)
                                    )
                                )
                            )
                        )
                        .then(Commands.argument("family", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"coop", "barn", "silo"}, builder))
                            .then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
                                .then(Commands.argument("name", StringArgumentType.string())
                                    .then(Commands.argument("range", IntegerArgumentType.integer(2, 64))
                                        .then(Commands.argument("capacity", IntegerArgumentType.integer(1, 64))
                                            .executes(AnimalDebugCommand::createBuilding)
                                        )
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("rename")
                        .then(Commands.argument("buildingId", StringArgumentType.word())
                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(AnimalDebugCommand::renameBuilding)
                            )
                        )
                    )
                    .then(Commands.literal("remove")
                        .then(Commands.argument("buildingId", StringArgumentType.word())
                            .executes(AnimalDebugCommand::removeBuilding)
                        )
                    )
                    .then(Commands.literal("info")
                        .then(Commands.literal("silo")
                            .executes(ctx -> buildingInfoByFamily(ctx, "silo"))
                        )
                        .then(Commands.literal("coop")
                            .executes(ctx -> buildingInfoByFamily(ctx, "coop"))
                        )
                        .then(Commands.literal("barn")
                            .executes(ctx -> buildingInfoByFamily(ctx, "barn"))
                        )
                        .then(Commands.literal("all")
                            .executes(AnimalDebugCommand::listBuildings)
                        )
                        .then(Commands.argument("buildingId", StringArgumentType.word())
                            .executes(AnimalDebugCommand::buildingInfo)
                        )
                    )
                    .then(Commands.literal("door")
                        .then(Commands.argument("buildingId", StringArgumentType.word())
                            .then(Commands.literal("open").executes(ctx -> setDoor(ctx, true)))
                            .then(Commands.literal("close").executes(ctx -> setDoor(ctx, false)))
                            .then(Commands.literal("toggle").executes(AnimalDebugCommand::toggleDoor))
                        )
                    )
                    .then(Commands.literal("list")
                        .then(Commands.literal("silo")
                            .executes(ctx -> buildingInfoByFamily(ctx, "silo"))
                        )
                        .then(Commands.literal("coop")
                            .executes(ctx -> buildingInfoByFamily(ctx, "coop"))
                        )
                        .then(Commands.literal("barn")
                            .executes(ctx -> buildingInfoByFamily(ctx, "barn"))
                        )
                        .executes(AnimalDebugCommand::listBuildings)
                    )
                    .then(Commands.literal("animal")
                        .then(Commands.literal("purchase")
                            .then(Commands.argument("animalType", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(AnimalTypeCatalog.knownTypeIds(), builder))
                                .then(Commands.argument("buildingId", StringArgumentType.word())
                                    .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(AnimalDebugCommand::purchase)
                                    )
                                )
                            )
                        )
                        .then(Commands.literal("list")
                            .executes(AnimalDebugCommand::listAnimals)
                        )
                        .then(Commands.literal("shop")
                            .executes(AnimalDebugCommand::openAnimalShop)
                        )
                        .then(Commands.literal("sync")
                            .executes(AnimalDebugCommand::syncAnimals)
                        )
                    )
                    .then(Commands.literal("hay")
                        .then(Commands.literal("status")
                            .executes(AnimalDebugCommand::hayStatus)
                        )
                        .then(Commands.literal("store")
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 999))
                                .executes(AnimalDebugCommand::storeHay)
                            )
                        )
                        .then(Commands.literal("take")
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 999))
                                .executes(AnimalDebugCommand::takeHay)
                            )
                        )
                    )
                )
        );
    }

    private static int createBuilding(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        String family = StringArgumentType.getString(context, "family");
        int tier = IntegerArgumentType.getInteger(context, "tier");
        String name = StringArgumentType.getString(context, "name");
        int range = IntegerArgumentType.getInteger(context, "range");
        int capacity = IntegerArgumentType.getInteger(context, "capacity");

        AnimalBuildingType type = AnimalBuildingType.of(family, tier);
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = AnimalWorldData.get(level).createBuilding(
            level,
            type,
            player.getUUID(),
            player.blockPosition(),
            range,
            name,
            capacity
        );

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.animal.building_created", id, type.id(), name, capacity), true);
        return 1;
    }

    private static int createSilo(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        String name = StringArgumentType.getString(context, "name");
        int range = IntegerArgumentType.getInteger(context, "range");
        ServerPlayer player = context.getSource().getPlayerOrException();
        AnimalBuildingType type = AnimalBuildingType.SILO_TIER_1;
        String id = AnimalWorldData.get(level).createBuilding(level, type, player.getUUID(), player.blockPosition(), range, name, type.defaultCapacity());
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.animal.silo_created", id, type.hayCapacity()), true);
        return 1;
    }

    private static int createCoop(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        int tier = IntegerArgumentType.getInteger(context, "tier");
        String name = StringArgumentType.getString(context, "name");
        int range = IntegerArgumentType.getInteger(context, "range");
        ServerPlayer player = context.getSource().getPlayerOrException();
        AnimalBuildingType type = AnimalBuildingType.of("coop", tier);
        String id = AnimalWorldData.get(level).createBuilding(level, type, player.getUUID(), player.blockPosition(), range, name, type.defaultCapacity());
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.animal.coop_created", id, type.defaultCapacity()), true);
        return 1;
    }

    private static int createBarn(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        int tier = IntegerArgumentType.getInteger(context, "tier");
        String name = StringArgumentType.getString(context, "name");
        int range = IntegerArgumentType.getInteger(context, "range");
        ServerPlayer player = context.getSource().getPlayerOrException();
        AnimalBuildingType type = AnimalBuildingType.of("barn", tier);
        String id = AnimalWorldData.get(level).createBuilding(level, type, player.getUUID(), player.blockPosition(), range, name, type.defaultCapacity());
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.animal.barn_created", id, type.defaultCapacity()), true);
        return 1;
    }

    private static int renameBuilding(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        String buildingId = StringArgumentType.getString(context, "buildingId");
        String name = StringArgumentType.getString(context, "name");

        AnimalWorldData.get(level).renameBuilding(buildingId, name);
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.animal.building_renamed", buildingId, name), true);
        return 1;
    }

    private static int removeBuilding(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        String buildingId = StringArgumentType.getString(context, "buildingId");
        AnimalWorldData.get(level).removeBuilding(buildingId);
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.animal.building_removed", buildingId), true);
        return 1;
    }

    private static int buildingInfo(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        String buildingId = StringArgumentType.getString(context, "buildingId");
        var record = AnimalWorldData.get(level).getBuilding(buildingId)
            .orElseThrow(() -> new IllegalArgumentException("Building not found: " + buildingId));
        AnimalWorldData data = AnimalWorldData.get(level);
        int hayStored = parseOwnerHay(data, record.ownerPlayerUuid());
        int hayCap = data.getHayCapacity(java.util.UUID.fromString(record.ownerPlayerUuid()));

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.animal.building_info", record.buildingId()), false);
        context.getSource().sendSuccess(() -> Component.literal(
            "type=" + record.buildingType().id()
                + " | name=" + record.customName()
                + " | owner=" + record.ownerPlayerUuid()
                + " | door=" + (AnimalDoorStateService.isAnyBoundaryDoorOpen(level, record) ? "open" : "closed")
        ), false);
        context.getSource().sendSuccess(() -> Component.literal(
            "animals=" + record.memberAnimalIds().size() + "/" + record.capacity()
                + " | hayCap=" + record.hayCapacity()
                + " | hayStored=" + hayStored + "/" + hayCap
                + " | managerPos=" + record.managerPos()
                + " | range=" + record.range()
        ), false);
        return 1;
    }

    private static int buildingInfoByFamily(CommandContext<CommandSourceStack> context, String family) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        AnimalWorldData data = AnimalWorldData.get(level);
        var buildings = AnimalWorldData.get(level).getBuildings();
        int count = 0;
        for (var building : buildings) {
            if (!family.equalsIgnoreCase(building.buildingType().family())) {
                continue;
            }
            count++;
            int hayStored = parseOwnerHay(data, building.ownerPlayerUuid());
            int hayCap = data.getHayCapacity(java.util.UUID.fromString(building.ownerPlayerUuid()));
            context.getSource().sendSuccess(() -> Component.literal(
                "- " + building.buildingId()
                    + " | " + building.buildingType().id()
                    + " | name=" + building.customName()
                    + " | owner=" + building.ownerPlayerUuid()
                    + " | animals=" + building.memberAnimalIds().size() + "/" + building.capacity()
                    + " | hayCap=" + building.hayCapacity()
                    + " | hayStored=" + hayStored + "/" + hayCap
                    + " | door=" + (AnimalDoorStateService.isAnyBoundaryDoorOpen(level, building) ? "open" : "closed")
            ), false);
        }
        if (count == 0) {
            context.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.animal.no_buildings_for_family", family), false);
        }
        return 1;
    }

    private static int setDoor(CommandContext<CommandSourceStack> context, boolean open) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        String buildingId = StringArgumentType.getString(context, "buildingId");
        var record = AnimalWorldData.get(level).getBuilding(buildingId)
            .orElseThrow(() -> new IllegalArgumentException("Building not found: " + buildingId));
        int changed = AnimalDoorStateService.setBoundaryDoorsOpen(level, record, open);
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.animal.door_updated", buildingId, open ? "open" : "closed", changed), true);
        return 1;
    }

    private static int toggleDoor(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        String buildingId = StringArgumentType.getString(context, "buildingId");
        var record = AnimalWorldData.get(level).getBuilding(buildingId)
            .orElseThrow(() -> new IllegalArgumentException("Building not found: " + buildingId));
        boolean next = !AnimalDoorStateService.isAnyBoundaryDoorOpen(level, record);
        int changed = AnimalDoorStateService.setBoundaryDoorsOpen(level, record, next);
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.animal.door_toggled", buildingId, next ? "open" : "closed", changed), true);
        return 1;
    }

    private static int listBuildings(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        var buildings = AnimalWorldData.get(level).getBuildings();
        if (buildings.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.animal.no_buildings"), false);
            return 1;
        }

        for (var building : buildings) {
            context.getSource().sendSuccess(() -> Component.literal(
                "- " + building.buildingId()
                    + " | " + building.buildingType().id()
                    + " | name=" + building.customName()
                    + " | owner=" + building.ownerPlayerUuid()
                    + " | animals=" + building.memberAnimalIds().size() + "/" + building.capacity()
                    + " | hayCap=" + building.hayCapacity()
                    + " | door=" + (AnimalDoorStateService.isAnyBoundaryDoorOpen(level, building) ? "open" : "closed")
            ), false);
        }
        return 1;
    }

    private static int hayStatus(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        ServerPlayer player = context.getSource().getPlayerOrException();
        AnimalWorldData data = AnimalWorldData.get(level);
        int hay = data.getHayAmount(player.getUUID());
        int cap = data.getHayCapacity(player.getUUID());
        boolean hasSilo = data.hasAnySilo(player.getUUID());
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.animal.hay_status", hay, cap, hasSilo), false);
        return 1;
    }

    @SuppressWarnings("null")
    private static int storeHay(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        ServerPlayer player = context.getSource().getPlayerOrException();
        int count = IntegerArgumentType.getInteger(context, "count");
        AnimalWorldData data = AnimalWorldData.get(level);
        if (!data.hasAnySilo(player.getUUID())) {
            context.getSource().sendFailure(Component.translatable("stardewcraft.command.animal.silo_required"));
            return 0;
        }
        int stored = data.storeHay(player.getUUID(), count);
        int left = count - stored;
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.animal.hay_stored", stored, left,
            data.getHayAmount(player.getUUID()), data.getHayCapacity(player.getUUID())), true);
        return 1;
    }

    private static int takeHay(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        ServerPlayer player = context.getSource().getPlayerOrException();
        int count = IntegerArgumentType.getInteger(context, "count");
        AnimalWorldData data = AnimalWorldData.get(level);
        int removed = data.takeHay(player.getUUID(), count);
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.animal.hay_taken", removed,
            data.getHayAmount(player.getUUID()), data.getHayCapacity(player.getUUID())), true);
        return 1;
    }

    private static int purchase(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        String animalType = StringArgumentType.getString(context, "animalType");
        String buildingId = StringArgumentType.getString(context, "buildingId");
        String name = StringArgumentType.getString(context, "name");

        var record = AnimalAcquireService.purchase(level, animalType, name, buildingId);
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.animal.purchase_success",
            record.animalId(), record.animalTypeId(), record.customName(),
            record.isBaby() ? "baby" : "adult", record.ageDays(), record.daysToMature(), record.buildingId()), true);
        return 1;
    }

    private static int openAnimalShop(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        requireStardewLevel(context);
        ServerPlayer player = context.getSource().getPlayerOrException();
        AnimalShopService.openForPlayer(player);
        return 1;
    }

    private static int listAnimals(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        var animals = AnimalWorldData.get(level).getAnimals();
        if (animals.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.animal.no_animals"), false);
            return 1;
        }

        for (var animal : animals) {
            context.getSource().sendSuccess(() -> Component.literal(
                "- id=" + animal.animalId()
                    + " | type=" + animal.animalTypeId()
                    + " | name=" + animal.customName()
                    + " | stage=" + (animal.isBaby() ? "baby" : "adult")
                    + " | age=" + animal.ageDays() + "/" + animal.daysToMature()
                    + " | source=" + animal.acquisitionSource().name()
                    + " | building=" + animal.buildingId()
            ), false);
        }
        return 1;
    }

    private static int syncAnimals(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = requireStardewLevel(context);
        AnimalEntitySyncService.SyncResult result = AnimalEntitySyncService.syncAll(level);
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.animal.sync_complete",
            result.updated(), result.spawned(), result.orphansRemoved()), true);
        return 1;
    }

    private static ServerLevel requireStardewLevel(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            throw new IllegalStateException("Animal debug command requires the Stardew Valley dimension");
        }
        return level;
    }

    private static int parseOwnerHay(AnimalWorldData data, String ownerUuid) {
        try {
            return data.getHayAmount(java.util.UUID.fromString(ownerUuid));
        } catch (IllegalArgumentException ignored) {
            return 0;
        }
    }
}
