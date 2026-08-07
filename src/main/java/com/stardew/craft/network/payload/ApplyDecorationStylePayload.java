package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.deco.DecorationService;
import com.stardew.craft.deco.DecorationStyleRegistry;
import com.stardew.craft.deco.DecorationType;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

public record ApplyDecorationStylePayload(
    String decorationType, BlockPos targetPos, String styleId,
    boolean regionMode, @Nullable BlockPos cornerA, @Nullable BlockPos cornerB
) implements CustomPacketPayload {

    /** Convenience constructor for flood-fill mode (backward compatible). */
    public ApplyDecorationStylePayload(String decorationType, BlockPos targetPos, String styleId) {
        this(decorationType, targetPos, styleId, false, null, null);
    }

    /** Convenience constructor for region-select mode. */
    public static ApplyDecorationStylePayload region(String decorationType, BlockPos targetPos, String styleId,
                                                      BlockPos cornerA, BlockPos cornerB) {
        return new ApplyDecorationStylePayload(decorationType, targetPos, styleId, true, cornerA, cornerB);
    }

    @SuppressWarnings("null")
    public static final Type<ApplyDecorationStylePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "apply_decoration_style"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, ApplyDecorationStylePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.decorationType(), 32);
            buf.writeBlockPos(payload.targetPos());
            buf.writeUtf(payload.styleId(), 64);
            buf.writeBoolean(payload.regionMode());
            if (payload.regionMode() && payload.cornerA() != null && payload.cornerB() != null) {
                buf.writeBlockPos(payload.cornerA());
                buf.writeBlockPos(payload.cornerB());
            }
        },
        buf -> {
            String decoType = buf.readUtf(32);
            BlockPos target = buf.readBlockPos();
            String style = buf.readUtf(64);
            boolean region = buf.readBoolean();
            BlockPos cA = null, cB = null;
            if (region) {
                cA = buf.readBlockPos();
                cB = buf.readBlockPos();
            }
            return new ApplyDecorationStylePayload(decoType, target, style, region, cA, cB);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(ApplyDecorationStylePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            DecorationType type;
            try {
                type = DecorationType.valueOf(payload.decorationType());
            } catch (IllegalArgumentException ex) {
                HudHintPayload.send(player, "stardewcraft.decoration.error.invalid_type");
                return;
            }

            if (DecorationStyleRegistry.getStyle(type, payload.styleId()) == null) {
                HudHintPayload.send(player, "stardewcraft.decoration.error.unknown_style");
                return;
            }

            if (!PlayerStardewDataAPI.getData(player).isDecorationUnlocked(type, payload.styleId())) {
                HudHintPayload.send(player, "stardewcraft.decoration.error.locked");
                return;
            }

            boolean holdingPaintbrush = player.getMainHandItem().is(ModItems.PAINTBRUSH.get()) || player.getOffhandItem().is(ModItems.PAINTBRUSH.get());
            if (!holdingPaintbrush) {
                HudHintPayload.send(player, "stardewcraft.decoration.error.paintbrush_required");
                return;
            }

            if (player.distanceToSqr(payload.targetPos().getX() + 0.5, payload.targetPos().getY() + 0.5, payload.targetPos().getZ() + 0.5) > 4096.0) {
                HudHintPayload.send(player, "stardewcraft.decoration.error.target_too_far");
                return;
            }

            int changed;
            if (payload.regionMode() && payload.cornerA() != null && payload.cornerB() != null) {
                // Validate region size (max 50x50x50 = 125000 blocks)
                int dx = Math.abs(payload.cornerA().getX() - payload.cornerB().getX()) + 1;
                int dy = Math.abs(payload.cornerA().getY() - payload.cornerB().getY()) + 1;
                int dz = Math.abs(payload.cornerA().getZ() - payload.cornerB().getZ()) + 1;
                if ((long) dx * dy * dz > 125000) {
                    HudHintPayload.send(player, "stardewcraft.decoration.error.region_too_large");
                    return;
                }
                // Validate corner distance
                if (player.distanceToSqr(payload.cornerA().getX() + 0.5, payload.cornerA().getY() + 0.5, payload.cornerA().getZ() + 0.5) > 4096.0
                    || player.distanceToSqr(payload.cornerB().getX() + 0.5, payload.cornerB().getY() + 0.5, payload.cornerB().getZ() + 0.5) > 4096.0) {
                    HudHintPayload.send(player, "stardewcraft.decoration.error.corners_too_far");
                    return;
                }
                changed = DecorationService.applyToRegion(player.level(), payload.cornerA(), payload.cornerB(), type, payload.styleId());
            } else {
                changed = DecorationService.applyToConnected(player.level(), payload.targetPos(), type, payload.styleId());
            }
            if (changed <= 0) {
                HudHintPayload.send(player, "stardewcraft.decoration.error.nothing_found");
                StardewCraft.LOGGER.warn("Decoration apply produced 0 changes. Player={}, pos={}, type={}, style={}",
                    player.getGameProfile().getName(), payload.targetPos(), payload.decorationType(), payload.styleId());
            }
        });
    }
}
