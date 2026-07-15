package com.stardew.craft.book;

import com.stardew.craft.animal.service.AnimalShopService;
import com.stardew.craft.item.StardewBookItem;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.RecipeCatalogData;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.network.payload.ReadBookVisualPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BookService {
    private static final int SKILL_BOOK_XP = 250;
    private static final int REPEAT_TAGGED_XP = 100;
    private static final int REPEAT_ALL_SKILLS_XP = 20;
    private static final int READ_MESSAGE_DELAY_TICKS = 1;
    private static final int READ_FREEZE_TICKS = 21;
    private static final String READ_A_BOOK_FLAG = "read_a_book";
    private static final Map<UUID, Integer> READING_FREEZE = new HashMap<>();
    private static final Map<UUID, Integer> READING_TOKENS = new HashMap<>();
    private static int nextReadingToken = 1;

    private BookService() {
    }

    public static int startReadingVisual(ServerPlayer player, int durationTicks) {
        int freezeTicks = Math.max(READ_FREEZE_TICKS, durationTicks + 1);
        int token = nextReadingToken++;
        if (nextReadingToken == Integer.MAX_VALUE) {
            nextReadingToken = 1;
        }
        READING_FREEZE.put(player.getUUID(), freezeTicks);
        READING_TOKENS.put(player.getUUID(), token);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.BOOK_READ.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                ReadBookVisualPayload.fromPlayer(player, false, durationTicks));
        return token;
    }

    public static void finishReadingVisual(ServerPlayer player) {
        READING_FREEZE.remove(player.getUUID());
        READING_TOKENS.remove(player.getUUID());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                ReadBookVisualPayload.fromPlayer(player, true, 0));
    }

    public static boolean isReadingTokenActive(ServerPlayer player, int token) {
        return READING_TOKENS.getOrDefault(player.getUUID(), -1) == token;
    }

    public static void scheduleBookRead(ServerPlayer player, BookDefinition definition,
                                        InteractionHand hand, int token, int delayTicks) {
        player.server.tell(new TickTask(player.server.getTickCount() + Math.max(1, delayTicks), () -> {
            if (player.isRemoved() || !isReadingTokenActive(player, token)) {
                return;
            }
            ItemStack current = player.getItemInHand(hand);
            if (current.getItem() instanceof StardewBookItem bookItem
                    && bookItem.getDefinition() == definition) {
                readBook(player, definition, current);
                return;
            }
            finishReadingVisual(player);
        }));
    }

    public static void tickReadingFreeze(ServerPlayer player) {
        Integer ticks = READING_FREEZE.get(player.getUUID());
        if (ticks == null) {
            return;
        }
        if (ticks <= 0 || player.isRemoved()) {
            READING_FREEZE.remove(player.getUUID());
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(0.0D, motion.y, 0.0D);
        player.xxa = 0.0F;
        player.zza = 0.0F;
        player.setSprinting(false);
        player.hurtMarked = true;
        READING_FREEZE.put(player.getUUID(), ticks - 1);
    }

    public static void readBook(ServerPlayer player, BookDefinition definition, ItemStack stack) {
        if (definition.kind() == BookDefinition.BookKind.ANIMAL_CATALOGUE) {
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            finishReadingVisual(player);
            AnimalShopService.openForPlayer(player);
            return;
        }

        switch (definition.kind()) {
            case SKILL -> grantSkillBook(player, definition);
            case PURPLE -> grantAllSkills(player, SKILL_BOOK_XP);
            case POWER -> grantPowerBook(player, definition);
            case ANIMAL_CATALOGUE -> {
            }
            case QUEEN_OF_SAUCE -> grantQueenOfSauce(player, definition);
        }

        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        finishReadingVisual(player);
    }

    public static boolean hasRead(ServerPlayer player, BookDefinition definition) {
        return PlayerStardewDataAPI.getStat(player, definition.statKey()) > 0;
    }

    public static boolean hasWellRead(ServerPlayer player) {
        for (BookDefinition definition : BookDefinition.all()) {
            if (definition.wellReadPower() && !hasRead(player, definition)) {
                return false;
            }
        }
        return true;
    }

    private static void grantSkillBook(ServerPlayer player, BookDefinition definition) {
        SkillType skill = definition.skill();
        if (skill != null) {
            boolean leveledUp = PlayerStardewDataAPI.addExperience(player, skill, SKILL_BOOK_XP);
            if (!leveledUp) {
                scheduleMessage(player, Component.translatable(
                        "stardewcraft.book.message.skill",
                        Component.translatable("stardewcraft.skill." + skill.getName())));
            }
        }
    }

    private static void grantPowerBook(ServerPlayer player, BookDefinition definition) {
        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        if (data.getStat(definition.statKey()) <= 0) {
            data.incrementStat(definition.statKey(), 1);
            data.addMailFlag(READ_A_BOOK_FLAG);
            PlayerDataEventHandler.syncPlayerData(player, data);
            scheduleMessage(player, Component.translatable("stardewcraft.book.message.learned_power"));
            if (hasWellRead(player)) {
                scheduleMessage(player, Component.translatable("stardewcraft.book.message.well_read"));
            }
            return;
        }

        if (definition.repeatSkill() != null) {
            PlayerStardewDataAPI.addExperience(player, definition.repeatSkill(), REPEAT_TAGGED_XP);
        } else if (definition.repeatAllSkills()) {
            grantAllSkills(player, REPEAT_ALL_SKILLS_XP);
        }
    }

    private static void grantQueenOfSauce(ServerPlayer player, BookDefinition definition) {
        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        int learned = 0;
        data.incrementStat(definition.statKey(), 1);
        for (String recipeId : RecipeCatalogData.getCookingRecipeIds()) {
            if (data.unlockRecipe(recipeId)) {
                learned++;
            }
        }
        PlayerDataEventHandler.syncPlayerData(player, data);
        scheduleMessage(player, Component.translatable("stardewcraft.book.message.queen", learned));
    }

    private static void grantAllSkills(ServerPlayer player, int amount) {
        for (SkillType skill : SkillType.values()) {
            PlayerStardewDataAPI.addExperience(player, skill, amount);
        }
    }

    private static void scheduleMessage(ServerPlayer player, Component message) {
        player.server.tell(new TickTask(player.server.getTickCount() + READ_MESSAGE_DELAY_TICKS, () -> {
            if (!player.isRemoved()) {
				com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player, message);
            }
        }));
    }
}
