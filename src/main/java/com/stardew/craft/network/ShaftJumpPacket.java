package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.mine.MineLadderBlock;
import com.stardew.craft.core.ModMiningDimensions;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 竖井跳入包 - 客户端 → 服务端
 * 玩家确认跳入竖井后发送此包。
 */
public record ShaftJumpPacket(BlockPos shaftPos) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final CustomPacketPayload.Type<ShaftJumpPacket> TYPE =
            new CustomPacketPayload.Type<>(new ResourceLocation(StardewCraft.MODID, "shaft_jump"));

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, ShaftJumpPacket> STREAM_CODEC =
            StreamCodec.composite(
                    com.stardew.craft.port.PortCodecs.BLOCK_POS.cast(),
                    ShaftJumpPacket::shaftPos,
                    ShaftJumpPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理：验证方块确实是竖井后执行跳跃
     */
    public static void handle(ShaftJumpPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
            ServerLevel level = serverPlayer.serverLevel();

            // 安全验证：必须在矿井维度
            if (!level.dimension().equals(ModMiningDimensions.STARDEW_MINING)) return;

            // 安全验证：距离不能太远
            if (serverPlayer.blockPosition().distSqr(packet.shaftPos()) > 100) return;

            // 安全验证：目标方块必须是 shaft=true 的梯子
            BlockState state = level.getBlockState(packet.shaftPos());
            if (!(state.getBlock() instanceof MineLadderBlock)) return;
            if (!state.getValue(MineLadderBlock.SHAFT)) return;

            MineLadderBlock.enterShaft(serverPlayer, level, packet.shaftPos());
        });
    }
}
