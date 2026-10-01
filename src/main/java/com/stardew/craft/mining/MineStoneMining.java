package com.stardew.craft.mining;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.mine.MineStoneBlock;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.event.FarmAreaProtectionEvents;
import com.stardew.craft.festival.desert.DesertFestivalMineService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.tool.StardewPickaxeItem;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.player.SkillType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.minecraftforge.event.level.BlockEvent;
import java.util.ArrayList;
import java.util.List;

/** Source-mapped ground nodes and their original drop branches. */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class MineStoneMining {
    private MineStoneMining() {}

    /** Exact source appearance lookup for the upcoming generator; never substitute an unknown ID. */
    public static java.util.Optional<BlockState> stateForSource(String sourceId, int sourceHealth) {
        Block block = switch (sourceId) {
            case "32" -> com.stardew.craft.block.ModBlocks.MINE_STONE_32.get();
            case "38" -> com.stardew.craft.block.ModBlocks.MINE_STONE_38.get();
            case "40" -> com.stardew.craft.block.ModBlocks.MINE_STONE_40.get();
            case "42" -> com.stardew.craft.block.ModBlocks.MINE_STONE_42.get();
            case "668" -> com.stardew.craft.block.ModBlocks.MINE_STONE_668.get();
            case "670" -> com.stardew.craft.block.ModBlocks.MINE_STONE_670.get();
            case "751" -> com.stardew.craft.block.ModBlocks.MINE_STONE_751.get();
            case "8" -> com.stardew.craft.block.ModBlocks.MINE_STONE_8.get();
            case "10" -> com.stardew.craft.block.ModBlocks.MINE_STONE_10.get();
            case "44" -> com.stardew.craft.block.ModBlocks.MINE_STONE_44.get();
            case "34" -> com.stardew.craft.block.ModBlocks.MINE_STONE_34.get();
            case "36" -> com.stardew.craft.block.ModBlocks.MINE_STONE_36.get();
            case "48" -> com.stardew.craft.block.ModBlocks.MINE_STONE_48.get();
            case "50" -> com.stardew.craft.block.ModBlocks.MINE_STONE_50.get();
            case "52" -> com.stardew.craft.block.ModBlocks.MINE_STONE_52.get();
            case "54" -> com.stardew.craft.block.ModBlocks.MINE_STONE_54.get();
            case "290" -> com.stardew.craft.block.ModBlocks.MINE_STONE_290.get();
            case "6" -> com.stardew.craft.block.ModBlocks.MINE_STONE_6.get();
            case "14" -> com.stardew.craft.block.ModBlocks.MINE_STONE_14.get();
            case "2" -> com.stardew.craft.block.ModBlocks.MINE_STONE_2.get();
            case "56" -> com.stardew.craft.block.ModBlocks.MINE_STONE_56.get();
            case "58" -> com.stardew.craft.block.ModBlocks.MINE_STONE_58.get();
            case "760" -> com.stardew.craft.block.ModBlocks.MINE_STONE_760.get();
            case "762" -> com.stardew.craft.block.ModBlocks.MINE_STONE_762.get();
            case "764" -> com.stardew.craft.block.ModBlocks.MINE_STONE_764.get();
            case "4" -> com.stardew.craft.block.ModBlocks.MINE_STONE_4.get();
            case "12" -> com.stardew.craft.block.ModBlocks.MINE_STONE_12.get();
            case "46" -> com.stardew.craft.block.ModBlocks.MINE_STONE_46.get();
            case "765" -> com.stardew.craft.block.ModBlocks.MINE_STONE_765.get();
            case "CalicoEggStone_0" -> com.stardew.craft.block.ModBlocks.MINE_STONE_CALICO_EGG_STONE_0.get();
            case "CalicoEggStone_1" -> com.stardew.craft.block.ModBlocks.MINE_STONE_CALICO_EGG_STONE_1.get();
            case "CalicoEggStone_2" -> com.stardew.craft.block.ModBlocks.MINE_STONE_CALICO_EGG_STONE_2.get();
            case "75" -> com.stardew.craft.block.ModBlocks.MINE_STONE_75.get();
            case "76" -> com.stardew.craft.block.ModBlocks.MINE_STONE_76.get();
            case "77" -> com.stardew.craft.block.ModBlocks.MINE_STONE_77.get();
            case "95" -> com.stardew.craft.block.ModBlocks.MINE_STONE_95.get();
            case "846" -> com.stardew.craft.block.ModBlocks.MINE_STONE_846.get();
            case "847" -> com.stardew.craft.block.ModBlocks.MINE_STONE_847.get();
            case "849" -> com.stardew.craft.block.ModBlocks.MINE_STONE_849.get();
            case "850" -> com.stardew.craft.block.ModBlocks.MINE_STONE_850.get();
            case "VolcanoGoldNode" -> com.stardew.craft.block.ModBlocks.MINE_STONE_VOLCANO_GOLD_NODE.get();
            case "VolcanoCoalNode0" -> com.stardew.craft.block.ModBlocks.MINE_STONE_VOLCANO_COAL_NODE0.get();
            case "VolcanoCoalNode1" -> com.stardew.craft.block.ModBlocks.MINE_STONE_VOLCANO_COAL_NODE1.get();
            case "BasicCoalNode0" -> com.stardew.craft.block.ModBlocks.MINE_STONE_BASIC_COAL_NODE0.get();
            case "BasicCoalNode1" -> com.stardew.craft.block.ModBlocks.MINE_STONE_BASIC_COAL_NODE1.get();
            case "843" -> com.stardew.craft.block.ModBlocks.MINE_STONE_843.get();
            case "844" -> com.stardew.craft.block.ModBlocks.MINE_STONE_844.get();
            case "845" -> com.stardew.craft.block.ModBlocks.MINE_STONE_845.get();
            case "817" -> com.stardew.craft.block.ModBlocks.MINE_STONE_817.get();
            case "818" -> com.stardew.craft.block.ModBlocks.MINE_STONE_818.get();
            case "819" -> com.stardew.craft.block.ModBlocks.MINE_STONE_819.get();
            case "450" -> com.stardew.craft.block.ModBlocks.MINE_STONE_450.get();
            case "25" -> com.stardew.craft.block.ModBlocks.MINE_STONE_25.get();
            case "816" -> com.stardew.craft.block.ModBlocks.MINE_STONE_816.get();
            case "343" -> com.stardew.craft.block.ModBlocks.MINE_STONE_343.get();
            default -> null;
        };
        return block == null ? java.util.Optional.empty() : java.util.Optional.of(
                block.defaultBlockState().setValue(MineStoneBlock.STONE_HEALTH, sourceHealth));
    }

    public static boolean isCalicoStone(String sourceId) {
        return sourceId.equals("CalicoEggStone_0") || sourceId.equals("CalicoEggStone_1") || sourceId.equals("CalicoEggStone_2");
    }

    /** Absolute client progress avoids repeated float-addition drift, even for long configured durations. */
    public static float progressAfterTicks(int elapsedTicks, int requiredTicks) {
        return Math.min(1.0F, elapsedTicks / (float) requiredTicks);
    }

    public static int pickaxePower(ItemStack stack) {
        if (stack.getItem() instanceof StardewPickaxeItem pickaxe) {
            return pickaxe.getStardewTier() + 1
                    + (StardewEnchantments.has(stack, StardewEnchantments.POWERFUL) ? 1 : 0);
        }
        return stack.is(ItemTags.PICKAXES) || stack.getItem() instanceof PickaxeItem ? 1 : 0;
    }

    public static int requiredSwings(int health, int power) {
        return Math.max(1, (int) Math.ceil(health / (double) Math.max(1, power)));
    }

    public static int breakTicks(int health, ItemStack tool) {
        int tier = tool.getItem() instanceof StardewPickaxeItem pickaxe ? pickaxe.getStardewTier() : 0;
        boolean swift = tool.getItem() instanceof StardewPickaxeItem
                && StardewEnchantments.has(tool, StardewEnchantments.SWIFT);
        return breakTicks(health, pickaxePower(tool), tier, swift);
    }

    /** Whole source swings plus the user-approved MC bonus of one tick per upgraded tier. */
    public static int breakTicks(int health, int power, int tier, boolean swift) {
        return Math.max(Config.SERVER.GROUND_STONE_MIN_TICKS.get(),
                (int) Math.ceil(Math.max(1, Config.SERVER.GROUND_STONE_TICKS_PER_SWING.get() - tier)
                        * requiredSwings(health, power) * (swift ? 0.66 : 1.0)));
    }

    public static float energyCost(int health, int power, int miningLevel) {
        double perSwing = Math.max(0, Config.SERVER.GROUND_STONE_ENERGY_PER_SWING.get() - miningLevel * 0.1);
        return (float) (perSwing * requiredSwings(health, power));
    }

    public static float energyCost(ServerPlayer player, BlockState state) {
        return energyCost(player, state, player.getMainHandItem());
    }

    private static float energyCost(ServerPlayer player, BlockState state, ItemStack tool) {
        if (tool.getItem() instanceof StardewPickaxeItem
                && StardewEnchantments.has(tool, StardewEnchantments.EFFICIENT)) return 0;
        return energyCost(state.getValue(MineStoneBlock.STONE_HEALTH), pickaxePower(tool),
                PlayerStardewDataAPI.getSkillLevel(player, SkillType.MINING));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof MineStoneBlock)
                || !(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) return;
        if (pickaxePower(player.getMainHandItem()) == 0
                || (player.level().dimension() == ModDimensions.STARDEW_VALLEY
                && !FarmAreaProtectionEvents.canModifyAt(player, event.getPos()))) {
            event.setCanceled(true);
            return;
        }
        // Eligibility only: payment and rewards happen after actual destruction, never on a canceled break.
        if (!PlayerStardewDataAPI.canConsumeEnergy(player, energyCost(player, event.getState()))) {
            event.setCanceled(true);
            com.stardew.craft.network.payload.HudHintPayload.send(player, "stardewcraft.message.player.exhausted");
        }
    }

    @SubscribeEvent
    public static void afterBreak(BlockDropsEvent event) {
        if (!(event.getState().getBlock() instanceof MineStoneBlock)) return;
        event.getDrops().clear();
        event.setDroppedExperience(0);
        if (!(event.getBreaker() instanceof ServerPlayer player) || player.isCreative()) return;
        // Vanilla may consume the tool's last durability before emitting this event.
        ItemStack tool = event.getTool();
        if (pickaxePower(tool) == 0) return;
        if (!PlayerStardewDataAPI.consumeEnergyOrNotify(player, energyCost(player, event.getState(), tool))) return;
        finishDrops(event.getLevel(), player, event.getPos(), event.getState(), false);
    }

    public record Context(String sourceId, int floor, int mining, int luck, double dailyLuck, boolean excavator,
                          boolean geologist, boolean prospector, boolean geodeBlessing, boolean coalBlessing,
                          boolean festival, int festivalRating, int addedOres, boolean miningMastery) {
        public Context(int sourceId, int floor, int mining, int luck, double dailyLuck, boolean excavator,
                       boolean geologist, boolean prospector, boolean geodeBlessing, boolean coalBlessing,
                       boolean festival, int festivalRating, int addedOres, boolean miningMastery) {
            this(Integer.toString(sourceId), floor, mining, luck, dailyLuck, excavator, geologist, prospector,
                    geodeBlessing, coalBlessing, festival, festivalRating, addedOres, miningMastery);
        }
    }
    public record Result(List<ItemStack> drops, int miningExperience) {}

    public static Result roll(Context c, RandomSource r) {
        List<ItemStack> drops = new ArrayList<>();
        // GameLocation.breakStone handles rich stones before MineShaft's ordinary-stone rolls.
        if (c.sourceId.equals("846") || c.sourceId.equals("847") || c.sourceId.equals("845") || c.sourceId.equals("668") || c.sourceId.equals("670")) {
            int count = c.addedOres + 1 + r.nextInt(2)
                    + (r.nextDouble() < c.luck / 100.0 ? 1 : 0)
                    + (r.nextDouble() < c.mining / 100.0 ? 1 : 0);
            drops.add(new ItemStack(ModItems.STONE.get(), count));
            boolean coal = r.nextDouble() < 0.08;
            if (coal) drops.add(new ItemStack(ModItems.COAL.get(), 1 + c.addedOres));
            return new Result(drops, coal ? 4 : 3);
        }
        if (c.sourceId.equals("849") || c.sourceId.equals("751") || c.sourceId.equals("290") || c.sourceId.equals("850")
                || c.sourceId.equals("764") || c.sourceId.equals("VolcanoGoldNode") || c.sourceId.equals("VolcanoCoalNode0") || c.sourceId.equals("VolcanoCoalNode1")
                || c.sourceId.equals("BasicCoalNode0") || c.sourceId.equals("BasicCoalNode1")) {
            int count = c.addedOres + 1 + r.nextInt(3)
                    + (r.nextDouble() < c.luck / 100.0 ? 1 : 0)
                    + (r.nextDouble() < c.mining / 100.0 ? 1 : 0);
            var ore = switch (c.sourceId) {
                case "764", "VolcanoGoldNode" -> ModItems.GOLD_ORE.get();
                case "290", "850" -> ModItems.IRON_ORE.get();
                case "VolcanoCoalNode0", "VolcanoCoalNode1", "BasicCoalNode0", "BasicCoalNode1" -> ModItems.COAL.get();
                default -> ModItems.COPPER_ORE.get();
            };
            int experience = switch (c.sourceId) {
                case "764", "VolcanoGoldNode" -> 18;
                case "290", "850" -> 12;
                case "VolcanoCoalNode0", "VolcanoCoalNode1", "BasicCoalNode0", "BasicCoalNode1" -> 10;
                default -> 5;
            };
            drops.add(new ItemStack(ore, count));
            return new Result(drops, experience);
        }
        if (c.sourceId.equals("4") || c.sourceId.equals("12") || c.sourceId.equals("2") || c.sourceId.equals("14") || c.sourceId.equals("6") || c.sourceId.equals("8") || c.sourceId.equals("10") || c.sourceId.equals("44")) {
            int gem = c.sourceId.equals("44") ? (1 + r.nextInt(7)) * 2 : Integer.parseInt(c.sourceId);
            return gemDrop(gem, c, r);
        }
        if (c.sourceId.equals("46")) {
            drops.add(new ItemStack(ModItems.IRIDIUM_ORE.get(), 1 + r.nextInt(3)));
            drops.add(new ItemStack(ModItems.GOLD_ORE.get(), 1 + r.nextInt(4)));
            if (r.nextDouble() < 0.25) drops.add(new ItemStack(ModItems.PRISMATIC_SHARD.get()));
            return new Result(drops, 150);
        }
        if (c.sourceId.equals("765") || isCalicoStone(c.sourceId)) {
            boolean iridium = c.sourceId.equals("765");
            int count = 1 + r.nextInt(3) + (iridium ? c.addedOres : 0)
                    + (r.nextDouble() < c.luck / 100.0 ? 1 : 0)
                    + (r.nextDouble() < c.mining / 100.0 ? 1 : 0);
            drops.add(new ItemStack(iridium ? ModItems.IRIDIUM_ORE.get() : ModItems.CALICO_EGG.get(), count));
            if (iridium && r.nextDouble() < 0.035) drops.add(new ItemStack(ModItems.PRISMATIC_SHARD.get()));
            return new Result(drops, 50);
        }
        // These are guaranteed geode nodes, not the ordinary-stone geode chance.
        if (c.sourceId.equals("75") || c.sourceId.equals("76") || c.sourceId.equals("77")) {
            Item geode = c.sourceId.equals("77") ? ModItems.MAGMA_GEODE.get()
                    : c.sourceId.equals("76") ? ModItems.FROZEN_GEODE.get() : ModItems.GEODE.get();
            return new Result(List.of(new ItemStack(geode)), c.sourceId.equals("77") ? 32 : c.sourceId.equals("76") ? 16 : 8);
        }
        if (c.sourceId.equals("95")) {
            int count = 1 + r.nextInt(2) + c.addedOres
                    + (r.nextDouble() < c.luck / 100.0 ? 1 : 0)
                    + (r.nextDouble() < c.mining / 200.0 ? 1 : 0);
            return new Result(List.of(new ItemStack(ModItems.RADIOACTIVE_ORE.get(), count)), 18);
        }
        if (c.sourceId.equals("25")) {
            return new Result(List.of(new ItemStack(ModItems.MUSSEL.get(), 2 + r.nextInt(3))), 5);
        }
        if (c.sourceId.equals("816") || c.sourceId.equals("817")) {
            if (r.nextDouble() < 0.1) drops.add(new ItemStack(ModItems.FOSSILIZED_LEG.get()));
            else if (r.nextDouble() < 0.015) drops.add(new ItemStack(ModItems.FOSSILIZED_RIBS.get()));
            else if (r.nextDouble() < 0.1) {
                Item fossil = switch (r.nextInt(11)) {
                    case 0 -> ModItems.PREHISTORIC_SCAPULA.get(); case 1 -> ModItems.PREHISTORIC_TIBIA.get();
                    case 2 -> ModItems.PREHISTORIC_SKULL.get(); case 3 -> ModItems.SKELETAL_HAND.get();
                    case 4 -> ModItems.PREHISTORIC_RIB.get(); case 5 -> ModItems.PREHISTORIC_VERTEBRA.get();
                    case 6 -> ModItems.SKELETAL_TAIL.get(); case 7 -> ModItems.NAUTILUS_FOSSIL.get();
                    case 8 -> ModItems.AMPHIBIAN_FOSSIL.get(); case 9 -> ModItems.PALM_FOSSIL.get();
                    default -> ModItems.TRILOBITE.get();
                };
                drops.add(new ItemStack(fossil));
            }
            drops.add(new ItemStack(ModItems.BONE_FRAGMENT.get(), c.addedOres + 1 + r.nextInt(2)
                    + (r.nextDouble() < c.luck / 100.0 ? 1 : 0)
                    + (r.nextDouble() < c.mining / 100.0 ? 1 : 0)));
            return new Result(drops, 6);
        }
        if (c.sourceId.equals("818")) {
            int count = c.addedOres + 1 + r.nextInt(2)
                    + (r.nextDouble() < c.luck / 100.0 ? 1 : 0)
                    + (r.nextDouble() < c.mining / 100.0 ? 1 : 0);
            return new Result(List.of(new ItemStack(ModItems.CLAY.get(), count)), 6);
        }
        if (c.sourceId.equals("819")) return new Result(List.of(new ItemStack(ModItems.OMNI_GEODE.get())), 64);
        if (c.sourceId.equals("843") || c.sourceId.equals("844")) {
            int count = c.addedOres + 1 + r.nextInt(2)
                    + (r.nextDouble() < c.luck / 100.0 ? 1 : 0)
                    + (r.nextDouble() < c.mining / 200.0 ? 1 : 0);
            return new Result(List.of(new ItemStack(ModItems.CINDER_SHARD.get(), count)), 12);
        }
        double modifier = 1 + c.dailyLuck / 2 + c.mining * 0.005 + c.luck * 0.001;
        double geodeMultiplier = modifier * (c.excavator ? 2 : 1) * (c.geodeBlessing ? 1.25 : 1);
        if (r.nextDouble() < 0.022 * geodeMultiplier) {
            Item geode = c.floor > 120 ? ModItems.OMNI_GEODE.get()
                    : c.floor >= 80 ? ModItems.MAGMA_GEODE.get()
                    : c.floor >= 40 ? ModItems.FROZEN_GEODE.get() : ModItems.GEODE.get();
            drops.add(new ItemStack(geode, c.geologist && r.nextBoolean() ? 2 : 1));
        }
        if (c.floor > 20 && r.nextDouble() < 0.005 * geodeMultiplier) {
            drops.add(new ItemStack(ModItems.OMNI_GEODE.get(), c.geologist && r.nextBoolean() ? 2 : 1));
        }
        double oreModifier = (c.sourceId.equals("40") || c.sourceId.equals("42")) ? 1.2 : 0.8;
        if (r.nextDouble() < 0.05 * modifier * oreModifier) {
            if (r.nextDouble() < 0.25 * (c.prospector ? 2 : 1) + (c.coalBlessing ? 0.1 : 0)) {
                drops.add(new ItemStack(ModItems.COAL.get()));
            }
            drops.add(new ItemStack(oreForFloor(c, r)));
            return new Result(drops, 5);
        }
        if (r.nextBoolean()) drops.add(new ItemStack(ModItems.STONE.get()));
        return new Result(drops, 0);
    }

    /** GameLocation.OnStoneDestroyed/breakStone for 343 outside generated mines. */
    public static Result rollSurfaceStone(Context c, int daysPlayed, boolean outdoors, boolean hasPlayer,
                                           RandomSource datedExtras, RandomSource outdoorCoal) {
        List<ItemStack> drops = new ArrayList<>();
        if (datedExtras.nextDouble() < 0.035 * (c.geodeBlessing ? 1.25 : 1) && daysPlayed > 1) {
            Item geode = daysPlayed > 60 && datedExtras.nextDouble() < 0.2 ? ModItems.FROZEN_GEODE.get()
                    : daysPlayed > 120 && datedExtras.nextDouble() < 0.2 ? ModItems.MAGMA_GEODE.get() : ModItems.GEODE.get();
            drops.add(new ItemStack(geode));
        }
        if (datedExtras.nextDouble() < 0.035 * (c.prospector ? 2 : 1) + (c.coalBlessing ? 0.03 : 0)
                && daysPlayed > 1) drops.add(new ItemStack(ModItems.COAL.get()));
        if (datedExtras.nextDouble() < 0.01 && daysPlayed > 1) drops.add(new ItemStack(ModItems.STONE.get()));
        int xp = 0;
        if (outdoors) {
            drops.add(new ItemStack(ModItems.STONE.get()));
            double modifier = c.dailyLuck / 2 + c.mining * 0.005 + c.luck * 0.001;
            if (hasPlayer) {
                xp = 1;
                double professionCoal = (c.prospector ? 0.05 * (1 + modifier) : 0) + (c.coalBlessing ? 0.025 : 0);
                if (outdoorCoal.nextDouble() < professionCoal) drops.add(new ItemStack(ModItems.COAL.get()));
            }
            if (outdoorCoal.nextDouble() < 0.05 * (1 + modifier)) {
                drops.add(new ItemStack(ModItems.COAL.get()));
                if (hasPlayer) xp += 5;
            }
        }
        return new Result(drops, xp);
    }

    /** Registered interiors can share an otherwise sky-lit dimension with the outdoor map. */
    public static boolean surfaceIsOutdoors(net.minecraft.resources.ResourceLocation dimension, BlockPos pos,
                                            boolean dimensionHasSkyLight) {
        return com.stardew.craft.api.v1.world.StardewLocations.find(dimension, pos)
                .map(location -> !location.indoor()).orElse(dimensionHasSkyLight);
    }

    private static RandomSource surfaceDayRandom(ServerLevel level, BlockPos pos, int daysPlayed, int salt) {
        // Keep each source branch deterministic by world, day and block; MC and .NET RNG sequences differ.
        return RandomSource.create(level.getSeed() ^ ((long) daysPlayed * 341873128712L)
                ^ net.minecraft.util.Mth.getSeed(pos.getX() * salt, pos.getY(), pos.getZ()));
    }

    private static Result gemDrop(int source, Context c, RandomSource r) {
        Item gem = switch (source) {
            case 2 -> ModItems.DIAMOND.get(); case 4 -> ModItems.RUBY.get();
            case 6 -> ModItems.JADE.get(); case 8 -> ModItems.AMETHYST.get();
            case 10 -> ModItems.TOPAZ.get(); case 12 -> ModItems.EMERALD.get();
            case 14 -> ModItems.AQUAMARINE.get();
            default -> throw new IllegalArgumentException("Unknown gem source " + source);
        };
        boolean extra = c.geologist && r.nextBoolean();
        int count = (c.miningMastery ? 2 : 1) * (extra ? 2 : 1);
        // Original Geologist branch replaces XP rather than adding to it.
        int xp = switch (source) {
            case 2 -> extra ? 100 : 150; case 4, 12 -> extra ? 50 : 80;
            case 6, 14 -> extra ? 20 : 40; default -> extra ? 8 : 16;
        };
        return new Result(List.of(new ItemStack(gem, count)), xp);
    }

    private static Item oreForFloor(Context c, RandomSource r) {
        int floor = c.floor;
        if (floor < 40) return floor >= 20 && r.nextDouble() < 0.1 ? ModItems.IRON_ORE.get() : ModItems.COPPER_ORE.get();
        if (floor < 80) {
            if (floor >= 60 && r.nextDouble() < 0.1) return ModItems.GOLD_ORE.get();
            return r.nextDouble() < 0.75 ? ModItems.IRON_ORE.get() : ModItems.COPPER_ORE.get();
        }
        if (floor >= 120) {
            if (c.festival && r.nextDouble() < 0.13 + c.festivalRating * 0.005) return ModItems.CALICO_EGG.get();
            if (r.nextDouble() < 0.01 + (floor - 120) / 2000.0) return ModItems.IRIDIUM_ORE.get();
        }
        if (r.nextDouble() < 0.75) return ModItems.GOLD_ORE.get();
        return r.nextDouble() < 0.75 ? ModItems.IRON_ORE.get() : ModItems.COPPER_ORE.get();
    }

    public static int floor(ServerLevel level, BlockPos pos) {
        return level.dimension() == ModMiningDimensions.STARDEW_MINING
                ? Math.max(1, Math.round(pos.getZ() / (float) MiningCoordinates.FLOOR_SPACING)) : 1;
    }

    /** Called once by either the completed player break or the mod bomb, never by both. */
    public static void finishDrops(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, boolean bomb) {
        var r = OrdinaryMineRuntime.nodeDropRandom(level, player, pos);
        int floor = floor(level, pos);
        boolean festival = DesertFestivalMineService.isActive();
        Context c = new Context(((MineStoneBlock) state.getBlock()).sourceId(), floor,
                player == null ? 0 : PlayerStardewDataAPI.getSkillLevel(player, SkillType.MINING),
                player == null ? 0 : PlayerStardewDataAPI.getLuckBuffLevel(player),
                player == null ? 0 : PlayerStardewDataAPI.getDailyLuck(player),
                player != null && PlayerStardewDataAPI.hasProfession(player, ProfessionType.EXCAVATOR),
                player != null && PlayerStardewDataAPI.hasProfession(player, ProfessionType.GEOLOGIST),
                player != null && PlayerStardewDataAPI.hasProfession(player, ProfessionType.PROSPECTOR),
                player != null && player.hasEffect(com.stardew.craft.effect.ModMobEffects.DWARF_STATUE_4),
                player != null && player.hasEffect(com.stardew.craft.effect.ModMobEffects.DWARF_STATUE_2),
                festival, festival ? DesertFestivalMineService.currentRating(level) : 0,
                (player != null && PlayerStardewDataAPI.hasProfession(player, ProfessionType.MINER) ? 1 : 0)
                        + (player != null && player.hasEffect(com.stardew.craft.effect.ModMobEffects.DWARF_STATUE_0) ? 1 : 0),
                player != null && PlayerDataManager.getPlayerData(player).hasMastery(SkillType.MINING));
        // This source entry runs before the location-specific stone rewards, including calico nodes.
        DesertFestivalMineService.tryAddStoneEggDrop(level, player, pos, r);
        boolean generatedMine = level.dimension() == ModMiningDimensions.STARDEW_MINING;
        int daysPlayed = com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
        IslandStoneRewards.beforeNodeDrop(level, pos, c.sourceId, player != null, r);
        Result result = (c.sourceId.equals("343") || c.sourceId.equals("450")) && !generatedMine
                ? rollSurfaceStone(c, daysPlayed, surfaceIsOutdoors(level.dimension().location(), pos,
                        level.dimensionType().hasSkyLight()), player != null,
                        surfaceDayRandom(level, pos, daysPlayed, 2000), surfaceDayRandom(level, pos, daysPlayed, 1000))
                : roll(c, r);
        IslandStoneRewards.afterNodeDrop(level, pos, c.sourceId, r);
        result.drops.forEach(stack -> Block.popResource(level, pos, stack));
        if (player == null) return;
        if (result.miningExperience > 0) PlayerStardewDataAPI.addExperience(player, SkillType.MINING, result.miningExperience);
        if (c.sourceId.equals("46")) PlayerDataManager.getPlayerData(player).recordMysticStoneCrushed();
        if (bomb) PlayerDataManager.getPlayerData(player).addMineBlocksBombed(1);
        else {
            boolean gemNode = c.sourceId.equals("4") || c.sourceId.equals("12") || c.sourceId.equals("2") || c.sourceId.equals("14") || c.sourceId.equals("6") || c.sourceId.equals("8") || c.sourceId.equals("10") || c.sourceId.equals("44");
            PlayerDataManager.getPlayerData(player).recordMineBlockBroken(c.sourceId.equals("VolcanoCoalNode1") || c.sourceId.equals("BasicCoalNode0") || c.sourceId.equals("BasicCoalNode1") || c.sourceId.equals("850") || c.sourceId.equals("VolcanoGoldNode") || c.sourceId.equals("VolcanoCoalNode0") || c.sourceId.equals("849") || c.sourceId.equals("843") || c.sourceId.equals("844") || c.sourceId.equals("95") || c.sourceId.equals("751") || c.sourceId.equals("290") || c.sourceId.equals("764") || c.sourceId.equals("46") || c.sourceId.equals("765") || isCalicoStone(c.sourceId) || gemNode, gemNode, false);
        }
        ItemStack note = com.stardew.craft.secretnote.SecretNoteService.tryCreateFromSource(player, r, 0.0075F);
        if (!note.isEmpty()) Block.popResource(level, pos, note);
        if (!bomb && BookPowerEffects.shouldDropDiamondFromStone(PlayerDataManager.getPlayerData(player), r)) {
            Block.popResource(level, pos, new ItemStack(ModItems.DIAMOND.get(), c.geologist && r.nextBoolean() ? 2 : 1));
        }
        // Node-specific and ordinary-ore festival rewards remain independent of the entry-point egg roll.
    }
}
