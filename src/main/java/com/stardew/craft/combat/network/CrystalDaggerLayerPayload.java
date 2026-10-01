package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record CrystalDaggerLayerPayload(int stacks, int durationTicks, boolean playChime) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<CrystalDaggerLayerPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "crystal_dagger_layer")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, CrystalDaggerLayerPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        CrystalDaggerLayerPayload::stacks,
        ByteBufCodecs.VAR_INT,
        CrystalDaggerLayerPayload::durationTicks,
        ByteBufCodecs.BOOL,
        CrystalDaggerLayerPayload::playChime,
        CrystalDaggerLayerPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CrystalDaggerLayerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(CrystalDaggerLayerPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        var level = Objects.requireNonNull(mc.level, "level");
        var player = Objects.requireNonNull(mc.player, "player");
        com.stardew.craft.client.weapon.CrystalVenomVisuals.ensureLevel();
        long nowTick = level.getGameTime();
        if (payload.stacks() > 0) {
            com.stardew.craft.client.weapon.CrystalDaggerLayerClientState.start(nowTick, payload.durationTicks(), payload.stacks());
        } else {
            com.stardew.craft.client.weapon.CrystalDaggerLayerClientState.clear();
        }
        if (payload.playChime() && payload.stacks() > 0) {
            float pitch = 1.0f + 0.2f * Math.max(0, payload.stacks() - 1);
            var sound = SoundEvents.AMETHYST_BLOCK_CHIME;
            if (sound != null) {
                player.playSound(sound, 0.35f, pitch);
            }
        }
    }
}
