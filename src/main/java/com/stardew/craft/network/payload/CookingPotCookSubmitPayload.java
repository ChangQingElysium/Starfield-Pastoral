package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.CookingPotBlock;
import com.stardew.craft.blockentity.FridgeBlockEntity;
import com.stardew.craft.cooking.service.CookingPotService;
import com.stardew.craft.cooking.service.VanillaCookingRecipeData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record CookingPotCookSubmitPayload(String recipeItemId, int craftCount) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<CookingPotCookSubmitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "cooking_pot_cook_submit"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, CookingPotCookSubmitPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.recipeItemId, 128);
                buf.writeVarInt(payload.craftCount);
            },
            buf -> new CookingPotCookSubmitPayload(buf.readUtf(128), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(CookingPotCookSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            String recipeId = payload.recipeItemId == null ? "" : payload.recipeItemId.trim();
            ResourceLocation recipeLoc = recipeId.indexOf(':') >= 0
                    ? ResourceLocation.tryParse(recipeId)
                    : ResourceLocation.tryBuild(StardewCraft.MODID, recipeId);
            var definition = recipeLoc == null
                    ? java.util.Optional.<com.stardew.craft.api.v1.production.StardewCookingRecipeDefinition>empty()
                    : VanillaCookingRecipeData.getDefinition(recipeLoc);
            if (recipeLoc == null || definition.isEmpty()) {
                player.sendSystemMessage(Component.translatable("stardewcraft.cooking.invalid_recipe"));
                return;
            }

            String storageId = VanillaCookingRecipeData.storageId(recipeLoc);
            if (!com.stardew.craft.player.PlayerStardewDataAPI.getData(player).isRecipeUnlocked(storageId)) {
                player.sendSystemMessage(Component.translatable("stardewcraft.cooking.recipe_locked"));
                return;
            }

            List<com.stardew.craft.api.v1.production.StardewCookingIngredient> requirements =
                    VanillaCookingRecipeData.getRequirements(recipeLoc);
            List<FridgeBlockEntity> nearbyFridges = findNearbyFridges(player);
            int count = resolveCraftCount(payload.craftCount, player.getInventory(), nearbyFridges, requirements);
            if (count <= 0) {
                player.sendSystemMessage(Component.translatable("stardewcraft.cooking.invalid_recipe"));
                return;
            }

            for (var req : requirements) {
                int required = req.count() * count;
                Predicate<ItemStack> matcher = stack -> VanillaCookingRecipeData.matches(stack, req);
                if (countMatching(player.getInventory(), matcher) + countMatchingFridges(nearbyFridges, matcher) < required) {
                    player.sendSystemMessage(Component.translatable(
                            "stardewcraft.cooking.missing_ingredient",
                            VanillaCookingRecipeData.describe(req),
                            required
                    ));
                    return;
                }
            }

            for (var req : requirements) {
                int required = req.count() * count;
                Predicate<ItemStack> matcher = stack -> VanillaCookingRecipeData.matches(stack, req);
                int remain = consumeMatching(player.getInventory(), matcher, required);
                if (remain > 0) {
                    consumeMatchingFridges(nearbyFridges, matcher, remain);
                }
            }

            ItemStack output = VanillaCookingRecipeData.getOutputStack(recipeLoc, count);
            moveOutputToCursorFirst(player, output);
            if (!output.isEmpty()) {
                boolean added = player.getInventory().add(output);
                if (!added) {
                    player.drop(output, false);
                }
            }
            PlayerStardewDataAPI.recordRecipeCrafted(player, storageId, count);

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, CookingPotIngredientAvailabilityPayload.fromPlayer(player));
        });
    }

    private static int resolveCraftCount(int requestedCount,
                                         Inventory inventory,
                                         List<FridgeBlockEntity> fridges,
                                         List<com.stardew.craft.api.v1.production.StardewCookingIngredient> requirements) {
        if (requestedCount == -1) {
            return computeMaxCrafts(inventory, fridges, requirements);
        }
        return Mth.clamp(requestedCount, 1, 5);
    }

    private static int computeMaxCrafts(Inventory inventory,
                                        List<FridgeBlockEntity> fridges,
                                        List<com.stardew.craft.api.v1.production.StardewCookingIngredient> requirements) {
        if (requirements.isEmpty()) {
            return 1;
        }

        int maxCrafts = Integer.MAX_VALUE;
        for (var req : requirements) {
            if (req.count() <= 0) {
                continue;
            }
            Predicate<ItemStack> matcher = stack -> VanillaCookingRecipeData.matches(stack, req);
            int available = countMatching(inventory, matcher) + countMatchingFridges(fridges, matcher);
            int byThisReq = available / req.count();
            maxCrafts = Math.min(maxCrafts, byThisReq);
        }

        if (maxCrafts == Integer.MAX_VALUE) {
            return 0;
        }
        return Math.max(0, maxCrafts);
    }

    @SuppressWarnings("null")
    private static void moveOutputToCursorFirst(ServerPlayer player, ItemStack output) {
        ItemStack carried = player.containerMenu.getCarried();

        if (carried.isEmpty()) {
            int move = Math.min(output.getCount(), output.getMaxStackSize());
            if (move > 0) {
                ItemStack onCursor = output.copyWithCount(move);
                                player.containerMenu.setCarried(onCursor);
                player.connection.send(new ClientboundContainerSetSlotPacket(-1, player.containerMenu.getStateId(), -1, onCursor));
                output.shrink(move);
            }
            player.containerMenu.broadcastChanges();
            return;
        }

        if (!ItemStack.isSameItemSameComponents(carried, output)) {
            return;
        }

        int space = Math.max(0, carried.getMaxStackSize() - carried.getCount());
        if (space <= 0) {
            return;
        }

        int move = Math.min(space, output.getCount());
        if (move <= 0) {
            return;
        }

        carried.grow(move);
        output.shrink(move);
                player.containerMenu.setCarried(carried);
        player.connection.send(new ClientboundContainerSetSlotPacket(-1, player.containerMenu.getStateId(), -1, carried));
        player.containerMenu.broadcastChanges();
    }

    private static int countMatching(Inventory inventory, Predicate<ItemStack> matcher) {
        int total = 0;
        for (ItemStack stack : inventory.items) {
            if (matcher.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int consumeMatching(Inventory inventory, Predicate<ItemStack> matcher, int amount) {
        int remain = amount;
        for (ItemStack stack : inventory.items) {
            if (remain <= 0) {
                break;
            }
            if (!matcher.test(stack)) {
                continue;
            }
            int take = Math.min(remain, stack.getCount());
            stack.shrink(take);
            remain -= take;
        }
        inventory.setChanged();
        return remain;
    }

    private static int countMatchingFridges(List<FridgeBlockEntity> fridges, Predicate<ItemStack> matcher) {
        int total = 0;
        for (FridgeBlockEntity fridge : fridges) {
            for (int slot = 0; slot < fridge.getContainerSize(); slot++) {
                ItemStack stack = fridge.getItem(slot);
                if (!stack.isEmpty() && matcher.test(stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static int consumeMatchingFridges(List<FridgeBlockEntity> fridges, Predicate<ItemStack> matcher, int amount) {
        int remain = amount;
        for (FridgeBlockEntity fridge : fridges) {
            boolean modified = false;
            for (int slot = 0; slot < fridge.getContainerSize(); slot++) {
                if (remain <= 0) {
                    break;
                }
                ItemStack stack = fridge.getItem(slot);
                if (stack.isEmpty() || !matcher.test(stack)) {
                    continue;
                }
                int take = Math.min(remain, stack.getCount());
                stack.shrink(take);
                remain -= take;
                modified = true;
            }
            if (modified) {
                fridge.setChanged();
            }
            if (remain <= 0) {
                return 0;
            }
        }
        return remain;
    }

    private static List<FridgeBlockEntity> findNearbyFridges(ServerPlayer player) {
        if (!player.getPersistentData().contains(CookingPotService.LAST_COOKING_POT_POS_TAG)) {
            return List.of();
        }
        if (player.level().isClientSide) {
            return List.of();
        }

        BlockPos potPos = BlockPos.of(player.getPersistentData().getLong(CookingPotService.LAST_COOKING_POT_POS_TAG));
        var level = player.serverLevel();
        BlockState state = level.getBlockState(potPos);
        if (!state.is(ModBlocks.COOKING_POT.get())) {
            return List.of();
        }
        if (state.hasProperty(CookingPotBlock.PART) && state.getValue(CookingPotBlock.PART) == CookingPotBlock.Part.EXTENSION) {
            potPos = CookingPotBlock.getMainPos(potPos, state);
        }

        Set<BlockPos> seen = new HashSet<>();
        List<FridgeBlockEntity> result = new ArrayList<>();
        for (BlockPos scanPos : BlockPos.betweenClosed(potPos.offset(-2, -2, -2), potPos.offset(2, 2, 2))) {
            BlockEntity be = level.getBlockEntity(scanPos);
            if (!(be instanceof FridgeBlockEntity fridge)) {
                continue;
            }
            if (seen.add(fridge.getBlockPos())) {
                result.add(fridge);
            }
        }
        return result;
    }
}
