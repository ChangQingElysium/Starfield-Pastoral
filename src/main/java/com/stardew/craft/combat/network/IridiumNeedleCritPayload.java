package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record IridiumNeedleCritPayload(int stacks) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<IridiumNeedleCritPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "iridium_needle_crit")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, IridiumNeedleCritPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        IridiumNeedleCritPayload::stacks,
        IridiumNeedleCritPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(IridiumNeedleCritPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(IridiumNeedleCritPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (payload.stacks() > 0) {
            com.stardew.craft.client.weapon.IridiumNeedleCritClientState.setStacks(payload.stacks());
        } else {
            com.stardew.craft.client.weapon.IridiumNeedleCritClientState.clear();
        }
    }
}
