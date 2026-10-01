package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record DashMovementPayload(boolean active, int durationTicks, double endX, double endY, double endZ)
    implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<DashMovementPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "dash_movement_state")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, DashMovementPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        DashMovementPayload::active,
        ByteBufCodecs.VAR_INT,
        DashMovementPayload::durationTicks,
        ByteBufCodecs.DOUBLE,
        DashMovementPayload::endX,
        ByteBufCodecs.DOUBLE,
        DashMovementPayload::endY,
        ByteBufCodecs.DOUBLE,
        DashMovementPayload::endZ,
        DashMovementPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DashMovementPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(DashMovementPayload payload) {
        if (payload.active()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            long nowTick = mc.level != null ? mc.level.getGameTime() : 0L;
            Vec3 end = new Vec3(payload.endX(), payload.endY(), payload.endZ());
            com.stardew.craft.client.weapon.DashMovementClientState.start(nowTick, payload.durationTicks(), end);
        } else {
            com.stardew.craft.client.weapon.DashMovementClientState.clear();
        }
    }
}
