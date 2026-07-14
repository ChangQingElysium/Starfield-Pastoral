package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * 农场类型枚举。
 * 每种类型对应不同的 schematic、图标、描述和布局数据。
 * 只有当前公开的类型可供新农场选择。已有农场的布局数据仍保留以兼容旧存档。
 */
public enum FarmType {
    STANDARD("standard", true, layout(
            0,
            337, 23, 382,
            new BlockPos(229, 5, 257), 90.0f,
            new BlockPos(236, 5, 137), new BlockPos(265, 5, 200),
            entry(new BlockPos(266, 5, 174), 90.0f,
                    new BlockPos(267, 5, 171), new BlockPos(267, 7, 177)),
            entry(new BlockPos(38, 5, 174), -90.0f,
                    new BlockPos(37, 5, 172), new BlockPos(37, 7, 177)),
            entry(new BlockPos(225, 5, 298), 180.0f,
                    new BlockPos(217, 5, 300), new BlockPos(239, 7, 300)),
            null, null, null,
            region(new BlockPos(254, 5, 127), new BlockPos(254, 7, 131)),
            region(new BlockPos(253, 5, 127), new BlockPos(253, 7, 131)),
            null,
            new BlockPos(251, 5, 129), -90.0f
    )),

    RIVERLAND("riverland", false, layout(
            0,
            230, 70, 253,
            new BlockPos(162, 10, 202), 90.0f,
            new BlockPos(172, 10, 88), new BlockPos(206, 10, 115),
            entry(new BlockPos(210, 10, 129), 90.0f,
                    new BlockPos(211, 10, 127), new BlockPos(211, 12, 131)),
            entry(new BlockPos(4, 10, 153), -90.0f,
                    new BlockPos(2, 10, 149), new BlockPos(2, 12, 156)),
            entry(new BlockPos(166, 10, 248), 180.0f,
                    new BlockPos(163, 10, 249), new BlockPos(169, 12, 249)),
            "pelican_town_river", null, null,
            null,
            region(new BlockPos(213, 10, 108), new BlockPos(213, 12, 111)),
            null,
            new BlockPos(211, 10, 110), -90.0f
    )),

    FOREST("forest", true, layout(
            0,
            309, 23, 335,
            new BlockPos(228, 3, 260), 90.0f,
            new BlockPos(247, 3, 122), new BlockPos(268, 3, 187),
            entry(new BlockPos(279, 3, 172), 90.0f,
                    new BlockPos(280, 3, 167), new BlockPos(280, 5, 177)),
            entry(new BlockPos(39, 3, 174), -90.0f,
                    new BlockPos(38, 3, 169), new BlockPos(38, 5, 178)),
            entry(new BlockPos(227, 3, 313), 180.0f,
                    new BlockPos(222, 3, 315), new BlockPos(235, 5, 315)),
            "secret_woods_pond",
            new BlockPos(48, 3, 43), new BlockPos(206, 7, 98),
            region(new BlockPos(283, 3, 119), new BlockPos(283, 6, 126)),
            region(new BlockPos(282, 3, 119), new BlockPos(282, 6, 126)),
            region(new BlockPos(279, 3, 119), new BlockPos(282, 6, 126)),
            new BlockPos(279, 3, 123), -90.0f
    )),

    HILLTOP("hilltop", false, null),
    WILDERNESS("wilderness", false, null),
    FOUR_CORNERS("four_corners", false, null),
    BEACH("beach", false, null);

    // ══════════════════════════════════════════
    //  数据结构
    // ══════════════════════════════════════════

    /** 单个入口/出口的布局数据 */
    public record EntryData(
            BlockPos teleportOffset, float yaw,
            BlockPos exitMin, BlockPos exitMax
    ) {}

    /** 立方体区域规范（相对 farm origin），用于农场洞穴的墙/传送区/清空区。min/max 均包含。 */
    public record CaveRegion(BlockPos min, BlockPos max) {}

    /** 完整农场布局（仅解锁类型有值） */
    public record FarmLayout(
            int originY,
            int schemWidth, int schemHeight, int schemLength,
            BlockPos spawnOffset, float spawnYaw,
            BlockPos greenhouseOffset,
            BlockPos totemOffset,
            EntryData entrySouth,
            EntryData entryEast,
            EntryData entryWest,
            @Nullable String biomeId,
            @Nullable BlockPos forageZoneMin,
            @Nullable BlockPos forageZoneMax,
            @Nullable CaveRegion caveBlackWall,
            @Nullable CaveRegion cavePortalWall,
            @Nullable CaveRegion caveClearBox,
            @Nullable BlockPos caveExitSpawn,
            float caveExitYaw
    ) {
        public BlockPos boundsMin() { return BlockPos.ZERO; }
        public BlockPos boundsMax() { return new BlockPos(schemWidth - 1, schemHeight - 1, schemLength - 1); }
    }

    // ══════════════════════════════════════════
    //  枚举字段
    // ══════════════════════════════════════════

    private final String id;
    private final boolean unlocked;
    @Nullable
    private final FarmLayout layout;

    FarmType(String id, boolean unlocked, @Nullable FarmLayout layout) {
        this.id = id;
        this.unlocked = unlocked;
        this.layout = layout;
    }

    public String getId() { return id; }
    public boolean isUnlocked() { return unlocked; }

    @Nullable
    public FarmLayout getLayout() { return layout; }

    public ResourceLocation getIconTexture() {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID,
                "textures/gui/farm_select/icon_" + id + ".png");
    }

    public Component getDisplayName() {
        return Component.translatable("gui.stardewcraft.farm_type." + id + ".name");
    }

    public Component getDescription() {
        return Component.translatable("gui.stardewcraft.farm_type." + id + ".desc");
    }

    public String getSchematicPath() {
        return "data/stardewcraft/structures/farm/" + id + ".schem";
    }

    public static FarmType fromId(String id) {
        for (FarmType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return STANDARD;
    }

    public static List<FarmType> allTypes() {
        return Arrays.asList(values());
    }

    // ── 便捷构造 ──

    private static EntryData entry(BlockPos tp, float yaw, BlockPos exitMin, BlockPos exitMax) {
        return new EntryData(tp, yaw, exitMin, exitMax);
    }

    private static FarmLayout layout(int originY, int w, int h, int l,
                                     BlockPos spawn, float spawnYaw,
                                     BlockPos greenhouse, BlockPos totem,
                                     EntryData south, EntryData east, EntryData west,
                                     @Nullable String biomeId,
                                     @Nullable BlockPos forageMin, @Nullable BlockPos forageMax,
                                     @Nullable CaveRegion blackWall,
                                     @Nullable CaveRegion portalWall,
                                     @Nullable CaveRegion clearBox,
                                     @Nullable BlockPos caveExitSpawn,
                                     float caveExitYaw) {
        return new FarmLayout(originY, w, h, l, spawn, spawnYaw,
                greenhouse, totem, south, east, west, biomeId, forageMin, forageMax,
                blackWall, portalWall, clearBox, caveExitSpawn, caveExitYaw);
    }

    private static CaveRegion region(BlockPos min, BlockPos max) {
        return new CaveRegion(min, max);
    }
}
