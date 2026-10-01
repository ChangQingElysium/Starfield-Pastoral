package com.stardew.craft.pet;

import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-originated pet feedback; visibility is evaluated by each listener's 3D camera. */
public record PetSoundPayload(ResourceLocation sound, Vec3 position, boolean voice, float volume, float pitch,
                              int border, int range, int delay, boolean footstep) implements CustomPacketPayload {
    public static final Type<PetSoundPayload> TYPE = new Type<>(new ResourceLocation("stardewcraft:pet_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PetSoundPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeResourceLocation(p.sound); b.writeVec3(p.position); b.writeBoolean(p.voice); b.writeFloat(p.volume); b.writeFloat(p.pitch); b.writeInt(p.border); b.writeInt(p.range); b.writeVarInt(p.delay); b.writeBoolean(p.footstep); },
            b -> new PetSoundPayload(b.readResourceLocation(), b.readVec3(), b.readBoolean(), b.readFloat(), b.readFloat(), b.readInt(), b.readInt(), b.readVarInt(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(PetSoundPayload payload, IPayloadContext context) { context.enqueueWork(() -> client(payload)); }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void client(PetSoundPayload payload) { com.stardew.craft.client.pet.PetSoundClient.play(payload); }
}
