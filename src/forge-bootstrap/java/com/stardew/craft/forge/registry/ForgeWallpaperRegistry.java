package com.stardew.craft.forge.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.stardew.craft.block.utility.WallpaperBlock;
import com.stardew.craft.block.utility.LegacyWallpaperBlock;
import com.stardew.craft.block.utility.FlooringBlock;
import com.stardew.craft.forge.ForgeBootstrap;
import com.stardew.craft.item.WallpaperBlockItem;
import com.stardew.craft.item.StardewSimpleBlockItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Stable-ID wallpaper styles plus the legacy wallpaper/flooring blocks.
 *
 * <p>The 138 stable IDs are registered independently from the legacy block so
 * an old save can be migrated without losing its visual style.</p>
 */
public final class ForgeWallpaperRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ForgeBootstrap.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ForgeBootstrap.MOD_ID);

    public static final RegistryObject<LegacyWallpaperBlock> WALLPAPER_BLOCK = BLOCKS.register(
            "wallpaper_block", () -> new LegacyWallpaperBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL).sound(SoundType.WOOL).strength(0.8F, 1.0F)));
    public static final RegistryObject<Block> FLOORING_BLOCK = BLOCKS.register(
            "flooring_block", () -> new FlooringBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(1.0F, 1.0F)));

    public static final Map<String, RegistryObject<WallpaperBlock>> BLOCK_STYLES = registerBlocks();
    public static final Map<String, RegistryObject<Item>> ITEM_STYLES = registerItems();
    public static final RegistryObject<Item> WALLPAPER_ITEM = ITEMS.register("wallpaper_block", () ->
            new WallpaperBlockItem(BLOCK_STYLES.get("0").get(), "stardewcraft.type.utility", new Item.Properties().stacksTo(999)));
    public static final RegistryObject<Item> FLOORING_ITEM = ITEMS.register("flooring_block", () ->
            new StardewSimpleBlockItem(FLOORING_BLOCK.get(), "stardewcraft.type.utility", -1, new Item.Properties().stacksTo(999)));

    private static Map<String, RegistryObject<WallpaperBlock>> registerBlocks() {
        LinkedHashMap<String, RegistryObject<WallpaperBlock>> result = new LinkedHashMap<>();
        for (int i = 0; i < 112; i++) {
            String styleId = Integer.toString(i);
            result.put(styleId, registerStyle(styleId, "wallpaper_" + i));
        }
        for (int i = 0; i < 26; i++) {
            String styleId = "MoreWalls:" + i;
            String path = "wallpaper_morewalls_" + i;
            result.put(styleId, registerStyle(styleId, path));
        }
        return Collections.unmodifiableMap(result);
    }

    private static RegistryObject<WallpaperBlock> registerStyle(String styleId, String path) {
        return BLOCKS.register(path, () -> new WallpaperBlock(
                net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                        .mapColor(MapColor.WOOL).sound(SoundType.WOOL).strength(0.8F, 1.0F), styleId));
    }

    private static Map<String, RegistryObject<Item>> registerItems() {
        LinkedHashMap<String, RegistryObject<Item>> result = new LinkedHashMap<>();
        for (int i = 1; i < 112; i++) {
            String styleId = Integer.toString(i);
            RegistryObject<WallpaperBlock> block = BLOCK_STYLES.get(styleId);
            result.put(styleId, registerStyleItem("wallpaper_" + i, block));
        }
        for (int i = 0; i < 26; i++) {
            String styleId = "MoreWalls:" + i;
            String path = "wallpaper_morewalls_" + i;
            RegistryObject<WallpaperBlock> block = BLOCK_STYLES.get(styleId);
            result.put(styleId, registerStyleItem(path, block));
        }
        return Collections.unmodifiableMap(result);
    }

    private static RegistryObject<Item> registerStyleItem(String path, RegistryObject<WallpaperBlock> block) {
        return ITEMS.register(path, () -> new WallpaperBlockItem(
                block.get(), "stardewcraft.type.hidden", new Item.Properties().stacksTo(999)));
    }

    private ForgeWallpaperRegistry() {
    }
}
