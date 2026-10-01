package com.stardew.craft.client.building;

import com.stardew.craft.building.runtime.BuildingRecord;
import com.stardew.craft.client.gui.FarmFolioScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Server-observed obstructions, grouped by block with a concrete location. */
public final class BuildingObstaclesScreen extends FarmFolioScreen {
    private final CompoundTag data;
    private int page;

    public BuildingObstaclesScreen(CompoundTag data) {
        super(ui("upgrade_blocked"), null);
        this.data = data;
    }

    @Override
    protected int preferredWidth() {
        return 474;
    }

    @Override
    protected int preferredHeight() {
        return 328;
    }

    @Override
    protected void layout() {
        int count = Math.max(1, (h - 160) / 45);
        int total = data.getList("Obstacles", 10).size();
        arrow(
                false,
                x + 28,
                y + h - 76,
                page > 0,
                () -> {
                    page--;
                    init();
                });
        arrow(
                true,
                x + 96,
                y + h - 76,
                (page + 1) * count < total,
                () -> {
                    page++;
                    init();
                });
        button(Component.translatable("gui.back"), x + 16, y + h - 36, 104, 24, this::onClose);
        button(
                Component.translatable("building.stardewcraft.preview_upgrade"),
                x + w - 196,
                y + h - 36,
                180,
                24,
                () -> BuildingPlacementPreview.showUpgrade(data.getCompound("Building")));
    }

    @Override
    protected void paint(GuiGraphics g) {
        paper(g, x + 16, y + 36, w - 32, h - 84);
        var building = BuildingRecord.load(data.getCompound("Building"));
        label(g, building.title(), x + 30, y + 50, w - 60, INK);
        paragraph(g, ui("remove_obstacles"), x + 30, y + 74, w - 60, y + 106, MUTED);
        rule(g, x + 30, y + 104, w - 60);
        var rows = data.getList("Obstacles", 10);
        int count = Math.max(1, (h - 160) / 45);
        for (int i = page * count; i < Math.min(rows.size(), (page + 1) * count); i++) {
            var row = rows.getCompound(i);
            int yy = y + 118 + (i - page * count) * 45;
            var block = BuiltInRegistries.BLOCK.get(new ResourceLocation(row.getString("Block")));
            item(g, new ItemStack(block), x + 30, yy + 3, 1);
            label(g, block.getName(), x + 56, yy, w - 142, INK);
            label(
                    g,
                    Component.literal(
                            row.getInt("X") + ", " + row.getInt("Y") + ", " + row.getInt("Z")),
                    x + 56,
                    yy + 19,
                    w - 142,
                    MUTED);
            label(g, Component.literal("× " + row.getInt("Count")), x + w - 74, yy + 5, 44, INK);
        }
    }
}
