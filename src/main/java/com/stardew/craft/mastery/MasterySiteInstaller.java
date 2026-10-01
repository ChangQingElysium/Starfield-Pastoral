package com.stardew.craft.mastery;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.player.SkillType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Map;

/**
 * 精通山洞站点的方块/实体一次性放置。
 *
 * 含：
 *  - (-90,64,109) 铁门，两半，FACING=SOUTH，OPEN=false。
 *  - (-90,64,104) 讲台，FACING=SOUTH。
 *  - 5 个 item_display 实体（按 SNBT 召唤）。
 *  - 5 组 candle（每组 2 根，不点燃，颜色对应技能）。
 *
 * 版本升级：把 SITE_VERSION 加 1 即可强制旧存档重新铺设；幂等。
 */
@SuppressWarnings("null")
public final class MasterySiteInstaller extends SavedData {

    private static final String DATA_NAME = "stardew_mastery_site";
    public static final int SITE_VERSION = 4;

    // 用户指定的 1×1×1 交互方块位置（block 坐标 = 西北下角，Pos 居中加 0.5）。
    private static final Map<SkillType, BlockPos> INTERACTION_BLOCK = new EnumMap<>(SkillType.class);
    // 用户指定的 text_display 位置（precise Vec3）+ 颜色 + 翻译键。
    private static final Map<SkillType, net.minecraft.world.phys.Vec3> TEXT_POS = new EnumMap<>(SkillType.class);
    private static final Map<SkillType, String> TEXT_COLOR = new EnumMap<>(SkillType.class);
    private static final Map<SkillType, String> TEXT_KEY = new EnumMap<>(SkillType.class);
    static {
        INTERACTION_BLOCK.put(SkillType.COMBAT,   new BlockPos(-94, 65, 103));
        INTERACTION_BLOCK.put(SkillType.FORAGING, new BlockPos(-92, 65, 101));
        INTERACTION_BLOCK.put(SkillType.FARMING,  new BlockPos(-90, 65, 101));
        INTERACTION_BLOCK.put(SkillType.FISHING,  new BlockPos(-88, 65, 101));
        INTERACTION_BLOCK.put(SkillType.MINING,   new BlockPos(-86, 65, 103));

        TEXT_POS.put(SkillType.COMBAT,   new net.minecraft.world.phys.Vec3(-93.5, 66.3125, 103.0));
        TEXT_POS.put(SkillType.FORAGING, new net.minecraft.world.phys.Vec3(-91.5, 66.3125, 101.0));
        TEXT_POS.put(SkillType.FARMING,  new net.minecraft.world.phys.Vec3(-89.5, 66.3125, 101.0));
        TEXT_POS.put(SkillType.FISHING,  new net.minecraft.world.phys.Vec3(-87.5, 66.3125, 101.0));
        TEXT_POS.put(SkillType.MINING,   new net.minecraft.world.phys.Vec3(-85.5, 66.3125, 103.0));

        TEXT_COLOR.put(SkillType.COMBAT,   "red");
        TEXT_COLOR.put(SkillType.FORAGING, "yellow");
        TEXT_COLOR.put(SkillType.FARMING,  "green");
        TEXT_COLOR.put(SkillType.FISHING,  "aqua");
        TEXT_COLOR.put(SkillType.MINING,   "gold");

        TEXT_KEY.put(SkillType.COMBAT,   "stardewcraft.mastery.label.combat");
        TEXT_KEY.put(SkillType.FORAGING, "stardewcraft.mastery.label.foraging");
        TEXT_KEY.put(SkillType.FARMING,  "stardewcraft.mastery.label.farming");
        TEXT_KEY.put(SkillType.FISHING,  "stardewcraft.mastery.label.fishing");
        TEXT_KEY.put(SkillType.MINING,   "stardewcraft.mastery.label.mining");
    }

    public static final String TEXT_DISPLAY_TAG = "stardewcraft_mastery_label";

    /** 用于识别由本安装器创建的 interaction 实体。 */
    public static final String INTERACTION_TAG_ROOT = "stardewcraft_mastery";
    public static String interactionTagFor(SkillType skill) {
        return INTERACTION_TAG_ROOT + "_" + skill.name().toLowerCase();
    }

    private int placedVersion = 0;

    public MasterySiteInstaller() {}

    public static MasterySiteInstaller get(ServerLevel anyLevelInServer) {
        ServerLevel overworld = anyLevelInServer.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) return new MasterySiteInstaller();
        return overworld.getDataStorage().computeIfAbsent(com.stardew.craft.port.PortSavedData.loader(factory()), com.stardew.craft.port.PortSavedData.constructor(factory()), DATA_NAME);
    }

    public void resetForMigration() {
        placedVersion = 0;
        setDirty();
    }

    public void ensurePlaced(ServerLevel stardewLevel) {
        if (placedVersion >= SITE_VERSION) return;
        if (!ModDimensions.STARDEW_VALLEY.equals(stardewLevel.dimension())) return;

        StardewCraft.LOGGER.info("[MASTERY_SITE] Installing mastery cave site (version {} -> {})", placedVersion, SITE_VERSION);

        cleanStaleBlocks(stardewLevel);
        placeDoor(stardewLevel);
        placeLectern(stardewLevel);
        placeCandles(stardewLevel);
        spawnItemDisplays(stardewLevel);
        spawnTextDisplays(stardewLevel);

        placedVersion = SITE_VERSION;
        setDirty();
        StardewCraft.LOGGER.info("[MASTERY_SITE] Mastery cave site placement complete.");
    }

    /** 历史版本曾把 fishing 蜡烛错放到 (-89,66,102)；清掉避免遗留点亮假象。 */
    private static final BlockPos[] STALE_BLOCK_POSITIONS = new BlockPos[] {
        new BlockPos(-89, 66, 102),
    };

    private static void cleanStaleBlocks(ServerLevel level) {
        for (BlockPos pos : STALE_BLOCK_POSITIONS) {
            BlockState here = level.getBlockState(pos);
            if (here.getBlock() instanceof CandleBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
    }

    // ── 方块放置 ──

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static void placeDoor(ServerLevel level) {
        BlockPos lower = MasterySite.DOOR_POS;
        BlockPos upper = lower.above();
        BlockState lowerState = Blocks.WARPED_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.OPEN, false)
            .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
            .setValue(DoorBlock.POWERED, false)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upperState = lowerState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        level.setBlock(lower, lowerState, FLAGS);
        level.setBlock(upper, upperState, FLAGS);
    }

    private static void placeLectern(ServerLevel level) {
        BlockState state = Blocks.LECTERN.defaultBlockState()
            .setValue(LecternBlock.FACING, Direction.SOUTH)
            .setValue(LecternBlock.HAS_BOOK, false);
        level.setBlock(MasterySite.CENTRAL_PEDESTAL_POS, state, FLAGS);
    }

    private static final Map<SkillType, Block> CANDLE_BLOCKS;
    static {
        CANDLE_BLOCKS = new EnumMap<>(SkillType.class);
        CANDLE_BLOCKS.put(SkillType.COMBAT,   Blocks.RED_CANDLE);
        CANDLE_BLOCKS.put(SkillType.FORAGING, Blocks.YELLOW_CANDLE);
        CANDLE_BLOCKS.put(SkillType.FARMING,  Blocks.LIME_CANDLE);
        CANDLE_BLOCKS.put(SkillType.FISHING,  Blocks.LIGHT_BLUE_CANDLE);
        CANDLE_BLOCKS.put(SkillType.MINING,   Blocks.ORANGE_CANDLE);
    }

    private static void placeCandles(ServerLevel level) {
        for (SkillType skill : SkillType.values()) {
            MasterySite.SkillStation st = MasterySite.station(skill);
            if (st == null) continue;
            Block candle = CANDLE_BLOCKS.get(skill);
            if (candle == null) continue;
            BlockState state = candle.defaultBlockState()
                .setValue(CandleBlock.CANDLES, 2)
                .setValue(CandleBlock.LIT, false);
            level.setBlock(st.candlePos(), state, FLAGS);
        }
    }

    // ── item_display 实体 ──

    /** 用户提供的 5 份 SNBT（无 Pos，会在 spawnItemDisplays 中注入）。 */
    private static final Map<SkillType, String> DISPLAY_NBTS;
    static {
        DISPLAY_NBTS = new EnumMap<>(SkillType.class);
        DISPLAY_NBTS.put(SkillType.COMBAT,
            "{item:{components:{\"minecraft:custom_data\":{StardewWeapon:{CritChance:0.02f,CritPower:0.0f,Defense:0,Knockback:0.0f,MaxDamage:8.0f,MinDamage:4.0f,Precision:0.0f,Speed:2,Type:0}}},count:1,id:\"stardewcraft:steel_smallsword\"},transformation:{left_rotation:[0.0f,0.0f,-0.3826835f,0.9238795f],right_rotation:[0.0f,0.0f,0.0f,1.0f],scale:[1.0000002f,1.0000002f,1.0f],translation:[0.0f,0.0f,0.0f]}}");
        DISPLAY_NBTS.put(SkillType.FORAGING,
            "{item:{count:1,id:\"stardewcraft:steel_axe\"},transformation:{left_rotation:[0.0f,0.0f,0.38268346f,0.9238795f],right_rotation:[0.0f,0.0f,0.0f,1.0f],scale:[1.0f,1.0f,1.0f],translation:[0.0f,0.0f,0.0f]}}");
        DISPLAY_NBTS.put(SkillType.FARMING,
            "{item:{count:1,id:\"stardewcraft:copper_watering_can\"},transformation:{left_rotation:[0.0f,0.0f,-0.37460658f,0.92718387f],right_rotation:[0.0f,0.0f,0.0f,1.0f],scale:[1.0f,1.0f,1.0f],translation:[0.0f,0.0f,0.0f]}}");
        DISPLAY_NBTS.put(SkillType.FISHING,
            "{item:{count:1,id:\"stardewcraft:fiberglass_rod\"},transformation:{left_rotation:[0.0f,1.0f,0.0f,0.0f],right_rotation:[0.0f,0.0f,0.0f,1.0f],scale:[1.0f,1.0f,1.0f],translation:[0.0f,0.0f,0.0f]}}");
        DISPLAY_NBTS.put(SkillType.MINING,
            "{item:{count:1,id:\"stardewcraft:steel_pickaxe\"},transformation:{left_rotation:[-0.38268346f,0.9238795f,0.0f,0.0f],right_rotation:[0.0f,0.0f,0.0f,1.0f],scale:[1.0f,1.0f,1.0f],translation:[0.0f,0.0f,0.0f]}}");
    }

    private static void spawnItemDisplays(ServerLevel level) {
        for (SkillType skill : SkillType.values()) {
            MasterySite.SkillStation st = MasterySite.station(skill);
            if (st == null) continue;
            // 防重复：删掉范围内的旧 ItemDisplay 与旧 Interaction（按 tag 识别）。
            AABB box = new AABB(st.displayPos(), st.displayPos()).inflate(0.8);
            level.getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class, box)
                .forEach(Entity::discard);
            String wantedTag = interactionTagFor(skill);
            level.getEntitiesOfClass(net.minecraft.world.entity.Interaction.class, box.inflate(0.5))
                .stream()
                .filter(e -> e.getTags().contains(wantedTag) || e.getTags().contains(INTERACTION_TAG_ROOT))
                .forEach(Entity::discard);

            // 1) item_display（视觉）
            String snbt = DISPLAY_NBTS.get(skill);
            if (snbt != null) {
                try {
                    CompoundTag tag = TagParser.parseTag(snbt);
                    tag.putString("id", "minecraft:item_display");
                    net.minecraft.nbt.ListTag pos = new net.minecraft.nbt.ListTag();
                    pos.add(net.minecraft.nbt.DoubleTag.valueOf(st.displayPos().x));
                    pos.add(net.minecraft.nbt.DoubleTag.valueOf(st.displayPos().y));
                    pos.add(net.minecraft.nbt.DoubleTag.valueOf(st.displayPos().z));
                    tag.put("Pos", pos);
                    Entity entity = EntityType.loadEntityRecursive(tag, level, e -> e);
                    if (entity != null) level.addFreshEntity(entity);
                } catch (Exception e) {
                    StardewCraft.LOGGER.error("[MASTERY_SITE] Failed to spawn item_display for {}", skill, e);
                }
            }

            // 2) interaction（1×1×1 居中在用户指定的方块位置）
            BlockPos ibp = INTERACTION_BLOCK.get(skill);
            if (ibp != null) {
                cleanInteractionAt(level, skill, ibp);
                spawnInteractionEntity(level, skill, ibp);
            }
        }
    }

    private static void cleanInteractionAt(ServerLevel level, SkillType skill, BlockPos block) {
        AABB box = new AABB(block).inflate(0.6);
        String wantedTag = interactionTagFor(skill);
        level.getEntitiesOfClass(net.minecraft.world.entity.Interaction.class, box)
            .stream()
            .filter(e -> e.getTags().contains(wantedTag) || e.getTags().contains(INTERACTION_TAG_ROOT))
            .forEach(Entity::discard);
    }

    private static void spawnInteractionEntity(ServerLevel level, SkillType skill, BlockPos block) {
        try {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "minecraft:interaction");
            tag.putFloat("width", 1.0f);
            tag.putFloat("height", 1.0f);
            tag.putBoolean("response", true);

            // BB 居中在该方块整格：Pos = (x+0.5, y, z+0.5)
            net.minecraft.nbt.ListTag pos = new net.minecraft.nbt.ListTag();
            pos.add(net.minecraft.nbt.DoubleTag.valueOf(block.getX() + 0.5));
            pos.add(net.minecraft.nbt.DoubleTag.valueOf(block.getY()));
            pos.add(net.minecraft.nbt.DoubleTag.valueOf(block.getZ() + 0.5));
            tag.put("Pos", pos);

            net.minecraft.nbt.ListTag tags = new net.minecraft.nbt.ListTag();
            tags.add(net.minecraft.nbt.StringTag.valueOf(INTERACTION_TAG_ROOT));
            tags.add(net.minecraft.nbt.StringTag.valueOf(interactionTagFor(skill)));
            tag.put("Tags", tags);

            Entity entity = EntityType.loadEntityRecursive(tag, level, e -> e);
            if (entity != null) level.addFreshEntity(entity);
        } catch (Exception e) {
            StardewCraft.LOGGER.error("[MASTERY_SITE] Failed to spawn interaction entity for {}", skill, e);
        }
    }

    private static void spawnTextDisplays(ServerLevel level) {
        for (SkillType skill : SkillType.values()) {
            net.minecraft.world.phys.Vec3 tpos = TEXT_POS.get(skill);
            if (tpos == null) continue;

            // 清理范围内旧的 text_display（按 tag 识别）。
            AABB box = new AABB(tpos, tpos).inflate(0.5);
            level.getEntitiesOfClass(net.minecraft.world.entity.Display.TextDisplay.class, box)
                .stream()
                .filter(e -> e.getTags().contains(TEXT_DISPLAY_TAG))
                .forEach(Entity::discard);

            try {
                CompoundTag tag = new CompoundTag();
                tag.putString("id", "minecraft:text_display");
                tag.putString("alignment", "center");
                tag.putInt("background", 0);
                tag.putBoolean("default_background", false);
                tag.putInt("line_width", 200);
                tag.putBoolean("see_through", false);
                tag.putBoolean("shadow", false);
                tag.putByte("text_opacity", (byte) -1);

                // text 是序列化 Component JSON。
                String textJson = "{\"translate\":\"" + TEXT_KEY.get(skill)
                    + "\",\"color\":\"" + TEXT_COLOR.get(skill) + "\",\"bold\":true}";
                tag.putString("text", textJson);

                net.minecraft.nbt.ListTag pos = new net.minecraft.nbt.ListTag();
                pos.add(net.minecraft.nbt.DoubleTag.valueOf(tpos.x));
                pos.add(net.minecraft.nbt.DoubleTag.valueOf(tpos.y));
                pos.add(net.minecraft.nbt.DoubleTag.valueOf(tpos.z));
                tag.put("Pos", pos);

                net.minecraft.nbt.ListTag tags = new net.minecraft.nbt.ListTag();
                tags.add(net.minecraft.nbt.StringTag.valueOf(TEXT_DISPLAY_TAG));
                tag.put("Tags", tags);

                Entity entity = EntityType.loadEntityRecursive(tag, level, e -> e);
                if (entity != null) level.addFreshEntity(entity);
            } catch (Exception e) {
                StardewCraft.LOGGER.error("[MASTERY_SITE] Failed to spawn text_display for {}", skill, e);
            }
        }
    }

    // ── NBT ──

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        tag.putInt("PlacedVersion", placedVersion);
        return tag;
    }

    private static MasterySiteInstaller load(CompoundTag tag, HolderLookup.Provider provider) {
        MasterySiteInstaller m = new MasterySiteInstaller();
        m.placedVersion = tag.getInt("PlacedVersion");
        return m;
    }

    public static com.stardew.craft.port.PortSavedData.Factory<MasterySiteInstaller> factory() {
        return new com.stardew.craft.port.PortSavedData.Factory<>(MasterySiteInstaller::new, MasterySiteInstaller::load);
    }
}
