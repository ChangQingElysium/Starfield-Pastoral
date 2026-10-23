// Upstream-SHA256: d6daeabbcf3529e87e1a4d575c72ac616883f9055a8abbe1376f7ebd1a1a0b48
package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Server to client: vanilla secret-gift yes/no question. */
@SuppressWarnings("null")
public record OpenWinterStarGiftPromptPayload(String npcId, String npcDisplayName, boolean female) implements CustomPacketPayload {
    public static final Type<OpenWinterStarGiftPromptPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_winter_star_gift_prompt"));
    public static final StreamCodec<FriendlyByteBuf, OpenWinterStarGiftPromptPayload> STREAM_CODEC = StreamCodec.of(
        (buf, value) -> {
            buf.writeUtf(value.npcId(), 64);
            buf.writeUtf(value.npcDisplayName(), 256);
            buf.writeBoolean(value.female());
        },
        buf -> new OpenWinterStarGiftPromptPayload(buf.readUtf(64), buf.readUtf(256), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenWinterStarGiftPromptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Client.handleClient(payload));
    }

    // Keep client bytecode and its compiler-generated lambdas out of the common payload.
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    private static final class Client {
        private Client() {}

        private static void handleClient(OpenWinterStarGiftPromptPayload payload) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
                com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                Component.translatable(payload.female()
                    ? "stardewcraft.festival.winter_star.gift_prompt_female"
                    : "stardewcraft.festival.winter_star.gift_prompt_male", payload.npcDisplayName()),
                List.of(Component.translatable("gui.yes"), Component.translatable("gui.no")),
                answer -> {
                    if (answer == 0) {
                        // The question screen closes itself after invoking this callback.
                        // Defer one client task so its setScreen(null) doesn't erase the picker.
                        mc.tell(() -> mc.setScreen(new com.stardew.craft.client.gui.festival.WinterStarGiftSelectionScreen(
                            payload.npcId(), payload.npcDisplayName())));
                    }
                },
                -1
            )));
        }
    }
}
