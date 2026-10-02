package com.stardew.craft.gingerisland;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.item.StardewBlockItem;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Stable IDs for the complete authored catalog; variants are recorded by the export ledger. */
public final class GingerIslandBlocks {
    private GingerIslandBlocks() {}

    public static Map<String, DeferredBlock<Block>> registerBlocks(DeferredRegister.Blocks registry) {
        Map<String, DeferredBlock<Block>> blocks = new LinkedHashMap<>();
        for (var asset : GingerIslandAssets.blocks()) {
            blocks.put(asset.id(), registry.register(asset.id(), () -> create(asset)));
        }
        return Collections.unmodifiableMap(blocks);
    }

    private static Block create(GingerIslandAssets.BlockAsset asset) {
        var props = Block.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.5F).sound(SoundType.WOOD);
        if (asset.type().equals("natural_rock")) {
            props.mapColor(MapColor.STONE).strength(3F).sound(SoundType.STONE);
        } else if (asset.type().equals("natural_ground")) {
            props.mapColor(MapColor.DIRT).strength(.5F).sound(SoundType.GRAVEL);
        } else if (asset.type().equals("natural_grass") || asset.type().equals("natural_flower")
                || asset.type().equals("natural_aquatic") || asset.type().equals("forage")) {
            props.mapColor(MapColor.PLANT).strength(.2F).sound(SoundType.GRASS);
        }
        return switch (asset.kind()) {
            case "volcano_floor" -> new GingerIslandSurfaceBlock(props.sound(SoundType.STONE).strength(3F), 6, 75);
            case "caldera_floor" -> new GingerIslandSurfaceBlock(props.sound(SoundType.STONE).strength(3F), 3, 80);
            case "cube" -> new Block(asset.type().equals("natural_ground")
                    ? props : props.sound(SoundType.STONE).strength(3F));
            case "cooled_lava" -> new CooledLavaBlock(props.sound(SoundType.STONE).strength(-1F, 3600000F).noOcclusion().noLootTable());
            case "volcano_switch" -> new VolcanoFloorSwitchBlock(props.sound(SoundType.METAL).strength(-1F, 3600000F).noOcclusion());
            case "bed" -> new TropicalBedBlock(props.noOcclusion(), asset.model());
            case "kitchen" -> new IslandWorkstationBlock(props.sound(SoundType.METAL).noOcclusion(), asset.model(), IslandWorkstationBlock.Kind.KITCHEN);
            case "forge" -> new IslandWorkstationBlock(props.sound(SoundType.STONE).strength(-1F, 3600000F).noOcclusion(), asset.model(), IslandWorkstationBlock.Kind.FORGE);
            case "tv" -> new com.stardew.craft.block.tv.TVBlock(props.noOcclusion(), asset.model());
            case "heavy_tapper" -> new HeavyTapperBlock(props.sound(SoundType.METAL).noOcclusion(), asset.model());
            case "ostrich_incubator" -> new OstrichIncubatorBlock(props.sound(SoundType.METAL).noOcclusion());
            case "fireplace" -> new IslandFlameBlock(props.sound(SoundType.STONE).noOcclusion(), asset.model(), true,
                    new net.minecraft.world.phys.Vec3(1, 8.0 / 16, 5.0 / 16));
            case "altar_torch" -> new IslandFlameBlock(props.noOcclusion(), asset.model(), false,
                    new net.minecraft.world.phys.Vec3(.5, 25.0 / 16, .5));
            case "chair" -> new com.stardew.craft.block.utility.ChairBlock(props.noOcclusion(), asset.model(), 10.0 / 16.0);
            case "beach_chair" -> new com.stardew.craft.block.utility.ChairBlock(props.noOcclusion(), asset.model(), 9.0 / 16.0);
            case "decor" -> new MapDecorStaticBlock(props.noOcclusion(), asset.model());
            case "state_decor" -> GingerIslandStateDecorBlock.create(props.noOcclusion(), asset);
            default -> throw new IllegalArgumentException("Unknown Ginger Island behavior: " + asset.kind());
        };
    }

    public static Map<String, DeferredItem<Item>> registerItems(DeferredRegister.Items registry) {
        Map<String, DeferredItem<Item>> items = new LinkedHashMap<>();
        for (var asset : GingerIslandAssets.blocks()) {
            items.put(asset.id(), registry.register(asset.id(), () -> asset.kind().equals("heavy_tapper")
                    ? new com.stardew.craft.item.TapperItem(get(asset.id()), new Item.Properties().stacksTo(999))
                    : new StardewBlockItem(get(asset.id()), "stardewcraft.type." + asset.type(), -1,
                            new Item.Properties().stacksTo(999))));
        }
        return Collections.unmodifiableMap(items);
    }

    public static Block get(String id) {
        var block = ModBlocks.GINGER_ISLAND.get(id);
        if (block == null) throw new IllegalArgumentException("Unknown Ginger Island block: " + id);
        return block.get();
    }
}
