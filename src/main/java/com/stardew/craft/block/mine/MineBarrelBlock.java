package com.stardew.craft.block.mine;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.trinket.TrinketDropService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Two-cell mine caches; one accepted hit releases the source container loot exactly once. */
@SuppressWarnings("null")
public class MineBarrelBlock extends com.stardew.craft.block.decor.MapDecorStaticBlock {
    private final boolean crate;
    private record BreakOwner(ServerLevel level, BlockPos main, long tick, net.minecraft.server.level.ServerPlayer player, boolean explosion) {}
    private static final ThreadLocal<BreakOwner> BREAK_OWNER = new ThreadLocal<>();

    /** Weapons and bombs must pass their owner through the actual main-cell removal. */
    public static boolean breakBy(ServerLevel level, BlockPos pos, net.minecraft.server.level.ServerPlayer player) {
        return breakBy(level, pos, player, false);
    }

    public static boolean breakByExplosion(ServerLevel level, BlockPos pos, net.minecraft.server.level.ServerPlayer player) {
        return breakBy(level, pos, player, true);
    }

    private static boolean breakBy(ServerLevel level, BlockPos pos, net.minecraft.server.level.ServerPlayer player, boolean explosion) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MineBarrelBlock block)) return false;
        BlockPos main = block.findMainPos(level, pos, state);
        if (main == null) return false;
        BreakOwner previous = BREAK_OWNER.get();
        BREAK_OWNER.set(new BreakOwner(level, main.immutable(), level.getGameTime(), player, explosion));
        try { return level.destroyBlock(main, false); }
        finally {
            if (previous == null) BREAK_OWNER.remove(); else BREAK_OWNER.set(previous);
        }
    }

    public MineBarrelBlock(Properties properties) { this(properties, false); }

    public MineBarrelBlock(Properties properties, boolean crate) {
        super(properties, "block/mine/loot/earth_" + (crate ? "crate" : "barrel"));
        this.crate = crate;
        registerDefaultState(defaultBlockState().setValue(MineBuildingTheme.PROPERTY, MineBuildingTheme.EARTH));
    }

    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MineBuildingTheme.PROPERTY);
    }

    @Override protected VoxelShape canonicalShape() {
        return Block.box(0, 0, 0, 16, crate ? 20 : 25, 16);
    }

    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(MineBuildingTheme.PROPERTY, MineBuildingTheme.forPlacement(context));
    }

    @Override public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        return MineBuildingTheme.picked(this, state);
    }

    @Override protected java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return java.util.List.of();
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server) {
            BlockPos main = findMainPos(level, pos, state);
            if (main != null) {
                // MAIN is removed by vanilla immediately after this callback. Do not remove it here:
                // that would skip vanilla's successful-break stats and tool durability handling.
                BREAK_OWNER.set(new BreakOwner(server, main.immutable(), server.getGameTime(),
                        player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null, false));
                if (state.getValue(PART) == Part.EXTENSION) {
                    runWithDropsSuppressed(() -> level.removeBlock(main, false));
                    return state;
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && state.getValue(PART) == Part.MAIN && level instanceof ServerLevel server) {
            BreakOwner owner = BREAK_OWNER.get();
            boolean owned = owner != null && owner.level() == server && owner.main().equals(pos)
                    && owner.tick() == server.getGameTime();
            var player = owned ? owner.player() : null;
            if (owner != null && owner.level() == server && owner.main().equals(pos)) BREAK_OWNER.remove();
            if ((owned && owner.explosion() || player == null || !player.isCreative())
                    && !com.stardew.craft.mining.OrdinaryMineRuntime.isRebuilding()) {
                com.stardew.craft.mining.OrdinaryMineRuntime.barrelBroken(server, pos);
                dropBarrelLoot(server, pos, state.getValue(MineBuildingTheme.PROPERTY), player);
                level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, .9F);
            }
        }
        runWithDropsSuppressed(() -> super.onRemove(state, level, pos, next, moving));
    }

    @Override protected BlockState updateShape(BlockState state, net.minecraft.core.Direction direction, BlockState neighbor,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        level.scheduleTick(pos, this, 1); return state;
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!super.canSurvive(state, level, pos)) runWithDropsSuppressed(() -> level.removeBlock(pos, false));
    }

    /** Runtime population reserves both cells; never bake these caches into architecture. */
    public static boolean placeRandom(ServerLevel level, BlockPos pos, RandomSource random, MineBuildingTheme theme) {
        var block = (MineBarrelBlock) (random.nextBoolean()
                ? com.stardew.craft.block.ModBlocks.MINE_CRATE.get() : com.stardew.craft.block.ModBlocks.MINE_BARREL.get());
        if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.above()).canBeReplaced()) return false;
        var state = block.defaultBlockState().setValue(MineBuildingTheme.PROPERTY, theme);
        level.setBlock(pos, state, 2);
        return block.placeExtensions(level, pos, state);
    }

    // ======================== BreakableContainer.releaseContents ========================

    public static void dropBarrelLoot(ServerLevel level, BlockPos pos) {
        int floor = getFloorFromPos(pos);
        dropBarrelLoot(level, pos, floor < 40 ? MineBuildingTheme.EARTH : floor < 80 ? MineBuildingTheme.FROST : MineBuildingTheme.LAVA, null);
    }

    public static void dropBarrelLoot(ServerLevel level, BlockPos pos, MineBuildingTheme theme, net.minecraft.server.level.ServerPlayer player) {
        int floor = level.dimension() == com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING
                ? getFloorFromPos(pos) : switch (theme) {
                    case FROST, FROST_DARK -> 40;
                    case LAVA, LAVA_DARK -> 80;
                    case DESERT, DESERT_DARK -> 121;
                    default -> 1;
                };
        int area = switch (theme) {
            case FROST, FROST_DARK -> 40;
            case LAVA, LAVA_DARK, DESERT, DESERT_DARK -> 80;
            default -> 0;
        };

        RandomSource r = com.stardew.craft.mining.MineContainerRewards.random(level, pos, floor);
        int effectiveMineLevel = floor == 77377 ? 5000 : floor;
        var extras = com.stardew.craft.mining.MineContainerRewards.roll(player, r, () -> {
            com.stardew.craft.festival.desert.DesertFestivalMineService.tryAddBarrelEggDrop(level, pos, r);
            TrinketDropService.trySpawnContainerDrop(level, pos, 1.0 + effectiveMineLevel * 0.001, player);
        });
        for (ItemStack stack : extras.items()) Block.popResource(level, pos, stack);
        if (!extras.continueToContents()) return;

        boolean reachedMineBottom = player != null && com.stardew.craft.mining.MiningDataManager
                .getPlayerData(player).getMaxFloorReached() >= 120;

        // 按区段分配掉落
        switch (area) {
            case 0  -> dropStandard(level, pos, r, floor, reachedMineBottom);
            case 40 -> dropFrost(level, pos, r, floor, reachedMineBottom);
            default -> dropDarkDesert(level, pos, r, floor, reachedMineBottom);
        }

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
        if (r.nextDouble() < 0.65) {
            if (r.nextDouble() < 0.80) {
                // r.Next(9) — case 2 为空（无掉落），保留以维持概率分布
                switch (r.nextInt(9)) {
                    case 0 -> drop(level, pos, item("coal"), 1 + r.nextInt(2));        // (O)382
                    case 1 -> drop(level, pos, item("copper_ore"), 1 + r.nextInt(3));  // (O)378
                    case 2 -> { /* empty */ }
                    case 3 -> drop(level, pos, item("stone"), 2 + r.nextInt(4));       // (O)390
                    case 4 -> drop(level, pos, item("wood_normal"), 2 + r.nextInt(1));                 // (O)388 r.Next(2,3)==2
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
        } else if (r.nextDouble() < 0.40) {
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
        if (r.nextDouble() < 0.65) {
            if (r.nextDouble() < 0.80) {
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
        } else if (r.nextDouble() < 0.40) {
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
        if (r.nextDouble() < 0.65) {
            if (r.nextDouble() < 0.80) {
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
        } else if (r.nextDouble() < 0.40) {
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
        if (floor > 0 && level.dimension() == com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING) {
            Block.popResource(level,pos,com.stardew.craft.mining.OrdinaryMineSpecialLoot.roll(level,floor,pos));
            r.nextInt(4); // Source debris direction; keep the container stream aligned.
            return;
        }
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
        var rl = new net.minecraft.resources.ResourceLocation(StardewCraft.MODID, name);
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


}
