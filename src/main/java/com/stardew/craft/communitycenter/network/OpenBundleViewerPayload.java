package com.stardew.craft.communitycenter.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.communitycenter.data.BundleAreaVisibility;
import com.stardew.craft.communitycenter.menu.BundleMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: request to open the read-only bundle viewer from the
 * inventory tab's junimo note icon. Mirrors SDV JunimoNoteMenu(fromGameMenu=true):
 * player can inspect bundle requirements but cannot deposit / purchase / claim.
 */
@SuppressWarnings("null")
public record OpenBundleViewerPayload() implements CustomPacketPayload {

    public static final Type<OpenBundleViewerPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "open_bundle_viewer")
    );

    public static final StreamCodec<ByteBuf, OpenBundleViewerPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenBundleViewerPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenBundleViewerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;

            // Default to first incomplete area; fall back to area 1 (crafts room).
            final int defaultArea = pickDefaultArea(sp);

            // Push a fresh full sync before opening so the read-only viewer always shows
            // up-to-date personal donation progress (otherwise client may have stale or
            // empty BundleClientData and every bundle appears empty).
            BundleSyncPayload.sendFullSync(sp);

            sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new BundleMenu(id, inv, defaultArea, true),
                    Component.translatable("stardewcraft.bundle.viewer")
            ));
        });
    }

    private static int pickDefaultArea(ServerPlayer sp) {
        java.util.UUID uuid = sp.getUUID();
        // SDV parity: open to the first area currently visible on the note map.
        for (int area = 0; area < 6; area++) {
            if (BundleAreaVisibility.shouldNoteAppearInArea(uuid, area)) return area;
        }
        // Fallback: Crafts Room (always visible unless complete).
        return 1;
    }
}
