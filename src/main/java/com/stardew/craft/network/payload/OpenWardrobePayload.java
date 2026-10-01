package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.WardrobeBlockEntity;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public record OpenWardrobePayload(BlockPos pos, String titleKey, List<ItemStack> items) implements CustomPacketPayload {
    public static final Type<OpenWardrobePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_wardrobe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWardrobePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBlockPos(payload.pos());
            buf.writeUtf(payload.titleKey(), 256);
            buf.writeVarInt(payload.items().size());
            for (ItemStack stack : payload.items()) {
                com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.encode(buf, stack);
            }
        },
        buf -> {
            BlockPos pos = buf.readBlockPos();
            String titleKey = buf.readUtf(256);
            int size = buf.readVarInt();
            List<ItemStack> items = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ItemStack stack = com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.decode(buf);
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
            return new OpenWardrobePayload(pos, titleKey, items);
        }
    );

    public static OpenWardrobePayload from(BlockPos pos, WardrobeBlockEntity wardrobe) {
        return new OpenWardrobePayload(pos, wardrobe.getBlockState().getBlock().getDescriptionId(), wardrobe.itemsView());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenWardrobePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenWardrobePayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mc.screen instanceof com.stardew.craft.client.gui.WardrobeScreen wardrobeScreen
            && wardrobeScreen.isFor(payload.pos())) {
            wardrobeScreen.updateItems(payload.items());
        } else {
            mc.setScreen(new com.stardew.craft.client.gui.WardrobeScreen(payload));
        }
    }
}
