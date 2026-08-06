package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.GardenPotBlock;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.FarmBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public class FarmlandEventHandler {

    /**
     * 防止星露谷维度的耕地被踩坏
     */
    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        
        // 检查维度是否为星露谷
        if (event.getLevel() instanceof net.minecraft.world.level.Level level 
                && level.dimension() == ModDimensions.STARDEW_VALLEY) {
            event.setCanceled(true);
        }
    }

    /**
     * 星露谷维度破坏耕地 → 掉落黄土而非原版泥土。
     * SDV parity: 锄过的地撸掉后回到原始土壤状态。
     * 但若位于受保护区域（城镇等），由 FarmAreaProtectionEvents 拦截，本处不应处理。
     */
    @SubscribeEvent
    public static void onFarmlandBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getState().getBlock() instanceof FarmBlock)) return;
        // The Garden Pot inherits FarmBlock for moisture support, but it is not terrain.
        if (event.getState().getBlock() instanceof GardenPotBlock) return;
        if (!(event.getLevel() instanceof net.minecraft.world.level.Level level)) return;
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) return;

        // Creative breaking must follow vanilla semantics: remove the block without any drop.
        if (event.getPlayer().isCreative()) return;

        BlockPos pos = event.getPos();

        // 区域保护：城镇/他人农场等不允许破坏耕地（创造模式照常通过）
        if (event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp
                && !sp.isCreative()
                && !FarmAreaProtectionEvents.canModifyAt(sp, pos)) {
            event.setCanceled(true);
            sp.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("stardewcraft.farm.build_farm_only"), true);
            return;
        }

        // 取消原版破坏（避免掉落原版泥土），把耕地直接移除变成空气，并掉落黄土
        event.setCanceled(true);
        level.removeBlock(pos, false);
        net.minecraft.world.level.block.Block.popResource(level, pos,
                new ItemStack(ModBlocks.YELLOW_DIRT.get()));
    }
}
