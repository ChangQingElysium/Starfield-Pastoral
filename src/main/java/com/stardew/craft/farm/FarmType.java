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
            288, 81, 272,
            new BlockPos(199, 25, 94), 180.0f,
            new BlockPos(101, 24, 81), new BlockPos(175, 25, 71),
            entry(new BlockPos(142, 25, 37), 0.0f,
                    new BlockPos(141, 25, 36), new BlockPos(143, 27, 36),
                    new BlockPos(0, 0, 35), new BlockPos(287, 80, 35)),
            entry(new BlockPos(144, 25, 236), 180.0f,
                    new BlockPos(143, 25, 237), new BlockPos(145, 27, 237),
                    new BlockPos(0, 0, 238), new BlockPos(287, 80, 238)),
            entry(new BlockPos(250, 25, 95), 90.0f,
                    new BlockPos(251, 25, 94), new BlockPos(251, 27, 96),
                    new BlockPos(252, 0, 0), new BlockPos(252, 80, 271)),
            null, null, null,
            region(new BlockPos(128, 25, 70), new BlockPos(130, 27, 70)),
            region(new BlockPos(128, 25, 71), new BlockPos(130, 26, 71)),
            null,
            new BlockPos(129, 25, 72), 0.0f
    )),

    RIVERLAND("riverland", true, layout(
            0,
            288, 81, 272,
            new BlockPos(199, 25, 93), 180.0f,
            new BlockPos(101, 24, 81), new BlockPos(221, 25, 81),
            entry(new BlockPos(141, 25, 38), 0.0f,
                    new BlockPos(139, 25, 37), new BlockPos(143, 27, 37),
                    new BlockPos(0, 0, 36), new BlockPos(287, 80, 36)),
            entry(new BlockPos(145, 25, 236), 180.0f,
                    new BlockPos(143, 25, 237), new BlockPos(147, 27, 237),
                    new BlockPos(0, 0, 238), new BlockPos(287, 80, 238)),
            entry(new BlockPos(250, 25, 95), 90.0f,
                    new BlockPos(251, 25, 93), new BlockPos(251, 27, 97),
                    new BlockPos(252, 0, 0), new BlockPos(252, 80, 271)),
            "pelican_town_river", null, null,
            region(new BlockPos(118, 25, 77), new BlockPos(120, 27, 77)),
            region(new BlockPos(118, 25, 78), new BlockPos(120, 26, 78)),
            null,
            new BlockPos(119, 25, 79), 0.0f
    )),

    FOREST("forest", true, layout(
            0,
            288, 81, 272,
            new BlockPos(199, 25, 93), 180.0f,
            new BlockPos(101, 24, 81), new BlockPos(218, 25, 74),
            entry(new BlockPos(142, 25, 38), 0.0f,
                    new BlockPos(140, 25, 37), new BlockPos(144, 27, 37),
                    new BlockPos(0, 0, 36), new BlockPos(287, 80, 36)),
            entry(new BlockPos(144, 25, 236), 180.0f,
                    new BlockPos(142, 25, 237), new BlockPos(146, 27, 237),
                    new BlockPos(0, 0, 238), new BlockPos(287, 80, 238)),
            entry(new BlockPos(250, 25, 95), 90.0f,
                    new BlockPos(251, 25, 93), new BlockPos(251, 27, 97),
                    new BlockPos(252, 0, 0), new BlockPos(252, 80, 271)),
            null,
            new BlockPos(65, 24, 72), new BlockPos(216, 28, 202),
            region(new BlockPos(124, 25, 72), new BlockPos(126, 27, 72)),
            region(new BlockPos(124, 25, 73), new BlockPos(126, 26, 73)),
            null,
            new BlockPos(125, 25, 74), 0.0f
    )),

    HILLTOP("hilltop", true, layout(
            0,
            288, 83, 272,
            new BlockPos(199, 25, 93), 180.0f,
            new BlockPos(101, 24, 81), new BlockPos(163, 25, 77),
            entry(new BlockPos(142, 25, 38), 0.0f,
                    new BlockPos(139, 25, 37), new BlockPos(145, 27, 37),
                    new BlockPos(0, 0, 36), new BlockPos(287, 82, 36)),
            entry(new BlockPos(144, 21, 236), 180.0f,
                    new BlockPos(143, 21, 237), new BlockPos(146, 23, 237),
                    new BlockPos(0, 0, 238), new BlockPos(287, 82, 238)),
            entry(new BlockPos(250, 25, 95), 90.0f,
                    new BlockPos(251, 25, 94), new BlockPos(251, 27, 96),
                    new BlockPos(252, 0, 0), new BlockPos(252, 82, 271)),
            null, null, null,
            region(new BlockPos(129, 25, 72), new BlockPos(131, 27, 72)),
            region(new BlockPos(129, 25, 73), new BlockPos(131, 26, 73)),
            null,
            new BlockPos(130, 25, 74), 0.0f
    )),
    WILDERNESS("wilderness", true, layout(
            0,
            288, 81, 272,
            new BlockPos(199, 25, 93), 180.0f,
            new BlockPos(101, 24, 81), new BlockPos(175, 25, 70),
            entry(new BlockPos(142, 25, 38), 0.0f,
                    new BlockPos(140, 25, 37), new BlockPos(144, 27, 37),
                    new BlockPos(0, 0, 36), new BlockPos(287, 80, 36)),
            entry(new BlockPos(144, 25, 236), 180.0f,
                    new BlockPos(143, 25, 237), new BlockPos(145, 27, 237),
                    new BlockPos(0, 0, 238), new BlockPos(287, 80, 238)),
            entry(new BlockPos(250, 25, 95), 90.0f,
                    new BlockPos(251, 25, 94), new BlockPos(251, 27, 96),
                    new BlockPos(252, 0, 0), new BlockPos(252, 80, 271)),
            null, null, null,
            region(new BlockPos(126, 25, 72), new BlockPos(128, 27, 72)),
            region(new BlockPos(126, 25, 73), new BlockPos(128, 26, 73)),
            null,
            new BlockPos(127, 25, 74), 0.0f
    )),
    FOUR_CORNERS("four_corners", true, layout(
            0,
            288, 81, 272,
            new BlockPos(199, 25, 93), 180.0f,
            new BlockPos(132, 24, 110), new BlockPos(150, 25, 140),
            entry(new BlockPos(142, 25, 38), 0.0f,
                    new BlockPos(141, 25, 37), new BlockPos(143, 27, 37),
                    new BlockPos(0, 0, 36), new BlockPos(287, 80, 36)),
            entry(new BlockPos(144, 29, 236), 180.0f,
                    new BlockPos(143, 29, 237), new BlockPos(145, 31, 237),
                    new BlockPos(0, 0, 238), new BlockPos(287, 80, 238)),
            entry(new BlockPos(250, 25, 95), 90.0f,
                    new BlockPos(251, 25, 94), new BlockPos(251, 27, 96),
                    new BlockPos(252, 0, 0), new BlockPos(252, 80, 271)),
            null, null, null,
            region(new BlockPos(122, 25, 134), new BlockPos(124, 27, 134)),
            region(new BlockPos(122, 25, 139), new BlockPos(124, 26, 139)),
            null,
            new BlockPos(123, 25, 140), 0.0f
    )),
    BEACH("beach", true, layout(
            0,
            288, 78, 272,
            new BlockPos(154, 25, 99), 180.0f,
            new BlockPos(72, 24, 85), new BlockPos(183, 25, 111),
            entry(new BlockPos(148, 25, 34), 0.0f,
                    new BlockPos(146, 25, 33), new BlockPos(150, 27, 33),
                    new BlockPos(0, 0, 32), new BlockPos(287, 77, 32)),
            entry(new BlockPos(181, 25, 236), 180.0f,
                    new BlockPos(180, 25, 237), new BlockPos(182, 27, 237),
                    new BlockPos(0, 0, 238), new BlockPos(287, 77, 238)),
            entry(new BlockPos(250, 25, 95), 90.0f,
                    new BlockPos(251, 25, 92), new BlockPos(251, 27, 98),
                    new BlockPos(252, 0, 0), new BlockPos(252, 77, 271)),
            null, null, null,
            region(new BlockPos(107, 25, 85), new BlockPos(109, 27, 85)),
            region(new BlockPos(107, 25, 86), new BlockPos(109, 26, 86)),
            null,
            new BlockPos(108, 25, 87), 0.0f
    )),
    MEADOWLANDS("meadowlands", true, layout(
            0,
            288, 81, 272,
            new BlockPos(199, 25, 93), 180.0f,
            new BlockPos(118, 24, 103), new BlockPos(175, 25, 77),
            entry(new BlockPos(164, 25, 38), 0.0f,
                    new BlockPos(162, 25, 37), new BlockPos(167, 27, 37),
                    new BlockPos(0, 0, 36), new BlockPos(287, 80, 36)),
            entry(new BlockPos(144, 25, 236), 180.0f,
                    new BlockPos(142, 25, 237), new BlockPos(146, 27, 237),
                    new BlockPos(0, 0, 238), new BlockPos(287, 80, 238)),
            entry(new BlockPos(250, 25, 95), 90.0f,
                    new BlockPos(251, 25, 93), new BlockPos(251, 27, 97),
                    new BlockPos(252, 0, 0), new BlockPos(252, 80, 271)),
            null, null, null,
            region(new BlockPos(206, 25, 171), new BlockPos(208, 27, 171)),
            region(new BlockPos(206, 25, 172), new BlockPos(208, 26, 172)),
            null,
            new BlockPos(207, 25, 173), 0.0f
    ));

    // ══════════════════════════════════════════
    //  数据结构
    // ══════════════════════════════════════════

    /** 单个入口/出口的布局数据 */
    public record EntryData(
            BlockPos teleportOffset, float yaw,
            BlockPos exitMin, BlockPos exitMax,
            BlockPos barrierMin, BlockPos barrierMax
    ) {
        public EntryData(
                BlockPos teleportOffset, float yaw,
                BlockPos exitMin, BlockPos exitMax
        ) {
            this(
                    teleportOffset, yaw, exitMin, exitMax,
                    new com.stardew.craft.api.v1.farm.StardewFarmLayout.Entry(
                            teleportOffset, yaw, exitMin, exitMax).barrierMin(),
                    new com.stardew.craft.api.v1.farm.StardewFarmLayout.Entry(
                            teleportOffset, yaw, exitMin, exitMax).barrierMax());
        }
    }

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
        return new ResourceLocation(StardewCraft.MODID,
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

    private static EntryData entry(
            BlockPos tp, float yaw,
            BlockPos exitMin, BlockPos exitMax,
            BlockPos barrierMin, BlockPos barrierMax
    ) {
        return new EntryData(
                tp, yaw, exitMin, exitMax, barrierMin, barrierMax);
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
