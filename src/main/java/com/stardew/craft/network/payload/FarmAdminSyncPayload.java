package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * S→C: 发送所有农场数据给管理员客户端以打开/刷新管理 GUI。
 */
@SuppressWarnings("null")
public record FarmAdminSyncPayload(
        List<FarmEntry> entries
) implements CustomPacketPayload {

    public static final Type<FarmAdminSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "farm_admin_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FarmAdminSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public FarmAdminSyncPayload decode(RegistryFriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<FarmEntry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        entries.add(new FarmEntry(
                                buf.readUUID(),
                                buf.readUtf(64),
                                buf.readUtf(64),
                                buf.readVarInt(),
                                buf.readBlockPos(),
                                buf.readUtf(32),
                                buf.readBoolean(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readVarInt()
                        ));
                    }
                    return new FarmAdminSyncPayload(entries);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, FarmAdminSyncPayload payload) {
                    buf.writeVarInt(payload.entries.size());
                    for (FarmEntry entry : payload.entries) {
                        buf.writeUUID(entry.ownerUUID);
                        buf.writeUtf(entry.ownerName, 64);
                        buf.writeUtf(entry.farmName, 64);
                        buf.writeVarInt(entry.slotIndex);
                        buf.writeBlockPos(entry.origin);
                        buf.writeUtf(entry.farmType, 32);
                        buf.writeBoolean(entry.initialized);
                        buf.writeVarInt(entry.lastOnlineDay);
                        buf.writeVarInt(entry.lastOnlineSeason);
                        buf.writeVarInt(entry.memberCount);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static FarmAdminSyncPayload fromRegistry(FarmInstanceRegistry registry) {
        net.minecraft.server.MinecraftServer server =
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        Collection<FarmInstance> farms = registry.getAllFarms();
        List<FarmEntry> entries = new ArrayList<>(farms.size());
        for (FarmInstance farm : farms) {
            entries.add(new FarmEntry(
                    farm.getOwnerUUID(),
                    server == null ? farm.getOwnerName()
                            : com.stardew.craft.player.PlayerDisplayName.get(server, farm.getOwnerUUID()),
                    farm.getFarmName(),
                    farm.getSlotIndex(),
                    farm.getOrigin(),
                    farm.getFarmType().name(),
                    farm.isInitialized(),
                    farm.getLastOnlineDay(),
                    farm.getLastOnlineSeason(),
                    farm.getFarmerCount()
            ));
        }
        return new FarmAdminSyncPayload(entries);
    }

    public static void handle(FarmAdminSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(FarmAdminSyncPayload payload) {
        com.stardew.craft.client.gui.FarmAdminScreen.openFromPayload(payload);
    }

    public record FarmEntry(
            UUID ownerUUID,
            String ownerName,
            String farmName,
            int slotIndex,
            BlockPos origin,
            String farmType,
            boolean initialized,
            int lastOnlineDay,
            int lastOnlineSeason,
            int memberCount // 成员数（含 owner）
    ) {}
}
