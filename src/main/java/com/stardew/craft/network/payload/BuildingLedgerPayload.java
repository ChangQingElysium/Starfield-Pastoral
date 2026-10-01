package com.stardew.craft.network.payload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record BuildingLedgerPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<BuildingLedgerPayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:building_ledger"));
    public static final StreamCodec<FriendlyByteBuf, BuildingLedgerPayload> CODEC = StreamCodec.of((b,p)->b.writeNbt(p.data), b->new BuildingLedgerPayload(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(BuildingLedgerPayload p, IPayloadContext context) { context.enqueueWork(() -> client(p)); }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void client(BuildingLedgerPayload p) { com.stardew.craft.client.gui.BuildingLedgerScreen.show(p.data); }
}
