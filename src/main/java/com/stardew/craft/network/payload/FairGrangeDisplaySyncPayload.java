package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.festival.FairGrangeDisplayClientCache;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public record FairGrangeDisplaySyncPayload(boolean active, List<ItemStack> display) implements CustomPacketPayload {
    public static final Type<FairGrangeDisplaySyncPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "fair_grange_display_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FairGrangeDisplaySyncPayload> STREAM_CODEC = StreamCodec.of(
        FairGrangeDisplaySyncPayload::write,
        FairGrangeDisplaySyncPayload::read
    );

    private static void write(RegistryFriendlyByteBuf buf, FairGrangeDisplaySyncPayload payload) {
        buf.writeBoolean(payload.active());
        writeStacks(buf, payload.display());
    }

    private static FairGrangeDisplaySyncPayload read(RegistryFriendlyByteBuf buf) {
        return new FairGrangeDisplaySyncPayload(buf.readBoolean(), readStacks(buf));
    }

    private static void writeStacks(RegistryFriendlyByteBuf buf, List<ItemStack> stacks) {
        int count = Math.min(9, stacks == null ? 0 : stacks.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.encode(buf, stacks.get(i));
        }
    }

    private static List<ItemStack> readStacks(RegistryFriendlyByteBuf buf) {
        int count = Math.max(0, Math.min(9, buf.readVarInt()));
        List<ItemStack> stacks = new ArrayList<>(9);
        for (int i = 0; i < count; i++) {
            stacks.add(com.stardew.craft.port.PortCodecs.OPTIONAL_ITEM_STACK.decode(buf));
        }
        while (stacks.size() < 9) {
            stacks.add(ItemStack.EMPTY);
        }
        return List.copyOf(stacks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FairGrangeDisplaySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(FairGrangeDisplaySyncPayload payload) {
        FairGrangeDisplayClientCache.apply(payload.active(), payload.display());
    }
}
