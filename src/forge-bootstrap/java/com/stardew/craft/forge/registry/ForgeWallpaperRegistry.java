package com.stardew.craft.forge.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.stardew.craft.block.utility.WallpaperBlock;
import com.stardew.craft.forge.ForgeBootstrap;
import com.stardew.craft.item.WallpaperBlockItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The 26 MoreWalls stable-ID styles (visual indices 112-137).
 *
 * <p>This registry deliberately does not include the legacy {@code wallpaper_block}
 * or its 0-111 styles. Legacy fallback and old-save migration remain outside
 * this static-content slice until the DecorBlockEntity/FlooringBlock subsystem
 * is ported as one complete Forge dependency closure.</p>
 */
public final class ForgeWallpaperRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ForgeBootstrap.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ForgeBootstrap.MOD_ID);

    public static final Map<String, RegistryObject<WallpaperBlock>> BLOCK_STYLES = registerBlocks();
    public static final Map<String, RegistryObject<Item>> ITEM_STYLES = registerItems();

    private static Map<String, RegistryObject<WallpaperBlock>> registerBlocks() {
        LinkedHashMap<String, RegistryObject<WallpaperBlock>> result = new LinkedHashMap<>();
        for (int i = 0; i < 26; i++) {
            String styleId = "MoreWalls:" + i;
            String path = "wallpaper_morewalls_" + i;
            result.put(styleId, BLOCKS.register(path, () -> new WallpaperBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOL)
                            .sound(SoundType.WOOL)
                            .strength(0.8F, 1.0F), styleId)));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, RegistryObject<Item>> registerItems() {
        LinkedHashMap<String, RegistryObject<Item>> result = new LinkedHashMap<>();
        for (int i = 0; i < 26; i++) {
            String styleId = "MoreWalls:" + i;
            String path = "wallpaper_morewalls_" + i;
            RegistryObject<WallpaperBlock> block = BLOCK_STYLES.get(styleId);
            result.put(styleId, ITEMS.register(path, () -> new WallpaperBlockItem(
                    block.get(), "stardewcraft.type.hidden", new Item.Properties().stacksTo(999))));
        }
        return Collections.unmodifiableMap(result);
    }

    private ForgeWallpaperRegistry() {
    }
}
