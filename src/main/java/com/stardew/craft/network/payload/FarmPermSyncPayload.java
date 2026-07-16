package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.farm.FarmPermissionManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

/**
 * S→C: 发送在线玩家列表 + 当前玩家对每个玩家的权限设置。
 * 用于农场管理 GUI 的权限编辑界面。
 */
@SuppressWarnings("null")
public record FarmPermSyncPayload(
        int defaultPerm,  // 自己的默认权限
        List<PlayerPermEntry> players
) implements CustomPacketPayload {

    /** 在线玩家的权限数据 */
    public record PlayerPermEntry(
            UUID uuid,
            String name,
            int permission  // -1=使用默认, 0/1/2=覆盖值
    ) {}

    public static final Type<FarmPermSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "farm_perm_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FarmPermSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public FarmPermSyncPayload decode(RegistryFriendlyByteBuf buf) {
                    int defaultPerm = buf.readByte();
                    int count = buf.readVarInt();
                    List<PlayerPermEntry> players = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        players.add(new PlayerPermEntry(buf.readUUID(), buf.readUtf(), buf.readByte()));
                    }
                    return new FarmPermSyncPayload(defaultPerm, players);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, FarmPermSyncPayload payload) {
                    buf.writeByte(payload.defaultPerm);
                    buf.writeVarInt(payload.players.size());
                    for (PlayerPermEntry entry : payload.players) {
                        buf.writeUUID(entry.uuid);
                        buf.writeUtf(entry.name);
                        buf.writeByte(entry.permission);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FarmPermSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(FarmPermSyncPayload payload) {
        // 存储数据到客户端缓存，供 StardewGameMenuScreen 的农场管理 Tab 使用
        com.stardew.craft.client.gui.FarmPermissionClientCache.update(payload.defaultPerm, payload.players);
    }

    /**
     * 服务端构建并发送权限数据。
     */
    public static void sendToPlayer(ServerPlayer player) {
        UUID ownerUUID = com.stardew.craft.farm.FarmInstanceRegistry.get().getOwnerForPlayer(player.getUUID());
        if (ownerUUID == null) ownerUUID = player.getUUID();
        FarmPermissionManager mgr = FarmPermissionManager.get();
        int defaultPerm = mgr.getDefaultPermission(ownerUUID);

        List<PlayerPermEntry> entries = new ArrayList<>();
        com.stardew.craft.farm.FarmInstance farm = com.stardew.craft.farm.FarmInstanceRegistry.get().getFarm(ownerUUID);
        for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
            if (online.getUUID().equals(ownerUUID)) continue;
            // 跳过农场成员（他们已有完整权限）
            if (farm != null && farm.getMembers().contains(online.getUUID())) continue;
            int override = mgr.getOverridePermission(ownerUUID, online.getUUID());
            entries.add(new PlayerPermEntry(online.getUUID(),
                    com.stardew.craft.player.PlayerDisplayName.get(online), override));
        }

        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new FarmPermSyncPayload(defaultPerm, entries));
    }
}
