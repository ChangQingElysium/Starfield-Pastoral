package com.stardew.craft.shop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Installs the prize ticket machine at its authored location in Lewis's house. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class PrizeTicketMachineInstaller {
    public static final BlockPos MACHINE_POS = new BlockPos(51, 51, 21);

    private PrizeTicketMachineInstaller() {
    }

    public static void ensurePlaced(ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }

        BlockState expected = ModBlocks.PRIZE_TICKET_MACHINE.get().defaultBlockState()
                .setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
                .setValue(MapDecorStaticBlock.FACING, Direction.SOUTH);
        if (level.getBlockState(MACHINE_POS).equals(expected)) {
            return;
        }

        level.destroyBlock(MACHINE_POS, false);
        level.setBlock(MACHINE_POS, expected, Block.UPDATE_ALL);
        ModBlocks.PRIZE_TICKET_MACHINE.get().setPlacedBy(
                level, MACHINE_POS, expected, null, ItemStack.EMPTY);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ensurePlaced(level);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ensurePlaced(player.serverLevel());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ensurePlaced(player.serverLevel());
        }
    }
}
