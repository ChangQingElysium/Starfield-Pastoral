package com.stardew.craft.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.stardew.craft.StardewCraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;

public class CameraDebugCommand {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CAM_FILE = FMLPaths.CONFIGDIR.get().resolve("stardewcraft_cameras.json").toFile();
    private static JsonObject cameraData = new JsonObject();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        loadCameras();

        dispatcher.register(Commands.literal("sccam")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(CameraDebugCommand::saveCamera)))
                .then(Commands.literal("tp")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String key : cameraData.keySet()) {
                                        builder.suggest(key);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(CameraDebugCommand::tpCamera)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String key : cameraData.keySet()) {
                                        builder.suggest(key);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(CameraDebugCommand::removeCamera)))
                .then(Commands.literal("list")
                        .executes(CameraDebugCommand::listCameras))
        );
    }

    private static void loadCameras() {
        try {
            if (CAM_FILE.exists()) {
                try (FileReader reader = new FileReader(CAM_FILE)) {
                    cameraData = GSON.fromJson(reader, JsonObject.class);
                    if (cameraData == null) cameraData = new JsonObject();
                }
            } else {
                cameraData = new JsonObject();
            }
        } catch (Exception e) {
            StardewCraft.LOGGER.error("Failed to load camera data", e);
            cameraData = new JsonObject();
        }
    }

    private static void saveToDisk() {
        try (FileWriter writer = new FileWriter(CAM_FILE)) {
            GSON.toJson(cameraData, writer);
        } catch (Exception e) {
            StardewCraft.LOGGER.error("Failed to save camera data", e);
        }
    }

    private static int saveCamera(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");

        JsonObject cam = new JsonObject();
        // Using player's continuous exact position
        double x = Math.round(player.getX() * 1000.0) / 1000.0;
        double y = Math.round(player.getY() * 1000.0) / 1000.0;
        double z = Math.round(player.getZ() * 1000.0) / 1000.0;
        float yaw = Math.round(player.getYRot() * 10.0f) / 10.0f;
        float pitch = Math.round(player.getXRot() * 10.0f) / 10.0f;

        cam.addProperty("x", x);
        cam.addProperty("y", y);
        cam.addProperty("z", z);
        cam.addProperty("yaw", yaw);
        cam.addProperty("pitch", pitch);

        boolean overwritten = cameraData.has(name);
        cameraData.add(name, cam);
        saveToDisk();

        String copyableJson = String.format(java.util.Locale.ROOT, "{ \"cmd\": \"camera\", \"x\": %.3f, \"y\": %.3f, \"z\": %.3f, \"yaw\": %.1f, \"pitch\": %.1f, \"relative\": false }", x, y, z, yaw, pitch);
        
        context.getSource().sendSuccess(() -> Component.literal(
                (overwritten ? "§e[Overwritten] " : "§a[Saved] ") + "Camera '" + name + "'\n" +
                "§7Pos: " + x + ", " + y + ", " + z + " | Rot: " + yaw + " / " + pitch + "\n" +
                "§fJSON (Click to copy): §b" + copyableJson
        ).withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, copyableJson))), false);

        return 1;
    }

    private static int tpCamera(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");

        if (!cameraData.has(name)) {
            context.getSource().sendFailure(Component.literal("Camera '" + name + "' not found."));
            return 0;
        }

        JsonObject cam = cameraData.getAsJsonObject(name);
        double x = cam.get("x").getAsDouble();
        double y = cam.get("y").getAsDouble();
        double z = cam.get("z").getAsDouble();
        float yaw = cam.get("yaw").getAsFloat();
        float pitch = cam.get("pitch").getAsFloat();

        player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch);
        context.getSource().sendSuccess(() -> Component.literal("§aTeleported to camera '" + name + "'"), false);

        return 1;
    }

    private static int removeCamera(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");

        if (!cameraData.has(name)) {
            context.getSource().sendFailure(Component.literal("Camera '" + name + "' not found."));
            return 0;
        }

        cameraData.remove(name);
        saveToDisk();
        context.getSource().sendSuccess(() -> Component.literal("§cRemoved camera '" + name + "'"), false);
        return 1;
    }

    private static int listCameras(CommandContext<CommandSourceStack> context) {
        if (cameraData.size() == 0) {
            context.getSource().sendSuccess(() -> Component.literal("§eNo cameras saved yet."), false);
            return 1;
        }
        
        context.getSource().sendSuccess(() -> Component.literal("§aSaved Cameras:"), false);
        for (Map.Entry<String, JsonElement> entry : cameraData.entrySet()) {
            String name = entry.getKey();
            context.getSource().sendSuccess(() -> Component.literal(
                " §7- §f" + name + " §8(tp to check)"
            ).withStyle(style -> style
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, "/sccam tp " + name))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to tp")))), false);
        }
        return 1;
    }
}
