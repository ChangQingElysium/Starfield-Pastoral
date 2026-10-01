package com.stardew.craft.pet;

import com.stardew.craft.api.v1.pet.StardewPets;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Verify the addon catalog before the player can open a farm questionnaire or enter the world. */
public record PetCatalogHandshake() implements ICustomConfigurationTask {
    public static final Type TYPE = new Type(new ResourceLocation("stardewcraft:pet_catalog"));
    @Override public Type type() { return TYPE; }
    @Override public void run(Consumer<CustomPacketPayload> sender) { StardewPets.freeze(); sender.accept(new Offer(StardewPets.fingerprint())); }
    public record Offer(String fingerprint) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Offer> TYPE = new CustomPacketPayload.Type<>(new ResourceLocation("stardewcraft:pet_catalog_offer"));
        public static final StreamCodec<FriendlyByteBuf, Offer> CODEC = StreamCodec.of((b, p) -> b.writeUtf(p.fingerprint, 64), b -> new Offer(b.readUtf(64)));
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static void handle(Offer payload, IPayloadContext context) {
            StardewPets.freeze();
            if (!payload.fingerprint.equals(StardewPets.fingerprint())) { context.disconnect(Component.translatable("pet.stardewcraft.catalog_mismatch")); return; }
            context.reply(new Ack(payload.fingerprint));
        }
    }
    public record Ack(String fingerprint) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Ack> TYPE = new CustomPacketPayload.Type<>(new ResourceLocation("stardewcraft:pet_catalog_ack"));
        public static final StreamCodec<FriendlyByteBuf, Ack> CODEC = StreamCodec.of((b, p) -> b.writeUtf(p.fingerprint, 64), b -> new Ack(b.readUtf(64)));
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static void handle(Ack payload, IPayloadContext context) {
            if (!payload.fingerprint.equals(StardewPets.fingerprint())) { context.disconnect(Component.translatable("pet.stardewcraft.catalog_mismatch")); return; }
            context.finishCurrentTask(PetCatalogHandshake.TYPE);
        }
    }
}
