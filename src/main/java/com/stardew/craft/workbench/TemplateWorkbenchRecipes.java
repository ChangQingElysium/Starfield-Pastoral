package com.stardew.craft.workbench;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.templates.TemplateShape;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;

public final class TemplateWorkbenchRecipes {
    private TemplateWorkbenchRecipes() {}

    public static List<WorkbenchEntry> build() {
        return Arrays.stream(TemplateShape.values())
                .filter(TemplateShape::visibleInCreativeTab)
                .map(TemplateWorkbenchRecipes::recipe).toList();
    }

    private static WorkbenchEntry recipe(TemplateShape shape) {
        boolean hardwood = switch (shape) {
            case COLUMN, POST, POLE, STICK, HORIZONTAL_COLUMN, HORIZONTAL_POST,
                    HORIZONTAL_POLE, HORIZONTAL_STICK, CORNER_PILLAR, THREEWAY_CORNER_PILLAR,
                    FRAME_BOTTOM, FRAME_TOP, FRAME_LEFT, FRAME_RIGHT, IRON_TRAPDOOR,
                    WALL_BEAM, WALL_POST, WALL_BRACE_LEFT, WALL_BRACE_RIGHT, ROOF_EAVE, GABLE_PANEL -> true;
            default -> shape.meshKind() == TemplateShape.MeshKind.ROOF;
        };
        int cost = switch (shape) {
            case ELEVATED_SLOPE_EDGE, VERTICAL_STAIRS, SLICED_STAIRS_PANEL, ROOF_GAMBREL -> 3;
            case SLOPE, SLOPE_EDGE, HALF_STAIRS, FENCE_GATE, IRON_TRAPDOOR,
                    ROOF_SLOPE, ROOF_STEEP, ROOF_RIDGE -> 2;
            default -> 1;
        };
        int output = switch (shape) {
            case SNOW_LAYER, FLOOR_BOARD, WALL_BOARD, BUTTON, PRESSURE_PLATE,
                    STICK, HORIZONTAL_STICK -> 4;
            case SLAB, SLAB_EDGE, SLAB_CORNER, SIDE_QUARTER_SLAB, PANEL,
                    POLE, HORIZONTAL_POLE, FRAME_LEFT, FRAME_RIGHT -> 2;
            default -> 1;
        };
        return new WorkbenchEntry(new ResourceLocation(StardewCraft.MODID, shape.registryPath()),
                hardwood ? "template_hardwood" : "template_wood", cost, output, StardewCraft.MODID,
                hardwood ? "stardewcraft:wood_hard" : "stardewcraft:wood_normal");
    }
}
