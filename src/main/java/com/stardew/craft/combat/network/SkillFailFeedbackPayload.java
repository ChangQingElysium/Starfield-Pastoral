package com.stardew.craft.combat.network;

import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import com.stardew.craft.StardewCraft;

/**
 * 服务端 -> 客户端：技能冷却失败反馈（触发颤抖、声音、粒子）
 */
public record SkillFailFeedbackPayload(boolean mainHand) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<SkillFailFeedbackPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "skill_fail_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillFailFeedbackPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.mainHand),
                    buf -> new SkillFailFeedbackPayload(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillFailFeedbackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.stardew.craft.client.weapon.SkillFailFeedbackHandler.onReceive(payload.mainHand);
        });
    }
}
