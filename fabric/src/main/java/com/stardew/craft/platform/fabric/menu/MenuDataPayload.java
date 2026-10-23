package com.stardew.craft.platform.fabric.menu;

import com.stardew.craft.platform.fabric.menu.client.FabricMenuDataClient;
import com.stardew.craft.platform.fabric.network.IPayloadContext;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Full signed-int DataSlot synchronization for a specific currently open mod menu. */
public record MenuDataPayload(int containerId, ResourceLocation menuType, int dataSlot, int value)
        implements CustomPacketPayload {
    public static final Type<MenuDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            "stardewcraft", "fabric_menu_data_v1"));
    public static final StreamCodec<ByteBuf, MenuDataPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MenuDataPayload::containerId,
            ResourceLocation.STREAM_CODEC, MenuDataPayload::menuType,
            ByteBufCodecs.VAR_INT, MenuDataPayload::dataSlot,
            ByteBufCodecs.INT, MenuDataPayload::value,
            MenuDataPayload::new);

    public MenuDataPayload {
        Objects.requireNonNull(menuType, "Menu type");
        if (containerId < 0 || dataSlot < 0 || dataSlot > Short.MAX_VALUE
                || !"stardewcraft".equals(menuType.getNamespace())) {
            throw new IllegalArgumentException("Invalid StardewCraft menu data address");
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** An id alone is insufficient: menu counters are reused when another menu opens. */
    public boolean matches(int currentContainerId, ResourceLocation currentType, int currentDataSlots) {
        return containerId == currentContainerId && menuType.equals(currentType) && dataSlot < currentDataSlots;
    }

    public static void handle(MenuDataPayload payload, IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND) {
            throw new IllegalStateException("Menu data is a clientbound payload");
        }
        context.enqueueWork(() -> FabricMenuDataClient.apply(payload));
    }
}
