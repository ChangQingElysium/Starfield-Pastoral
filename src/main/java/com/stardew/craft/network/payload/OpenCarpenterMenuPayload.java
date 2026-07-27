package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.CarpenterBlueprint;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server → Client: open the carpenter building menu.
 */
@SuppressWarnings("null")
public record OpenCarpenterMenuPayload(
    String builder,
    int playerMoney,
    List<CarpenterBlueprint> blueprints,
    long catalogRevision
) implements CustomPacketPayload {

    public static final Type<OpenCarpenterMenuPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_carpenter_menu"));

    private static final StreamCodec<RegistryFriendlyByteBuf, CarpenterBlueprint.MaterialEntry> MATERIAL_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CarpenterBlueprint.MaterialEntry::itemId,
            ByteBufCodecs.INT,         CarpenterBlueprint.MaterialEntry::count,
            CarpenterBlueprint.MaterialEntry::new
        );

    private static final StreamCodec<RegistryFriendlyByteBuf, CarpenterBlueprint> BLUEPRINT_CODEC =
        new StreamCodec<>() {
            @Override
            public CarpenterBlueprint decode(RegistryFriendlyByteBuf buf) {
                String id = ByteBufCodecs.STRING_UTF8.decode(buf);
                String displayNameKey = ByteBufCodecs.STRING_UTF8.decode(buf);
                String descriptionKey = ByteBufCodecs.STRING_UTF8.decode(buf);
                int cost = buf.readInt();
                List<CarpenterBlueprint.MaterialEntry> materials = MATERIAL_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                String resultItemId = ByteBufCodecs.STRING_UTF8.decode(buf);
                boolean isUpgrade = buf.readBoolean();
                int previewCanvasSize = buf.readVarInt();
                boolean magicalConstruction = buf.readBoolean();
                return new CarpenterBlueprint(id, displayNameKey, descriptionKey, cost, materials,
                        resultItemId, isUpgrade, previewCanvasSize, magicalConstruction);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, CarpenterBlueprint bp) {
                ByteBufCodecs.STRING_UTF8.encode(buf, bp.id());
                ByteBufCodecs.STRING_UTF8.encode(buf, bp.displayNameKey());
                ByteBufCodecs.STRING_UTF8.encode(buf, bp.descriptionKey());
                buf.writeInt(bp.cost());
                MATERIAL_CODEC.apply(ByteBufCodecs.list()).encode(buf, bp.materials());
                ByteBufCodecs.STRING_UTF8.encode(buf, bp.resultItemId());
                buf.writeBoolean(bp.isUpgrade());
                buf.writeVarInt(bp.previewCanvasSize());
                buf.writeBoolean(bp.magicalConstruction());
            }
        };

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCarpenterMenuPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public OpenCarpenterMenuPayload decode(
                    RegistryFriendlyByteBuf buffer
            ) {
                return new OpenCarpenterMenuPayload(
                        ByteBufCodecs.STRING_UTF8.decode(buffer),
                        buffer.readInt(),
                        BLUEPRINT_CODEC.apply(ByteBufCodecs.list())
                                .decode(buffer),
                        buffer.readVarLong());
            }

            @Override
            public void encode(
                    RegistryFriendlyByteBuf buffer,
                    OpenCarpenterMenuPayload payload
            ) {
                ByteBufCodecs.STRING_UTF8.encode(
                        buffer, payload.builder());
                buffer.writeInt(payload.playerMoney());
                BLUEPRINT_CODEC.apply(ByteBufCodecs.list())
                        .encode(buffer, payload.blueprints());
                buffer.writeVarLong(payload.catalogRevision());
            }
        };

    /** Source-compatible constructor for callers predating catalog sessions. */
    public OpenCarpenterMenuPayload(
            String builder,
            int playerMoney,
            List<CarpenterBlueprint> blueprints
    ) {
        this(builder, playerMoney, blueprints, -1L);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenCarpenterMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenCarpenterMenuPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new com.stardew.craft.client.gui.CarpenterMenuScreen(payload));
    }
}
