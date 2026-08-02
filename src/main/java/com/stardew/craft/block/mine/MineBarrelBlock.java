package com.stardew.craft.block.mine;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.item.trinket.TrinketDropService;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;

/**
 * 矿井木桶方块（精确复刻 SDV BreakableContainer.releaseContents）
 *
 * 掉落逻辑按 SDV 源码 BreakableContainer.cs 行 248-450+ 还原：
 * - 20% 概率完全不掉落
 * - 0.81% mystery_box 特殊掉落
 * - 根据矿井区段（area 0/40/80）选择不同掉落表
 * - 每个区段：65% 普通掉落（80% 初级 9-way / 20% 次级）+ 40% 稀有掉落（5-way 含宝石和特殊物品）
 * - 稀有掉落中 20% 概率使用 getSpecialItemForThisMineLevel 按层级给不同价值物品
 */
@SuppressWarnings("null")
public class MineBarrelBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(2.5, 0, 2.5, 13.5, 21, 13.5);

    public MineBarrelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) {
            level.destroyBlock(pos, false);
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            level.destroyBlock(pos, false);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            dropBarrelLoot((ServerLevel) level, pos);
            level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ======================== SDV 精确掉落逻辑 ========================

    /**
     * 仿 BreakableContainer.releaseContents()
     * 概率链：20% 空 → 0.81% mystery_box → 区段掉落表
     */
    public static void dropBarrelLoot(ServerLevel level, BlockPos pos) {
        RandomSource r = level.getRandom();
        int floor = getFloorFromPos(pos);
        int area = getArea(floor);

        // SDV: 20% 概率完全不掉落
        if (r.nextFloat() < 0.20f) {
            return;
        }

        int effectiveMineLevel = floor == 77377 ? 5000 : floor;
        TrinketDropService.trySpawnContainerDrop(level, pos, 1.0 + effectiveMineLevel * 0.001);

        net.minecraft.world.entity.player.Player nearestPlayer = level.getNearestPlayer(
            pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 12.0D, false);
        com.stardew.craft.player.PlayerStardewData playerData = nearestPlayer instanceof net.minecraft.server.level.ServerPlayer serverPlayer
            ? PlayerDataManager.getPlayerData(serverPlayer)
            : null;
        boolean reachedMineBottom = nearestPlayer instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && com.stardew.craft.mining.MiningDataManager.getPlayerData(serverPlayer)
                .getMaxFloorReached() >= 120;

        // SDV: 0.81% mystery_box, routed through Utility.tryRollMysteryBox multiplier.
        if (r.nextDouble() < BookPowerEffects.applyMysteryBoxChance(playerData, 0.0081)) {
            drop(level, pos, item("mystery_box"), 1);
        }

        // 按区段分配掉落
        switch (area) {
            case 0  -> dropStandard(level, pos, r, floor, reachedMineBottom);
            case 40 -> dropFrost(level, pos, r, floor, reachedMineBottom);
            default -> dropDarkDesert(level, pos, r, floor, reachedMineBottom);
        }

        com.stardew.craft.festival.desert.DesertFestivalMineService.tryAddBarrelEggDrop(level, pos, r);
    }

    // ======================== Standard Barrel (area 0, ItemId "118", floor 1-39) ========================
    // SDV BreakableContainer.cs::releaseContents case "118"

    private static void dropStandard(
            ServerLevel level,
            BlockPos pos,
            RandomSource r,
            int floor,
            boolean reachedMineBottom
    ) {
        // 65% 普通掉落 ELSE 40% 稀有掉落（SDV 是 if/else if，互斥）
        if (r.nextFloat() < 0.65f) {
            if (r.nextFloat() < 0.80f) {
                // r.Next(9) — case 2 为空（无掉落），保留以维持概率分布
                switch (r.nextInt(9)) {
                    case 0 -> drop(level, pos, item("coal"), 1 + r.nextInt(2));        // (O)382
                    case 1 -> drop(level, pos, item("copper_ore"), 1 + r.nextInt(3));  // (O)378
                    case 2 -> { /* empty */ }
                    case 3 -> drop(level, pos, item("stone"), 2 + r.nextInt(4));       // (O)390
                    case 4 -> drop(level, pos, item("wood_normal"), 2);                 // (O)388 r.Next(2,3)==2
                    case 5 -> drop(level, pos,
                            item(reachedMineBottom
                                    ? "quartz"
                                    : (r.nextBoolean() ? "sap" : "basic_retaining_soil")),
                            2 + r.nextInt(2));
                    case 6 -> drop(level, pos, item("wood_normal"), 2 + r.nextInt(4)); // (O)388
                    case 7 -> drop(level, pos, item("stone"), 2 + r.nextInt(4));       // (O)390
                    case 8 -> drop(level, pos, item("mixed_seeds"), 1);                 // (O)770
                }
            } else {
                // r.Next(4) — 3/4 cave_carrot, 1/4 geode
                switch (r.nextInt(4)) {
                    case 0, 1, 2 -> drop(level, pos, item("cave_carrot"), 1 + r.nextInt(2)); // (O)78
                    case 3       -> drop(level, pos, item("geode"), 1 + r.nextInt(2));        // (O)535
                }
            }
        } else if (r.nextFloat() < 0.40f) {
            // 40% 稀有 r.Next(5)
            switch (r.nextInt(5)) {
                case 0 -> drop(level, pos, item("amethyst"), 1);              // (O)66
                case 1 -> drop(level, pos, item("topaz"), 1);                 // (O)68
                case 2 -> drop(level, pos, item("wood_hard"), 1);             // (O)709
                case 3 -> drop(level, pos, item("geode"), 1);                 // (O)535
                case 4 -> dropSpecialItem(level, pos, r, floor);              // getSpecialItemForThisMineLevel
            }
        }
    }

    // ======================== Frost Barrel (area 40, ItemId "120", floor 40-79) ========================
    // SDV BreakableContainer.cs::releaseContents case "120"

    private static void dropFrost(
            ServerLevel level,
            BlockPos pos,
            RandomSource r,
            int floor,
            boolean reachedMineBottom
    ) {
        if (r.nextFloat() < 0.65f) {
            if (r.nextFloat() < 0.80f) {
                switch (r.nextInt(9)) {
                    case 0 -> drop(level, pos, item("coal"), 1 + r.nextInt(2));        // (O)382
                    case 1 -> drop(level, pos, item("iron_ore"), 1 + r.nextInt(3));    // (O)380
                    case 2 -> { /* empty */ }
                    case 3 -> drop(level, pos, item("copper_ore"), 2 + r.nextInt(4));  // (O)378
                    case 4 -> drop(level, pos, item("wood_normal"), 2 + r.nextInt(4)); // (O)388
                    case 5 -> drop(level, pos,
                            item(reachedMineBottom
                                    ? "frozen_tear"
                                    : (r.nextBoolean() ? "sap" : "quality_retaining_soil")),
                            2 + r.nextInt(2));
                    case 6 -> drop(level, pos, item("stone"), 2 + r.nextInt(2));       // (O)390 r.Next(2,4)
                    case 7 -> drop(level, pos, item("stone"), 2 + r.nextInt(4));       // (O)390
                    case 8 -> drop(level, pos, item("mixed_seeds"), 1);                 // (O)770
                }
            } else {
                switch (r.nextInt(4)) {
                    case 0, 2, 3 -> drop(level, pos, item("cave_carrot"), 1 + r.nextInt(2)); // (O)78
                    case 1       -> drop(level, pos, item("frozen_geode"), 1 + r.nextInt(2)); // (O)536
                }
            }
        } else if (r.nextFloat() < 0.40f) {
            switch (r.nextInt(5)) {
                case 0 -> drop(level, pos, item("aquamarine"), 1);              // (O)62
                case 1 -> drop(level, pos, item("jade"), 1);                    // (O)70
                case 2 -> drop(level, pos, item("wood_hard"), 1 + r.nextInt(3)); // (O)709
                case 3 -> drop(level, pos, item("frozen_geode"), 1);            // (O)536
                case 4 -> dropSpecialItem(level, pos, r, floor);
            }
        }
    }

    // ======================== Dark/Desert Barrel (area 80+, ItemId "122"/"124") ========================
    // SDV BreakableContainer.cs::releaseContents case "122"/case "124"（共用）

    private static void dropDarkDesert(
            ServerLevel level,
            BlockPos pos,
            RandomSource r,
            int floor,
            boolean reachedMineBottom
    ) {
        if (r.nextFloat() < 0.65f) {
            if (r.nextFloat() < 0.80f) {
                // r.Next(8) — case 2 为空
                switch (r.nextInt(8)) {
                    case 0 -> drop(level, pos, item("coal"), 1 + r.nextInt(2));        // (O)382
                    case 1 -> drop(level, pos, item("gold_ore"), 1 + r.nextInt(3));    // (O)384
                    case 2 -> { /* empty */ }
                    case 3 -> drop(level, pos, item("iron_ore"), 2 + r.nextInt(4));    // (O)380
                    case 4 -> drop(level, pos, item("copper_ore"), 2 + r.nextInt(4));  // (O)378
                    case 5 -> drop(level, pos, item("stone"), 2 + r.nextInt(4));       // (O)390
                    case 6 -> drop(level, pos, item("wood_normal"), 2 + r.nextInt(4)); // (O)388
                    case 7 -> drop(level, pos, item("bone_fragment"), 2 + r.nextInt(4)); // (O)881
                }
            } else {
                switch (r.nextInt(4)) {
                    case 0 -> drop(level, pos, item("cave_carrot"), 1 + r.nextInt(2));   // (O)78
                    case 1 -> drop(level, pos, item("magma_geode"), 1 + r.nextInt(2));   // (O)537
                    case 2 -> drop(level, pos,
                            item(reachedMineBottom ? "fire_quartz" : "cave_carrot"),
                            1 + r.nextInt(2));
                    case 3 -> drop(level, pos, item("cave_carrot"), 1 + r.nextInt(2));   // (O)78
                }
            }
        } else if (r.nextFloat() < 0.40f) {
            // r.Next(6)
            switch (r.nextInt(6)) {
                case 0 -> drop(level, pos, item("emerald"), 1);                  // (O)60
                case 1 -> drop(level, pos, item("ruby"), 1);                     // (O)64
                case 2 -> drop(level, pos, item("wood_hard"), 1 + r.nextInt(3)); // (O)709
                case 3 -> drop(level, pos, item("omni_geode"), 1);                // (O)749
                case 4 -> dropSpecialItem(level, pos, r, floor);                  // getSpecialItemForThisMineLevel
                case 5 -> drop(level, pos, item("warp_totem_desert"), 1);         // (O)688
            }
        }
    }

    // ======================== Special Item (仿 getSpecialItemForThisMineLevel) ========================

    private static void dropSpecialItem(ServerLevel level, BlockPos pos, RandomSource r, int floor) {
        // Preserve the original slot count and order. The five unported club IDs
        // use the closest registered weapon by original damage range.
        String[] pool;
        if (floor < 20) {
            pool = new String[]{"carving_knife", "pirate_sword", "sneakers",
                    "rubber_boots", "small_glow_ring", "small_magnet_ring"};
        } else if (floor < 40) {
            pool = new String[]{"wind_spire", "pirate_sword", "sneakers",
                    "rubber_boots", "small_glow_ring", "small_magnet_ring", "forest_sword"};
        } else if (floor < 60) {
            pool = new String[]{"iron_edge", "bone_sword", "forest_sword",
                    "thermal_boots", "glow_ring", "magnet_ring", "iron_edge"};
        } else if (floor < 80) {
            pool = new String[]{"bone_sword", "iron_edge", "combat_boots",
                    "thermal_boots", "glow_ring", "magnet_ring", "shadow_dagger"};
        } else if (floor < 100) {
            pool = new String[]{"yeti_tooth", "yeti_tooth", "dark_boots",
                    "genie_shoes", "burglars_shank", "dark_sword",
                    "tempered_broadsword", "holy_blade"};
        } else if (floor < 120) {
            pool = new String[]{"shadow_dagger", "steel_falchion", "dark_boots",
                    "genie_shoes", "burglars_shank", "tempered_broadsword",
                    "immunity_band", "holy_blade"};
        } else {
            pool = new String[]{"wicked_kris", "steel_falchion", "dark_boots",
                    "genie_shoes", "burglars_shank", "dark_sword",
                    "tempered_broadsword", "battery_pack", "crystal_shoes",
                    "curiosity_lure", "lucky_ring", "immunity_band"};
        }
        drop(level, pos, item(pool[r.nextInt(pool.length)]), 1);
    }

    // ======================== helpers ========================

    private static void drop(ServerLevel level, BlockPos pos, Item item, int count) {
        if (item == null || count <= 0) return;
        Block.popResource(level, pos, new ItemStack(item, count));
    }

    private static Item item(String name) {
        var rl = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, name);
        Item found = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        if (found == Items.AIR) {
            StardewCraft.LOGGER.warn("[MineBarrel] Item not found: {}", name);
            return null;
        }
        return found;
    }

    private static int getFloorFromPos(BlockPos pos) {
        float spacing = com.stardew.craft.mining.MiningCoordinates.FLOOR_SPACING;
        return Math.max(0, Math.round(pos.getZ() / spacing));
    }

    private static int getArea(int floor) {
        if (floor < 40) return 0;
        if (floor < 80) return 40;
        return 80;
    }
}
