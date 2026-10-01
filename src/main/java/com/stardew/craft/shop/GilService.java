package com.stardew.craft.shop;

import com.stardew.craft.port.PortItemData;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Gil owns rewards; the wall owns progress. Neither route participates in friendship. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class GilService {
    public static final double X = 112.0, Y = 60, Z = -147.5;
    public static final float YAW = 90;
    private static final Set<UUID> TALKED_THIS_VISIT = new HashSet<>();
    private static final Set<UUID> AFTER_DIALOGUE = new HashSet<>();
    private GilService() {}

    public static boolean inGuild(ServerPlayer player) {
        return player.level().dimension().equals(ModDimensions.STARDEW_VALLEY)
                && player.getX() >= 101 && player.getX() < 114
                && player.getY() >= 59 && player.getY() < 65
                && player.getZ() >= -158 && player.getZ() < -141;
    }

    public static boolean canReach(ServerPlayer player) {
        return inGuild(player) && player.distanceToSqr(X, Y, Z) <= 36;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!inGuild(player)) { TALKED_THIS_VISIT.remove(player.getUUID()); AFTER_DIALOGUE.remove(player.getUUID()); }
            else {
                if (player.tickCount % 20 == 0) GuildBoardPlacement.replaceLegacyNotices(player.serverLevel());
                var data = PlayerDataManager.getPlayerData(player);
                data.addMailFlag("guildMember");
                for (String goal : data.getClaimedSlayerRewards()) data.addMailFlag("Gil_" + goal);
            }
        }
    }

    public static void holdPosition(StardewNpcEntity npc) {
        npc.setNoAi(true);
        npc.getNavigation().stop();
        npc.setDeltaMovement(0, 0, 0);
        if (npc.distanceToSqr(X, Y, Z) > 1.0e-6) npc.moveTo(X, Y, Z, YAW, 0);
        npc.setYRot(YAW); npc.setYBodyRot(YAW); npc.setYHeadRot(YAW);
    }

    public static InteractionResult interact(ServerPlayer player) {
        if (!canReach(player)) return InteractionResult.PASS;
        boolean talked = !TALKED_THIS_VISIT.add(player.getUUID());
        var data = PlayerDataManager.getPlayerData(player);
        if (data.getMonsterKills("FlameSpirits") >= 150 && !data.hasMailFlag("Gil_Telephone")) {
            for (var farmer : PlayerDataManager.get().getAllPlayerData().values()) farmer.addMailFlag("Gil_Telephone");
            AFTER_DIALOGUE.add(player.getUUID());
            PacketDistributor.sendToPlayer(player, new OpenNpcDialogueScreenPayload("gil", "stardewcraft.gil.telephone", 0));
            return InteractionResult.SUCCESS;
        }
        if (rewardGoals(player).stream().anyMatch(g -> available(player, g.goalKey()) && g.hasReward())) {
            player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new GilRewardMenu(id, inventory), Component.translatable("entity.stardewcraft.npc.gil")));
        } else {
            PacketDistributor.sendToPlayer(player, new OpenNpcDialogueScreenPayload("gil",
                    talked ? "stardewcraft.gil.snoring" : "stardewcraft.gil.come_back_later", 0));
        }
        return InteractionResult.SUCCESS;
    }

    public static void afterDialogue(ServerPlayer player, String npcId) {
        if ("gil".equals(npcId) && AFTER_DIALOGUE.remove(player.getUUID()) && canReach(player)
                && rewardGoals(player).stream().anyMatch(g -> available(player, g.goalKey()) && g.hasReward())) {
            player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new GilRewardMenu(id, inventory), Component.translatable("entity.stardewcraft.npc.gil")));
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        TALKED_THIS_VISIT.remove(event.getEntity().getUUID()); AFTER_DIALOGUE.remove(event.getEntity().getUUID());
    }

    public static boolean available(ServerPlayer player, String goalKey) {
        var goal = MonsterSlayerGoalRegistry.getGoal(goalKey);
        var data = PlayerDataManager.getPlayerData(player);
        return !data.hasClaimedSlayerReward(goalKey) && !data.hasMailFlag("Gil_" + goalKey)
                && (data.getGuildRewardClaims().contains(goalKey)
                    || goal != null && data.getMonsterKills(goalKey) >= goal.requiredKills());
    }

    public static void markTaken(ServerPlayer player, String goalKey) {
        var data = PlayerDataManager.getPlayerData(player);
        data.claimSlayerReward(goalKey);
        data.addMailFlag("Gil_" + goalKey);
    }

    /** A static icon only. No public query/action is executed while rendering a reward. */
    public static ItemStack rewardItem(MonsterSlayerGoalRegistry.SlayerGoal goal) {
        if (!goal.hasReward()) return ItemStack.EMPTY;
        if (goal.rewardPreview().isPresent()) {
            var icon = new ItemStack(BuiltInRegistries.ITEM.get(goal.rewardPreview().get()));
            if (!icon.isEmpty()) return icon;
        }
        for (var action : goal.rewards()) {
            var stack = directItem(action);
            if (!stack.isEmpty()) return stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize()));
        }
        var icon = new ItemStack(net.minecraft.world.item.Items.PAPER);
        PortItemData.set(icon, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.translatable(goal.translationKey()));
        return icon;
    }

    private static ItemStack directItem(com.stardew.craft.api.v1.action.StardewAction action) {
        if (!action.type().equals(new ResourceLocation("stardewcraft:add_item"))) return ItemStack.EMPTY;
        var encoded = com.stardew.craft.port.PortDataResults.getOrThrow(StardewActions.CODEC.encodeStart(JsonOps.INSTANCE, action)).getAsJsonObject().getAsJsonObject("data");
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(encoded.get("item").getAsString())),
                encoded.has("count") ? encoded.get("count").getAsInt() : 1);
    }

    public static java.util.List<MonsterSlayerGoalRegistry.SlayerGoal> rewardGoals(ServerPlayer player) {
        var goals = new java.util.LinkedHashMap<String, MonsterSlayerGoalRegistry.SlayerGoal>();
        MonsterSlayerGoalRegistry.getAllGoals().forEach(g -> goals.put(g.goalKey(), g));
        var journal = PlayerDataManager.getPlayerData(player).getGuildRewardClaims();
        for (var key : journal.getAllKeys()) {
            try {
                var entry = journal.getCompound(key);
                var actions = com.stardew.craft.port.PortDataResults.getOrThrow(StardewActions.CODEC.listOf().parse(JsonOps.INSTANCE,
                        com.google.gson.JsonParser.parseString(entry.getString("Actions"))));
                goals.put(key, new MonsterSlayerGoalRegistry.SlayerGoal(key, entry.getString("Translation"), 1, java.util.List.of(), actions));
            } catch (RuntimeException ex) { StardewCraft.LOGGER.error("[Gil] Cannot decode pending reward {}", key, ex); }
        }
        return java.util.List.copyOf(goals.values());
    }

    private static final Set<UUID> CLAIMING = new HashSet<>();
    /** Commit successful actions once, and persist the remaining suffix for retry after an addon failure. */
    public static boolean claim(ServerPlayer player, MonsterSlayerGoalRegistry.SlayerGoal expected) {
        if (!canReach(player) || !available(player, expected.goalKey()) || !CLAIMING.add(player.getUUID())) return false;
        try {
            var data = PlayerDataManager.getPlayerData(player);
            var journal = data.getGuildRewardClaims();
            var entry = journal.getCompound(expected.goalKey());
            java.util.List<com.stardew.craft.api.v1.action.StardewAction> actions;
            if (journal.contains(expected.goalKey())) {
                actions = com.stardew.craft.port.PortDataResults.getOrThrow(StardewActions.CODEC.listOf().parse(JsonOps.INSTANCE,
                        com.google.gson.JsonParser.parseString(entry.getString("Actions"))));
            } else {
                if (!expected.equals(MonsterSlayerGoalRegistry.getGoal(expected.goalKey()))) return false;
                actions = expected.rewards();
                entry.putString("Actions", com.stardew.craft.port.PortDataResults.getOrThrow(StardewActions.CODEC.listOf().encodeStart(JsonOps.INSTANCE, actions)).toString());
                entry.putString("Translation", expected.translationKey());
            }
            if (actions.isEmpty()) return false;
            int next = entry.getInt("Next");
            var capacity = new net.minecraft.world.SimpleContainer(36);
            for (int i = 0; i < 36; i++) capacity.setItem(i, player.getInventory().getItem(i).copy());
            for (int i = next; i < actions.size(); i++) {
                var stack = directItem(actions.get(i));
                if (!stack.isEmpty() && !capacity.addItem(stack).isEmpty()) return false;
            }
            journal.put(expected.goalKey(), entry); data.setGuildRewardClaims(journal);
            for (int i = next; i < actions.size(); i++) {
                var result = StardewActions.execute(actions.get(i), com.stardew.craft.api.v1.action.StardewActionContext.forPlayer(player));
                if (result.result().isEmpty() || !result.result().get().success()) {
                    StardewCraft.LOGGER.error("[Gil] Reward {} action {} failed; successful prefix will not repeat: {}", expected.goalKey(), i, result);
                    player.inventoryMenu.broadcastChanges(); return false;
                }
                entry.putInt("Next", i + 1); journal.put(expected.goalKey(), entry); data.setGuildRewardClaims(journal);
            }
            markTaken(player, expected.goalKey());
            journal.remove(expected.goalKey()); data.setGuildRewardClaims(journal);
            player.inventoryMenu.broadcastChanges();
            return true;
        } catch (RuntimeException ex) {
            StardewCraft.LOGGER.error("[Gil] Failed to claim reward {}", expected.goalKey(), ex); return false;
        } finally { CLAIMING.remove(player.getUUID()); }
    }
}
