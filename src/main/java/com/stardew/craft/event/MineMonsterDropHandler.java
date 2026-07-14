package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.trinket.TrinketDropService;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.shop.MonsterSlayerGoalRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.Collection;
import java.util.Set;

/**
 * 矿井怪物掉落事件处理器 — 严格对齐 SDV 源码 Content/Data/Monsters.json。
 *
 * <p>每个 SDV 怪物的掉落表由一组 "itemId chance" 独立 roll 组成：
 * 每对是一次独立的随机判定，通过则掉落 1 个对应物品（非互斥）。
 *
 * <p>同名变种（Green Slime / Frost Jelly / Sludge 等）通过 sd_tier_* 子标签区分。
 * 变种映射规则：变种仍使用同一种原版 MC 实体，仅用 tag 切换掉落表。
 *
 * <p>SDV 物品 ID 快速对照：
 * <pre>
 *  60=Emerald 62=Aquamarine 64=Ruby 66=Amethyst 68=Topaz 70=Jade
 *  72=Diamond 74=PrismaticShard 80=Quartz 84=FrozenTear 86=EarthCrystal
 *  92=Sap 96..99=DwarfScrollI..IV 105=Chanterelle
 *  153=GreenAlgae 156=Ghostfish 157=WhiteAlgae 203=StrangeBun 226=SpicyEel
 *  243=MinersTreat 273=RiceShoot 280=StarShards 286=CherryBomb 287=Bomb 288=MegaBomb
 *  334..337=Copper/Iron/Gold/IridiumBar 338=RefinedQuartz 349=EnergyTonic
 *  378=CopperOre 380=IronOre 382=Coal 384=GoldOre 386=IridiumOre 390=Stone
 *  428=Cloth 433=CoffeeBean 446=RabbitsFoot 535=Geode 579=PrehistoricHandaxe
 *  684=BugMeat 717=Crab 732=Lobster 749=OmniGeode 766=Slime 767=BatWing
 *  768=SolarEssence 769=VoidEssence 770=MixedSeeds 771=Fiber 773=LifeElixir
 *  787=BatteryPack 814=SquidInk 848=CinderShard 851=MagmaCap 852=DragonTooth
 *  856=CuriosityLure 881=BoneFragment
 * </pre>
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public class MineMonsterDropHandler {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        Set<String> tags = entity.getTags();
        if (tags.stream().noneMatch(t -> t.startsWith("sd_mob_"))) return;

        // 清除原版掉落
        Collection<ItemEntity> drops = event.getDrops();
        drops.clear();

        RandomSource random = serverLevel.getRandom();

        // 标签分派 — 按 MineMonsterSpawnHandler 的分组
        if (tags.contains("sd_mob_slime")) {
            dropSlimeVariant(drops, entity, random, tags);
        } else if (tags.contains("sd_mob_bigslime_skull")) {
            dropBigSlime(drops, entity, random);
        } else if (tags.contains("sd_mob_bat")) {
            dropBatVariant(drops, entity, random, tags);
        } else if (tags.contains("sd_mob_fly")) {
            dropFly(drops, entity, random);
        } else if (tags.contains("sd_mob_grub")) {
            dropGrub(drops, entity, random);
        } else if (tags.contains("sd_mob_bug")) {
            dropBug(drops, entity, random);
        } else if (tags.contains("sd_mob_dust_sprite")) {
            dropDustSpirit(drops, entity, random);
        } else if (tags.contains("sd_mob_skeleton")) {
            dropSkeleton(drops, entity, random);
        } else if (tags.contains("sd_mob_ghost")) {
            if (tags.contains("sd_tier_skull")) dropCarbonGhost(drops, entity, random);
            else dropGhost(drops, entity, random);
        } else if (tags.contains("sd_mob_crab")) {
            if (tags.contains("sd_tier_4") || tags.contains("sd_tier_skull")) {
                dropIridiumCrab(drops, entity, random);
            } else if (tags.contains("sd_tier_2")) {
                dropLavaCrab(drops, entity, random);
            } else {
                dropRockCrab(drops, entity, random);
            }
        } else if (tags.contains("sd_mob_golem")) {
            dropStoneGolem(drops, entity, random);
        } else if (tags.contains("sd_mob_shadow")) {
            if (tags.contains("sd_tier_2")) dropShadowShaman(drops, entity, random);
            else dropShadowBrute(drops, entity, random);
        } else if (tags.contains("sd_mob_duggy")) {
            dropDuggy(drops, entity, random);
        } else if (tags.contains("sd_mob_metal_head")) {
            dropMetalHead(drops, entity, random);
        } else if (tags.contains("sd_mob_squid")) {
            dropSquidKid(drops, entity, random);
        } else if (tags.contains("sd_mob_mummy")) {
            dropMummy(drops, entity, random);
        } else if (tags.contains("sd_mob_royal_serpent")) {
            dropRoyalSerpent(drops, entity, random);
        } else if (tags.contains("sd_mob_serpent")) {
            dropSerpent(drops, entity, random);
        } else if (tags.contains("sd_mob_dino")) {
            dropPepperRex(drops, entity, random);
        }

        if (event.getSource() != null && event.getSource().getEntity() instanceof ServerPlayer player) {
            if (tags.contains("sd_mob_ghost")
                && com.stardew.craft.specialorder.SpecialOrderManager.hasActiveIncompleteOrder(player, "Wizard")
                && !com.stardew.craft.specialorder.SpecialOrderManager.hasSpecialDropFlag(player, "ectoplasmDrop")
                && random.nextFloat() < 0.095F) {
                addDrop(drops, entity, ModItems.ECTOPLASM.get(), 1);
            }
            if (tags.contains("sd_mob_prismatic_slime")
                && com.stardew.craft.specialorder.SpecialOrderManager.hasActiveIncompleteOrder(player, "Wizard2")
                && !com.stardew.craft.specialorder.SpecialOrderManager.hasSpecialDropFlag(player, "prismaticJellyDrop")) {
                addDrop(drops, entity, ModItems.PRISMATIC_JELLY.get(), 1);
                com.stardew.craft.specialorder.SpecialOrderManager.recordMonsterSlain(player, "Prismatic Slime");
            }
            BookPowerEffects.applyVoidMonsterDropDuplicate(PlayerDataManager.getPlayerData(player), drops, entity, random);
            com.stardew.craft.book.BookAcquisitionService.recordMonsterKilledAndMaybeAddVoidBook(player, drops, entity, random);
            TrinketDropService.tryAddMonsterDrop(drops, entity, player, random);
        }

        // ---- Monster Slayer kill tracking (SDV Gil goals) ----
        if (event.getSource() != null && event.getSource().getEntity() instanceof ServerPlayer player) {
            java.util.Set<String> progressedGoals = new java.util.LinkedHashSet<>();
            for (String tag : tags) {
                progressedGoals.addAll(MonsterSlayerGoalRegistry.getGoalKeysForTag(tag));
            }
            PlayerStardewData slayerData = PlayerDataManager.getPlayerData(player);
            progressedGoals.forEach(goalKey -> slayerData.addMonsterKills(goalKey, 1));
            for (String tag : tags) {
                if (tag.startsWith("sd_mob_")) {
                    com.stardew.craft.quest.StardewQuestEvents.fireMonsterSlain(player, tag);
                    com.stardew.craft.specialorder.SpecialOrderManager.recordMonsterSlain(player, tag);
                }
            }
            com.stardew.craft.festival.desert.DesertFestivalMarlonChallengeService.recordMonsterSlain(player, tags);
            com.stardew.craft.festival.desert.DesertFestivalMineService.tryAddMonsterEggDrop(drops, entity, player, random);
            MiningBlockBreakHandler.tryCreateLadderFromMonsterDrop(serverLevel, player, entity.blockPosition());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //                        SLIME 系
    // ═══════════════════════════════════════════════════════════

    private static void dropSlimeVariant(Collection<ItemEntity> drops, LivingEntity e, RandomSource r, Set<String> tags) {
        if (tags.contains("sd_tier_3")) dropSludge(drops, e, r);
        else if (tags.contains("sd_tier_2")) dropFrostJelly(drops, e, r);
        else dropGreenSlime(drops, e, r);
    }

    /** SDV Green Slime: 766 .75 | 766 .05 | 153 .1 | 66 .015 | 92 .15 | 96 .005 | 99 .001 */
    private static void dropGreenSlime(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.75f, ModItems.SLIME_ITEM.get(), 1);
        rollDrop(drops, e, r, 0.05f, ModItems.SLIME_ITEM.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.GREEN_ALGAE.get(), 1);
        rollDrop(drops, e, r, 0.015f, ModItems.AMETHYST.get(), 1);
        rollDrop(drops, e, r, 0.15f, ModItems.SAP.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_I.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Frost Jelly: 766 .75 | 412 .08 | 70 .02 | 98 .015 | 92 .5 | 97 .005 | 99 .001 (412 跳过) */
    private static void dropFrostJelly(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.75f, ModItems.SLIME_ITEM.get(), 1);
        rollDrop(drops, e, r, 0.02f, ModItems.JADE.get(), 1);
        rollDrop(drops, e, r, 0.015f, ModItems.DWARF_SCROLL_III.get(), 1);
        rollDrop(drops, e, r, 0.50f, ModItems.SAP.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_II.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Sludge: 766 .8 | 157 .1 | -4 .1 | 72 .01 | 92 .5 | 98 .005 | 99 .001 (-4 跳过) */
    private static void dropSludge(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.80f, ModItems.SLIME_ITEM.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.WHITE_ALGAE.get(), 1);
        rollDrop(drops, e, r, 0.01f, ModItems.DIAMOND.get(), 1);
        rollDrop(drops, e, r, 0.50f, ModItems.SAP.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_III.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Big Slime: 766 .99 | 766 .9 | 766 .4 | 99 .001 */
    private static void dropBigSlime(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.99f, ModItems.SLIME_ITEM.get(), 1);
        rollDrop(drops, e, r, 0.90f, ModItems.SLIME_ITEM.get(), 1);
        rollDrop(drops, e, r, 0.40f, ModItems.SLIME_ITEM.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    // ═══════════════════════════════════════════════════════════
    //                         BAT 系
    // ═══════════════════════════════════════════════════════════

    private static void dropBatVariant(Collection<ItemEntity> drops, LivingEntity e, RandomSource r, Set<String> tags) {
        if (tags.contains("sd_tier_4")) dropIridiumBat(drops, e, r);
        else if (tags.contains("sd_tier_3")) dropLavaBat(drops, e, r);
        else if (tags.contains("sd_tier_2")) dropFrostBat(drops, e, r);
        else dropBat(drops, e, r);
    }

    /** SDV Bat: 767 .9 | 767 .4 | 108 .001 | 287 .02 | 96 .005 | 99 .001 (108 跳过) */
    private static void dropBat(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.90f, ModItems.BAT_WING.get(), 1);
        rollDrop(drops, e, r, 0.40f, ModItems.BAT_WING.get(), 1);
        rollDrop(drops, e, r, 0.02f, ModItems.BOMB_ITEM.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_I.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Frost Bat: 767 .9 | 767 .55 | 108 .001 | 287 .02 | 97 .005 | 99 .001 */
    private static void dropFrostBat(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.90f, ModItems.BAT_WING.get(), 1);
        rollDrop(drops, e, r, 0.55f, ModItems.BAT_WING.get(), 1);
        rollDrop(drops, e, r, 0.02f, ModItems.BOMB_ITEM.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_II.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Lava Bat: 767 .9 | 767 .7 | 108 .001 | 287 .02 | 98 .005 | 99 .001 */
    private static void dropLavaBat(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.90f, ModItems.BAT_WING.get(), 1);
        rollDrop(drops, e, r, 0.70f, ModItems.BAT_WING.get(), 1);
        rollDrop(drops, e, r, 0.02f, ModItems.BOMB_ITEM.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_III.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Iridium Bat: 386 .9 | 386 .5 | 386 .25 | 386 .1 | 288 .05 | 768 .5 | 773 .05 | 349 .05 | 787 .05 | 337 .008 */
    private static void dropIridiumBat(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.90f, ModItems.IRIDIUM_ORE.get(), 1);
        rollDrop(drops, e, r, 0.50f, ModItems.IRIDIUM_ORE.get(), 1);
        rollDrop(drops, e, r, 0.25f, ModItems.IRIDIUM_ORE.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.IRIDIUM_ORE.get(), 1);
        rollDrop(drops, e, r, 0.05f, ModItems.MEGA_BOMB.get(), 1);
        rollDrop(drops, e, r, 0.50f, ModItems.SOLAR_ESSENCE.get(), 1);
        rollDropByRegistry(drops, e, r, 0.05f, "life_elixir", 1);
        rollDrop(drops, e, r, 0.05f, ModItems.ENERGY_TONIC.get(), 1);
        rollDrop(drops, e, r, 0.05f, ModItems.BATTERY_PACK.get(), 1);
        rollDrop(drops, e, r, 0.008f, ModItems.IRIDIUM_BAR.get(), 1);
    }

    // ═══════════════════════════════════════════════════════════
    //                    虫 / 苍蝇 / 蛆 / 尘魂
    // ═══════════════════════════════════════════════════════════

    /** SDV Fly: 684 .9 | 157 .02 | 96 .005 | 99 .001 */
    private static void dropFly(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.90f, ModItems.BUG_MEAT.get(), 1);
        rollDrop(drops, e, r, 0.02f, ModItems.WHITE_ALGAE.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_I.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Grub: 684 .6 | 273 .05 | 273 .05 | 157 .02 | 96 .005 | 99 .001 (273=RiceShoot 通过注册表) */
    private static void dropGrub(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.60f, ModItems.BUG_MEAT.get(), 1);
        rollDropByRegistry(drops, e, r, 0.05f, "rice_shoot", 1);
        rollDropByRegistry(drops, e, r, 0.05f, "rice_shoot", 1);
        rollDrop(drops, e, r, 0.02f, ModItems.WHITE_ALGAE.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_I.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Bug: 684 .76 | 157 .02 | 96 .005 | 99 .001 */
    private static void dropBug(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.76f, ModItems.BUG_MEAT.get(), 1);
        rollDrop(drops, e, r, 0.02f, ModItems.WHITE_ALGAE.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_I.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Dust Spirit: 382 .5 | 433 .01 | 336 .001 | 84 .02 | 414 .02 | 97 .005 | 99 .001 */
    private static void dropDustSpirit(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.50f, ModItems.COAL.get(), 1);
        rollDrop(drops, e, r, 0.01f, ModItems.COFFEE_BEAN.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.GOLD_BAR.get(), 1);
        rollDrop(drops, e, r, 0.02f, ModItems.FROZEN_TEAR.get(), 1);
        rollDropByRegistry(drops, e, r, 0.02f, "crystal_fruit", 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_II.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    // ═══════════════════════════════════════════════════════════
    //                       骷髅 / 幽灵
    // ═══════════════════════════════════════════════════════════

    /** SDV Skeleton: 881 .5 | 881 .4 | 881 .2 | 579 .005 | 99 .001 */
    private static void dropSkeleton(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.50f, ModItems.BONE_FRAGMENT.get(), 1);
        rollDrop(drops, e, r, 0.40f, ModItems.BONE_FRAGMENT.get(), 1);
        rollDrop(drops, e, r, 0.20f, ModItems.BONE_FRAGMENT.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.PREHISTORIC_TIBIA.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Ghost: 768 .95 | 768 .1 | 156 .08 | 338 .08 | -6 .2 | 97 .005 | 99 .001 (-6 跳过) */
    private static void dropGhost(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.95f, ModItems.SOLAR_ESSENCE.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.SOLAR_ESSENCE.get(), 1);
        rollDrop(drops, e, r, 0.08f, ModItems.GHOSTFISH.get(), 1);
        rollDrop(drops, e, r, 0.08f, ModItems.REFINED_QUARTZ.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_II.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Carbon Ghost: 749 .99 | 338 .1 */
    private static void dropCarbonGhost(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.99f, ModItems.OMNI_GEODE.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.REFINED_QUARTZ.get(), 1);
    }

    // ═══════════════════════════════════════════════════════════
    //                          蟹 系
    // ═══════════════════════════════════════════════════════════

    /** SDV Rock Crab: 717 .15 | 286 .4 | 96 .005 | 99 .001 */
    private static void dropRockCrab(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.15f, ModItems.CRAB.get(), 1);
        rollDrop(drops, e, r, 0.40f, ModItems.CHERRY_BOMB.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_I.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Lava Crab: 717 .25 | 287 .4 | 98 .005 | 99 .001 */
    private static void dropLavaCrab(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.25f, ModItems.CRAB.get(), 1);
        rollDrop(drops, e, r, 0.40f, ModItems.BOMB_ITEM.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_III.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Iridium Crab: 732 .5 | 386 .5 | 386 .5 | 386 .5 */
    private static void dropIridiumCrab(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.50f, ModItems.LOBSTER.get(), 1);
        rollDrop(drops, e, r, 0.50f, ModItems.IRIDIUM_ORE.get(), 1);
        rollDrop(drops, e, r, 0.50f, ModItems.IRIDIUM_ORE.get(), 1);
        rollDrop(drops, e, r, 0.50f, ModItems.IRIDIUM_ORE.get(), 1);
    }

    // ═══════════════════════════════════════════════════════════
    //                  Golem / Shadow / 其他人形
    // ═══════════════════════════════════════════════════════════

    /** SDV Stone Golem: 390 .9 | 80 .1 | 382 .1 | 380 .1 | 96 .005 | 99 .001 */
    private static void dropStoneGolem(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.90f, ModItems.STONE.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.QUARTZ.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.COAL.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.IRON_ORE.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_I.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Shadow Brute:
     * 769 .75 | 769 .1 | 337 .002 | 336 .01 | 335 .02 | 334 .04 | 203 .04 | 108 .003 | -4 .1 | 98 .005 | 99 .001 | 74 .0005 */
    private static void dropShadowBrute(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.75f, ModItems.VOID_ESSENCE.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.VOID_ESSENCE.get(), 1);
        rollDrop(drops, e, r, 0.002f, ModItems.IRIDIUM_BAR.get(), 1);
        rollDrop(drops, e, r, 0.01f, ModItems.GOLD_BAR.get(), 1);
        rollDrop(drops, e, r, 0.02f, ModItems.IRON_BAR.get(), 1);
        rollDrop(drops, e, r, 0.04f, ModItems.COPPER_BAR.get(), 1);
        rollDropByRegistry(drops, e, r, 0.04f, "strange_bun", 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_III.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
        rollDrop(drops, e, r, 0.0005f, ModItems.PRISMATIC_SHARD.get(), 1);
    }

    /** SDV Shadow Shaman:
     * 769 .75 | 769 .2 | 337 .002 | 336 .01 | 335 .02 | 334 .04 | 108 .003 | -4 .1 | 98 .005 | 99 .001 | 74 .0005 */
    private static void dropShadowShaman(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.75f, ModItems.VOID_ESSENCE.get(), 1);
        rollDrop(drops, e, r, 0.20f, ModItems.VOID_ESSENCE.get(), 1);
        rollDrop(drops, e, r, 0.002f, ModItems.IRIDIUM_BAR.get(), 1);
        rollDrop(drops, e, r, 0.01f, ModItems.GOLD_BAR.get(), 1);
        rollDrop(drops, e, r, 0.02f, ModItems.IRON_BAR.get(), 1);
        rollDrop(drops, e, r, 0.04f, ModItems.COPPER_BAR.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_III.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
        rollDrop(drops, e, r, 0.0005f, ModItems.PRISMATIC_SHARD.get(), 1);
    }

    /** SDV Duggy: 286 .25 | 535 .25 | 280 .03 | 105 .02 | 86 .1 | 72 .01 | 96 .005 | 99 .001 */
    private static void dropDuggy(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.25f, ModItems.CHERRY_BOMB.get(), 1);
        rollDrop(drops, e, r, 0.25f, ModItems.GEODE.get(), 1);
        rollDrop(drops, e, r, 0.03f, ModItems.STAR_SHARDS.get(), 1);
        rollDrop(drops, e, r, 0.02f, ModItems.CHANTERELLE.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.EARTH_CRYSTAL.get(), 1);
        rollDrop(drops, e, r, 0.01f, ModItems.DIAMOND.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_I.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Metal Head: 768 .65 | 378 .1 | 378 .1 | 380 .1 | 380 .1 | 382 .1 | 98 .005 | 99 .001 */
    private static void dropMetalHead(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.65f, ModItems.SOLAR_ESSENCE.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.COPPER_ORE.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.COPPER_ORE.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.IRON_ORE.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.IRON_ORE.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.COAL.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_III.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    /** SDV Squid Kid: 768 .75 | 814 .2 | 336 .05 | 287 .1 | 288 .05 | 98 .005 | 99 .001 */
    private static void dropSquidKid(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.75f, ModItems.SOLAR_ESSENCE.get(), 1);
        rollDropByRegistry(drops, e, r, 0.20f, "squid_ink", 1);
        rollDrop(drops, e, r, 0.05f, ModItems.GOLD_BAR.get(), 1);
        rollDrop(drops, e, r, 0.10f, ModItems.BOMB_ITEM.get(), 1);
        rollDrop(drops, e, r, 0.05f, ModItems.MEGA_BOMB.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.DWARF_SCROLL_III.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    // ═══════════════════════════════════════════════════════════
    //                      骷髅矿洞专属
    // ═══════════════════════════════════════════════════════════

    /** SDV Mummy: 768 .99 | 428 .2 | 428 .05 | 768 .15 | 243 .04 | 856 .01 | 99 .001 | 74 .001 */
    private static void dropMummy(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.99f, ModItems.SOLAR_ESSENCE.get(), 1);
        rollDrop(drops, e, r, 0.20f, ModItems.CLOTH.get(), 1);
        rollDrop(drops, e, r, 0.05f, ModItems.CLOTH.get(), 1);
        rollDrop(drops, e, r, 0.15f, ModItems.SOLAR_ESSENCE.get(), 1);
        rollDropByRegistry(drops, e, r, 0.04f, "miners_treat", 1);
        rollDrop(drops, e, r, 0.01f, ModItems.CURIOSITY_LURE.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.PRISMATIC_SHARD.get(), 1);
    }

    /** SDV Serpent: 769 .99 | 769 .15 | 287 .15 | 226 .06 | 446 .008 | 74 .001 */
    private static void dropSerpent(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.99f, ModItems.VOID_ESSENCE.get(), 1);
        rollDrop(drops, e, r, 0.15f, ModItems.VOID_ESSENCE.get(), 1);
        rollDrop(drops, e, r, 0.15f, ModItems.BOMB_ITEM.get(), 1);
        rollDropByRegistry(drops, e, r, 0.06f, "spicy_eel", 1);
        rollDrop(drops, e, r, 0.008f, ModItems.RABBITS_FOOT.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.PRISMATIC_SHARD.get(), 1);
    }

    /** SDV Royal Serpent: 769 .99 | 769 .4 | 288 .15 | 226 .1 | 446 .02 | 74 .01 */
    private static void dropRoyalSerpent(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 0.99f, ModItems.VOID_ESSENCE.get(), 1);
        rollDrop(drops, e, r, 0.40f, ModItems.VOID_ESSENCE.get(), 1);
        rollDrop(drops, e, r, 0.15f, ModItems.MEGA_BOMB.get(), 1);
        rollDropByRegistry(drops, e, r, 0.10f, "spicy_eel", 1);
        rollDrop(drops, e, r, 0.02f, ModItems.RABBITS_FOOT.get(), 1);
        rollDrop(drops, e, r, 0.01f, ModItems.PRISMATIC_SHARD.get(), 1);
    }

    /** SDV Pepper Rex (特殊：SDV 中主要通过地上产蛋表现，这里给最小近似掉落) */
    private static void dropPepperRex(Collection<ItemEntity> drops, LivingEntity e, RandomSource r) {
        rollDrop(drops, e, r, 1.00f, ModItems.QUARTZ.get(), 1);
        rollDrop(drops, e, r, 0.50f, ModItems.BONE_FRAGMENT.get(), 1);
        rollDrop(drops, e, r, 0.30f, ModItems.BONE_FRAGMENT.get(), 1);
        rollDrop(drops, e, r, 0.005f, ModItems.PREHISTORIC_TIBIA.get(), 1);
        rollDrop(drops, e, r, 0.001f, ModItems.DWARF_SCROLL_IV.get(), 1);
    }

    // ═══════════════════════════════════════════════════════════
    //                        工具方法
    // ═══════════════════════════════════════════════════════════

    private static void rollDrop(Collection<ItemEntity> drops, LivingEntity e, RandomSource r, float chance, Item item, int count) {
        if (r.nextFloat() < chance) addDrop(drops, e, item, count);
    }

    private static void rollDropByRegistry(Collection<ItemEntity> drops, LivingEntity e, RandomSource r, float chance, String name, int count) {
        if (r.nextFloat() < chance) addDropByRegistry(drops, e, name, count);
    }

    private static void addDrop(Collection<ItemEntity> drops, LivingEntity entity, Item item, int count) {
        if (item == null || item == Items.AIR) return;
        ItemStack stack = new ItemStack(item, count);
        ItemEntity itemEntity = new ItemEntity(entity.level(),
                entity.getX(), entity.getY(), entity.getZ(), stack);
        itemEntity.setDefaultPickUpDelay();
        drops.add(itemEntity);
    }

    /** 通过注册表名查找物品（用于可能未在 ModItems 直接声明的物品）。 */
    private static void addDropByRegistry(Collection<ItemEntity> drops, LivingEntity entity, String name, int count) {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, name);
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == Items.AIR) {
            StardewCraft.LOGGER.warn("[MineMonsterDrop] Item not found: {}", name);
            return;
        }
        addDrop(drops, entity, item, count);
    }
}
