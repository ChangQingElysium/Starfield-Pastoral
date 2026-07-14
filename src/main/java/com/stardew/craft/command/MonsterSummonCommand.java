package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

@SuppressWarnings("null")
public final class MonsterSummonCommand {

    private MonsterSummonCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("stardew")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("summon")
                                .then(Commands.argument("monsterId", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                MineMonsterSpawnHandler.getSummonableMonsterIds(), builder))
                                        .executes(context -> summon(context, inferFloor(context), 1))
                                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 999))
                                                .executes(context -> summon(
                                                        context,
                                                        IntegerArgumentType.getInteger(context, "floor"),
                                                        1))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                                                        .executes(context -> summon(
                                                                context,
                                                                IntegerArgumentType.getInteger(context, "floor"),
                                                                IntegerArgumentType.getInteger(context, "count")))))))
        );
    }

    private static int summon(CommandContext<CommandSourceStack> context, int floor, int count) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("stardewcraft.command.summon.player_only"));
            return 0;
        }

        String monsterId = StringArgumentType.getString(context, "monsterId");
        ServerLevel level = player.serverLevel();
        Vec3 lookAhead = player.getLookAngle().normalize().scale(2.5D);
        Vec3 base = player.position().add(lookAhead).add(0.0D, 0.1D, 0.0D);

        int spawned = 0;
        Mob lastMob = null;
        for (int index = 0; index < count; index++) {
            Vec3 position = count == 1
                    ? base
                    : base.add((index % 4 - 1.5D) * 0.9D, 0.0D, (index / 4) * 0.9D);
            Mob mob = MineMonsterSpawnHandler.spawnConfiguredMonster(level, monsterId, position, player.getYRot(), floor);
            if (mob != null) {
                spawned++;
                lastMob = mob;
            }
        }

        if (spawned <= 0) {
            source.sendFailure(Component.translatable("stardewcraft.command.summon.unknown", monsterId));
            return 0;
        }

        Mob resultMob = lastMob;
        int resultCount = spawned;
        int resultFloor = floor;
        Component monsterName = resultMob != null && resultMob.getCustomName() != null
                ? resultMob.getCustomName()
                : Component.literal(monsterId);
        source.sendSuccess(() -> Component.translatable(
                "stardewcraft.command.summon.success", resultCount, monsterName, resultFloor), true);
        return spawned;
    }

    private static int inferFloor(CommandContext<CommandSourceStack> context) {
        return MineMonsterSpawnHandler.inferFloor(context.getSource().getPlayer());
    }
}
