package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.mail.MailService;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: 玩家检查邮箱（右键邮箱方块）。
 * 服务端从 mailbox 队列 pop 第一封邮件，返回 OpenMailPayload。
 */
@SuppressWarnings("null")
public record CheckMailboxPayload() implements CustomPacketPayload {

    public static final Type<CheckMailboxPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "check_mailbox"));

    public static final StreamCodec<ByteBuf, CheckMailboxPayload> STREAM_CODEC =
            StreamCodec.unit(new CheckMailboxPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CheckMailboxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                MailService.openNextMail(sp);
            }
        });
    }
}
