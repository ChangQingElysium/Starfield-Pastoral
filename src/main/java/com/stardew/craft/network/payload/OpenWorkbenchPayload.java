package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.workbench.WorkbenchType;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: open the workbench screen.
 */
@SuppressWarnings("null")
public record OpenWorkbenchPayload(int typeId) implements CustomPacketPayload {

    public static final Type<OpenWorkbenchPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_workbench"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWorkbenchPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, OpenWorkbenchPayload::typeId,
            OpenWorkbenchPayload::new
        );

    public OpenWorkbenchPayload(WorkbenchType type) {
        this(type.getId());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenWorkbenchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenWorkbenchPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        WorkbenchType wbType = WorkbenchType.fromId(payload.typeId());
        mc.setScreen(new com.stardew.craft.client.gui.WorkbenchScreen(wbType));
    }
}
