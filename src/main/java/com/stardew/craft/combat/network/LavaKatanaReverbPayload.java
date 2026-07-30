package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record LavaKatanaReverbPayload(boolean active, int durationTicks) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<LavaKatanaReverbPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "lava_katana_reverb_state")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, LavaKatanaReverbPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        LavaKatanaReverbPayload::active,
        ByteBufCodecs.VAR_INT,
        LavaKatanaReverbPayload::durationTicks,
        LavaKatanaReverbPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LavaKatanaReverbPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(LavaKatanaReverbPayload payload) {
        if (payload.active()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            long nowTick = mc.level != null ? mc.level.getGameTime() : 0L;
            com.stardew.craft.client.weapon.LavaKatanaReverbClientState.start(nowTick, payload.durationTicks());
            if (mc.player != null) {
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(
                    "lava_katana_reverb",
                    mc.player
                );
            }
        } else {
            com.stardew.craft.client.weapon.LavaKatanaReverbClientState.clear();
        }
    }
}
