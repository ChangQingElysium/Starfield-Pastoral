package com.stardew.craft.block.mine;

import com.stardew.craft.gingerisland.GingerIslandBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Ground-only palette; adding volcano ground must not invent volcano elevator/crate themes. */
public record MineGroundMaterial(String id, Supplier<Block> soilSource, Supplier<Block> looseSource, Supplier<Block> wallSource) {
    private static final List<MineGroundMaterial> ALL = create();
    public static List<MineGroundMaterial> values() { return ALL; }
    public Block soil() { return soilSource.get(); }
    public Block looseSoil() { return looseSource.get(); }
    public Block wall() { return wallSource.get(); }
    public int rank(BlockState state) {
        return state.is(looseSoil()) ? 2 : state.is(soil()) ? 1 : state.is(wall()) ? 0 : -1;
    }
    private static List<MineGroundMaterial> create() {
        List<MineGroundMaterial> result = new ArrayList<>();
        for (var theme : MineBuildingTheme.values())
            result.add(new MineGroundMaterial(theme.id(), theme::soil, theme::looseSoil, theme::wall));
        result.add(new MineGroundMaterial("volcano", () -> GingerIslandBlocks.get("ginger_volcano_floor"),
                () -> GingerIslandBlocks.get("ginger_volcano_mud"), () -> GingerIslandBlocks.get("ginger_volcano_wall")));
        return List.copyOf(result);
    }
}
