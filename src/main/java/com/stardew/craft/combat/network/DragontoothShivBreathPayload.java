package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record DragontoothShivBreathPayload(int casterId, boolean active, int durationTicks, long gameTick) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<DragontoothShivBreathPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "dragontooth_shiv_breath_state")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, DragontoothShivBreathPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        DragontoothShivBreathPayload::casterId,
        ByteBufCodecs.BOOL,
        DragontoothShivBreathPayload::active,
        ByteBufCodecs.VAR_INT,
        DragontoothShivBreathPayload::durationTicks,
        ByteBufCodecs.VAR_LONG,
        DragontoothShivBreathPayload::gameTick,
        DragontoothShivBreathPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DragontoothShivBreathPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(DragontoothShivBreathPayload payload) {
        com.stardew.craft.client.weapon.DragontoothShivBreathClientState.apply(
                payload.casterId(), payload.active(), payload.durationTicks(), payload.gameTick());
    }
}
