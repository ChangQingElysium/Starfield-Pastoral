package com.stardew.craft.combat.network;

import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import com.stardew.craft.StardewCraft;

/**
 * 服务端 -> 客户端：同步技能冷却信息
 */
public record SkillCooldownSyncPayload(String weaponId, String skillId, int totalTicks, int remainingTicks) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<SkillCooldownSyncPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "skill_cooldown_sync"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, SkillCooldownSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.weaponId);
                        buf.writeUtf(payload.skillId);
                        buf.writeInt(payload.totalTicks);
                        buf.writeInt(payload.remainingTicks);
                    },
                    buf -> new SkillCooldownSyncPayload(
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readInt(),
                            buf.readInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillCooldownSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.setCooldown(
                    payload.weaponId, payload.skillId, payload.totalTicks, payload.remainingTicks
            );
        });
    }
}
