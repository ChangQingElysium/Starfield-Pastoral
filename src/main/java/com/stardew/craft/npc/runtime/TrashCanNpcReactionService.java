package com.stardew.craft.npc.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.emote.EmoteCatalog;
import com.stardew.craft.emote.EmoteType;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.payload.EmoteBroadcastPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.network.payload.SyncNpcFriendshipStatusPayload;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.Locale;

/**
 * Vanilla garbage-can NPC reaction parity.
 * <p>
 * Stardew checks NPCs in a 2D tile radius. In this 3D world, NPC interiors can
 * exist below the outdoor map, so the search is horizontal and layer-limited.
 */
public final class TrashCanNpcReactionService {
    private static final int HORIZONTAL_RADIUS = 7;
    private static final int MAX_VERTICAL_LAYER_DISTANCE = 4;
    private static final int DEFAULT_FRIENDSHIP_DELTA = -25;
    private static final int LINUS_FRIENDSHIP_DELTA = 5;

    private TrashCanNpcReactionService() {
    }

    public static void reactToSearch(ServerPlayer player, BlockPos trashPos) {
        if (player == null || trashPos == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        StardewNpcEntity npc = nearestNpcOnSameLayer(level, trashPos);
        if (npc == null) {
            return;
        }

        String npcId = npc.getNpcId();
        if (npcId == null || npcId.isBlank()) {
            return;
        }
        npcId = npcId.toLowerCase(Locale.ROOT);

        Component npcName = npc.getName();
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.translatable("stardewcraft.chat.trash_can",
                    com.stardew.craft.player.PlayerDisplayName.get(player), npcName),
            false
        );
        if ("linus".equals(npcId)) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("stardewcraft.chat.linus_trash_can"),
                false
            );
        }

        int delta = "linus".equals(npcId) ? LINUS_FRIENDSHIP_DELTA : DEFAULT_FRIENDSHIP_DELTA;
        int points = changeFriendship(player, level, npcId, delta);
        broadcastEmote(npc, emoteIdFor(npcId));
        PacketDistributor.sendToPlayer(player, new OpenNpcDialogueScreenPayload(
            npcId,
            dialogueKeyFor(npcId),
            points
        ));
    }

    private static StardewNpcEntity nearestNpcOnSameLayer(ServerLevel level, BlockPos trashPos) {
        AABB searchBox = new AABB(
            trashPos.getX() - HORIZONTAL_RADIUS,
            trashPos.getY() - MAX_VERTICAL_LAYER_DISTANCE,
            trashPos.getZ() - HORIZONTAL_RADIUS,
            trashPos.getX() + HORIZONTAL_RADIUS + 1,
            trashPos.getY() + MAX_VERTICAL_LAYER_DISTANCE + 1,
            trashPos.getZ() + HORIZONTAL_RADIUS + 1
        );
        return level.getEntitiesOfClass(StardewNpcEntity.class, searchBox, npc -> isWithinHorizontalRadius(npc, trashPos))
            .stream()
            .min(Comparator.comparingDouble(npc -> horizontalDistanceSqr(npc, trashPos)))
            .orElse(null);
    }

    private static boolean isWithinHorizontalRadius(Entity entity, BlockPos trashPos) {
        if (Math.abs(entity.blockPosition().getY() - trashPos.getY()) > MAX_VERTICAL_LAYER_DISTANCE) {
            return false;
        }
        return horizontalDistanceSqr(entity, trashPos) <= HORIZONTAL_RADIUS * HORIZONTAL_RADIUS;
    }

    private static double horizontalDistanceSqr(Entity entity, BlockPos trashPos) {
        double dx = entity.getX() - (trashPos.getX() + 0.5D);
        double dz = entity.getZ() - (trashPos.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    private static int changeFriendship(ServerPlayer player, ServerLevel level, String npcId, int delta) {
        NpcFriendshipDataManager friendship = NpcFriendshipDataManager.get(level);
        NpcFriendshipDataManager.FriendshipState state = friendship.getOrCreate(player.getUUID(), npcId);
        state.addPoints(delta, NpcInteractionService.getMaxFriendshipPointsFor(npcId));
        friendship.setDirty();

        int points = Math.max(0, state.points());
        int dayKey = currentDayKey();
        PacketDistributor.sendToPlayer(player, new SyncNpcFriendshipStatusPayload(
            npcId,
            points,
            Math.max(0, Math.min(14, points / 250)),
            Math.max(0, Math.min(2, state.giftsThisWeek())),
            state.lastGiftDayKey() == dayKey,
            state.lastTalkDayKey() == dayKey
        ));
        return points;
    }

    private static int currentDayKey() {
        StardewTimeManager time = StardewTimeManager.get();
        if (time == null) {
            return 1;
        }
        return (time.getCurrentYear() - 1) * 112 + time.getCurrentSeason() * 28 + time.getCurrentDay();
    }

    private static String emoteIdFor(String npcId) {
        if ("linus".equals(npcId)) {
            return "happy";
        }
        NpcCapabilityProfile profile = NpcDataRegistry.capabilities().get(npcId);
        int age = com.stardew.craft.api.v1.npc.StardewNpcProfiles.resolve(npcId)
                .map(definition -> definition.profile().age())
                .orElse(profile != null
                        ? profile.age()
                        : NpcCapabilityProfile.AGE_ADULT);
        if (age == NpcCapabilityProfile.AGE_CHILD) {
            return "sad";
        }
        if (age == NpcCapabilityProfile.AGE_TEEN) {
            return "question";
        }
        return "angry";
    }

    private static void broadcastEmote(StardewNpcEntity npc, String emoteId) {
        EmoteType emote = EmoteCatalog.byId(emoteId);
        if (emote != null) {
            PacketDistributor.sendToAllPlayers(new EmoteBroadcastPayload(npc.getId(), EmoteCatalog.getBubbleBaseIndex(emote)));
        }
    }

    private static String dialogueKeyFor(String npcId) {
        JsonObject root = NpcDataRegistry.dialogues().get(npcId);
        if (root != null && root.has("entries") && root.get("entries").isJsonObject()) {
            JsonElement custom = root.getAsJsonObject("entries").get("DumpsterDiveComment");
            if (custom != null && custom.isJsonPrimitive()) {
                String key = custom.getAsString();
                if (key != null && !key.isBlank()) {
                    return key;
                }
            }
        }

        NpcCapabilityProfile profile = NpcDataRegistry.capabilities().get(npcId);
        int age = com.stardew.craft.api.v1.npc.StardewNpcProfiles.resolve(npcId)
                .map(definition -> definition.profile().age())
                .orElse(profile != null
                        ? profile.age()
                        : NpcCapabilityProfile.AGE_ADULT);
        if (age == NpcCapabilityProfile.AGE_CHILD) {
            return "stardewcraft.npc.generic.dumpster_dive.child";
        }
        if (age == NpcCapabilityProfile.AGE_TEEN) {
            return "stardewcraft.npc.generic.dumpster_dive.teen";
        }
        return "stardewcraft.npc.generic.dumpster_dive.adult";
    }
}
