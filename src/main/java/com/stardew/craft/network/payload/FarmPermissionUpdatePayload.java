package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmPermissionManager;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * C→S: 玩家在农场管理 GUI 中修改权限设置。
 * <p>
 * action:
 *   0 — 设置对特定玩家的权限覆盖 (targetUUID, level)
 *   1 — 移除对特定玩家的权限覆盖 (targetUUID)
 *   2 — 设置默认权限等级 (level)
 */
@SuppressWarnings("null")
public record FarmPermissionUpdatePayload(
        int action,
        UUID targetUUID,  // action 0/1 时为目标玩家 UUID, action 2 时可为零 UUID
        int level         // action 0/2 时有效
) implements CustomPacketPayload {

    public static final Type<FarmPermissionUpdatePayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "farm_perm_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FarmPermissionUpdatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public FarmPermissionUpdatePayload decode(RegistryFriendlyByteBuf buf) {
                    return new FarmPermissionUpdatePayload(buf.readByte(), buf.readUUID(), buf.readByte());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, FarmPermissionUpdatePayload payload) {
                    buf.writeByte(payload.action);
                    buf.writeUUID(payload.targetUUID);
                    buf.writeByte(payload.level);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FarmPermissionUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // 解析真实农场主 UUID（成员也能管理）
            UUID ownerUUID = FarmInstanceRegistry.get().getOwnerForPlayer(player.getUUID());
            if (ownerUUID == null) return;

            FarmPermissionManager mgr = FarmPermissionManager.get();

            switch (payload.action) {
                case 0 -> { // 设置对特定玩家的权限
                    if (payload.targetUUID.equals(ownerUUID)) return; // 不能设置自己
                    mgr.setPermission(ownerUUID, payload.targetUUID, payload.level);
                    player.displayClientMessage(
                            Component.translatable("stardewcraft.farm.perm_updated"), true);
                }
                case 1 -> { // 移除对特定玩家的覆盖
                    mgr.removeOverride(ownerUUID, payload.targetUUID);
                    player.displayClientMessage(
                            Component.translatable("stardewcraft.farm.perm_reset"), true);
                }
                case 2 -> { // 设置默认权限
                    mgr.setDefaultPermission(ownerUUID, payload.level);
                    player.displayClientMessage(
                            Component.translatable("stardewcraft.farm.default_perm_updated"), true);
                }
            }
        });
    }
}
