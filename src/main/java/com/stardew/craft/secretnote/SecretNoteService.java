package com.stardew.craft.secretnote;

import com.stardew.craft.api.v1.secretnote.StardewSecretNoteDefinition;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.OpenSecretNotePayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.quest.QuestManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;

/** Source-parity rules for magnifying-glass unlock, note generation and reading. */
public final class SecretNoteService {
    public static final String MAGNIFYING_GLASS_FLAG = "HasMagnifyingGlass";
    public static final String MAGNIFYING_GLASS_SPECIAL_ITEM = "stardewcraft:magnifying_glass";
    public static final float FIRST_SECRET_NOTE_CHANCE = 0.80F;
    public static final float LAST_SECRET_NOTE_CHANCE = 0.12F;

    private SecretNoteService() {}

    public static boolean hasMagnifyingGlass(PlayerStardewData data) {
        return data != null && (data.hasMailFlag(MAGNIFYING_GLASS_FLAG)
                || data.hasSpecialItem(MAGNIFYING_GLASS_SPECIAL_ITEM));
    }

    public static boolean grantMagnifyingGlass(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        boolean changed = false;
        if (!data.hasMailFlag(MAGNIFYING_GLASS_FLAG)) {
            data.addMailFlag(MAGNIFYING_GLASS_FLAG);
            changed = true;
        }
        if (!data.hasSpecialItem(MAGNIFYING_GLASS_SPECIAL_ITEM)) {
            data.addSpecialItem(MAGNIFYING_GLASS_SPECIAL_ITEM);
            changed = true;
        }
        if (changed) {
            saveAndSync(player, data);
        }
        return changed;
    }

    public static List<Map.Entry<ResourceLocation, StardewSecretNoteDefinition>> unseenNotes(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        return SecretNoteRegistry.orderedNotes().stream()
                .filter(entry -> entry.getValue().obtainable())
                .filter(entry -> !data.hasSeenSecretNote(entry.getKey().toString()))
                .toList();
    }

    /**
     * SDV GameLocation.tryToCreateUnseenSecretNote: inner 80% -> 12% roll.
     * The caller owns the source-specific outer chance.
     */
    public static ItemStack tryCreateUnseenNote(ServerPlayer player, RandomSource random) {
		if (player == null || random == null) return ItemStack.EMPTY;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!hasMagnifyingGlass(data)) return ItemStack.EMPTY;

        List<Map.Entry<ResourceLocation, StardewSecretNoteDefinition>> unseen = unseenNotes(player);
        int looseNotes = player.getInventory().countItem(ModItems.SECRET_NOTE.get());
        int totalUnseen = unseen.size() - looseNotes;
        if (totalUnseen <= 0) return ItemStack.EMPTY;

        int totalNotes = (int) SecretNoteRegistry.orderedNotes().stream()
                .filter(entry -> entry.getValue().obtainable())
                .count();
		float chance = generationChance(totalUnseen, totalNotes);
        return random.nextFloat() < chance ? new ItemStack(ModItems.SECRET_NOTE.get()) : ItemStack.EMPTY;
    }

	static float generationChance(int totalUnseen, int totalNotes) {
		float fractionRemaining = (float) (totalUnseen - 1) / (float) Math.max(1, totalNotes - 1);
		return LAST_SECRET_NOTE_CHANCE
				+ (FIRST_SECRET_NOTE_CHANCE - LAST_SECRET_NOTE_CHANCE) * fractionRemaining;
	}

    public static ItemStack tryCreateFromSource(ServerPlayer player, RandomSource random, float sourceChance) {
		if (player == null || random == null
				|| !hasMagnifyingGlass(PlayerDataManager.getPlayerData(player))) {
			return ItemStack.EMPTY;
		}
        if (sourceChance < 1.0F && random.nextFloat() >= sourceChance) return ItemStack.EMPTY;
        return tryCreateUnseenNote(player, random);
    }

    /** Resolve and consume one generic note, matching SDV Object.checkForAction for (O)79. */
    public static boolean readOne(ServerPlayer player, ItemStack stack) {
        List<Map.Entry<ResourceLocation, StardewSecretNoteDefinition>> unseen = unseenNotes(player);
        if (unseen.isEmpty()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "stardewcraft.secret_note.none_unseen"));
            return false;
        }

        long seed = player.serverLevel().getSeed()
                ^ player.getUUID().getMostSignificantBits()
                ^ player.getUUID().getLeastSignificantBits()
                ^ (long) unseen.size() * 777L;
        RandomSource selectionRandom = RandomSource.create(seed);
        Map.Entry<ResourceLocation, StardewSecretNoteDefinition> selected =
                unseen.get(selectionRandom.nextInt(unseen.size()));

        if (!discover(player, selected.getKey(), selected.getValue())) return false;

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        open(player, selected.getKey(), selected.getValue());
        return true;
    }

    /** Read a numbered creative-catalog variant without changing generic (O)79 drop behavior. */
    public static boolean readSpecific(ServerPlayer player, ItemStack stack, ResourceLocation noteId) {
        if (noteId == null) return false;
        StardewSecretNoteDefinition definition = SecretNoteRegistry.get(noteId);
        if (definition == null) return false;

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!data.hasSeenSecretNote(noteId.toString()) && !discover(player, noteId, definition)) return false;
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        open(player, noteId, definition);
        return true;
    }

    public static boolean debugDiscover(ServerPlayer player, ResourceLocation noteId) {
        StardewSecretNoteDefinition definition = SecretNoteRegistry.get(noteId);
        if (definition == null) return false;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!data.hasSeenSecretNote(noteId.toString()) && !discover(player, noteId, definition)) return false;
        open(player, noteId, definition);
        return true;
    }

    public static boolean openSeen(ServerPlayer player, ResourceLocation noteId) {
        StardewSecretNoteDefinition definition = SecretNoteRegistry.get(noteId);
        if (definition == null) return false;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!data.hasSeenSecretNote(noteId.toString())) return false;
        open(player, noteId, definition);
        return true;
    }

    private static void open(ServerPlayer player, ResourceLocation id, StardewSecretNoteDefinition definition) {
        PacketDistributor.sendToPlayer(player, new OpenSecretNotePayload(
                id.toString(), definition.displayNumber(), definition.text(), definition.imageIndex()));
    }

    private static boolean discover(ServerPlayer player, ResourceLocation id,
                                    StardewSecretNoteDefinition definition) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!data.markSecretNoteSeen(id.toString())) return false;
        for (StardewSecretNoteDefinition.GiftReveal reveal : definition.giftReveals()) {
            data.revealGiftTaste(reveal.npc(), reveal.objectId());
        }
        applyVanillaFirstReadEffect(player, definition);
        saveAndSync(player, data);
        return true;
    }

    private static void applyVanillaFirstReadEffect(ServerPlayer player, StardewSecretNoteDefinition definition) {
        if (!definition.obtainable()) return;
        QuestManager quests = QuestManager.of(player);
        if (quests == null) return;
        if (definition.vanillaNumber() == 10) {
            PlayerStardewData data = PlayerDataManager.getPlayerData(player);
            if (!data.hasMailFlag("qiCave") && !quests.hasQuest("30") && !quests.isQuestCompleted("30")) {
                quests.acceptQuest("30", player);
            }
        } else if (definition.vanillaNumber() == 23
                && !quests.hasQuest("29")
                && !quests.isQuestCompleted("29")) {
            quests.acceptQuest("29", player);
        }
    }

    private static void saveAndSync(ServerPlayer player, PlayerStardewData data) {
        PlayerDataManager.get().savePlayerData(player.getUUID(), data);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }
}
