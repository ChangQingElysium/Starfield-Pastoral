package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.tv.TVChannelData;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: unlocks a cooking recipe learned from the TV cooking channel.
 */
@SuppressWarnings("null")
public record TVRecipeUnlockPayload(String recipeId) implements CustomPacketPayload {

    public static final Type<TVRecipeUnlockPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "tv_recipe_unlock"));

    public static final StreamCodec<FriendlyByteBuf, TVRecipeUnlockPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.recipeId, 128),
            buf -> new TVRecipeUnlockPayload(buf.readUtf(128))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TVRecipeUnlockPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                PlayerStardewData data = PlayerStardewDataAPI.getData(serverPlayer);
                StardewTimeManager timeManager = StardewTimeManager.get();
                int dayKey = timeManager.getAbsoluteDay();
                int dayOfWeek = (timeManager.getCurrentDay() - 1) % 7;
                String expectedRecipeId = TVChannelData.getCookingRecipeIdForDay(serverPlayer, dayKey, dayOfWeek);

                if (data.hasWatchedQueenOfSauceOnDay(dayKey)) {
                    PlayerDataEventHandler.syncPlayerData(serverPlayer, data);
                    return;
                }
                if (payload.recipeId == null || payload.recipeId.isBlank() || !payload.recipeId.equals(expectedRecipeId)) {
                    StardewCraft.LOGGER.warn("[TV] Rejected invalid cooking unlock payload from {}: got={}, expected={}",
                            serverPlayer.getGameProfile().getName(), payload.recipeId, expectedRecipeId);
                    PlayerDataEventHandler.syncPlayerData(serverPlayer, data);
                    return;
                }

                data.markQueenOfSauceWatched(dayKey, expectedRecipeId);
                if (!data.isRecipeUnlocked(expectedRecipeId)) {
                    data.unlockRecipe(expectedRecipeId);
                }
                PlayerDataEventHandler.syncPlayerData(serverPlayer, data);
            }
        });
    }
}
