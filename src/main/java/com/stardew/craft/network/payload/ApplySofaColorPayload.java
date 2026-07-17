package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.CushionBlock;
import com.stardew.craft.block.utility.OfficeChair2Block;
import com.stardew.craft.block.utility.OfficeStoolBlock;
import com.stardew.craft.block.utility.OakTableBlock;
import com.stardew.craft.block.utility.SofaBlock;
import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.entity.seat.CushionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public record ApplySofaColorPayload(BlockPos targetPos, int colorIndex, int targetEntityId) implements CustomPacketPayload {
    public ApplySofaColorPayload(BlockPos targetPos, int colorIndex) {
        this(targetPos, colorIndex, -1);
    }

    @SuppressWarnings("null")
    public static final Type<ApplySofaColorPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "apply_sofa_color"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, ApplySofaColorPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBlockPos(payload.targetPos());
            buf.writeVarInt(payload.colorIndex());
            buf.writeVarInt(payload.targetEntityId());
        },
        buf -> new ApplySofaColorPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(ApplySofaColorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            boolean holdingPaintbrush = player.getMainHandItem().is(ModItems.PAINTBRUSH.get())
                || player.getOffhandItem().is(ModItems.PAINTBRUSH.get());
            if (!holdingPaintbrush) {
                return;
            }

            int clamped = WoodenChestColorPalette.clampIndex(payload.colorIndex());
            if (clamped < 0) {
                clamped = WoodenChestColorPalette.defaultColorIndex();
            }

            if (payload.targetEntityId() >= 0) {
                if (player.level().getEntity(payload.targetEntityId()) instanceof CushionEntity cushion
                        && player.distanceToSqr(cushion) <= 100.0D) {
                    cushion.setColor(clamped);
                }
                return;
            }

            if (player.distanceToSqr(payload.targetPos().getX() + 0.5D, payload.targetPos().getY() + 0.5D, payload.targetPos().getZ() + 0.5D) > 100.0D) {
                return;
            }

            BlockState state = player.level().getBlockState(payload.targetPos());

            if (state.getBlock() instanceof SofaBlock) {
                for (BlockPos sofaPos : collectConnectedSofas(payload.targetPos(), player.level())) {
                    BlockState sofaState = player.level().getBlockState(sofaPos);
                    if (!(sofaState.getBlock() instanceof SofaBlock) || !sofaState.hasProperty(SofaBlock.COLOR)) {
                        continue;
                    }
                    BlockState updated = sofaState.setValue(SofaBlock.COLOR, clamped);
                    if (updated != sofaState) {
                        player.level().setBlock(sofaPos, updated, 3);
                    }
                }
                return;
            }

            if (state.getBlock() instanceof OakTableBlock
                && state.hasProperty(OakTableBlock.HAS_CLOTH)
                && state.hasProperty(OakTableBlock.CLOTH_STYLE)
                && state.getValue(OakTableBlock.HAS_CLOTH)
                && OakTableBlock.isDyeableClothStyle(state.getValue(OakTableBlock.CLOTH_STYLE))) {
                OakTableBlock.applyClothColorToConnectedTables(player.level(), payload.targetPos(), clamped);
                return;
            }

            if (state.getBlock() instanceof CushionBlock && state.hasProperty(CushionBlock.COLOR)) {
                BlockState updated = state.setValue(CushionBlock.COLOR, clamped);
                if (updated != state) {
                    player.level().setBlock(payload.targetPos(), updated, 3);
                }
                return;
            }

            if (state.getBlock() instanceof OfficeStoolBlock && state.hasProperty(OfficeStoolBlock.COLOR)) {
                BlockState updated = state.setValue(OfficeStoolBlock.COLOR, clamped);
                if (updated != state) {
                    player.level().setBlock(payload.targetPos(), updated, 3);
                }
                return;
            }

            if (state.getBlock() instanceof OfficeChair2Block && state.hasProperty(OfficeChair2Block.COLOR)) {
                BlockState updated = state.setValue(OfficeChair2Block.COLOR, clamped);
                if (updated != state) {
                    player.level().setBlock(payload.targetPos(), updated, 3);
                }
                return;
            }

            if (state.getBlock() instanceof com.stardew.craft.block.utility.DyeableChairBlock && state.hasProperty(com.stardew.craft.block.utility.DyeableChairBlock.COLOR)) {
                com.stardew.craft.block.utility.DyeableChairBlock chairBlock =
                    (com.stardew.craft.block.utility.DyeableChairBlock) state.getBlock();
                BlockPos mainPos = chairBlock.resolveMainPos(player.level(), payload.targetPos(), state);
                BlockState mainState = player.level().getBlockState(mainPos);
                if (!(mainState.getBlock() instanceof com.stardew.craft.block.utility.DyeableChairBlock)
                    || !mainState.hasProperty(com.stardew.craft.block.utility.DyeableChairBlock.COLOR)) {
                    return;
                }

                BlockState updatedMain = mainState.setValue(com.stardew.craft.block.utility.DyeableChairBlock.COLOR, clamped);
                if (updatedMain != mainState) {
                    player.level().setBlock(mainPos, updatedMain, 3);
                }

                // Keep extension cells synchronized with MAIN so clicking either part is stable.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            BlockPos candidatePos = mainPos.offset(dx, dy, dz);
                            BlockState candidateState = player.level().getBlockState(candidatePos);
                            if (!(candidateState.getBlock() instanceof com.stardew.craft.block.utility.DyeableChairBlock)
                                || !candidateState.hasProperty(com.stardew.craft.block.utility.DyeableChairBlock.COLOR)) {
                                continue;
                            }
                            BlockPos resolvedMain = chairBlock.resolveMainPos(player.level(), candidatePos, candidateState);
                            if (!mainPos.equals(resolvedMain)) {
                                continue;
                            }
                            BlockState updatedCandidate = candidateState.setValue(com.stardew.craft.block.utility.DyeableChairBlock.COLOR, clamped);
                            if (updatedCandidate != candidateState) {
                                player.level().setBlock(candidatePos, updatedCandidate, 3);
                            }
                        }
                    }
                }
            }
        });
    }

    @SuppressWarnings("null")
    private static Set<BlockPos> collectConnectedSofas(BlockPos start, net.minecraft.world.level.Level level) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }

            BlockState currentState = level.getBlockState(current);
            if (!(currentState.getBlock() instanceof SofaBlock)) {
                continue;
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if (visited.contains(next)) {
                    continue;
                }
                BlockState nextState = level.getBlockState(next);
                if (nextState.getBlock() instanceof SofaBlock) {
                    queue.add(next);
                }
            }
        }
        return visited;
    }
}
