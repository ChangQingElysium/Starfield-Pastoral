package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntime;
import com.stardew.craft.api.v1.agriculture.StardewCropState;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.manager.CropGrowthManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GrowCropsPayload() implements CustomPacketPayload {
    
    @SuppressWarnings("null")
    public static final Type<GrowCropsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "grow_crops"));
    public static final StreamCodec<ByteBuf, GrowCropsPayload> STREAM_CODEC = StreamCodec.unit(new GrowCropsPayload());

    @Override
    public Type<GrowCropsPayload> type() {
        return TYPE;
    }

    public static void handle(GrowCropsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!player.isCreative() || !player.hasPermissions(2)) {
                return;
            }
            Level level = player.level();
            BlockPos playerPos = player.blockPosition();

            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }
            
            // 遍历周围5格范围内的所有方块 (服务器端逻辑)
            for (int x = -5; x <= 5; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -5; z <= 5; z++) {
                        BlockPos pos = playerPos.offset(x, y, z);
                        @SuppressWarnings("null")
                        BlockState state = level.getBlockState(pos);
                        
                        StardewCropState runtimeCrop =
                                StardewCropRuntime.inspect(serverLevel, pos);
                        if (runtimeCrop != null
                                && runtimeCrop.root().equals(pos)) {
                            CropGrowthManager cropManager = CropGrowthManager.get(serverLevel);
                            // Debug: 视为“推进一天且已浇水”，避免把所有作物误导成只走 AGE 0-3。
                            StardewCropRuntime.growOneDay(
                                    serverLevel, pos, true, false);
                            cropManager.setDirty();

                            // SDV 对齐：成熟当日 1% 概率长成 3×3 巨型作物
                            BlockState afterGrow = serverLevel.getBlockState(pos);
                            if (afterGrow.getBlock() instanceof StardewCropBlock matureCheck
                                    && afterGrow.hasProperty(StardewCropBlock.AGE)
                                    && afterGrow.getValue(StardewCropBlock.AGE) == StardewCropBlock.MAX_AGE) {
                                com.stardew.craft.spawner.GiantCropSpawner.tryRoll(serverLevel, pos, matureCheck);
                            }
                        }
                    }
                }
            }
        });
    }
}
