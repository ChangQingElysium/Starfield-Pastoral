package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** S→C: asks an existing-save player to complete the new dialogue profile. */
@SuppressWarnings("null")
public record OpenPlayerProfileSetupPayload() implements CustomPacketPayload {
    public static final Type<OpenPlayerProfileSetupPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "open_player_profile_setup"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPlayerProfileSetupPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenPlayerProfileSetupPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenPlayerProfileSetupPayload payload, IPayloadContext context) {
        context.enqueueWork(OpenPlayerProfileSetupPayload::handleClient);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setScreen(new com.stardew.craft.client.gui.PlayerProfileSetupScreen());
        }
    }
}
