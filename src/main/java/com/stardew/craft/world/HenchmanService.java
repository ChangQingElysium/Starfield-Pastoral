package com.stardew.craft.world;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.NpcVisibilityPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.quest.QuestManager;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Per-player implementation of the Witch's Swamp henchman story gate. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class HenchmanService {
    public static final String NPC_ID = "henchman";
    public static final String INTRO_FLAG = "Henchman1";
    public static final String GONE_FLAG = "henchmanGone";
    public static final String MOVE_PENDING_FLAG = "henchmanMovePending";
    public static final String QUEST_ID = "27";

    public static final AABB LOCKED_AREA = new AABB(39.0D, 44.0D, -257.0D, 68.0D, 59.0D, -234.0D);
    public static final Vec3 NORTH_EXIT = new Vec3(51.5D, 48.0D, -233.5D);

    private static final String GATE_ID = "witch_swamp_henchman";
    private static final ResourceLocation STRANGE_BUN_ID =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "strange_bun");

    private HenchmanService() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && PlayerDataManager.getPlayerData(player).hasMailFlag(MOVE_PENDING_FLAG)) {
            finishMovingAside(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())) {
            return;
        }

        repairCompletedQuestFlag(player);
        boolean locked = !hasMovedAside(player) && LOCKED_AREA.intersects(player.getBoundingBox());
        PlayerAreaEvictionService.enforce(player, GATE_ID, locked, NORTH_EXIT, null);
    }

    public static boolean hasMovedAside(ServerPlayer player) {
        return PlayerDataManager.getPlayerData(player).hasMailFlag(GONE_FLAG);
    }

    public static InteractionResult handleInteraction(ServerPlayer player,
                                                      StardewNpcEntity henchman,
                                                      InteractionHand hand) {
        if (hasMovedAside(player)) {
            return InteractionResult.SUCCESS;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.hasMailFlag(MOVE_PENDING_FLAG)) {
            showDialogue(player, henchman, 5);
            return InteractionResult.SUCCESS;
        }
        if (!data.hasMailFlag(INTRO_FLAG)) {
            data.addMailFlag(INTRO_FLAG);
            QuestManager quests = QuestManager.of(player);
            if (quests != null && !quests.hasQuest(QUEST_ID) && !quests.isQuestCompleted(QUEST_ID)) {
                quests.acceptQuest(QUEST_ID, player);
            }
            PlayerDataEventHandler.syncPlayerData(player, data);
            showDialogue(player, henchman, 1);
            return InteractionResult.SUCCESS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            showDialogue(player, henchman, 2);
        } else if (held.is(ModItems.VOID_MAYONNAISE.get())) {
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            data.addMailFlag(MOVE_PENDING_FLAG);
            PlayerDataEventHandler.syncPlayerData(player, data);
            player.playNotifySound(ModSounds.COIN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            showDialogue(player, henchman, 5);
        } else if (STRANGE_BUN_ID.equals(BuiltInRegistries.ITEM.getKey(held.getItem()))) {
            showDialogue(player, henchman, 4);
        } else {
            showDialogue(player, henchman, 3);
        }
        return InteractionResult.SUCCESS;
    }

    public static void onDialogueClosed(ServerPlayer player, String npcId) {
        if (NPC_ID.equalsIgnoreCase(npcId)
                && PlayerDataManager.getPlayerData(player).hasMailFlag(MOVE_PENDING_FLAG)) {
            finishMovingAside(player);
        }
    }

    private static void showDialogue(ServerPlayer player, StardewNpcEntity henchman, int line) {
        // Facing state is shared by the one server entity, so never store a player's
        // dialogue callback on it: simultaneous multiplayer interactions must all answer.
        henchman.facePlayerTemporarily(player, 60, null);
        PacketDistributor.sendToPlayer(player,
                new OpenNpcDialogueScreenPayload(NPC_ID, "stardewcraft.npc.henchman." + line, 0));
    }

    private static void repairCompletedQuestFlag(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.hasMailFlag(GONE_FLAG)) {
            return;
        }
        QuestManager quests = QuestManager.of(player);
        if (quests != null && quests.isQuestCompleted(QUEST_ID)) {
            data.addMailFlag(GONE_FLAG);
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
    }

    private static void finishMovingAside(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!data.hasMailFlag(MOVE_PENDING_FLAG) || data.hasMailFlag(GONE_FLAG)) {
            return;
        }

        QuestManager quests = QuestManager.of(player);
        if (quests != null && quests.hasQuest(QUEST_ID)) {
            // Vanilla removes quest 27 here instead of showing a generic reward toast.
            quests.removeQuest(QUEST_ID, player);
        }
        data.removeMailFlag(MOVE_PENDING_FLAG);
        data.addMailFlag(GONE_FLAG);
        PlayerDataEventHandler.syncPlayerData(player, data);
        PacketDistributor.sendToPlayer(player, new NpcVisibilityPayload(NPC_ID, true));
    }
}
