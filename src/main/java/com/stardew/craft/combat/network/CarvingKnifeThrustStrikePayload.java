package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record CarvingKnifeThrustStrikePayload() implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<CarvingKnifeThrustStrikePayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "carving_thrust_strike")
    );

    public static final StreamCodec<ByteBuf, CarvingKnifeThrustStrikePayload> STREAM_CODEC =
        StreamCodec.unit(new CarvingKnifeThrustStrikePayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CarvingKnifeThrustStrikePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(CarvingKnifeThrustStrikePayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects("carving_thrust", mc.player);
        }
    }
}
