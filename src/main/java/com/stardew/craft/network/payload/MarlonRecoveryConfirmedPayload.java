package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.chat.ComponentSerialization;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Localizes the recovered item's name in Marlon's portrait dialogue on the receiving client. */
public record MarlonRecoveryConfirmedPayload(Component message) implements CustomPacketPayload {
    public static final Type<MarlonRecoveryConfirmedPayload> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID, "marlon_recovery_confirmed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarlonRecoveryConfirmedPayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, MarlonRecoveryConfirmedPayload::message, MarlonRecoveryConfirmedPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(MarlonRecoveryConfirmedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> client(payload));
    }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void client(MarlonRecoveryConfirmedPayload payload) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) mc.setScreen(new com.stardew.craft.client.gui.common.StardewNpcDialogueScreen("marlon", payload.message().getString(), 0));
    }
}
