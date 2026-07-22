package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.cutscene.network.SyncEventSeenPayload;
import com.stardew.craft.cutscene.server.EventSeenData;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmPermissionManager;
import com.stardew.craft.mining.MineRewardClaimManager;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.network.payload.FarmPermSyncPayload;
import com.stardew.craft.network.payload.SyncNpcFriendshipOverviewPayload;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.player.*;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData;

/**
 * 玩家数据命令
 * 用于测试和调试玩家数据系统
 */
public class PlayerDataCommand {

    private static final double LUCK_MIN = PlayerStardewDataAPI.DAILY_LUCK_MIN;
    private static final double LUCK_MAX = PlayerStardewDataAPI.DAILY_LUCK_MAX;
    
    @SuppressWarnings("null")
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardew")
            .then(Commands.literal("player")
                .requires(source -> source.hasPermission(2))
                // 查看数据
                .then(CommandTargets.executesWithTarget(
                    Commands.literal("info"),
                    PlayerDataCommand::showPlayerInfo))

                // 经验相关
                .then(Commands.literal("exp")
                    .then(Commands.literal("add")
                        .then(Commands.literal("farming")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(1)),
                                ctx -> addExperience(ctx, SkillType.FARMING))))
                        .then(Commands.literal("fishing")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(1)),
                                ctx -> addExperience(ctx, SkillType.FISHING))))
                        .then(Commands.literal("foraging")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(1)),
                                ctx -> addExperience(ctx, SkillType.FORAGING))))
                        .then(Commands.literal("mining")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(1)),
                                ctx -> addExperience(ctx, SkillType.MINING))))
                        .then(Commands.literal("combat")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(1)),
                                ctx -> addExperience(ctx, SkillType.COMBAT)))))
                    .then(Commands.literal("set")
                        .then(Commands.literal("farming")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(0)),
                                ctx -> setExperience(ctx, SkillType.FARMING))))
                        .then(Commands.literal("fishing")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(0)),
                                ctx -> setExperience(ctx, SkillType.FISHING))))
                        .then(Commands.literal("foraging")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(0)),
                                ctx -> setExperience(ctx, SkillType.FORAGING))))
                        .then(Commands.literal("mining")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(0)),
                                ctx -> setExperience(ctx, SkillType.MINING))))
                        .then(Commands.literal("combat")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(0)),
                                ctx -> setExperience(ctx, SkillType.COMBAT)))))
                    .then(Commands.literal("remove")
                        .then(Commands.literal("farming")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(1)),
                                ctx -> removeExperience(ctx, SkillType.FARMING))))
                        .then(Commands.literal("fishing")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(1)),
                                ctx -> removeExperience(ctx, SkillType.FISHING))))
                        .then(Commands.literal("foraging")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(1)),
                                ctx -> removeExperience(ctx, SkillType.FORAGING))))
                        .then(Commands.literal("mining")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(1)),
                                ctx -> removeExperience(ctx, SkillType.MINING))))
                        .then(Commands.literal("combat")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("amount", IntegerArgumentType.integer(1)),
                                ctx -> removeExperience(ctx, SkillType.COMBAT))))))

                // 金币相关
                .then(Commands.literal("money")
                    .then(Commands.literal("add")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("amount", IntegerArgumentType.integer(1)),
                            PlayerDataCommand::addMoney)))
                    .then(Commands.literal("remove")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("amount", IntegerArgumentType.integer(1)),
                            PlayerDataCommand::removeMoney)))
                    .then(Commands.literal("set")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("amount", IntegerArgumentType.integer(0)),
                            PlayerDataCommand::setMoney))))

                // 能量相关
                .then(Commands.literal("energy")
                    .then(Commands.literal("add")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("amount", FloatArgumentType.floatArg(0)),
                            PlayerDataCommand::addEnergy)))
                    .then(Commands.literal("consume")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("amount", FloatArgumentType.floatArg(0)),
                            PlayerDataCommand::consumeEnergy)))
                    .then(CommandTargets.executesWithTarget(
                        Commands.literal("cure"),
                        PlayerDataCommand::cureExhaustion)))

                // 生命值相关
                .then(Commands.literal("health")
                    .then(Commands.literal("set")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("amount", IntegerArgumentType.integer(1)),
                            PlayerDataCommand::setHealth)))
                    .then(Commands.literal("setmax")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("amount", IntegerArgumentType.integer(100)),
                            PlayerDataCommand::setMaxHealth))))

                // 今日运气（daily luck）
                .then(Commands.literal("lucky")
                    .then(CommandTargets.executesWithTarget(
                        Commands.literal("get"),
                        PlayerDataCommand::getLucky))
                    .then(Commands.literal("set")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("value", DoubleArgumentType.doubleArg()),
                            PlayerDataCommand::setLucky))))

                // Recipe commands
                .then(Commands.literal("recipe")
                    .then(Commands.literal("crafting")
                        .then(Commands.literal("unlock")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("recipe_id", StringArgumentType.string())
                                    .suggests((ctx, builder) -> {
                                        java.util.List<String> suggestions = getCraftingRecipeSuggestions();
                                        suggestions.add("all");
                                        return net.minecraft.commands.SharedSuggestionProvider.suggest(suggestions, builder);
                                    }),
                                ctx -> unlockRecipeByCategory(ctx, RecipeCatalogData.getCraftingRecipeIds(), "crafting"))))
                        .then(Commands.literal("lock")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("recipe_id", StringArgumentType.string())
                                    .suggests((ctx, builder) -> {
                                        java.util.List<String> suggestions = getCraftingRecipeSuggestions();
                                        suggestions.add("all");
                                        return net.minecraft.commands.SharedSuggestionProvider.suggest(suggestions, builder);
                                    }),
                                ctx -> lockRecipeByCategory(ctx, RecipeCatalogData.getCraftingRecipeIds(), "crafting")))))
                    .then(Commands.literal("cooking")
                        .then(Commands.literal("unlock")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("recipe_id", StringArgumentType.string())
                                    .suggests((ctx, builder) -> {
                                        java.util.List<String> suggestions = getCookingRecipeSuggestions();
                                        suggestions.add("all");
                                        return net.minecraft.commands.SharedSuggestionProvider.suggest(suggestions, builder);
                                    }),
                                ctx -> unlockRecipeByCategory(ctx, RecipeCatalogData.getCookingRecipeIds(), "cooking"))))
                        .then(Commands.literal("lock")
                            .then(CommandTargets.executesWithTarget(
                                Commands.argument("recipe_id", StringArgumentType.string())
                                    .suggests((ctx, builder) -> {
                                        java.util.List<String> suggestions = getCookingRecipeSuggestions();
                                        suggestions.add("all");
                                        return net.minecraft.commands.SharedSuggestionProvider.suggest(suggestions, builder);
                                    }),
                                ctx -> lockRecipeByCategory(ctx, RecipeCatalogData.getCookingRecipeIds(), "cooking")))))
                    .then(Commands.literal("unlock")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("recipe_id", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    java.util.List<String> suggestions = getRecipeSuggestions();
                                    suggestions.add("all");
                                    return net.minecraft.commands.SharedSuggestionProvider.suggest(suggestions, builder);
                                }),
                            PlayerDataCommand::unlockRecipe)))
                    .then(Commands.literal("lock")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("recipe_id", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    java.util.List<String> suggestions = getRecipeSuggestions();
                                    suggestions.add("all");
                                    return net.minecraft.commands.SharedSuggestionProvider.suggest(suggestions, builder);
                                }),
                            PlayerDataCommand::lockRecipe))))

                .then(Commands.literal("unlock-source")
                    .then(Commands.literal("apply")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("source_id", StringArgumentType.string())
                                .suggests((ctx, builder) ->
                                    net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        UnlockSourceData.getSourceIds(),
                                        builder
                                    )),
                            PlayerDataCommand::applyUnlockSource))))

                // 职业相关（调试 + 原版分支选择流程）
                .then(Commands.literal("profession")
                    .then(CommandTargets.executesWithTarget(
                        Commands.literal("list"),
                        PlayerDataCommand::listProfessions))
                    .then(CommandTargets.executesWithTarget(
                        Commands.literal("pending"),
                        PlayerDataCommand::showPendingProfessions))
                    .then(Commands.literal("choose")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("profession", StringArgumentType.word())
                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(getProfessionSuggestions(), builder)),
                            PlayerDataCommand::choosePendingProfession)))
                    .then(Commands.literal("grant")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("profession", StringArgumentType.word())
                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(getProfessionSuggestions(), builder)),
                            PlayerDataCommand::grantProfession)))
                    .then(Commands.literal("revoke")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("profession", StringArgumentType.word())
                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(getProfessionSuggestions(), builder)),
                            PlayerDataCommand::revokeProfession)))
                    .then(CommandTargets.executesWithTarget(
                        Commands.literal("clear"),
                        PlayerDataCommand::clearProfessions))
                    .then(CommandTargets.executesWithTarget(
                        Commands.literal("repair"),
                        PlayerDataCommand::repairProfessionChoices)))

                // 重置数据
                .then(CommandTargets.executesWithTarget(
                    Commands.literal("reset"),
                    PlayerDataCommand::resetPlayerData))
            )
        );
    }

    @SuppressWarnings("null")
    private static int unlockRecipeByCategory(CommandContext<CommandSourceStack> ctx, java.util.List<String> categoryRecipeIds, String categoryLabel) {
        ServerPlayer player = CommandTargets.resolve(ctx);
        if (player == null) return 0;

        String recipeId = StringArgumentType.getString(ctx, "recipe_id");
        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        boolean changed = false;
        if ("all".equalsIgnoreCase(recipeId)) {
            for (String key : categoryRecipeIds) {
                if (data.unlockRecipe(key)) {
                    changed = true;
                }
            }
        } else {
            if (!categoryRecipeIds.contains(recipeId)) {
                ctx.getSource().sendFailure(Component.literal("Unknown " + categoryLabel + " recipe: " + recipeId));
                return 0;
            }
            changed = data.unlockRecipe(recipeId);
        }

        if (changed) {
            syncPlayerData(player, data);
            ctx.getSource().sendSuccess(() -> Component.literal("Unlocked " + categoryLabel + " recipe(s): " + recipeId), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal(categoryLabel + " recipe(s) already unlocked or invalid: " + recipeId));
            return 0;
        }
    }

    @SuppressWarnings("null")
    private static int lockRecipeByCategory(CommandContext<CommandSourceStack> ctx, java.util.List<String> categoryRecipeIds, String categoryLabel) {
        ServerPlayer player = CommandTargets.resolve(ctx);
        if (player == null) return 0;

        String recipeId = StringArgumentType.getString(ctx, "recipe_id");
        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        boolean changed = false;
        if ("all".equalsIgnoreCase(recipeId)) {
            for (String key : categoryRecipeIds) {
                if (data.lockRecipe(key)) {
                    changed = true;
                }
            }
        } else {
            if (!categoryRecipeIds.contains(recipeId)) {
                ctx.getSource().sendFailure(Component.literal("Unknown " + categoryLabel + " recipe: " + recipeId));
                return 0;
            }
            changed = data.lockRecipe(recipeId);
        }

        if (changed) {
            syncPlayerData(player, data);
            ctx.getSource().sendSuccess(() -> Component.literal("Locked " + categoryLabel + " recipe(s): " + recipeId), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal(categoryLabel + " recipe(s) not unlocked or invalid: " + recipeId));
            return 0;
        }
    }

    @SuppressWarnings("null")
    private static int unlockRecipe(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = CommandTargets.resolve(ctx);
        if (player == null) return 0;
        
        String recipeId = StringArgumentType.getString(ctx, "recipe_id");
        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        boolean changed = false;
        if ("all".equalsIgnoreCase(recipeId)) {
            for (String key : RecipeCatalogData.getAllKnownRecipeIds()) {
                if (data.unlockRecipe(key)) {
                    changed = true;
                }
            }
        } else {
            changed = data.unlockRecipe(recipeId);
        }
        
        if (changed) {
            syncPlayerData(player, data);
            ctx.getSource().sendSuccess(() -> Component.literal("Unlocked recipe(s): " + recipeId), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("Recipe(s) already unlocked or invalid: " + recipeId));
            return 0;
        }
    }

    @SuppressWarnings("null")
    private static int lockRecipe(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = CommandTargets.resolve(ctx);
        if (player == null) return 0;
        
        String recipeId = StringArgumentType.getString(ctx, "recipe_id");
        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        boolean changed = false;
        if ("all".equalsIgnoreCase(recipeId)) {
            for (String key : RecipeCatalogData.getAllKnownRecipeIds()) {
                if (data.lockRecipe(key)) {
                    changed = true;
                }
            }
        } else {
            changed = data.lockRecipe(recipeId);
        }
        
        if (changed) {
            syncPlayerData(player, data);
            ctx.getSource().sendSuccess(() -> Component.literal("Locked recipe(s): " + recipeId), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("Recipe(s) not unlocked or invalid: " + recipeId));
            return 0;
        }
    }

    @SuppressWarnings("null")
    private static int applyUnlockSource(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = CommandTargets.resolve(ctx);
        if (player == null) return 0;

        String sourceId = StringArgumentType.getString(ctx, "source_id");
        boolean changed = PlayerStardewDataAPI.applyUnlockSource(player, sourceId);
        if (changed) {
            ctx.getSource().sendSuccess(() -> Component.literal("Applied unlock source: " + sourceId), true);
            return 1;
        }

        ctx.getSource().sendFailure(Component.literal("No unlock applied (unknown source or already unlocked): " + sourceId));
        return 0;
    }

    private static int getLucky(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;

        StardewTimeManager timeManager = StardewTimeManager.get();
        double luck = PlayerStardewDataAPI.getDailyLuck(player);

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.lucky.info",
            String.format("%.3f", luck),
            timeManager.getCurrentYear(),
            timeManager.getCurrentSeason(),
            timeManager.getCurrentDay()
        ), false);

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.lucky.range",
            String.format("%.2f", LUCK_MIN),
            String.format("%.2f", LUCK_MAX)
        ), false);

        return 1;
    }

    @SuppressWarnings("null")
    private static int setLucky(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;

        double value = DoubleArgumentType.getDouble(context, "value");
        if (value < LUCK_MIN || value > LUCK_MAX) {
            context.getSource().sendFailure(Component.translatable(
                "stardewcraft.command.lucky.out_of_range",
                String.format("%.2f", LUCK_MIN),
                String.format("%.2f", LUCK_MAX)
            ));
            return 0;
        }

        StardewTimeManager timeManager = StardewTimeManager.get();
        PlayerStardewDataAPI.setDailyLuckForToday(player, value);

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.lucky.set",
            String.format("%.3f", value),
            timeManager.getCurrentYear(),
            timeManager.getCurrentSeason(),
            timeManager.getCurrentDay()
        ), false);

        return 1;
    }
    
    /**
     * 显示玩家信息
     */
    private static int showPlayerInfo(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        
        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        
        context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.player.info"), false);
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.health.info", data.getHealth(), data.getMaxHealth()), false);
        context.getSource().sendSuccess(() -> Component.translatable(
            data.isExhausted() ? "stardewcraft.command.energy.info_exhausted" : "stardewcraft.command.energy.info",
            String.format("%.1f", data.getEnergy()), data.getMaxEnergy()), false);
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.money.info", PlayerStardewDataAPI.getMoney(player)), false);
        
        context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.skill.header"), false);
        for (SkillType skill : SkillType.values()) {
            int level = data.getSkillLevel(skill);
            int exp = data.getSkillExperience(skill);
            int nextExp = data.getExpForNextLevel(skill);
            float progress = data.getLevelProgress(skill);
            
            context.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.skill.detail", 
                skill.getDisplayName(), level, exp, nextExp, String.format("%.1f", progress * 100)), false);
        }
        
        if (!data.getProfessions().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.profession.header"), false);
            for (int profId : data.getProfessions()) {
                ProfessionType prof = ProfessionType.fromId(profId);
                if (prof != null) {
                    context.getSource().sendSuccess(() -> Component.translatable(
                        "stardewcraft.command.profession.detail", prof.getDisplayName()), false);
                }
            }
        }

        if (data.hasPendingProfessionChoices()) {
            context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.profession.pending_header"), false);
            int index = 1;
            for (PlayerStardewData.ProfessionChoicePrompt prompt : data.getPendingProfessionChoices()) {
                SkillType skill = prompt.skill();
                int level = prompt.level();
                Component options = getChoiceOptionsText(skill, level, data);
                int currentIndex = index;
                context.getSource().sendSuccess(() -> Component.translatable(
                    "stardewcraft.command.profession.pending_entry",
                    currentIndex, skill.getDisplayName(), level, options), false);
                index++;
            }
        }
        
        return 1;
    }

    private static java.util.List<String> getProfessionSuggestions() {
        java.util.List<String> suggestions = new java.util.ArrayList<>();
        for (ProfessionType profession : ProfessionType.values()) {
            suggestions.add(profession.getName());
        }
        return suggestions;
    }

    private static java.util.List<String> getRecipeSuggestions() {
        return new java.util.ArrayList<>(RecipeCatalogData.getAllKnownRecipeIds());
    }

    private static java.util.List<String> getCraftingRecipeSuggestions() {
        return new java.util.ArrayList<>(RecipeCatalogData.getCraftingRecipeIds());
    }

    private static java.util.List<String> getCookingRecipeSuggestions() {
        return new java.util.ArrayList<>(RecipeCatalogData.getCookingRecipeIds());
    }

    private static ProfessionType parseProfession(CommandContext<CommandSourceStack> context) {
        String input = StringArgumentType.getString(context, "profession");
        for (ProfessionType profession : ProfessionType.values()) {
            if (profession.getName().equalsIgnoreCase(input)) {
                return profession;
            }
        }
        return null;
    }

    private static Component getChoiceOptionsText(SkillType skill, int level, PlayerStardewData data) {
        if (level == 5) {
            ProfessionType[] options = ProfessionType.getLevel5Options(skill);
            return Component.literal(options[0].getName() + " | " + options[1].getName());
        }
        if (level == 10) {
            ProfessionType level5 = null;
            for (int professionId : data.getProfessions()) {
                ProfessionType profession = ProfessionType.fromId(professionId);
                if (profession != null && profession.getSkillType() == skill && profession.getRequiredLevel() == 5) {
                    level5 = profession;
                    break;
                }
            }
            if (level5 != null) {
                ProfessionType[] options = ProfessionType.getLevel10Options(skill, level5);
                return Component.literal(options[0].getName() + " | " + options[1].getName());
            }
        }
        return Component.translatable("stardewcraft.command.profession.no_options");
    }

    private static int listProfessions(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;

        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        if (data.getProfessions().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.profession.none_selected"), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.profession.selected_header"), false);
        for (int professionId : data.getProfessions()) {
            ProfessionType profession = ProfessionType.fromId(professionId);
            if (profession != null) {
                context.getSource().sendSuccess(() -> Component.literal(
                    "- " + profession.getName() + " (" + profession.getSkillType().getName() + " Lv." + profession.getRequiredLevel() + ")"
                ), false);
            }
        }
        return 1;
    }

    private static int showPendingProfessions(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;

        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        if (!data.hasPendingProfessionChoices()) {
            context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.profession.none_pending"), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.profession.pending_queue_header"), false);
        int index = 1;
        for (PlayerStardewData.ProfessionChoicePrompt prompt : data.getPendingProfessionChoices()) {
            SkillType skill = prompt.skill();
            int level = prompt.level();
            int currentIndex = index;
            Component options = getChoiceOptionsText(skill, level, data);
            context.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.profession.pending_entry",
                currentIndex, skill.getDisplayName(), level, options), false);
            index++;
        }
        return 1;
    }

    private static int choosePendingProfession(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;

        ProfessionType profession = parseProfession(context);
        if (profession == null) {
            sendFailure(context.getSource(), "stardewcraft.command.profession.unknown");
            return 0;
        }

        boolean success = PlayerStardewDataAPI.choosePendingProfession(player, profession);
        if (!success) {
            sendFailure(context.getSource(), "stardewcraft.command.profession.choose_failed");
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.profession.chosen", profession.getDisplayName()), true);
        return 1;
    }

    private static int grantProfession(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;

        ProfessionType profession = parseProfession(context);
        if (profession == null) {
            sendFailure(context.getSource(), "stardewcraft.command.profession.unknown");
            return 0;
        }

        boolean changed = PlayerStardewDataAPI.addProfession(player, profession);
        if (!changed) {
            sendFailure(context.getSource(), "stardewcraft.command.profession.already_owned");
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.profession.granted", profession.getDisplayName()), true);
        return 1;
    }

    private static int revokeProfession(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;

        ProfessionType profession = parseProfession(context);
        if (profession == null) {
            sendFailure(context.getSource(), "stardewcraft.command.profession.unknown");
            return 0;
        }

        boolean changed = PlayerStardewDataAPI.removeProfession(player, profession);
        if (!changed) {
            sendFailure(context.getSource(), "stardewcraft.command.profession.not_owned");
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.profession.revoked", profession.getDisplayName()), true);
        return 1;
    }

    private static int clearProfessions(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;

        boolean changed = PlayerStardewDataAPI.clearProfessions(player);
        if (!changed) {
            sendFailure(context.getSource(), "stardewcraft.command.profession.nothing_to_clear");
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.profession.cleared"), true);
        return 1;
    }

    private static int repairProfessionChoices(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;

        PlayerStardewDataAPI.repairMissingProfessionChoices(player);
        context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.profession.repaired"), false);
        return 1;
    }

    @SuppressWarnings("null")
    private static void sendFailure(CommandSourceStack source, String key) {
        source.sendFailure(Component.translatable(key));
    }
    
    /**
     * 添加经验
     */
    private static int addExperience(CommandContext<CommandSourceStack> context, SkillType skill) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        
        int amount = IntegerArgumentType.getInteger(context, "amount");
        boolean leveledUp = PlayerStardewDataAPI.addExperience(player, skill, amount);
        
        int newLevel = PlayerStardewDataAPI.getSkillLevel(player, skill);
        int newExp = PlayerStardewDataAPI.getSkillExperience(player, skill);
        
        if (leveledUp) {
            context.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.exp.pending_level_up", newLevel), true);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.exp.added", amount, skill.getDisplayName(), newExp), false);
        }
        
        return 1;
    }
    
    /**
     * 设置经验
     */
    private static int setExperience(CommandContext<CommandSourceStack> context, SkillType skill) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        
        int amount = IntegerArgumentType.getInteger(context, "amount");
        PlayerStardewDataAPI.setExperience(player, skill, amount);
        
        int newLevel = PlayerStardewDataAPI.getSkillLevel(player, skill);
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.exp.set", skill.getDisplayName(), amount, newLevel), false);
        
        return 1;
    }
    
    /**
     * 移除经验
     */
    private static int removeExperience(CommandContext<CommandSourceStack> context, SkillType skill) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        
        int amount = IntegerArgumentType.getInteger(context, "amount");
        PlayerStardewDataAPI.removeExperience(player, skill, amount);
        
        int newLevel = PlayerStardewDataAPI.getSkillLevel(player, skill);
        int newExp = PlayerStardewDataAPI.getSkillExperience(player, skill);
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.exp.removed", amount, skill.getDisplayName(), newExp, newLevel), false);
        
        return 1;
    }
    
    /**
     * 添加金币
     */
    private static int addMoney(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        
        int amount = IntegerArgumentType.getInteger(context, "amount");
        PlayerStardewDataAPI.addMoney(player, amount);
        
        int newMoney = PlayerStardewDataAPI.getMoney(player);
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.money.added", amount, newMoney), false);
        
        // 同步到客户端
        syncPlayerData(player, PlayerDataManager.getPlayerData(player));
        
        return 1;
    }
    
    /**
     * 移除金币
     */
    @SuppressWarnings("null")
    private static int removeMoney(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        
        int amount = IntegerArgumentType.getInteger(context, "amount");
        boolean success = PlayerStardewDataAPI.removeMoney(player, amount);
        
        if (success) {
            int newMoney = PlayerStardewDataAPI.getMoney(player);
            context.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.money.removed", amount, newMoney), false);
            
            // 同步到客户端
            syncPlayerData(player, PlayerDataManager.getPlayerData(player));
        } else {
            context.getSource().sendFailure(Component.translatable("stardewcraft.command.money.insufficient"));
        }
        
        return success ? 1 : 0;
    }
    
    /**
     * 设置金币
     */
    private static int setMoney(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        
        int amount = IntegerArgumentType.getInteger(context, "amount");
        PlayerStardewDataAPI.setMoney(player, amount);
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.money.set", amount), false);
        
        return 1;
    }
    
    /**
     * 添加能量
     */
    private static int addEnergy(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        
        float amount = FloatArgumentType.getFloat(context, "amount");
        PlayerStardewDataAPI.restoreEnergy(player, amount);
        
        float newEnergy = PlayerStardewDataAPI.getEnergy(player);
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.energy.added", String.format("%.1f", amount), String.format("%.1f", newEnergy)), false);
        
        return 1;
    }
    
    /**
     * 消耗能量
     */
    private static int consumeEnergy(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        
        float amount = FloatArgumentType.getFloat(context, "amount");
        PlayerStardewDataAPI.consumeEnergy(player, amount);
        
        float newEnergy = PlayerStardewDataAPI.getEnergy(player);
        boolean exhausted = PlayerStardewDataAPI.isExhausted(player);
        
        if (exhausted) {
            context.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.energy.consumed_exhausted", amount, newEnergy), false);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.energy.consumed", amount, newEnergy), false);
        }
        
        return 1;
    }
    
    /**
     * 治愈疲惫
     */
    private static int cureExhaustion(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        
        PlayerStardewDataAPI.cureExhaustion(player);
        context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.energy.cured"), false);
        
        return 1;
    }
    
    /**
     * 设置生命值
     */
    private static int setHealth(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        
        int amount = IntegerArgumentType.getInteger(context, "amount");
        PlayerStardewDataAPI.setHealth(player, amount);
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.health.set", amount), false);
        
        return 1;
    }
    
    /**
     * 设置最大生命值
     */
    private static int setMaxHealth(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        
        int amount = IntegerArgumentType.getInteger(context, "amount");
        PlayerStardewDataAPI.setMaxHealth(player, amount);
        
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.health.setmax", amount), false);
        
        return 1;
    }
    
    /**
     * 重置玩家数据 — 完全格式化所有模组相关数据
     */
    private static int resetPlayerData(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;

        UUID uuid = player.getUUID();
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) return 0;
        var overworld = server.overworld();
        int cleared = 0;

        // 1. 主数据（技能、金钱、能量、职业、邮件、已知配方 等）
        PlayerDataManager.get().removePlayerData(uuid);
        PlayerStardewData freshData = PlayerDataManager.get().getOrCreateData(uuid);
        cleared++;

        // 2. NPC 好感度
        try { NpcFriendshipDataManager.get(overworld).clearPlayer(uuid); cleared++; } catch (Exception ignored) {}

        // 3. 事件已看记录
        try { EventSeenData.get().clearSeen(uuid); cleared++; } catch (Exception ignored) {}

        // 4. 矿洞奖励领取
        try { MineRewardClaimManager.get(overworld).clearPlayer(uuid); cleared++; } catch (Exception ignored) {}

        // 5. 矿井进度
        try { MiningDataManager.clearPlayerData(player); cleared++; } catch (Exception ignored) {}

        // 6. 农场权限
        try { FarmPermissionManager.get().clearAllForOwner(uuid); cleared++; } catch (Exception ignored) {}

        // 7. 农场实例
        try { FarmInstanceRegistry.get().deleteFarm(uuid); cleared++; } catch (Exception ignored) {}

        // 8. 玩家 persistentData 中的模组标签
        int tagCount = clearModPersistentTags(player);

        // 9. 同步到客户端
        syncPlayerData(player, freshData);

        // 10. 同步事件已看数据（清空后）到客户端，否则客户端缓存仍认为剧情已看过
        try {
            PacketDistributor.sendToPlayer(player, new SyncEventSeenPayload(List.of()));
        } catch (Exception ignored) {}

        // 11. 同步 NPC 好感度（清空后）到客户端
        try {
            PacketDistributor.sendToPlayer(player, new SyncNpcFriendshipOverviewPayload(List.of()));
        } catch (Exception ignored) {}

        // 12. 同步农场权限（清空后）到客户端
        try {
            FarmPermSyncPayload.sendToPlayer(player);
        } catch (Exception ignored) {}

        int finalCleared = cleared;
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.player.reset_complete",
            com.stardew.craft.player.PlayerDisplayName.get(player), finalCleared, tagCount), true);

        return 1;
    }

    /**
     * 清除玩家 persistentData 中所有 Stardew / stardewcraft_ 开头的标签。
     * 返回移除的标签数量。
     */
    private static int clearModPersistentTags(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        List<String> toRemove = new ArrayList<>();
        for (String key : data.getAllKeys()) {
            if (key.startsWith("Stardew") || key.startsWith("stardewcraft_")) {
                toRemove.add(key);
            }
        }
        for (String key : toRemove) {
            data.remove(key);
        }
        return toRemove.size();
    }
}
