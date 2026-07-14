package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.fishing.data.FishingDataManager;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.cooking.service.VanillaCookingRecipeData;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.item.artisan.PreservesIngredientDataManager;
import com.stardew.craft.interior.InteriorRegionRegistry;
import com.stardew.craft.mastery.MasteryRewardRegistry;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.player.UnlockSourceData;
import com.stardew.craft.player.StardewCraftingRecipeData;
import com.stardew.craft.player.ProfessionData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;

/**
 * S→C: 同步所有 DataManager 的原始 JSON 到客户端。
 * <p>
 * 专用服务器场景下，客户端没有 datapack ReloadListener，
 * 导致 ArtisanRecipe/PreservesIngredient/Fishing/NpcEvents 数据为空。
 * 此 payload 在玩家登录时发送，客户端收到后重放 JSON 解析逻辑。
 */
@SuppressWarnings("null")
public record DataRegistrySyncPayload(
        String artisanJson,
        String cookingJson,
        String craftingJson,
        String preservesJson,
        String fishingJson,
        String npcEventsJson,
        String unlockSourcesJson,
        String festivalsJson,
        String masteryRewardsJson,
        String locationsJson,
        String professionsJson
) implements CustomPacketPayload {

    public static final Type<DataRegistrySyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "data_registry_sync")
    );

    public static final StreamCodec<ByteBuf, DataRegistrySyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DataRegistrySyncPayload decode(ByteBuf buf) {
            String artisan = readLargeString(buf);
            String cooking = readLargeString(buf);
            String crafting = readLargeString(buf);
            String preserves = readLargeString(buf);
            String fishing = readLargeString(buf);
            String npcEvents = readLargeString(buf);
            String unlockSources = readLargeString(buf);
            String festivals = readLargeString(buf);
            String masteryRewards = readLargeString(buf);
            String locations = readLargeString(buf);
            String professions = readLargeString(buf);
            return new DataRegistrySyncPayload(
                    artisan, cooking, crafting, preserves, fishing, npcEvents, unlockSources, festivals,
                    masteryRewards, locations, professions);
        }

        @Override
        public void encode(ByteBuf buf, DataRegistrySyncPayload payload) {
            writeLargeString(buf, payload.artisanJson);
            writeLargeString(buf, payload.cookingJson);
            writeLargeString(buf, payload.craftingJson);
            writeLargeString(buf, payload.preservesJson);
            writeLargeString(buf, payload.fishingJson);
            writeLargeString(buf, payload.npcEventsJson);
            writeLargeString(buf, payload.unlockSourcesJson);
            writeLargeString(buf, payload.festivalsJson);
            writeLargeString(buf, payload.masteryRewardsJson);
            writeLargeString(buf, payload.locationsJson);
            writeLargeString(buf, payload.professionsJson);
        }
    };

    /** 写入不受 32767 字符限制的 UTF-8 字符串 */
    private static void writeLargeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        VarInt.write(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    /** 读取不受 32767 字符限制的 UTF-8 字符串 */
    private static String readLargeString(ByteBuf buf) {
        int len = VarInt.read(buf);
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handler: replay JSON parsing for each DataManager.
     */
    public static void handle(DataRegistrySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!payload.artisanJson.isEmpty()) {
                ArtisanRecipeDataManager.applyFromJson(payload.artisanJson);
            }
            if (!payload.cookingJson.isEmpty()) {
                VanillaCookingRecipeData.applyFromJson(payload.cookingJson);
            }
            if (!payload.craftingJson.isEmpty()) {
                StardewCraftingRecipeData.applyFromJson(payload.craftingJson);
            }
            if (!payload.preservesJson.isEmpty()) {
                PreservesIngredientDataManager.applyFromJson(payload.preservesJson);
            }
            if (!payload.fishingJson.isEmpty()) {
                FishingDataManager.applyFromJson(payload.fishingJson);
            }
            if (!payload.npcEventsJson.isEmpty()) {
                NpcDataRegistry.applyEventsFromJson(payload.npcEventsJson);
            }
            if (!payload.unlockSourcesJson.isEmpty()) {
                UnlockSourceData.applyFromJson(payload.unlockSourcesJson);
            }
            if (!payload.festivalsJson.isEmpty()) {
                FestivalRegistry.applyFromJson(payload.festivalsJson);
            }
            if (!payload.masteryRewardsJson.isEmpty()) {
                MasteryRewardRegistry.applyFromJson(payload.masteryRewardsJson);
            }
            if (!payload.locationsJson.isEmpty()) {
                InteriorRegionRegistry.applyFromJson(payload.locationsJson);
            }
            if (!payload.professionsJson.isEmpty()) {
                ProfessionData.applyFromJson(payload.professionsJson);
            }
            StardewCraft.LOGGER.info("[DATA-SYNC] Received data registry sync from server");
        });
    }

    /**
     * 从服务端发送给指定玩家。
     */
    public static void sendFullSync(ServerPlayer player) {
        String artisan = ArtisanRecipeDataManager.getCachedJson();
        String cooking = VanillaCookingRecipeData.getCachedJson();
        String crafting = StardewCraftingRecipeData.getCachedJson();
        String preserves = PreservesIngredientDataManager.getCachedJson();
        String fishing = FishingDataManager.getCachedJson();
        String npcEvents = NpcDataRegistry.getCachedEventsJson();
        String unlockSources = UnlockSourceData.getCachedJson();
        String festivals = FestivalRegistry.getCachedJson();
        String masteryRewards = MasteryRewardRegistry.getCachedJson();
        String locations = InteriorRegionRegistry.getCachedJson();
        String professions = ProfessionData.getCachedJson();
        PacketDistributor.sendToPlayer(player, new DataRegistrySyncPayload(
                artisan, cooking, crafting, preserves, fishing, npcEvents, unlockSources, festivals,
                masteryRewards, locations, professions));
    }
}
