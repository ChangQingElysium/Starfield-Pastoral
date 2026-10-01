package com.stardew.craft.block.mine;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.block.ModBlocks;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Authored mine architecture materials; separate from mine gameplay and ore themes. */
public enum MineBuildingTheme implements net.minecraft.util.StringRepresentable {
    EARTH("earth", () -> ModBlocks.MINE_EARTH_SOIL.get(), () -> ModBlocks.MINE_EARTH_LOOSE_SOIL.get(), () -> ModBlocks.MINE_EARTH_WALL.get()),
    FROST("frost", () -> ModBlocks.MINE_FROST_SOIL.get(), () -> ModBlocks.MINE_FROST_LOOSE_SOIL.get(), () -> ModBlocks.MINE_FROST_WALL.get()),
    LAVA("lava", () -> ModBlocks.MINE_LAVA_SOIL.get(), () -> ModBlocks.MINE_LAVA_LOOSE_SOIL.get(), () -> ModBlocks.MINE_LAVA_WALL.get()),
    DESERT("desert", () -> ModBlocks.MINE_DESERT_SOIL.get(), () -> ModBlocks.MINE_DESERT_LOOSE_SOIL.get(), () -> ModBlocks.MINE_DESERT_WALL.get()),
    EARTH_DARK("earth_dark", () -> ModBlocks.MINE_EARTH_DARK_SOIL.get(), () -> ModBlocks.MINE_EARTH_DARK_LOOSE_SOIL.get(), () -> ModBlocks.MINE_EARTH_DARK_WALL.get()),
    FROST_DARK("frost_dark", () -> ModBlocks.MINE_FROST_DARK_SOIL.get(), () -> ModBlocks.MINE_FROST_DARK_LOOSE_SOIL.get(), () -> ModBlocks.MINE_FROST_DARK_WALL.get()),
    LAVA_DARK("lava_dark", () -> ModBlocks.MINE_LAVA_DARK_SOIL.get(), () -> ModBlocks.MINE_LAVA_DARK_LOOSE_SOIL.get(), () -> ModBlocks.MINE_LAVA_DARK_WALL.get()),
    DESERT_DARK("desert_dark", () -> ModBlocks.MINE_DESERT_DARK_SOIL.get(), () -> ModBlocks.MINE_DESERT_DARK_LOOSE_SOIL.get(), () -> ModBlocks.MINE_DESERT_DARK_WALL.get());

    private final String id;
    private final Supplier<Block> soil, looseSoil, wall;

    MineBuildingTheme(String id, Supplier<Block> soil, Supplier<Block> looseSoil, Supplier<Block> wall) {
        this.id = id;
        this.soil = soil;
        this.looseSoil = looseSoil;
        this.wall = wall;
    }

    public String id() { return id; }
    @Override public String getSerializedName() { return id; }
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<MineBuildingTheme> PROPERTY =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("theme", MineBuildingTheme.class);

    public static MineBuildingTheme forPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        var fixed = PortItemData.getOrDefault(context.getItemInHand(), com.stardew.craft.port.net.minecraft.core.component.DataComponents.BLOCK_STATE,
                com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties.EMPTY).get(PROPERTY);
        if (fixed != null) return fixed;
        for (var direction : net.minecraft.core.Direction.values()) {
            BlockState neighbor = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
            if (neighbor.hasProperty(PROPERTY)) return neighbor.getValue(PROPERTY);
            for (var theme : values()) if (theme.rank(neighbor) >= 0) return theme;
        }
        return EARTH;
    }

    public static net.minecraft.world.item.ItemStack picked(Block block, BlockState state) {
        var stack = new net.minecraft.world.item.ItemStack(block);
        PortItemData.set(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.BLOCK_STATE,
                com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties.EMPTY.with(PROPERTY, state));
        return stack;
    }
    public Block soil() { return soil.get(); }
    public Block looseSoil() { return looseSoil.get(); }
    public Block wall() { return wall.get(); }

    public int rank(BlockState state) {
        if (state.is(looseSoil())) return 2;
        if (state.is(soil())) return 1;
        return state.is(wall()) ? 0 : -1;
    }
}
