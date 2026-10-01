package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.workbench.WorkbenchEntry;
import com.stardew.craft.workbench.WorkbenchRecipeManager;
import com.stardew.craft.workbench.WorkbenchType;
import net.minecraft.core.registries.BuiltInRegistries;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: player requests crafting at a workbench.
 */
@SuppressWarnings("null")
public record WorkbenchCraftPayload(
    int typeId,
    String targetItemId,
    int count
) implements CustomPacketPayload {

    public static final Type<WorkbenchCraftPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "workbench_craft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorkbenchCraftPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,         WorkbenchCraftPayload::typeId,
            ByteBufCodecs.STRING_UTF8, WorkbenchCraftPayload::targetItemId,
            ByteBufCodecs.INT,         WorkbenchCraftPayload::count,
            WorkbenchCraftPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(WorkbenchCraftPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            WorkbenchType wbType = WorkbenchType.fromId(payload.typeId());
            int requestCount = Math.max(1, Math.min(payload.count(), 999));

            // Validate target item
            ResourceLocation targetRl;
            try { targetRl = new ResourceLocation(payload.targetItemId()); }
            catch (Exception e) { sendResult(player, false, 0, 0, 0); return; }

            WorkbenchEntry entry = WorkbenchRecipeManager.findEntry(wbType, targetRl);
            if (entry == null) {
                sendResult(player, false, 0, 0, 0);
                return;
            }

            Item targetItem = BuiltInRegistries.ITEM.get(targetRl);
            if (targetItem == null || targetItem == Items.AIR) {
                sendResult(player, false, 0, 0, 0);
                return;
            }

            if (wbType == WorkbenchType.TEMPLATE) {
                int crafted = com.stardew.craft.workbench.TemplateWorkbenchCrafting.craft(player, targetRl, payload.count());
                Item material = BuiltInRegistries.ITEM.get(new ResourceLocation(entry.inputItemId(wbType)));
                sendResult(player, crafted > 0, countItem(player, material), 0, crafted);
                return;
            }

            // Count available materials
            ResourceLocation inputRl = new ResourceLocation(wbType.getInputItemId());
            Item inputItem = BuiltInRegistries.ITEM.get(inputRl);
            int inputCount = countItem(player, inputItem);

            int bonusCount = 0;
            Item bonusItem = null;
            if (wbType.hasBonus()) {
                ResourceLocation bonusRl = new ResourceLocation(wbType.getBonusItemId());
                bonusItem = BuiltInRegistries.ITEM.get(bonusRl);
                if (bonusItem != null && bonusItem != Items.AIR) {
                    bonusCount = countItem(player, bonusItem);
                }
            }

            // Total effective material = input + bonus * multiplier
            int totalEffective = inputCount + bonusCount * wbType.getBonusMultiplier();
            int costPerCraft = entry.cost();
            int maxCraftable = totalEffective / costPerCraft;
            int actualCount = Math.min(requestCount, maxCraftable);

            if (actualCount <= 0) {
                sendResult(player, false, inputCount, bonusCount, 0);
                return;
            }

            // Check inventory space
            int outputPerCraft = entry.outputCount();
            int totalOutput = actualCount * outputPerCraft;
            int maxStack = targetItem.getDefaultMaxStackSize();
            totalOutput = Math.min(totalOutput, maxStack * 36); // sanity cap

            // Deduct materials: prefer bonus first (hardwood is worth more)
            int totalCost = actualCount * costPerCraft;
            int remainingCost = totalCost;

            // Use bonus items first
            if (bonusItem != null && bonusCount > 0) {
                int bonusToUse = Math.min(bonusCount, remainingCost / wbType.getBonusMultiplier());
                if (bonusToUse > 0) {
                    removeItem(player, bonusItem, bonusToUse);
                    remainingCost -= bonusToUse * wbType.getBonusMultiplier();
                }
            }

            // Use normal input for the rest
            if (remainingCost > 0) {
                removeItem(player, inputItem, remainingCost);
            }

            // Give output
            ItemStack output = new ItemStack(targetItem, totalOutput);
            if (!player.getInventory().add(output)) {
                player.drop(output, false);
            }

            // Send result
            int newInputCount = countItem(player, inputItem);
            int newBonusCount = bonusItem != null ? countItem(player, bonusItem) : 0;
            sendResult(player, true, newInputCount, newBonusCount, totalOutput);
        });
    }

    private static void sendResult(ServerPlayer player, boolean success, int remaining, int remainingBonus, int crafted) {
        PacketDistributor.sendToPlayer(player,
            new WorkbenchCraftResultPayload(success, remaining, remainingBonus, crafted));
    }

    private static int countItem(ServerPlayer player, Item item) {
        if (item == null || item == Items.AIR) return 0;
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void removeItem(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }
}
