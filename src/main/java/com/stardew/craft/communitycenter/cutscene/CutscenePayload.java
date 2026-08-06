package com.stardew.craft.communitycenter.cutscene;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S→C: 触发/推进社区中心过场动画。
 * 客户端根据 cutsceneType + phase 执行对应的视觉效果。
 */
@SuppressWarnings("null")
public record CutscenePayload(
        byte cutsceneType,  // 0=AreaRestore, 1=GoodbyeDance
        byte phase,         // 0-3
        int areaId,
        BlockPos centerPos  // 摄像头目标/效果中心
) implements CustomPacketPayload {

    public static final byte TYPE_AREA_RESTORE = 0;
    public static final byte TYPE_GOODBYE_DANCE = 1;

    public static final byte PHASE_FREEZE  = 0;
    public static final byte PHASE_APPEAR  = 1;
    public static final byte PHASE_GLOW    = 2;
    public static final byte PHASE_RESTORE = 3;
    /** Explicitly clears any client overlay left by an interrupted cutscene. */
    public static final byte PHASE_END     = 4;

    public static final Type<CutscenePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "cc_cutscene")
    );

    public static CutscenePayload clearScreen() {
        return new CutscenePayload(TYPE_AREA_RESTORE, PHASE_END, -1, BlockPos.ZERO);
    }

    public static final StreamCodec<ByteBuf, CutscenePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CutscenePayload decode(ByteBuf buf) {
            byte type = buf.readByte();
            byte phase = buf.readByte();
            int areaId = ByteBufCodecs.VAR_INT.decode(buf);
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            return new CutscenePayload(type, phase, areaId, new BlockPos(x, y, z));
        }

        @Override
        public void encode(ByteBuf buf, CutscenePayload payload) {
            buf.writeByte(payload.cutsceneType);
            buf.writeByte(payload.phase);
            ByteBufCodecs.VAR_INT.encode(buf, payload.areaId);
            buf.writeInt(payload.centerPos.getX());
            buf.writeInt(payload.centerPos.getY());
            buf.writeInt(payload.centerPos.getZ());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：转交给 ScreenFade 执行视觉效果。
     */
    public static void handle(CutscenePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(CutscenePayload payload) {
        ScreenFade.onCutscenePacket(payload);
    }
}
