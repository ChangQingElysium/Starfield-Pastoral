package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

/**
 * S→C: 发送所有农场列表 + 当前玩家对每个农场的权限。
 * 触发客户端打开农场选择入口 GUI。
 */
@SuppressWarnings("null")
public record FarmListSyncPayload(
        List<FarmEntry> farms,
        String entryTag // 从哪个入口进入的 (farm_entry_south / east / west)
) implements CustomPacketPayload {

    /** 单个农场数据（发送到客户端用于 GUI 显示） */
    public record FarmEntry(
            UUID ownerUUID,
            String ownerName,
            String farmName,
            String farmTypeId,
            int permission, // 0/1/2 — 当前玩家对该农场的权限
            boolean isMember // 当前玩家是否为该农场的成员（owner 或 member）
    ) {}

    public static final Type<FarmListSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "farm_list_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FarmListSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public FarmListSyncPayload decode(RegistryFriendlyByteBuf buf) {
                    String entryTag = buf.readUtf();
                    int count = buf.readVarInt();
                    List<FarmEntry> farms = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        UUID uuid = buf.readUUID();
                        String ownerName = buf.readUtf();
                        String farmName = buf.readUtf();
                        String farmTypeId = buf.readUtf();
                        int perm = buf.readByte();
                        boolean isMember = buf.readBoolean();
                        farms.add(new FarmEntry(uuid, ownerName, farmName, farmTypeId, perm, isMember));
                    }
                    return new FarmListSyncPayload(farms, entryTag);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, FarmListSyncPayload payload) {
                    buf.writeUtf(payload.entryTag);
                    buf.writeVarInt(payload.farms.size());
                    for (FarmEntry entry : payload.farms) {
                        buf.writeUUID(entry.ownerUUID);
                        buf.writeUtf(entry.ownerName);
                        buf.writeUtf(entry.farmName);
                        buf.writeUtf(entry.farmTypeId);
                        buf.writeByte(entry.permission);
                        buf.writeBoolean(entry.isMember);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FarmListSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(FarmListSyncPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        if ("farm_join".equals(payload.entryTag)) {
            mc.setScreen(new com.stardew.craft.client.gui.FarmJoinSelectScreen(payload.farms));
        } else {
            mc.setScreen(new com.stardew.craft.client.gui.FarmEntryScreen(payload.farms, payload.entryTag));
        }
    }

    /**
     * 服务端构建并发送农场列表。
     */
    public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player, String entryTag) {
        var registry = com.stardew.craft.farm.FarmInstanceRegistry.get();
        var permMgr = com.stardew.craft.farm.FarmPermissionManager.get();
        UUID playerUUID = player.getUUID();

        List<FarmEntry> entries = new ArrayList<>();
        for (com.stardew.craft.farm.FarmInstance farm : registry.getAllFarms()) {
            if (!farm.isInitialized()) continue;
            // 成员视为 PERM_FULL
            int perm = farm.isFarmer(playerUUID)
                    ? com.stardew.craft.farm.FarmPermissionManager.PERM_FULL
                    : permMgr.getPermission(farm.getOwnerUUID(), playerUUID);
            boolean isMember = farm.isFarmer(playerUUID);
            entries.add(new FarmEntry(
                    farm.getOwnerUUID(),
                    com.stardew.craft.player.PlayerDisplayName.get(player.server, farm.getOwnerUUID()),
                    farm.getFarmName(),
                    farm.getFarmType().getId(),
                    perm,
                    isMember
            ));
        }

        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new FarmListSyncPayload(entries, entryTag));
    }
}
