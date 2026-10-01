package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Server → Client：打开矿车选线界面。 */
@SuppressWarnings("null")
public record OpenMinecartMenuPayload(String currentStationId, List<String> destinationIds)
        implements CustomPacketPayload {

    public static final Type<OpenMinecartMenuPayload> TYPE =
            new Type<>(new ResourceLocation(StardewCraft.MODID, "open_minecart_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenMinecartMenuPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.currentStationId(), 32);
                buf.writeVarInt(p.destinationIds().size());
                for (String id : p.destinationIds()) {
                    buf.writeUtf(id, 32);
                }
            },
            buf -> {
                String cur = buf.readUtf(32);
                int n = buf.readVarInt();
                List<String> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(buf.readUtf(32));
                return new OpenMinecartMenuPayload(cur, list);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenMinecartMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenMinecartMenuPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        List<Component> options = new ArrayList<>(payload.destinationIds().size());
        for (String id : payload.destinationIds()) {
            options.add(Component.translatable("stardewcraft.minecart.dest." + id));
        }
        // 最后追加一个"取消"选项
        options.add(Component.translatable("stardewcraft.minecart.cancel"));

        Component question = Component.translatable("stardewcraft.minecart.choose");
        List<String> destIds = payload.destinationIds();
        String currentId = payload.currentStationId();

        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
                com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                        question,
                        options,
                        index -> {
                            if (index < 0 || index >= destIds.size()) return; // 取消或越界
                            PacketDistributor.sendToServer(
                                    new SelectMinecartDestinationPayload(currentId, destIds.get(index)));
                        },
                        -1
                )));
    }
}
