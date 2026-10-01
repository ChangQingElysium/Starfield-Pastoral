package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public record OpenDesertFestivalMarlonChallengesPayload(List<ChallengeEntry> entries,
                                                        String activeChallengeId,
                                                        int activeProgress,
                                                        boolean activeRewardClaimed) implements CustomPacketPayload {
    public record ChallengeEntry(String challengeId, String titleKey, String textKey, String objectiveKey,
                                 String targetKey, int targetCount, int rewardEggs) {
    }

    public static final Type<OpenDesertFestivalMarlonChallengesPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_desert_festival_marlon_challenges"));

    public static final StreamCodec<FriendlyByteBuf, OpenDesertFestivalMarlonChallengesPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.entries().size());
            for (ChallengeEntry entry : payload.entries()) {
                buf.writeUtf(entry.challengeId());
                buf.writeUtf(entry.titleKey());
                buf.writeUtf(entry.textKey());
                buf.writeUtf(entry.objectiveKey());
                buf.writeUtf(entry.targetKey());
                buf.writeVarInt(entry.targetCount());
                buf.writeVarInt(entry.rewardEggs());
            }
            buf.writeUtf(payload.activeChallengeId());
            buf.writeVarInt(payload.activeProgress());
            buf.writeBoolean(payload.activeRewardClaimed());
        },
        buf -> {
            int size = buf.readVarInt();
            List<ChallengeEntry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                entries.add(new ChallengeEntry(
                    buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt()));
            }
            return new OpenDesertFestivalMarlonChallengesPayload(entries, buf.readUtf(), buf.readVarInt(), buf.readBoolean());
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenDesertFestivalMarlonChallengesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenDesertFestivalMarlonChallengesPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new com.stardew.craft.client.gui.DesertFestivalMarlonChallengeScreen(payload));
    }
}