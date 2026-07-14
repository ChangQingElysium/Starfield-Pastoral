package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.CookingPotBlock;
import com.stardew.craft.blockentity.FridgeBlockEntity;
import com.stardew.craft.cooking.service.CookingPotService;
import com.stardew.craft.cooking.service.VanillaCookingRecipeData;
import com.stardew.craft.api.v1.production.StardewCookingIngredient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record CookingPotIngredientAvailabilityPayload(Map<String, Integer> fridgeTokenCounts) implements CustomPacketPayload {

    public static final Type<CookingPotIngredientAvailabilityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "cooking_pot_ingredient_availability"));

    public static final StreamCodec<FriendlyByteBuf, CookingPotIngredientAvailabilityPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                Map<String, Integer> counts = payload.fridgeTokenCounts == null ? Map.of() : payload.fridgeTokenCounts;
                buf.writeVarInt(counts.size());
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    buf.writeUtf(entry.getKey(), 256);
                    buf.writeVarInt(Math.max(0, entry.getValue()));
                }
            },
            buf -> {
                int size = Math.max(0, buf.readVarInt());
                Map<String, Integer> counts = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    String token = buf.readUtf(256);
                    int count = Math.max(0, buf.readVarInt());
                    counts.put(token, count);
                }
                return new CookingPotIngredientAvailabilityPayload(counts);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CookingPotIngredientAvailabilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.CookingIngredientAvailabilityCache.setFridgeTokenCounts(payload.fridgeTokenCounts));
    }

    public static CookingPotIngredientAvailabilityPayload fromPlayer(ServerPlayer player) {
        List<FridgeBlockEntity> nearbyFridges = findNearbyFridges(player);
        Map<String, StardewCookingIngredient> ingredients = collectAllCookingIngredients();
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<String, StardewCookingIngredient> entry : ingredients.entrySet()) {
            String token = entry.getKey();
            Predicate<ItemStack> matcher = stack -> VanillaCookingRecipeData.matches(stack, entry.getValue());
            int count = countMatchingFridges(nearbyFridges, matcher);
            if (count > 0) {
                counts.put(token, count);
            }
        }
        return new CookingPotIngredientAvailabilityPayload(counts);
    }

    private static Map<String, StardewCookingIngredient> collectAllCookingIngredients() {
        Map<String, StardewCookingIngredient> ingredients = new java.util.LinkedHashMap<>();
        for (ResourceLocation recipeId : VanillaCookingRecipeData.getRecipeIds()) {
            for (StardewCookingIngredient ingredient : VanillaCookingRecipeData.getRequirements(recipeId)) {
                ingredients.putIfAbsent(ingredient.matcherKey(), ingredient);
            }
        }
        return ingredients;
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
