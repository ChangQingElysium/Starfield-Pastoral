package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Server-authored presentation snapshot for one weapon skill cast.
 *
 * <p>The action duration drives the local held-item pose. The presentation duration
 * may be longer for effects which persist after the cast. The server game tick and
 * active offset put animation, hit resolution and observer presentation on the same
 * timeline. Origin, yaw and seed make presentation deterministic.</p>
 */
public record WeaponSkillAnimPayload(
        int casterEntityId,
        String weaponId,
        String skillId,
        int actionDurationTicks,
        int presentationDurationTicks,
        long startGameTick,
        int activeTickOffset,
        double originX,
        double originY,
        double originZ,
        float yaw,
        long seed
) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<WeaponSkillAnimPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "weapon_skill_anim")
    );

    public static final StreamCodec<ByteBuf, WeaponSkillAnimPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WeaponSkillAnimPayload decode(ByteBuf buffer) {
            return new WeaponSkillAnimPayload(
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.DOUBLE.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer)
            );
        }

        @Override
        public void encode(ByteBuf buffer, WeaponSkillAnimPayload payload) {
            ByteBufCodecs.VAR_INT.encode(buffer, payload.casterEntityId());
            ByteBufCodecs.STRING_UTF8.encode(buffer, payload.weaponId());
            ByteBufCodecs.STRING_UTF8.encode(buffer, payload.skillId());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.actionDurationTicks());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.presentationDurationTicks());
            ByteBufCodecs.VAR_LONG.encode(buffer, payload.startGameTick());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.activeTickOffset());
            ByteBufCodecs.DOUBLE.encode(buffer, payload.originX());
            ByteBufCodecs.DOUBLE.encode(buffer, payload.originY());
            ByteBufCodecs.DOUBLE.encode(buffer, payload.originZ());
            ByteBufCodecs.FLOAT.encode(buffer, payload.yaw());
            ByteBufCodecs.VAR_LONG.encode(buffer, payload.seed());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WeaponSkillAnimPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(payload));
    }
}
