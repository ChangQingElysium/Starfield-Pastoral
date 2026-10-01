package com.stardew.craft.client.model.block;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.LuauFestivalDecorBlockEntity;
import com.stardew.craft.client.hud.StardewTimeHud;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchModel;

public class LuauFestivalDecorGeoModel extends BlockbenchModel<LuauFestivalDecorBlockEntity> {
    private static final ResourceLocation WIZARD_MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/decor/wizard_cauldron.geo.json");
    private static final ResourceLocation WIZARD_TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/decor/common/wizard_cauldron.png");
    private static final ResourceLocation WIZARD_ANIMATION = new ResourceLocation(StardewCraft.MODID, "animations/block/festival/wizard_cauldron.animation.json");
    private static final ResourceLocation SOUP_ANIMATION = new ResourceLocation(StardewCraft.MODID, "animations/block/festival/luau_soup_pot.animation.json");
    private static final ResourceLocation SOUP_POT_MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/festival/luau_soup_pot.geo.json");
    private static final ResourceLocation SOUP_POT_TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/festival/luau_soup_pot.png");
    private static final ResourceLocation TOTEM_MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/festival/luau_totem.geo.json");
    private static final ResourceLocation TOTEM_TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/festival/luau_totem.png");
    private static final ResourceLocation WINTER_STAR_TREE_MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/festival/winter_star_tree.geo.json");
    private static final ResourceLocation WINTER_STAR_TREE_TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/festival/winter_star_tree.png");
    private static final ResourceLocation SQUID_FEST_PROMO_POSTER_MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/festival/squid_fest_promo_poster.geo.json");
    private static final ResourceLocation SQUID_FEST_PROMO_POSTER_TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/block/festival/squid_fest_promo_poster.png");
    private static final ResourceLocation SQUID_FEST_REQUIREMENT_POSTER_MODEL = new ResourceLocation(StardewCraft.MODID, "geo/block/festival/squid_fest_requirement_poster.geo.json");
    private static final ResourceLocation SQUID_FEST_REQUIREMENT_POSTER_TEXTURE_12 = new ResourceLocation(StardewCraft.MODID, "textures/block/festival/squid_fest_requirement_poster_12.png");
    private static final ResourceLocation SQUID_FEST_REQUIREMENT_POSTER_TEXTURE_13 = new ResourceLocation(StardewCraft.MODID, "textures/block/festival/squid_fest_requirement_poster_13.png");

    @Override
    public ResourceLocation getModelResource(LuauFestivalDecorBlockEntity animatable) {
        if (animatable.getBlockState().is(ModBlocks.WIZARD_CAULDRON.get())) return WIZARD_MODEL;
        if (animatable.getBlockState().is(ModBlocks.WINTER_STAR_TREE.get())) {
            return WINTER_STAR_TREE_MODEL;
        }
        if (animatable.getBlockState().is(ModBlocks.LUAU_TOTEM.get())) {
            return TOTEM_MODEL;
        }
        if (animatable.getBlockState().is(ModBlocks.SQUID_FEST_PROMO_POSTER.get())) {
            return SQUID_FEST_PROMO_POSTER_MODEL;
        }
        if (animatable.getBlockState().is(ModBlocks.SQUID_FEST_REQUIREMENT_POSTER.get())) {
            return SQUID_FEST_REQUIREMENT_POSTER_MODEL;
        }
        return SOUP_POT_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LuauFestivalDecorBlockEntity animatable) {
        if (animatable.getBlockState().is(ModBlocks.WIZARD_CAULDRON.get())) return WIZARD_TEXTURE;
        if (animatable.getBlockState().is(ModBlocks.WINTER_STAR_TREE.get())) {
            return WINTER_STAR_TREE_TEXTURE;
        }
        if (animatable.getBlockState().is(ModBlocks.LUAU_TOTEM.get())) {
            return TOTEM_TEXTURE;
        }
        if (animatable.getBlockState().is(ModBlocks.SQUID_FEST_PROMO_POSTER.get())) {
            return SQUID_FEST_PROMO_POSTER_TEXTURE;
        }
        if (animatable.getBlockState().is(ModBlocks.SQUID_FEST_REQUIREMENT_POSTER.get())) {
            return isWinter13() ? SQUID_FEST_REQUIREMENT_POSTER_TEXTURE_13 : SQUID_FEST_REQUIREMENT_POSTER_TEXTURE_12;
        }
        return SOUP_POT_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(LuauFestivalDecorBlockEntity animatable) {
        if (animatable.getBlockState().is(ModBlocks.WIZARD_CAULDRON.get())) return WIZARD_ANIMATION;
        if (animatable.getBlockState().is(ModBlocks.LUAU_SOUP_POT.get())) return SOUP_ANIMATION;
        return null;
    }

    private boolean isWinter13() {
        StardewTimeManager time = StardewTimeHud.getClientTimeCache();
        return time != null && time.getCurrentSeason() == 3 && time.getCurrentDay() == 13;
    }
}
