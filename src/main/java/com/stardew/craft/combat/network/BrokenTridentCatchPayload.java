package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record BrokenTridentCatchPayload(boolean active, int durationTicks) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<BrokenTridentCatchPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "broken_trident_catch")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, BrokenTridentCatchPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        BrokenTridentCatchPayload::active,
        ByteBufCodecs.VAR_INT,
        BrokenTridentCatchPayload::durationTicks,
        BrokenTridentCatchPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BrokenTridentCatchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(BrokenTridentCatchPayload payload) {
        if (payload.active()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            long nowTick = mc.level != null ? mc.level.getGameTime() : 0L;
            boolean wasActive = mc.player != null && com.stardew.craft.client.weapon.BrokenTridentCatchClientState.isActive(mc.player);
            com.stardew.craft.client.weapon.BrokenTridentCatchClientState.start(nowTick, payload.durationTicks());
            if (!wasActive && mc.player != null) {
                com.stardew.craft.client.weapon.SkillEffectsClient.playFishcatchReady(mc.player);
            }
        } else {
            com.stardew.craft.client.weapon.BrokenTridentCatchClientState.clear();
        }
    }
}
