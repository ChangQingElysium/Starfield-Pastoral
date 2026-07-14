package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: sends monster slayer goal progress for all goals.
 */
@SuppressWarnings("null")
public record OpenGilGoalsPayload(List<GoalEntry> goals) implements CustomPacketPayload {

    public record GoalEntry(
            String goalKey,
            String translationKey,
            int currentKills,
            int requiredKills,
            boolean claimed,
            boolean hasReward
    ) {}

    public static final Type<OpenGilGoalsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_gil_goals"));

    public static final StreamCodec<FriendlyByteBuf, OpenGilGoalsPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.goals.size());
            for (GoalEntry e : payload.goals) {
                buf.writeUtf(e.goalKey);
                buf.writeUtf(e.translationKey);
                buf.writeVarInt(e.currentKills);
                buf.writeVarInt(e.requiredKills);
                buf.writeBoolean(e.claimed);
                buf.writeBoolean(e.hasReward);
            }
        },
        buf -> {
            int size = buf.readVarInt();
            List<GoalEntry> goals = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                goals.add(new GoalEntry(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                        buf.readBoolean(), buf.readBoolean()));
            }
            return new OpenGilGoalsPayload(goals);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenGilGoalsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenGilGoalsPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new com.stardew.craft.client.gui.GilGoalsScreen(payload.goals()));
    }
}
