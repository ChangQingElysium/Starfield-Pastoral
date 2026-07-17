package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.cutscene.runtime.EventActorEntity;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.npc.data.NpcDataRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Commands for temporary, inert NPC actors used in screenshots and videos. */
@SuppressWarnings("null")
public final class ActorCommand {
    public static final String PHOTO_ACTOR_TAG = "stardewcraft_photo_actor";
    private static final double DEFAULT_SPAWN_DISTANCE = 2.5D;
    private static final float DEFAULT_WALK_SPEED = 1.6F;
    private static final double WORKING_RADIUS = 256.0D;

    private ActorCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardew")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("actor")
                .then(Commands.literal("spawn")
                    .then(Commands.argument("npcId", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(actorIds(), builder))
                        .executes(ActorCommand::spawnInFront)
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                            .executes(context -> spawnAt(context, null))
                            .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                .executes(context -> spawnAt(
                                    context, FloatArgumentType.getFloat(context, "yaw")))))))
                .then(Commands.literal("list")
                    .executes(ActorCommand::listActors))
                .then(walkNode())
                .then(Commands.literal("stop")
                    .then(Commands.literal("nearest")
                        .executes(context -> stopActors(context, false)))
                    .then(Commands.literal("all")
                        .executes(context -> stopActors(context, true))))
                .then(Commands.literal("face")
                    .then(Commands.literal("nearest")
                        .executes(context -> faceActors(context, false)))
                    .then(Commands.literal("all")
                        .executes(context -> faceActors(context, true))))
                .then(Commands.literal("rotate")
                    .then(Commands.literal("nearest")
                        .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                            .executes(context -> rotateActors(context, false))))
                    .then(Commands.literal("all")
                        .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                            .executes(context -> rotateActors(context, true)))))
                .then(Commands.literal("animation")
                    .then(Commands.literal("nearest")
                        .then(animationArgument(false)))
                    .then(Commands.literal("all")
                        .then(animationArgument(true))))
                .then(Commands.literal("remove")
                    .then(Commands.literal("nearest")
                        .executes(ActorCommand::removeNearest)))
                .then(Commands.literal("clear")
                    .executes(ActorCommand::clearActors))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> walkNode() {
        return Commands.literal("walk")
            .then(Commands.literal("nearest")
                .then(Commands.argument("to", Vec3Argument.vec3())
                    .executes(context -> walkNearest(context, DEFAULT_WALK_SPEED))
                    .then(Commands.argument("speed", FloatArgumentType.floatArg(0.1F, 20.0F))
                        .executes(context -> walkNearest(
                            context, FloatArgumentType.getFloat(context, "speed"))))))
            .then(Commands.argument("npcId", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(actorIds(), builder))
                .then(Commands.argument("from", Vec3Argument.vec3())
                    .then(Commands.argument("to", Vec3Argument.vec3())
                        .executes(context -> walkFromTo(context, DEFAULT_WALK_SPEED))
                        .then(Commands.argument("speed", FloatArgumentType.floatArg(0.1F, 20.0F))
                            .executes(context -> walkFromTo(
                                context, FloatArgumentType.getFloat(context, "speed")))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
    animationArgument(boolean all) {
        return Commands.argument("animation", StringArgumentType.word())
            .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[] {"idle", "walk"}, builder))
            .executes(context -> setAnimation(context, all));
    }

    private static int spawnInFront(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Vec3 position = defaultSpawnPosition(source.getPosition(), source.getRotation().y);
        return spawn(context, position, yawToward(position, source.getPosition()));
    }

    private static int spawnAt(CommandContext<CommandSourceStack> context, Float requestedYaw) {
        Vec3 position = Vec3Argument.getVec3(context, "pos");
        float yaw = requestedYaw == null
            ? yawToward(position, context.getSource().getPosition())
            : requestedYaw;
        return spawn(context, position, yaw);
    }

    private static int spawn(CommandContext<CommandSourceStack> context, Vec3 position, float yaw) {
        CommandSourceStack source = context.getSource();
        String npcId = EventActorEntity.normalizeNpcId(StringArgumentType.getString(context, "npcId"));
        if (!NpcDataRegistry.capabilities().containsKey(npcId)) {
            source.sendFailure(Component.literal("Unknown actor NPC: " + npcId + ". Use /stardew actor list."));
            return 0;
        }

        EventActorEntity actor = createPhotoActor(source, npcId, position, yaw);
        if (actor == null) {
            source.sendFailure(Component.literal("Could not spawn actor " + npcId + "."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
            "Spawned actor %s at %.2f %.2f %.2f (yaw %.1f).",
            npcId, position.x, position.y, position.z, Mth.wrapDegrees(yaw))), false);
        return 1;
    }

    private static int walkFromTo(CommandContext<CommandSourceStack> context, float speedBlocksPerSecond) {
        CommandSourceStack source = context.getSource();
        String npcId = EventActorEntity.normalizeNpcId(StringArgumentType.getString(context, "npcId"));
        if (!NpcDataRegistry.capabilities().containsKey(npcId)) {
            source.sendFailure(Component.literal("Unknown actor NPC: " + npcId + ". Use /stardew actor list."));
            return 0;
        }
        Vec3 from = Vec3Argument.getVec3(context, "from");
        Vec3 to = Vec3Argument.getVec3(context, "to");
        EventActorEntity actor = createPhotoActor(source, npcId, from, yawToward(from, to));
        if (actor == null) {
            source.sendFailure(Component.literal("Could not spawn actor " + npcId + "."));
            return 0;
        }
        actor.walkTo(to, blocksPerTick(speedBlocksPerSecond));
        sendWalkStarted(source, actor, from, to, speedBlocksPerSecond);
        return 1;
    }

    private static int walkNearest(CommandContext<CommandSourceStack> context, float speedBlocksPerSecond) {
        CommandSourceStack source = context.getSource();
        List<EventActorEntity> actors = selectedActors(source, false);
        if (actors.isEmpty()) {
            return noActors(source);
        }
        EventActorEntity actor = actors.getFirst();
        Vec3 from = actor.position();
        Vec3 to = Vec3Argument.getVec3(context, "to");
        actor.walkTo(to, blocksPerTick(speedBlocksPerSecond));
        sendWalkStarted(source, actor, from, to, speedBlocksPerSecond);
        return 1;
    }

    private static int stopActors(CommandContext<CommandSourceStack> context, boolean all) {
        CommandSourceStack source = context.getSource();
        List<EventActorEntity> actors = selectedActors(source, all);
        if (actors.isEmpty()) {
            return noActors(source);
        }
        actors.forEach(EventActorEntity::stopWalking);
        sendChanged(source, actors.size(), "Stopped");
        return actors.size();
    }

    private static EventActorEntity createPhotoActor(CommandSourceStack source, String npcId,
                                                       Vec3 position, float yaw) {
        EventActorEntity actor = new EventActorEntity(ModEntities.EVENT_ACTOR.get(), source.getLevel());
        actor.setNpcId(npcId);
        actor.addTag(PHOTO_ACTOR_TAG);
        actor.moveTo(position.x, position.y, position.z, Mth.wrapDegrees(yaw), 0.0F);
        applyYaw(actor, yaw);
        return source.getLevel().addFreshEntity(actor) ? actor : null;
    }

    private static void sendWalkStarted(CommandSourceStack source, EventActorEntity actor,
                                        Vec3 from, Vec3 to, float speedBlocksPerSecond) {
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
            "Actor %s walking from %.2f %.2f %.2f to %.2f %.2f %.2f at %.2f blocks/s.",
            actor.getNpcId(), from.x, from.y, from.z, to.x, to.y, to.z, speedBlocksPerSecond)), false);
    }

    static double blocksPerTick(float blocksPerSecond) {
        return blocksPerSecond / 20.0D;
    }

    private static int listActors(CommandContext<CommandSourceStack> context) {
        List<String> ids = actorIds();
        context.getSource().sendSuccess(
            () -> Component.literal("Actor NPC IDs (" + ids.size() + "): " + String.join(", ", ids)), false);
        return ids.size();
    }

    private static int faceActors(CommandContext<CommandSourceStack> context, boolean all) {
        CommandSourceStack source = context.getSource();
        List<EventActorEntity> actors = selectedActors(source, all);
        if (actors.isEmpty()) {
            return noActors(source);
        }
        Vec3 target = source.getPosition();
        actors.forEach(actor -> applyYaw(actor, yawToward(actor.position(), target)));
        sendChanged(source, actors.size(), "Faced");
        return actors.size();
    }

    private static int rotateActors(CommandContext<CommandSourceStack> context, boolean all) {
        CommandSourceStack source = context.getSource();
        List<EventActorEntity> actors = selectedActors(source, all);
        if (actors.isEmpty()) {
            return noActors(source);
        }
        float yaw = FloatArgumentType.getFloat(context, "yaw");
        actors.forEach(actor -> applyYaw(actor, yaw));
        sendChanged(source, actors.size(), "Rotated");
        return actors.size();
    }

    private static int setAnimation(CommandContext<CommandSourceStack> context, boolean all) {
        CommandSourceStack source = context.getSource();
        String animation = StringArgumentType.getString(context, "animation").toLowerCase(Locale.ROOT);
        if (!"idle".equals(animation) && !"walk".equals(animation)) {
            source.sendFailure(Component.literal("Unknown actor animation: " + animation + ". Use idle or walk."));
            return 0;
        }
        List<EventActorEntity> actors = selectedActors(source, all);
        if (actors.isEmpty()) {
            return noActors(source);
        }
        boolean walking = "walk".equals(animation);
        actors.forEach(actor -> actor.setWalking(walking));
        sendChanged(source, actors.size(), "Set " + animation + " animation on");
        return actors.size();
    }

    private static int removeNearest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<EventActorEntity> actors = selectedActors(source, false);
        if (actors.isEmpty()) {
            return noActors(source);
        }
        EventActorEntity actor = actors.getFirst();
        String npcId = actor.getNpcId();
        actor.discard();
        source.sendSuccess(() -> Component.literal("Removed nearest actor " + npcId + "."), false);
        return 1;
    }

    private static int clearActors(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<EventActorEntity> actors = photoActors(source);
        actors.forEach(EventActorEntity::discard);
        int count = actors.size();
        source.sendSuccess(() -> Component.literal("Removed " + count + " photography actor(s)."), false);
        return count;
    }

    private static List<EventActorEntity> selectedActors(CommandSourceStack source, boolean all) {
        List<EventActorEntity> actors = photoActors(source);
        if (all || actors.isEmpty()) {
            return actors;
        }
        return List.of(actors.getFirst());
    }

    private static List<EventActorEntity> photoActors(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 origin = source.getPosition();
        AABB bounds = AABB.ofSize(origin, WORKING_RADIUS * 2.0D,
            WORKING_RADIUS * 2.0D, WORKING_RADIUS * 2.0D);
        return level.getEntitiesOfClass(EventActorEntity.class, bounds,
                actor -> actor.isAlive() && actor.getTags().contains(PHOTO_ACTOR_TAG))
            .stream()
            .sorted(Comparator.comparingDouble(actor -> actor.distanceToSqr(origin)))
            .toList();
    }

    private static int noActors(CommandSourceStack source) {
        source.sendFailure(Component.literal("No photography actors found within 256 blocks."));
        return 0;
    }

    private static void sendChanged(CommandSourceStack source, int count, String action) {
        source.sendSuccess(() -> Component.literal(action + " " + count + " photography actor(s)."), false);
    }

    private static List<String> actorIds() {
        return NpcDataRegistry.capabilities().keySet().stream()
            .map(EventActorEntity::normalizeNpcId)
            .filter(id -> !id.isBlank())
            .distinct()
            .sorted()
            .toList();
    }

    static Vec3 defaultSpawnPosition(Vec3 sourcePosition, float sourceYaw) {
        double radians = Math.toRadians(sourceYaw);
        return sourcePosition.add(
            -Math.sin(radians) * DEFAULT_SPAWN_DISTANCE,
            0.0D,
            Math.cos(radians) * DEFAULT_SPAWN_DISTANCE);
    }

    static float yawToward(Vec3 position, Vec3 target) {
        double dx = target.x - position.x;
        double dz = target.z - position.z;
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
    }

    private static void applyYaw(EventActorEntity actor, float yaw) {
        float wrapped = Mth.wrapDegrees(yaw);
        actor.setYRot(wrapped);
        actor.setYHeadRot(wrapped);
        actor.setYBodyRot(wrapped);
    }
}
