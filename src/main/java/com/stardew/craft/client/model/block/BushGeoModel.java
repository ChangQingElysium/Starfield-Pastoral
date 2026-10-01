package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.BerryBushBlock;
import com.stardew.craft.blockentity.BushBlockEntity;
import com.stardew.craft.client.hud.StardewTimeHud;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public class BushGeoModel extends BlockbenchModel<BushBlockEntity> {
    private static final ResourceLocation MODEL_SMALL = new ResourceLocation(StardewCraft.MODID, "geo/block/nature/small_bush.geo.json");
    private static final ResourceLocation MODEL_LARGE = new ResourceLocation(StardewCraft.MODID, "geo/block/nature/berry_bush.geo.json");
    private static final ResourceLocation MODEL_SALMONBERRY = new ResourceLocation(StardewCraft.MODID, "geo/block/nature/berry_bush_salmonberry.geo.json");
    private static final ResourceLocation MODEL_BLACKBERRY = new ResourceLocation(StardewCraft.MODID, "geo/block/nature/berry_bush_blackberry.geo.json");

    private static final ResourceLocation TEX_SMALL = new ResourceLocation(StardewCraft.MODID, "textures/block/bush/1.png");
    private static final ResourceLocation TEX_LARGE = new ResourceLocation(StardewCraft.MODID, "textures/block/bush/2.png");
    private static final ResourceLocation TEX_SALMONBERRY = new ResourceLocation(StardewCraft.MODID, "textures/block/bush/2_red.png");
    private static final ResourceLocation TEX_BLACKBERRY = new ResourceLocation(StardewCraft.MODID, "textures/block/bush/2_black.png");

    @Override
    public ResourceLocation getModelResource(BushBlockEntity be) {
        BerryBushBlock.BerryKind berry = currentBerry(be);
        return switch (berry) {
            case SALMONBERRY -> MODEL_SALMONBERRY;
            case BLACKBERRY -> MODEL_BLACKBERRY;
            case NONE -> isSmallBush(be) ? MODEL_SMALL : MODEL_LARGE;
        };
    }

    @Override
    public ResourceLocation getTextureResource(BushBlockEntity be) {
        BerryBushBlock.BerryKind berry = currentBerry(be);
        return switch (berry) {
            case SALMONBERRY -> TEX_SALMONBERRY;
            case BLACKBERRY -> TEX_BLACKBERRY;
            case NONE -> isSmallBush(be) ? TEX_SMALL : TEX_LARGE;
        };
    }

    @Override
    public ResourceLocation getAnimationResource(BushBlockEntity be) {
        return null;
    }

    private static boolean isSmallBush(BushBlockEntity be) {
        return be.getBlockState().is(ModBlocks.SMALL_BUSH.get());
    }

    private static BerryBushBlock.BerryKind currentBerry(BushBlockEntity be) {
        Block block = be.getBlockState().getBlock();
        if (!(block instanceof BerryBushBlock)) {
            return BerryBushBlock.BerryKind.NONE;
        }
        StardewTimeManager time = StardewTimeHud.getClientTimeCache();
        if (be.getLastHarvestAbsoluteDay() == time.getAbsoluteDay()) {
            return BerryBushBlock.BerryKind.NONE;
        }
        BerryBushBlock.BerryKind berry = BerryBushBlock.getBloomBerry(time.getCurrentSeason(), time.getCurrentDay());
        if (berry == BerryBushBlock.BerryKind.NONE) {
            return BerryBushBlock.BerryKind.NONE;
        }
        return BerryBushBlock.hasBerriesToday(be.getBlockPos(), berry, time.getAbsoluteDay())
            ? berry
            : BerryBushBlock.BerryKind.NONE;
    }
}