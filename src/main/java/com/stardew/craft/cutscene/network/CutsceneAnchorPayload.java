package com.stardew.craft.cutscene.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: register a named cutscene anchor origin for the current player.
 * <p>
 * Used for per-player subspaces (CC interior, greenhouse, farm, ...). Cutscene
 * JSON commands with an {@code "anchor": "<name>"} field will have their
 * coordinates resolved as origin + offset on the client.
 *
 * @param name  anchor name, e.g. "cc_interior", "greenhouse_interior", "farm"
 * @param x,y,z absolute world coordinates of this player's anchor origin
 */
public record CutsceneAnchorPayload(String name, double x, double y, double z)
        implements CustomPacketPayload {


    public static final Type<CutsceneAnchorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "cutscene_anchor"));

    public static final StreamCodec<ByteBuf, CutsceneAnchorPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CutsceneAnchorPayload::name,
            ByteBufCodecs.DOUBLE,      CutsceneAnchorPayload::x,
            ByteBufCodecs.DOUBLE,      CutsceneAnchorPayload::y,
            ByteBufCodecs.DOUBLE,      CutsceneAnchorPayload::z,
            CutsceneAnchorPayload::new);

    public static void handle(CutsceneAnchorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            CutsceneAnchorRegistry.set(payload.name, payload.x, payload.y, payload.z);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
