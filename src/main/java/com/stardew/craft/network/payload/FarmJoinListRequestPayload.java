package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModGameRules;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * C→S: 玩家请求可加入的农场列表。
 */
@SuppressWarnings("null")
public record FarmJoinListRequestPayload() implements CustomPacketPayload {

    public static final Type<FarmJoinListRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "farm_join_list_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FarmJoinListRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new FarmJoinListRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FarmJoinListRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            FarmInstanceRegistry registry = FarmInstanceRegistry.get();
            com.stardew.craft.farm.FarmJoinManager.syncPendingState(
                    player,
                    com.stardew.craft.farm.FarmJoinManager.hasPending(player.getUUID())
            );

            // 已有农场不可加入
            if (registry.hasFarm(player.getUUID())) {
                player.sendSystemMessage(
                        net.minecraft.network.chat.Component.translatable("stardewcraft.farm.join.already_has_farm"));
                return;
            }

            // 构建可加入农场列表（已初始化且未满的农场）
            int maxFarmers = ModGameRules.getMaxFarmersPerFarm(player.server);
            List<FarmListSyncPayload.FarmEntry> entries = new ArrayList<>();
            for (FarmInstance farm : registry.getAllFarms()) {
                if (!farm.isInitialized()) continue;
                if (farm.getFarmerCount() >= maxFarmers) continue;
                entries.add(new FarmListSyncPayload.FarmEntry(
                        farm.getOwnerUUID(),
                        com.stardew.craft.player.PlayerDisplayName.get(player.server, farm.getOwnerUUID()),
                        farm.getFarmName(),
                        farm.getFarmLayoutId().toString(),
                        0, // permission irrelevant for join list
                        false
                ));
            }

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new FarmListSyncPayload(entries, "farm_join"));
        });
    }
}
