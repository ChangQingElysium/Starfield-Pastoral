package com.stardew.craft.block.decor;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.item.StardewBlockItem;
import com.stardew.craft.item.block.WaterLanternItem;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredBlock;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredItem;
import net.minecraftforge.registries.DeferredRegister;

public final class NaturalDecorRegistry {
    private NaturalDecorRegistry() {}
    public static Map<String, DeferredBlock<NaturalPlantBlock>> blocks(DeferredRegister.Blocks registry) {
        Map<String, DeferredBlock<NaturalPlantBlock>> result = new LinkedHashMap<>();
        for (NaturalDecorKind kind : NaturalDecorKind.values()) result.put(kind.id, registry.register(kind.id, () -> {
            var properties = Block.Properties.of().noCollission().noOcclusion().instabreak()
                    .sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY);
            return kind.habitat == NaturalDecorKind.Habitat.SURFACE
                    ? new FloatingPlantBlock(properties, kind) : new NaturalPlantBlock(properties, kind);
        }));
        return Collections.unmodifiableMap(result);
    }
    public static Map<String, DeferredItem<Item>> items(DeferredRegister.Items registry) {
        Map<String, DeferredItem<Item>> result = new LinkedHashMap<>();
        for (NaturalDecorKind kind : NaturalDecorKind.values()) result.put(kind.id, registry.register(kind.id, () -> {
            var block = ModBlocks.NATURAL_DECOR.get(kind.id).get();
            var properties = new Item.Properties().stacksTo(999);
            return kind.habitat == NaturalDecorKind.Habitat.SURFACE
                    ? new WaterLanternItem(block, kind.typeKey, -1, properties)
                    : new StardewBlockItem(block, kind.typeKey, -1, properties);
        }));
        return Collections.unmodifiableMap(result);
    }
}
