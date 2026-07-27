package com.stardew.craft.communitycenter.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.communitycenter.menu.BundleMenu;
import com.stardew.craft.communitycenter.menu.BundleRewardMenu;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterVariantRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: request to open the reward grab menu for a bundle area.
 * SDV parity: openRewardsMenu() → ItemGrabMenu for reward items.
 */
@SuppressWarnings("null")
public record OpenBundleRewardsPayload(
        int areaId
) implements CustomPacketPayload {

    public static final Type<OpenBundleRewardsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_bundle_rewards")
    );

    public static final StreamCodec<ByteBuf, OpenBundleRewardsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, OpenBundleRewardsPayload::areaId,
                    OpenBundleRewardsPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenBundleRewardsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            // Must currently be in a BundleMenu
            if (!(sp.containerMenu instanceof BundleMenu)) return;

            int areaId = payload.areaId;

            // Verify there are actually rewards to claim
            CommunityCenterSavedData data = CommunityCenterSavedData.get();
            boolean hasRewards = false;
            for (BundleDefinition def
                    : StardewCommunityCenterVariantRegistry.area(sp.getUUID(), areaId)) {
                if (data.isRewardAvailable(sp.getUUID(), def.bundleId())) {
                    hasRewards = true;
                    break;
                }
            }
            if (!hasRewards) return;

            // Open reward grab menu
            sp.openMenu(new SimpleMenuProvider(
                    (id, inv, player) -> new BundleRewardMenu(id, inv, areaId),
                    Component.translatable("stardewcraft.bundle.rewards")
            ));
        });
    }
}
