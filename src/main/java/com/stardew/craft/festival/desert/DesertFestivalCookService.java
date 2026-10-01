package com.stardew.craft.festival.desert;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class DesertFestivalCookService {
    public static final String TARGET_ID = "desert_festival_cook";
    public static final String MARKER_TAG = "sdv_festival_marker:desert_cook";
    public static final BlockPos INTERACTION_MIN = new BlockPos(-246, 65, -184);
    public static final BlockPos INTERACTION_MAX = new BlockPos(-244, 65, -184);

    private static final String CONTEXT_INTRO = "cook_intro";
    private static final String CONTEXT_CONFIRM = "cook_confirm";
    private static final String CONTEXT_INGREDIENT = "cook_ingredient";
    private static final String CONTEXT_SAUCE = "cook_sauce";
    private static final String LAST_COOK_DISH_DAY_STAT = "desertFestivalCookDishDay";
    private static final String YES_ID = "yes";
    private static final String NO_ID = "no";

    private static final Dish[] DISHES = new Dish[] {
        new Dish(0, 1, "earthy_mousse"),
        new Dish(0, 2, "sweet_bean_cake"),
        new Dish(0, 3, "skull_cave_casserole"),
        new Dish(0, 4, "spicy_tacos"),
        new Dish(1, 0, "mountain_chili"),
        new Dish(1, 2, "crystal_cake"),
        new Dish(1, 3, "cave_kebab"),
        new Dish(1, 4, "hot_log"),
        new Dish(2, 0, "sour_salad"),
        new Dish(2, 1, "superfood_cake"),
        new Dish(2, 3, "warrior_smoothie"),
        new Dish(2, 4, "rumpled_fruit_skin"),
        new Dish(3, 0, "calico_pizza"),
        new Dish(3, 1, "stuffed_mushrooms_desert"),
        new Dish(3, 2, "elf_quesadilla"),
        new Dish(3, 4, "nachos_of_the_desert"),
        new Dish(4, 0, "cioppino_desert"),
        new Dish(4, 1, "rainforest_shrimp"),
        new Dish(4, 2, "shrimp_donut"),
        new Dish(4, 3, "smell_of_the_sea"),
        new Dish(4, 4, "desert_gumbo")
    };

    private DesertFestivalCookService() {
    }

    public static void openCook(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!DesertFestivalService.isFestivalOpen()) {
            sendDialogue(player, "stardewcraft.desert_festival.cook.closed");
            return;
        }
        if (hasClaimedToday(player)) {
            sendDialogue(player, "stardewcraft.desert_festival.cook.already_today");
            return;
        }
        sendQuestion(player, CONTEXT_INTRO, -1, "", Component.translatable("stardewcraft.desert_festival.cook.intro"), List.of(
            response(YES_ID, Component.translatable("stardewcraft.ui.yes"), player),
            response(NO_ID, Component.translatable("stardewcraft.ui.no"), player)
        ));
    }

    public static void handleQuestionResponse(ServerPlayer player, String context, int questionIndex, String choiceId) {
        if (player == null || context == null || choiceId == null) {
            return;
        }
        if (CONTEXT_INTRO.equals(context)) {
            if (YES_ID.equals(choiceId)) {
                sendQuestion(player, CONTEXT_CONFIRM, -1, "stardewcraft.desert_festival.cook.intro_yes",
                    Component.translatable("stardewcraft.desert_festival.cook.intro_yes2"), List.of(
                        response(YES_ID, Component.translatable("stardewcraft.ui.yes"), player),
                        response(NO_ID, Component.translatable("stardewcraft.ui.no"), player)
                    ));
            } else {
                sendDialogue(player, "stardewcraft.desert_festival.cook.intro_no");
            }
            return;
        }
        if (CONTEXT_CONFIRM.equals(context)) {
            if (YES_ID.equals(choiceId)) {
                sendIngredientQuestion(player);
            } else {
                sendDialogue(player, "stardewcraft.desert_festival.cook.intro_no");
            }
            return;
        }
        if (CONTEXT_INGREDIENT.equals(context)) {
            int ingredient = parseChoice(choiceId);
            if (ingredient < 0 || ingredient > 4) {
                openCook(player);
                return;
            }
            sendSauceQuestion(player, ingredient);
            return;
        }
        if (CONTEXT_SAUCE.equals(context)) {
            int ingredient = questionIndex;
            int sauce = parseChoice(choiceId);
            if (!isValidDish(ingredient, sauce)) {
                openCook(player);
                return;
            }
            giveDish(player, ingredient, sauce);
        }
    }

    private static void sendIngredientQuestion(ServerPlayer player) {
        List<OpenDesertFestivalQuestionPayload.ResponseOption> responses = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            responses.add(response(Integer.toString(i), Component.translatable(ingredientKey(i)), player));
        }
        sendQuestion(player, CONTEXT_INGREDIENT, -1, "",
            Component.translatable("stardewcraft.desert_festival.cook.intro_yes3"), responses);
    }

    private static void sendSauceQuestion(ServerPlayer player, int ingredient) {
        List<OpenDesertFestivalQuestionPayload.ResponseOption> responses = new ArrayList<>();
        for (int sauce = 0; sauce < 5; sauce++) {
            if (sauce != ingredient || ingredient == 4) {
                responses.add(response(Integer.toString(sauce), Component.translatable(sauceKey(sauce)), player));
            }
        }
        sendQuestion(player, CONTEXT_SAUCE, ingredient, "",
            Component.translatable("stardewcraft.desert_festival.cook.chose_ingredient",
                Component.translatable(ingredientKey(ingredient))), responses);
    }

    private static void giveDish(ServerPlayer player, int ingredient, int sauce) {
        if (!DesertFestivalService.isFestivalOpen()) {
            sendDialogue(player, "stardewcraft.desert_festival.cook.closed");
            return;
        }
        if (hasClaimedToday(player)) {
            sendDialogue(player, "stardewcraft.desert_festival.cook.already_today");
            return;
        }
        Dish dish = dishFor(ingredient, sauce);
        if (dish == null) {
            openCook(player);
            return;
        }
        PlayerStardewDataAPI.setStat(player, LAST_COOK_DISH_DAY_STAT, currentDayKey());
        Item item = ModItems.DESERT_FESTIVAL_COOK_DISHES.get(dish.itemId()).get();
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.inventoryMenu.broadcastChanges();
        com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player, new OpenNpcDialogueScreenPayload(
            "desert_festival",
            doneKey(ingredient, sauce),
            0,
            StardewCraft.MODID + ":" + dish.itemId(),
            false
        ));
    }

    private static boolean isValidDish(int ingredient, int sauce) {
        return ingredient >= 0 && ingredient <= 4
            && sauce >= 0 && sauce <= 4
            && (sauce != ingredient || ingredient == 4)
            && dishFor(ingredient, sauce) != null;
    }

    private static Dish dishFor(int ingredient, int sauce) {
        for (Dish dish : DISHES) {
            if (dish.ingredient() == ingredient && dish.sauce() == sauce) {
                return dish;
            }
        }
        return null;
    }

    private static int parseChoice(String choiceId) {
        try {
            return Integer.parseInt(choiceId);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean hasClaimedToday(ServerPlayer player) {
        return PlayerStardewDataAPI.getStat(player, LAST_COOK_DISH_DAY_STAT) == currentDayKey();
    }

    private static int currentDayKey() {
        return StardewTimeManager.get().getAbsoluteDay();
    }

    private static void sendQuestion(ServerPlayer player, String context, int questionIndex, String preDialogueKey,
                                     Component question, List<OpenDesertFestivalQuestionPayload.ResponseOption> responses) {
        PacketDistributor.sendToPlayer(player, new OpenDesertFestivalQuestionPayload(
            context,
            questionIndex,
            preDialogueKey == null ? "" : preDialogueKey,
            Component.Serializer.toJson(question, player.registryAccess()),
            responses
        ));
    }

    private static OpenDesertFestivalQuestionPayload.ResponseOption response(String id, Component label, ServerPlayer player) {
        return new OpenDesertFestivalQuestionPayload.ResponseOption(id, Component.Serializer.toJson(label, player.registryAccess()));
    }

    private static void sendDialogue(ServerPlayer player, String key) {
        com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player, new OpenNpcDialogueScreenPayload("desert_festival", key, 0));
    }

    private static String ingredientKey(int ingredient) {
        return "stardewcraft.desert_festival.cook.ingredient." + ingredient;
    }

    private static String sauceKey(int sauce) {
        return "stardewcraft.desert_festival.cook.sauce." + sauce;
    }

    private static String doneKey(int ingredient, int sauce) {
        return "stardewcraft.desert_festival.cook.done." + ingredient + "_" + sauce;
    }

    private record Dish(int ingredient, int sauce, String itemId) {
    }
}