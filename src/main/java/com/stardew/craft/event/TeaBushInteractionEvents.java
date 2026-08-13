package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.nature.TeaBushBlock;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.interior.SunroomService;
import com.stardew.craft.manager.TeaBushManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Exact interaction bridge for SDV tea bushes, which aren't normal mineable crop blocks. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class TeaBushInteractionEvents {
    private TeaBushInteractionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        BlockState clicked = event.getLevel().getBlockState(event.getPos());
        if (!(clicked.getBlock() instanceof TeaBushBlock bush)) {
            return;
        }

        BlockPos lowerPos = TeaBushBlock.lowerPos(clicked, event.getPos());
        if (SunroomService.isCentralTeaBush(event.getLevel(), lowerPos)) {
            event.setCanceled(true);
            return;
        }

        if (event.isCanceled()) {
            return;
        }

        ItemStack tool = event.getEntity().getItemInHand(InteractionHand.MAIN_HAND);
        if (!tool.is(ModItems.GOLDEN_SCYTHE.get()) && !tool.is(ModItems.IRIDIUM_SCYTHE.get())) {
            return;
        }

        event.setCanceled(true);
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        bush.interact(player, level, lowerPos);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        level.getServer().tell(new TickTask(level.getServer().getTickCount() + 1,
                () -> {
                    TeaBushManager.get(level).synchronizeChunk(level, chunk.getPos());
                    if (chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(
                            SunroomService.CENTRAL_TEA_BUSH))) {
                        SunroomService.ensurePlaced(level);
                    }
                }));
    }

}
