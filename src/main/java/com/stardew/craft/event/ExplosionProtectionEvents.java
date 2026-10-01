package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.portal.PortalTriggerBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.ExplosionEvent;

/**
 * 爆炸保护：某些功能方块不应被任何 vanilla explosion 路径移除。
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ExplosionProtectionEvents {

    private ExplosionProtectionEvents() {
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        event.getAffectedBlocks().removeIf(pos -> isExplosionProtected(level.getBlockState(pos)));
    }

    private static boolean isExplosionProtected(BlockState state) {
        return state.is(ModBlocks.MINE_LADDER.get())
            || state.getBlock() instanceof com.stardew.craft.block.mine.MineLadderBlock
            || state.getBlock() instanceof PortalTriggerBlock;
    }
}