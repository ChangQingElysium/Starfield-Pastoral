package com.stardew.craft.pet;

import net.minecraft.nbt.CompoundTag;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record PetScreenPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<PetScreenPayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:pet_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PetScreenPayload> CODEC = StreamCodec.of((b, p) -> b.writeNbt(p.data), b -> new PetScreenPayload(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(PetScreenPayload payload, IPayloadContext context) { context.enqueueWork(() -> client(payload.data)); }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void client(CompoundTag data) { com.stardew.craft.client.pet.PetScreen.receive(data); }
}
