package com.stardew.craft.quest;

import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.List;

/**
 * SDV ItemDeliveryQuest — 物品交付任务
 * 将指定物品交给指定NPC
 */
@SuppressWarnings("null")
public class ItemDeliveryQuest extends StardewQuest {

    private String targetNpc = "";
    private String itemId = "";
    private int number = 1;
    private int numberDelivered;
    private String targetMessage = "";

    public ItemDeliveryQuest() {
        this.questType = TYPE_DELIVERY;
    }

    @Override
    public boolean onItemOfferedToNpc(ServerPlayer player, String npcId, String offeredItemId) {
        if (completed || !accepted) return false;
        if (targetNpc.equals(npcId) && itemId.equals(offeredItemId)) {
            numberDelivered++;
            if (numberDelivered >= number) {
                questComplete(player);
            }
            return true; // SDV: quest intercepts item, skip gift taste processing
        }
        return false;
    }

    @Override
    public boolean matchesItemDelivery(String npcId, String offeredItemId) {
        return accepted && !completed && !destroy
                && targetNpc.equalsIgnoreCase(npcId) && itemId.equalsIgnoreCase(offeredItemId);
    }

    @Override
    public String getDeliveryTargetMessage() {
        return targetMessage;
    }

    @Override
    public void questComplete(ServerPlayer player) {
        if (completed) return;
        super.questComplete(player);
        // SDV: dailyQuest gives +150, story quest gives +255
        if (!targetNpc.isEmpty() && player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            NpcFriendshipDataManager friendshipMgr = NpcFriendshipDataManager.get(serverLevel);
            NpcFriendshipDataManager.FriendshipState fs = friendshipMgr.getOrCreate(player.getUUID(), targetNpc);
            int friendshipBonus = dailyQuest ? 150 : 255;
            friendshipBonus = BookPowerEffects.applyFriendshipGain(PlayerDataManager.getPlayerData(player), friendshipBonus);
            fs.addPoints(friendshipBonus, NpcInteractionService.getMaxFriendshipPointsFor(targetNpc));
            friendshipMgr.setDirty();
        }
    }

    @Override
    public int getCurrentObjectiveCount() { return number > 1 ? numberDelivered : -1; }
    @Override
    public int getTotalObjectiveCount() { return number > 1 ? number : -1; }

    @Override
    public List<Component> getObjectiveComponents() {
        // 有本地化 key → 用动态进度构造（args: [itemKey, npcKey]）
        if (objectiveKey != null && !objectiveKey.isEmpty() && objectiveArgs.size() >= 2) {
            Component itemName = Component.translatable(objectiveArgs.get(0));
            Component npcName = Component.translatable(objectiveArgs.get(1));
            if (number > 1) {
                // 多数量版：再拼一行 "N/M"
                return Collections.singletonList(Component.translatable(
                        "stardewcraft.quest.delivery.objective_multi",
                        String.valueOf(number), itemName, npcName,
                        String.valueOf(numberDelivered)));
            }
            return Collections.singletonList(Component.translatable(
                    objectiveKey, itemName, npcName));
        }
        return super.getObjectiveComponents();
    }

    @Override
    public List<String> getObjectiveDescriptions() {
        List<Component> comps = getObjectiveComponents();
        List<String> out = new java.util.ArrayList<>(comps.size());
        for (Component c : comps) out.add(c.getString());
        return out;
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putString("TargetNpc", targetNpc);
        tag.putString("ItemId", itemId);
        tag.putInt("Number", number);
        tag.putInt("NumberDelivered", numberDelivered);
        tag.putString("TargetMessage", targetMessage);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        targetNpc = tag.getString("TargetNpc");
        itemId = tag.getString("ItemId");
        number = tag.getInt("Number");
        numberDelivered = tag.getInt("NumberDelivered");
        targetMessage = tag.getString("TargetMessage");
    }

    // ─── Getters / Setters ───
    public String getTargetNpc() { return targetNpc; }
    public void setTargetNpc(String targetNpc) { this.targetNpc = targetNpc; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }
    public String getTargetMessage() { return targetMessage; }
    public void setTargetMessage(String targetMessage) { this.targetMessage = targetMessage; }
}
