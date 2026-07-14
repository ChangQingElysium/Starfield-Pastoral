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
import com.stardew.craft.secretnote.SecretNoteRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
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
 * 此 payload 通过 {@link ClientContentSyncService} 在玩家登录和 {@code /reload} 时发送，
 * 客户端收到后重放 JSON 解析逻辑。
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
        String professionsJson,
        String secretNotesJson
) implements CustomPacketPayload {
    static final int MAX_DOCUMENT_BYTES = 4 * 1024 * 1024;
    static final int MAX_PAYLOAD_BYTES = 16 * 1024 * 1024;

    public static final Type<DataRegistrySyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "data_registry_sync")
    );

    public DataRegistrySyncPayload {
        artisanJson = objectDocument(artisanJson);
        cookingJson = objectDocument(cookingJson);
        craftingJson = objectDocument(craftingJson);
        preservesJson = objectDocument(preservesJson);
        fishingJson = objectDocument(fishingJson);
        npcEventsJson = objectDocument(npcEventsJson);
        unlockSourcesJson = objectDocument(unlockSourcesJson);
        festivalsJson = objectDocument(festivalsJson);
        masteryRewardsJson = objectDocument(masteryRewardsJson);
        locationsJson = objectDocument(locationsJson);
        professionsJson = objectDocument(professionsJson);
        secretNotesJson = objectDocument(secretNotesJson);
    }

    public static final StreamCodec<ByteBuf, DataRegistrySyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DataRegistrySyncPayload decode(ByteBuf buf) {
            if (buf.readableBytes() > MAX_PAYLOAD_BYTES) {
                throw new DecoderException("Stardew content sync payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
            }
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
            String secretNotes = readLargeString(buf);
            return new DataRegistrySyncPayload(
                    artisan, cooking, crafting, preserves, fishing, npcEvents, unlockSources, festivals,
                    masteryRewards, locations, professions, secretNotes);
        }

        @Override
        public void encode(ByteBuf buf, DataRegistrySyncPayload payload) {
            int startIndex = buf.writerIndex();
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
            writeLargeString(buf, payload.secretNotesJson);
            int encodedBytes = buf.writerIndex() - startIndex;
            if (encodedBytes > MAX_PAYLOAD_BYTES) {
                throw new EncoderException("Stardew content sync payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
            }
        }
    };

    /** 写入不受 32767 字符限制的 UTF-8 字符串 */
    private static void writeLargeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_DOCUMENT_BYTES) {
            throw new EncoderException("Stardew content sync document exceeds " + MAX_DOCUMENT_BYTES + " bytes");
        }
        VarInt.write(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    /** 读取不受 32767 字符限制的 UTF-8 字符串 */
    private static String readLargeString(ByteBuf buf) {
        int len = VarInt.read(buf);
        if (len < 0 || len > MAX_DOCUMENT_BYTES) {
            throw new DecoderException("Invalid Stardew content sync document length: " + len);
        }
        if (len > buf.readableBytes()) {
            throw new DecoderException("Truncated Stardew content sync document: expected " + len
                    + " bytes but only " + buf.readableBytes() + " remain");
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String objectDocument(String json) {
        return json == null || json.isBlank() ? "{}" : json;
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
            ArtisanRecipeDataManager.applyFromJson(payload.artisanJson);
            VanillaCookingRecipeData.applyFromJson(payload.cookingJson);
            StardewCraftingRecipeData.applyFromJson(payload.craftingJson);
            PreservesIngredientDataManager.applyFromJson(payload.preservesJson);
            FishingDataManager.applyFromJson(payload.fishingJson);
            NpcDataRegistry.applyEventsFromJson(payload.npcEventsJson);
            UnlockSourceData.applyFromJson(payload.unlockSourcesJson);
            FestivalRegistry.applyFromJson(payload.festivalsJson);
            MasteryRewardRegistry.applyFromJson(payload.masteryRewardsJson);
            InteriorRegionRegistry.applyFromJson(payload.locationsJson);
            ProfessionData.applyFromJson(payload.professionsJson);
            SecretNoteRegistry.applyFromJson(payload.secretNotesJson);
            com.stardew.craft.client.ClientContentRefreshHooks.onSyncedRegistriesChanged();
            StardewCraft.LOGGER.info("[DATA-SYNC] Received data registry sync from server");
        });
    }

    /**
     * Captures the currently committed server-side registry documents.
     */
    public static DataRegistrySyncPayload current() {
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
        String secretNotes = SecretNoteRegistry.getCachedJson();
        return new DataRegistrySyncPayload(
                artisan, cooking, crafting, preserves, fishing, npcEvents, unlockSources, festivals,
                masteryRewards, locations, professions, secretNotes);
    }

    /**
     * Sends the currently committed snapshot to one player.
     */
    public static void sendFullSync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, current());
    }
}
