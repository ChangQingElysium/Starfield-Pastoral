package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.mastery.MasteryService;
import com.stardew.craft.player.SkillType;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：玩家在 Mastery pedestal UI 上点击 Claim。
 * skillId: 0=Farming, 1=Fishing, 2=Foraging, 3=Mining, 4=Combat。
 */
public record RequestClaimMasteryRewardPayload(int skillId) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<RequestClaimMasteryRewardPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "request_claim_mastery_reward")
    );

    public static final StreamCodec<ByteBuf, RequestClaimMasteryRewardPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        RequestClaimMasteryRewardPayload::skillId,
        RequestClaimMasteryRewardPayload::new
    );

    @Override
    public Type<RequestClaimMasteryRewardPayload> type() {
        return TYPE;
    }

    public static void handle(RequestClaimMasteryRewardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (payload.skillId < 0 || payload.skillId > 4) return;
            SkillType skill = SkillType.fromId(payload.skillId);
            MasteryService.tryClaim(sp, skill);
        });
    }
}
