package com.stardew.craft.shop;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.book.BookAcquisitionService;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.payload.GeodeCrackResultPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Server-side geode processing service — SDV Utility.getTreasureFromGeode() parity.
 *
 * SDV loot resolution order:
 * 1. Omni geode: 0.8 % prismatic shard (after 16 geodes cracked)
 * 2. Data-driven GeodeDrops (Objects.json RandomItemId per geode type)
 *    → 50 % chance when GeodeDropsDefaultItems = true
 * 3. Hardcoded fallback:
 *    a. 50 % stone / clay / type-gem
 *    b. 50 % ore (pool differs by geode type)
 */
@SuppressWarnings({"unchecked", "null"})
public class GeodeLootService {

    private static final int GEODE_COST = 25;

    /**
     * Pending treasure per player — SDV only gives treasure when geodeAnimationTimer <= 0.
     * Stored here until the client sends GeodeClaimPayload after animation finishes.
     */
    private static final Map<UUID, ItemStack> pendingTreasure = new ConcurrentHashMap<>();

    // ────────────────────────────────────────────────────────────────────
    //  SDV Objects.json GeodeDrops — data-driven mineral pools
    // ────────────────────────────────────────────────────────────────────

    // Geode (535): 16 minerals
    private static final Supplier<Item>[] GEODE_MINERALS = new Supplier[]{
        ModItems.ALAMITE, ModItems.CALCITE, ModItems.JAMBORITE, ModItems.JAGOITE,
        ModItems.MALACHITE, ModItems.NEKOITE, ModItems.ORPIMENT, ModItems.PETRIFIED_SLIME,
        ModItems.THUNDER_EGG, ModItems.CELESTINE, ModItems.SANDSTONE, ModItems.GRANITE,
        ModItems.LIMESTONE_MINERAL, ModItems.MUDSTONE, ModItems.SLATE, ModItems.DWARVISH_HELM
    };

    // Frozen Geode (536): 15 minerals
    private static final Supplier<Item>[] FROZEN_GEODE_MINERALS = new Supplier[]{
        ModItems.AERINITE, ModItems.ESPERITE, ModItems.FLUORAPATITE, ModItems.GEMINITE,
        ModItems.KYANITE, ModItems.LUNARITE, ModItems.PYRITE, ModItems.OCEAN_STONE,
        ModItems.GHOST_CRYSTAL, ModItems.OPAL, ModItems.MARBLE, ModItems.SOAPSTONE,
        ModItems.HEMATITE, ModItems.FAIRY_STONE, ModItems.ANCIENT_DRUM
    };

    // Magma Geode (537): 13 minerals
    private static final Supplier<Item>[] MAGMA_GEODE_MINERALS = new Supplier[]{
        ModItems.BIXITE, ModItems.BARYTE, ModItems.DOLOMITE, ModItems.HELVITE,
        ModItems.NEPTUNITE, ModItems.LEMON_STONE, ModItems.TIGERSEYE, ModItems.JASPER,
        ModItems.FIRE_OPAL, ModItems.BASALT, ModItems.OBSIDIAN, ModItems.STAR_SHARDS,
        ModItems.DWARF_GADGET
    };

    // Omni Geode (749): all three pools combined
    private static final Supplier<Item>[] OMNI_GEODE_MINERALS;
    static {
        List<Supplier<Item>> all = new ArrayList<>();
        Collections.addAll(all, GEODE_MINERALS);
        Collections.addAll(all, FROZEN_GEODE_MINERALS);
        Collections.addAll(all, MAGMA_GEODE_MINERALS);
        OMNI_GEODE_MINERALS = all.toArray(new Supplier[0]);
    }

    // ────────────────────────────────────────────────────────────────────
    //  Artifact Trove (275) — 严格对齐 SDV Content/Data/Objects.json "275".GeodeDrops：
    //  GeodeDropsDefaultItems=false, 单条 Chance=1.0 的 RandomItemId 列表（等权随机选一）。
    //  原版 RandomItemId：(O)100..125（跳过 102/107）+ (O)166 宝藏箱 +
    //  (O)373 棱彩碎片 + (O)797 珍珠 + (O)Book_Artifact（本模组未实现，省略）。
    // ────────────────────────────────────────────────────────────────────
    private static final Supplier<Item>[] ARTIFACT_TROVE_POOL = new Supplier[]{
        ModItems.CHIPPED_AMPHORA,    // (O)100
        ModItems.ARROWHEAD,          // (O)101
        ModItems.ANCIENT_DOLL,       // (O)103
        ModItems.ELVISH_JEWELRY,     // (O)104
        ModItems.CHEWING_STICK,      // (O)105
        ModItems.ORNAMENTAL_FAN,     // (O)106
        ModItems.RARE_DISC,          // (O)108
        ModItems.ANCIENT_SWORD,      // (O)109
        ModItems.RUSTY_SPOON,        // (O)110
        ModItems.RUSTY_SPUR,         // (O)111
        ModItems.RUSTY_COG,          // (O)112
        ModItems.CHICKEN_STATUE,     // (O)113
        ModItems.ANCIENT_SEED,       // (O)114
        ModItems.PREHISTORIC_TOOL,   // (O)115
        ModItems.DRIED_STARFISH,     // (O)116
        ModItems.ANCHOR,             // (O)117
        ModItems.GLASS_SHARDS,       // (O)118
        ModItems.BONE_FLUTE,         // (O)119
        ModItems.PREHISTORIC_HANDAXE,// (O)120
        ModItems.DWARVISH_HELM,      // (O)121
        ModItems.DWARF_GADGET,       // (O)122
        ModItems.ANCIENT_DRUM,       // (O)123
        ModItems.GOLDEN_MASK,        // (O)124
        ModItems.GOLDEN_RELIC,       // (O)125
        ModItems.TREASURE_CHEST,     // (O)166
        ModItems.PRISMATIC_SHARD,    // (O)373
        ModItems.PEARL               // (O)797
    };

    // ────────────────────────────────────────────────────────────────────

    public static void handleGeodeCrack(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) return;

        ItemStack geodeStack = player.getInventory().getItem(slot);
        if (geodeStack.isEmpty()) return;

        String geodeType = getGeodeType(geodeStack);
        if (geodeType == null) return;
        ResourceLocation customId = geodeType.indexOf(':') >= 0 ? ResourceLocation.tryParse(geodeType) : null;
        if (customId != null && !GeodeDropData.isAvailable(customId, player)) return;

        int money = PlayerStardewDataAPI.getMoney(player);
        if (money < GEODE_COST) return;

        int freeSlots = countFreeSlots(player);
        if (freeSlots < 1) return;

        // Deduct cost (SDV: Game1.player.Money -= 25)
        PlayerStardewDataAPI.removeMoney(player, GEODE_COST);

        // Consume one geode
        geodeStack.shrink(1);

        // Determine treasure (SDV parity)
        ItemStack treasure = getTreasureFromGeode(geodeType, player);

        // SDV: treasure is given to player ONLY when geodeAnimationTimer <= 0 in update().
        // Store pending treasure — the client will send GeodeClaimPayload after animation finishes.
        if (!treasure.isEmpty()) {
            pendingTreasure.put(player.getUUID(), treasure.copy());
        }

        // Sync the geode consumption to client (geode stack shrunk above).
        player.inventoryMenu.broadcastChanges();

        int newMoney = PlayerStardewDataAPI.getMoney(player);
        String treasureId = BuiltInRegistries.ITEM.getKey(treasure.getItem()).toString();
        PacketDistributor.sendToPlayer(player,
            new GeodeCrackResultPayload(treasureId, geodeType, newMoney));
    }

    /**
     * Called when client sends GeodeClaimPayload (animation finished).
     * Matches SDV: Game1.player.addItemToInventoryBool(geodeTreasure) at timer <= 0.
     */
    public static void handleGeodeClaim(ServerPlayer player) {
        ItemStack treasure = pendingTreasure.remove(player.getUUID());
        if (treasure != null && !treasure.isEmpty()) {
            ItemStack hudStack = treasure.copy();
            if (!player.getInventory().add(treasure)) {
                player.drop(treasure, false);
            }
            ItemPickupHudPacket.sendTo(player, hudStack, hudStack.getCount(), false);
            player.inventoryMenu.broadcastChanges();
        }
    }

    /**
     * Clean up pending treasure on player disconnect.
     * If the player had a pending geode treasure, give it back so it's not lost.
     */
    public static void onPlayerLogout(ServerPlayer player) {
        ItemStack treasure = pendingTreasure.remove(player.getUUID());
        if (treasure != null && !treasure.isEmpty()) {
            if (!player.getInventory().add(treasure)) {
                player.drop(treasure, false);
            }
        }
    }

    /**
     * SDV Utility.getTreasureFromGeode() parity.
     */
    private static ItemStack getTreasureFromGeode(String geodeType, ServerPlayer player) {
        Random r = new Random();
        // SDV: prewarm random (mimics seed-based RNG warm-up)
        int prewarm = r.nextInt(9) + 1;
        for (int i = 0; i < prewarm; i++) r.nextDouble();

        if (geodeType.indexOf(':') >= 0) {
            ResourceLocation customId = ResourceLocation.tryParse(geodeType);
            if (customId == null) return ItemStack.EMPTY;
            return GeodeDropData.roll(customId, player, r).orElse(ItemStack.EMPTY);
        }
        prewarm = r.nextInt(9) + 1;
        for (int i = 0; i < prewarm; i++) r.nextDouble();

        // ── Mystery Box / Golden Mystery Box ──
        if (geodeType.contains("mystery_box")) {
            return getMysteryBoxTreasure(geodeType, r, player);
        }

        // ── Artifact Trove (275) ──
        // SDV Utility.getTreasureFromGeode() 走数据驱动分支：
        // Objects.json "275".GeodeDrops 单条 Chance=1.0、MinStack=MaxStack=-1、
        // RandomItemId 等权随机选一。对应下方的 ARTIFACT_TROVE_POOL。
        if ("artifact_trove".equals(geodeType)) {
            Item chosen = ARTIFACT_TROVE_POOL[r.nextInt(ARTIFACT_TROVE_POOL.length)].get();
            return new ItemStack(chosen);
        }

        // ── Omni geode: 0.8 % prismatic shard ──
        // SDV: requires ≥ 16 geodes cracked; we always allow for simplicity
        if (geodeType.equals("omni_geode") && r.nextDouble() < 0.008) {
            return new ItemStack(ModItems.PRISMATIC_SHARD.get());
        }

        // ── Data-driven GeodeDrops ──
        // SDV: GeodeDropsDefaultItems = true for all four geode types
        //      → 50 % chance (!defaultItems || r.NextBool()) to use data pool
        Supplier<Item>[] pool = getMineralPool(geodeType);
        if (pool.length > 0 && r.nextBoolean()) {
            Item chosen = pool[r.nextInt(pool.length)].get();
            return new ItemStack(chosen);
        }

        // ── Hardcoded fallback ──
        // SDV: amount = r.Next(3)*2+1, 10% → 10, 1% → 20
        int amount = r.nextInt(3) * 2 + 1;
        if (r.nextDouble() < 0.1) amount = 10;
        if (r.nextDouble() < 0.01) amount = 20;

        // SDV: 50 % stone / clay / type-gem
        if (r.nextBoolean()) {
            switch (r.nextInt(4)) {
                case 0: case 1:
                    return new ItemStack(ModItems.STONE.get(), amount);
                case 2:
                    return new ItemStack(ModItems.CLAY.get());
                default:
                    return getTypeGem(geodeType, r);
            }
        }

        // SDV: ore fallback (differs by geode type)
        return getOreByType(geodeType, r, amount);
    }

    public static boolean isGeodeCrusherInput(ItemStack stack) {
        String geodeType = getGeodeType(stack);
        return geodeType != null && (geodeType.indexOf(':') >= 0
            || "geode".equals(geodeType)
            || "frozen_geode".equals(geodeType)
            || "magma_geode".equals(geodeType)
            || "omni_geode".equals(geodeType));
    }

    public static ItemStack getTreasureForGeodeCrusher(ItemStack stack, ServerPlayer player) {
        String geodeType = getGeodeType(stack);
        if (!isGeodeCrusherInput(stack) || geodeType == null) {
            return ItemStack.EMPTY;
        }
        return getTreasureFromGeode(geodeType, player);
    }

    // ────────────────────────────────────────────────────────────────────
    //  Mystery Box loot — SDV Utility.getTreasureFromGeode() parity
    // ────────────────────────────────────────────────────────────────────

    /**
     * SDV Mystery Box loot table. Golden mystery box has double rare chance (rareMod=2)
     * and an exclusive rare roll. Items we don't have are skipped (re-rolled to next check).
     */
    private static ItemStack getMysteryBoxTreasure(String geodeType, Random r, ServerPlayer player) {
        boolean isGolden = "golden_mystery_box".equals(geodeType);
        double rareMod = isGolden ? 2.0 : 1.0;
        BookAcquisitionService.recordMysteryBoxOpened(player);

        // SDV: rare pool only if opened > 10 or golden
        // We simplify: always enable for golden, require some usage otherwise
        boolean rarePoolEnabled = isGolden || r.nextDouble() < 0.8; // 模拟 opened > 10

        if (rarePoolEnabled) {
            // SDV Golden-only: Golden Animal Cracker (farming mastery + 0.5%)
            if (isGolden && r.nextDouble() < 0.005) {
                return makeItem("stardewcraft:golden_animal_cracker", 1);
            }
            // SDV Golden-only: Auto-Petter (0.5%)
            if (isGolden && r.nextDouble() < 0.005) {
                return makeItem("stardewcraft:auto_petter", 1);
            }

            // SDV: 0.2% × rareMod → Magic Rock Candy
            if (r.nextDouble() < 0.002 * rareMod) {
                return new ItemStack(ModItems.MAGIC_ROCK_CANDY.get());
            }
            // SDV: 0.4% × rareMod → Prismatic Shard
            if (r.nextDouble() < 0.004 * rareMod) {
                return new ItemStack(ModItems.PRISMATIC_SHARD.get());
            }
            // SDV: 0.8% × rareMod → Treasure Chest
            if (r.nextDouble() < 0.008 * rareMod) {
                return new ItemStack(ModItems.TREASURE_CHEST.get());
            }
            ItemStack book = BookAcquisitionService.rollMysteryBoxBook(player, r, rareMod);
            if (!book.isEmpty()) {
                return book;
            }
            // SDV: 1% × rareMod → Pearl / Golden Pumpkin (我们没有golden_pumpkin，只给pearl)
            if (r.nextDouble() < 0.01 * rareMod) {
                return new ItemStack(ModItems.PEARL.get());
            }
            // SDV: 1% × rareMod → MysteryHat (我们没有，跳过)
            // SDV: 1% × rareMod → MysteryShirt (我们没有，跳过)
            // SDV: 1% × rareMod → Wallpaper (我们没有，跳过)

            // SDV: 10% (always for golden) → medium-rare pool
            if (r.nextDouble() < 0.1 || isGolden) {
                return rollMysteryBoxMediumRare(r, player);
            }
        }

        // SDV: common pool (always available) — switch(14)
        return rollMysteryBoxCommon(r);
    }

    /** SDV mystery box medium-rare: switch(r.Next(15)), 跳过不存在的物品用替代 */
    private static ItemStack rollMysteryBoxMediumRare(Random r, ServerPlayer player) {
        switch (r.nextInt(13)) { // 我们可用的有13项
            case 0: // SDV case 0: Mega Bomb ×5
                return new ItemStack(ModItems.MEGA_BOMB.get(), 5);
            case 1: // SDV case 2: fishing≥6 → Dressed Spinner / Cork Bobber
            {
                int fishingLevel = PlayerStardewDataAPI.getSkillLevel(player, com.stardew.craft.player.SkillType.FISHING);
                if (fishingLevel >= 6 && r.nextBoolean()) {
                    return r.nextBoolean()
                        ? new ItemStack(ModItems.DRESSED_SPINNER.get())
                        : new ItemStack(ModItems.CORK_BOBBER.get());
                }
                // Fallback: Sprinkler
                return new ItemStack(ModItems.SPRINKLER.get());
            }
            case 2: // SDV case 4: Melon Seeds ×20
                return new ItemStack(ModItems.MELON_SEEDS.get(), 20);
            case 3: // SDV case 5: Pumpkin Seeds ×20
                return new ItemStack(ModItems.PUMPKIN_SEEDS.get(), 20);
            case 4: // SDV case 7: Warp Totem: Farm ×3
                return new ItemStack(ModItems.WARP_TOTEM_FARM.get(), 3);
            case 5: // SDV case 8: random (O)628-633 fruit sapling
                return randomFruitSapling(r);
            case 6: // SDV case 9: Random low-grade crop ×20 → mixed seeds
                return new ItemStack(ModItems.MIXED_SEEDS.get(), 20);
            case 7: // SDV case 10: Ossified Blade / Slingshot → Ossified Blade
                return makeItem("stardewcraft:ossified_blade", 1);
            case 8: // SDV case 11: Sprinkler
                return new ItemStack(ModItems.SPRINKLER.get());
            case 9: // SDV case 12: MysteryBox ×3-5
                return new ItemStack(ModItems.MYSTERY_BOX.get(), r.nextInt(3) + 3);
            case 10: // Quality Sprinkler (替代 SkillBook)
                return new ItemStack(ModItems.QUALITY_SPRINKLER.get());
            case 11: // Iridium Sprinkler (替代 raccoon seeds)
                return new ItemStack(ModItems.IRIDIUM_SPRINKLER.get());
            case 12: // Wild Bait ×10 (额外替代项)
                return new ItemStack(ModItems.WILD_BAIT.get(), 10);
            default:
                return new ItemStack(ModItems.COAL.get(), 5);
        }
    }

    private static ItemStack randomFruitSapling(Random r) {
        return switch (r.nextInt(6)) {
            case 0 -> new ItemStack(ModItems.CHERRY_SAPLING.get());
            case 1 -> new ItemStack(ModItems.APRICOT_SAPLING.get());
            case 2 -> new ItemStack(ModItems.ORANGE_SAPLING.get());
            case 3 -> new ItemStack(ModItems.PEACH_SAPLING.get());
            case 4 -> new ItemStack(ModItems.POMEGRANATE_SAPLING.get());
            default -> new ItemStack(ModItems.APPLE_SAPLING.get());
        };
    }

    /** SDV mystery box common pool: switch(14), 跳过不存在的用替代 */
    private static ItemStack rollMysteryBoxCommon(Random r) {
        switch (r.nextInt(14)) {
            case 0: // SDV: Coffee Bean ×3
                return new ItemStack(ModItems.COFFEE_BEAN.get(), 3);
            case 1: // SDV: Bomb ×5 → Mega Bomb ×2 (我们没bomb)
                return new ItemStack(ModItems.MEGA_BOMB.get(), 2);
            case 2: // SDV: Random low-grade crop ×8 → Mixed Seeds ×8
                return new ItemStack(ModItems.MIXED_SEEDS.get(), 8);
            case 3: // SDV: Random season seed → Melon Seeds ×5
                return new ItemStack(ModItems.MELON_SEEDS.get(), 5);
            case 4: // SDV: Random cooked food → Warp Totem: Farm
                return new ItemStack(ModItems.WARP_TOTEM_FARM.get());
            case 5: // SDV: Hardwood ×10 → Stone ×10
                return new ItemStack(ModItems.STONE.get(), 10);
            case 6: // SDV: Melon Seeds ×10
                return new ItemStack(ModItems.MELON_SEEDS.get(), 10);
            case 7: // SDV: Pumpkin Seeds ×10
                return new ItemStack(ModItems.PUMPKIN_SEEDS.get(), 10);
            case 8: // SDV: Warp Totem: Farm
                return new ItemStack(ModItems.WARP_TOTEM_FARM.get());
            case 9: // SDV: Warp Totem: Mountains
                return new ItemStack(ModItems.WARP_TOTEM_MOUNTAIN.get());
            case 10: // SDV: 40% Ring, else MysteryBox ×2
                if (r.nextDouble() < 0.4) {
                    return switch (r.nextInt(4)) {
                        case 0 -> makeItem("stardewcraft:small_glow_ring", 1);
                        case 1 -> makeItem("stardewcraft:warrior_ring", 1);
                        case 2 -> makeItem("stardewcraft:ring_of_yoba", 1);
                        default -> makeItem("stardewcraft:amethyst_ring", 1);
                    };
                }
                return new ItemStack(ModItems.MYSTERY_BOX.get(), 2);
            case 11: // SDV: MixedFlowerSeeds ×10 → Mixed Seeds ×10
                return new ItemStack(ModItems.MIXED_SEEDS.get(), 10);
            case 12: // SDV: Warp Totem: Beach
                return new ItemStack(ModItems.WARP_TOTEM_BEACH.get());
            case 13: // SDV default: Coal
                return new ItemStack(ModItems.COAL.get());
            default:
                return new ItemStack(ModItems.COAL.get());
        }
    }

    /** 按物品ID创建ItemStack，找不到返回coal兜底 */
    private static ItemStack makeItem(String itemId, int count) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(rl), count);
        }
        return new ItemStack(ModItems.COAL.get(), count);
    }

    /** SDV type-specific gem: geode→earth_crystal, frozen→frozen_tear, magma→fire_quartz, omni→random */
    private static ItemStack getTypeGem(String geodeType, Random r) {
        return switch (geodeType) {
            case "omni_geode" -> switch (r.nextInt(3)) {
                // SDV: "(O)" + (82 + r.Next(3)*2) → 82, 84, 86
                case 0 -> new ItemStack(ModItems.FIRE_QUARTZ.get());
                case 1 -> new ItemStack(ModItems.FROZEN_TEAR.get());
                default -> new ItemStack(ModItems.EARTH_CRYSTAL.get());
            };
            case "geode" -> new ItemStack(ModItems.EARTH_CRYSTAL.get());       // SDV "(O)86"
            case "frozen_geode" -> new ItemStack(ModItems.FROZEN_TEAR.get());  // SDV "(O)84"
            default -> new ItemStack(ModItems.FIRE_QUARTZ.get());              // magma → SDV "(O)82"
        };
    }

    /** SDV ore fallback by geode type. */
    private static ItemStack getOreByType(String geodeType, Random r, int amount) {
        return switch (geodeType) {
            // SDV geode (535): copper / iron(if deep) / coal — 3 choices
            case "geode" -> switch (r.nextInt(3)) {
                case 0 -> new ItemStack(ModItems.COPPER_ORE.get(), amount);
                case 1 -> new ItemStack(ModItems.IRON_ORE.get(), amount);
                default -> new ItemStack(ModItems.COAL.get(), amount);
            };
            // SDV frozen geode (536): copper / iron / coal / gold(if deep) — 4 choices
            case "frozen_geode" -> switch (r.nextInt(4)) {
                case 0 -> new ItemStack(ModItems.COPPER_ORE.get(), amount);
                case 1 -> new ItemStack(ModItems.IRON_ORE.get(), amount);
                case 2 -> new ItemStack(ModItems.COAL.get(), amount);
                default -> new ItemStack(ModItems.GOLD_ORE.get(), amount);
            };
            // SDV magma geode (537) / omni geode (749): copper / iron / coal / gold / iridium
            default -> switch (r.nextInt(5)) {
                case 0 -> new ItemStack(ModItems.COPPER_ORE.get(), amount);
                case 1 -> new ItemStack(ModItems.IRON_ORE.get(), amount);
                case 2 -> new ItemStack(ModItems.COAL.get(), amount);
                case 3 -> new ItemStack(ModItems.GOLD_ORE.get(), amount);
                default -> new ItemStack(ModItems.IRIDIUM_ORE.get(), amount / 2 + 1);
            };
        };
    }

    private static Supplier<Item>[] getMineralPool(String geodeType) {
        return switch (geodeType) {
            case "geode" -> GEODE_MINERALS;
            case "frozen_geode" -> FROZEN_GEODE_MINERALS;
            case "magma_geode" -> MAGMA_GEODE_MINERALS;
            case "omni_geode" -> OMNI_GEODE_MINERALS;
            default -> new Supplier[0];
        };
    }

    private static String getGeodeType(ItemStack stack) {
        ResourceLocation custom = GeodeDropData.definitionFor(stack);
        if (custom != null) return custom.toString();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        return switch (path) {
            case "geode" -> "geode";
            case "frozen_geode" -> "frozen_geode";
            case "magma_geode" -> "magma_geode";
            case "omni_geode" -> "omni_geode";
            case "artifact_trove" -> "artifact_trove";
            case "mystery_box" -> "mystery_box";
            case "golden_mystery_box" -> "golden_mystery_box";
            default -> null;
        };
    }

    private static int countFreeSlots(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).isEmpty()) count++;
        }
        return count;
    }
}
