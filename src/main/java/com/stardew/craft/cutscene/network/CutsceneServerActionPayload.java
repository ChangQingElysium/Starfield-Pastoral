package com.stardew.craft.cutscene.network;

import com.mojang.logging.LogUtils;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.quest.QuestManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

/**
 * Client → Server: execute a server-side action from a cutscene command.
 * Supports cutscene state changes which must run on the server.
 */
public record CutsceneServerActionPayload(
        String eventId,
        long sessionId,
        int commandToken,
        String action,
        String value
) implements CustomPacketPayload {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<CutsceneServerActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "cutscene_server_action"));

    public static final StreamCodec<ByteBuf, CutsceneServerActionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CutsceneServerActionPayload decode(ByteBuf buffer) {
            return new CutsceneServerActionPayload(
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer));
        }

        @Override
        public void encode(ByteBuf buffer, CutsceneServerActionPayload payload) {
            ByteBufCodecs.STRING_UTF8.encode(buffer, payload.eventId);
            ByteBufCodecs.VAR_LONG.encode(buffer, payload.sessionId);
            ByteBufCodecs.VAR_INT.encode(buffer, payload.commandToken);
            ByteBufCodecs.STRING_UTF8.encode(buffer, payload.action);
            ByteBufCodecs.STRING_UTF8.encode(buffer, payload.value);
        }
    };

    @SuppressWarnings("null")
    public static void handle(CutsceneServerActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!com.stardew.craft.cutscene.server.ServerCutsceneTracker.authorizeAction(
                    player, payload.sessionId, payload.eventId, payload.commandToken,
                    payload.action, payload.value)) {
                LOGGER.warn("Rejected unauthorized cutscene action {} at {}#{} from {}",
                        payload.action, payload.eventId, payload.commandToken,
                        player.getName().getString());
                return;
            }
            if (LegacyCutsceneActionAdapter.tryExecute(payload.action, payload.value, player)) {
                return;
            }
            switch (payload.action) {
                case "add_quest" -> {
                    QuestManager mgr = QuestManager.of(player);
                    mgr.acceptQuest(payload.value, player);
                    LOGGER.debug("Cutscene added quest {} for {}", payload.value, player.getName().getString());
                }
                case "remove_quest" -> {
                    QuestManager mgr = QuestManager.of(player);
                    if (mgr != null) {
                        mgr.removeQuest(payload.value, player);
                    }
                    LOGGER.debug("Cutscene removed quest {} for {}", payload.value, player.getName().getString());
                }
                case "set_flag" -> {
                    PlayerStardewData data = PlayerDataManager.getPlayerData(player);
                    data.addMailFlag(payload.value);
                    LOGGER.debug("Cutscene set flag '{}' for {}", payload.value, player.getName().getString());
                    com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(player, data);
                    // canReadJunimoText 影响 bundle 界面渲染，需同步到客户端
                    if ("canReadJunimoText".equals(payload.value)) {
                        com.stardew.craft.communitycenter.network.BundleSyncPayload.sendFullSync(player);
                    }
                    if (com.stardew.craft.specialorder.SpecialOrderManager.BOARD_UNLOCK_FLAG.equals(payload.value)) {
                        net.minecraft.server.level.ServerLevel stardewLevel =
                            player.server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
                        if (stardewLevel != null) {
                            com.stardew.craft.specialorder.SpecialOrderBoardInstaller.get(stardewLevel)
                                .ensurePlaced(stardewLevel);
                        }
                        com.stardew.craft.specialorder.SpecialOrderManager.syncState(player);
                    }
                }
                case "grant_rusty_key" -> {
                    com.stardew.craft.sewer.SewerService.grantRustyKey(player, false);
                    LOGGER.debug("Cutscene granted Rusty Key to {}", player.getName().getString());
                }
                case "grant_magnifying_glass" -> {
                    com.stardew.craft.secretnote.SecretNoteService.grantMagnifyingGlass(player);
                    LOGGER.debug("Cutscene granted Magnifying Glass to {}", player.getName().getString());
                }
                case "grant_bear_knowledge" -> {
                    com.stardew.craft.secretnote.SecretNote23Service.grantBearKnowledge(player);
                    LOGGER.debug("Cutscene granted Bear's Knowledge to {}", player.getName().getString());
                }
                case "mark_opened_sewer" -> {
                    com.stardew.craft.sewer.SewerService.markOpenedSewer(player);
                    LOGGER.debug("Cutscene marked sewer opened for {}", player.getName().getString());
                }
                case "add_recipe" -> {
                    PlayerStardewData data = PlayerDataManager.getPlayerData(player);
                    if (data.unlockRecipe(payload.value)) {
                        LOGGER.debug("Cutscene unlocked recipe '{}' for {}",
                                payload.value, player.getName().getString());
                        // markDirty() only flags save; we must push to the client
                        // so JEI / crafting UIs see the new recipe immediately.
                        com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(player, data);
                    }
                }
                case "add_mail_now" -> {
                    com.stardew.craft.mail.MailService.addMail(player, payload.value);
                    PlayerStardewData data = PlayerDataManager.getPlayerData(player);
                    com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(player, data);
                    LOGGER.debug("Cutscene added mail '{}' for {}", payload.value, player.getName().getString());
                }
                case "add_mail_for_tomorrow" -> {
                    com.stardew.craft.mail.MailService.addMailForTomorrow(player, payload.value);
                    PlayerStardewData data = PlayerDataManager.getPlayerData(player);
                    com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(player, data);
                    LOGGER.debug("Cutscene queued mail '{}' for tomorrow for {}", payload.value, player.getName().getString());
                }
                case "apply_unlock_source" -> {
                    boolean changed = com.stardew.craft.player.PlayerStardewDataAPI.applyUnlockSource(player, payload.value);
                    LOGGER.debug("Cutscene applied unlock source '{}' for {} changed={}",
                            payload.value, player.getName().getString(), changed);
                }
                case "set_cave_choice" -> {
                    com.stardew.craft.farm.FarmCaveChoice choice =
                            com.stardew.craft.farm.FarmCaveChoice.fromName(payload.value);
                    if (choice == null) {
                        LOGGER.warn("Cutscene set_cave_choice: unknown value '{}'", payload.value);
                    } else if (!com.stardew.craft.farm.FarmCaveAPI.setCaveChoice(player, choice)) {
                        LOGGER.warn("Cutscene set_cave_choice failed for {} (no farm or not owner)",
                                player.getName().getString());
                    } else {
                        LOGGER.debug("Cutscene set cave choice '{}' for {}",
                                choice.getName(), player.getName().getString());
                    }
                }
                case "door" -> {
                    setDoorOpen(player, payload.value);
                }
                case "add_friendship" -> {
                    // value format: "npc_id:points"
                    String[] parts = payload.value.split(":", 2);
                    if (parts.length == 2) {
                        String npcId = parts[0];
                        int points = Integer.parseInt(parts[1]);
                        var fm = com.stardew.craft.npc.runtime.NpcFriendshipDataManager.get(
                                (net.minecraft.server.level.ServerLevel) player.level());
                        var state = fm.getOrCreate(player.getUUID(), npcId);
                        points = com.stardew.craft.book.BookPowerEffects.applyFriendshipGain(
                            com.stardew.craft.player.PlayerDataManager.getPlayerData(player), points);
                        state.addPoints(points, com.stardew.craft.npc.runtime.NpcInteractionService.getMaxFriendshipPointsFor(npcId));
                        fm.setDirty();
                        com.stardew.craft.npc.runtime.NpcFriendshipRewardService.applyEligibleRewards(player, npcId, state.points());
                        LOGGER.debug("Cutscene added {} friendship to {} for {}", points, npcId,
                                player.getName().getString());
                    }
                }
                case "add_item" -> {
                    // value format: "item_id:count"，item_id 自身含 ':'，按最后一个 ':' 切分
                    int sep = payload.value.lastIndexOf(':');
                    if (sep > 0 && sep < payload.value.length() - 1) {
                        String[] parts = { payload.value.substring(0, sep), payload.value.substring(sep + 1) };
                        try {
                            var rl = ResourceLocation.parse(parts[0]);
                            int count = Integer.parseInt(parts[1]);
                            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
                            if (item != net.minecraft.world.item.Items.AIR) {
                                var stack = new net.minecraft.world.item.ItemStack(item, count);
                                if (!player.getInventory().add(stack)) {
                                    player.drop(stack, false);
                                }
                                LOGGER.debug("Cutscene gave {}x{} to {}", count, rl,
                                        player.getName().getString());
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Cutscene add_item failed: {}", e.getMessage());
                        }
                    }
                }
                case "remove_item" -> {
                    // value format: "item_id:count"，item_id 自身含 ':'，按最后一个 ':' 切分
                    int sep = payload.value.lastIndexOf(':');
                    if (sep > 0 && sep < payload.value.length() - 1) {
                        String[] parts = { payload.value.substring(0, sep), payload.value.substring(sep + 1) };
                        try {
                            var rl = ResourceLocation.parse(parts[0]);
                            int count = Integer.parseInt(parts[1]);
                            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
                            if (item != net.minecraft.world.item.Items.AIR
                                    && player.getInventory().countItem(item) >= count
                                    && removeItems(player, item, count)) {
                                LOGGER.debug("Cutscene removed {}x{} from {}", count, rl,
                                        player.getName().getString());
                            } else {
                                LOGGER.warn("Cutscene remove_item skipped: {} lacks {}x{}",
                                        player.getName().getString(), count, rl);
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Cutscene remove_item failed: {}", e.getMessage());
                        }
                    }
                }
                case "teleport_cc" -> {
                    com.stardew.craft.cutscene.server.ServerCutsceneTracker.markServerMovedPlayer(player);
                    com.stardew.craft.event.InteriorPortalInteractionEvents.handleCCEntryForCutscene(player);
                    // Send the player their CC interior anchor so any anchor-tagged
                    // commands in the cutscene (Part B) resolve to the correct origin.
                    try {
                        net.minecraft.server.level.ServerLevel lvl = player.serverLevel();
                        net.minecraft.core.BlockPos origin = com.stardew.craft.interior.PlayerInteriorAllocator
                                .get(lvl).getCCOrigin(player.getUUID());
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                                new com.stardew.craft.cutscene.network.CutsceneAnchorPayload(
                                        "cc_interior",
                                        origin.getX(), origin.getY(), origin.getZ()));
                    } catch (Exception e) {
                        LOGGER.warn("Failed to send cc_interior anchor: {}", e.getMessage());
                    }
                    LOGGER.debug("Cutscene teleported {} to CC interior", player.getName().getString());
                }
                case "egg_festival_award_complete" -> {
                    com.stardew.craft.cutscene.server.ServerCutsceneTracker.markServerMovedPlayer(player);
                    com.stardew.craft.festival.EggFestivalService.onCutsceneCompleted(player, "egg_festival_award");
                    LOGGER.debug("Cutscene completed Egg Festival award for {}", player.getName().getString());
                }
                case "egg_festival_blackout" -> {
                    com.stardew.craft.cutscene.server.ServerCutsceneTracker.markServerMovedPlayer(player);
                    com.stardew.craft.festival.EggFestivalService.onCutsceneBlackout(player, payload.value);
                    LOGGER.debug("Cutscene prepared Egg Festival {} stage for {}", payload.value, player.getName().getString());
                }
                case "flower_dance_stage" -> {
                    com.stardew.craft.cutscene.server.ServerCutsceneTracker.markServerMovedPlayer(player);
                    com.stardew.craft.festival.FlowerDanceService.onCutsceneStage(player, payload.value);
                    LOGGER.debug("Cutscene prepared Flower Dance {} stage for {}", payload.value, player.getName().getString());
                }
                case "moonlight_jellies_stage" -> {
                    com.stardew.craft.festival.MoonlightJelliesFestivalService.onCutsceneStage(player, payload.value);
                    LOGGER.debug("Cutscene prepared Moonlight Jellies {} stage for {}", payload.value, player.getName().getString());
                }
                case "winter_star_open_gift" -> {
                    com.stardew.craft.festival.WinterStarFestivalService.claimReturnGiftDuringCutscene(player);
                    LOGGER.debug("Cutscene revealed Winter Star return gift for {}",
                            player.getName().getString());
                }
                default -> LOGGER.warn("Unknown cutscene server action: {}", payload.action);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static boolean removeItems(ServerPlayer player, net.minecraft.world.item.Item item, int count) {
        if (count <= 0) return false;
        int remaining = count;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int remove = Math.min(stack.getCount(), remaining);
            stack.shrink(remove);
            remaining -= remove;
        }
        player.inventoryMenu.broadcastChanges();
        return remaining == 0;
    }

    private static void setDoorOpen(ServerPlayer player, String value) {
        int sep = value.lastIndexOf(':');
        if (sep <= 0 || sep >= value.length() - 1) {
            LOGGER.warn("Cutscene door action has invalid payload '{}'", value);
            return;
        }
        String[] coords = value.substring(0, sep).split(",", 3);
        if (coords.length != 3) {
            LOGGER.warn("Cutscene door action has invalid coordinates '{}'", value);
            return;
        }
        try {
            BlockPos pos = new BlockPos(
                    Integer.parseInt(coords[0]),
                    Integer.parseInt(coords[1]),
                    Integer.parseInt(coords[2]));
            boolean open = Boolean.parseBoolean(value.substring(sep + 1));
            BlockState state = player.level().getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock
                    && state.hasProperty(DoorBlock.HALF)
                    && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                pos = pos.below();
                state = player.level().getBlockState(pos);
                if (!(state.getBlock() instanceof DoorBlock)) {
                    LOGGER.warn("Cutscene door action found no lower door at {}", pos.toShortString());
                    return;
                }
            }
            if (!(state.getBlock() instanceof DoorBlock door) || !state.hasProperty(DoorBlock.OPEN)) {
                LOGGER.warn("Cutscene door action found no door at {}", pos.toShortString());
                return;
            }
            if (state.getValue(DoorBlock.OPEN) != open) {
                door.setOpen(player, player.level(), state, pos, open);
            }
            LOGGER.debug("Cutscene set door {} open={} for {}", pos.toShortString(), open,
                    player.getName().getString());
        } catch (Exception e) {
            LOGGER.warn("Cutscene door action failed: {}", e.getMessage());
        }
    }
}
