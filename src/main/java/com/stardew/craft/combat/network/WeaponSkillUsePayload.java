package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record WeaponSkillUsePayload(boolean majorSkill) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<WeaponSkillUsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "weapon_skill_use")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, WeaponSkillUsePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            WeaponSkillUsePayload::majorSkill,
            WeaponSkillUsePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WeaponSkillUsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            com.stardew.craft.combat.skill.WeaponSkillDispatcher.use(
                    player.level(), player, InteractionHand.MAIN_HAND, payload.majorSkill());
        });
    }
}
