package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.TotemPoleBlockEntity;
import com.stardew.craft.totem.TotemPoleTracker;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record TotemNamingSubmitPayload(
        long blockPos,
        String newName
) implements CustomPacketPayload {

    public static final Type<TotemNamingSubmitPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "totem_naming_submit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TotemNamingSubmitPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, TotemNamingSubmitPayload::blockPos,
                    ByteBufCodecs.STRING_UTF8, TotemNamingSubmitPayload::newName,
                    TotemNamingSubmitPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TotemNamingSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel serverLevel)) return;

            BlockPos pos = BlockPos.of(payload.blockPos);

            // 安全验证：距离不能太远
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;

            if (serverLevel.getBlockEntity(pos) instanceof TotemPoleBlockEntity pole) {
                // 系统柱不可重命名
                if (pole.isSystemPole()) return;

                // 名称长度限制
                String name = payload.newName;
                if (name.length() > 48) name = name.substring(0, 48);
                name = name.trim();
                if (name.isEmpty()) return;

                pole.setPoleName(name);

                // 同步到 tracker
                TotemPoleTracker tracker = TotemPoleTracker.get(serverLevel);
                tracker.updateName(pole.getPoleId(), name);
            }
        });
    }
}
