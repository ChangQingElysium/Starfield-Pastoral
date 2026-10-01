package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Server to client: open one already-resolved secret note. */
@SuppressWarnings("null")
public record OpenSecretNotePayload(
        String noteId,
        int displayNumber,
        String text,
        int imageIndex
) implements CustomPacketPayload {
    public static final Type<OpenSecretNotePayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "open_secret_note")
    );

    public static final StreamCodec<ByteBuf, OpenSecretNotePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenSecretNotePayload::noteId,
            ByteBufCodecs.INT, OpenSecretNotePayload::displayNumber,
            ByteBufCodecs.STRING_UTF8, OpenSecretNotePayload::text,
            ByteBufCodecs.INT, OpenSecretNotePayload::imageIndex,
            OpenSecretNotePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenSecretNotePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenSecretNotePayload payload) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (payload.imageIndex() >= 0) {
            minecraft.setScreen(new com.stardew.craft.client.gui.SecretNoteImageScreen(
                    payload.displayNumber(), payload.imageIndex()));
            return;
        }
        minecraft.setScreen(new com.stardew.craft.client.gui.LetterViewerScreen(
                new OpenMailPayload(
                        "secret_note:" + payload.noteId(),
                        payload.text(),
                        "",
                        1,
                        "gray",
                        List.of(),
                        0,
                        "",
                        "",
                        false,
                        0
                )));
    }
}
