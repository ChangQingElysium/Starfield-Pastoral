package com.stardew.craft.port.net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** 1.21 {@code minecraft:block_state}; stored as the vanilla 1.20.1 {@code BlockStateTag} compound of strings. */
public record BlockItemStateProperties(Map<String, String> properties) {
    public static final BlockItemStateProperties EMPTY = new BlockItemStateProperties(Map.of());
    public static final Codec<BlockItemStateProperties> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING)
            .xmap(BlockItemStateProperties::new, BlockItemStateProperties::properties);

    public BlockItemStateProperties {
        properties = Map.copyOf(properties);
    }

    public <T extends Comparable<T>> BlockItemStateProperties with(Property<T> property, T value) {
        Map<String, String> updated = new LinkedHashMap<>(properties);
        updated.put(property.getName(), property.getName(value));
        return new BlockItemStateProperties(updated);
    }

    public <T extends Comparable<T>> BlockItemStateProperties with(Property<T> property, BlockState state) {
        return with(property, state.getValue(property));
    }

    @Nullable
    public <T extends Comparable<T>> T get(Property<T> property) {
        String value = properties.get(property.getName());
        return value == null ? null : property.getValue(value).orElse(null);
    }

    public BlockState apply(BlockState state) {
        var definition = state.getBlock().getStateDefinition();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = definition.getProperty(entry.getKey());
            if (property != null) {
                state = updateState(state, property, entry.getValue());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState updateState(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    public boolean isEmpty() {
        return properties.isEmpty();
    }
}
