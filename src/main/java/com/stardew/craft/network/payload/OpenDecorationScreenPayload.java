package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record OpenDecorationScreenPayload(
    String decorationType,
    BlockPos targetPos,
    String currentStyleId,
    List<DecorationOption> options,
    int currentSegment
) implements CustomPacketPayload {

    /** Backward-compatible constructor (defaults currentSegment to -1). */
    public OpenDecorationScreenPayload(String decorationType, BlockPos targetPos, String currentStyleId, List<DecorationOption> options) {
        this(decorationType, targetPos, currentStyleId, options, -1);
    }

    public record DecorationOption(
        String styleId,
        String texture,
        int texWidth,
        int texHeight,
        int sourceX,
        int sourceY,
        int sourceWidth,
        int sourceHeight,
        boolean unlocked,
        String unlockHintKey,
        int sortOrder
    ) {
        @SuppressWarnings("null")
        public static final StreamCodec<RegistryFriendlyByteBuf, DecorationOption> STREAM_CODEC = StreamCodec.of(
            (buf, option) -> {
                buf.writeUtf(option.styleId());
                buf.writeUtf(option.texture());
                buf.writeInt(option.texWidth());
                buf.writeInt(option.texHeight());
                buf.writeInt(option.sourceX());
                buf.writeInt(option.sourceY());
                buf.writeInt(option.sourceWidth());
                buf.writeInt(option.sourceHeight());
                buf.writeBoolean(option.unlocked());
                buf.writeUtf(option.unlockHintKey());
                buf.writeInt(option.sortOrder());
            },
            buf -> new DecorationOption(
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readInt()
            )
        );
    }

    @SuppressWarnings("null")
    public static final Type<OpenDecorationScreenPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_decoration_screen"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDecorationScreenPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.decorationType());
            buf.writeBlockPos(p.targetPos());
            buf.writeUtf(p.currentStyleId());
            DecorationOption.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.options());
            buf.writeInt(p.currentSegment());
        },
        buf -> {
            String decoType = buf.readUtf();
            BlockPos pos = buf.readBlockPos();
            String style = buf.readUtf();
            List<DecorationOption> opts = DecorationOption.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
            int seg = buf.readInt();
            return new OpenDecorationScreenPayload(decoType, pos, style, opts, seg);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenDecorationScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenDecorationScreenPayload payload) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        minecraft.setScreen(new com.stardew.craft.client.gui.DecorationSelectionScreen(payload));
    }
}
