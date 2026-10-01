package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record SkillExperienceGainPayload(
        int skillId,
        int amount,
        int expBefore,
        int expAfter,
        int levelBefore,
        int levelAfter
) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<SkillExperienceGainPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "skill_experience_gain")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, SkillExperienceGainPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            SkillExperienceGainPayload::skillId,
            ByteBufCodecs.INT,
            SkillExperienceGainPayload::amount,
            ByteBufCodecs.INT,
            SkillExperienceGainPayload::expBefore,
            ByteBufCodecs.INT,
            SkillExperienceGainPayload::expAfter,
            ByteBufCodecs.INT,
            SkillExperienceGainPayload::levelBefore,
            ByteBufCodecs.INT,
            SkillExperienceGainPayload::levelAfter,
            SkillExperienceGainPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillExperienceGainPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.hud.SkillExperienceHud.onExperienceGained(payload));
    }
}
