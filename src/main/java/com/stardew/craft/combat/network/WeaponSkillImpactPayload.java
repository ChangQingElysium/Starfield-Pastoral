package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Server-confirmed impact event for a weapon skill active frame.
 */
public record WeaponSkillImpactPayload(
        int casterEntityId,
        String skillId,
        List<Integer> targetEntityIds,
        long seed
) implements CustomPacketPayload {
    public static final Type<WeaponSkillImpactPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "weapon_skill_impact")
    );

    public static final StreamCodec<ByteBuf, WeaponSkillImpactPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    WeaponSkillImpactPayload::casterEntityId,
                    ByteBufCodecs.STRING_UTF8,
                    WeaponSkillImpactPayload::skillId,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
                    WeaponSkillImpactPayload::targetEntityIds,
                    ByteBufCodecs.VAR_LONG,
                    WeaponSkillImpactPayload::seed,
                    WeaponSkillImpactPayload::new
            );

    public WeaponSkillImpactPayload {
        targetEntityIds = targetEntityIds == null ? List.of() : List.copyOf(targetEntityIds);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WeaponSkillImpactPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.stardew.craft.client.weapon.presentation.SkillPresentationClient.onImpact(payload)
        );
    }
}
