package com.stardew.craft.communitycenter.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.communitycenter.data.BundleAreaVisibility;
import com.stardew.craft.communitycenter.menu.BundleMenu;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C → S: cycle the read-only bundle viewer to the next/previous area.
 * Mirrors SDV JunimoNoteMenu.SwapPage(direction) — skips areas that
 * do not satisfy {@code shouldNoteAppearInArea} (simple rule parity:
 * unlocked by CC progress OR already complete).
 */
@SuppressWarnings("null")
public record SwitchBundleViewerAreaPayload(int direction) implements CustomPacketPayload {

    public static final Type<SwitchBundleViewerAreaPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "switch_bundle_viewer_area")
    );

    public static final StreamCodec<ByteBuf, SwitchBundleViewerAreaPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SwitchBundleViewerAreaPayload::direction,
                    SwitchBundleViewerAreaPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwitchBundleViewerAreaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof BundleMenu menu)) return;
            if (!menu.isReadOnly()) return;

            int direction = Integer.signum(payload.direction);
            if (direction == 0) return;

            final int areaCount = 6;
            int area = menu.getAreaId();
            java.util.UUID uuid = sp.getUUID();

            for (int i = 0; i < areaCount; i++) {
                area += direction;
                if (area < 0) area += areaCount;
                if (area >= areaCount) area -= areaCount;
                if (BundleAreaVisibility.shouldNoteAppearInArea(uuid, area)) {
                    menu.setAreaId(area);
                    return;
                }
            }
        });
    }
}
