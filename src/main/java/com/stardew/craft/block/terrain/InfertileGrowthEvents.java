package com.stardew.craft.block.terrain;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.crop.StardewCropBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.level.block.CropGrowEvent;

/** Vanilla and cooperative mod plants use MinecraftForge growth hooks; our crops use saved daily progress. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class InfertileGrowthEvents {
    private InfertileGrowthEvents() {}

    @SubscribeEvent
    public static void cropGrowth(CropGrowEvent.Pre event) {
        if (event.getState().getBlock() instanceof StardewCropBlock) return;
        if (event.getLevel() instanceof ServerLevel level
                && TerrainSoils.infertile(level.getBlockState(event.getPos().below()))
                && level.random.nextInt(3) == 0) event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
    }

    @SubscribeEvent
    public static void treeGrowth(BlockGrowFeatureEvent event) {
        if (event.getLevel().getBlockState(event.getPos()).is(BlockTags.SAPLINGS)
                && TerrainSoils.infertile(event.getLevel().getBlockState(event.getPos().below()))
                && event.getRandom().nextFloat() >= .8F) event.setCanceled(true);
    }
}
