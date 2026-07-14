package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.player.RecipeCatalogData;
import com.stardew.craft.player.StardewCraftingRecipeData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record CraftingMenuCraftSubmitPayload(String recipeItemId, int craftCount) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<CraftingMenuCraftSubmitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "crafting_menu_craft_submit"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, CraftingMenuCraftSubmitPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.recipeItemId, 128);
                buf.writeVarInt(payload.craftCount);
            },
            buf -> new CraftingMenuCraftSubmitPayload(buf.readUtf(128), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(CraftingMenuCraftSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            String recipeId = normalizeRecipeId(payload.recipeItemId);
            if (recipeId.isBlank() || !RecipeCatalogData.getCraftingRecipeIds().contains(recipeId)) {
                player.sendSystemMessage(Component.translatable("stardewcraft.crafting.invalid_recipe"));
                return;
            }

            if (!PlayerStardewDataAPI.getData(player).isRecipeUnlocked(recipeId)) {
                player.sendSystemMessage(Component.translatable("stardewcraft.crafting.recipe_locked"));
                return;
            }

            List<Ingredient> ingredients = StardewCraftingRecipeData.toExpandedIngredients(recipeId,
                PlayerStardewDataAPI.hasProfession(player, ProfessionType.TRAPPER));
            if (ingredients.isEmpty()) {
                player.sendSystemMessage(Component.translatable("stardewcraft.crafting.invalid_recipe"));
                return;
            }

            ItemStack oneCraftOutput = StardewCraftingRecipeData.getOutputStack(recipeId);
            if (oneCraftOutput.isEmpty()) {
                player.sendSystemMessage(Component.translatable("stardewcraft.crafting.invalid_recipe"));
                return;
            }

            int requestedCraftCount = resolveCraftCount(payload.craftCount);
            int maxCraftable = computeMaxCrafts(player.getInventory(), ingredients, 999);
            int desiredCraftCount = payload.craftCount == -1
                    ? maxCraftable
                    : Math.min(requestedCraftCount, maxCraftable);

            int outputPerCraft = Math.max(1, oneCraftOutput.getCount());
            int maxByCursor = maxCraftsByCursorCapacity(player, oneCraftOutput, outputPerCraft);
            int actualCraftCount = Math.min(desiredCraftCount, maxByCursor);

            if (actualCraftCount <= 0) {
                return;
            }

            int[] before = snapshotCounts(player.getInventory());
            int[] remain = before.clone();
            for (int i = 0; i < actualCraftCount; i++) {
                if (!tryConsumeOneCraft(remain, player.getInventory(), ingredients)) {
                    player.sendSystemMessage(Component.translatable("stardewcraft.crafting.missing_ingredient"));
                    return;
                }
            }
            applyConsumption(player.getInventory(), before, remain);

            int totalOutput = outputPerCraft * actualCraftCount;
            moveOutputToCursor(player, oneCraftOutput, totalOutput);

            PlayerStardewDataAPI.recordRecipeCrafted(player, recipeId, outputPerCraft * actualCraftCount);

            // Quest: recipe crafted
            com.stardew.craft.quest.StardewQuestEvents.fireRecipeCrafted(player, recipeId);
        });
    }

    private static String normalizeRecipeId(String recipeItemId) {
        if (recipeItemId == null) {
            return "";
        }
        String trimmed = recipeItemId.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        ResourceLocation parsed = ResourceLocation.tryParse(trimmed);
        if (parsed != null) {
            return StardewCraftingRecipeData.storageId(parsed);
        }
        return trimmed;
    }

    private static int resolveCraftCount(int requestedCount) {
        if (requestedCount == -1) {
            return -1;
        }
        return Mth.clamp(requestedCount, 1, 9999);
    }

    private static int[] snapshotCounts(Inventory inventory) {
        int size = inventory.items.size();
        int[] counts = new int[size];
        for (int i = 0; i < size; i++) {
            counts[i] = inventory.items.get(i).getCount();
        }
        return counts;
    }

    private static int computeMaxCrafts(Inventory inventory, List<Ingredient> ingredients, int cap) {
        int[] remain = snapshotCounts(inventory);
        int crafts = 0;
        while (crafts < cap && tryConsumeOneCraft(remain, inventory, ingredients)) {
            crafts++;
        }
        return crafts;
    }

    private static boolean tryConsumeOneCraft(int[] remain, Inventory inventory, List<Ingredient> ingredients) {
        for (Ingredient ingredient : ingredients) {
            int chosenSlot = -1;
            for (int slot = 0; slot < inventory.items.size(); slot++) {
                if (remain[slot] <= 0) {
                    continue;
                }
                ItemStack stack = inventory.items.get(slot);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    chosenSlot = slot;
                    break;
                }
            }
            if (chosenSlot < 0) {
                return false;
            }
            remain[chosenSlot]--;
        }
        return true;
    }

    private static void applyConsumption(Inventory inventory, int[] before, int[] after) {
        for (int i = 0; i < inventory.items.size(); i++) {
            int consumed = Math.max(0, before[i] - after[i]);
            if (consumed <= 0) {
                continue;
            }
            ItemStack stack = inventory.items.get(i);
            stack.shrink(consumed);
        }
        inventory.setChanged();
    }

    @SuppressWarnings("null")
    private static int maxCraftsByCursorCapacity(ServerPlayer player, ItemStack prototype, int outputPerCraft) {
        if (outputPerCraft <= 0) {
            return 0;
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            return prototype.getMaxStackSize() / outputPerCraft;
        }
        if (!ItemStack.isSameItemSameComponents(carried, prototype)) {
            return 0;
        }

        int space = Math.max(0, carried.getMaxStackSize() - carried.getCount());
        return space / outputPerCraft;
    }

    @SuppressWarnings("null")
    private static void moveOutputToCursor(ServerPlayer player, ItemStack prototype, int totalOutput) {
        int amount = Math.max(0, totalOutput);
        if (amount <= 0) {
            return;
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            ItemStack onCursor = prototype.copyWithCount(amount);
            player.containerMenu.setCarried(onCursor);
            player.connection.send(new ClientboundContainerSetSlotPacket(-1, player.containerMenu.getStateId(), -1, onCursor));
            player.containerMenu.broadcastChanges();
            return;
        }

        carried.grow(amount);
        player.containerMenu.setCarried(carried);
        player.connection.send(new ClientboundContainerSetSlotPacket(-1, player.containerMenu.getStateId(), -1, carried));
        player.containerMenu.broadcastChanges();
    }
}
