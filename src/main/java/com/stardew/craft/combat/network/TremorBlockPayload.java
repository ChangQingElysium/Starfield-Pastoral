package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record TremorBlockPayload(float x, float y, float z, int blockStateId, float ySpeed)
        implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<TremorBlockPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "tremor_block")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, TremorBlockPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT,
        TremorBlockPayload::x,
        ByteBufCodecs.FLOAT,
        TremorBlockPayload::y,
        ByteBufCodecs.FLOAT,
        TremorBlockPayload::z,
        ByteBufCodecs.VAR_INT,
        TremorBlockPayload::blockStateId,
        ByteBufCodecs.FLOAT,
        TremorBlockPayload::ySpeed,
        TremorBlockPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TremorBlockPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(TremorBlockPayload payload) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }
        var level = java.util.Objects.requireNonNull(mc.level);
        var state = Block.stateById(payload.blockStateId());
        if (state == null) {
            return;
        }
        level.addParticle(
            new BlockParticleOption(java.util.Objects.requireNonNull(ParticleTypes.BLOCK), java.util.Objects.requireNonNull(state)),
            payload.x(), payload.y(), payload.z(),
            0.0, payload.ySpeed(), 0.0
        );
    }
}
